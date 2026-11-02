package com.csm.market.ui.mylistings

import com.csm.market.core.error.CsmError
import com.csm.market.core.ui.UiState
import com.csm.market.R
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition
import com.csm.market.data.model.ProductQuery
import com.csm.market.data.repo.NewProduct
import com.csm.market.data.repo.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MyListingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `有发布记录时进入成功态`() = runTest(dispatcher.scheduler) {
        val repository = FakeProductRepository(listOf(product("p1"), product("p2")))
        val viewModel = MyListingsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf("p1", "p2"), (state as UiState.Success).data.map { it.id })
    }

    @Test
    fun `没有任何发布时进入空态`() = runTest(dispatcher.scheduler) {
        val viewModel = MyListingsViewModel(FakeProductRepository(emptyList()))
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Empty)
    }

    @Test
    fun `请求失败时进入可重试的错误态`() = runTest(dispatcher.scheduler) {
        val repository = FakeProductRepository(emptyList(), failure = java.io.IOException("boom"))
        val viewModel = MyListingsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Error)
        assertTrue((state as UiState.Error).error is CsmError.Network)
    }

    @Test
    fun `下架成功后重新拉取列表并从服务端结果里消失`() = runTest(dispatcher.scheduler) {
        val repository = FakeProductRepository(listOf(product("p1"), product("p2")))
        val viewModel = MyListingsViewModel(repository)
        advanceUntilIdle()

        viewModel.takeDown((viewModel.state.value as UiState.Success).data.first())
        advanceUntilIdle()

        assertEquals(listOf("p1"), repository.takenDown)
        assertEquals(listOf("p2"), (viewModel.state.value as UiState.Success).data.map { it.id })
    }

    @Test
    fun `下架失败时提示错误并保留原列表`() = runTest(dispatcher.scheduler) {
        val repository = FakeProductRepository(listOf(product("p1")), takeDownFailure = java.io.IOException("boom"))
        val viewModel = MyListingsViewModel(repository)
        advanceUntilIdle()

        viewModel.takeDown((viewModel.state.value as UiState.Success).data.first())
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Error)
        assertEquals(com.csm.market.R.string.my_listings_take_down_failed, viewModel.message.value)
    }

    private fun product(id: String) = Product(
        id = id,
        title = "商品 $id",
        price = 10.0,
        originalPrice = 20.0,
        category = ProductCategory.BOOK,
        condition = ProductCondition.GOOD,
        description = "描述",
        location = "图书馆",
        publishedMinutesAgo = 30,
        sellerName = "我",
        coverRes = R.drawable.bg_photo_1
    )

    /** 只实现「我的发布」用例用得到的方法，其余一律报错，避免假实现悄悄偏离真实契约。 */
    private class FakeProductRepository(
        products: List<Product>,
        private val failure: Throwable? = null,
        private val takeDownFailure: Throwable? = null
    ) : ProductRepository {

        private val mine = MutableStateFlow(products)

        val takenDown = mutableListOf<String>()

        override val isOffline: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

        override fun observeProducts(category: ProductCategory?): Flow<List<Product>> = mine

        override suspend fun search(query: ProductQuery): List<Product> = mine.value

        override suspend fun getProduct(id: String): Product? = mine.value.firstOrNull { it.id == id }

        override suspend fun saveProduct(product: Product) = Unit

        override suspend fun publish(
            product: NewProduct,
            images: List<File>,
            onProgress: (done: Int, total: Int) -> Unit
        ): Product = error("我的发布用例不会调用 publish")

        override suspend fun countProducts(): Int = mine.value.size

        override suspend fun priceStats(category: ProductCategory?) = null

        override suspend fun myListings(): List<Product> {
            failure?.let { throw it }
            return mine.value
        }

        override suspend fun takeDown(id: String) {
            takeDownFailure?.let { throw it }
            takenDown += id
            mine.value = mine.value.filterNot { it.id == id }
        }
    }
}
