package com.csm.market.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 本地数据库。
 *
 * - 每加一张表都要把 `version` +1 并写迁移，不允许 `fallbackToDestructiveMigration`
 *   上线（那等于用户数据随时可能被清空）。
 * - 单例由 [get] 保证：Room 自己维护连接池，重复建库会浪费内存。
 */
@Database(
    entities = [ProductEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CsmDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    abstract fun favoriteDao(): FavoriteDao

    companion object {
        private const val DB_NAME = "csm-market.db"

        /**
         * v1 → v2：新增收藏表。
         *
         * 迁移写成「只加表、不动老表」，已经装了 v1 的用户升级后商品数据原样保留；
         * SQL 必须和 Room 生成的建表语句完全一致（列顺序、NOT NULL、主键），否则
         * 下次启动会抛 `Migration didn't properly handle` —— 所以这里不用 ALTER。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorites` (
                        `productId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`productId`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: CsmDatabase? = null

        fun get(context: Context): CsmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CsmDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
