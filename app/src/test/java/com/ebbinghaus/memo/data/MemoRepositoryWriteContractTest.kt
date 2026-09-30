package com.ebbinghaus.memo.data

import com.ebbinghaus.memo.data.local.dao.KnowledgeMemoDao
import com.ebbinghaus.memo.data.local.dao.ReviewTaskDao
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.TrashedMemoWithProgress
import com.ebbinghaus.memo.data.repository.MemoRepositoryImpl
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// =====================================================================================
// 仓储写路径契约守护（E06 软删除/还原/超期清理、E11 批量删除 / 批量打标签）
//
// 全仓**唯一**直接以「生产 MemoRepositoryImpl + 可控内存 DAO 替身」观测写路径的测试：
// 软删/还原逐字段一致、绝不触碰 review_tasks、批量单语句、purgeExpiredTrash cutoff、
// addTagToMemos 去重、clearTrash 委派。
// =====================================================================================

/** 空实现基类：集中承载 DAO 接口新增方法的 stub，避免逐方法实现的匿名对象编译失败。 */
private abstract class StubMemoDao : KnowledgeMemoDao {
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

/** 空实现基类：ReviewTaskDao。读操作给默认空值，写操作留待子类覆写。 */
private abstract class StubTaskDao : ReviewTaskDao {
    override suspend fun insertAll(tasks: List<ReviewTaskEntity>) {}
    override suspend fun getAllTasksSync(): List<ReviewTaskEntity> = emptyList()
    override suspend fun deleteAll() {}
    override suspend fun getTaskById(id: Long): ReviewTaskEntity? = null
    override suspend fun getTaskByMemoId(memoId: Long): ReviewTaskEntity? = null
    override fun getDueMemosWithTasks(targetDate: LocalDate): Flow<List<MemoWithReviewTask>> = flowOf(emptyList())
    override suspend fun getDueMemosWithTasksSync(targetDate: LocalDate): List<MemoWithReviewTask> = emptyList()
    override fun getAllMemosWithTasks(): Flow<List<MemoWithReviewTask>> = flowOf(emptyList())
}

/** 记录型复习任务 DAO：任何写操作都会被计数，用于证明软删除/还原「绝不触碰 review_tasks」。 */
private class RecordingTaskDao : StubTaskDao() {
    var mutations = 0
        private set

    override suspend fun insert(task: ReviewTaskEntity): Long { mutations++; return task.id }
    override suspend fun insertAll(tasks: List<ReviewTaskEntity>) { mutations++ }
    override suspend fun update(task: ReviewTaskEntity) { mutations++ }
    override suspend fun deleteById(id: Long) { mutations++ }
    override suspend fun deleteAll() { mutations++ }
}

/** 有状态内存 DAO：忠实模拟生产 SQL 的「单列更新」语义（软删仅写 deletedAt，还原仅置 NULL）。 */
private class InMemoryMemoDao : StubMemoDao() {
    val store = LinkedHashMap<Long, KnowledgeMemoEntity>()
    var softDeleteByIdCalls = 0
    var softDeleteByIdsCalls = 0
    var lastSoftDeleteIds: List<Long> = emptyList()
    var lastSoftDeleteTs: Long? = null
    var restoreByIdCalls = 0
    var purgeCutoffSeen: Long? = null
    var clearTrashCalls = 0
    var insertedTags: List<List<String>> = emptyList()

    override suspend fun insert(memo: KnowledgeMemoEntity): Long { store[memo.id] = memo; return memo.id }
    override suspend fun update(memo: KnowledgeMemoEntity) { store[memo.id] = memo }
    override suspend fun updateNotes(memoId: Long, newNotes: String, updatedAt: Long) {
        store[memoId]?.let { store[memoId] = it.copy(notes = newNotes, updatedAt = updatedAt) }
    }
    override suspend fun deleteById(id: Long) { store.remove(id) }
    override suspend fun insertAll(memos: List<KnowledgeMemoEntity>) {
        memos.forEach { store[it.id] = it }
        insertedTags = insertedTags + memos.map { it.tags }
    }
    override suspend fun getMemoById(id: Long): KnowledgeMemoEntity? = store[id]
    override suspend fun getMemosByIds(ids: List<Long>): List<KnowledgeMemoEntity> =
        ids.mapNotNull { store[it] }

