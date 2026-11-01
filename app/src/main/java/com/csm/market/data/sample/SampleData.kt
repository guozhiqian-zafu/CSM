package com.csm.market.data.sample

import com.csm.market.R
import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition

/**
 * 第 1 次提交使用的样例数据。
 *
 * 目的：在没有网络层和数据库时，也能完整演示「列表 -> 详情」的导航结构，
 * 以及列表、详情页的视觉规格。第 5 次提交接入 Room 后，这里会被 Repository 替换。
 */
object SampleData {

    val products: List<Product> = listOf(
        Product(
            id = "p1001",
            title = "《数据结构（C 语言版）》第 3 版，无笔记",
            price = 18.0,
            originalPrice = 45.0,
            category = ProductCategory.BOOK,
            condition = ProductCondition.LIKE_NEW,
            description = "大一上用过一学期，书角有轻微磨损，内页干净没有笔记。图书馆门口或二食堂当面交易都可以。",
            location = "图书馆",
            publishedMinutesAgo = 25,
            sellerName = "李同学",
            coverRes = R.drawable.bg_photo_1
        ),
        Product(
            id = "p1002",
            title = "罗技 M170 无线鼠标，带接收器",
            price = 25.0,
            originalPrice = 79.0,
            category = ProductCategory.DIGITAL,
            condition = ProductCondition.GOOD,
            description = "换鼠标所以出掉，功能一切正常，滚轮略脏可自行清理，附原装接收器。",
            location = "9 号宿舍楼",
            publishedMinutesAgo = 130,
            sellerName = "王同学",
            coverRes = R.drawable.bg_photo_2
        ),
        Product(
            id = "p1003",
            title = "宿舍小风扇（USB 供电，三档）",
            price = 12.0,
            originalPrice = 39.0,
            category = ProductCategory.DAILY,
            condition = ProductCondition.GOOD,
            description = "夏天买的，风力够用，声音不大。毕业清宿舍，便宜出。",
            location = "3 号宿舍楼",
            publishedMinutesAgo = 240,
            sellerName = "赵同学",
            coverRes = R.drawable.bg_photo_3
        ),
        Product(
            id = "p1004",
            title = "斯伯丁 7 号篮球，送打气筒",
            price = 45.0,
            originalPrice = 129.0,
            category = ProductCategory.SPORTS,
            condition = ProductCondition.FAIR,
            description = "打了一年多，表面有正常磨损，弹性没问题，附赠打气筒。",
            location = "东区篮球场",
            publishedMinutesAgo = 600,
            sellerName = "陈同学",
            coverRes = R.drawable.bg_photo_4
        ),
        Product(
            id = "p1005",
            title = "优衣库 男款连帽卫衣 L 码",
            price = 40.0,
            originalPrice = 199.0,
            category = ProductCategory.CLOTHING,
            condition = ProductCondition.LIKE_NEW,
            description = "只穿过两次，尺码买大了，洗过一次无起球。",
            location = "菜鸟驿站",
            publishedMinutesAgo = 1500,
            sellerName = "周同学",
            coverRes = R.drawable.bg_photo_5
        ),
        Product(
            id = "p1006",
            title = "考研英语真题（2010-2023）",
            price = 15.0,
            originalPrice = 58.0,
            category = ProductCategory.BOOK,
            condition = ProductCondition.GOOD,
            description = "做过前 5 套，后面都是空白，可以刷题用。",
            location = "教学楼 B 座",
            publishedMinutesAgo = 2900,
            sellerName = "吴同学",
            coverRes = R.drawable.bg_photo_6
        )
    )

    fun findById(id: String): Product? = products.firstOrNull { it.id == id }
}
