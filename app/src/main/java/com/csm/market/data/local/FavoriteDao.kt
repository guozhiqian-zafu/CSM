package com.csm.market.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 收藏表访问对象。
 *
 * 所有「读」都返回 [Flow]：收藏表一变，详情页的按钮状态和收藏页的列表会自动刷新，
 * 不需要页面之间互相回调（也不需要 EventBus 那类全局总线）。
 */
@Dao
interface FavoriteDao {

    /** 收藏的商品 id，按收藏时间倒序。 */
    @Query("SELECT productId FROM favorites ORDER BY createdAt DESC")
    fun observeFavoriteIds(): Flow<List<String>>

    /** 某个商品是否被收藏，详情页按钮订阅它。 */
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE productId = :productId)")
    fun observeIsFavorite(productId: String): Flow<Boolean>

    /**
     * 收藏页的数据源。
     *
     * 用 INNER JOIN 而不是把商品字段冗余进 favorites 表：商品改价后收藏页显示新价格，
     * 也不会因为商品被删除留下「幽灵收藏」。
     */
    @Query(
        """
        SELECT p.* FROM products p
        INNER JOIN favorites f ON f.productId = p.id
        ORDER BY f.createdAt DESC
        """
    )
    fun observeFavoriteProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE productId = :productId")
    suspend fun delete(productId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE productId = :productId)")
    suspend fun isFavorite(productId: String): Boolean

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int
}
