package com.ebbinghaus.memo.data.repository

import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.TrashedMemoWithProgress
import kotlinx.coroutines.flow.Flow

/**
 * 知识点仓储接口
 *
 * 软删除口径（E06）：`deleteMemo` 语义保持「物理删除」（供内部/历史调用与对抗测试），
 * 面向用户界面的删除统一走 [softDeleteMemo] / [softDeleteMemos]（移入回收站）。
 */
interface MemoRepository {

    /**
     * 获取全量存活知识点响应式数据流（按更新时间倒序，已排除回收站条目）
     */
    fun getAllMemos(): Flow<List<KnowledgeMemoEntity>>

    /**
     * 统一查询管线：关键词 AND + 标签 + 排序，三者收敛到同一条 DAO 查询。
     *
     * @param query 搜索关键词（空格分隔，最多 6 个生效）
     * @param tag 标签筛选（null 或空白表示全部分类）
     * @param sort 排序选项（仅改变 ORDER BY，不参与过滤）
     */
    fun searchMemos(query: String, tag: String?, sort: MemoSortOption): Flow<List<KnowledgeMemoEntity>>

    /**
     * 根据 ID 获取知识点实体
     */
    suspend fun getMemoById(id: Long): KnowledgeMemoEntity?

    /**
     * 新增知识点并自动派发初始艾宾浩斯复习任务（Day 0 录入，Day 1 首次到期）
     *
     * @return 新增知识点的 ID
     */
    suspend fun createMemo(content: String, notes: String = "", tags: List<String> = emptyList()): Long

    /**
     * 编辑知识点内容（严格保留原有艾宾浩斯复习进度与档位）
     */
    suspend fun updateMemo(id: Long, content: String, notes: String, tags: List<String>)

    /**
     * 独立更新个人笔记
     */
    suspend fun updateNotesOnly(id: Long, notes: String)

    /**
     * 物理删除知识点（由 SQLite 外键 CASCADE 级联清理绑定的复习任务）
     */
    suspend fun deleteMemo(id: Long)

    // ------------------------------------------------------------------
    // 软删除 / 回收站（E06）
    // ------------------------------------------------------------------

    /** 软删除单条知识点（移入回收站，review_tasks 保留不删） */
    suspend fun softDeleteMemo(id: Long)

    /** 批量软删除（单事务） */
    suspend fun softDeleteMemos(ids: List<Long>)

    /** 从回收站还原单条知识点（review_tasks 从未删除，进度逐字段复原） */
    suspend fun restoreMemo(id: Long)

    /** 从回收站立即物理删除单条知识点（连带 CASCADE 清除复习任务） */
    suspend fun hardDeleteMemo(id: Long)

    /** 清空回收站（物理删除全部软删条目） */
    suspend fun clearTrash()

    /** 回收站条目响应式数据流（按 deletedAt 倒序） */
    fun getTrashedMemos(): Flow<List<KnowledgeMemoEntity>>

    /**
     * 回收站独立查询域（P2-4）：关键词（多词 AND，范围 = content + notes + tags）+ 3 种排序。
     *
     * 与列表页 [searchMemos] **物理隔离**：方向相反（`deletedAt IS NOT NULL`），
     * 且进出回收站**不读写**列表页的搜索词 / 标签筛选状态。
     *
     * @param query 搜索关键词（空白切分，最多 6 个生效，口径见 `SearchQueryTokenizer`）
     * @param sort 回收站排序（仅改变 ORDER BY）
     */
    fun getTrashedMemosFiltered(query: String, sort: TrashSortOption): Flow<List<TrashedMemoWithProgress>>

    /**
     * 批量还原（单事务）：仅还原「确实处于回收站」的条目，返回实际还原行数。
     *
     * 影响行数 < 请求条数 说明部分条目已被物理清理（`purgeExpiredTrash`），
     * 调用方据此提示「该条目已过期，无法还原」。
     */
    suspend fun restoreMemos(ids: List<Long>): Int

    /** 批量彻底删除（单事务）：由 SQLite 外键 CASCADE 连带清除 `review_tasks` */
    suspend fun hardDeleteMemos(ids: List<Long>)

    /** 物理清除超过保留期的回收站条目 */
    suspend fun purgeExpiredTrash(nowMillis: Long)

    /**
     * 为指定知识点批量添加标签（单事务 + 去重合并；已含该标签的条目跳过）
     */
    suspend fun addTagToMemos(ids: List<Long>, tag: String)

    /** 导出用：读取全量知识点（含软删条目），保证往返完整 */
    suspend fun getAllMemosIncludingDeleted(): List<KnowledgeMemoEntity>
}
