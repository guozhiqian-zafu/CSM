package com.csm.market.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 商品表。
 *
 * 设计要点：
 * 1. **时间存绝对时间戳**（`publishedAt`，毫秒），而不是领域模型里的「多少分钟前」。
 *    相对时间是展示层的事——存成相对值，App 放一天再打开就永远显示「3 分钟前」。
 * 2. 分类、成色存 `enum.name` 字符串而不是 ordinal：ordinal 一旦调整枚举顺序，
 *    老数据就会静默错位，字符串虽然占点空间但可读、可迁移。
 * 3. 封面暂时是本地 drawable 资源 id（样例数据阶段），接入真实图片后
 *    这一列会换成 `coverUrl`，图片文件另存到应用私有目录。
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val title: String,
    val price: Double,
    val originalPrice: Double?,
    val category: String,
    val condition: String,
    val description: String,
    val location: String,
    /** 发布时间戳（epoch millis） */
    val publishedAt: Long,
    val sellerName: String,
    val coverRes: Int
)
