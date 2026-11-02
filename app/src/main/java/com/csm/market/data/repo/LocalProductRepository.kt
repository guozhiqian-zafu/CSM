package com.csm.market.data.repo

import com.csm.market.data.local.ProductDao
import com.csm.market.data.local.toDomain
import com.csm.market.data.local.toEntity
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.sample.SampleData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 本地仓库实现：数据全部来自 Room。
 *
 * 关于「种子数据」：首次启动时表是空的，页面会先闪一下空态再出现内容，体验不好。
 * 这里在第一次读取前检查 `count()==0` 就灌入样例数据，保证空态只在
 * 「真的没有商品」时出现。
 *
 * [clock] 注入是为了单元测试固定时间；生产代码用默认值即可。
 */
class LocalProductRepository(
    private val dao: ProductDao,
    private val seedProducts: List<Product> = SampleData.products,
    private val clock: () -> Long = System::currentTimeMillis
) : ProductRepository {

    /** 并发调用 observeProducts 时，避免两个协程同时判断「表是空的」而重复插入。 */
    private val seedMutex = Mutex()

    override fun observeProducts(category: ProductCategory?): Flow<List<Product>> = flow {
        ensureSeeded()
        val source = if (category == null) {
            dao.observeAll()
        } else {
            dao.observeByCategory(category.name)
        }
        emitAll(source.map { entities -> entities.map { it.toDomain(clock()) } })
    }

    override suspend fun getProduct(id: String): Product? =
        dao.findById(id)?.toDomain(clock())

    override suspend fun saveProduct(product: Product) {
        dao.upsert(product.toEntity(clock()))
    }

    override suspend fun countProducts(): Int = dao.count()

    private suspend fun ensureSeeded() = seedMutex.withLock {
        if (dao.count() > 0) return@withLock
        val now = clock()
        dao.upsertAll(seedProducts.map { it.toEntity(now) })
    }
}
