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
import com.csm.market.data.repo.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 详情页 ViewModel。
 *
 * 传进来的是 id 而不是整个 [Product]：Fragment 之间只传递「标识」，
 * 真正数据从仓库读。这样做的好处是详情页永远是最新的（别人改了价格也看得到），
 * 而且进程被杀后重建也能恢复。
 */
class DetailViewModel(
    private val repository: ProductRepository,
    private val productId: String
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<Product>>(UiState.Loading)
    val state: StateFlow<UiState<Product>> = _state.asStateFlow()

    init {
        load()
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

    companion object {
        fun factory(productId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CsmApp
                DetailViewModel(app.container.productRepository, productId)
            }
        }
    }
}
