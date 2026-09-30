package com.ebbinghaus.memo.data

import com.ebbinghaus.memo.core.engine.RolloverEngine
import com.ebbinghaus.memo.core.engine.SchedulableTask
import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.core.model.ReviewStage
import com.ebbinghaus.memo.data.local.converter.Converters
import com.ebbinghaus.memo.data.local.dao.KnowledgeMemoDao
import com.ebbinghaus.memo.data.local.dao.ReviewTaskDao
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.TrashedMemoWithProgress
import com.ebbinghaus.memo.data.repository.MemoRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 匿名 DAO 测试替身基类：为「非本用例关注」的新增 DAO 方法提供空实现。
 *
 * 依据架构风险 R1：DAO 接口一加抽象方法，逐方法实现的匿名对象即编译失败；
 * 用抽象基类集中承载 stub，接口再演进时只需在此处补一处。
 */
private abstract class StubKnowledgeMemoDao : KnowledgeMemoDao {
    override suspend fun insertAll(memos: List<KnowledgeMemoEntity>) {}
    override suspend fun getMemosByIds(ids: List<Long>): List<KnowledgeMemoEntity> = emptyList()
    override suspend fun softDeleteById(id: Long, deletedAt: Long) {}
    override suspend fun softDeleteByIds(ids: List<Long>, deletedAt: Long) {}
    override suspend fun restoreById(id: Long) {}
    override suspend fun purgeTrashedBefore(cutoff: Long) {}
    override suspend fun clearTrash() {}
    override fun getTrashedMemos(): Flow<List<KnowledgeMemoEntity>> = flowOf(emptyList())
    override suspend fun getAllMemosIncludingDeleted(): List<KnowledgeMemoEntity> = emptyList()
    override suspend fun deleteAllMemos() {}
    override suspend fun restoreByIds(ids: List<Long>): Int = 0
    override suspend fun hardDeleteByIds(ids: List<Long>): Int = 0
    override fun getTrashedMemosFiltered(
        k1: String, k2: String, k3: String, k4: String, k5: String, k6: String,
        sortKey: Int
    ): Flow<List<TrashedMemoWithProgress>> = flowOf(emptyList())
}

private abstract class StubReviewTaskDao : ReviewTaskDao {
    override suspend fun insertAll(tasks: List<ReviewTaskEntity>) {}
    override suspend fun getAllTasksSync(): List<ReviewTaskEntity> = emptyList()
    override suspend fun deleteAll() {}
}

/**
 * M1 数据持久层、类型转换器与跨模块契约对抗性实证测试套件
 *
 * 验证重点：
 * 1. Converters 对空列表、空字符串、全空白字符串、特殊字符、Emoji 及分隔符碰撞解除与向后兼容性
 * 2. Converters 对 LocalDate 的 null、纪元原点 (1970-01-01)、负数天数、闰年、大日期的双向转换无损性
 * 3. MemoWithReviewTask 聚合模型对 SchedulableTask 契约的实现及其与 RolloverEngine 的无缝数据流流动
 * 4. MemoRepositoryImpl.updateMemo 仅更新 KnowledgeMemoEntity，绝不修改关联 ReviewTaskEntity 的复习进度
 * 5. SQLite Foreign Key CASCADE 在未配置开启时的失效隐患实证分析
 * 6. MemoRepositoryImpl.createMemo 跨表双写契约验证
 */
class DataLayerContractAdversarialTest {

    private val converters = Converters()
    private val today = LocalDate.of(2026, 9, 14)

    // =========================================================================
    // 维度 1: Converters 鲁棒性与边界对抗测试
    // =========================================================================

    @Test
    fun testConverters_tagList_nullAndEmptySafety() {
        // 1. null 输入转换为 ""
        assertEquals("", converters.fromTagList(null))
        // 2. "" 反序列化为 emptyList()
        assertEquals(emptyList<String>(), converters.toTagList(null))
        assertEquals(emptyList<String>(), converters.toTagList(""))
        assertEquals(emptyList<String>(), converters.toTagList("   \t\n  "))
    }

