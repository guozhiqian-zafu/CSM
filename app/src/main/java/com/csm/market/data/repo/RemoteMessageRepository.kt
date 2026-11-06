package com.csm.market.data.repo

import android.content.Context

import com.csm.market.data.local.WidgetState
import com.csm.market.data.model.Conversation
import com.csm.market.data.model.Message
import com.csm.market.data.remote.ApiClient
import com.csm.market.data.remote.CsmApi
import com.csm.market.data.remote.dto.CreateMessageRequest
import com.csm.market.data.remote.dto.toDomain
import com.csm.market.data.remote.unwrap
import kotlinx.coroutines.CancellationException

/** 留言仓库的网络实现：服务端是唯一数据源。 */
class RemoteMessageRepository(
    private val api: CsmApi = ApiClient.api,
    /** 只用来把未读数写给桌面小组件；为空时小组件角标保持旧值。 */
    private val appContext: Context? = null,
    private val clock: () -> Long = System::currentTimeMillis
) : MessageRepository {

    override suspend fun messages(productId: String): List<Message> = try {
        val now = clock()
        api.listMessages(productId).unwrap().orEmpty().map { it.toDomain(now) }
    } catch (e: CancellationException) {
        throw e
    }

    override suspend fun send(productId: String, content: String): Message {
        val now = clock()
        val created = api.postMessage(productId, CreateMessageRequest(content.trim()))
            .unwrap()
            ?: error("留言发送失败：服务端没有返回内容")
        return created.toDomain(now)
    }

    override suspend fun conversations(): List<Conversation> = try {
        val now = clock()
        api.myConversations().unwrap().orEmpty().map { it.toDomain(now) }
    } catch (e: CancellationException) {
        throw e
    }

    override suspend fun unreadCount(): Int = try {
        val count = api.unreadCount().unwrap()?.get("count") ?: 0
        // 顺手把未读数落一份给小组件：小组件在广播里读网络会超时，只能读缓存
        appContext?.let { WidgetState.saveUnreadCount(it, count) }
        count
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 角标拿不到就不显示，不能因为一个数字让整页报错
        0
    }
}
