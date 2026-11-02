package com.csm.market.data.model

/**
 * 校园地点坐标表。
 *
 * 「附近好物」需要知道每个商品大概在哪儿，但商品数据里只有一个人能读懂的地名
 * （「三食堂门口」「图书馆南门」），没有经纬度。两个选择：
 *
 * 1. 让发布页强制记 GPS —— 很少有人愿意为了卖本书把定位交出去，而且室内定位常常飘；
 * 2. 维护一张「校园地点 → 坐标」的对照表，用地名换坐标。
 *
 * 选 2：数据量小、可解释、答辩时能讲清楚，也不需要额外申请精确定位权限。
 * 用户自己的位置仍然来自系统定位（粗定位即可），两者一起算直线距离。
 */
object CampusLocations {

    /** 一个地点的经纬度；用 Double 而不是 android.location.Location，方便纯 JVM 单测。 */
    data class LatLng(val latitude: Double, val longitude: Double)

    /** 学校大致的中心点，定位失败时兜底用（避免把所有人算到同一个点还算出 0 米）。 */
    val CAMPUS_CENTER = LatLng(30.2741, 120.1551)

    private val table: Map<String, LatLng> = mapOf(
        "图书馆" to LatLng(30.2755, 120.1533),
        "图书馆南门" to LatLng(30.2751, 120.1531),
        "三食堂" to LatLng(30.2732, 120.1568),
        "三食堂门口" to LatLng(30.2732, 120.1568),
        "二食堂" to LatLng(30.2761, 120.1571),
        "三号宿舍楼" to LatLng(30.2725, 120.1546),
        "五号宿舍楼" to LatLng(30.2729, 120.1538),
        "东区教学楼" to LatLng(30.2771, 120.1552),
        "西区教学楼" to LatLng(30.2765, 120.1524),
        "体育馆" to LatLng(30.2718, 120.1585),
        "操场" to LatLng(30.2721, 120.1579),
        "快递驿站" to LatLng(30.2738, 120.1583),
        "校医院" to LatLng(30.2749, 120.1591),
        "北门" to LatLng(30.2788, 120.1548),
        "南门" to LatLng(30.2705, 120.1553),
        "校园内" to CAMPUS_CENTER
    )

    /**
     * 把商品里记录的地名解析成坐标。
     *
     * 用「包含」而不是「相等」来匹配：卖家可能填「三食堂门口旁边」「图书馆 3 楼」，
     * 这种带后缀的写法很常见，精确匹配会漏掉一大半。
     */
    fun resolve(location: String): LatLng? {
        val text = location.trim()
        if (text.isEmpty()) return null
        table[text]?.let { return it }
        return table.entries
            .filter { text.contains(it.key) }
            // 多个地点名同时出现时取最长的那个（「三食堂门口」优先于「三食堂」）
            .maxByOrNull { it.key.length }
            ?.value
    }

    /** 供测试与文档使用的条目数。 */
    val size: Int get() = table.size
}
