package com.csm.market.data.remote.dto

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
