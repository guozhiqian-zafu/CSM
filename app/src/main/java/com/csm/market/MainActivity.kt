package com.csm.market

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.csm.market.companion.MarketWidgetProvider
import com.csm.market.databinding.ActivityMainBinding
import com.csm.market.util.NotificationHelper

/**
 * 单 Activity 架构：所有页面都是 Fragment，由 Navigation 组件管理。
 *
 * 好处：
 * 1. 底部导航与返回栈天然一致；
 * 2. 详情页可以隐藏底部栏，避免用户在二级页面误触；
 * 3. 后续做桌面小组件深链时，只需要一个 DeepLink 入口指向同一个 graph。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /**
     * Android 13+ 通知需要运行时授权：这里在首页显示后申请一次。
     * 拒绝也不影响使用，只是收不到未读留言提醒（Worker 里会再检查一次权限）。
     *
     * 注意：registerForActivityResult 必须在 STARTED 之前注册，
     * 所以写成属性初始化，不能在 onResume 里临时注册。
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝静默降级 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        // 只有一级页面显示底部导航，详情页隐藏
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = destination.id in TOP_LEVEL_DESTINATIONS
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时顺手刷新桌面小组件：数据可能已经因为发布/收藏而变化
        MarketWidgetProvider.requestUpdate(this)
    }

    /**
     * Android 13+ 通知需要运行时授权：首页第一次显示时申请一次。
     * 拒绝也不影响使用，只是收不到未读留言提醒（Worker 里会再检查一次权限）。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (NotificationHelper.canPostNotifications(this)) return

        // 只问一次：用户拒绝过就不再打扰，设置里可以随时手动打开
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

        val TOP_LEVEL_DESTINATIONS = setOf(
            R.id.homeFragment,
            R.id.publishFragment,
            R.id.favoritesFragment,
            R.id.profileFragment
        )
    }
}
