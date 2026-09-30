package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.ui.detail.MemoDetailUiEvent
import com.ebbinghaus.memo.ui.detail.MemoDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * MemoDetailViewModel 状态流与生命周期测试
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var memoRepository: FakeMemoRepository
    private lateinit var reviewRepository: FakeReviewRepository

    @Before
    fun setUp() {
        memoRepository = FakeMemoRepository()
        reviewRepository = FakeReviewRepository()
    }

    @Test
    fun testLoadMemoDetails_success() = runTest {
        val memoId = memoRepository.createMemo(
            content = "uA\\le uB \\iff CF=1 \\lor ZF=1",
            notes = "无符号数比较逻辑",
            tags = listOf("汇编", "计算机组成原理")
        )
        val task = ReviewTaskEntity(
            id = 100L,
            memoId = memoId,
            stageLevel = 1,
            dueDate = LocalDate.now().plusDays(1)
        )
        val memo = memoRepository.getMemoById(memoId)!!
        reviewRepository.tasksState.value = listOf(
            MemoWithReviewTask(memo = memo, reviewTask = task)
        )

        val viewModel = MemoDetailViewModel(memoId, memoRepository, reviewRepository)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.memo)
        assertEquals("uA\\le uB \\iff CF=1 \\lor ZF=1", state.memo?.content)
        assertNotNull(state.reviewTask)
        assertEquals(1, state.reviewTask?.stageLevel)
    }

    @Test
    fun testUpdateMemo_savesAndRefreshes() = runTest {
        val memoId = memoRepository.createMemo(
            content = "原知识点内容",
            notes = "原笔记",
            tags = listOf("标签1")
        )
        val viewModel = MemoDetailViewModel(memoId, memoRepository, reviewRepository)

        viewModel.onEvent(MemoDetailUiEvent.OnOpenEditDialog)
        assertTrue(viewModel.uiState.value.isEditorDialogVisible)

        viewModel.onEvent(
            MemoDetailUiEvent.OnSaveMemo(
                id = memoId,
                content = "修改后的正文内容",
                notes = "修改后的新笔记",
                tags = listOf("标签1", "标签2")
            )
        )

        val state = viewModel.uiState.value
        assertFalse(state.isEditorDialogVisible)
        assertEquals("修改后的正文内容", state.memo?.content)
        assertEquals("修改后的新笔记", state.memo?.notes)
        assertEquals(listOf("标签1", "标签2"), state.memo?.tags)
    }

    @Test
    fun testDeleteMemo_marksDeleted() = runTest {
        val memoId = memoRepository.createMemo(content = "待删除知识点")
        val viewModel = MemoDetailViewModel(memoId, memoRepository, reviewRepository)

        viewModel.onEvent(MemoDetailUiEvent.OnOpenDeleteDialog)
        assertTrue(viewModel.uiState.value.isDeleteDialogOpen)

        viewModel.onEvent(MemoDetailUiEvent.OnConfirmDelete)
        val state = viewModel.uiState.value
        assertFalse(state.isDeleteDialogOpen)
        assertTrue(state.isDeleted)

        // 验证仓储内已被清除
        val found = memoRepository.getMemoById(memoId)
        org.junit.Assert.assertNull(found)
    }
}
