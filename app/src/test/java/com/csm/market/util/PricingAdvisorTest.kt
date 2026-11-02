package com.csm.market.util

import com.csm.market.data.model.PriceStats
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 定价助手的单元测试。
 *
 * 这是本项目的「自定义业务逻辑」模块，也是答辩时最容易被追问的地方，
 * 所以每条规则都单独钉死：无统计兜底、成色缩放、区间、评分边界、捡漏判定。
 */
class PricingAdvisorTest {

    private val bookStats = PriceStats(
        category = ProductCategory.BOOK,
        median = 40.0,
        p25 = 30.0,
        p75 = 55.0,
        sampleCount = 12
    )

    @Test
    fun `没有统计数据时退回分类基准价乘成色系数`() {
        // 教材书籍 basePrice = 35，全新系数 1.15 → 40.25
        val advice = PricingAdvisor.advise(
            listedPrice = null,
            stats = null,
            category = ProductCategory.BOOK,
            condition = ProductCondition.NEW
        )

        assertEquals(40.25, advice.suggestedPrice, 0.001)
        assertEquals(0, advice.sampleCount)
        assertNull("没填价格时不应该给评分", advice.score)
        assertFalse(advice.isReliable)
    }

    @Test
    fun `有统计时按成色相对 GOOD 缩放中位数`() {
        // 中位数 40 是混合成色，GOOD 系数 0.95；FAIR 0.80 → 40 * 0.80 / 0.95
        val advice = PricingAdvisor.advise(
            listedPrice = null,
            stats = bookStats,
            category = ProductCategory.BOOK,
            condition = ProductCondition.FAIR
        )

        assertEquals(33.68, advice.suggestedPrice, 0.01)
        assertEquals(12, advice.sampleCount)
        assertTrue(advice.isReliable)
    }

    @Test
    fun `参考区间是建议价的正负 15 百分比`() {
        val advice = PricingAdvisor.advise(
            listedPrice = null,
            stats = bookStats,
            category = ProductCategory.BOOK,
            condition = ProductCondition.GOOD
        )

        assertEquals(40.0, advice.suggestedPrice, 0.001)
        assertEquals(34.0, advice.low, 0.001)
        assertEquals(46.0, advice.high, 0.001)
    }

    @Test
    fun `价格等于建议价时满分`() {
        val advice = PricingAdvisor.advise(
            listedPrice = 40.0,
            stats = bookStats,
            category = ProductCategory.BOOK,
            condition = ProductCondition.GOOD
        )

        assertEquals(100, advice.score)
        assertEquals(PricingAdvisor.PriceLevel.FAIR, advice.level)
    }

    @Test
    fun `偏离过大时分数归零但依然返回建议`() {
        val advice = PricingAdvisor.advise(
            listedPrice = 400.0,
            stats = bookStats,
            category = ProductCategory.BOOK,
            condition = ProductCondition.GOOD
        )

        assertEquals(0, advice.score)
        assertEquals(PricingAdvisor.PriceLevel.EXPENSIVE, advice.level)
        assertEquals(40.0, advice.suggestedPrice, 0.001)
    }

    @Test
    fun `低于建议价三成算捡漏`() {
        val cheap = PricingAdvisor.advise(
            listedPrice = 27.0, // 40 * 0.7 = 28 以下算捡漏
            stats = bookStats,
            category = ProductCategory.BOOK,
            condition = ProductCondition.GOOD
        )
        val normal = PricingAdvisor.advise(
            listedPrice = 29.0,
            stats = bookStats,
            category = ProductCategory.BOOK,
            condition = ProductCondition.GOOD
        )

        assertTrue(cheap.isBargain)
        assertEquals(PricingAdvisor.PriceLevel.CHEAP, cheap.level)
        assertFalse(normal.isBargain)
    }

    @Test
    fun `样本为 0 的统计被当作没有统计`() {
        val empty = bookStats.copy(sampleCount = 0, median = 0.0)

        val advice = PricingAdvisor.advise(
            listedPrice = null,
            stats = empty,
            category = ProductCategory.BOOK,
            condition = ProductCondition.GOOD
        )

        // 35 * 0.95 = 33.25，走分类基准价而不是 0 元
        assertEquals(33.25, advice.suggestedPrice, 0.001)
        assertNull(advice.referenceMedian)
    }
}
