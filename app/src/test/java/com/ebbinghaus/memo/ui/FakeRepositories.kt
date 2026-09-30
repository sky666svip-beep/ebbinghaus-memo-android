package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.core.engine.EbbinghausScheduler
import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.core.model.ReviewStage
import com.ebbinghaus.memo.core.util.SearchQueryTokenizer
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.TrashedMemoWithProgress
import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.data.repository.MemoSortOption
import com.ebbinghaus.memo.data.repository.ReviewRepository
import com.ebbinghaus.memo.data.repository.SettingsRepository
import com.ebbinghaus.memo.data.repository.TrashSortOption
import com.ebbinghaus.memo.data.trash.TrashRetention
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * 排序所需的复习任务信息（测试替身专用）。
 *
 * @property stageLevel 复习档位
 * @property dueDateEpochDay 到期日 epochDay
 * @property reviewCount 累计已复习次数（回收站预览用）
 */
data class ReviewSortInfo(
    val stageLevel: Int,
    val dueDateEpochDay: Long,
    val reviewCount: Int = 0
)

/**
 * 纯内存仿真的知识点仓储，用于纯 JVM ViewModel 单元测试
 *
 * 软删除语义与生产实现保持一致：列表/搜索/按 ID 查询一律排除 `deletedAt != null` 的条目；
 * 回收站为独立查询域。
 */
class FakeMemoRepository : MemoRepository {
    val memosState = MutableStateFlow<List<KnowledgeMemoEntity>>(emptyList())

    /** 排序用的复习任务信息（仅 STAGE_DESC / DUE_ASC 需要），由测试按需填充 */
    val reviewSortInfo: MutableMap<Long, ReviewSortInfo> = mutableMapOf()

    /** 置为 true 时单条软删除抛异常，用于验证失败反馈（P1-4） */
    var failOnSoftDeleteMemo: Boolean = false

    /** 置为 true 时批量软删除抛异常，用于验证失败回滚（P1-4） */
    var failOnSoftDeleteMemos: Boolean = false

    /** 置为 true 时批量打标签抛异常，用于验证失败回滚（P1-4） */
    var failOnAddTagToMemos: Boolean = false

    /** 置为 true 时新增知识点抛异常，用于验证保存失败反馈（QA 独立复核，P1-4） */
    var failOnCreateMemo: Boolean = false

    /** 置为 true 时更新知识点抛异常，用于验证保存失败反馈（QA 独立复核，P1-4） */
    var failOnUpdateMemo: Boolean = false

    /** 置为 true 时批量还原抛异常，用于验证失败提示（P2-3） */
    var failOnRestoreMemos: Boolean = false

    /** 置为 true 时批量彻底删除抛异常，用于验证失败提示（P2-3） */
    var failOnHardDeleteMemos: Boolean = false

    private var nextId = 1L

    override fun getAllMemos(): Flow<List<KnowledgeMemoEntity>> =
        memosState.map { list -> list.filter { it.deletedAt == null }.sortedByDescending { it.updatedAt } }

    override fun searchMemos(
        query: String,
        tag: String?,
        sort: MemoSortOption
    ): Flow<List<KnowledgeMemoEntity>> {
        // 与生产实现共用同一切分真源（架构 §8-1）
        val keywords = SearchQueryTokenizer.activeKeywords(query)
        return memosState.map { memos ->
            memos.filter { memo ->
                val matchesTag = tag.isNullOrBlank() || memo.tags.contains(tag)
                val matchesKeywords = if (keywords.isEmpty()) {
                    true
                } else {
                    keywords.all { kw ->
                        memo.content.contains(kw, ignoreCase = true) ||
                                memo.notes.contains(kw, ignoreCase = true)
                    }
                }
                memo.deletedAt == null && matchesTag && matchesKeywords
            }.sortedWith(comparatorFor(sort))
        }
    }

    private fun comparatorFor(sort: MemoSortOption): Comparator<KnowledgeMemoEntity> {
        return when (sort) {
            MemoSortOption.CREATED_DESC ->
                compareByDescending<KnowledgeMemoEntity> { it.createdAt }.thenByDescending { it.id }

            MemoSortOption.UPDATED_DESC ->
                compareByDescending<KnowledgeMemoEntity> { it.updatedAt }.thenByDescending { it.id }

            MemoSortOption.STAGE_DESC ->
                compareByDescending<KnowledgeMemoEntity> {
                    reviewSortInfo[it.id]?.stageLevel ?: 0
                }.thenByDescending { it.id }

            MemoSortOption.DUE_ASC ->
                compareBy<KnowledgeMemoEntity> {
                    reviewSortInfo[it.id]?.dueDateEpochDay ?: Long.MAX_VALUE
                }.thenByDescending { it.id }
        }
    }

