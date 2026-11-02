package com.csm.market.ui.favorites

import com.csm.market.core.error.CsmError
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition
import com.csm.market.data.repo.FavoriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FavoritesViewModel] 的单元测试。
 *
 * 重点是三条业务规则：收藏为空是 Empty 而不是 Success(空列表)、
 * 读表失败要变成错误态、重试后必须能恢复（Room 的 Flow 出错即终止）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    @Test
    fun `没有收藏时状态为 Empty`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = FavoritesViewModel(FakeFavoriteRepository())
            advanceUntilIdle()

            assertTrue("期望 Empty，实际 ${viewModel.state.value}", viewModel.state.value is UiState.Empty)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `有收藏时状态为 Success 且顺序与仓库一致`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val newest = product("p2")
            val older = product("p1")
            val viewModel = FavoritesViewModel(FakeFavoriteRepository(listOf(newest, older)))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue("期望 Success，实际 $state", state is UiState.Success)
            assertEquals(listOf(newest, older), (state as UiState.Success).data)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `读收藏失败时状态为 Error 且映射成 Network`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = FavoritesViewModel(
                FakeFavoriteRepository(failure = java.net.UnknownHostException("offline"))
            )
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue("期望 Error，实际 $state", state is UiState.Error)
            assertTrue((state as UiState.Error).error is CsmError.Network)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `重试后能重新订阅并恢复成 Empty`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeFavoriteRepository(failFirstTime = true)
            val viewModel = FavoritesViewModel(repository)
            advanceUntilIdle()
            assertTrue(viewModel.state.value is UiState.Error)

            repository.failFirstTime = false
            viewModel.retry()
            advanceUntilIdle()

            assertTrue(
                "重试后期望 Empty，实际 ${viewModel.state.value}",
                viewModel.state.value is UiState.Empty
            )
            assertEquals("应重新订阅一次收藏表", 2, repository.observeCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun product(id: String): Product = Product(
        id = id,
        title = "收藏商品 $id",
        price = 18.0,
        originalPrice = null,
        category = ProductCategory.BOOK,
        condition = ProductCondition.GOOD,
        description = "描述",
        location = "图书馆",
        publishedMinutesAgo = 3,
        sellerName = "同学",
        coverRes = 0
    )

    /**
     * 假收藏仓库。
     *
     * [failFirstTime] 模拟「第一次读表失败、重启后恢复」，用来验证 retry 真的重新订阅了，
     * 而不是盯着一个已经死掉的 Flow 干等。
     */
    private class FakeFavoriteRepository(
        products: List<Product> = emptyList(),
        private val failure: Throwable? = null,
        var failFirstTime: Boolean = false
    ) : FavoriteRepository {

        var observeCount = 0
            private set

        private val source = MutableStateFlow(products)

        override fun observeFavorites(): Flow<List<Product>> = flow {
            observeCount++
            if (failFirstTime) throw java.net.UnknownHostException("offline")
            failure?.let { throw it }
            emitAll(source)
        }

        override fun observeIsFavorite(productId: String): Flow<Boolean> =
            MutableStateFlow(false)

        override suspend fun toggleFavorite(productId: String): Boolean = true

        override suspend fun countFavorites(): Int = source.value.size
    }
}
