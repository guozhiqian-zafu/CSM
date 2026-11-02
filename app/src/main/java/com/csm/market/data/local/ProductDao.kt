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

    /** 最新发布的一条，给桌面小组件的「最新上架」用。 */
    @Query("SELECT * FROM products ORDER BY publishedAt DESC LIMIT 1")
    suspend fun latest(): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 删掉不在 [keepIds] 里的商品：服务端把商品下架后，本地缓存也要跟着消失。
     *
     * 注意调用方必须保证 [keepIds] 非空——SQLite 的 `NOT IN ()` 是语法错误，
     * 而「服务端返回空列表」多半是异常情况，此时宁可不清理也不要把缓存删光。
     */
    @Query("DELETE FROM products WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<String>)
}
