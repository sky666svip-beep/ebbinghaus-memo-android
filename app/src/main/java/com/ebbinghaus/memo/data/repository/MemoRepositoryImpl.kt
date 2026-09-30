package com.ebbinghaus.memo.data.repository

import androidx.room.withTransaction
import com.ebbinghaus.memo.core.engine.EbbinghausScheduler
import com.ebbinghaus.memo.core.util.SearchQueryTokenizer
import com.ebbinghaus.memo.data.local.AppDatabase
import com.ebbinghaus.memo.data.local.dao.KnowledgeMemoDao
import com.ebbinghaus.memo.data.local.dao.ReviewTaskDao
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.TrashedMemoWithProgress
import com.ebbinghaus.memo.data.trash.TrashRetention
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 知识点仓储实现类
 *
 * 保证“编辑知识点不重置复习进度”、“新增知识点与初始复习任务事务原子性”、
 * “软删除不触碰 review_tasks”、“批量操作单事务”以及“删除知识点时 SQLite 外键级联清除关联任务”。
 */
class MemoRepositoryImpl(
    private val memoDao: KnowledgeMemoDao,
    private val reviewTaskDao: ReviewTaskDao,
    private val database: AppDatabase? = null
) : MemoRepository {

    /**
     * 便捷构造函数：直接通过 AppDatabase 实例化仓储，确保自动装配 DAO 与 Room 事务支持
     */
    constructor(database: AppDatabase) : this(
        memoDao = database.knowledgeMemoDao(),
        reviewTaskDao = database.reviewTaskDao(),
        database = database
    )

    override fun getAllMemos(): Flow<List<KnowledgeMemoEntity>> {
        return memoDao.getAllMemos()
    }

    override fun searchMemos(
        query: String,
        tag: String?,
        sort: MemoSortOption
    ): Flow<List<KnowledgeMemoEntity>> {
        // 关键词固定 6 元：切分口径与 UI 高亮 / 回收站搜索共用同一真源（P2-10 / P2-4-④），
        // 即 SearchQueryTokenizer.activeKeywords（含全角空格 U+3000，取前 6 个），空位补空串（空串 = 该位不启用）
        val keywords = SearchQueryTokenizer.activeKeywords(query)
        val k1 = keywords.getOrElse(0) { "" }
        val k2 = keywords.getOrElse(1) { "" }
        val k3 = keywords.getOrElse(2) { "" }
        val k4 = keywords.getOrElse(3) { "" }
        val k5 = keywords.getOrElse(4) { "" }
        val k6 = keywords.getOrElse(5) { "" }
        val normalizedTag = tag?.takeIf { it.isNotBlank() }
        return memoDao.searchMemos(k1, k2, k3, k4, k5, k6, normalizedTag, sort.ordinal)
    }

    override suspend fun getMemoById(id: Long): KnowledgeMemoEntity? {
        return memoDao.getMemoById(id)
    }

    override suspend fun createMemo(content: String, notes: String, tags: List<String>): Long {
        val insertAction = suspend {
            val now = System.currentTimeMillis()
            val memo = KnowledgeMemoEntity(
                content = content,
                notes = notes,
                tags = tags,
                createdAt = now,
                updatedAt = now
            )
            val memoId = memoDao.insert(memo)

            // 绑定初始艾宾浩斯复习任务：以录入日为 Day 0，初始档位 STAGE_1，首个复习到期日在 Day 1 (录入当天不排入复习)
            val today = LocalDate.now()
            val initialSchedule = EbbinghausScheduler.initialSchedule(today)
            val initialTask = ReviewTaskEntity(
                memoId = memoId,
                stageLevel = initialSchedule.nextStage.level,
                dueDate = initialSchedule.nextReviewDate,
                lastReviewDate = null,
                reviewCount = 0,
                updatedAt = now
            )
            reviewTaskDao.insert(initialTask)
            memoId
        }

        // 若注入了 Room Database 实例，则使用 withTransaction 提供跨表强原子事务保护
        return if (database != null) {
            database.withTransaction { insertAction() }
        } else {
            insertAction()
        }
    }

    override suspend fun updateMemo(id: Long, content: String, notes: String, tags: List<String>) {
        val existing = memoDao.getMemoById(id) ?: return
        val updated = existing.copy(
            content = content,
            notes = notes,
            tags = tags,
            updatedAt = System.currentTimeMillis()
        )
        // 关键业务规范：仅更新知识点实体属性，绝不修改 review_tasks 表，完全保留既有复习进度
        memoDao.update(updated)
    }

    override suspend fun updateNotesOnly(id: Long, notes: String) {
        memoDao.updateNotes(memoId = id, newNotes = notes, updatedAt = System.currentTimeMillis())
    }

    override suspend fun deleteMemo(id: Long) {
        // 由 SQLite 外键 ForeignKey.CASCADE 自动在数据库层级联清理关联的 review_tasks 记录
        memoDao.deleteById(id)
    }

    // ------------------------------------------------------------------
    // 软删除 / 回收站（E06）
    // ------------------------------------------------------------------

    override suspend fun softDeleteMemo(id: Long) {
        // 仅写入 deletedAt，review_tasks 行保留不删 → 还原即恢复进度
        memoDao.softDeleteById(id, System.currentTimeMillis())
    }

    override suspend fun softDeleteMemos(ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val action = suspend { memoDao.softDeleteByIds(ids, now) }
        if (database != null) database.withTransaction { action() } else action()
    }

    override suspend fun restoreMemo(id: Long) {
        memoDao.restoreById(id)
    }

    override suspend fun hardDeleteMemo(id: Long) {
        memoDao.deleteById(id)
    }

    override suspend fun clearTrash() {
        memoDao.clearTrash()
    }

    override fun getTrashedMemos(): Flow<List<KnowledgeMemoEntity>> {
        return memoDao.getTrashedMemos()
    }

    override fun getTrashedMemosFiltered(
        query: String,
        sort: TrashSortOption
    ): Flow<List<TrashedMemoWithProgress>> {
        // 切分口径与列表页共用同一真源（P2-4-④ / P2-10）；回收站搜索范围含 tags（架构 R4 已裁定）
        val keywords = SearchQueryTokenizer.activeKeywords(query)
        val k1 = keywords.getOrElse(0) { "" }
        val k2 = keywords.getOrElse(1) { "" }
        val k3 = keywords.getOrElse(2) { "" }
        val k4 = keywords.getOrElse(3) { "" }
        val k5 = keywords.getOrElse(4) { "" }
        val k6 = keywords.getOrElse(5) { "" }
        return memoDao.getTrashedMemosFiltered(k1, k2, k3, k4, k5, k6, sort.ordinal)
    }

    override suspend fun restoreMemos(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        val action = suspend { memoDao.restoreByIds(ids) }
        return if (database != null) database.withTransaction { action() } else action()
    }

    override suspend fun hardDeleteMemos(ids: List<Long>) {
        if (ids.isEmpty()) return
        val action = suspend { memoDao.hardDeleteByIds(ids) }
        if (database != null) database.withTransaction { action() } else action()
    }

    override suspend fun purgeExpiredTrash(nowMillis: Long) {
        memoDao.purgeTrashedBefore(TrashRetention.purgeCutoff(nowMillis))
    }

    override suspend fun addTagToMemos(ids: List<Long>, tag: String) {
        val cleanTag = tag.trim()
        if (cleanTag.isEmpty() || ids.isEmpty()) return
        val action = suspend {
            val memos = memoDao.getMemosByIds(ids)
            val now = System.currentTimeMillis()
            // 去重合并：已含该标签的条目跳过，不产生重复标签
            val updated = memos
                .filter { !it.tags.contains(cleanTag) }
                .map { it.copy(tags = it.tags + cleanTag, updatedAt = now) }
            if (updated.isNotEmpty()) {
                memoDao.insertAll(updated)
            }
        }
        if (database != null) database.withTransaction { action() } else action()
    }

    override suspend fun getAllMemosIncludingDeleted(): List<KnowledgeMemoEntity> {
        return memoDao.getAllMemosIncludingDeleted()
    }
}
