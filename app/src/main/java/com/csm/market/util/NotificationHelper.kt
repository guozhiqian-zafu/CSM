package com.csm.market.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.csm.market.MainActivity
import com.csm.market.R

/**
 * 通知相关的集中处理。
 *
 * 降级：Android 13 起通知需要运行时授权，用户拒绝时不弹通知、也不报错——
 * 提醒只是锦上添花，不能因为它打断用户浏览商品。
 */
object NotificationHelper {

    const val CHANNEL_UNREAD = "csm-unread"
    private const val NOTIFICATION_ID_UNREAD = 1001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_UNREAD,
            context.getString(R.string.notification_channel_unread),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_unread_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyUnread(context: Context, unread: Int) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UNREAD,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_UNREAD)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(context.getString(R.string.notification_unread_title))
            .setContentText(context.getString(R.string.notification_unread_text, unread))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UNREAD, notification)
    }
}
