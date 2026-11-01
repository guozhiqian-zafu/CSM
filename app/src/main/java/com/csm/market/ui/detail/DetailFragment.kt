package com.csm.market.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.csm.market.R
import com.csm.market.data.sample.SampleData
import com.csm.market.databinding.FragmentDetailBinding
import com.csm.market.util.formatPrice
import com.csm.market.util.formatRelativeTime

/**
 * 商品详情页。
 *
 * 目前根据 productId 从样例数据里取；接入数据库后改为 ViewModel + 单一数据源，
 * 页面结构（图片、价格、成色、描述、卖家、底部操作栏）保持不变。
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

        val productId = arguments?.getString("productId")
        val product = productId?.let(SampleData::findById)
        if (product == null) {
            showMissingProduct()
            return
        }

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

    private fun showMissingProduct() {
        binding.contentGroup.visibility = View.GONE
        binding.btnFavorite.isEnabled = false
        binding.btnMessage.isEnabled = false
        toast(R.string.detail_not_found)
    }

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
