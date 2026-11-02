package com.csm.market.ui.publish

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.csm.market.CsmApp
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition
import com.csm.market.data.repo.NewProduct
import com.csm.market.data.repo.ProductRepository
import com.csm.market.util.ImageCompressor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * 发布页状态机。
 *
 * 只做三件事：维护表单与图片、跑压缩（在 IO 线程）、提交时按「上传→创建」两步走。
 * UI 不碰 Bitmap，也不碰 File 读写——Fragment 只负责把 click 转成方法调用。
 */
class PublishViewModel(
    application: Application,
    private val repository: ProductRepository
) : AndroidViewModel(application) {

    /** 一张已压缩待上传的照片。 */
    data class Photo(
        val file: File,
        val originalWidth: Int,
        val originalHeight: Int,
        val bytes: Int
    )

    data class UiState(
        val category: ProductCategory = ProductCategory.BOOK,
        val condition: ProductCondition = ProductCondition.GOOD,
        val photos: List<Photo> = emptyList(),
        /** 正在压缩选中的图片（相册大图压缩要 200~500ms，需要转圈）。 */
        val compressing: Boolean = false,
        /** 正在提交（上传图片 + 创建商品）。 */
        val submitting: Boolean = false,
        val uploadedCount: Int = 0,
        val uploadTotal: Int = 0,
        val errors: Map<Field, String> = emptyMap(),
        val message: String? = null,
        /** 发布成功后的商品 id，UI 收到后跳回首页并清空表单。 */
        val publishedId: String? = null
    )

    enum class Field { TITLE, PRICE, PHOTO }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun selectCategory(category: ProductCategory) = _state.update { it.copy(category = category) }

    fun selectCondition(condition: ProductCondition) = _state.update { it.copy(condition = condition) }

    /**
     * 接收相册 Uri：先压缩再放进列表。
     *
     * 压缩放在 ViewModel 里而不是 Fragment：转屏时 Fragment 重建，压缩若绑在 Fragment 的
     * lifecycleScope 上会被取消，用户会看到「选完图什么都没发生」。
     */
    fun addPhoto(uri: Uri) {
        if (_state.value.photos.size >= MAX_PHOTOS) {
            _state.update { it.copy(errors = it.errors + (Field.PHOTO to "最多上传 $MAX_PHOTOS 张照片")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(compressing = true) }
            try {
                val result = ImageCompressor.compress(getApplication(), uri, _state.value.photos.size)
                _state.update {
                    it.copy(
                        compressing = false,
                        photos = it.photos + Photo(result.file, result.originalWidth, result.originalHeight, result.bytes),
                        errors = it.errors - Field.PHOTO
                    )
                }
            } catch (error: Exception) {
                // 压缩失败最常见的原因是 Uri 失效（云盘文件、权限被撤），提示重选即可
                _state.update {
                    it.copy(compressing = false, errors = it.errors + (Field.PHOTO to "图片处理失败，换一张试试"))
                }
            }
        }
    }

    fun removePhoto(file: File) {
        file.delete()
        _state.update { it.copy(photos = it.photos.filterNot { photo -> photo.file == file }) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun submit(title: String, priceText: String, originalPriceText: String, description: String, location: String) {
        if (_state.value.submitting || _state.value.compressing) return

        val price = priceText.toDoubleOrNull()
        val originalPrice = originalPriceText.toDoubleOrNull()
        val trimmedTitle = title.trim()

        val errors = buildMap {
            if (trimmedTitle.isEmpty()) put(Field.TITLE, "起个标题，买家才知道你在卖什么")
            if (price == null || price <= 0) put(Field.PRICE, "填一个大于 0 的价格")
            if (price != null && originalPrice != null && originalPrice in 0.0..price) {
                put(Field.PRICE, "原价应该高于售价")
            }
            if (_state.value.photos.isEmpty()) put(Field.PHOTO, "至少放一张实拍图，没有人会买没有图的二手")
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }

        viewModelScope.launch {
            val photos = _state.value.photos
            _state.update {
                it.copy(
                    submitting = true,
                    errors = emptyMap(),
                    uploadedCount = 0,
                    uploadTotal = photos.size
                )
            }
            try {
                val published = repository.publish(
                    product = NewProduct(
                        title = trimmedTitle,
                        price = price!!,
                        originalPrice = originalPrice,
                        category = _state.value.category,
                        condition = _state.value.condition,
                        description = description.trim(),
                        location = location.trim().ifEmpty { "校园内" }
                    ),
                    images = photos.map { it.file },
                    onProgress = { done, total ->
                        _state.update { it.copy(uploadedCount = done, uploadTotal = total) }
                    }
                )
                photos.forEach { it.file.delete() }
                _state.update {
                    it.copy(
                        submitting = false,
                        photos = emptyList(),
                        publishedId = published.id,
                        message = "发布成功，去首页看看吧"
                    )
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update {
                    it.copy(
                        submitting = false,
                        errors = it.errors + (Field.PHOTO to (error.message ?: "发布失败，请检查网络后重试"))
                    )
                }
            }
        }
    }

    fun consumePublished() = _state.update { it.copy(publishedId = null) }

    override fun onCleared() {
        // 退出页面时清掉没上传成功的临时文件，避免 cache 目录越积越大
        _state.value.photos.forEach { it.file.delete() }
        super.onCleared()
    }

    companion object {
        const val MAX_PHOTOS = 4

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CsmApp
                PublishViewModel(app, app.container.productRepository)
            }
        }
    }
}
