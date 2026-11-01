package com.csm.market

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.csm.market.databinding.ActivityMainBinding

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
    }

    private companion object {
        val TOP_LEVEL_DESTINATIONS = setOf(
            R.id.homeFragment,
            R.id.publishFragment,
            R.id.favoritesFragment,
            R.id.profileFragment
        )
    }
}
