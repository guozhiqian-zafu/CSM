package com.csm.market.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.csm.market.databinding.FragmentDetailBinding
import com.csm.market.ui.common.hideState
import com.csm.market.ui.common.showError
import com.csm.market.ui.common.showLoading
import com.csm.market.util.formatPrice
import com.csm.market.util.formatRelativeTime
import kotlinx.coroutines.launch

/**
 * 商品详情页。
 *
 * 第 3 次提交接入 ViewModel：商品从仓库按 id 读取，Fragment 只渲染状态。
 * 加载中显示进度条而不是留白，查不到则给出错误态 + 返回入口。
 */
class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailViewModel by viewModels {
        DetailViewModel.factory(requireArguments().getString(ARG_PRODUCT_ID).orEmpty())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnFavorite.setOnClickListener { toast(R.string.feature_coming_soon) }
        binding.btnMessage.setOnClickListener { toast(R.string.feature_coming_soon) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: UiState<Product>) {
        when (state) {
            is UiState.Loading -> {
                binding.contentGroup.isVisible = false
                binding.bottomBar.isVisible = false
                binding.uiState.showLoading()
            }

            is UiState.Success -> {
                binding.uiState.hideState()
                binding.contentGroup.isVisible = true
                binding.bottomBar.isVisible = true
                bindProduct(state.data)
            }

            is UiState.Error -> {
                binding.contentGroup.isVisible = false
                binding.bottomBar.isVisible = false
                // 商品不存在时「重试」没有意义，按钮改为返回列表（navigateUp）
                binding.uiState.showError(state.error) { findNavController().navigateUp() }
            }

            is UiState.Empty -> Unit // 详情页不存在「空列表」语义，理论上不会出现
        }
    }

    private fun bindProduct(product: Product) {
        binding.ivCover.setBackgroundResource(product.coverRes)
        binding.tvTitle.text = product.title
        binding.tvPrice.text = formatPrice(product.price)
        binding.tvOriginalPrice.text = product.originalPrice?.let(::formatPrice).orEmpty()
        binding.tvOriginalPrice.paint.isStrikeThruText = true
        binding.tvCondition.text = product.condition.label
        binding.tvCategory.text = product.category.label
        binding.tvDescription.text = product.description
        binding.tvLocation.text = product.location
        binding.tvTime.text = formatRelativeTime(product.publishedMinutesAgo)
        binding.tvSeller.text = product.sellerName
        binding.tvSellerAvatar.text = product.sellerName.take(1)
    }

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ARG_PRODUCT_ID = "productId"
    }
}
