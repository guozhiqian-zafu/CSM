package com.csm.market.data.repo

import com.csm.market.data.local.FavoriteDao
import com.csm.market.data.local.FavoriteEntity
import com.csm.market.data.local.toDomain
import com.csm.market.data.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 收藏仓库的本地实现。
 *
 * [clock] 做成可注入的，测试里传入固定时间就能断言「收藏时间」而不依赖真实时钟。
 */
class LocalFavoriteRepository(
    private val dao: FavoriteDao,
    private val clock: () -> Long = System::currentTimeMillis
) : FavoriteRepository {

    override fun observeFavorites(): Flow<List<Product>> =
        dao.observeFavoriteProducts().map { entities ->
            entities.map { it.toDomain(clock()) }
        }

    override fun observeIsFavorite(productId: String): Flow<Boolean> =
        dao.observeIsFavorite(productId)

    override suspend fun toggleFavorite(productId: String): Boolean {
        // 先读后写，中间没有加锁：收藏是幂等的「切换」操作，并发点两下最坏结果
        // 是回到原状态，不会产生重复行（productId 是主键，插入用 REPLACE）。
        return if (dao.isFavorite(productId)) {
            dao.delete(productId)
            false
        } else {
            dao.insert(FavoriteEntity(productId = productId, createdAt = clock()))
            true
        }
    }

    override suspend fun countFavorites(): Int = dao.count()
}
