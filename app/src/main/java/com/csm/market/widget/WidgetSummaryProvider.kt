package com.csm.market.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.csm.market.data.local.CsmDatabase
import com.csm.market.data.local.WidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 主 App 提供给伴生模块（桌面小组件）的**只读**数据出口。
 *
 * 为什么用 ContentProvider 而不是让小组件直接开 Room：
 * - 小组件是独立模块，不该知道主 App 的数据库长什么样，否则改表结构就编译不过；
 * - ContentProvider 是 Android 自带的跨组件契约，`ContentResolver.query` 一行就能读，
 *   以后就算把伴生模块做成独立 APK，这条链路也不用改。
 *
 * 契约（`content://com.csm.market.widget/summary`，单行）：
 * | 列名 | 含义 |
 * | --- | --- |
 * | listings_count | 本地缓存的在售商品数 |
 * | favorites_count | 收藏数 |
 * | unread_count | 未读留言数（由 App 写入的缓存值，小组件自己不发网络请求） |
 * | latest_title | 最新上架商品标题，可为空 |
 * | latest_price | 最新上架商品价格，可为空 |
 * | updated_at | 生成这一行的时间戳，小组件用它显示「更新于 …」 |
 */
class WidgetSummaryProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (MATCHER.match(uri) != SUMMARY) return null
        val context = context ?: return null

        // ContentProvider 的 query 跑在 Binder 线程，不是主线程，所以这里可以阻塞等待 Room。
        val snapshot = runBlocking(Dispatchers.IO) {
            val db = CsmDatabase.get(context)
            val products = db.productDao()
            val favorites = db.favoriteDao()
            Snapshot(
                listings = products.count(),
                favorites = favorites.count(),
                latest = products.latest()
            )
        }

        val cursor = MatrixCursor(COLUMNS)
        cursor.addRow(
            arrayOf<Any?>(
                snapshot.listings,
                snapshot.favorites,
                WidgetState.unreadCount(context),
                snapshot.latest?.title,
                snapshot.latest?.price,
                System.currentTimeMillis()
            )
        )
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.csm.market.widget-summary"

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("小组件只读，不接受写入")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("小组件只读，不接受删除")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("小组件只读，不接受更新")

    private data class Snapshot(
        val listings: Int,
        val favorites: Int,
        val latest: com.csm.market.data.local.ProductEntity?
    )

    companion object {
        const val AUTHORITY = "com.csm.market.widget"
        const val PATH_SUMMARY = "summary"

        /** 小组件订阅这个 Uri 就能在数据变化时收到通知。 */
        val SUMMARY_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SUMMARY")

        private const val SUMMARY = 1
        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_SUMMARY, SUMMARY)
        }

        private val COLUMNS = arrayOf(
            "listings_count",
            "favorites_count",
            "unread_count",
            "latest_title",
            "latest_price",
            "updated_at"
        )
    }
}
