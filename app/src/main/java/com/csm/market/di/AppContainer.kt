package com.csm.market.di

import android.content.Context
import com.csm.market.data.local.CsmDatabase
import com.csm.market.data.repo.LocalProductRepository
import com.csm.market.data.repo.ProductRepository

/**
 * 极简依赖容器（手动 DI）。
 *
 * 课程要求的是「架构清晰」，不是「必须用 Hilt」：这里用一个懒加载容器把
 * 数据库、仓库的创建集中在一处，ViewModel 通过 Factory 从这里取依赖。
 * 好处是零注解处理器、编译快、依赖关系一眼可见；以后要换 Hilt，
 * 改动也只在 Factory 里，不影响业务代码。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: CsmDatabase by lazy { CsmDatabase.get(appContext) }

    val productRepository: ProductRepository by lazy {
        LocalProductRepository(database.productDao())
    }
}
