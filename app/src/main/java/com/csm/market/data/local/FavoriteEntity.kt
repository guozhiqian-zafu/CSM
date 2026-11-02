package com.csm.market.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏表。
 *
 * 只存「谁收藏了哪个商品」这一个关系，商品信息仍留在 `products` 表：
 * - 商品改价后，收藏页跟着显示新价格，不会出现两份不一致的数据；
 * - 收藏页查询用 `products INNER JOIN favorites`，避免冗余字段。
 *
 * [createdAt] 用来做「最近收藏」排序，收藏页按它倒序展示。
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val productId: String,
    val createdAt: Long
)
