package com.csm.market.ui.favorites

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 收藏页 ViewModel。
 *
 * 列表直接来自 `favorites` 表的 Flow：在详情页点收藏之后切回收藏页，
 * 不需要手动刷新——数据库变了，UI 跟着变。
 */
class FavoritesViewModel(
    private val repository: FavoriteRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Product>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Product>>> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeFavorites()
    }

    /**
     * 重新订阅收藏表。
     *
     * 之所以要「重新订阅」而不是简单重读一次：Room 的 Flow 抛出异常后已经终止，
     * 旧流不会再发出任何数据，只有 cancel + 重新 collect 才能恢复。
     */
    fun retry() {
        observeFavorites()
    }

    private fun observeFavorites() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _state.value = UiState.Loading
            repository.observeFavorites()
                .map<List<Product>, UiState<List<Product>>> { products ->
                    if (products.isEmpty()) UiState.Empty else UiState.Success(products)
                }
                .catch { throwable -> _state.value = UiState.Error(CsmError.from(throwable)) }
                .collect { _state.value = it }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CsmApp
                FavoritesViewModel(app.container.favoriteRepository)
            }
        }
    }
}
