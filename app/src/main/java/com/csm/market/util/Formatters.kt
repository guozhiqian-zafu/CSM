package com.csm.market.util

import java.util.Locale

/** 价格展示：整数不显示小数，有小数时保留两位。 */
fun formatPrice(value: Double): String {
    return if (value % 1.0 == 0.0) {
        String.format(Locale.CHINA, "¥%.0f", value)
    } else {
        String.format(Locale.CHINA, "¥%.2f", value)
    }
}

/** 把「多少分钟前」转成列表里更好读的相对时间。 */
fun formatRelativeTime(minutesAgo: Int): String = when {
    minutesAgo < 1 -> "刚刚"
    minutesAgo < 60 -> "${minutesAgo} 分钟前"
    minutesAgo < 60 * 24 -> "${minutesAgo / 60} 小时前"
    minutesAgo < 60 * 24 * 30 -> "${minutesAgo / (60 * 24)} 天前"
    else -> "${minutesAgo / (60 * 24 * 30)} 个月前"
}
