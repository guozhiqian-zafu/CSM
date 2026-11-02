package com.csm.market.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.csm.market.R
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Product
import com.csm.market.databinding.FragmentFavoritesBinding
import com.csm.market.ui.common.hideState
import com.csm.market.ui.common.showEmpty
import com.csm.market.ui.common.showError
import com.csm.market.ui.common.showLoading
import com.csm.market.ui.home.ProductAdapter
import kotlinx.coroutines.launch

/**
 * 收藏页。
 *
 * 数据来自 `favorites` 表（Room Flow），四态齐全：加载 / 空 / 错误 / 列表。
 * 空态带「去逛逛」按钮，直接把用户带回首页，而不是让他自己找入口。
 */
class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FavoritesViewModel by viewModels { FavoritesViewModel.Factory }

    private val adapter = ProductAdapter { product -> openDetail(product) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvFavorites.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: UiState<List<Product>>) {
        val hasData = state is UiState.Success && state.data.isNotEmpty()
        binding.rvFavorites.isVisible = hasData

        when (state) {
            is UiState.Loading -> binding.uiState.showLoading()

            is UiState.Empty -> binding.uiState.showEmpty(
                titleRes = R.string.favorites_empty_title,
                descRes = R.string.favorites_empty_desc,
                actionRes = R.string.favorites_go_shopping,
                onAction = { findNavController().navigate(R.id.homeFragment) }
            )

            is UiState.Error -> binding.uiState.showError(state.error) { viewModel.retry() }

            is UiState.Success -> {
                binding.uiState.hideState()
                adapter.submitList(state.data)
            }
        }
    }

    private fun openDetail(product: Product) {
        findNavController().navigate(
            R.id.action_favorites_to_detail,
            bundleOf(ARG_PRODUCT_ID to product.id)
        )
    }

    override fun onDestroyView() {
        binding.rvFavorites.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ARG_PRODUCT_ID = "productId"
    }
}
