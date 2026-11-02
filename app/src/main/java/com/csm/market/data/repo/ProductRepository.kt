package com.csm.market.data.repo

import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductQuery
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

    /**
     * 搜索商品：关键词、分类、成色、价格区间、排序一次性交给服务端。
     *
     * 返回 List 而不是 Flow：搜索结果是一次性的快照，不像首页那样需要「表变了自动刷新」，
     * 用户每次改条件都重新请求。结果会顺带写入缓存，点进详情时即使断网也能看。
     */
    suspend fun search(query: ProductQuery): List<Product>

    /** 按 id 取单个商品，不存在返回 null（不是抛异常）。 */
    suspend fun getProduct(id: String): Product?

    /** 新增或覆盖一条商品；发布页会用到。 */
    suspend fun saveProduct(product: Product)

    /**
     * 发布商品：先逐张上传图片拿到服务端地址，再提交商品。
     *
     * [images] 是已经压缩好的本地文件；[onProgress] 的签名是 (已完成, 总数)，
     * 发布页据此显示「正在上传第 2/3 张」——上传 3 张图要好几秒，没有进度用户会以为卡死。
     */
    suspend fun publish(
        product: NewProduct,
        images: List<java.io.File> = emptyList(),
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Product

    /** 商品总数，桌面小组件与空态判断会用到。 */
    suspend fun countProducts(): Int

    /**
     * 取某个分类的价格统计，用于发布页的智能定价提示。
     *
     * 返回 null 表示「这次拿不到统计」（断网、服务端没这个分类），
     * 调用方应当退回到分类经验基准价继续给建议——定价提示是锦上添花，
     * 不能因为它失败就挡住发布流程。
     */
    suspend fun priceStats(category: ProductCategory?): com.csm.market.data.model.PriceStats?
}
