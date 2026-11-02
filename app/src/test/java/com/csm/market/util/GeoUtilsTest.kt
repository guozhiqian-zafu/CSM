package com.csm.market.util

import com.csm.market.data.model.CampusLocations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 距离计算与地点解析的单元测试。
 *
 * 这两段是「附近好物」的全部业务逻辑（其余只是权限与定位读取），
 * 把它钉死之后，即使没有模拟器也能说明功能是对的。
 */
class GeoUtilsTest {

    private val library = CampusLocations.LatLng(30.2755, 120.1533)

    @Test
    fun `同一点距离为零`() {
        assertEquals(0.0, GeoUtils.distanceMeters(library, library), 0.001)
    }

    @Test
    fun `纬度差 0_001 度约 111 米`() {
        // 1 度纬度 ≈ 111.2km，0.001 度 ≈ 111 米
        val near = CampusLocations.LatLng(library.latitude + 0.001, library.longitude)

        val distance = GeoUtils.distanceMeters(library, near)

        assertTrue("实际 $distance", distance in 110.0..112.5)
    }

    @Test
    fun `距离文案在米与公里之间切换`() {
        assertEquals("就在附近", GeoUtils.formatDistance(60.0))
        assertEquals("距你 320 米", GeoUtils.formatDistance(317.0))
        assertEquals("距你 1000 米", GeoUtils.formatDistance(999.0))
        assertEquals("距你 1.2 公里", GeoUtils.formatDistance(1234.0))
        assertEquals("距离未知", GeoUtils.formatDistance(-1.0))
    }

    @Test
    fun `精确地名与带后缀的地名都能解析`() {
        assertNotNull(CampusLocations.resolve("图书馆"))
        assertNotNull("「图书馆 3 楼」应当解析到图书馆", CampusLocations.resolve("图书馆 3 楼"))
        assertNotNull("「三食堂门口旁边」应当解析到三食堂", CampusLocations.resolve("三食堂门口旁边"))
    }

    @Test
    fun `多个地名同时出现时取最长的那个`() {
        // 表里同时有「三食堂」与「三食堂门口」，后者更具体
        val resolved = CampusLocations.resolve("三食堂门口")
        val plain = CampusLocations.resolve("三食堂")
        assertNotNull(resolved)
        assertEquals(plain, resolved)
    }

    @Test
    fun `解析不出的地名返回 null`() {
        assertNull(CampusLocations.resolve("火星基地"))
        assertNull(CampusLocations.resolve(""))
    }
}
