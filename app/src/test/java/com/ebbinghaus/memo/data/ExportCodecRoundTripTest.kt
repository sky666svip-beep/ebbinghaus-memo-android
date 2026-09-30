package com.ebbinghaus.memo.data

import com.ebbinghaus.memo.data.export.AppSnapshot
import com.ebbinghaus.memo.data.export.CURRENT_SCHEMA_VERSION
import com.ebbinghaus.memo.data.export.CorruptedExportException
import com.ebbinghaus.memo.data.export.ExportCodec
import com.ebbinghaus.memo.data.export.MemoDto
import com.ebbinghaus.memo.data.export.ReviewTaskDto
import com.ebbinghaus.memo.data.export.SettingsDto
import com.ebbinghaus.memo.data.export.VersionTooNewException
import com.ebbinghaus.memo.data.export.toDto
import com.ebbinghaus.memo.data.export.toEntity
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出/导入编解码纯 JVM 往返测试（E01）
 *
 * 覆盖：往返逐字段相等、空库合法空结构、tags 为 JSON 数组、
 * 高版本拒绝、损坏文件拒绝、缺字段拒绝。
 */
class ExportCodecRoundTripTest {

    private val codec = ExportCodec()

    private fun sample(): AppSnapshot = AppSnapshot(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        exportedAt = 1737000000000L,
        appVersion = "1.0.0",
        memos = listOf(
            MemoDto(
                id = 1,
                content = "柯尔莫哥洛夫复杂度",
                notes = "与香农熵的区别",
                tags = listOf("算法", "信息论"),
                createdAt = 1736900000000L,
                updatedAt = 1736900000000L,
                deletedAt = null
            ),
            MemoDto(
                id = 2,
                content = "已删除条目",
                notes = "",
                tags = emptyList(),
                createdAt = 1736800000000L,
                updatedAt = 1736850000000L,
                deletedAt = 1736950000000L
            )
        ),
        reviewTasks = listOf(
            ReviewTaskDto(
                id = 1,
                memoId = 1,
                stageLevel = 3,
                dueDate = "2025-01-16",
                lastReviewDate = null,
                reviewCount = 0,
                updatedAt = 1736900000000L
            ),
            ReviewTaskDto(
                id = 2,
                memoId = 2,
                stageLevel = 1,
                dueDate = "2025-01-20",
                lastReviewDate = "2025-01-10",
                reviewCount = 4,
                updatedAt = 1736900000000L
            )
        ),
        userSettings = SettingsDto(
            id = 1,
            dailyReviewLimit = 20,
            lastActiveDate = "2025-01-15",
            lastPromptedDate = null
        )
    )

    @Test
    fun emptyLibrary_producesValidEmptyStructure() {
        val empty = AppSnapshot(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            exportedAt = 1L,
            appVersion = "1.0.0",
            memos = emptyList(),
            reviewTasks = emptyList(),
            userSettings = null
        )
        val encoded = codec.encode(empty)
        val decoded = codec.decode(encoded).getOrThrow()
        assertEquals(empty, decoded)
        assertTrue("空库导出应为合法空结构", encoded.contains("\"memos\":[]"))
        assertTrue(encoded.contains("\"reviewTasks\":[]"))
        assertTrue(encoded.contains("\"schemaVersion\":1"))
    }

    @Test
    fun tags_encodedAsJsonArray_notDelimiterJoined() {
        val encoded = codec.encode(sample())
        assertTrue("tags 必须为 JSON 数组", encoded.contains("\"tags\":[\"算法\",\"信息论\"]"))
        assertTrue("空标签须为合法空数组", encoded.contains("\"tags\":[]"))
    }

    @Test
    fun localDate_encodedAsIso8601() {
        val encoded = codec.encode(sample())
        assertTrue(encoded.contains("\"dueDate\":\"2025-01-16\""))
    }

    // ------------------------------------------------------------------
    // 对抗性载荷：特殊字符 / 空标签 / 超长 / 软删条目 / null 设置 / 纪元边界
    // ------------------------------------------------------------------

