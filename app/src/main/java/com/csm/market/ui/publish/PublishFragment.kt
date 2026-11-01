package com.csm.market.ui.publish

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.csm.market.R
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition
import com.csm.market.databinding.FragmentPublishBinding
import com.google.android.material.chip.Chip

/**
 * 发布商品页。
 *
 * 本次提交先把表单结构与校验跑通；拍照/相册、压缩、EXIF 脱敏、上传会在
 * 第 6~9 次提交中接入（见 docs/05-开发计划与提交记录.md）。
 */
class PublishFragment : Fragment() {

    private var _binding: FragmentPublishBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPublishBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddPhoto.setOnClickListener {
            toast(getString(R.string.publish_photo_coming_soon))
        }
        binding.btnSubmit.setOnClickListener { submit() }
        binding.tvPricingHint.setOnClickListener {
            toast(getString(R.string.publish_pricing_coming_soon))
        }

        renderCategoryChips()
        renderConditionChips()
    }

    private fun renderCategoryChips() {
        val group = binding.chipGroupCategory
        group.removeAllViews()
        ProductCategory.entries.forEachIndexed { index, category ->
            val chip = Chip(requireContext()).apply {
                text = category.label
                isCheckable = true
                isChecked = index == 0
                setEnsureMinTouchTargetSize(false)
                tag = category
            }
            group.addView(chip)
        }
    }

    private fun renderConditionChips() {
        val group = binding.chipGroupCondition
        group.removeAllViews()
        ProductCondition.entries.forEachIndexed { index, condition ->
            val chip = Chip(requireContext()).apply {
                text = condition.label
                isCheckable = true
                isChecked = index == 2
                setEnsureMinTouchTargetSize(false)
                tag = condition
            }
            group.addView(chip)
        }
    }

    private fun submit() {
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val priceText = binding.etPrice.text?.toString()?.trim().orEmpty()
        val price = priceText.toDoubleOrNull()

        var valid = true
        if (title.isEmpty()) {
            binding.tilTitle.error = getString(R.string.publish_error_title)
            valid = false
        } else {
            binding.tilTitle.error = null
        }

        if (price == null || price <= 0) {
            binding.tilPrice.error = getString(R.string.publish_error_price)
            valid = false
        } else {
            binding.tilPrice.error = null
        }

        if (!valid) return
        toast(getString(R.string.publish_submit_coming_soon))
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
