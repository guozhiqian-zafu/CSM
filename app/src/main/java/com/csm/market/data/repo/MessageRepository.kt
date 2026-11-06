package com.csm.market.data.repo

import com.csm.market.data.model.Conversation
import com.csm.market.data.model.Message

/**
 * 留言仓库。
 *
 * 留言不落 Room：它是「别人说的话」，随时可能变新，本地缓存反而会造成
 * 「我刚发的消息重进页面就消失」这种错觉。断网时如实报错，由界面给出重试。
 */
interface MessageRepository {

    suspend fun messages(productId: String): List<Message>

    suspend fun send(productId: String, content: String): Message

    /** 「我的留言」会话列表：一个商品一条，按最后一条留言时间倒序。 */
    suspend fun conversations(): List<Conversation>

    /** 未读数：伴生小组件与「我的」页都要用。 */
    suspend fun unreadCount(): Int
}
