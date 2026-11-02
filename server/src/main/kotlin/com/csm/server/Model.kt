package com.csm.server

import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/** 业务状态码：与 docs/04 的表格一一对应，客户端按 code 分支。 */
object ErrorCode {
    const val OK = 0
    const val PARAM = 1001
    const val UNAUTHORIZED = 1002
    const val FORBIDDEN = 1003
    const val NOT_FOUND = 2001
    const val SERVER = 5000
}

/**
 * 统一响应信封。
 *
 * `data` 声明为可空且默认 null：错误响应用 `envelope(code = ...)` 就能自动省掉 data 字段，
 * 不需要再定义一个「没有 data 的响应类」，客户端也只解析一种结构。
 */
@Serializable
data class ApiResponse<T>(
    val code: Int = ErrorCode.OK,
    val message: String = "ok",
    val data: T? = null
)

fun <T> envelope(code: Int = ErrorCode.OK, message: String = "ok", data: T? = null): ApiResponse<T> =
    ApiResponse(code = code, message = message, data = data)

@Serializable
data class SellerDto(val id: String, val name: String)

@Serializable
data class ProductDto(
    val id: String,
    val title: String,
    val price: Double,
    val originalPrice: Double? = null,
    val category: String,
    val condition: String,
    val description: String,
    val location: String,
    val publishedAt: String,
    val seller: SellerDto,
    val cover: String? = null,
    val images: List<String> = emptyList(),
    /** 服务端按「同类目中位数 × 成色系数 × 时间折旧」算出的建议价，保证多端一致。 */
    val suggestPrice: Double? = null,
    /** 低于建议价 70% 视为捡漏，列表上打标。 */
    val isBargain: Boolean = false
)

@Serializable
data class ProductPage(
    val page: Int,
    val size: Int,
    val total: Int,
    val hasMore: Boolean,
    val items: List<ProductDto>
)

@Serializable
data class CreateProductRequest(
    val title: String,
    val price: Double,
    val originalPrice: Double? = null,
    val category: String,
    val condition: String,
    val description: String = "",
    val location: String = "",
    val sellerName: String = "我"
)

@Serializable
data class PriceStatsDto(
    val category: String,
    val median: Double,
    val p25: Double,
    val p75: Double,
    val sampleCount: Int,
    val updatedAt: String = OffsetDateTime.now().toString()
)

/**
 * 分类价格基准。
 *
 * @param medianPrice 该分类所有在售商品价格的中位数（有原价信息不够用时的兜底）
 * @param medianRatio 该分类「成交价 / 原价」比例的中位数，用来按商品自身原价折算建议价
 * @param ratioSample 参与 [medianRatio] 计算的样本数，为 0 时说明分类里没人填原价
 */
data class CategoryStat(
    val medianPrice: Double,
    val medianRatio: Double,
    val ratioSample: Int
)

/** 成色系数：与客户端 `ProductCondition.factor` 保持一致，评优答辩时要能解释每个数字的来历。 */
val conditionFactors: Map<String, Double> = mapOf(
    "NEW" to 1.15,
    "LIKE_NEW" to 1.05,
    "GOOD" to 0.95,
    "FAIR" to 0.80,
    "POOR" to 0.65
)

val validCategories: Set<String> = setOf("BOOK", "DIGITAL", "DAILY", "SPORTS", "CLOTHING", "OTHER")

/**
 * 定价核心：base 用同类目已上架商品的中位数（比平均值抗极端值），
 * 再乘成色系数与时间折旧，最后给出 ±15% 的区间。
 *
 * @param daysListed 已上架天数，越久卖不动越应该降价，最多折 15%
 */
fun suggestPrice(base: Double, condition: String, daysListed: Long): Double {
    val factor = conditionFactors[condition] ?: 1.0
    val urgency = 1.0 - minOf(0.15, 0.01 * daysListed)
    return round2(base * factor * urgency)
}

fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

/** 中位数：偶数个取中间两个的平均值。 */
fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
}

/** 分位数（最近秩法），样本不足时返回最小值/最大值，避免小样本插值出奇怪的数。 */
fun percentile(values: List<Double>, p: Double): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val index = ((sorted.size - 1) * p).toInt()
    return round2(sorted[index])
}
