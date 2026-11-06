package com.csm.market.data.remote.dto

import com.csm.market.BuildConfig
import com.csm.market.data.model.Conversation
import com.csm.market.data.model.Message
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** 把服务端的留言转成界面用的模型；时间统一换成「几分钟前」，与商品列表一致。 */
fun MessageDto.toDomain(now: Long = System.currentTimeMillis()): Message = Message(
    id = id,
    productId = productId,
    senderName = senderName.ifBlank { if (isMine) "我" else "匿名同学" },
    content = content,
    publishedMinutesAgo = minutesAgo(createdAt, now),
    isMine = isMine,
    read = read
)

/** 会话摘要：封面走与商品一致的相对地址补全规则。 */
fun ConversationDto.toDomain(
    now: Long = System.currentTimeMillis(),
    baseUrl: String = BuildConfig.API_BASE_URL
): Conversation = Conversation(
    productId = productId,
    productTitle = productTitle.ifBlank { "已下架的商品" },
    coverUrl = absoluteUrl(cover, baseUrl),
    lastContent = lastContent,
    lastMinutesAgo = minutesAgo(lastAt, now),
    unreadCount = unreadCount
)

private fun minutesAgo(isoTime: String, now: Long): Int {
    if (isoTime.isBlank()) return 0
    return try {
        val created = OffsetDateTime.parse(isoTime).toInstant().toEpochMilli()
        ((now - created) / 60_000L).coerceAtLeast(0L).toInt()
    } catch (e: DateTimeParseException) {
        // 时间格式坏掉不该让整页崩，退化成「刚刚」
        0
    }
}
