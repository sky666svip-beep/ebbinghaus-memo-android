package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.repository.TrashSortOption
import com.ebbinghaus.memo.data.trash.TrashRetention
import com.ebbinghaus.memo.ui.trash.TrashEffect
import com.ebbinghaus.memo.ui.trash.TrashUiEvent
import com.ebbinghaus.memo.ui.trash.TrashViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 回收站能力单元测试（P2-2 / P2-3 / P2-4）
 *
 * 覆盖：
 * - 数据域隔离：仅加载 `deletedAt != null` 的条目；
 * - 独立搜索（content + notes + tags，多词 AND）与清空搜索；
 * - 三种排序（删除时间倒序 / 正序 / 按标签）；
 * - 就地展开（可同时展开多条）；
 * - 多选：进入 / 切换 / 全选 / 取消全选 / 退出，且长按在多选态下退化为切换勾选；
 * - 选择集与展开集在筛选变化后与可见条目求交（PRD 边界 5 / 11）；
 * - 批量还原（免确认）、批量彻底删除（二次确认）、单条还原、单条彻底删除、清空；
 * - 失败反馈（保留多选态 + 提示「操作失败，请重试」）；
 * - 剩余天数 / 已删除天数纯函数口径。
 *
 * 说明：Compose 层交互（就地展开动画、5 秒窗口、长按手势）无法在 JVM 断言（架构 R9），
 * 此处以 ViewModel 状态机守护全部可测契约。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val day = 24L * 60L * 60L * 1000L

    /** 固定「当前时刻」，保证剩余 / 已删除天数可确定断言 */
    private val now = 1_700_000_000_000L

    private lateinit var repository: FakeMemoRepository
    private lateinit var viewModel: TrashViewModel

    @Before
    fun setUp() {
        repository = FakeMemoRepository()
        repository.memosState.value = listOf(
            KnowledgeMemoEntity(
                id = 1,
                content = "Zebra 苹果",
                notes = "笔记甲",
                tags = listOf("zebra"),
                createdAt = now - 10 * day,
                updatedAt = now - 10 * day,
                deletedAt = now - 1 * day
            ),
            KnowledgeMemoEntity(
                id = 2,
                content = "Banana 香蕉",
                notes = "",
                tags = listOf("apple"),
                createdAt = now - 10 * day,
                updatedAt = now - 10 * day,
                deletedAt = now - 2 * day
            ),
            KnowledgeMemoEntity(
                id = 3,
                content = "Car 汽车",
                notes = "note keyword",
                tags = listOf("mango"),
                createdAt = now - 10 * day,
                updatedAt = now - 10 * day,
                deletedAt = now - 3 * day
            ),
            KnowledgeMemoEntity(
                id = 4,
                content = "存活条目",
                notes = "未被删除",
                tags = emptyList(),
                createdAt = now - 10 * day,
                updatedAt = now - 10 * day,
                deletedAt = null
            )
        )
        repository.reviewSortInfo[1] = ReviewSortInfo(
            stageLevel = 3,
            dueDateEpochDay = 20_000L,
            reviewCount = 7
        )
        viewModel = TrashViewModel(repository, nowProvider = { now })
    }

    private fun TestScope.collectEffects(): MutableList<TrashEffect> {
        val collected = mutableListOf<TrashEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.toList(collected)
        }
        return collected
    }

    private fun List<TrashEffect>.messages(): List<String> =
        filterIsInstance<TrashEffect.ShowSnackbar>().map { it.message }

    private fun List<TrashEffect>.anyMessageContains(fragment: String): Boolean =
        messages().any { it.contains(fragment) }

    private fun visibleIds(): List<Long> = viewModel.uiState.value.items.map { it.memo.id }

    private fun deletedAtOf(id: Long): Long? =
        repository.memosState.value.firstOrNull { it.id == id }?.deletedAt

    private fun existsInStore(id: Long): Boolean =
        repository.memosState.value.any { it.id == id }

    // ------------------------------------------------------------------
    // 数据域隔离
    // ------------------------------------------------------------------

    @Test
    fun loadsOnlyTrashedMemos_defaultSortDeletedDesc() = runTest {
        assertEquals("仅加载软删条目", listOf(1L, 2L, 3L), visibleIds())
        assertFalse("存活条目不得出现在回收站", visibleIds().contains(4L))
    }

    // ------------------------------------------------------------------
    // 独立搜索（P2-4）
    // ------------------------------------------------------------------

    @Test
    fun search_matchesContent() = runTest {
        viewModel.onEvent(TrashUiEvent.OnSearchQueryChanged("苹果"))
        assertEquals(listOf(1L), visibleIds())
    }

    @Test
    fun search_matchesNotes() = runTest {
        viewModel.onEvent(TrashUiEvent.OnSearchQueryChanged("keyword"))
        assertEquals(listOf(3L), visibleIds())
    }

    @Test
    fun search_matchesTags() = runTest {
        // 回收站搜索范围含 tags（架构 R4 裁定）
        viewModel.onEvent(TrashUiEvent.OnSearchQueryChanged("apple"))
        assertEquals(listOf(2L), visibleIds())
    }

    @Test
    fun search_multiWord_isAndSemantics() = runTest {
        // "car" 命中 id3 内容，"keyword" 命中 id3 笔记 → AND 命中 id3
        viewModel.onEvent(TrashUiEvent.OnSearchQueryChanged("car keyword"))
        assertEquals(listOf(3L), visibleIds())

        // 跨条目的词不得命中（id2 有 banana，id3 有 keyword，但无任一条同时含二者）
        viewModel.onEvent(TrashUiEvent.OnSearchQueryChanged("banana keyword"))
        assertTrue("跨条目 AND 应为空", visibleIds().isEmpty())
    }

    @Test
    fun search_noResult_returnsEmpty() = runTest {
        viewModel.onEvent(TrashUiEvent.OnSearchQueryChanged("不存在的关键词"))
        assertTrue(visibleIds().isEmpty())
    }

    @Test
    fun clearSearch_restoresAllTrashed() = runTest {
        viewModel.onEvent(TrashUiEvent.OnSearchQueryChanged("苹果"))
        assertEquals(listOf(1L), visibleIds())

        viewModel.onEvent(TrashUiEvent.OnClearSearch)
        assertEquals(listOf(1L, 2L, 3L), visibleIds())
    }

    // ------------------------------------------------------------------
    // 排序（P2-4）
    // ------------------------------------------------------------------

    @Test
    fun sort_deletedAsc_ordersByDeletedAtAscending() = runTest {
        viewModel.onEvent(TrashUiEvent.OnSortOptionSelected(TrashSortOption.DELETED_ASC))
        assertEquals(listOf(3L, 2L, 1L), visibleIds())
    }

    @Test
    fun sort_tagAsc_ordersByTags() = runTest {
        viewModel.onEvent(TrashUiEvent.OnSortOptionSelected(TrashSortOption.TAG_ASC))
        // apple(2) < mango(3) < zebra(1)
        assertEquals(listOf(2L, 3L, 1L), visibleIds())
    }

    @Test
    fun sort_deletedDesc_isDefault() = runTest {
        assertEquals(TrashSortOption.DELETED_DESC, viewModel.uiState.value.sortOption)
        assertEquals(listOf(1L, 2L, 3L), visibleIds())
    }

    // ------------------------------------------------------------------
    // 就地展开（P2-2）
    // ------------------------------------------------------------------

    @Test
    fun toggleExpand_supportsMultipleExpandedItems() = runTest {
        viewModel.onEvent(TrashUiEvent.OnToggleExpand(1))
        viewModel.onEvent(TrashUiEvent.OnToggleExpand(3))
        assertEquals(setOf(1L, 3L), viewModel.uiState.value.expandedIds)

        viewModel.onEvent(TrashUiEvent.OnToggleExpand(1))
        assertEquals(setOf(3L), viewModel.uiState.value.expandedIds)
    }

    @Test
    fun expand_exposesReviewProgressProjection() = runTest {
        val item = viewModel.uiState.value.items.first { it.memo.id == 1L }
        assertEquals(3, item.stageLevel)
        assertEquals(7, item.reviewCount)
        assertEquals(java.time.LocalDate.ofEpochDay(20_000L), item.dueDate)

        // 无复习任务信息的条目，进度字段防御式为空
        val item2 = viewModel.uiState.value.items.first { it.memo.id == 2L }
        assertNull(item2.stageLevel)
        assertNull(item2.reviewCount)
        assertNull(item2.dueDate)
    }

    // ------------------------------------------------------------------
    // 多选（P2-3）
    // ------------------------------------------------------------------

    @Test
    fun enterSelectionMode_selectsInitialItem() = runTest {
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(1L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun longPressAgainInSelectionMode_togglesWithoutResetting() = runTest {
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(TrashUiEvent.OnToggleSelection(2))
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedIds)

        // 多选态下再次长按已选项 → 取消勾选（不得重置选择集）
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        assertEquals(setOf(2L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun selectAll_thenClearSelection() = runTest {
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(TrashUiEvent.OnSelectAll)
        assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selectedIds)

        viewModel.onEvent(TrashUiEvent.OnClearSelection)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
        assertTrue("取消全选不得退出多选态", viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun exitSelectionMode_resetsSelectionAndDialog() = runTest {
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(TrashUiEvent.OnRequestBatchDelete)
        assertTrue(viewModel.uiState.value.isBatchDeleteDialogVisible)

        viewModel.onEvent(TrashUiEvent.OnExitSelectionMode)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
        assertFalse(viewModel.uiState.value.isBatchDeleteDialogVisible)
    }

    @Test
    fun selectionAndExpanded_intersectWithVisibleAfterFilter() = runTest {
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(TrashUiEvent.OnToggleExpand(1))
        assertEquals(setOf(1L), viewModel.uiState.value.selectedIds)
        assertEquals(setOf(1L), viewModel.uiState.value.expandedIds)

        // 筛选后仅 id2 可见 → 选择集 / 展开集应剔除不可见 id
        viewModel.onEvent(TrashUiEvent.OnSearchQueryChanged("banana"))
        assertEquals(listOf(2L), visibleIds())
        assertTrue("不可见的选择应被剔除", viewModel.uiState.value.selectedIds.isEmpty())
        assertTrue("不可见的展开应被剔除", viewModel.uiState.value.expandedIds.isEmpty())
    }

    // ------------------------------------------------------------------
    // 批量还原 / 批量彻底删除（P2-3）
    // ------------------------------------------------------------------

    @Test
    fun batchRestore_restoresSelectedWithoutConfirm() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(TrashUiEvent.OnToggleSelection(2))

        viewModel.onEvent(TrashUiEvent.OnBatchRestore)

        assertNull(deletedAtOf(1))
        assertNull(deletedAtOf(2))
        assertFalse("批量还原后应退出多选态", viewModel.uiState.value.isSelectionMode)
        assertTrue(effects.anyMessageContains("已还原 2 条"))
    }

    @Test
    fun batchDelete_requiresConfirm_thenHardDeletes() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(TrashUiEvent.OnToggleSelection(2))

        // 未确认前不得删除
        viewModel.onEvent(TrashUiEvent.OnRequestBatchDelete)
        assertTrue(viewModel.uiState.value.isBatchDeleteDialogVisible)
        assertTrue("二次确认前条目必须存在", existsInStore(1) && existsInStore(2))

        viewModel.onEvent(TrashUiEvent.OnConfirmBatchDelete)

        assertFalse("确认后应物理删除", existsInStore(1))
        assertFalse(existsInStore(2))
        assertTrue("未选中的条目应保留", existsInStore(3))
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(effects.anyMessageContains("已彻底删除 2 条"))
    }

    @Test
    fun batchDelete_cancel_keepsItems() = runTest {
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(TrashUiEvent.OnRequestBatchDelete)

        viewModel.onEvent(TrashUiEvent.OnCancelBatchDelete)

        assertFalse(viewModel.uiState.value.isBatchDeleteDialogVisible)
        assertTrue("取消后条目必须保留", existsInStore(1))
    }

    // ------------------------------------------------------------------
    // 单条还原 / 单条彻底删除 / 清空
    // ------------------------------------------------------------------

    @Test
    fun singleRestore_restoresEntry() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(TrashUiEvent.OnRestore(1))

        assertNull(deletedAtOf(1))
        assertTrue(effects.anyMessageContains("已还原"))
    }

    @Test
    fun singleHardDelete_requiresConfirm() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(TrashUiEvent.OnRequestDeleteForever(1))
        assertEquals(1L, viewModel.uiState.value.pendingDeleteId)
        assertTrue("确认前条目必须存在", existsInStore(1))

        viewModel.onEvent(TrashUiEvent.OnConfirmDeleteForever)

        assertFalse(existsInStore(1))
        assertNull(viewModel.uiState.value.pendingDeleteId)
        assertTrue(effects.anyMessageContains("已彻底删除"))
    }

    @Test
    fun clearTrash_requiresConfirm_removesAllTrashed() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(TrashUiEvent.OnRequestClearTrash)
        assertTrue(viewModel.uiState.value.isClearConfirmVisible)

        viewModel.onEvent(TrashUiEvent.OnConfirmClearTrash)

        assertFalse(existsInStore(1))
        assertFalse(existsInStore(2))
        assertFalse(existsInStore(3))
        assertTrue("存活条目不得被清空", existsInStore(4))
        assertTrue(effects.anyMessageContains("已清空回收站"))
    }

    // ------------------------------------------------------------------
    // 失败反馈（保留多选态，不崩溃）
    // ------------------------------------------------------------------

    @Test
    fun batchRestore_failure_keepsSelectionAndReportsFailure() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        repository.failOnRestoreMemos = true

        viewModel.onEvent(TrashUiEvent.OnBatchRestore)

        assertTrue("失败后应保留多选态", viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(1L), viewModel.uiState.value.selectedIds)
        assertTrue(effects.anyMessageContains("操作失败，请重试"))
    }

    @Test
    fun batchDelete_failure_reportsFailure() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(TrashUiEvent.OnRequestBatchDelete)
        repository.failOnHardDeleteMemos = true

        viewModel.onEvent(TrashUiEvent.OnConfirmBatchDelete)

        assertTrue(effects.anyMessageContains("操作失败，请重试"))
    }

    @Test
    fun batchDeleteFailure_keepsSelectionAndItems() = runTest {
        // 失败时不仅提示，且必须回滚：保留多选态与选择集，条目不得被删除
        viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(1))
        repository.failOnHardDeleteMemos = true

        viewModel.onEvent(TrashUiEvent.OnRequestBatchDelete)
        viewModel.onEvent(TrashUiEvent.OnConfirmBatchDelete)

        assertTrue("失败后应保留多选态", viewModel.uiState.value.isSelectionMode)
        assertEquals("失败后应保留选择集", setOf(1L), viewModel.uiState.value.selectedIds)
        assertTrue("失败后条目不得被删除", existsInStore(1))
    }

    // ------------------------------------------------------------------
    // 保留天数纯函数口径
    // ------------------------------------------------------------------

    @Test
    fun remainingAndElapsedDays_matchRetentionPolicy() = runTest {
        val deletedAt = now - 1 * day
        assertEquals(1, viewModel.elapsedDays(deletedAt))
        assertEquals(TrashRetention.RETENTION_DAYS - 1, viewModel.remainingDays(deletedAt))

        val justDeleted = now
        assertEquals(0, viewModel.elapsedDays(justDeleted))
        assertEquals(TrashRetention.RETENTION_DAYS, viewModel.remainingDays(justDeleted))
    }
}
