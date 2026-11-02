package com.csm.market.data.repo

import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 商品仓库接口。
 *
 * 页面只依赖这个接口，不关心数据来自 Room、网络还是缓存。
 * 第 6 次提交把实现换成了「网络优先 + Room 缓存」，ViewModel 与 UI 只是多订阅了一个
 * [isOffline]，其余一行没改——这就是分层的收益。
 *
 * 注意 [isOffline] 也是一个 Flow 而不是 Boolean：数据在请求过程中才变成离线，
 * UI 需要「数据到了 → 横幅同时出现」这种联动，用布尔字段就没法通知界面刷新。
 */
interface ProductRepository {

    /** 是否正在展示缓存数据（最近一次网络请求失败但本地有旧数据）。 */
    val isOffline: StateFlow<Boolean>

    /** 按分类观察商品列表；[category] 传 null 表示全部。 */
    fun observeProducts(category: ProductCategory? = null): Flow<List<Product>>

    /** 按 id 取单个商品，不存在返回 null（不是抛异常）。 */
    suspend fun getProduct(id: String): Product?

    /** 新增或覆盖一条商品；发布页会用到。 */
    suspend fun saveProduct(product: Product)

    /** 商品总数，桌面小组件与空态判断会用到。 */
    suspend fun countProducts(): Int
}