    /** 含引号、反斜杠、换行、制表符、emoji、U+001F 单元分隔符、CJK 与超长文本的对抗性载荷 */
    private fun adversarialSnapshot(): AppSnapshot {
        val longText = "长" + "x".repeat(20000)
        return AppSnapshot(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            exportedAt = 1_700_000_000_123L,
            appVersion = "1.0.0",
            memos = listOf(
                MemoDto(
                    id = 1, content = "引号\"反斜杠\\换行\n制表\t结束", notes = "emoji 🚀🧪 中文",
                    tags = listOf("算法", "信息论", "含空格 的标签"),
                    createdAt = 1000, updatedAt = 2000, deletedAt = null
                ),
                MemoDto(
                    id = 2, content = longText, notes = "",
                    tags = emptyList(), // 空标签数组
                    createdAt = 3000, updatedAt = 4000, deletedAt = null
                ),
                MemoDto(
                    id = 3, content = "含单元分隔符\u001F内容", notes = "软删条目",
                    tags = listOf("回收站", "标签\u001F内嵌"),
                    createdAt = 5000, updatedAt = 6000, deletedAt = 7000L // 软删条目也必须往返
                )
            ),
            reviewTasks = listOf(
                ReviewTaskDto(1, 1, 3, "2025-01-16", null, 0, 1000),
                ReviewTaskDto(2, 2, 7, "2026-02-28", "2026-01-01", 42, 2000),
                ReviewTaskDto(3, 3, 1, "1970-01-01", null, 0, 3000) // 纪元原点边界
            ),
            userSettings = null // 空库时 userSettings 为 null
        )
    }

    @Test
    fun roundTrip_adversarialPayload_fieldByFieldEqual() {
        val original = adversarialSnapshot()
        val decoded = codec.decode(codec.encode(original)).getOrThrow()
        assertEquals("导出→导入必须逐字段相等", original, decoded)
        // 显式核对软删条目与 U+001F 未被破坏
        assertEquals(7000L, decoded.memos.first { it.id == 3L }.deletedAt)
        assertEquals("含单元分隔符\u001F内容", decoded.memos.first { it.id == 3L }.content)
        assertEquals("1970-01-01", decoded.reviewTasks.first { it.id == 3L }.dueDate)
    }

    @Test
    fun roundTrip_entityDtoMapping_preservesEveryField() {
        val memo = KnowledgeMemoEntity(
            id = 9, content = "c", notes = "n", tags = listOf("a", "b"),
            createdAt = 11, updatedAt = 22, deletedAt = 33
        )
        assertEquals("memo 实体↔DTO 必须无损", memo, memo.toDto().toEntity())

        val task = ReviewTaskEntity(
            id = 5, memoId = 9, stageLevel = 4,
            dueDate = LocalDate.of(2026, 9, 15), lastReviewDate = LocalDate.of(2026, 9, 1),
            reviewCount = 7, updatedAt = 88
        )
        assertEquals("reviewTask 实体↔DTO 必须无损", task, task.toDto().toEntity())

        val nullLastReview = task.copy(lastReviewDate = null)
        assertNull(nullLastReview.toDto().toEntity().lastReviewDate)
    }

    @Test
    fun versionTooNew_isRejectedWithoutPartialParse() {
        // 高版本文件内容完全合法，仍必须整体拒绝（不得部分导入）
        val newer = adversarialSnapshot().copy(schemaVersion = CURRENT_SCHEMA_VERSION + 5)
        val result = codec.decode(codec.encode(newer))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VersionTooNewException)
        assertEquals(CURRENT_SCHEMA_VERSION + 5, (result.exceptionOrNull() as VersionTooNewException).version)
    }

    @Test
    fun corruptedJson_variants_allRejected() {
        val variants = listOf(
            "",                                  // 空串
            "   ",                               // 纯空白
            "not json at all",                   // 非 JSON
            "{ this is not json ]",              // 括号不匹配（反向合并自 corruptedJson_isRejected）
            "{\"schemaVersion\":1,\"memos\":[",  // 截断
            "{\"schemaVersion\":1}",             // 缺 memos/reviewTasks 数组
            "{\"memos\":[],\"reviewTasks\":[]}", // 缺 schemaVersion
            "[1,2,3]"                            // 根非对象
        )
        variants.forEach { bad ->
            val r = codec.decode(bad)
            assertTrue("损坏输入应被拒绝：$bad", r.isFailure)
            assertTrue(r.exceptionOrNull() is CorruptedExportException)
        }
    }

    @Test
    fun decode_isStateless_independentOfPriorCalls() {
        // 实质断言（替代原「同入参解码两次再自比」的恒真写法）：
        // 先用同一 codec 实例解码一个「被污染」的载荷，再解码原始载荷，
        // 结果必须仍与原始对象逐字段相等 —— 若存在跨调用隐藏状态/缓存，此断言必失败。
        val json = codec.encode(adversarialSnapshot())
        val polluted = adversarialSnapshot().copy(appVersion = "polluted-9.9.9")
        codec.decode(codec.encode(polluted)).getOrThrow()

        assertEquals(
            "decode 必须无跨调用隐藏状态：污染一次后重解原载荷仍须逐字段等于原始对象",
            adversarialSnapshot(),
            codec.decode(json).getOrThrow()
        )
    }
}
