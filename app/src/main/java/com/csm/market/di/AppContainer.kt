package com.csm.market.di

import android.content.Context
import com.csm.market.BuildConfig
import com.csm.market.data.local.CsmDatabase
import com.csm.market.data.remote.ApiClient
import com.csm.market.data.remote.NetworkProductDataSource
import com.csm.market.data.remote.ProductRemoteDataSource
import com.csm.market.data.repo.DefaultProductRepository
import com.csm.market.data.repo.FavoriteRepository
import com.csm.market.data.repo.LocalFavoriteRepository
import com.csm.market.data.repo.ProductRepository

/**
 * 极简依赖容器（手动 DI）。
 *
 * 课程要求的是「架构清晰」，不是「必须用 Hilt」：这里用一个懒加载容器把
 * 数据库、网络、仓库的创建集中在一处，ViewModel 通过 Factory 从这里取依赖。
 * 好处是零注解处理器、编译快、依赖关系一眼可见；以后要换 Hilt，
 * 改动也只在 Factory 里，不影响业务代码。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: CsmDatabase by lazy { CsmDatabase.get(appContext) }

    /**
     * 远端数据源。
     *
     * 基址从 BuildConfig 读，默认 `http://10.0.2.2:8080/`——这是 Android 模拟器访问
     * 宿主机 `localhost` 的固定别名，MuMu、夜神、官方 AVD 都适用。
     * 用真机演示时把 app/build.gradle.kts 里的 API_BASE_URL 改成电脑局域网 IP 即可。
     */
    private val remoteDataSource: ProductRemoteDataSource by lazy {
        NetworkProductDataSource(ApiClient.create(BuildConfig.API_BASE_URL))
    }

    val productRepository: ProductRepository by lazy {
        DefaultProductRepository(database.productDao(), remoteDataSource)
    }

    val favoriteRepository: FavoriteRepository by lazy {
        LocalFavoriteRepository(database.favoriteDao())
    }
}
