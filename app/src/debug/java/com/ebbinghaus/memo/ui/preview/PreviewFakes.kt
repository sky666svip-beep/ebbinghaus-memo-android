package com.ebbinghaus.memo.ui.preview

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
 * Compose Preview 专用的内存仓储实现。
 *
 * 仅存在于 `debug` 变体（`src/debug/`），**不会进入 release 产物**。
 * 目的：让强依赖 ViewModel 的页面级 Composable 能够在 IDE Preview 中渲染三档布局，
 * 而无需把 ViewModel 参数改为可空、也无需在 main 源集混入测试替身。
 */

/** Preview 用知识点仓储 */
internal class PreviewMemoRepository(
    initial: List<KnowledgeMemoEntity> = emptyList()
) : MemoRepository {

    val memosState = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun getAllMemos(): Flow<List<KnowledgeMemoEntity>> =
        memosState.map { list -> list.filter { it.deletedAt == null }.sortedByDescending { it.updatedAt } }

    override fun searchMemos(
        query: String,
        tag: String?,
        sort: MemoSortOption
    ): Flow<List<KnowledgeMemoEntity>> {
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
            }
        }
    }

    override suspend fun getMemoById(id: Long): KnowledgeMemoEntity? =
        memosState.value.firstOrNull { it.id == id && it.deletedAt == null }

    override suspend fun createMemo(content: String, notes: String, tags: List<String>): Long {
        val id = nextId++
        memosState.value = memosState.value + KnowledgeMemoEntity(
            id = id,
            content = content,
            notes = notes,
            tags = tags
        )
        return id
    }

    override suspend fun updateMemo(id: Long, content: String, notes: String, tags: List<String>) {
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
            if (it.id == id) it.copy(notes = notes, updatedAt = System.currentTimeMillis()) else it
        }
    }

    override suspend fun deleteMemo(id: Long) {
        memosState.value = memosState.value.filter { it.id != id }
    }

    override suspend fun softDeleteMemo(id: Long) {
        val now = System.currentTimeMillis()
        memosState.value = memosState.value.map { if (it.id == id) it.copy(deletedAt = now) else it }
    }

    override suspend fun softDeleteMemos(ids: List<Long>) {
        val now = System.currentTimeMillis()
        memosState.value = memosState.value.map { if (it.id in ids) it.copy(deletedAt = now) else it }
    }

    override suspend fun restoreMemo(id: Long) {
        memosState.value = memosState.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
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
                .map { memo -> TrashedMemoWithProgress(memo = memo, stageLevel = null, dueDate = null, reviewCount = null) }
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

/** Preview 用复习仓储 */
internal class PreviewReviewRepository(
    initial: List<MemoWithReviewTask> = emptyList()
) : ReviewRepository {

    val tasksState = MutableStateFlow(initial)

    override fun getDueReviewTasks(today: LocalDate): Flow<List<MemoWithReviewTask>> =
        tasksState.map { list -> list.filter { !it.reviewTask.dueDate.isAfter(today) } }

    override suspend fun getTaskByMemoId(memoId: Long): ReviewTaskEntity? =
        tasksState.value.find { it.reviewTask.memoId == memoId }?.reviewTask

    override suspend fun getAllTasksSync(): List<ReviewTaskEntity> =
        tasksState.value.map { it.reviewTask }

    override suspend fun submitReviewRating(
        taskId: Long,
        rating: ReviewRating,
        reviewDate: LocalDate
    ) {
        tasksState.value = tasksState.value.map { item ->
            if (item.reviewTask.id == taskId) {
                val currentStage = ReviewStage.fromLevel(item.reviewTask.stageLevel)
                val result = EbbinghausScheduler.calculateNextReview(currentStage, rating, reviewDate)
                item.copy(
                    reviewTask = item.reviewTask.copy(
                        stageLevel = result.nextStage.level,
                        dueDate = result.nextReviewDate,
                        lastReviewDate = reviewDate,
                        reviewCount = item.reviewTask.reviewCount + 1
                    )
                )
            } else {
                item
            }
        }
    }
}

/** Preview 用配置仓储 */
internal class PreviewSettingsRepository(
    initial: UserSettingsEntity = UserSettingsEntity()
) : SettingsRepository {

    val settingsState = MutableStateFlow(initial)

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

// ---------------------------------------------------------------------------
// 样例数据
// ---------------------------------------------------------------------------

private val TODAY: LocalDate = LocalDate.now()

private fun sampleMemo(
    id: Long,
    content: String,
    notes: String,
    tags: List<String>
) = KnowledgeMemoEntity(
    id = id,
    content = content,
    notes = notes,
    tags = tags,
    createdAt = System.currentTimeMillis() - id * 86_400_000L,
    updatedAt = System.currentTimeMillis() - id * 3_600_000L
)

/** 三条带笔记、覆盖多标签的样例知识点 */
internal fun previewMemos(): List<KnowledgeMemoEntity> = listOf(
    sampleMemo(
        id = 1,
        content = "柯尔莫哥洛夫复杂度：一个字符串的信息量等于生成它的最短程序长度",
        notes = "助记：与香农熵的区别在于「不可压缩性」，且 K 是不可计算的",
        tags = listOf("算法", "信息论")
    ),
    sampleMemo(
        id = 2,
        content = "Jetpack Compose 重组作用域：remember 在重组中保留，但配置变更后失效",
        notes = "rememberSaveable 才能跨旋转保存",
        tags = listOf("Android", "Compose")
    ),
    sampleMemo(
        id = 3,
        content = "狄利克雷卷积：(f * g)(n) = Σ_{d|n} f(d)·g(n/d)",
        notes = "",
        tags = listOf("数学")
    ),
    sampleMemo(
        id = 4,
        content = "艾宾浩斯遗忘曲线：标准复习间隔为 1、2、4、7、15、30 天",
        notes = "第 6 档再次记住后进入 60 天长周期",
        tags = listOf("算法", "记忆法")
    )
)

/** 与 [previewMemos] 一一对应、今日全部到期的复习任务（含一个长周期档位） */
internal fun previewDueTasks(): List<MemoWithReviewTask> =
    previewMemos().mapIndexed { index, memo ->
        MemoWithReviewTask(
            memo = memo,
            reviewTask = ReviewTaskEntity(
                id = memo.id,
                memoId = memo.id,
                stageLevel = when (index) {
                    0 -> ReviewStage.STAGE_4.level
                    1 -> ReviewStage.STAGE_2.level
                    2 -> ReviewStage.STAGE_1.level
                    else -> ReviewStage.LONG_TERM_60.level
                },
                dueDate = TODAY.minusDays(index.toLong()),
                lastReviewDate = TODAY.minusDays(index + 7L),
                reviewCount = 6 - index
            )
        )
    }
