package com.csm.market.ui.mylistings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.csm.market.CsmApp
import com.csm.market.R
import com.csm.market.core.error.CsmError
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Product
import com.csm.market.data.repo.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「我的发布」ViewModel。
 *
 * 和首页不同，这里不做「网络优先 + 缓存兜底」：下架操作必须打到服务端，
 * 展示一份可能已经过期的本地缓存会让人以为下架没生效。所以失败就进错误态，
 * 由用户主动重试。
 */
class MyListingsViewModel(
    private val repository: ProductRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Product>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Product>>> = _state.asStateFlow()

    /** 一次性提示（下架成功/失败），由界面消费后清空。 */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** 长按「下架」：确认弹窗由界面负责，这里只执行。 */
    fun takeDown(product: Product) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                repository.takeDown(product.id)
                _message.value = R.string.my_listings_taken_down
                load()
            } catch (error: Throwable) {
                // 取消（例如用户退出页面）不是错误，原样抛出让协程正常结束
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.value = UiState.Error(CsmError.from(error))
                _message.value = R.string.my_listings_take_down_failed
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        _state.value = UiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val mine = repository.myListings()
                _state.value = if (mine.isEmpty()) UiState.Empty else UiState.Success(mine)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.value = UiState.Error(CsmError.from(error))
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CsmApp
                MyListingsViewModel(app.container.productRepository)
            }
        }
    }
}