    @Test
    fun testConverters_tagList_whitespaceAndBlankElementsIgnored() {
        // 列表内全为空白字符
        val blankList = listOf("", "   ", "\t", "\n")
        val serialized = converters.fromTagList(blankList)
        assertEquals("", serialized)
        assertEquals(emptyList<String>(), converters.toTagList(serialized))

        // 列表中夹杂空白字符与正常字符
        val mixedList = listOf("  Kotlin  ", "   ", "Android 16", "")
        val mixedSerialized = converters.fromTagList(mixedList)
        val deserialized = converters.toTagList(mixedSerialized)
        // 验证非空标签被保留且空白标签被滤除，首尾空白被 trim
        assertEquals(listOf("Kotlin", "Android 16"), deserialized)
    }

    @Test
    fun testConverters_tagList_specialCharactersAndUnicode() {
        // 包含中文字符、特殊符号、Emoji、引号、标点
        val specialTags = listOf("算法·艾宾浩斯", "Android 16 🚀", "Tag with, commas", "Tag \"quoted\"", "C++ / C#")
        val serialized = converters.fromTagList(specialTags)
        val deserialized = converters.toTagList(serialized)
        assertEquals(specialTags, deserialized)
    }

    @Test
    fun testConverters_tagList_delimiterCollisionResolved() {
        // 对抗场景：用户标签中含有法律章节符号 "§"
        // 验证：修复后系统不再发生意外分裂，完整保留原始标签内容
        val rawTag = "Chapter §1"
        val serialized = converters.fromTagList(listOf(rawTag))
        val deserialized = converters.toTagList(serialized)

        assertEquals(listOf("Chapter §1"), deserialized)
    }

    @Test
    fun testConverters_tagList_legacyDataCompatibility() {
        // 验证向后兼容：旧数据使用 "§" 拼接时依然能够正常反序列化
        val legacyData = "旧标签1§旧标签2§旧标签3"
        val deserialized = converters.toTagList(legacyData)
        assertEquals(listOf("旧标签1", "旧标签2", "旧标签3"), deserialized)
    }

