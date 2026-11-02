package com.csm.market.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.csm.market.CsmApp
import com.csm.market.core.error.CsmError
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductQuery
import com.csm.market.data.model.ProductSort
import com.csm.market.data.repo.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 搜索页 ViewModel。
 *
 * 两个关键点：
 * 1. **防抖**：每敲一个字就发请求会把服务端打满，也用不上「边打边看结果」的体验。
 *    `debounce(350)` 让用户停手 350ms 才发一次，配合 `collectLatest`，新输入到来时
 *    上一个还没返回的请求会被取消，不会出现「旧结果后到、把新结果覆盖」。
 * 2. **输入为空就清空结果**：不请求、也不报错，直接回到「输入关键词」的引导态。
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: ProductRepository
) : ViewModel() {

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    private val _sort = MutableStateFlow(ProductSort.LATEST)
    val sort: StateFlow<ProductSort> = _sort.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<Product>>>(UiState.Empty)
    val state: StateFlow<UiState<List<Product>>> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _keyword
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { keyword -> search(keyword) }
        }
        viewModelScope.launch {
            _sort
                .drop(1) // 初始值由上面那条流触发，避免页面一打开就搜两次
                .distinctUntilChanged()
                .collectLatest { search(_keyword.value) }
        }
    }

    fun onKeywordChange(text: String) {
        _keyword.value = text
    }

    fun onSortChange(sort: ProductSort) {
        if (_sort.value == sort) return
        _sort.value = sort
    }

    /** 错误态点「重试」：跳过防抖立即重发。 */
    fun retry() {
        viewModelScope.launch { search(_keyword.value) }
    }

    private suspend fun search(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            _state.value = UiState.Empty
            return
        }
        _state.value = UiState.Loading
        try {
            val results = repository.search(
                ProductQuery(keyword = trimmed, sort = _sort.value)
            )
            _state.value = if (results.isEmpty()) UiState.Empty else UiState.Success(results)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _state.value = UiState.Error(CsmError.from(error))
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 350L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CsmApp
                SearchViewModel(app.container.productRepository)
            }
        }
    }
}
