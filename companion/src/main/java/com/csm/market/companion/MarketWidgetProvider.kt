package com.csm.market.companion

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 伴生模块 P1 · 校园集市桌面小组件。
 *
 * 职责边界：
 * - 只读：通过 `content://com.csm.market.widget/summary` 读主 App 的数据；
 * - 无网络：广播的 onUpdate 只有几秒预算，联网请求必然超时，未读数由主 App 预先缓存好；
 * - 不持有数据：点一下卡片就深链回主 App，真正的内容在 App 里看。
 *
 * 降级策略：查不到数据（首次安装 / 主 App 被停用 / provider 异常）时显示引导文案，
 * 而不是空白或崩溃——小组件是「看一眼」，不能因为它挂了影响桌面。
 */
class MarketWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> render(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MarketWidgetProvider::class.java))
            ids.forEach { id -> render(context, manager, id) }
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_market)
        val summary = readSummary(context)

        if (summary == null) {
            views.setTextViewText(R.id.widget_counts, context.getString(R.string.widget_empty_hint))
            views.setTextViewText(R.id.widget_latest, context.getString(R.string.widget_empty_latest))
            views.setTextViewText(R.id.widget_updated, "")
        } else {
            views.setTextViewText(
                R.id.widget_counts,
                context.getString(
                    R.string.widget_counts,
                    summary.listings,
                    summary.favorites,
                    summary.unread
                )
            )
            views.setTextViewText(
                R.id.widget_latest,
                summary.latestTitle
                    ?.let { context.getString(R.string.widget_latest, it, summary.latestPrice) }
                    ?: context.getString(R.string.widget_latest_none)
            )
            views.setTextViewText(
                R.id.widget_updated,
                context.getString(R.string.widget_updated, formatTime(summary.updatedAt))
            )
        }

        // 整块卡片点开 App；这里用深链而不是显式类名，伴生模块不需要编译期依赖主 App 的类。
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))

        manager.updateAppWidget(widgetId, views)
    }

    private fun readSummary(context: Context): Summary? = try {
        context.contentResolver.query(SUMMARY_URI, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                Summary(
                    listings = cursor.getInt(cursor.getColumnIndexOrThrow("listings_count")),
                    favorites = cursor.getInt(cursor.getColumnIndexOrThrow("favorites_count")),
                    unread = cursor.getInt(cursor.getColumnIndexOrThrow("unread_count")),
                    latestTitle = cursor.getString(cursor.getColumnIndexOrThrow("latest_title")),
                    latestPrice = cursor.getDouble(cursor.getColumnIndexOrThrow("latest_price")),
                    updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
                )
            }
        }
    } catch (e: Exception) {
        // 主 App 未安装/被卸载数据被清掉/权限被停用：按「还没有数据」处理
        null
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DEEP_LINK_OPEN)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(context, 100, intent, pendingFlags())
    }

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, MarketWidgetProvider::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(context, 101, intent, pendingFlags())
    }

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))

    private data class Summary(
        val listings: Int,
        val favorites: Int,
        val unread: Int,
        val latestTitle: String?,
        val latestPrice: Double,
        val updatedAt: Long
    )

    companion object {
        const val ACTION_REFRESH = "com.csm.market.companion.ACTION_REFRESH"

        /** 主 App 回到前台时调用一次：让桌面上的小组件立刻反映最新的在售/收藏数。 */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MarketWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, MarketWidgetProvider::class.java).setAction(ACTION_REFRESH)
            )
        }

        /** 与主 App 清单里 `<data android:scheme="csm" android:host="widget" />` 对应。 */
        private const val DEEP_LINK_OPEN = "csm://widget/open"

        private val SUMMARY_URI: Uri = Uri.parse("content://com.csm.market.widget/summary")
    }
}
