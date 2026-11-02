package com.csm.market

import android.app.Application
import android.content.Context
import com.csm.market.di.AppContainer

/**
 * 应用入口。
 *
 * 全局只在这里创建一次依赖容器（数据库、仓库、后续的网络客户端），
 * 其他组件通过 [appContainer] 扩展取用，避免把初始化散落在各个 Activity/Fragment。
 */
class CsmApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** 在任意持有 Context 的地方取到依赖容器。 */
val Context.appContainer: AppContainer
    get() = (applicationContext as CsmApp).container
