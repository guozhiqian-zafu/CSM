package com.csm.market.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

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

    private val adapter = ProductAdapter { product -> openDetail(product) }

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
                    toast(getString(R.string.feature_coming_soon))
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
}
