package com.csm.market.data.model

/**
 * 某个分类的成交价统计，由服务端 `/api/price-stats` 汇总返回。
 *
 * 只带统计量、不带原始样本：客户端拿它做定价提示就够了，
 * 传几万条价格明细既费流量，也没必要让客户端知道别人的具体挂价。
 *
 * @param median 中位数（比平均值抗极端值，1280 的 iPad 不会把 45 的充电宝带跑）
 * @param p25 25 分位，作为「便宜」的参考下界
 * @param p75 75 分位，作为「偏贵」的参考上界
 * @param sampleCount 参与统计的样本数，太小时提示语要说明「参考样本少」
 */
data class PriceStats(
    val category: ProductCategory,
    val median: Double,
    val p25: Double,
    val p75: Double,
    val sampleCount: Int
) {
    /** 样本少于这个数时，提示语会降级成「样本较少，仅供参考」。 */
    val isSampleSmall: Boolean get() = sampleCount < MIN_SAMPLE

    companion object {
        const val MIN_SAMPLE = 5
    }
}
