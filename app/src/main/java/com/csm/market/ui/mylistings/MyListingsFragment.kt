package com.csm.market.ui.mylistings

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
import com.csm.market.R
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Product
import com.csm.market.databinding.FragmentMyListingsBinding
import com.csm.market.ui.common.hideState
import com.csm.market.ui.common.showEmpty
import com.csm.market.ui.common.showError
import com.csm.market.ui.common.showLoading
import com.csm.market.ui.home.ProductAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 我的发布。
 *
 * 点卡片进详情，长按弹出「下架」确认——列表页直接给删除按钮容易误触，
 * 长按 + 二次确认是二手交易类 App 的常见做法。
 */
class MyListingsFragment : Fragment() {

    private var _binding: FragmentMyListingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyListingsViewModel by viewModels { MyListingsViewModel.Factory }

    private val adapter = ProductAdapter(
        onItemClick = { product -> openDetail(product) },
        onItemLongClick = { product -> confirmTakeDown(product) }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyListingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rvMyListings.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { state -> renderState(state) } }
                launch {
                    viewModel.message.collect { messageRes ->
                        if (messageRes != null) {
                            Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
                            viewModel.consumeMessage()
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: UiState<List<Product>>) {
        val hasData = state is UiState.Success && state.data.isNotEmpty()
        binding.rvMyListings.isVisible = hasData
        binding.tvHint.isVisible = hasData

        when (state) {
            is UiState.Loading -> binding.uiState.showLoading()

            is UiState.Empty -> binding.uiState.showEmpty(
                titleRes = R.string.my_listings_empty_title,
                descRes = R.string.my_listings_empty_desc,
                actionRes = R.string.my_listings_go_publish,
                onAction = { findNavController().navigate(R.id.publishFragment) }
            )

            is UiState.Error -> binding.uiState.showError(state.error) { viewModel.retry() }

            is UiState.Success -> {
                binding.uiState.hideState()
                adapter.submitList(state.data)
            }
        }
    }

    private fun confirmTakeDown(product: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_listings_take_down_title)
            .setMessage(getString(R.string.my_listings_take_down_message, product.title))
            .setNegativeButton(R.string.my_listings_cancel, null)
            .setPositiveButton(R.string.my_listings_take_down_confirm) { _, _ ->
                viewModel.takeDown(product)
            }
            .show()
    }

    private fun openDetail(product: Product) {
        findNavController().navigate(
            R.id.action_my_listings_to_detail,
            bundleOf(ARG_PRODUCT_ID to product.id)
        )
    }

    override fun onDestroyView() {
        binding.rvMyListings.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ARG_PRODUCT_ID = "productId"
    }
}
