package com.csm.market.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 服务端统一响应信封：`{ code, message, data }`。
 *
 * 泛型 + `@Serializable`：kotlinx.serialization 会在编译期为每个具体 T 生成序列化器，
 * 所以 `ApiEnvelope<ProductPageDto>` 不需要手写解析代码。
 */
@Serializable
data class ApiEnvelope<T>(
    val code: Int = 0,
    val message: String = "ok",
    val data: T? = null
)

/** 卖家信息，只用来展示头像首字和昵称。 */
@Serializable
data class SellerDto(
    val id: String = "",
    val name: String = ""
)

/**
 * 商品 DTO：字段名与服务端 `ProductDto` 完全一致。
 *
 * 注意这里**不是**直接给 UI 用的模型：`category` 是字符串、`publishedAt` 是 ISO 时间，
 * 都要经过 `toDomain()` 才变成带枚举和「几分钟前」的 `Product`。
 * 直接把 DTO 丢给 UI 是很多项目腐化的开始（接口一改，UI 全崩）。
 */
@Serializable
data class ProductDto(
    val id: String,
    val title: String,
    val price: Double,
    val originalPrice: Double? = null,
    val category: String = "OTHER",
    val condition: String = "GOOD",
    val description: String = "",
    val location: String = "",
    val publishedAt: String = "",
    val seller: SellerDto = SellerDto(),
    val cover: String? = null,
    val images: List<String> = emptyList(),
    /** 服务端算好的建议价，客户端直接展示，不重复实现规则。 */
    val suggestPrice: Double? = null,
    /** 服务端判定：售价 ≤ 建议价 × 70%。 */
    val isBargain: Boolean = false
)

/** 分页结果。 */
@Serializable
data class ProductPageDto(
    val page: Int = 1,
    val size: Int = 20,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val items: List<ProductDto> = emptyList()
)

/** 发布商品请求体，字段名与服务端 `CreateProductRequest` 对齐。 */
@Serializable
data class CreateProductRequest(
    val title: String,
    val price: Double,
    val originalPrice: Double? = null,
    val category: String,
    val condition: String,
    val description: String = "",
    val location: String = "",
    val sellerName: String = "我",
    /** 图片先 POST /api/uploads 拿到相对地址，再随商品一起提交 */
    val images: List<String> = emptyList()
)

/** 图片上传结果：url 形如 /uploads/xxx.jpg，bytes 是服务端实际落盘的字节数。 */
@Serializable
data class UploadDto(
    val url: String = "",
    val bytes: Int = 0
)

/** 价格统计，发布页的定价助手用它做「同类目参考」。 */
@Serializable
data class PriceStatsDto(
    val category: String = "",
    val median: Double = 0.0,
    val p25: Double = 0.0,
    val p75: Double = 0.0,
    val sampleCount: Int = 0,
    val updatedAt: String = ""
)

@Serializable
data class MessageDto(
    val id: String = "",
    val productId: String = "",
    val senderName: String = "",
    val content: String = "",
    val createdAt: String = "",
    val isMine: Boolean = false,
    val read: Boolean = false
)

@Serializable
data class CreateMessageRequest(
    val content: String,
    val senderName: String = "我"
)