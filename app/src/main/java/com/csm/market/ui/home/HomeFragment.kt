package com.csm.market.ui.home

import android.os.Bundle
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.csm.market.R
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.databinding.FragmentHomeBinding
import com.csm.market.ui.common.hideState
import com.csm.market.ui.common.showEmpty
import com.csm.market.ui.common.showError
import com.csm.market.ui.common.startSkeletonPulse
import com.csm.market.ui.common.stopSkeletonPulse
import com.csm.market.util.LocationProvider
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 首页：商品列表 + 分类筛选。
 *
 * 第 3 次提交把数据源换成了 ViewModel + Repository：
 * - Fragment 不再自己查数据、不再持有 Job，只做「订阅状态 → 画界面」；
 * - 状态订阅用 `repeatOnLifecycle(STARTED)`：页面退到后台自动停止收集，
 *   回到前台自动恢复，既不会在不可见时白跑，也不会漏掉恢复后的最新数据；
 * - 数据来自 Room 的 Flow，商品表一变化列表自动刷新，无需手动回调。
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory }

    private val adapter = ProductAdapter(onItemClick = { product -> openDetail(product) })

    /** 系统权限弹窗的回调：同意就取定位，拒绝就只提示、保持默认排序。 */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadLocationAndEnable()
        } else {
            toast(getString(R.string.nearby_denied))
            binding.toolbar.menu.findItem(R.id.action_nearby)?.isChecked = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    findNavController().navigate(R.id.action_home_to_search)
                    true
                }

                R.id.action_nearby -> {
                    toggleNearby(item)
                    true
                }
                else -> false
            }
        }

        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = adapter

        binding.fabPublish.setOnClickListener {
            findNavController().navigate(R.id.publishFragment)
        }

        binding.uiState.btnStateRetry.setOnClickListener { viewModel.retry() }

        setupCategoryChips()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isOffline.collect { offline ->
                    binding.tvOfflineBanner.isVisible = offline
                }
            }
        }

        // 距离变化时只刷新条目文案，不重新提交列表（列表本身没变，位置变了）
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.distances.collect { distances ->
                    adapter.distances = distances
                    adapter.notifyItemRangeChanged(0, adapter.itemCount)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.message.collect { resId ->
                    resId ?: return@collect
                    toast(getString(resId))
                    viewModel.consumeMessage()
                }
            }
        }
    }

    /**
     * 切换「附近好物」。
     *
     * 权限流程按课程要求做成可降级：
     * 已授权 → 取一次定位并开启排序；
     * 未授权 → 请求权限，用户同意后开启；
     * 拒绝 → 提示「定位不可用，已按最新发布排序」，列表照常显示，什么都不崩。
     */
    private fun toggleNearby(item: MenuItem) {
        if (viewModel.nearbyEnabled.value) {
            item.isChecked = false
            viewModel.setNearbyEnabled(false)
            return
        }
        if (LocationProvider.hasPermission(requireContext())) {
            loadLocationAndEnable()
        } else {
            permissionLauncher.launch(LocationProvider.PERMISSION)
        }
    }

    private fun loadLocationAndEnable() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 定位超时也要能退出来，不能让用户对着空白列表等
            val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                LocationProvider.lastKnownLocation(requireContext())
            }
            viewModel.onLocationResolved(location)
            if (location != null) {
                viewModel.setNearbyEnabled(true)
            }
            binding.toolbar.menu.findItem(R.id.action_nearby)?.isChecked =
                viewModel.nearbyEnabled.value
        }
    }

    private fun setupCategoryChips() {
        val chipGroup = binding.chipGroupCategory
        chipGroup.removeAllViews()

        val allChip = createChip(getString(R.string.category_all), selected = true)
        allChip.tag = null
        chipGroup.addView(allChip)

        ProductCategory.entries.forEach { category ->
            val chip = createChip(category.label, selected = false)
            chip.tag = category
            chipGroup.addView(chip)
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: View.NO_ID)
            // tag 为 null 就是「全部」分类
            viewModel.selectCategory(chip?.tag as? ProductCategory)
        }
    }

    private fun createChip(text: String, selected: Boolean): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isCheckable = true
            isChecked = selected
            setEnsureMinTouchTargetSize(false)
            chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke)
        }
    }

    private fun renderState(state: UiState<List<Product>>) {
        when (state) {
            is UiState.Loading -> {
                binding.skeletonGroup.isVisible = true
                binding.skeletonGroup.startSkeletonPulse()
                binding.rvProducts.isVisible = false
                binding.uiState.hideState()
            }

            is UiState.Success -> {
                hideLoading()
                binding.uiState.hideState()
                binding.rvProducts.isVisible = true
                adapter.submitList(state.data)
            }

            is UiState.Empty -> {
                hideLoading()
                binding.rvProducts.isVisible = false
                // 空态文案取决于当前是否在筛选：拖到「教材书籍」却一件没有，
                // 和整个集市都没有商品，是两种完全不同的信息
                if (viewModel.selectedCategory.value == null) {
                    binding.uiState.showEmpty(R.string.home_empty_title, R.string.home_empty_desc)
                } else {
                    binding.uiState.showEmpty(
                        R.string.home_empty_filtered_title,
                        R.string.home_empty_filtered_desc
                    )
                }
            }

            is UiState.Error -> {
                hideLoading()
                binding.rvProducts.isVisible = false
                binding.uiState.showError(state.error) { viewModel.retry() }
            }
        }
    }

    private fun hideLoading() {
        binding.skeletonGroup.stopSkeletonPulse()
        binding.skeletonGroup.isVisible = false
    }

    private fun openDetail(product: Product) {
        findNavController().navigate(
            R.id.action_home_to_detail,
            bundleOf("productId" to product.id)
        )
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        binding.skeletonGroup.stopSkeletonPulse()
        binding.rvProducts.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        /** 定位最多等 3 秒：室内常常拿不到，等太久用户会以为卡死。 */
        const val LOCATION_TIMEOUT_MS = 3_000L
    }
}
