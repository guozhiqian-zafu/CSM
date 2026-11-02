package com.csm.market.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.csm.market.R
import com.csm.market.core.error.CsmError
import com.csm.market.data.sample.SampleData
import com.csm.market.databinding.FragmentDetailBinding
import com.csm.market.ui.common.hideState
import com.csm.market.ui.common.showError
import com.csm.market.util.formatPrice
import com.csm.market.util.formatRelativeTime

/**
 * 商品详情页。
 *
 * 第 2 次提交补上错误态：商品不存在时不再只是「Toast + 一片空白」，
 * 而是给出说明、隐藏底部操作栏，并允许返回列表。
 * 数据仍来自样例数据，第 3 次提交改为 ViewModel + 单一数据源。
 */
class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

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

        val productId = arguments?.getString(ARG_PRODUCT_ID)
        val product = productId?.let(SampleData::findById)
        if (product == null) {
            showMissingProduct()
            return
        }

        binding.uiState.hideState()
        binding.contentGroup.isVisible = true
        binding.bottomBar.isVisible = true

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

        binding.btnFavorite.setOnClickListener { toast(R.string.feature_coming_soon) }
        binding.btnMessage.setOnClickListener { toast(R.string.feature_coming_soon) }
    }

    /**
     * 商品不存在的完整降级：隐藏正文与操作栏，展示统一错误态。
     * 错误态里的按钮文案是「重试」，这里的语义是「返回列表」，所以直接把点击行为指回上一页。
     */
    private fun showMissingProduct() {
        binding.contentGroup.isVisible = false
        binding.bottomBar.isVisible = false
        binding.uiState.showError(CsmError.NotFound) {
            findNavController().navigateUp()
        }
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
