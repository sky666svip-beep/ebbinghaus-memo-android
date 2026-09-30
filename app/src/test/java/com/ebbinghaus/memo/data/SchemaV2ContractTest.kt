package com.ebbinghaus.memo.data

import com.ebbinghaus.memo.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v2 Schema 契约 JVM 兜底测试（R3）
 *
 * 背景：`MigrationTestHelper` 依赖 instrumentation + 真实 SQLite，只能跑在 androidTest；
 * 本环境无模拟器，因此以「读 Room 导出的 schema JSON + 断言迁移 SQL 常量」作为纯 JVM 守门：
 * 1. `1.json` 为 v1 基线（version=1、knowledge_memos 无 deletedAt）；
 * 2. `2.json` 为 v2（version=2、deletedAt 为 INTEGER 且可空）；
 * 3. 迁移 SQL 常量严格等于仅追加列的 ALTER TABLE 语句（不改动既有数据）。
 */
class SchemaV2ContractTest {

    private val schemaDirCandidates = listOf(
        File("schemas/com.ebbinghaus.memo.data.local.AppDatabase"),
        File("app/schemas/com.ebbinghaus.memo.data.local.AppDatabase")
    )

    private fun schemaText(fileName: String): String {
        val file = schemaDirCandidates
            .map { File(it, fileName) }
            .firstOrNull { it.exists() }
            ?: error("未找到 schema 文件 $fileName，候选目录：${schemaDirCandidates.map { it.absolutePath }}")
        return file.readText()
    }

    /** 提取指定实体的 JSON 片段（从 tableName 出现处到下一个 entity 的 tableName 之前） */
    private fun entityBlock(json: String, tableName: String): String {
        val marker = "\"tableName\": \"$tableName\""
        val start = json.indexOf(marker)
        assertTrue("schema 中应存在实体 $tableName", start >= 0)
        val next = json.indexOf("\"tableName\":", start + marker.length)
        return if (next > 0) json.substring(start, next) else json.substring(start)
    }

    @Test
    fun migrationSql_constant_isAdditiveColumnOnly() {
        assertEquals(
            "ALTER TABLE knowledge_memos ADD COLUMN deletedAt INTEGER",
            AppDatabase.MIGRATION_1_2_SQL
        )
        // 迁移必须只做追加列，禁止任何破坏性语句
        assertFalse(AppDatabase.MIGRATION_1_2_SQL.contains("DROP", ignoreCase = true))
        assertFalse(AppDatabase.MIGRATION_1_2_SQL.contains("DELETE FROM", ignoreCase = true))
    }

    @Test
    fun v1Baseline_hasNoDeletedAtColumn() {
        val json = schemaText("1.json")
        assertTrue("v1 基线版本号应为 1", json.contains("\"version\": 1"))
        val memoBlock = entityBlock(json, "knowledge_memos")
        assertFalse("v1 不应包含 deletedAt 字段", memoBlock.contains("\"columnName\": \"deletedAt\""))
    }

    @Test
    fun v2Schema_deletedAtIsNullableInteger() {
        val json = schemaText("2.json")
        assertTrue("v2 版本号应为 2", json.contains("\"version\": 2"))
        val memoBlock = entityBlock(json, "knowledge_memos")
        assertTrue("v2 应包含 deletedAt 字段", memoBlock.contains("\"columnName\": \"deletedAt\""))

        // 抽取 deletedAt 字段对象，断言亲和类型与可空性
        val fieldRegex = Regex(
            "\\{[^{}]*\"fieldPath\"\\s*:\\s*\"deletedAt\"[^{}]*\\}",
            RegexOption.DOT_MATCHES_ALL
        )
        val field = fieldRegex.find(memoBlock)?.value
            ?: error("2.json 中未解析到 deletedAt 字段对象")
        assertTrue("deletedAt 亲和类型应为 INTEGER", field.contains("\"affinity\": \"INTEGER\""))
        assertTrue("deletedAt 必须可空（notNull=false）", field.contains("\"notNull\": false"))
    }

    @Test
    fun v2Schema_reviewTasksForeignKeyCascadeRetained() {
        val json = schemaText("2.json")
        val taskBlock = entityBlock(json, "review_tasks")
        // 软删除不删除 review_tasks，但物理删除（清空/超期清理）仍依赖 CASCADE
        assertTrue("review_tasks 应保留 ON DELETE CASCADE 外键", taskBlock.contains("\"onDelete\": \"CASCADE\""))
    }
}