    override suspend fun getMemoById(id: Long): KnowledgeMemoEntity? {
        return memosState.value.firstOrNull { it.id == id && it.deletedAt == null }
    }

    override suspend fun createMemo(content: String, notes: String, tags: List<String>): Long {
        if (failOnCreateMemo) throw IllegalStateException("模拟新增知识点失败")
        val id = nextId++
        val newMemo = KnowledgeMemoEntity(
            id = id,
            content = content,
            notes = notes,
            tags = tags
        )
        memosState.value = memosState.value + newMemo
        return id
    }

    override suspend fun updateMemo(id: Long, content: String, notes: String, tags: List<String>) {
        if (failOnUpdateMemo) throw IllegalStateException("模拟更新知识点失败")
        memosState.value = memosState.value.map {
            if (it.id == id) {
                it.copy(
                    content = content,
                    notes = notes,
                    tags = tags,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                it
            }
        }
    }

    override suspend fun updateNotesOnly(id: Long, notes: String) {
        memosState.value = memosState.value.map {
            if (it.id == id) {
                it.copy(notes = notes, updatedAt = System.currentTimeMillis())
            } else {
                it
            }
        }
    }

    override suspend fun deleteMemo(id: Long) {
        memosState.value = memosState.value.filter { it.id != id }
    }

    override suspend fun softDeleteMemo(id: Long) {
        if (failOnSoftDeleteMemo) throw IllegalStateException("模拟单条软删除失败")
        val now = System.currentTimeMillis()
        memosState.value = memosState.value.map {
            if (it.id == id) it.copy(deletedAt = now) else it
        }
    }

    override suspend fun softDeleteMemos(ids: List<Long>) {
        if (failOnSoftDeleteMemos) throw IllegalStateException("模拟批量软删除失败")
        val now = System.currentTimeMillis()
        memosState.value = memosState.value.map {
            if (it.id in ids) it.copy(deletedAt = now) else it
        }
    }

    override suspend fun restoreMemo(id: Long) {
        memosState.value = memosState.value.map {
            if (it.id == id) it.copy(deletedAt = null) else it
        }
    }

    override suspend fun hardDeleteMemo(id: Long) {
        memosState.value = memosState.value.filter { it.id != id }
    }

    override suspend fun clearTrash() {
        memosState.value = memosState.value.filter { it.deletedAt == null }
    }

    override fun getTrashedMemos(): Flow<List<KnowledgeMemoEntity>> =
        memosState.map { list -> list.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt } }

    override fun getTrashedMemosFiltered(
        query: String,
        sort: TrashSortOption
    ): Flow<List<TrashedMemoWithProgress>> {
        // 回收站搜索范围含 tags（架构 R4 已裁定），切分口径与生产一致
        val keywords = SearchQueryTokenizer.activeKeywords(query)
        return memosState.map { memos ->
            memos.filter { it.deletedAt != null }
                .filter { memo ->
                    keywords.isEmpty() || keywords.all { kw ->
                        memo.content.contains(kw, ignoreCase = true) ||
                            memo.notes.contains(kw, ignoreCase = true) ||
                            memo.tags.any { it.equals(kw, ignoreCase = true) }
                    }
                }
                .sortedWith(trashComparatorFor(sort))
                .map { memo ->
                    val info = reviewSortInfo[memo.id]
                    TrashedMemoWithProgress(
                        memo = memo,
                        stageLevel = info?.stageLevel,
                        dueDate = info?.dueDateEpochDay?.let { LocalDate.ofEpochDay(it) },
                        reviewCount = info?.reviewCount
                    )
                }
        }
    }

    private fun trashComparatorFor(sort: TrashSortOption): Comparator<KnowledgeMemoEntity> =
        when (sort) {
            TrashSortOption.DELETED_DESC ->
                compareByDescending<KnowledgeMemoEntity> { it.deletedAt ?: 0L }.thenByDescending { it.id }

            TrashSortOption.DELETED_ASC ->
                compareBy<KnowledgeMemoEntity> { it.deletedAt ?: 0L }.thenByDescending { it.id }

            TrashSortOption.TAG_ASC ->
                compareBy<KnowledgeMemoEntity> { it.tags.joinToString("\u001F") }.thenByDescending { it.id }
        }

