package com.csm.market.data.repo

import com.csm.market.data.local.ProductDao
import com.csm.market.data.local.toDomain
import com.csm.market.data.local.toEntity
import com.csm.market.data.model.PriceStats
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductQuery
import com.csm.market.data.remote.ProductRemoteDataSource
import com.csm.market.data.remote.dto.CreateProductRequest
import com.csm.market.data.remote.dto.toDomain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 「网络优先 + Room 缓存」仓库。
 *
 * 一次读取分两步：
 * 1. **拉**：请求服务端，成功后写入 Room（顺便删掉服务端已经没有的商品）；
 * 2. **看**：只观察 Room 的 Flow，UI 拿到的永远是数据库里的数据。
 *
 * 为什么不把网络结果直接返回给 UI：数据库是唯一数据源（single source of truth），
 * 列表、详情、桌面小组件读同一份数据，才不会出现「列表有新商品、详情页却打不开」。
 *
 * 失败策略是本类的核心：
 * - 网络失败 + 有缓存 → 展示缓存 + `isOffline = true`（UI 顶部出现离线横幅）；
 * - 网络失败 + 无缓存 → 把异常抛出去，页面显示错误态与「重试」；
 * - 取消异常必须原样抛出，否则会被误判成「网络错误」而吞掉协程取消。
 */
class DefaultProductRepository(
    private val dao: ProductDao,
    private val remote: ProductRemoteDataSource,
    private val clock: () -> Long = System::currentTimeMillis
) : ProductRepository {

    private val _isOffline = MutableStateFlow(false)
    override val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    /** 同一时刻只允许一次刷新，避免首页 + 收藏页同时到达时打两次接口。 */
    private val refreshMutex = Mutex()

    override fun observeProducts(category: ProductCategory?): Flow<List<Product>> = flow {
        refreshCache(category)
        val source = if (category == null) {
            dao.observeAll()
        } else {
            dao.observeByCategory(category.name)
        }
        emitAll(source.map { entities -> entities.map { it.toDomain(clock()) } })
    }

    override suspend fun getProduct(id: String): Product? {
        return try {
            val fresh = remote.fetchProduct(id)
            _isOffline.value = false
            if (fresh == null) {
                // 服务端明确说「没有这个商品」，不是失败，不要回退缓存把自己骗了
                null
            } else {
                dao.upsert(fresh.toEntity(clock()))
                fresh
            }
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            val cached = dao.findById(id)?.toDomain(clock()) ?: throw error
            _isOffline.value = true
            cached
        }
    }

    override suspend fun saveProduct(product: Product) {
        dao.upsert(product.toEntity(clock()))
    }

    override suspend fun search(query: ProductQuery): List<Product> {
        val results = remote.searchProducts(query)
        if (results.isNotEmpty()) {
            val now = clock()
            dao.upsertAll(results.map { it.toEntity(now) })
        }
        _isOffline.value = false
        return results
    }

    override suspend fun countProducts(): Int = dao.count()

    override suspend fun myListings(): List<Product> {
        val mine = remote.fetchMyListings()
        _isOffline.value = false
        return mine
    }

    override suspend fun takeDown(id: String) {
        // 先让服务端下架，成功后再删本地缓存：反过来的话服务端失败，
        // 本地却已经看不到了，下次刷新又「复活」，用户会以为下架没生效
        remote.takeDownProduct(id)
        dao.deleteById(id)
    }

    override suspend fun publish(
        product: NewProduct,
        images: List<java.io.File>,
        onProgress: (done: Int, total: Int) -> Unit
    ): Product {
        // 先传图再建商品：商品一旦创建就出现在列表里，图片还没传完会让买家看到空壳
        val total = images.size
        onProgress(0, total)
        val paths = images.mapIndexed { index, file ->
            val path = remote.uploadImage(file)
            onProgress(index + 1, total)
            path
        }
        val created = remote.publishProduct(
            CreateProductRequest(
                title = product.title,
                price = product.price,
                originalPrice = product.originalPrice,
                category = product.category.name,
                condition = product.condition.name,
                description = product.description,
                location = product.location,
                sellerName = product.sellerName,
                images = paths
            )
        )
        dao.upsert(created.toEntity(clock()))
        _isOffline.value = false
        return created
    }

    override suspend fun priceStats(category: ProductCategory?): PriceStats? {
        val resolved = category ?: return null
        return try {
            val dto = remote.fetchPriceStats(resolved)
            dto.toDomain(resolved)
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            // 统计失败不该影响发布：返回 null，调用方用分类基准价兜底
            null
        }
    }

    private suspend fun refreshCache(category: ProductCategory?) = refreshMutex.withLock {
        try {
            val fresh = remote.fetchProducts(category)
            if (fresh.isNotEmpty()) {
                val now = clock()
                dao.upsertAll(fresh.map { it.toEntity(now) })
                // 只在「全量刷新」时清理本地多余数据：按分类拉取时不能删别的分类
                if (category == null) {
                    dao.deleteMissing(fresh.map { it.id })
                }
            }
            _isOffline.value = false
        } catch (error: Throwable) {
            error.rethrowIfCancellation()
            // 有缓存就降级展示，没缓存才让页面进错误态
            if (dao.count() == 0) throw error
            _isOffline.value = true
        }
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) throw this
    }
}
