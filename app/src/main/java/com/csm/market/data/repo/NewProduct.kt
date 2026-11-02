package com.csm.market.data.repo

import com.csm.market.data.model.ProductCategory
import com.csm.market.data.model.ProductCondition

/**
 * 发布商品时的输入模型。
 *
 * 单独定义而不是复用 DTO：仓库接口属于领域层，不应该依赖 Retrofit 的 `@Serializable` 类型；
 * 以后服务端字段改名，只需要改 `NetworkProductDataSource` 里的那一处映射。
 */
data class NewProduct(
    val title: String,
    val price: Double,
    val originalPrice: Double?,
    val category: ProductCategory,
    val condition: ProductCondition,
    val description: String,
    val location: String,
    val sellerName: String = "我"
)
