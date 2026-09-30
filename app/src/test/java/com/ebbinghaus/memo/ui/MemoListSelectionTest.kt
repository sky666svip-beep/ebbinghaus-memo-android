package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.ui.memolist.MemoListUiEvent
import com.ebbinghaus.memo.ui.memolist.MemoListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 批量多选操作（E11）单元测试
 *
 * 覆盖：长按进入多选与计数、全选/取消全选、退出清空、
 * 批量软删除（单事务语义）、批量打标签去重合并。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoListSelectionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeMemoRepository
    private lateinit var viewModel: MemoListViewModel

    @Before
    fun setUp() {
        repository = FakeMemoRepository()
        repository.memosState.value = listOf(
            KnowledgeMemoEntity(id = 1, content = "条目一", tags = listOf("已有")),
            KnowledgeMemoEntity(id = 2, content = "条目二"),
            KnowledgeMemoEntity(id = 3, content = "条目三")
        )
        viewModel = MemoListViewModel(repository)
    }

    @Test
    fun longPress_entersSelectionModeWithSingleSelection() = runTest {
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(2))

        val state = viewModel.uiState.value
        assertTrue(state.isSelectionMode)
        assertEquals(setOf(2L), state.selectedIds)
    }

    @Test
    fun longPress_whileAlreadyInSelectionMode_togglesInsteadOfResetting() = runTest {
        // 先进入多选态并勾选 1、2
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(2))
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedIds)

        // 多选态下再次长按第 3 条：应「新增勾选」，而非重置为仅 {3}（P1-2）
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(3))
        assertTrue("多选态下长按应保持多选态", viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selectedIds)

        // 多选态下长按已勾选的第 1 条：应「取消勾选」，其余保留
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        assertEquals(setOf(2L, 3L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun toggleSelection_addsAndRemoves() = runTest {
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(3))
        assertEquals(setOf(1L, 3L), viewModel.uiState.value.selectedIds)

        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(1))
        assertEquals(setOf(3L), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun selectAll_thenClearSelection_keepsMode() = runTest {
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnSelectAll)
        assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selectedIds)

        viewModel.onEvent(MemoListUiEvent.OnClearSelection)
        assertTrue(viewModel.uiState.value.selectedIds.isEmpty())
        assertTrue("取消全选应保持多选态", viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun exitSelectionMode_clearsSelection() = runTest {
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(2))
        viewModel.onEvent(MemoListUiEvent.OnExitSelectionMode)

        val state = viewModel.uiState.value
        assertFalse(state.isSelectionMode)
        assertTrue(state.selectedIds.isEmpty())
    }

    @Test
    fun batchDelete_softDeletesAllAndExitsSelection() = runTest {
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(2))

        viewModel.onEvent(MemoListUiEvent.OnBatchDelete)
        assertTrue(viewModel.uiState.value.isBatchDeleteDialogVisible)

        viewModel.onEvent(MemoListUiEvent.OnConfirmBatchDelete)

        val state = viewModel.uiState.value
        assertFalse(state.isSelectionMode)
        assertTrue(state.selectedIds.isEmpty())
        // 列表只剩 1 条存活
        assertEquals(listOf(3L), state.memos.map { it.id })
        // 两条均已软删除且可还原
        val trashed = repository.memosState.value.filter { it.deletedAt != null }.map { it.id }
        assertEquals(setOf(1L, 2L), trashed.toSet())

        repository.restoreMemo(1)
        repository.restoreMemo(2)
        assertEquals(3, repository.memosState.value.count { it.deletedAt == null })
    }

    @Test
    fun batchOperations_areNoOpWithoutSelection() = runTest {
        viewModel.onEvent(MemoListUiEvent.OnBatchDelete)
        assertFalse(viewModel.uiState.value.isBatchDeleteDialogVisible)

        viewModel.onEvent(MemoListUiEvent.OnOpenTagPicker)
        assertFalse(viewModel.uiState.value.isTagPickerVisible)
    }

    @Test
    fun longPress_inSelectionMode_withEmptySelection_addsInsteadOfStayingEmpty() = runTest {
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnClearSelection)
        assertTrue("前置条件：多选态但选择集为空", viewModel.uiState.value.selectedIds.isEmpty())

        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(2))

        val state = viewModel.uiState.value
        assertTrue(state.isSelectionMode)
        assertEquals("空选择集下长按应新增该条", setOf(2L), state.selectedIds)
    }

    @Test
    fun batchAddTag_mixedSelection_dedupesAndSkipsExisting() = runTest {
        repository.memosState.value = listOf(
            KnowledgeMemoEntity(id = 1, content = "已含", tags = listOf("目标")),
            KnowledgeMemoEntity(id = 2, content = "多标签", tags = listOf("A", "B")),
            KnowledgeMemoEntity(id = 3, content = "空标签", tags = emptyList()),
            KnowledgeMemoEntity(id = 4, content = "未选中", tags = listOf("A"))
        )
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(2))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(3))

        viewModel.onEvent(MemoListUiEvent.OnBatchAddTag("目标"))

        assertEquals("已含标签不得重复", listOf("目标"), repository.memosState.value.first { it.id == 1L }.tags)
        assertEquals("多标签应追加", listOf("A", "B", "目标"), repository.memosState.value.first { it.id == 2L }.tags)
        assertEquals("空标签应新增", listOf("目标"), repository.memosState.value.first { it.id == 3L }.tags)
        assertEquals("未选中条目不得被改动", listOf("A"), repository.memosState.value.first { it.id == 4L }.tags)
        // 反向合并自 `batchAddTag_dedupesAndSkipsExisting`（C 档 2026-09-18）：
        // 该断言原仅存于弱版，合并后不得丢失
        assertFalse("批量打标签后应自动退出多选态", viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun batchAddTag_blankOrEmpty_neverMutatesNorExitsSelection() = runTest {
        repository.memosState.value = listOf(
            KnowledgeMemoEntity(id = 1, content = "已含", tags = listOf("目标")),
            KnowledgeMemoEntity(id = 2, content = "多标签", tags = listOf("A", "B"))
        )
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))

        viewModel.onEvent(MemoListUiEvent.OnBatchAddTag("   "))
        viewModel.onEvent(MemoListUiEvent.OnBatchAddTag(""))

        assertEquals(listOf("目标"), repository.memosState.value.first { it.id == 1L }.tags)
        assertTrue("空白标签应保持多选态（未执行操作）", viewModel.uiState.value.isSelectionMode)
    }
}
