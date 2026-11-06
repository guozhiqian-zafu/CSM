package com.csm.market.ui.messages

import com.csm.market.core.error.CsmError
import com.csm.market.core.ui.UiState
import com.csm.market.data.model.Conversation
import com.csm.market.data.model.Message
import com.csm.market.data.repo.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.IOException

/**
 * 会话列表状态机测试。
 *
 * 这条链路上最容易错的是空态判定：服务端返回空数组时必须进 Empty，
 * 而不是显示一个「列表为空」的成功态——两者在界面上的表现完全不同。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun conversation(id: String, unread: Int = 0) = Conversation(
        productId = id,
        productTitle = "商品 $id",
        coverUrl = null,
        lastContent = "最后一条 $id",
        lastMinutesAgo = 10,
        unreadCount = unread
    )

    @Test
    fun `有会话时展示列表并保持服务端顺序`() = runTest(dispatcher) {
        val repo = FakeMessageRepository(listOf(conversation("p2"), conversation("p1")))

        val viewModel = ConversationsViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf("p2", "p1"), (state as UiState.Success).data.map { it.productId })
    }

    @Test
    fun `没有会话时是空态`() = runTest(dispatcher) {
        val viewModel = ConversationsViewModel(FakeMessageRepository(emptyList()))
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Empty)
    }

    @Test
    fun `请求失败时是可重试的错误态`() = runTest(dispatcher) {
        val viewModel = ConversationsViewModel(
            FakeMessageRepository(emptyList(), failure = IOException("断网"))
        )
        advanceUntilIdle()

        val error = (viewModel.state.value as UiState.Error).error
        assertTrue(error is CsmError.Network)
    }

    @Test
    fun `重试会重新拉取并恢复成功态`() = runTest(dispatcher) {
        val repo = FakeMessageRepository(listOf(conversation("p1")), failFirstTime = true)
        val viewModel = ConversationsViewModel(repo)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Error)

        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Success)
        assertEquals(2, repo.observeCount)
    }

    private class FakeMessageRepository(
        private val conversations: List<Conversation>,
        private val failure: Exception? = null,
        private val failFirstTime: Boolean = false
    ) : MessageRepository {

        var observeCount = 0
            private set

        override suspend fun messages(productId: String): List<Message> = emptyList()

        override suspend fun send(productId: String, content: String): Message =
            error("会话列表不发送留言")

        override suspend fun conversations(): List<Conversation> {
            observeCount++
            if (failFirstTime && observeCount == 1) throw IOException("第一次失败")
            failure?.let { throw it }
            return conversations
        }

        override suspend fun unreadCount(): Int = conversations.sumOf { it.unreadCount }
    }
}
