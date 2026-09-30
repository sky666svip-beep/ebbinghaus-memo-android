package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.ui.memolist.MemoListEffect
import com.ebbinghaus.memo.ui.memolist.MemoListUiEvent
import com.ebbinghaus.memo.ui.memolist.MemoListViewModel
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
 * 写操作失败反馈与状态一致性单元测试（P1-4）
 *
 * 覆盖：批量删除 / 批量打标签 / 单条删除落库失败时——
 * 1. 不得静默（必须派发失败提示）；
 * 2. 不得使 UI 状态与数据不一致（失败时保留多选态与选择集）；
 * 3. 成功路径行为保持不变（回归保护）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoListBatchFailureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeMemoRepository
    private lateinit var viewModel: MemoListViewModel

    @Before
    fun setUp() {
        repository = FakeMemoRepository()
        repository.memosState.value = listOf(
            KnowledgeMemoEntity(id = 1, content = "条目一"),
            KnowledgeMemoEntity(id = 2, content = "条目二"),
            KnowledgeMemoEntity(id = 3, content = "条目三")
        )
        viewModel = MemoListViewModel(repository)
    }

    /**
     * 订阅一次性消息流并返回累积列表。
     *
     * `effect` 为无回放的 `SharedFlow`，须先订阅再触发事件；使用 `backgroundScope`
     * 承载常驻收集协程，测试结束自动取消。
     */
    private fun TestScope.collectEffects(): MutableList<MemoListEffect> {
        val collected = mutableListOf<MemoListEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.toList(collected)
        }
        return collected
    }

    @Test
    fun batchDeleteFailure_keepsSelectionAndEmitsFailure() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(2))
        repository.failOnSoftDeleteMemos = true

        viewModel.onEvent(MemoListUiEvent.OnBatchDelete)
        viewModel.onEvent(MemoListUiEvent.OnConfirmBatchDelete)

        val state = viewModel.uiState.value
        assertTrue("失败后应保留多选态", state.isSelectionMode)
        assertEquals("失败后应保留原选择集", setOf(1L, 2L), state.selectedIds)
        assertFalse("失败后二次确认弹窗应已关闭", state.isBatchDeleteDialogVisible)
        assertTrue(
            "失败后不得修改数据",
            repository.memosState.value.all { it.deletedAt == null }
        )
        assertTrue(
            "必须派发失败提示",
            effects.any { it is MemoListEffect.ShowSnackbar && it.message.contains("失败") }
        )
    }

    @Test
    fun batchAddTagFailure_keepsSelectionAndEmitsFailure() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(2))
        repository.failOnAddTagToMemos = true

        viewModel.onEvent(MemoListUiEvent.OnOpenTagPicker)
        viewModel.onEvent(MemoListUiEvent.OnBatchAddTag("标签"))

        val state = viewModel.uiState.value
        assertTrue("失败后应保留多选态", state.isSelectionMode)
        assertEquals("失败后应保留原选择集", setOf(1L, 2L), state.selectedIds)
        assertFalse("失败后候选弹窗应已关闭", state.isTagPickerVisible)
        assertTrue(
            "失败后不得修改数据",
            repository.memosState.value.none { it.tags.contains("标签") }
        )
        assertTrue(
            "必须派发失败提示",
            effects.any { it is MemoListEffect.ShowSnackbar && it.message.contains("失败") }
        )
    }

    @Test
    fun immediateDeleteFailure_emitsFailure() = runTest {
        val effects = collectEffects()
        repository.failOnSoftDeleteMemo = true

        // 正控（F1 补正）：先证明「删除确认弹窗」状态确可被观测为 true，
        // 否则下方「立即删除不得产生弹窗状态」的 false 断言缺乏判别力（可能恒真）。
        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(1))
        assertTrue("正控：请求删除应打开确认弹窗", viewModel.uiState.value.isDeleteDialogVisible)
        assertEquals("正控：请求删除应置位待删 id", 1L, viewModel.uiState.value.pendingDeleteId)
        viewModel.onEvent(MemoListUiEvent.OnCancelDeleteMemo)

        // OnDeleteMemo 保持「立即删除、不弹确认」语义不变
        viewModel.onEvent(MemoListUiEvent.OnDeleteMemo(1))

        val state = viewModel.uiState.value
        // F1 补正（QA 回归发现）：补回原 QaFixIndependentVerificationTest 丢失的 2 条断言。
        // 它们守护「立即删除（OnDeleteMemo）≠ 请求删除（OnRequestDeleteMemo）」这一语义边界——
        // 立即删除路径**不得**产生确认弹窗状态（OnDeleteMemo 的实现完全不触碰
        // pendingDeleteId / isDeleteDialogVisible）。若该路径被误改为走确认流程
        // （例如委托给 OnRequestDeleteMemo），这 2 条断言必红。
        assertFalse("立即删除语义不得打开确认弹窗", state.isDeleteDialogVisible)
        assertNull("立即删除语义不得产生待删 id", state.pendingDeleteId)
        assertEquals("失败后数据应保持不变", 3, state.memos.size)
        assertTrue(
            "必须派发失败提示",
            effects.any { it is MemoListEffect.ShowSnackbar && it.message.contains("失败") }
        )
    }

    @Test
    fun confirmDeleteFailure_emitsFailureAndKeepsData() = runTest {
        val effects = collectEffects()
        repository.failOnSoftDeleteMemo = true

        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(1))
        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)

        val state = viewModel.uiState.value
        assertFalse(state.isDeleteDialogVisible)
        assertNull(state.pendingDeleteId)
        assertEquals("失败后数据应保持不变", 3, state.memos.size)
        assertTrue(
            "必须派发失败提示",
            effects.any { it is MemoListEffect.ShowSnackbar && it.message.contains("失败") }
        )
    }

    @Test
    fun batchDeleteSuccess_stillExitsSelectionAndEmitsSuccess() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(2))

        viewModel.onEvent(MemoListUiEvent.OnBatchDelete)
        viewModel.onEvent(MemoListUiEvent.OnConfirmBatchDelete)

        val state = viewModel.uiState.value
        assertFalse("成功后应退出多选态", state.isSelectionMode)
        assertTrue("成功后应清空选择集", state.selectedIds.isEmpty())
        assertEquals(listOf(3L), state.memos.map { it.id })
        assertTrue(
            "成功路径应派发成功提示",
            effects.any { it is MemoListEffect.ShowSnackbar && it.message.contains("回收站") }
        )
    }

    @Test
    fun batchAddTagSuccess_clearsSelectionExitsMode_emitsSuccess() = runTest {
        // 条目 1 预置「已有」标签，验证成功路径去重合并
        repository.memosState.value = listOf(
            KnowledgeMemoEntity(id = 1, content = "条目一", tags = listOf("已有")),
            KnowledgeMemoEntity(id = 2, content = "条目二"),
            KnowledgeMemoEntity(id = 3, content = "条目三")
        )
        val effects = collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(2))

        viewModel.onEvent(MemoListUiEvent.OnOpenTagPicker)
        viewModel.onEvent(MemoListUiEvent.OnBatchAddTag("新标签"))

        val state = viewModel.uiState.value
        assertFalse("成功后应退出多选态", state.isSelectionMode)
        assertTrue("成功后应清空选择集", state.selectedIds.isEmpty())
        assertEquals(listOf("已有", "新标签"), repository.memosState.value.first { it.id == 1L }.tags)
        assertEquals(listOf("新标签"), repository.memosState.value.first { it.id == 2L }.tags)
        assertTrue(
            "成功应派发含新标签的提示",
            effects.any { it is MemoListEffect.ShowSnackbar && it.message.contains("新标签") }
        )
    }

    @Test
    fun saveNewMemoFailure_keepsEditorOpenAndEmitsFailure() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnOpenAddDialog)
        assertTrue(viewModel.uiState.value.isEditorDialogVisible)
        repository.failOnCreateMemo = true

        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(id = null, content = "新知识点", notes = "", tags = emptyList())
        )

        val state = viewModel.uiState.value
        assertTrue("保存失败应保留编辑器", state.isEditorDialogVisible)
        assertNull(state.editingMemo)
        assertEquals("保存失败不得写入数据", 3, state.memos.size)
        assertTrue(
            "必须派发失败提示",
            effects.any { it is MemoListEffect.ShowSnackbar && it.message.contains("失败") }
        )
    }

    @Test
    fun saveEditedMemoFailure_keepsEditorAndEditingMemo() = runTest {
        val effects = collectEffects()
        val target = viewModel.uiState.value.memos.first { it.id == 1L }
        viewModel.onEvent(MemoListUiEvent.OnOpenEditDialog(target))
        repository.failOnUpdateMemo = true

        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(id = 1L, content = "改后内容", notes = "", tags = emptyList())
        )

        val state = viewModel.uiState.value
        assertTrue("保存失败应保留编辑器", state.isEditorDialogVisible)
        assertEquals("保存失败应保留 editingMemo", target, state.editingMemo)
        assertEquals(
            "保存失败不得修改原条目",
            "条目一",
            state.memos.first { it.id == 1L }.content
        )
        assertTrue(
            "必须派发失败提示",
            effects.any { it is MemoListEffect.ShowSnackbar && it.message.contains("失败") }
        )
    }

    @Test
    fun saveBlankContent_isRejectedSilentlyWithoutFailureSnackbar() = runTest {
        val effects = collectEffects()

        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(id = null, content = "   ", notes = "", tags = emptyList())
        )

        assertEquals("空白内容不得写入", 3, viewModel.uiState.value.memos.size)
        assertFalse(
            "空白内容属前置校验拒绝，不应派发「失败」提示",
            effects.any { it is MemoListEffect.ShowSnackbar && it.message.contains("失败") }
        )
    }
}