    override suspend fun softDeleteById(id: Long, deletedAt: Long) {
        softDeleteByIdCalls++
        lastSoftDeleteTs = deletedAt
        store[id]?.let { store[id] = it.copy(deletedAt = deletedAt) }
    }
    override suspend fun softDeleteByIds(ids: List<Long>, deletedAt: Long) {
        softDeleteByIdsCalls++
        lastSoftDeleteIds = ids
        ids.forEach { id -> store[id]?.let { store[id] = it.copy(deletedAt = deletedAt) } }
    }
    override suspend fun restoreById(id: Long) {
        restoreByIdCalls++
        store[id]?.let { store[id] = it.copy(deletedAt = null) }
    }
    override suspend fun purgeTrashedBefore(cutoff: Long) {
        purgeCutoffSeen = cutoff
        store.entries.removeIf { (_, m) -> m.deletedAt != null && m.deletedAt!! < cutoff }
    }
    override suspend fun clearTrash() { clearTrashCalls++; store.entries.removeIf { (_, m) -> m.deletedAt != null } }
    override fun getTrashedMemos(): Flow<List<KnowledgeMemoEntity>> =
        flowOf(store.values.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt })

    override fun getAllMemos(): Flow<List<KnowledgeMemoEntity>> =
        flowOf(store.values.filter { it.deletedAt == null }.sortedByDescending { it.updatedAt })

    override fun searchMemos(
        k1: String, k2: String, k3: String, k4: String, k5: String, k6: String,
        tag: String?, sortKey: Int
    ): Flow<List<KnowledgeMemoEntity>> = flowOf(emptyList())
}

/**
 * 仓储写路径契约：软删除 / 还原 / 批量删除 / 批量打标签 / 超期清理 / 清空回收站。
 */
class MemoRepositoryWriteContractTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_700_000_000_000L

    private suspend fun seedDao(): InMemoryMemoDao {
        val dao = InMemoryMemoDao()
        dao.insert(
            KnowledgeMemoEntity(
                id = 1, content = "内容", notes = "笔记", tags = listOf("算法", "信息论"),
                createdAt = 111, updatedAt = 222, deletedAt = null
            )
        )
        return dao
    }

    @Test
    fun softDeleteThenRestore_fieldByFieldIdentical() = runBlocking {
        val dao = seedDao()
        val taskDao = RecordingTaskDao()
        val repo = MemoRepositoryImpl(dao, taskDao)

        val before = dao.store[1L]!!
        repo.softDeleteMemo(1L)
        assertTrue("软删除后 deletedAt 必须非空", dao.store[1L]!!.deletedAt != null)

        repo.restoreMemo(1L)
        val after = dao.store[1L]!!
        assertNull("还原后 deletedAt 必须为 null", after.deletedAt)
        assertEquals("还原后必须逐字段等于删除前", before.copy(deletedAt = null), after)
        assertEquals(111L, after.createdAt)
        assertEquals(222L, after.updatedAt)
        assertEquals(listOf("算法", "信息论"), after.tags)
    }

    @Test
    fun softDeleteAndRestore_neverTouchReviewTasks() = runBlocking {
        val dao = seedDao()
        val taskDao = RecordingTaskDao()
        val repo = MemoRepositoryImpl(dao, taskDao)

        repo.softDeleteMemo(1L)
        repo.restoreMemo(1L)

        assertEquals("软删+还原不得对 review_tasks 产生任何写操作", 0, taskDao.mutations)
        assertEquals(1, dao.softDeleteByIdCalls)
        assertEquals(1, dao.restoreByIdCalls)
    }

    @Test
    fun batchSoftDelete_isSingleStatementWithAllIds() = runBlocking {
        val dao = InMemoryMemoDao()
        listOf(1L, 2L, 3L).forEach { dao.insert(KnowledgeMemoEntity(id = it, content = "m$it")) }
        val repo = MemoRepositoryImpl(dao, RecordingTaskDao())

        repo.softDeleteMemos(listOf(1L, 2L, 3L))

        assertEquals("批量删除必须是一条 UPDATE ... WHERE id IN (...) 语句", 1, dao.softDeleteByIdsCalls)
        assertEquals(setOf(1L, 2L, 3L), dao.lastSoftDeleteIds.toSet())
        assertTrue(dao.store.values.all { it.deletedAt != null })
    }

    @Test
    fun batchSoftDelete_emptyList_isNoOp() = runBlocking {
        val dao = seedDao()
        val repo = MemoRepositoryImpl(dao, RecordingTaskDao())
        repo.softDeleteMemos(emptyList())
        assertEquals(0, dao.softDeleteByIdsCalls)
    }

    @Test
    fun addTagToMemos_dedupesSkipsExistingAndPreservesOrder() = runBlocking {
        val dao = InMemoryMemoDao()
        dao.insert(KnowledgeMemoEntity(id = 1, content = "a", tags = listOf("已有")))
        dao.insert(KnowledgeMemoEntity(id = 2, content = "b", tags = listOf("X", "Y")))
        dao.insert(KnowledgeMemoEntity(id = 3, content = "c", tags = emptyList()))
        val repo = MemoRepositoryImpl(dao, RecordingTaskDao())

        repo.addTagToMemos(listOf(1L, 2L, 3L), "已有")

        assertEquals("已含标签的条目不得重复追加", listOf("已有"), dao.store[1L]!!.tags)
        assertEquals("多标签条目应追加且保留原标签", listOf("X", "Y", "已有"), dao.store[2L]!!.tags)
        assertEquals("空标签条目应新增", listOf("已有"), dao.store[3L]!!.tags)
    }

    @Test
    fun addTagToMemos_blankTag_isNoOp() = runBlocking {
        val dao = InMemoryMemoDao()
        dao.insert(KnowledgeMemoEntity(id = 1, content = "a"))
        val repo = MemoRepositoryImpl(dao, RecordingTaskDao())

        repo.addTagToMemos(listOf(1L), "   ")
        repo.addTagToMemos(listOf(1L), "")

        assertTrue("空白标签不得写入", dao.store[1L]!!.tags.isEmpty())
        assertTrue(dao.insertedTags.isEmpty())
    }

    @Test
    fun hardDeleteAndClearTrash_delegateToDao() = runBlocking {
        val dao = InMemoryMemoDao()
        dao.insert(KnowledgeMemoEntity(id = 1, content = "a", deletedAt = 1L))
        dao.insert(KnowledgeMemoEntity(id = 2, content = "b", deletedAt = 2L))
        dao.insert(KnowledgeMemoEntity(id = 3, content = "c"))
        val repo = MemoRepositoryImpl(dao, RecordingTaskDao())

        repo.clearTrash()

        assertEquals(1, dao.clearTrashCalls)
        assertEquals("清空回收站只应移除软删条目", setOf(3L), dao.store.keys)
    }

    /**
     * 超期清理 cutoff 边界：恰好 30 天前的条目应保留，超过 30 天的应被物理清除。
     * 这是「保留策略」在仓储层的唯一观测点（cutoff = now - 30d）。
     */
    @Test
    fun purgeExpiredTrash_exactCutoffBoundary() = runBlocking {
        val dao = InMemoryMemoDao()
        dao.insert(KnowledgeMemoEntity(id = 1, content = "存活"))
        dao.insert(KnowledgeMemoEntity(id = 2, content = "删于29天前", deletedAt = now - 29 * day))
        dao.insert(KnowledgeMemoEntity(id = 3, content = "删于整30天前", deletedAt = now - 30 * day))
        dao.insert(KnowledgeMemoEntity(id = 4, content = "删于30天零1ms前", deletedAt = now - 30 * day - 1))
        dao.insert(KnowledgeMemoEntity(id = 5, content = "删于31天前", deletedAt = now - 31 * day))

        val repo = MemoRepositoryImpl(dao, RecordingTaskDao())
        repo.purgeExpiredTrash(now)

        assertEquals("cutoff 必须 = now-30d", now - 30 * day, dao.purgeCutoffSeen)
        assertEquals("存活条目 + 第29天 + 恰好第30天 应保留", setOf(1L, 2L, 3L), dao.store.keys)
        assertFalse("第31天条目应被物理清除", dao.store.containsKey(5L))
    }
}
