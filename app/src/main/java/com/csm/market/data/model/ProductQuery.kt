package com.csm.market.data.model

/**
 * 排序方式。
 *
 * [wireValue] 是传给服务端的字符串，必须与服务端 `sort` 白名单一致；
 * 不在客户端拼字符串，避免「改了枚举忘了改接口」这种低级错误。
 */
enum class ProductSort(val label: String, val wireValue: String) {
    LATEST("最新发布", "latest"),
    PRICE_ASC("价格从低到高", "priceAsc"),
    PRICE_DESC("价格从高到低", "priceDesc"),
}

/**
 * 一次搜索的全部条件。
 *
 * 用 data class 而不是一堆方法参数：条件多了以后（关键词 + 分类 + 成色 + 价格区间 + 排序），
 * ViewModel 里只需要持有一个不可变对象，任何一处变化都能用 `copy()` 精确表达，
 * 也方便做「条件是否为空 → 显示空态」的判断。
 */
data class ProductQuery(
    val keyword: String = "",
    val category: ProductCategory? = null,
    val condition: ProductCondition? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sort: ProductSort = ProductSort.LATEST,
) {

    /** 去掉首尾空格后的关键词；全空格视为没输入。 */
    val trimmedKeyword: String get() = keyword.trim()

    /** 是否真的带上了筛选条件（决定空态文案是「没搜到」还是「输入关键词试试」）。 */
    val hasConditions: Boolean
        get() = trimmedKeyword.isNotEmpty() ||
            category != null ||
            condition != null ||
            minPrice != null ||
            maxPrice != null
}
