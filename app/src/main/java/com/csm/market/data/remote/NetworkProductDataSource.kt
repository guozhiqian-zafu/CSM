package com.csm.market.data.remote

import com.csm.market.data.model.Product
import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductQuery
import com.csm.market.data.remote.dto.CreateProductRequest
import com.csm.market.data.remote.dto.PriceStatsDto
import com.csm.market.data.remote.dto.toDomain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Retrofit 实现。
 *
 * 这一层只做三件事：拼参数、解包信封、DTO → 领域模型。
 * 任何「失败要不要用缓存」的判断都不在这里——那是仓库层的职责，
 * 数据源保持纯粹，才好替换（以后换 GraphQL 只改这个文件）。
 */
class NetworkProductDataSource(
    private val api: CsmApi = ApiClient.api,
    private val clock: () -> Long = System::currentTimeMillis
) : ProductRemoteDataSource {

    override suspend fun fetchProducts(
        category: ProductCategory?,
        page: Int,
        size: Int
    ): List<Product> {
        val pageDto = api.listProducts(
            page = page,
            size = size,
            category = category?.name
        ).unwrap() ?: return emptyList()
        val now = clock()
        return pageDto.items.map { it.toDomain(now) }
    }

    override suspend fun searchProducts(query: ProductQuery, page: Int, size: Int): List<Product> {
        val pageDto = api.listProducts(
            page = page,
            size = size,
            category = query.category?.name,
            keyword = query.trimmedKeyword.ifEmpty { null },
            condition = query.condition?.name,
            minPrice = query.minPrice,
            maxPrice = query.maxPrice,
            sort = query.sort.wireValue
        ).unwrap() ?: return emptyList()
        val now = clock()
        return pageDto.items.map { it.toDomain(now) }
    }

    override suspend fun fetchProduct(id: String): Product? {
        val dto = api.product(id).unwrap() ?: return null
        return dto.toDomain(clock())
    }

    override suspend fun publishProduct(request: CreateProductRequest): Product {
        val dto = api.createProduct(request).unwrap()
            ?: throw ApiException(code = -1, httpStatus = 200, message = "发布失败：响应体为空")
        return dto.toDomain(clock())
    }

    override suspend fun fetchPriceStats(category: ProductCategory?): PriceStatsDto {
        return api.priceStats(category?.name).unwrap() ?: PriceStatsDto()
    }

    override suspend fun uploadImage(file: File): String {
        // 文件名和 MIME 都固定成 jpeg：上游 ImageCompressor 已经统一转成 JPEG 了
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = file.name,
            body = file.asRequestBody("image/jpeg".toMediaType())
        )
        val upload = api.uploadImage(part).unwrap()
            ?: throw ApiException(code = -1, httpStatus = 200, message = "图片上传失败：响应体为空")
        return upload.url
    }
}
