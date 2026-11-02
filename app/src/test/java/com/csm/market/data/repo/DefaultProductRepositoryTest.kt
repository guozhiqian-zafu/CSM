package com.csm.market.data.repo

import com.csm.market.data.local.ProductDao
import com.csm.market.data.local.ProductEntity
import com.csm.market.data.local.toEntity
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition
import com.csm.market.data.model.ProductQuery
import java.io.File
import com.csm.market.data.remote.ProductRemoteDataSource
import com.csm.market.data.remote.dto.CreateProductRequest
import com.csm.market.data.remote.dto.PriceStatsDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * [DefaultProductRepository] 的离线降级测试。
 *
 * 这段逻辑是「网络 + 缓存」里最容易出错的部分，也是答辩时最容易被追问的：
 * 断网时到底显示缓存还是错误页？服务端说「没有这个商品」时能不能走缓存？
 * 用假 DAO + 假远端把三种分支都钉死，不需要模拟器。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultProductRepositoryTest {

    private val book = product("b1", ProductCategory.BOOK, price = 20.0)
    private val phone = product("d1", ProductCategory.DIGITAL, price = 800.0)

    @Test
    fun `网络成功时写入缓存并结束离线态`() = runTest {
        val dao = FakeProductDao()
        val remote = FakeRemote(products = listOf(book, phone))
        val repository = DefaultProductRepository(dao, remote)

        val emitted = repository.observeProducts().first()

        assertEquals(setOf("b1", "d1"), emitted.map { it.id }.toSet())
        assertEquals(2, dao.count())
        assertFalse(repository.isOffline.value)
    }

    @Test
    fun `网络失败但有缓存时返回缓存并标记离线`() = runTest {
        val dao = FakeProductDao(listOf(book.toEntity(now = 0L)))
        val remote = FakeRemote(failure = IOException("no network"))
        val repository = DefaultProductRepository(dao, remote)

        val emitted = repository.observeProducts().first()

        assertEquals(listOf("b1"), emitted.map { it.id })
        assertTrue("有缓存时应该进入离线态", repository.isOffline.value)
    }

    @Test
    fun `网络失败且无缓存时抛异常让页面显示错误态`() = runTest {
        val dao = FakeProductDao()
        val remote = FakeRemote(failure = IOException("no network"))
        val repository = DefaultProductRepository(dao, remote)

        val error = runCatching { repository.observeProducts().first() }.exceptionOrNull()

        assertTrue("期望 IOException，实际 $error", error is IOException)
    }

    @Test
    fun `全量刷新会清理服务端已下架的商品`() = runTest {
        val stale = product("gone", ProductCategory.OTHER, price = 5.0)
        val dao = FakeProductDao(listOf(stale.toEntity(now = 0L)))
        val remote = FakeRemote(products = listOf(book))
        val repository = DefaultProductRepository(dao, remote)

        repository.observeProducts().first()

        assertNull("缓存里不在服务端列表中的商品应被删除", dao.findById("gone"))
        assertEquals(1, dao.count())
    }

    @Test
    fun `详情请求 404 时返回 null 而不是回退缓存`() = runTest {
        val dao = FakeProductDao(listOf(book.toEntity(now = 0L)))
        val remote = FakeRemote(singleResult = null)
        val repository = DefaultProductRepository(dao, remote)

        val result = repository.getProduct("b1")

        assertNull("服务端明确说不存在时必须返回 null，否则会展示已下架商品", result)
    }

    private fun product(id: String, category: ProductCategory, price: Double): Product = Product(
        id = id,
        title = "测试商品 $id",
        price = price,
        originalPrice = null,
        category = category,
        condition = ProductCondition.GOOD,
        description = "描述",
        location = "三食堂门口",
        publishedMinutesAgo = 10,
        sellerName = "同学",
        coverRes = 0
    )

    /** 内存版 DAO：行为与 Room 保持一致（同样返回 Flow，同样 upsert 覆盖）。 */
    private class FakeProductDao(initial: List<ProductEntity> = emptyList()) : ProductDao {

        private val state = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<ProductEntity>> =
            state.map { it.sortedByDescending { entity -> entity.publishedAt } }

        override fun observeByCategory(category: String): Flow<List<ProductEntity>> =
            state.map { list ->
                list.filter { it.category == category }.sortedByDescending { it.publishedAt }
            }

        override suspend fun findById(id: String): ProductEntity? =
            state.value.firstOrNull { it.id == id }

        override suspend fun count(): Int = state.value.size

        override suspend fun upsertAll(items: List<ProductEntity>) {
            val incomingIds = items.map { it.id }.toSet()
            state.value = state.value.filterNot { it.id in incomingIds } + items
        }

        override suspend fun upsert(item: ProductEntity) {
            state.value = state.value.filterNot { it.id == item.id } + item
        }

        override suspend fun deleteById(id: String) {
            state.value = state.value.filterNot { it.id == id }
        }

        override suspend fun deleteMissing(keepIds: List<String>) {
            require(keepIds.isNotEmpty()) { "keepIds 为空时不应调用 deleteMissing" }
            state.value = state.value.filter { it.id in keepIds }
        }
    }

    /** 假远端：用 [failure] 模拟断网，用 [singleResult] 模拟 404。 */
    private class FakeRemote(
        private val products: List<Product> = emptyList(),
        private val failure: Throwable? = null,
        private val singleResult: Product? = null
    ) : ProductRemoteDataSource {

        override suspend fun fetchProducts(
            category: ProductCategory?,
            page: Int,
            size: Int
        ): List<Product> {
            failure?.let { throw it }
            return products.filter { category == null || it.category == category }
        }

        override suspend fun searchProducts(query: ProductQuery, page: Int, size: Int): List<Product> {
            failure?.let { throw it }
            return products
        }

        override suspend fun fetchProduct(id: String): Product? {
            failure?.let { throw it }
            return singleResult
        }

        override suspend fun publishProduct(request: CreateProductRequest): Product {
            failure?.let { throw it }
            return products.first()
        }

        override suspend fun fetchPriceStats(category: ProductCategory?): PriceStatsDto = PriceStatsDto()

        override suspend fun uploadImage(file: File): String {
            failure?.let { throw it }
            uploadedFiles += file.name
            return "/uploads/${file.name}"
        }

        /** 记录上传过哪些文件，用来断言「几张图就上传几次」。 */
        val uploadedFiles = mutableListOf<String>()
    }
}
