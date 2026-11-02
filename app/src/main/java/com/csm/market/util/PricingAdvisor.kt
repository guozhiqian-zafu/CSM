package com.csm.market.util

import com.csm.market.data.model.PriceStats
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * 智能定价助手：本项目唯一的「自定义业务逻辑」模块（课程要求的那一项）。
 *
 * 纯函数、不碰 Android、不碰网络，所以能用普通 JVM 单元测试把每条规则钉死——
 * 这是答辩时最容易被追问的地方，也是最容易写对的地方。
 *
 * 思路分三步：
 * 1. 基准价：同类目**中位数**（不是平均价），再按成色系数缩放。
 *    用中位数是因为二手价格分布极偏——一件 1280 的 iPad 就能把「数码」的平均价拉高一大截，
 *    中位数不会被单件高价带跑。
 * 2. 参考区间：基准价 ±15%。二手交易本来就有议价空间，给区间比给一个精确数字更实用。
 * 3. 合理性评分：偏离基准越多分越低，0~100。
 *    70 分以上算「定价合理」，低于 40 分提示重新考虑——但**不阻止发布**，
 *    因为卖家可能有自己的理由（急出、有感情溢价、配件齐全）。
 */
object PricingAdvisor {

    /** 参考区间的上下浮动比例。 */
    const val RANGE_RATIO = 0.15

    /** 低于基准价 × 这个比例就算「捡漏」，与列表上的标记口径一致。 */
    const val BARGAIN_RATIO = 0.70

    /** 没有统计数据时的兜底：直接按分类的经验基准价给建议。 */
    fun fallbackPrice(category: ProductCategory, condition: ProductCondition): Double =
        round2(category.basePrice * condition.factor)

    /**
     * 计算建议价与评分。
     *
     * @param listedPrice 卖家当前填的价格；为空（还没填）时 score 为 null
     * @param stats 服务端给的同分类统计；为空或样本为 0 时退化为 [fallbackPrice]
     */
    fun advise(
        listedPrice: Double?,
        stats: PriceStats?,
        category: ProductCategory,
        condition: ProductCondition
    ): PricingAdvice {
        val usable = stats?.takeIf { it.median > 0.0 && it.sampleCount > 0 }
        val suggested = if (usable != null) {
            // 统计里的中位数是「全成色混合」的价格，按成色系数相对 GOOD 缩放，
            // 这样 5 成新的耳机不会被 9 成新的中位数误导。
            round2(usable.median * condition.factor / ProductCondition.GOOD.factor)
        } else {
            fallbackPrice(category, condition)
        }
        val low = round2(suggested * (1 - RANGE_RATIO))
        val high = round2(suggested * (1 + RANGE_RATIO))

        val score = listedPrice?.takeIf { it > 0.0 }?.let { price ->
            // 偏离 1% 扣 2 分，偏离 50% 直接 0 分：分数只是给卖家一个直觉
            val deviation = abs(price - suggested) / suggested
            (100 - deviation * 200).roundToInt().coerceIn(0, 100)
        }

        val level = when {
            score == null -> null
            score >= 70 -> PriceLevel.FAIR
            listedPrice!! < suggested -> PriceLevel.CHEAP
            else -> PriceLevel.EXPENSIVE
        }

        return PricingAdvice(
            suggestedPrice = suggested,
            low = low,
            high = high,
            score = score,
            level = level,
            isBargain = listedPrice != null && listedPrice > 0 && listedPrice <= suggested * BARGAIN_RATIO,
            sampleCount = usable?.sampleCount ?: 0,
            referenceMedian = usable?.median
        )
    }

    /** 保留两位小数。二手价格用不上更多精度，也避免 20.019999 这种显示。 */
    fun round2(value: Double): Double = round(value * 100) / 100

    /** 定价结论，UI 直接按它选颜色和文案。 */
    enum class PriceLevel { CHEAP, FAIR, EXPENSIVE }

    /** 一次定价建议的全部结果。 */
    data class PricingAdvice(
        val suggestedPrice: Double,
        val low: Double,
        val high: Double,
        val score: Int?,
        val level: PriceLevel?,
        val isBargain: Boolean,
        val sampleCount: Int,
        val referenceMedian: Double?
    ) {
        /** 样本不足时提示语要更谨慎。 */
        val isReliable: Boolean get() = sampleCount >= PriceStats.MIN_SAMPLE
    }
}
