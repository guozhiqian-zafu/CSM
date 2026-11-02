package com.csm.market.ui.home

import com.csm.market.core.error.CsmError
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition
import com.csm.market.data.model.ProductQuery
import com.csm.market.data.repo.NewProduct
import com.csm.market.data.repo.ProductRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HomeViewModel] 的单元测试。
 *
 * 这里能测，正是因为 ViewModel 只依赖 [ProductRepository] 接口：
 * 用一个假仓库替换 Room，测试不需要模拟器、不需要 Robolectric。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    /** 三条样例商品：两条教材、一条数码。 */
    private val books = product("b1", ProductCategory.BOOK)
    private val books2 = product("b2", ProductCategory.BOOK)
    private val digital = product("d1", ProductCategory.DIGITAL)
    private val allProducts = listOf(books, books2, digital)

    @Test
    fun `首次加载成功时状态为 Success 且包含全部商品`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeProductRepository(allProducts)
            val viewModel = HomeViewModel(repository)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue("期望 Success，实际 $state", state is UiState.Success)
            assertEquals(allProducts, (state as UiState.Success).data)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `切换到没有商品的分类时状态为 Empty`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeProductRepository(allProducts)
            val viewModel = HomeViewModel(repository)
            advanceUntilIdle()

            viewModel.selectCategory(ProductCategory.SPORTS)
            advanceUntilIdle()

            assertEquals(ProductCategory.SPORTS, viewModel.selectedCategory.value)
            assertTrue("期望 Empty，实际 ${viewModel.state.value}", viewModel.state.value is UiState.Empty)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `按分类筛选后只剩该分类的商品`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeProductRepository(allProducts)
            val viewModel = HomeViewModel(repository)
            advanceUntilIdle()

            viewModel.selectCategory(ProductCategory.BOOK)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state is UiState.Success)
            assertEquals(listOf(books, books2), (state as UiState.Success).data)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `仓库抛异常时状态为 Error 且能映射成 Network`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeProductRepository(
                products = emptyList(),
                failure = java.net.UnknownHostException("offline")
            )
            val viewModel = HomeViewModel(repository)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue("期望 Error，实际 $state", state is UiState.Error)
            assertTrue((state as UiState.Error).error is CsmError.Network)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun product(id: String, category: ProductCategory): Product = Product(
        id = id,
        title = "测试商品 $id",
        price = 20.0,
        originalPrice = null,
        category = category,
        condition = ProductCondition.GOOD,
        description = "描述",
        location = "图书馆",
        publishedMinutesAgo = 5,
        sellerName = "同学",
        coverRes = 0
    )

    /**
     * 假仓库：用 [MutableStateFlow] 模拟 Room 的「表变化自动推送」。
     * [failure] 不为空时，收集一开始就抛异常，用来验证错误态。
     */
    private class FakeProductRepository(
        products: List<Product>,
        private val failure: Throwable? = null
    ) : ProductRepository {

        private val source = MutableStateFlow(products)

        /** 假仓库始终「在线」，离线横幅不出现在这些用例里。 */
        override val isOffline: StateFlow<Boolean> = MutableStateFlow(false)

        override fun observeProducts(category: ProductCategory?): Flow<List<Product>> = flow {
            failure?.let { throw it }
            emitAll(
                source.map { list ->
                    if (category == null) list else list.filter { it.category == category }
                }
            )
        }

        override suspend fun getProduct(id: String): Product? =
            source.value.firstOrNull { it.id == id }

        override suspend fun search(query: ProductQuery): List<Product> = emptyList()

        override suspend fun saveProduct(product: Product) = Unit

        /** 首页用例不涉及发布，这里只要满足接口即可。 */
        override suspend fun publish(
            product: NewProduct,
            images: List<File>,
            onProgress: (done: Int, total: Int) -> Unit
        ): Product = error("首页用例不会调用 publish")

        override suspend fun countProducts(): Int = source.value.size

        /** 首页用例不涉及定价，返回 null 让调用方走兜底分支即可。 */
        override suspend fun priceStats(category: ProductCategory?) = null
    }
}
