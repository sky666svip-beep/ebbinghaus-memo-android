package com.ebbinghaus.memo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.TrashedMemoWithProgress
import kotlinx.coroutines.flow.Flow

/**
 * 知识点数据访问接口
 *
 * 软删除口径（E06）：所有面向列表/搜索的查询**必须**带 `deletedAt IS NULL`；
 * 回收站是独立查询域（`deletedAt IS NOT NULL`）。
 */
@Dao
interface KnowledgeMemoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memo: KnowledgeMemoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memos: List<KnowledgeMemoEntity>)

    @Update
    suspend fun update(memo: KnowledgeMemoEntity)

    @Query("UPDATE knowledge_memos SET notes = :newNotes, updatedAt = :updatedAt WHERE id = :memoId")
    suspend fun updateNotes(memoId: Long, newNotes: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM knowledge_memos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM knowledge_memos WHERE id = :id")
    suspend fun getMemoById(id: Long): KnowledgeMemoEntity?

    @Query("SELECT * FROM knowledge_memos WHERE id IN (:ids)")
    suspend fun getMemosByIds(ids: List<Long>): List<KnowledgeMemoEntity>

    @Query("SELECT * FROM knowledge_memos WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getAllMemos(): Flow<List<KnowledgeMemoEntity>>

    // ------------------------------------------------------------------
    // 软删除 / 回收站（E06）
    // ------------------------------------------------------------------

    @Query("UPDATE knowledge_memos SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteById(id: Long, deletedAt: Long)

    @Query("UPDATE knowledge_memos SET deletedAt = :deletedAt WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: List<Long>, deletedAt: Long)

    @Query("UPDATE knowledge_memos SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreById(id: Long)

    @Query("DELETE FROM knowledge_memos WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeTrashedBefore(cutoff: Long)

    @Query("DELETE FROM knowledge_memos WHERE deletedAt IS NOT NULL")
    suspend fun clearTrash()

    @Query("SELECT * FROM knowledge_memos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashedMemos(): Flow<List<KnowledgeMemoEntity>>

    // ------------------------------------------------------------------
    // 导出 / 导入（E01）——含软删条目，保证往返完整
    // ------------------------------------------------------------------

    @Query("SELECT * FROM knowledge_memos")
    suspend fun getAllMemosIncludingDeleted(): List<KnowledgeMemoEntity>

    @Query("DELETE FROM knowledge_memos")
    suspend fun deleteAllMemos()

    // ------------------------------------------------------------------
    // 统一查询管线：软删过滤 + 关键词 AND + 标签 + 排序（D5/D6）
    //
    // - 关键词固定 6 元参数（空串=不启用），以 AND 语义组合；
    // - 标签用 `(tags || char(31)) LIKE '%' || char(31) || :tag || char(31) || '%'`：
    //   列尾补分隔符以覆盖「最后一个标签」，前后夹分隔符使 tag1 不匹配 tag10；
    // - 排序仅改变 ORDER BY，不参与过滤；sortKey = MemoSortOption.ordinal。
    // ------------------------------------------------------------------

    @Query(
        """
        SELECT m.* FROM knowledge_memos m
        LEFT JOIN review_tasks t ON m.id = t.memoId
        WHERE m.deletedAt IS NULL
          AND (:tag IS NULL OR (m.tags || char(31)) LIKE '%' || char(31) || :tag || char(31) || '%')
          AND (:k1 = '' OR m.content LIKE '%' || :k1 || '%' OR m.notes LIKE '%' || :k1 || '%')
          AND (:k2 = '' OR m.content LIKE '%' || :k2 || '%' OR m.notes LIKE '%' || :k2 || '%')
          AND (:k3 = '' OR m.content LIKE '%' || :k3 || '%' OR m.notes LIKE '%' || :k3 || '%')
          AND (:k4 = '' OR m.content LIKE '%' || :k4 || '%' OR m.notes LIKE '%' || :k4 || '%')
          AND (:k5 = '' OR m.content LIKE '%' || :k5 || '%' OR m.notes LIKE '%' || :k5 || '%')
          AND (:k6 = '' OR m.content LIKE '%' || :k6 || '%' OR m.notes LIKE '%' || :k6 || '%')
        ORDER BY CASE WHEN :sortKey = 0 THEN m.createdAt END DESC,
                 CASE WHEN :sortKey = 1 THEN m.updatedAt END DESC,
                 CASE WHEN :sortKey = 2 THEN COALESCE(t.stageLevel, 0) END DESC,
                 CASE WHEN :sortKey = 3 THEN COALESCE(t.dueDate, 9999999) END ASC,
                 m.id DESC
        """
    )
    fun searchMemos(
        k1: String,
        k2: String,
        k3: String,
        k4: String,
        k5: String,
        k6: String,
        tag: String?,
        sortKey: Int
    ): Flow<List<KnowledgeMemoEntity>>

    // ------------------------------------------------------------------
    // 回收站独立查询域 + 批量还原 / 彻底删除（P2-2 / P2-3 / P2-4）
    //
    // - 与列表页 searchMemos（`deletedAt IS NULL`）方向相反，**不复用**；
    // - 关键词范围 = content + notes + tags（tags 用分隔符夹逼，支持整标签命中）；
    // - LEFT JOIN review_tasks 提供「就地预览」所需的复习进度（无任务行时为 null）；
    // - 排序 sortKey = TrashSortOption.ordinal：0 删除时间倒序 / 1 删除时间正序 / 2 按标签。
    // ------------------------------------------------------------------

    /**
     * 批量还原：仅影响「确实处于回收站」的行，返回实际还原行数。
     *
     * 调用方据此识别「已过期被物理清理」的边界（影响行数 < 请求条数）。
     */
    @Query("UPDATE knowledge_memos SET deletedAt = NULL WHERE id IN (:ids) AND deletedAt IS NOT NULL")
    suspend fun restoreByIds(ids: List<Long>): Int

    /**
     * 批量彻底删除：由 SQLite 外键 CASCADE 连带清除 `review_tasks`，返回实际删除行数。
     */
    @Query("DELETE FROM knowledge_memos WHERE id IN (:ids) AND deletedAt IS NOT NULL")
    suspend fun hardDeleteByIds(ids: List<Long>): Int

    /**
     * 回收站查询域：关键词（多词 AND，范围 = content + notes + tags）+ 3 种排序。
     *
     * 投影含 `@Embedded memo` 与 3 个标量列，显式 `AS` 别名对齐字段名；
     * `dueDate` 经 `Converters` 的 `LocalDate?` 双向转换。
     */
    @Query(
        """
        SELECT m.*, t.stageLevel AS stageLevel, t.dueDate AS dueDate, t.reviewCount AS reviewCount
        FROM knowledge_memos m
        LEFT JOIN review_tasks t ON m.id = t.memoId
        WHERE m.deletedAt IS NOT NULL
          AND (:k1 = '' OR m.content LIKE '%' || :k1 || '%' OR m.notes LIKE '%' || :k1 || '%'
                        OR (m.tags || char(31)) LIKE '%' || char(31) || :k1 || char(31) || '%')
          AND (:k2 = '' OR m.content LIKE '%' || :k2 || '%' OR m.notes LIKE '%' || :k2 || '%'
                        OR (m.tags || char(31)) LIKE '%' || char(31) || :k2 || char(31) || '%')
          AND (:k3 = '' OR m.content LIKE '%' || :k3 || '%' OR m.notes LIKE '%' || :k3 || '%'
                        OR (m.tags || char(31)) LIKE '%' || char(31) || :k3 || char(31) || '%')
          AND (:k4 = '' OR m.content LIKE '%' || :k4 || '%' OR m.notes LIKE '%' || :k4 || '%'
                        OR (m.tags || char(31)) LIKE '%' || char(31) || :k4 || char(31) || '%')
          AND (:k5 = '' OR m.content LIKE '%' || :k5 || '%' OR m.notes LIKE '%' || :k5 || '%'
                        OR (m.tags || char(31)) LIKE '%' || char(31) || :k5 || char(31) || '%')
          AND (:k6 = '' OR m.content LIKE '%' || :k6 || '%' OR m.notes LIKE '%' || :k6 || '%'
                        OR (m.tags || char(31)) LIKE '%' || char(31) || :k6 || char(31) || '%')
        ORDER BY CASE WHEN :sortKey = 0 THEN m.deletedAt END DESC,
                 CASE WHEN :sortKey = 1 THEN m.deletedAt END ASC,
                 CASE WHEN :sortKey = 2 THEN (m.tags || char(31)) END ASC,
                 m.id DESC
        """
    )
    fun getTrashedMemosFiltered(
        k1: String,
        k2: String,
        k3: String,
        k4: String,
        k5: String,
        k6: String,
        sortKey: Int
    ): Flow<List<TrashedMemoWithProgress>>
}