    @Test
    fun testConverters_localDate_epochDayRoundtrip() {
        // 1. null 安全性
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))

        // 2. 纪元原点 (1970-01-01 -> epochDay 0)
        val epochZero = LocalDate.of(1970, 1, 1)
        assertEquals(0L, converters.fromLocalDate(epochZero))
        assertEquals(epochZero, converters.toLocalDate(0L))

        // 3. 负数纪元天数 (1969-12-31 -> epochDay -1)
        val epochMinusOne = LocalDate.of(1969, 12, 31)
        assertEquals(-1L, converters.fromLocalDate(epochMinusOne))
        assertEquals(epochMinusOne, converters.toLocalDate(-1L))

        // 4. 闰年 2024-02-29
        val leapDay = LocalDate.of(2024, 2, 29)
        val leapEpoch = converters.fromLocalDate(leapDay)
        assertEquals(leapDay, converters.toLocalDate(leapEpoch))

        // 5. 当期业务日期 2026-09-14
        val testDate = LocalDate.of(2026, 9, 14)
        val testEpoch = converters.fromLocalDate(testDate)
        assertEquals(testDate, converters.toLocalDate(testEpoch))
    }

    // =========================================================================
    // 维度 2: MemoWithReviewTask 聚合模型与 SchedulableTask 契约对抗测试
    // =========================================================================

    @Test
    fun testMemoWithReviewTask_schedulableTaskContractCompliance() {
        val memo = KnowledgeMemoEntity(
            id = 101L,
            content = "艾宾浩斯核心概念",
            notes = "遗忘曲线记忆法",
            tags = listOf("算法", "记忆")
        )
        val task = ReviewTaskEntity(
            id = 501L,
            memoId = 101L,
            stageLevel = 3,
            dueDate = today.minusDays(2),
            lastReviewDate = today.minusDays(6),
            reviewCount = 2
        )

        val memoWithTask = MemoWithReviewTask(memo = memo, reviewTask = task)

        // 1. 验证 SchedulableTask 契约正确委托到 reviewTask.id 与 reviewTask.dueDate
        val schedulable: SchedulableTask = memoWithTask
        assertEquals(501L, schedulable.taskId)
        assertEquals(today.minusDays(2), schedulable.targetDueDate)

        // 2. 验证与 memo.id (101L) 不发生混淆
        assertTrue(schedulable.taskId != memo.id)
    }

    @Test
    fun testMemoWithReviewTask_flowingThroughRolloverEngine_preservesAllEntityData() {
        // 构建不同到期日的 MemoWithReviewTask 列表
        val items = (1..5).map { index ->
            val memo = KnowledgeMemoEntity(
                id = index * 10L,
                content = "知识点 $index",
                notes = "笔记 $index",
                tags = listOf("标签$index")
            )
            val task = ReviewTaskEntity(
                id = index * 100L,
                memoId = index * 10L,
                stageLevel = index,
                dueDate = today.minusDays((5 - index).toLong()), // 到期日: -4, -3, -2, -1, 0
                reviewCount = index
            )
            MemoWithReviewTask(memo = memo, reviewTask = task)
        }

        // 设定每日上限为 2 条
        val plan = RolloverEngine.planReviewBatch(items, today, dailyLimit = 2)

        // 验证调度结果
        assertEquals(5, plan.totalPendingCount)
        assertEquals(2, plan.todayBatch.size)
        assertEquals(3, plan.deferredTasks.size)
        assertTrue(plan.isLimitReached)

        // 验证今日批次为到期时间最早的 2 个任务
        val firstTask = plan.todayBatch[0]
        assertEquals(100L, firstTask.taskId)
        assertEquals("知识点 1", firstTask.memo.content)
        assertEquals("笔记 1", firstTask.memo.notes)
        assertEquals(listOf("标签1"), firstTask.memo.tags)
        assertEquals(1, firstTask.reviewTask.stageLevel)

        val secondTask = plan.todayBatch[1]
        assertEquals(200L, secondTask.taskId)
        assertEquals("知识点 2", secondTask.memo.content)

        // 验证顺延批次中完整保留实体模型与复习状态
        val deferredFirst = plan.deferredTasks[0]
        assertEquals(300L, deferredFirst.taskId)
        assertEquals("知识点 3", deferredFirst.memo.content)
        assertEquals(3, deferredFirst.reviewTask.stageLevel)
    }

    // =========================================================================
    // 维度 3: MemoRepositoryImpl.updateMemo 不重置复习进度实证测试
    // =========================================================================

    @Test
    fun testUpdateMemo_strictlyPreservesReviewTaskProgress() = runBlocking {
        // 模拟 InMemory 存储结构
        var storedMemo = KnowledgeMemoEntity(
            id = 1L,
            content = "原始知识点内容",
            notes = "原始笔记",
            tags = listOf("原始标签"),
            createdAt = 1000L,
            updatedAt = 1000L
        )
        var storedTask = ReviewTaskEntity(
            id = 88L,
            memoId = 1L,
            stageLevel = 4, // 已经复习到第 4 档 (7天)
            dueDate = today.plusDays(5),
            lastReviewDate = today.minusDays(2),
            reviewCount = 3,
            updatedAt = 1000L
        )

        var reviewTaskUpdateCount = 0
        var reviewTaskInsertCount = 0

        val fakeMemoDao = object : StubKnowledgeMemoDao() {
            override suspend fun insert(memo: KnowledgeMemoEntity): Long = memo.id
            override suspend fun update(memo: KnowledgeMemoEntity) { storedMemo = memo }
            override suspend fun updateNotes(memoId: Long, newNotes: String, updatedAt: Long) {
                storedMemo = storedMemo.copy(notes = newNotes, updatedAt = updatedAt)
            }
            override suspend fun deleteById(id: Long) {}
            override suspend fun getMemoById(id: Long): KnowledgeMemoEntity? = if (storedMemo.id == id) storedMemo else null
            override fun getAllMemos(): Flow<List<KnowledgeMemoEntity>> = flowOf(listOf(storedMemo))
            override fun searchMemos(
                k1: String, k2: String, k3: String, k4: String, k5: String, k6: String,
                tag: String?, sortKey: Int
            ): Flow<List<KnowledgeMemoEntity>> = flowOf(listOf(storedMemo))
        }

        val fakeReviewTaskDao = object : StubReviewTaskDao() {
            override suspend fun insert(task: ReviewTaskEntity): Long {
                reviewTaskInsertCount++
                storedTask = task
                return task.id
            }
            override suspend fun update(task: ReviewTaskEntity) {
                reviewTaskUpdateCount++
                storedTask = task
            }
            override suspend fun getTaskById(id: Long): ReviewTaskEntity? = if (storedTask.id == id) storedTask else null
            override suspend fun getTaskByMemoId(memoId: Long): ReviewTaskEntity? = if (storedTask.memoId == memoId) storedTask else null
            override fun getDueMemosWithTasks(targetDate: LocalDate): Flow<List<MemoWithReviewTask>> = flowOf(emptyList())
            override suspend fun getDueMemosWithTasksSync(targetDate: LocalDate): List<MemoWithReviewTask> = emptyList()
            override fun getAllMemosWithTasks(): Flow<List<MemoWithReviewTask>> = flowOf(emptyList())
            override suspend fun deleteById(id: Long) {}
        }

        val repository = MemoRepositoryImpl(fakeMemoDao, fakeReviewTaskDao)

        // 执行 updateMemo：修改内容文本、笔记与标签
        repository.updateMemo(
            id = 1L,
            content = "全新修改后的知识点内容",
            notes = "全新修改后的笔记",
            tags = listOf("新标签1", "新标签2")
        )

        // 验证 1：KnowledgeMemoEntity 成功被修改
        assertEquals("全新修改后的知识点内容", storedMemo.content)
        assertEquals("全新修改后的笔记", storedMemo.notes)
        assertEquals(listOf("新标签1", "新标签2"), storedMemo.tags)
        assertTrue(storedMemo.updatedAt > 1000L)

        // 验证 2：ReviewTaskEntity 绝对没有被重新插入或更新，各项艾宾浩斯复习状态 100% 保持原样
        assertEquals(0, reviewTaskInsertCount)
        assertEquals(0, reviewTaskUpdateCount)
        assertEquals(4, storedTask.stageLevel)
        assertEquals(today.plusDays(5), storedTask.dueDate)
        assertEquals(today.minusDays(2), storedTask.lastReviewDate)
        assertEquals(3, storedTask.reviewCount)
        assertEquals(1000L, storedTask.updatedAt)
    }

    // =========================================================================
    // 维度 4: SQLite Foreign Key CASCADE 失效与孤儿任务隐患实证验证
    // =========================================================================

    @Test
    fun testDeleteMemo_relianceOnForeignKeyCascade_demonstratesVulnerabilityIfForeignKeysDisabled() = runBlocking {
        // 在 SQLite 中，默认 PRAGMA foreign_keys = OFF。
        // 若应用层未显式在 RoomDatabase.Callback 中执行 db.setForeignKeyConstraintsEnabled(true)，
        // 则底层 SQLite 驱动对 DELETE FROM knowledge_memos 不会触发 CASCADE DELETE，导致 review_tasks 成为孤儿记录。
        val memoId = 99L
        val taskId = 199L

        val memosTable = mutableMapOf<Long, KnowledgeMemoEntity>()
        val reviewTasksTable = mutableMapOf<Long, ReviewTaskEntity>()

        memosTable[memoId] = KnowledgeMemoEntity(id = memoId, content = "测试备忘录")
        reviewTasksTable[taskId] = ReviewTaskEntity(id = taskId, memoId = memoId, stageLevel = 1, dueDate = today)

        // 模拟当前代码实现：仅调用 memoDao.deleteById(id)
        val fakeMemoDao = object : StubKnowledgeMemoDao() {
            override suspend fun insert(memo: KnowledgeMemoEntity): Long = memo.id
            override suspend fun update(memo: KnowledgeMemoEntity) {}
            override suspend fun updateNotes(memoId: Long, newNotes: String, updatedAt: Long) {}
            override suspend fun deleteById(id: Long) {
                memosTable.remove(id)
                // 注意：在没有启用 SQLite foreign_keys 时，SQLite 引擎不会删除 reviewTasksTable 的记录！
            }
            override suspend fun getMemoById(id: Long): KnowledgeMemoEntity? = memosTable[id]
            override fun getAllMemos(): Flow<List<KnowledgeMemoEntity>> = flowOf(memosTable.values.toList())
            override fun searchMemos(
                k1: String, k2: String, k3: String, k4: String, k5: String, k6: String,
                tag: String?, sortKey: Int
            ): Flow<List<KnowledgeMemoEntity>> = flowOf(emptyList())
        }

        val fakeReviewTaskDao = object : StubReviewTaskDao() {
            override suspend fun insert(task: ReviewTaskEntity): Long = task.id
            override suspend fun update(task: ReviewTaskEntity) {}
            override suspend fun getTaskById(id: Long): ReviewTaskEntity? = reviewTasksTable[id]
            override suspend fun getTaskByMemoId(memoId: Long): ReviewTaskEntity? = reviewTasksTable.values.find { it.memoId == memoId }
            override fun getDueMemosWithTasks(targetDate: LocalDate): Flow<List<MemoWithReviewTask>> = flowOf(emptyList())
            override suspend fun getDueMemosWithTasksSync(targetDate: LocalDate): List<MemoWithReviewTask> = emptyList()
            override fun getAllMemosWithTasks(): Flow<List<MemoWithReviewTask>> = flowOf(emptyList())
            override suspend fun deleteById(id: Long) {
                reviewTasksTable.remove(id)
            }
        }

        val repository = MemoRepositoryImpl(fakeMemoDao, fakeReviewTaskDao)

        // 执行 deleteMemo
        repository.deleteMemo(memoId)

        // 验证知识点已被删除
        assertNull(memosTable[memoId])

        // 对抗实证：由于 MemoRepositoryImpl 没有应用层双重保险（例如显式级联删除），
        // 且 AppDatabase 未配置 setForeignKeyConstraintsEnabled(true)，
        // review_tasks 表中残留孤儿记录 (Orphan Task)!
        assertNotNull("当底层 SQLite 外键未启用时，review_tasks 残留孤儿任务", reviewTasksTable[taskId])
        assertEquals(memoId, reviewTasksTable[taskId]?.memoId)
    }

    // =========================================================================
    // 维度 5: MemoRepositoryImpl.createMemo 跨表双写契约测试
    // =========================================================================

    @Test
    fun testCreateMemo_contract_insertsBothMemoAndReviewTask() = runBlocking {
        var insertedMemo: KnowledgeMemoEntity? = null
        var insertedTask: ReviewTaskEntity? = null

        val fakeMemoDao = object : StubKnowledgeMemoDao() {
            override suspend fun insert(memo: KnowledgeMemoEntity): Long {
                insertedMemo = memo
                return 101L
            }
            override suspend fun update(memo: KnowledgeMemoEntity) {}
            override suspend fun updateNotes(memoId: Long, newNotes: String, updatedAt: Long) {}
            override suspend fun deleteById(id: Long) {}
            override suspend fun getMemoById(id: Long): KnowledgeMemoEntity? = insertedMemo
            override fun getAllMemos(): Flow<List<KnowledgeMemoEntity>> = flowOf(listOfNotNull(insertedMemo))
            override fun searchMemos(
                k1: String, k2: String, k3: String, k4: String, k5: String, k6: String,
                tag: String?, sortKey: Int
            ): Flow<List<KnowledgeMemoEntity>> = flowOf(emptyList())
        }

        val fakeReviewTaskDao = object : StubReviewTaskDao() {
            override suspend fun insert(task: ReviewTaskEntity): Long {
                insertedTask = task
                return 201L
            }
            override suspend fun update(task: ReviewTaskEntity) {}
            override suspend fun getTaskById(id: Long): ReviewTaskEntity? = insertedTask
            override suspend fun getTaskByMemoId(memoId: Long): ReviewTaskEntity? = insertedTask
            override fun getDueMemosWithTasks(targetDate: LocalDate): Flow<List<MemoWithReviewTask>> = flowOf(emptyList())
            override suspend fun getDueMemosWithTasksSync(targetDate: LocalDate): List<MemoWithReviewTask> = emptyList()
            override fun getAllMemosWithTasks(): Flow<List<MemoWithReviewTask>> = flowOf(emptyList())
            override suspend fun deleteById(id: Long) {}
        }

        val repository = MemoRepositoryImpl(fakeMemoDao, fakeReviewTaskDao)
        val memoId = repository.createMemo(
            content = "知识点内容",
            notes = "个人笔记",
            tags = listOf("Chapter §1", "算法")
        )

        // 验证备忘录 ID 正确返回
        assertEquals(101L, memoId)

        // 验证 KnowledgeMemoEntity 属性
        assertNotNull(insertedMemo)
        assertEquals("知识点内容", insertedMemo?.content)
        assertEquals("个人笔记", insertedMemo?.notes)
        assertEquals(listOf("Chapter §1", "算法"), insertedMemo?.tags)

        // 验证绑定的初始复习任务 (Day 0 录入，初始 Stage 1，Day 1 首复习日，计数 0)
        assertNotNull(insertedTask)
        assertEquals(101L, insertedTask?.memoId)
        assertEquals(1, insertedTask?.stageLevel)
        assertEquals(LocalDate.now().plusDays(1), insertedTask?.dueDate)
        assertNull(insertedTask?.lastReviewDate)
        assertEquals(0, insertedTask?.reviewCount)
    }
}
