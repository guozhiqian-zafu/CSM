package com.csm.market.data.local

import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition

/**
 * 实体 ↔ 领域模型 转换。
 *
 * 只在数据层内部使用：UI 层永远拿 [Product]，不直接碰 [ProductEntity]，
 * 这样以后换 Room 版本、加网络 DTO 都不用改页面。
 */

/** 一分钟的毫秒数，换算「发布于多久前」用。 */
private const val MILLIS_PER_MINUTE = 60_000L

fun ProductEntity.toDomain(now: Long = System.currentTimeMillis()): Product {
    // 枚举用 name 存储，理论上不会出现脏值；真出现了也降级成 OTHER / GOOD，而不是崩溃
    val safeCategory = ProductCategory.entries.firstOrNull { it.name == category } ?: ProductCategory.OTHER
    val safeCondition = ProductCondition.entries.firstOrNull { it.name == condition } ?: ProductCondition.GOOD
    val minutesAgo = ((now - publishedAt) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(0)

    return Product(
        id = id,
        title = title,
        price = price,
        originalPrice = originalPrice,
        category = safeCategory,
        condition = safeCondition,
        description = description,
        location = location,
        publishedMinutesAgo = minutesAgo,
        sellerName = sellerName,
        coverRes = coverRes
    )
}

/**
 * 领域模型 → 实体。
 *
 * `now` 由调用方传入（默认当前时间），保证「一次批量写入的商品时间基准一致」，
 * 也让单元测试可以固定时钟。
 */
fun Product.toEntity(now: Long = System.currentTimeMillis()): ProductEntity = ProductEntity(
    id = id,
    title = title,
    price = price,
    originalPrice = originalPrice,
    category = category.name,
    condition = condition.name,
    description = description,
    location = location,
    publishedAt = now - publishedMinutesAgo * MILLIS_PER_MINUTE,
    sellerName = sellerName,
    coverRes = coverRes
)
