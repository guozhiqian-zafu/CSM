package com.csm.market.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.csm.market.CsmApp
import com.csm.market.R
import com.csm.market.core.error.CsmError
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.CampusLocations
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.repo.ProductRepository
import com.csm.market.util.GeoUtils
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

    /**
     * 离线状态直接转发仓库的 Flow。
     *
     * 不在这里再存一份 Boolean：仓库是唯一的判定方（它才知道这次请求到底成功没），
     * ViewModel 只做转发，避免两处状态不同步。
     */
    val isOffline: StateFlow<Boolean> = repository.isOffline

    /** 只保留最后一次订阅，切分类时取消上一条，避免旧结果覆盖新结果。 */
    private var loadJob: Job? = null

    /** 是否开启了「附近好物」排序。 */
    private val _nearbyEnabled = MutableStateFlow(false)
    val nearbyEnabled: StateFlow<Boolean> = _nearbyEnabled.asStateFlow()

    /** 当前定位；为 null 表示没权限/定位关闭，此时附近排序不可用。 */
    private val _userLocation = MutableStateFlow<CampusLocations.LatLng?>(null)

    /** 商品 id → 距离（米）。列表据此显示「距你 320 米」并按由近到远排序。 */
    private val _distances = MutableStateFlow<Map<String, Double>>(emptyMap())
    val distances: StateFlow<Map<String, Double>> = _distances.asStateFlow()

    /** 一次性提示（定位不可用等），由 Fragment 弹一次就消费掉。 */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * 拿到（或拿不到）定位后调用。
     *
     * 拿不到时不仅仅是「显示远一点」，而是明确告诉用户附近排序用不了：
     * 否则用户会以为系统按距离排了，其实只是原始顺序。
     */
    fun onLocationResolved(location: CampusLocations.LatLng?) {
        _userLocation.value = location
        if (location == null) {
            _nearbyEnabled.value = false
            _distances.value = emptyMap()
            _message.value = R.string.nearby_unavailable
        } else if (_nearbyEnabled.value) {
            load(showSkeleton = false)
        }
    }

    /** 开启/关闭附近排序；没有定位时直接拒绝并提示。 */
    fun setNearbyEnabled(enabled: Boolean) {
        if (enabled && _userLocation.value == null) {
            _message.value = R.string.nearby_need_permission
            return
        }
        if (_nearbyEnabled.value == enabled) return
        _nearbyEnabled.value = enabled
        load(showSkeleton = false)
    }

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
                    val prepared = applyNearby(products)
                    _state.value = if (prepared.isEmpty()) {
                        UiState.Empty
                    } else {
                        UiState.Success(prepared)
                    }
                }
        }
    }

    private fun observeProducts(category: ProductCategory?): Flow<List<Product>> =
        repository.observeProducts(category)

    /**
     * 附近排序：给每个商品算一次距离，按远近重排。
     *
     * 距离用「校园地点表 → 坐标 → Haversine」，解析不出地名的商品排到最后（distance 为 null）。
     * 关闭附近排序时清空距离表，列表恢复成服务端给的顺序。
     */
    private fun applyNearby(products: List<Product>): List<Product> {
        val origin = _userLocation.value
        if (!_nearbyEnabled.value || origin == null) {
            _distances.value = emptyMap()
            return products
        }
        val computed = products.associate { product ->
            product.id to (CampusLocations.resolve(product.location)
                ?.let { GeoUtils.distanceMeters(origin, it) } ?: Double.MAX_VALUE)
        }
        _distances.value = computed.filterValues { it != Double.MAX_VALUE }
        return products.sortedBy { computed[it.id] ?: Double.MAX_VALUE }
    }

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
