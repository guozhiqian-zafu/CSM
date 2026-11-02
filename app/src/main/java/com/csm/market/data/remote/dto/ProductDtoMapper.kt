package com.csm.market.data.remote.dto

import com.csm.market.BuildConfig
import com.csm.market.R
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * DTO → 领域模型的映射。
 *
 * 三个必须做的「翻译」，缺一个 UI 就会崩：
 * 1. `category`/`condition` 是字符串，服务端可能新增枚举值，必须降级成 OTHER / GOOD；
 * 2. `publishedAt` 是 ISO 时间，UI 要的是「几分钟前」，这里换算成分钟数；
 * 3. 服务端只给图片的相对路径（`/uploads/xxx.jpg`），拼上 baseUrl 才能给 Coil 加载。
 */
fun ProductDto.toDomain(
    now: Long = System.currentTimeMillis(),
    baseUrl: String = BuildConfig.API_BASE_URL
): Product = Product(
    id = id,
    title = title,
    price = price,
    originalPrice = originalPrice,
    category = ProductCategory.entries.firstOrNull { it.name == category } ?: ProductCategory.OTHER,
    condition = ProductCondition.entries.firstOrNull { it.name == condition } ?: ProductCondition.GOOD,
    description = description,
    location = location,
    publishedMinutesAgo = minutesSince(publishedAt, now),
    sellerName = seller.name.ifBlank { "匿名同学" },
    coverRes = fallbackCover(id),
    coverUrl = absoluteUrl(cover, baseUrl)
)

/**
 * 相对路径转绝对地址。
 *
 * 服务端存的是 `/uploads/xxx.jpg`——这样换域名、换端口都不用改数据；
 * 客户端在这里拼一次。已经是 http(s) 的地址原样返回，方便以后接真正的对象存储。
 */
internal fun absoluteUrl(path: String?, baseUrl: String): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}

/**
 * ISO-8601 → 「多少分钟前」。
 *
 * 时间解析失败（服务端字段格式变了、或者脏数据）时返回 0，也就是「刚刚」：
 * 宁可显示得保守，也不要因为一条脏数据让整个列表抛异常。
 */
internal fun minutesSince(isoTime: String, now: Long): Int {
    if (isoTime.isBlank()) return 0
    return try {
        val published = OffsetDateTime.parse(isoTime).toInstant().toEpochMilli()
        ((now - published) / 60_000L).coerceAtLeast(0L).toInt()
    } catch (e: DateTimeParseException) {
        0
    }
}

/**
 * 封面占位图：用 id 的哈希在 6 张渐变图里挑一张。
 *
 * 用哈希而不是随机数，是为了让同一个商品每次显示同一张图——
 * 列表滚动、重新进入都不会「换封面」，看起来才像真实数据。
 */
internal fun fallbackCover(id: String): Int {
    val index = (id.hashCode().absoluteValue % 6) + 1
    return when (index) {
        1 -> R.drawable.bg_photo_1
        2 -> R.drawable.bg_photo_2
        3 -> R.drawable.bg_photo_3
        4 -> R.drawable.bg_photo_4
        5 -> R.drawable.bg_photo_5
        else -> R.drawable.bg_photo_6
    }
}

private val Int.absoluteValue: Int get() = if (this == Int.MIN_VALUE) 0 else if (this < 0) -this else this
