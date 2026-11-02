package com.csm.market.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.csm.market.data.repo.RemoteMessageRepository
import com.csm.market.data.remote.ApiClient
import com.csm.market.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * 未读留言提醒：后台周期任务。
 *
 * 为什么用 WorkManager 而不是轮询或前台服务：
 * - 提醒是「可以晚一点发生」的任务，不需要常驻进程，WorkManager 由系统统一调度，
 *   会考虑 Doze、省电模式和网络可用性，比自建定时器省电也更稳定；
 * - 约束了 `NetworkType.CONNECTED`，断网时任务自动延后，不会白白失败。
 *
 * 重复策略用 KEEP：每次 App 启动都会 enqueue，KEEP 保证不会叠加出多个周期任务。
 */
class UnreadReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = RemoteMessageRepository(
            api = ApiClient.api,
            appContext = applicationContext
        )
        return try {
            val unread = repository.unreadCount()
            if (unread > 0) {
                NotificationHelper.notifyUnread(applicationContext, unread)
            }
            Result.success()
        } catch (e: Exception) {
            // 网络抖动不算失败：让 WorkManager 按退避策略重试
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "unread-reminder"
        private const val REPEAT_HOURS = 6L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UnreadReminderWorker>(REPEAT_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
