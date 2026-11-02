package com.csm.market.util

import com.csm.market.data.model.CampusLocations.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 距离计算：纯函数，方便单测。
 *
 * 用 Haversine 公式（球面大圆距离）而不是平面勾股：
 * 校园东西一公里，纬度 30° 处 1 经度 ≈ 96km，直接用经纬度做减法会把距离算歪。
 */
object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** 两点间的球面距离，单位米。 */
    fun distanceMeters(from: LatLng, to: LatLng): Double {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val a = sin(dLat / 2) * sin(dLat / 2) + sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * 距离文案：1km 以内显示米（取整到 10m），超过显示公里（保留 1 位）。
     *
     * 取整到 10 米是因为 GPS 粗定位本身就有几十米误差，显示「327 米」是假精确。
     */
    fun formatDistance(meters: Double): String = when {
        meters < 0 -> "距离未知"
        meters < 100 -> "就在附近"
        meters < 1000 -> "距你 ${(meters / 10).roundToInt() * 10} 米"
        else -> "距你 %.1f 公里".format(meters / 1000)
    }
}
