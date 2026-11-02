package com.csm.market.data.repo

import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import kotlinx.coroutines.flow.Flow

/**
 * 商品仓库接口。
 *
 * 页面只依赖这个接口，不关心数据来自 Room、网络还是缓存。
 * 第 5 次提交接入网络时，实现类换成「网络优先 + Room 兜底」即可，
 * ViewModel 与 UI 一行都不用改——这就是分层的收益，也是答辩时的加分点。
 */
interface ProductRepository {

    /** 按分类观察商品列表；[category] 传 null 表示全部。 */
    fun observeProducts(category: ProductCategory? = null): Flow<List<Product>>

    /** 按 id 取单个商品，不存在返回 null（不是抛异常）。 */
    suspend fun getProduct(id: String): Product?

    /** 新增或覆盖一条商品；发布页会用到。 */
    suspend fun saveProduct(product: Product)

    /** 商品总数，桌面小组件与空态判断会用到。 */
    suspend fun countProducts(): Int
}
