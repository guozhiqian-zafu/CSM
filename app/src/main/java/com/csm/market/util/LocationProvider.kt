package com.csm.market.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.csm.market.data.model.CampusLocations.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 取当前位置，只做「一次」查询。
 *
 * 为什么不用 FusedLocationProvider：那需要 Google Play 服务，国内机器和模拟器上经常没有；
 * 为什么不做持续定位：二手交易只需要「离我最近的商品」，跑一次拿到大概位置就够，
 * 长时间持续定位既费电也费用户信任。
 *
 * 结果封装成 `LatLng?`：返回 null 表示「拿不到位置」（没权限 / 定位开关关着 / 室内无信号），
 * 调用方必须能在 null 时退化成默认排序——这是课程要求的「权限被拒仍可用」。
 */
object LocationProvider {

    /** 需要的权限：粗定位就够算校园内的距离，精确定位反而更容易被用户拒绝。 */
    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(context: Context): LatLng? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        // 定位总开关关着时，getLastKnownLocation 会返回很早以前的缓存，直接当作没有位置
        val enabled = runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        if (!enabled) return@withContext null

        // 依次尝试 network（室内更容易命中缓存）→ gps → passive
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        providers.forEach { provider ->
            val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (location != null) {
                return@withContext LatLng(location.latitude, location.longitude)
            }
        }
        null
    }
}
