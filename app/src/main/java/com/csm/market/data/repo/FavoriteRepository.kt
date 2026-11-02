package com.csm.market.data.repo

import com.csm.market.data.model.Product
import kotlinx.coroutines.flow.Flow

/**
 * 收藏仓库。
 *
 * 页面只认识这个接口：现在落地在 Room，将来如果收藏要同步到服务器，
 * 只需要换一个实现，详情页/收藏页一行都不用改。
 */
interface FavoriteRepository {

    /** 收藏的商品列表，按收藏时间倒序。 */
    fun observeFavorites(): Flow<List<Product>>

    /** 某个商品当前是否已收藏。 */
    fun observeIsFavorite(productId: String): Flow<Boolean>

    /**
     * 切换收藏状态。
     *
     * @return 切换后的状态：true 表示已收藏，false 表示已取消。
     */
    suspend fun toggleFavorite(productId: String): Boolean

    suspend fun countFavorites(): Int
}
