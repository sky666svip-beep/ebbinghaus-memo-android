package com.ebbinghaus.memo.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room v1 → v2 迁移仪器化测试（E06）
 *
 * ⚠️ 需真实设备/模拟器（`MigrationTestHelper` 依赖 instrumentation + 真实 SQLite，无法纯 JVM 运行）。
 * 本环境无模拟器，用例保留待有设备时执行；纯 JVM 兜底见 `SchemaV2ContractTest`。
 *
 * 断言口径（对应 PRD §3 E06-⑥）：
 * 1. 迁移后旧库 memo 条数不变；
 * 2. 既有 memo 的 `deletedAt` 全为 NULL（存活态）；
 * 3. 关联的 `review_tasks` 行完整保留（软删除不删任务）。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesMemosTasksAndSettings() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO knowledge_memos (id, content, notes, tags, createdAt, updatedAt) " +
                    "VALUES (1, '知识点A', '笔记A', '', 1000, 1000)"
            )
            execSQL(
                "INSERT INTO knowledge_memos (id, content, notes, tags, createdAt, updatedAt) " +
                    "VALUES (2, '知识点B', '', '', 2000, 2000)"
            )
            execSQL(
                "INSERT INTO review_tasks (id, memoId, stageLevel, dueDate, lastReviewDate, reviewCount, updatedAt) " +
                    "VALUES (10, 1, 3, 20000, NULL, 2, 1000)"
            )
            execSQL(
                "INSERT INTO review_tasks (id, memoId, stageLevel, dueDate, lastReviewDate, reviewCount, updatedAt) " +
                    "VALUES (11, 2, 1, 20001, 19999, 1, 2000)"
            )
            execSQL(
                "INSERT INTO user_settings (id, dailyReviewLimit, lastActiveDate, lastPromptedDate) " +
                    "VALUES (1, 30, NULL, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        // 1. memo 条数不变
        db.query("SELECT COUNT(*) FROM knowledge_memos").use { cursor ->
            cursor.moveToFirst()
            assertEquals("迁移不得丢失知识点", 2, cursor.getInt(0))
        }

        // 2. deletedAt 全部为 NULL
        db.query("SELECT COUNT(*) FROM knowledge_memos WHERE deletedAt IS NOT NULL").use { cursor ->
            cursor.moveToFirst()
            assertEquals("迁移后 deletedAt 应全为 NULL", 0, cursor.getInt(0))
        }

        // 3. review_tasks 完整保留
        db.query("SELECT COUNT(*) FROM review_tasks").use { cursor ->
            cursor.moveToFirst()
            assertEquals("软删除不删复习任务", 2, cursor.getInt(0))
        }
        db.query("SELECT stageLevel FROM review_tasks WHERE id = 10").use { cursor ->
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
        }

        // 4. user_settings 保留
        db.query("SELECT dailyReviewLimit FROM user_settings WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(30, cursor.getInt(0))
        }

        // 5. 迁移后数据整体无损：memo 内容与 task 关联均保持
        db.query("SELECT content FROM knowledge_memos WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals("迁移不得篡改 memo 内容", "知识点A", cursor.getString(0))
        }
        db.query("SELECT memoId FROM review_tasks WHERE id = 10").use { cursor ->
            cursor.moveToFirst()
            assertEquals("迁移不得破坏 task 与 memo 的关联", 1, cursor.getInt(0))
        }
        db.close()
    }
}
