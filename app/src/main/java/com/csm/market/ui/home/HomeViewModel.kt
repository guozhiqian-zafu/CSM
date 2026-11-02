package com.csm.market.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.csm.market.CsmApp
import com.csm.market.core.error.CsmError
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.repo.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * 首页 ViewModel。
 *
 * 职责边界：
 * - 持有「当前分类」和「列表状态」，Fragment 只负责把状态画出来；
 * - 数据来自 [ProductRepository] 的 Flow，Room 一旦有变化会自动推送过来，
 *   所以「发布商品后列表自动刷新」不需要任何手动回调；
 * - 协程挂在 `viewModelScope`：页面销毁、配置变更（旋转）都不会泄漏，
 *   旋转时 ViewModel 存活、数据不用重新加载。
 */
class HomeViewModel(
    private val repository: ProductRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<ProductCategory?>(null)
    val selectedCategory: StateFlow<ProductCategory?> = _selectedCategory.asStateFlow()

    private val _state = MutableStateFlow<UiState<List<Product>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Product>>> = _state.asStateFlow()

    /** 只保留最后一次订阅，切分类时取消上一条，避免旧结果覆盖新结果。 */
    private var loadJob: Job? = null

    init {
        load(showSkeleton = true)
    }

    /** 切换分类；重复点同一个分类直接忽略，避免无意义的重新订阅。 */
    fun selectCategory(category: ProductCategory?) {
        if (_selectedCategory.value == category) return
        _selectedCategory.value = category
        // 切分类不重走骨架屏：数据就在本地，闪一下反而更像卡顿
        load(showSkeleton = false)
    }

    /** 错误态点「重试」。 */
    fun retry() = load(showSkeleton = true)

    private fun load(showSkeleton: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (showSkeleton) {
                _state.value = UiState.Loading
            }
            observeProducts(_selectedCategory.value)
                .catch { throwable ->
                    _state.value = UiState.Error(CsmError.from(throwable))
                }
                .collect { products ->
                    _state.value = if (products.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Success(products)
                    }
                }
        }
    }

    private fun observeProducts(category: ProductCategory?): Flow<List<Product>> =
        repository.observeProducts(category)

    companion object {
        /**
         * ViewModel 工厂：Fragment 用 `by viewModels { HomeViewModel.Factory }` 取实例。
         * 依赖从 Application 的容器里拿，测试时可以直接 `HomeViewModel(fakeRepo)` 构造。
         */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CsmApp
                HomeViewModel(app.container.productRepository)
            }
        }
    }
}
