package com.csm.market.data.remote

import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductQuery
import com.csm.market.data.remote.dto.CreateProductRequest
import com.csm.market.data.remote.dto.PriceStatsDto
import java.io.File

/**
 * 商品相关的远端能力。
 *
 * 抽成接口而不是直接用 [CsmApi]：仓库和 ViewModel 在单元测试里塞一个假实现就行，
 * 不需要启动 MockWebServer，测试跑得又快又稳。
 */
interface ProductRemoteDataSource {
    suspend fun fetchProducts(category: ProductCategory?, page: Int = 1, size: Int = 30): List<Product>

    suspend fun searchProducts(query: ProductQuery, page: Int = 1, size: Int = 30): List<Product>

    suspend fun fetchProduct(id: String): Product?

    /** 我发布的商品（服务端按 sellerId=u-me 过滤）。 */
    suspend fun fetchMyListings(): List<Product>

    /** 下架自己发布的商品；服务端做软删除，返回后列表里就不该再有它。 */
    suspend fun takeDownProduct(id: String)

    suspend fun publishProduct(request: CreateProductRequest): Product

    suspend fun fetchPriceStats(category: ProductCategory?): PriceStatsDto

    /** 上传一张已压缩好的图片，返回服务端给的相对地址（`/uploads/xxx.jpg`）。 */
    suspend fun uploadImage(file: File): String
}
