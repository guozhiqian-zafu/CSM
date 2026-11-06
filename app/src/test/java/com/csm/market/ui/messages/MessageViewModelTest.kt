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
 * 留言页状态机测试。
 *
 * 重点不是「能不能拿到列表」，而是三种容易写错的交互：
 * 1. 空列表要变成 Empty 而不是 Success(空)；
 * 2. 发出去的消息要立刻出现在列表末尾（不做二次请求）；
 * 3. 发送失败时草稿必须留着，否则用户白打一遍字。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun message(id: String, mine: Boolean = false) = Message(
        id = id,
        productId = "p1",
        senderName = if (mine) "我" else "林同学",
        content = "内容 $id",
        publishedMinutesAgo = 5,
        isMine = mine,
        read = false
    )

    @Test
    fun `有留言时直接展示列表`() = runTest(dispatcher) {
        val repo = FakeMessageRepository(mutableListOf(message("m1"), message("m2", mine = true)))

        val viewModel = MessageViewModel(repo, "p1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is UiState.Success)
        assertEquals(listOf("m1", "m2"), (state as UiState.Success).data.map { it.id })
    }

    @Test
    fun `没有留言时是空态而不是空列表`() = runTest(dispatcher) {
        val viewModel = MessageViewModel(FakeMessageRepository(mutableListOf()), "p1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Empty)
    }

    @Test
    fun `仓库异常时给出可重试的错误态`() = runTest(dispatcher) {
        val repo = FakeMessageRepository(mutableListOf(), failure = IOException("断网"))

        val viewModel = MessageViewModel(repo, "p1")
        advanceUntilIdle()

        val error = (viewModel.state.value as UiState.Error).error
        assertTrue(error is CsmError.Network)
    }

    @Test
    fun `发送成功后清空草稿并追加到列表末尾`() = runTest(dispatcher) {
        val repo = FakeMessageRepository(mutableListOf(message("m1")))
        val viewModel = MessageViewModel(repo, "p1")
        advanceUntilIdle()

        viewModel.onDraftChange("  还能便宜点吗  ")
        viewModel.send()
        advanceUntilIdle()

        assertEquals("", viewModel.draft.value)
        val data = (viewModel.state.value as UiState.Success).data
        assertEquals(2, data.size)
        assertEquals("还能便宜点吗", data.last().content)
        assertEquals("还能便宜点吗", repo.sent.single().content)
    }

    @Test
    fun `发送失败时保留草稿`() = runTest(dispatcher) {
        val repo = FakeMessageRepository(mutableListOf(), sendFailure = IOException("断网"))
        val viewModel = MessageViewModel(repo, "p1")
        advanceUntilIdle()

        viewModel.onDraftChange("这个还在吗")
        viewModel.send()
        advanceUntilIdle()

        assertEquals("这个还在吗", viewModel.draft.value)
        assertTrue(viewModel.state.value is UiState.Empty)
        assertEquals(false, viewModel.sending.value)
    }

    @Test
    fun `空白内容不会发出请求`() = runTest(dispatcher) {
        val repo = FakeMessageRepository(mutableListOf())
        val viewModel = MessageViewModel(repo, "p1")
        advanceUntilIdle()

        viewModel.onDraftChange("   ")
        viewModel.send()
        advanceUntilIdle()

        assertTrue(repo.sent.isEmpty())
    }

    private class FakeMessageRepository(
        private val initial: MutableList<Message>,
        private val failure: Exception? = null,
        private val sendFailure: Exception? = null
    ) : MessageRepository {

        val sent = mutableListOf<Message>()

        override suspend fun messages(productId: String): List<Message> {
            failure?.let { throw it }
            return initial.toList()
        }

        override suspend fun send(productId: String, content: String): Message {
            sendFailure?.let { throw it }
            val created = Message(
                id = "m" + (initial.size + sent.size + 1),
                productId = productId,
                senderName = "我",
                content = content,
                publishedMinutesAgo = 0,
                isMine = true,
                read = false
            )
            sent += created
            initial += created
            return created
        }

        override suspend fun conversations(): List<Conversation> =
            initial.map { it.productId }.distinct().map { productId ->
                val last = initial.last { it.productId == productId }
                Conversation(
                    productId = productId,
                    productTitle = "商品 $productId",
                    coverUrl = null,
                    lastContent = last.content,
                    lastMinutesAgo = last.publishedMinutesAgo,
                    unreadCount = initial.count { it.productId == productId && !it.isMine && !it.read }
                )
            }

        override suspend fun unreadCount(): Int = initial.count { !it.isMine && !it.read }
    }
}
