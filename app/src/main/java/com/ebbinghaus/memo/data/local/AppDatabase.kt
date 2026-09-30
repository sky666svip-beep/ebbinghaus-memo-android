package com.ebbinghaus.memo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ebbinghaus.memo.data.local.converter.Converters
import com.ebbinghaus.memo.data.local.dao.KnowledgeMemoDao
import com.ebbinghaus.memo.data.local.dao.ReviewTaskDao
import com.ebbinghaus.memo.data.local.dao.UserSettingsDao
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity

/**
 * 艾宾浩斯备忘录应用 Room 核心数据库
 *
 * 版本演进：
 * - v1：初版（knowledge_memos / review_tasks / user_settings）
 * - v2：为 knowledge_memos 追加 `deletedAt`（软删除，E06 回收站），**唯一 schema 变更**
 */
@Database(
    entities = [
        KnowledgeMemoEntity::class,
        ReviewTaskEntity::class,
        UserSettingsEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun knowledgeMemoDao(): KnowledgeMemoDao
    abstract fun reviewTaskDao(): ReviewTaskDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        /** v1 → v2 迁移 SQL：仅追加软删除列（INTEGER，可空） */
        const val MIGRATION_1_2_SQL = "ALTER TABLE knowledge_memos ADD COLUMN deletedAt INTEGER"

        /**
         * v1 → v2 迁移：为 knowledge_memos 追加 `deletedAt` 列。
         *
         * 迁移不触碰任何既有数据，旧库 memo 条数保持不变，`deletedAt` 全部为 NULL（存活态）。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(MIGRATION_1_2_SQL)
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ebbinghaus_memo.db"
                ).addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // 显式激活底层 SQLite 连接池外键约束支持，确保 ReviewTaskEntity 的 ON DELETE CASCADE 正常触发
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                })
                    // 唯一 schema 变更的迁移路径（v1 → v2）
                    .addMigrations(MIGRATION_1_2)
                    // 仅降级兜底：降级（新库被旧代码打开）时重建；
                    // 绝不使用全量 fallbackToDestructiveMigration()，以免升级场景静默清空用户数据
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
