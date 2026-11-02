package com.csm.market.data.remote

import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.remote.dto.CreateProductRequest
import com.csm.market.data.remote.dto.PriceStatsDto

/**
 * 远端数据源接口。
 *
 * 为什么不直接在仓库里 `ApiClient.api`：仓库要能在单元测试里替换成假实现，
 * 否则「网络失败回退缓存」这段逻辑就只能靠真机验证。接口只有 4 个方法，
 * 比引入 MockWebServer + Robolectric 轻得多。
 */
interface ProductRemoteDataSource {

    /** 拉取商品列表；[category] 为 null 表示全部。 */
    suspend fun fetchProducts(category: ProductCategory?, page: Int = 1, size: Int = 30): List<Product>

    /** 拉取单个商品；服务端明确返回 404 时返回 null（网络异常仍然抛）。 */
    suspend fun fetchProduct(id: String): Product?

    /** 发布商品，返回服务端落库后的完整对象（含生成的 id 与建议价）。 */
    suspend fun publishProduct(request: CreateProductRequest): Product

    /** 分类价格统计，发布页定价助手使用。 */
    suspend fun fetchPriceStats(category: ProductCategory?): PriceStatsDto
}
