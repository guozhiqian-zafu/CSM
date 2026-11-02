package com.csm.market.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 本地数据库。
 *
 * - 第 3 次提交只有商品表，后续依次补 `product_images`、`favorites`、`messages` 等，
 *   每加一张表都要把 `version` +1 并写迁移，不允许 `fallbackToDestructiveMigration`
 *   上线（那等于用户数据随时可能被清空）。
 * - 单例由 [get] 保证：Room 自己维护连接池，重复建库会浪费内存。
 */
@Database(
    entities = [ProductEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CsmDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    companion object {
        private const val DB_NAME = "csm-market.db"

        @Volatile
        private var instance: CsmDatabase? = null

        fun get(context: Context): CsmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CsmDatabase::class.java,
                    DB_NAME
                ).build().also { instance = it }
            }
    }
}
