package com.csm.market.data.model

/**
 * 「我的留言」里的一条会话：一个商品 + 最后一条留言 + 未读数。
 *
 * 为什么不复用 [Message]：会话是对「一段对话」的摘要，需要商品标题与封面，
 * 而单条留言只关心内容与时间。硬塞成一个模型会让两边字段都长着用不到的肉。
 */
data class Conversation(
    val productId: String,
    val productTitle: String,
    val coverUrl: String?,
    val lastContent: String,
    val lastMinutesAgo: Int,
    val unreadCount: Int
)
