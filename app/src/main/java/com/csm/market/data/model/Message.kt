package com.csm.market.data.model

/**
 * 一条商品留言。
 *
 * 只有商品 id 没有整个商品对象：留言页顶部的商品信息由上一个页面传进来，
 * 这里再塞一份副本，商品改价后就可能显示旧价格。
 */
data class Message(
    val id: String,
    val productId: String,
    val senderName: String,
    val content: String,
    val publishedMinutesAgo: Int,
    val isMine: Boolean,
    val read: Boolean
)
