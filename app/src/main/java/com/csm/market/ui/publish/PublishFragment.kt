package com.csm.market.ui.publish

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.csm.market.R
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition
import com.csm.market.databinding.FragmentPublishBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 发布商品页。
 *
 * 与第 1 次提交的差别：图片不再是假入口，走「相册选图 → 压缩 → 上传 → 创建商品」的完整链路；
 * 表单校验、按钮禁用、上传进度都由 [PublishViewModel] 的状态驱动，Fragment 不做业务判断。
 */
class PublishFragment : Fragment() {

    private var _binding: FragmentPublishBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PublishViewModel by viewModels { PublishViewModel.Factory }

    /**
     * 系统相册选择器（Photo Picker）。
     *
     * 用 PickVisualMedia 而不是 ACTION_GET_CONTENT：Android 13+ 不需要任何存储权限，
     * 低版本由 androidx 转发到兼容实现，所以这个功能在 minSdk 24 上也能跑。
     */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.addPhoto(uri)
    }

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
            pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        binding.btnSubmit.setOnClickListener {
            viewModel.submit(
                title = binding.etTitle.text?.toString().orEmpty(),
                priceText = binding.etPrice.text?.toString().orEmpty(),
                originalPriceText = "",
                description = binding.etDescription.text?.toString().orEmpty(),
                location = ""
            )
        }
        binding.tvPricingHint.setOnClickListener {
            toast(getString(R.string.publish_pricing_hint_coming_soon))
        }

        setupCategoryChips()
        setupConditionChips()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }
    }

    private fun setupCategoryChips() {
        val group = binding.chipGroupCategory
        group.removeAllViews()
        ProductCategory.entries.forEachIndexed { index, category ->
            group.addView(
                Chip(requireContext()).apply {
                    text = category.label
                    isCheckable = true
                    isChecked = index == 0
                    tag = category
                    setEnsureMinTouchTargetSize(false)
                    chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke)
                }
            )
        }
        group.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: View.NO_ID)
            (chip?.tag as? ProductCategory)?.let(viewModel::selectCategory)
        }
    }

    private fun setupConditionChips() {
        val group = binding.chipGroupCondition
        group.removeAllViews()
        ProductCondition.entries.forEachIndexed { index, condition ->
            group.addView(
                Chip(requireContext()).apply {
                    text = condition.label
                    isCheckable = true
                    isChecked = index == 2
                    tag = condition
                    setEnsureMinTouchTargetSize(false)
                    chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke)
                }
            )
        }
        group.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = group.findViewById<Chip>(checkedIds.firstOrNull() ?: View.NO_ID)
            (chip?.tag as? ProductCondition)?.let(viewModel::selectCondition)
        }
    }

    private fun render(state: PublishViewModel.UiState) {
        binding.pbCompressing.isVisible = state.compressing
        binding.btnSubmit.isEnabled = !state.submitting && !state.compressing
        binding.etTitle.isEnabled = !state.submitting
        binding.etPrice.isEnabled = !state.submitting

        binding.btnSubmit.text = when {
            state.submitting && state.uploadTotal > 0 ->
                getString(R.string.publish_uploading, state.uploadedCount, state.uploadTotal)

            state.submitting -> getString(R.string.publish_submitting)
            else -> getString(R.string.publish_submit)
        }

        binding.tilTitle.error = state.errors[PublishViewModel.Field.TITLE]
        binding.tilPrice.error = state.errors[PublishViewModel.Field.PRICE]
        binding.tvPhotoHint.text = state.errors[PublishViewModel.Field.PHOTO]
            ?: getString(R.string.publish_photo_sub)
        binding.tvPhotoHint.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                requireContext(),
                if (state.errors.containsKey(PublishViewModel.Field.PHOTO)) R.color.price_red else R.color.text_tertiary
            )
        )

        renderPhotos(state)

        state.message?.let { message ->
            toast(message)
            viewModel.consumeMessage()
        }
        state.publishedId?.let {
            viewModel.consumePublished()
            clearForm()
            // 发布成功后回首页：用户能看到自己刚发的商品排在第一条
            findNavController().navigate(R.id.homeFragment)
        }
    }

    /** 缩略图列表整体重建：最多 4 张，重建成本可以忽略，比做局部增删更好维护。 */
    private fun renderPhotos(state: PublishViewModel.UiState) {
        val strip = binding.photoStrip
        // 只移除缩略图，保留末尾的「添加」按钮
        while (strip.childCount > 1) {
            strip.removeViewAt(0)
        }
        state.photos.forEach { photo ->
            val thumb = layoutInflater.inflate(R.layout.item_photo_thumb, strip, false) as View
            thumb.findViewById<ImageView>(R.id.iv_photo).apply {
                // Coil 直接加载本地 File，内部会按 ImageView 尺寸降采样
                load(photo.file) { crossfade(true) }
            }
            thumb.findViewById<View>(R.id.btn_remove).setOnClickListener {
                viewModel.removePhoto(photo.file)
            }
            strip.addView(thumb, strip.childCount - 1, thumbLayoutParams())
        }
        binding.btnAddPhoto.isVisible = state.photos.size < PublishViewModel.MAX_PHOTOS
    }

    private fun thumbLayoutParams() = LinearLayout.LayoutParams(
        resources.getDimensionPixelSize(R.dimen.photo_thumb_size),
        resources.getDimensionPixelSize(R.dimen.photo_thumb_size)
    ).apply {
        marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_s)
    }

    private fun clearForm() {
        binding.etTitle.setText("")
        binding.etPrice.setText("")
        binding.etDescription.setText("")
        binding.tilTitle.error = null
        binding.tilPrice.error = null
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
