package com.csm.market.data.remote

import com.csm.market.data.remote.dto.ApiEnvelope
import com.csm.market.data.remote.dto.CreateMessageRequest
import com.csm.market.data.remote.dto.CreateProductRequest
import com.csm.market.data.remote.dto.MessageDto
import com.csm.market.data.remote.dto.PriceStatsDto
import com.csm.market.data.remote.dto.ProductDto
import com.csm.market.data.remote.dto.ProductPageDto
import com.csm.market.data.remote.dto.UploadDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 服务端接口声明（Retrofit）。
 *
 * 为什么返回 `Response<ApiEnvelope<T>>` 而不是直接返回 `ApiEnvelope<T>`：
 * 后者遇到 400/404 会抛 `HttpException`，错误体里的 `code`/`message` 就拿不到，
 * 前端只能显示「请求失败」。用 `Response` 自己判状态码，才能把服务端写好的
 * 「缺少必填字段：category」「商品不存在或已下架」原样展示给用户。
 */
interface CsmApi {

    @GET("api/products")
    suspend fun listProducts(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 30,
        @Query("category") category: String? = null,
        @Query("keyword") keyword: String? = null,
        @Query("condition") condition: String? = null,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null,
        @Query("sort") sort: String? = null
    ): Response<ApiEnvelope<ProductPageDto>>

    @GET("api/products/{id}")
    suspend fun product(@Path("id") id: String): Response<ApiEnvelope<ProductDto>>

    @POST("api/products")
    suspend fun createProduct(@Body body: CreateProductRequest): Response<ApiEnvelope<ProductDto>>

    @DELETE("api/products/{id}")
    suspend fun deleteProduct(@Path("id") id: String): Response<ApiEnvelope<Map<String, String>>>

    /** 某商品的留言，按时间正序。 */
    @GET("api/products/{id}/messages")
    suspend fun listMessages(
        @Path("id") productId: String
    ): Response<ApiEnvelope<List<MessageDto>>>

    /** 发一条留言。 */
    @POST("api/products/{id}/messages")
    suspend fun postMessage(
        @Path("id") productId: String,
        @Body request: CreateMessageRequest
    ): Response<ApiEnvelope<MessageDto>>

    /** 未读留言数。 */
    @GET("api/messages/unread-count")
    suspend fun unreadCount(): Response<ApiEnvelope<Map<String, Int>>>

    @GET("api/price-stats")
    suspend fun priceStats(@Query("category") category: String? = null): Response<ApiEnvelope<PriceStatsDto>>

    /**
     * 上传一张图片。
     *
     * 用 `@Multipart` + OkHttp 的 `MultipartBody.Part`：Retrofit 会把文件流直接写进请求体，
     * 不会把整张图先读成 ByteArray 再复制一份，内存占用只有一块缓冲区。
     */
    @Multipart
    @POST("api/uploads")
    suspend fun uploadImage(@Part file: MultipartBody.Part): Response<ApiEnvelope<UploadDto>>
}
