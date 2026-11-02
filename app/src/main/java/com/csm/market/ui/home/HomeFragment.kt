package com.csm.market.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.csm.market.R
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.sample.SampleData
import com.csm.market.databinding.FragmentHomeBinding
import com.csm.market.ui.common.hideState
import com.csm.market.ui.common.showEmpty
import com.csm.market.ui.common.showError
import com.csm.market.ui.common.startSkeletonPulse
import com.csm.market.ui.common.stopSkeletonPulse
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页：商品列表 + 分类筛选。
 *
 * 第 2 次提交把「加载 / 空 / 错误」三态补齐：
 * - 加载：骨架屏（形状与真实卡片一致，数据到位不跳动）
 * - 空：区分「一件商品都没有」和「当前分类没有商品」，文案不同
 * - 错误：统一 CsmError，按错误类型给不同提示，可重试的才显示重试按钮
 *
 * 数据源仍是内存样例数据；第 3 次提交会把这里换成 ViewModel + Repository，
 * 页面只消费 UiState，不需要再改三态逻辑。
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val adapter = ProductAdapter { product -> openDetail(product) }

    /** 当前选中的分类，null 表示「全部」 */
    private var selectedCategory: ProductCategory? = null

    /** 只保留最后一次加载，避免快速切分类时旧请求把新结果覆盖掉 */
    private var loadJob: Job? = null

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

        binding.uiState.btnStateRetry.setOnClickListener { loadProducts(showLoading = true) }

        setupCategoryChips()
        loadProducts(showLoading = true)
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
            val category = chip?.tag as? ProductCategory
            if (category == selectedCategory) return@setOnCheckedStateChangeListener
            selectedCategory = category
            // 切分类不必再走一次骨架屏：数据在内存里，闪一下加载反而更慢
            loadProducts(showLoading = false)
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

    /**
     * 加载列表。
     *
     * 当前没有网络层与数据库，用 400ms 模拟一次异步请求，目的是让三态在真机上可演示、
     * 也让「异步 + 生命周期」的处理方式先成型：协程挂在 viewLifecycleOwner 上，
     * 页面销毁自动取消，不会在已销毁的 View 上更新 UI。
     */
    private fun loadProducts(showLoading: Boolean) {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            if (showLoading) {
                renderState(UiState.Loading)
                delay(MOCK_LOAD_DELAY_MS)
            }
            val result = queryProducts()
            renderState(
                if (result.isEmpty()) UiState.Empty else UiState.Success(result)
            )
        }
    }

    private fun queryProducts(): List<Product> {
        val category = selectedCategory ?: return SampleData.products
        return SampleData.products.filter { it.category == category }
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
                if (selectedCategory == null) {
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
                binding.uiState.showError(state.error) { loadProducts(showLoading = true) }
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
        loadJob?.cancel()
        binding.skeletonGroup.stopSkeletonPulse()
        binding.rvProducts.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        /** 模拟异步加载的延迟，接入真实数据源后删除 */
        const val MOCK_LOAD_DELAY_MS = 400L
    }
}
