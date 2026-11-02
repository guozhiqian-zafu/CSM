package com.csm.market.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import com.csm.market.data.model.ProductSort
import com.csm.market.databinding.FragmentSearchBinding
import com.csm.market.ui.common.hideState
import com.csm.market.ui.common.showEmpty
import com.csm.market.ui.common.showError
import com.csm.market.ui.common.showLoading
import com.csm.market.ui.home.ProductAdapter
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * 搜索页。
 *
 * 只做三件事：把输入框内容交给 ViewModel、把排序选择交给 ViewModel、把状态画出来。
 * 防抖与取消都在 ViewModel 里，Fragment 不持有 Job，也不做网络请求。
 */
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels { SearchViewModel.Factory }

    private val adapter = ProductAdapter { product -> openDetail(product) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rvSearch.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearch.adapter = adapter

        binding.etSearch.doAfterTextChanged { text ->
            viewModel.onKeywordChange(text?.toString().orEmpty())
        }

        setupSortChips()

        binding.uiState.btnStateRetry.setOnClickListener { viewModel.retry() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }
    }

    private fun setupSortChips() {
        val group = binding.chipGroupSort
        group.removeAllViews()
        ProductSort.entries.forEach { sort ->
            val chip = Chip(requireContext()).apply {
                text = sort.label
                isCheckable = true
                isChecked = sort == ProductSort.LATEST
                tag = sort
                setEnsureMinTouchTargetSize(false)
                chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke)
            }
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: View.NO_ID)
            (chip?.tag as? ProductSort)?.let(viewModel::onSortChange)
        }
    }

    private fun renderState(state: UiState<List<Product>>) {
        when (state) {
            is UiState.Loading -> {
                binding.rvSearch.isVisible = false
                binding.uiState.showLoading()
            }

            is UiState.Success -> {
                binding.uiState.hideState()
                binding.rvSearch.isVisible = true
                adapter.submitList(state.data)
            }

            is UiState.Empty -> {
                binding.rvSearch.isVisible = false
                // 没输入关键词和「搜了没结果」是两件事，提示要分开
                if (viewModel.keyword.value.isBlank()) {
                    binding.uiState.showEmpty(
                        R.string.search_prompt_title,
                        R.string.search_prompt_desc
                    )
                } else {
                    binding.uiState.showEmpty(
                        R.string.search_empty_title,
                        R.string.search_empty_desc
                    )
                }
            }

            is UiState.Error -> {
                binding.rvSearch.isVisible = false
                binding.uiState.showError(state.error) { viewModel.retry() }
            }
        }
    }

    private fun openDetail(product: Product) {
        findNavController().navigate(
            R.id.action_search_to_detail,
            bundleOf("productId" to product.id)
        )
    }

    override fun onDestroyView() {
        binding.rvSearch.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
