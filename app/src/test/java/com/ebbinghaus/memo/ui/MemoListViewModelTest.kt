package com.ebbinghaus.memo.ui

import android.util.Log
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.ui.memolist.MemoListUiEvent
import com.ebbinghaus.memo.ui.memolist.MemoListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * MemoListViewModel 响应式流与生命周期事件单元测试套件
 *
 * 另含 `observeAllTags()` 标签流加固回归：标签查询失败时不得崩溃、主列表不受影响、
 * 且必须写入 `Log.w`（非静默失效）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var memoRepository: FakeMemoRepository
    private lateinit var viewModel: MemoListViewModel

    @Before
    fun setUp() {
        Log.reset()
        memoRepository = FakeMemoRepository()
        viewModel = MemoListViewModel(memoRepository)
    }

    @Test
    fun testInitialState_emptyMemos() = runTest {
        val state = viewModel.uiState.value
        assertTrue(state.memos.isEmpty())
        assertTrue(state.allTags.isEmpty())
        assertNull(state.selectedTag)
        assertEquals("", state.searchQuery)
        assertFalse(state.isEditorDialogVisible)
        assertNull(state.editingMemo)
    }

    @Test
    fun testCreateMemo_persistsAndUpdatesTags() = runTest {
        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = null,
                content = "Kotlin 协程深入解析",
                notes = "重点理解 Flow 与 Channel",
                tags = listOf("Kotlin", "Android")
            )
        )

        val state = viewModel.uiState.value
        assertEquals(1, state.memos.size)
        assertEquals("Kotlin 协程深入解析", state.memos[0].content)
        assertEquals("重点理解 Flow 与 Channel", state.memos[0].notes)
        assertEquals(listOf("Android", "Kotlin"), state.allTags)
        assertFalse(state.isEditorDialogVisible)
    }

    @Test
    fun testUpdateMemo_modifiesContentAndRetainsProgress() = runTest {
        // 先创建一条知识点
        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = null,
                content = "原始知识点",
                notes = "原始笔记",
                tags = listOf("Tag1")
            )
        )
        val createdId = viewModel.uiState.value.memos.first().id

        // 编辑该知识点
        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = createdId,
                content = "已更新的知识点",
                notes = "已更新的笔记",
                tags = listOf("Tag1", "Tag2")
            )
        )

        val state = viewModel.uiState.value
        assertEquals(1, state.memos.size)
        assertEquals("已更新的知识点", state.memos[0].content)
        assertEquals("已更新的笔记", state.memos[0].notes)
        assertEquals(listOf("Tag1", "Tag2"), state.allTags)
    }

    @Test
    fun testDeleteMemo_removesEntity() = runTest {
        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = null,
                content = "待删除知识点",
                notes = "",
                tags = emptyList()
            )
        )
        val memoId = viewModel.uiState.value.memos.first().id
        assertEquals(1, viewModel.uiState.value.memos.size)

        viewModel.onEvent(MemoListUiEvent.OnDeleteMemo(memoId))
        assertEquals(0, viewModel.uiState.value.memos.size)
    }

    @Test
    fun testSearchQuery_matchesContentOrNotes() = runTest {
        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = null,
                content = "Jetpack Compose 架构规范",
                notes = "声明式 UI 核心",
                tags = listOf("Compose")
            )
        )
        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = null,
                content = "Room 数据库事务分析",
                notes = "SQLite 底层 Foreign Key",
                tags = listOf("Room")
            )
        )

        // 检索正文包含 Compose
        viewModel.onEvent(MemoListUiEvent.OnSearchQueryChanged("Compose"))
        assertEquals(1, viewModel.uiState.value.memos.size)
        assertEquals("Jetpack Compose 架构规范", viewModel.uiState.value.memos[0].content)

        // 检索笔记包含 SQLite
        viewModel.onEvent(MemoListUiEvent.OnSearchQueryChanged("SQLite"))
        assertEquals(1, viewModel.uiState.value.memos.size)
        assertEquals("Room 数据库事务分析", viewModel.uiState.value.memos[0].content)

        // 清空搜索
        viewModel.onEvent(MemoListUiEvent.OnSearchQueryChanged(""))
        assertEquals(2, viewModel.uiState.value.memos.size)
    }

    @Test
    fun testTagSelection_filtersAndToggles() = runTest {
        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = null,
                content = "Kotlin 条目",
                notes = "",
                tags = listOf("Kotlin")
            )
        )
        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = null,
                content = "Android 条目",
                notes = "",
                tags = listOf("Android")
            )
        )

        // 选中 Kotlin
        viewModel.onEvent(MemoListUiEvent.OnTagSelected("Kotlin"))
        assertEquals("Kotlin", viewModel.uiState.value.selectedTag)
        assertEquals(1, viewModel.uiState.value.memos.size)
        assertEquals("Kotlin 条目", viewModel.uiState.value.memos[0].content)

        // 再次点击相同标签 -> 反选取消
        viewModel.onEvent(MemoListUiEvent.OnTagSelected("Kotlin"))
        assertNull(viewModel.uiState.value.selectedTag)
        assertEquals(2, viewModel.uiState.value.memos.size)
    }

    @Test
    fun testDialogStateTransitions() = runTest {
        viewModel.onEvent(MemoListUiEvent.OnOpenAddDialog)
        assertTrue(viewModel.uiState.value.isEditorDialogVisible)
        assertNull(viewModel.uiState.value.editingMemo)

        val sampleMemo = KnowledgeMemoEntity(id = 100, content = "测试", notes = "")
        viewModel.onEvent(MemoListUiEvent.OnOpenEditDialog(sampleMemo))
        assertTrue(viewModel.uiState.value.isEditorDialogVisible)
        assertEquals(sampleMemo, viewModel.uiState.value.editingMemo)

        viewModel.onEvent(MemoListUiEvent.OnDismissDialog)
        assertFalse(viewModel.uiState.value.isEditorDialogVisible)
        assertNull(viewModel.uiState.value.editingMemo)
    }

    @Test
    fun testBlankContent_rejected() = runTest {
        viewModel.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = null,
                content = "   ",
                notes = "note",
                tags = emptyList()
            )
        )
        assertTrue(viewModel.uiState.value.memos.isEmpty())
    }

    // ------------------------------------------------------------------
    // observeAllTags() 标签流加固回归
    // ------------------------------------------------------------------

    @Test
    fun observeAllTags_dependencyThrows_doesNotCrashAndMainListStillWorks() = runTest {
        val delegate = FakeMemoRepository().apply {
            memosState.value = listOf(KnowledgeMemoEntity(id = 1, content = "存活条目", tags = listOf("T")))
        }
        val repository = ThrowingTagsMemoRepository(delegate, IllegalStateException("模拟标签查询失败"))

        Log.reset()
        val vm = MemoListViewModel(repository)

        // 标签读取失败：不崩溃，标签集保持为空
        assertTrue("标签流失败时 allTags 应为空", vm.uiState.value.allTags.isEmpty())
        // 主列表不受影响（走 searchMemos，未抛异常）
        assertEquals(1, vm.uiState.value.memos.size)
        assertEquals("存活条目", vm.uiState.value.memos[0].content)
        // 非静默：必须记录 Log.w
        assertEquals("标签失败必须被 Log.w 记录一次", 1, Log.wCount)
        assertEquals("EbbinghausLaunch", Log.lastTag)
    }

    @Test
    fun observeAllTags_happyPath_stillPopulatesTags() = runTest {
        val delegate = FakeMemoRepository()
        val vm = MemoListViewModel(delegate)

        vm.onEvent(
            MemoListUiEvent.OnSaveMemo(
                id = null,
                content = "Kotlin 条目",
                notes = "",
                tags = listOf("Kotlin", "Android")
            )
        )

        assertEquals(listOf("Android", "Kotlin"), vm.uiState.value.allTags)
        assertEquals("正常路径不得产生任何警告/错误日志", 0, Log.wCount + Log.eCount)
    }

    @Test
    fun observeAllTagsFailure_doesNotAffectMainListErrorState() = runTest {
        val delegate = FakeMemoRepository().apply {
            memosState.value = listOf(KnowledgeMemoEntity(id = 7, content = "独立条目"))
        }
        val repository = ThrowingTagsMemoRepository(delegate, RuntimeException("模拟标签查询失败"))
        val vm = MemoListViewModel(repository)

        // 主列表正常 → errorMessage 不应被标签失败污染
        assertNull("标签失败不得写入主列表 errorMessage", vm.uiState.value.errorMessage)
        assertEquals(1, vm.uiState.value.memos.size)
    }
}

/** 仅让 `getAllMemos()`（标签流）抛异常的 [MemoRepository] 替身，其余委托给真实内存替身 */
private class ThrowingTagsMemoRepository(
    private val delegate: FakeMemoRepository,
    private val error: Throwable
) : MemoRepository by delegate {
    override fun getAllMemos(): Flow<List<KnowledgeMemoEntity>> = flow { throw error }
}
