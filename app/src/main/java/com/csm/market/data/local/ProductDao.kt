package com.csm.market.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 商品 DAO。
 *
 * 所有查询都返回 [Flow]，Room 会在表数据变化时自动重新执行并推送新值——
 * 这样「发布一条商品 → 首页列表自动出现」不需要手动刷新回调。
 */
@Dao
interface ProductDao {

    /** 全部商品，按发布时间倒序（新的在前）。 */
    @Query("SELECT * FROM products ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<ProductEntity>>

    /** 指定分类，同样按发布时间倒序。 */
    @Query("SELECT * FROM products WHERE category = :category ORDER BY publishedAt DESC")
    fun observeByCategory(category: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)
}
