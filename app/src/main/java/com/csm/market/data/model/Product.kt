package com.csm.market.data.model

import androidx.annotation.DrawableRes

/**
 * 商品分类。
 *
 * 分类同时参与「智能定价」：不同类目的基准价不同，算法按类目取历史价格中位数。
 */
enum class ProductCategory(val label: String, val basePrice: Int) {
    BOOK("教材书籍", 35),
    DIGITAL("数码电子", 320),
    DAILY("生活用品", 25),
    SPORTS("运动户外", 90),
    CLOTHING("服饰鞋包", 60),
    OTHER("其他", 40)
}

/**
 * 成色。factor 是「智能定价助手」使用的成色系数（见 docs/06-个性化功能设计.md）。
 */
enum class ProductCondition(val label: String, val factor: Double) {
    NEW("全新", 1.15),
    LIKE_NEW("几乎全新", 1.05),
    GOOD("轻微使用", 0.95),
    FAIR("明显使用", 0.80),
    POOR("有瑕疵", 0.65)
}

/**
 * 商品领域模型。
 *
 * 第 1 次提交先用于 UI 骨架和样例数据；接入 Room 后会增加 @Entity 映射与
 * 网络 DTO 的转换函数，保持 UI 层只依赖这个模型。
 */
data class Product(
    val id: String,
    val title: String,
    val price: Double,
    val originalPrice: Double?,
    val category: ProductCategory,
    val condition: ProductCondition,
    val description: String,
    val location: String,
    /** 发布于多少分钟前，用于列表展示「x 小时前」；接入网络后换成真实时间戳。 */
    val publishedMinutesAgo: Int,
    val sellerName: String,
    /** 本地占位图（服务端没图或图片加载失败时用），保证列表不会出现灰块。 */
    @DrawableRes val coverRes: Int,
    /** 服务端返回的封面地址；为空时退回 [coverRes] 的渐变占位图。 */
    val coverUrl: String? = null
)
