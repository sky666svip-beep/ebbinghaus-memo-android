package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.ui.memolist.MemoListUiEvent
import com.ebbinghaus.memo.ui.memolist.MemoListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 删除二次确认流程与筛选清空恢复路径单元测试
 *
 * 覆盖新增的 [MemoListUiEvent.OnRequestDeleteMemo] / [OnConfirmDeleteMemo] / [OnCancelDeleteMemo]
 * 三事件闭环，以及状态矩阵 S2「搜索/筛选无结果」的清空恢复路径。
 *
 * 注意：[MemoListUiEvent.OnDeleteMemo] 的「立即删除」语义由
 * `MemoListViewModelTest.testDeleteMemo_removesEntity` 单独守护，本套件不重复覆盖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoListDeleteConfirmTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var memoRepository: FakeMemoRepository
    private lateinit var viewModel: MemoListViewModel

    @Before
    fun setUp() {
        memoRepository = FakeMemoRepository()
        viewModel = MemoListViewModel(memoRepository)
    }

    private suspend fun addMemo(content: String, tags: List<String> = emptyList()): Long {
        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = null,
                content = content,
                notes = "",
                tags = tags
            )
        )
        return viewModel.uiState.value.memos.first { it.content == content }.id
    }

    @Test
    fun requestDelete_opensDialogWithoutDeleting() = runTest {
        val memoId = addMemo("待确认删除的知识点")

        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(memoId))

        val state = viewModel.uiState.value
        assertTrue(state.isDeleteDialogVisible)
        assertEquals(memoId, state.pendingDeleteId)
        // 请求删除不得产生副作用
        assertEquals(1, state.memos.size)
    }

    @Test
    fun cancelDelete_closesDialogAndKeepsMemo() = runTest {
        val memoId = addMemo("需要保留的知识点")

        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(memoId))
        viewModel.onEvent(MemoListUiEvent.OnCancelDeleteMemo)

        val state = viewModel.uiState.value
        assertFalse(state.isDeleteDialogVisible)
        assertNull(state.pendingDeleteId)
        assertEquals(1, state.memos.size)
    }

    @Test
    fun confirmDelete_removesMemoAndClosesDialog() = runTest {
        val memoId = addMemo("确认删除的知识点")

        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(memoId))
        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)

        val state = viewModel.uiState.value
        assertEquals(0, state.memos.size)
        assertFalse(state.isDeleteDialogVisible)
        assertNull(state.pendingDeleteId)
    }

    @Test
    fun confirmDelete_withoutPendingRequest_isNoOp() = runTest {
        addMemo("未发起删除请求的知识点")

        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)

        assertEquals(1, viewModel.uiState.value.memos.size)
    }

    @Test
    fun clearFilters_restoresFullListAfterNoMatch() = runTest {
        addMemo("Alpha 知识点", tags = listOf("Alpha"))
        addMemo("Beta 知识点")

        // 构造「搜索 + 标签」双重筛选无结果
        viewModel.onEvent(MemoListUiEvent.OnSearchQueryChanged("不存在的关键词"))
        assertEquals(0, viewModel.uiState.value.memos.size)
        assertTrue(viewModel.uiState.value.errorMessage == null)

        viewModel.onEvent(MemoListUiEvent.OnClearFilters)

        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertNull(state.selectedTag)
        assertEquals(2, state.memos.size)
    }

    @Test
    fun clearFilters_alsoResetsSelectedTag() = runTest {
        addMemo("Alpha 知识点", tags = listOf("Alpha"))
        addMemo("Beta 知识点", tags = listOf("Beta"))

        viewModel.onEvent(MemoListUiEvent.OnTagSelected("Alpha"))
        assertEquals(1, viewModel.uiState.value.memos.size)

        viewModel.onEvent(MemoListUiEvent.OnClearFilters)

        assertEquals(2, viewModel.uiState.value.memos.size)
        assertNull(viewModel.uiState.value.selectedTag)
    }
}