    override suspend fun restoreMemos(ids: List<Long>): Int {
        if (failOnRestoreMemos) throw IllegalStateException("模拟批量还原失败")
        if (ids.isEmpty()) return 0
        var restored = 0
        memosState.value = memosState.value.map {
            if (it.id in ids && it.deletedAt != null) {
                restored++
                it.copy(deletedAt = null)
            } else {
                it
            }
        }
        return restored
    }

    override suspend fun hardDeleteMemos(ids: List<Long>) {
        if (failOnHardDeleteMemos) throw IllegalStateException("模拟批量彻底删除失败")
        if (ids.isEmpty()) return
        memosState.value = memosState.value.filterNot { it.id in ids && it.deletedAt != null }
    }

    override suspend fun purgeExpiredTrash(nowMillis: Long) {
        val cutoff = TrashRetention.purgeCutoff(nowMillis)
        memosState.value = memosState.value.filter { memo ->
            memo.deletedAt == null || (memo.deletedAt ?: 0L) >= cutoff
        }
    }

    override suspend fun addTagToMemos(ids: List<Long>, tag: String) {
        if (failOnAddTagToMemos) throw IllegalStateException("模拟批量打标签失败")
        val cleanTag = tag.trim()
        if (cleanTag.isEmpty()) return
        val now = System.currentTimeMillis()
        memosState.value = memosState.value.map {
            if (it.id in ids && !it.tags.contains(cleanTag)) {
                it.copy(tags = it.tags + cleanTag, updatedAt = now)
            } else {
                it
            }
        }
    }

    override suspend fun getAllMemosIncludingDeleted(): List<KnowledgeMemoEntity> = memosState.value
}

/**
 * 纯内存仿真的复习仓储
 */
class FakeReviewRepository : ReviewRepository {
    val tasksState = MutableStateFlow<List<MemoWithReviewTask>>(emptyList())
    val submittedRatings = mutableListOf<Triple<Long, ReviewRating, LocalDate>>()

    override fun getDueReviewTasks(today: LocalDate): Flow<List<MemoWithReviewTask>> {
        return tasksState.map { list ->
            list.filter { !it.reviewTask.dueDate.isAfter(today) }
        }
    }

    override suspend fun getTaskByMemoId(memoId: Long): ReviewTaskEntity? {
        return tasksState.value.find { it.reviewTask.memoId == memoId }?.reviewTask
    }

    override suspend fun getAllTasksSync(): List<ReviewTaskEntity> =
        tasksState.value.map { it.reviewTask }

    override suspend fun submitReviewRating(taskId: Long, rating: ReviewRating, reviewDate: LocalDate) {
        submittedRatings.add(Triple(taskId, rating, reviewDate))
        tasksState.value = tasksState.value.map { item ->
            if (item.reviewTask.id == taskId) {
                val currentStage = ReviewStage.fromLevel(item.reviewTask.stageLevel)
                val result = EbbinghausScheduler.calculateNextReview(currentStage, rating, reviewDate)
                val updatedTask = item.reviewTask.copy(
                    stageLevel = result.nextStage.level,
                    dueDate = result.nextReviewDate,
                    lastReviewDate = reviewDate,
                    reviewCount = item.reviewTask.reviewCount + 1
                )
                item.copy(reviewTask = updatedTask)
            } else {
                item
            }
        }
    }
}

/**
 * 纯内存仿真的配置仓储
 */
class FakeSettingsRepository(
    initialSettings: UserSettingsEntity = UserSettingsEntity()
) : SettingsRepository {
    val settingsState = MutableStateFlow(initialSettings)

    override fun getSettings(): Flow<UserSettingsEntity> = settingsState

    override suspend fun updateDailyLimit(newLimit: Int) {
        settingsState.value = settingsState.value.copy(dailyReviewLimit = newLimit.coerceIn(0, 50))
    }

    override suspend fun markDashboardPrompted(promptDate: LocalDate) {
        settingsState.value = settingsState.value.copy(lastPromptedDate = promptDate)
    }

    override suspend fun updateLastActiveDate(activeDate: LocalDate) {
        settingsState.value = settingsState.value.copy(lastActiveDate = activeDate)
    }
}
