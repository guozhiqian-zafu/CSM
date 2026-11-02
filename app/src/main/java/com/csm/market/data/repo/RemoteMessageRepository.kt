package com.csm.market.data.repo

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

    override suspend fun unreadCount(): Int = try {
        api.unreadCount().unwrap()?.get("count") ?: 0
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 角标拿不到就不显示，不能因为一个数字让整页报错
        0
    }
}
