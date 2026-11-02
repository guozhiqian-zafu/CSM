package com.csm.market.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.csm.market.CsmApp
import com.csm.market.core.error.CsmError
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Product
import com.csm.market.data.repo.FavoriteRepository
import com.csm.market.data.repo.ProductRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * 详情页 ViewModel。
 *
 * 传进来的是 id 而不是整个 [Product]：Fragment 之间只传递「标识」，
 * 真正数据从仓库读。这样做的好处是详情页永远是最新的（别人改了价格也看得到），
 * 而且进程被杀后重建也能恢复。
 *
 * 收藏状态也是订阅来的（`observeIsFavorite`），不在这里手动「点一下改一下」：
 * 收藏页取消收藏后回到详情页，按钮会自动变回「收藏」，不需要页面间通信。
 */
class DetailViewModel(
    private val repository: ProductRepository,
    private val favoriteRepository: FavoriteRepository,
    private val productId: String
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<Product>>(UiState.Loading)
    val state: StateFlow<UiState<Product>> = _state.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    /**
     * 一次性事件：Toast 只该弹一次，用 StateFlow 会在旋转屏幕后重放。
     * `extraBufferCapacity = 1` 让 tryEmit 在没人收集时也不丢事件。
     */
    private val _events = MutableSharedFlow<FavoriteEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FavoriteEvent> = _events.asSharedFlow()

    init {
        load()
        observeFavorite()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching { repository.getProduct(productId) }
                .onSuccess { product ->
                    _state.value = if (product == null) {
                        // 「查不到」是一种确定的业务结果，不是异常
                        UiState.Error(CsmError.NotFound)
                    } else {
                        UiState.Success(product)
                    }
                }
                .onFailure { throwable ->
                    _state.value = UiState.Error(CsmError.from(throwable))
                }
        }
    }

    private fun observeFavorite() {
        viewModelScope.launch {
            favoriteRepository.observeIsFavorite(productId)
                // 收藏表读取失败不该让整个详情页变成错误态：按钮退回「未收藏」即可
                .catch { _isFavorite.value = false }
                .collect { _isFavorite.value = it }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val added = runCatching { favoriteRepository.toggleFavorite(productId) }.getOrNull()
                ?: return@launch
            _events.tryEmit(if (added) FavoriteEvent.Added else FavoriteEvent.Removed)
        }
    }

    enum class FavoriteEvent { Added, Removed }

    companion object {
        fun factory(productId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CsmApp
                DetailViewModel(
                    repository = app.container.productRepository,
                    favoriteRepository = app.container.favoriteRepository,
                    productId = productId
                )
            }
        }
    }
}
