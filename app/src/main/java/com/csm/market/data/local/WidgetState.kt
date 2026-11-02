package com.csm.market.data.local

import android.content.Context

/**
 * 小组件需要跨进程/跨组件读取的少量状态。
 *
 * 为什么不用 DataStore：这里只有一个整数，且读它的是 ContentProvider 与
 * AppWidgetProvider（可能是短生命周期、无协程作用域的同步调用），
 * SharedPreferences 的同步读取更合适，也避免为了一个角标引入异步初始化竞态。
 */
object WidgetState {

    private const val PREFS = "widget_state"
    private const val KEY_UNREAD = "unread_count"

    fun unreadCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_UNREAD, 0)

    fun saveUnreadCount(context: Context, count: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_UNREAD, count.coerceAtLeast(0))
            .apply()
    }
}
