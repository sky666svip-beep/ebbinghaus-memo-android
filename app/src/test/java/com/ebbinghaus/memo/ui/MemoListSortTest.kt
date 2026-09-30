package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.repository.MemoSortOption
import com.ebbinghaus.memo.ui.memolist.MemoListUiEvent
import com.ebbinghaus.memo.ui.memolist.MemoListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 列表排序（E10）单元测试
 *
 * 覆盖：
 * 1. 四种排序在**刻意乱序 + 并列键**数据集下产生确定性 id 序列（含 id DESC 兜底）；
 * 2. 排序选择重启后保持（经 PreferenceStore）；
 * 3. 切换排序不重置搜索词与标签筛选；
 * 4. 排序只影响展示顺序，不改变数据集成员（且软删条目不得混入）。
 *
 * 说明：C 档反向合并（2026-09-18）已把「简单数据集版」的 2 条用例并入本文件的对抗性版本：
 * - `fourSorts_produceDeterministicOrder` → 由 `fourSorts_onShuffledData_produceExactIdSequences` 覆盖（数据集更难、断言更严）
 * - `sortChange_doesNotMutateDataSet` → 由 `sorting_neverChangesMembership_orSoftDeletedLeakIn` 覆盖（遍历全部排序项 + 软删不泄漏）
 *
 * 说明：真实 DAO 的 `ORDER BY` 语义需真机/模拟器验证（本环境无设备）；
 * 此处以与生产口径一致的测试替身守护 ViewModel 与排序管线契约。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoListSortTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun seedRepository(): FakeMemoRepository {
        val repo = FakeMemoRepository()
        repo.memosState.value = listOf(
            KnowledgeMemoEntity(id = 1, content = "A", createdAt = 1000, updatedAt = 5000),
            KnowledgeMemoEntity(id = 2, content = "B", createdAt = 3000, updatedAt = 1000),
            KnowledgeMemoEntity(id = 3, content = "C", createdAt = 2000, updatedAt = 4000)
        )
        repo.reviewSortInfo[1] = ReviewSortInfo(stageLevel = 5, dueDateEpochDay = 100)
        repo.reviewSortInfo[2] = ReviewSortInfo(stageLevel = 2, dueDateEpochDay = 50)
        repo.reviewSortInfo[3] = ReviewSortInfo(stageLevel = 1, dueDateEpochDay = 200)
        return repo
    }

    private fun MemoListViewModel.ids(): List<Long> = uiState.value.memos.map { it.id }

    @Test
    fun sortSelection_survivesViewModelRecreation() = runTest {
        val repo = seedRepository()
        val prefs = FakePreferenceStore()

        val first = MemoListViewModel(repo, prefs)
        first.onEvent(MemoListUiEvent.OnSortOptionSelected(MemoSortOption.DUE_ASC))
        assertEquals(MemoSortOption.DUE_ASC, prefs.sortOption.value)

        // 模拟「重启」：新建 ViewModel，从同一偏好存储恢复
        val second = MemoListViewModel(repo, prefs)
        assertEquals(MemoSortOption.DUE_ASC, second.uiState.value.sortOption)
        assertEquals(listOf(2L, 1L, 3L), second.ids())
    }

    @Test
    fun switchingSort_doesNotResetSearchOrTag() = runTest {
        val repo = FakeMemoRepository()
        repo.memosState.value = listOf(
            KnowledgeMemoEntity(id = 1, content = "Alpha 内容", tags = listOf("T1")),
            KnowledgeMemoEntity(id = 2, content = "Beta 内容", tags = listOf("T2"))
        )
        val viewModel = MemoListViewModel(repo)

        viewModel.onEvent(MemoListUiEvent.OnSearchQueryChanged("Alpha"))
        viewModel.onEvent(MemoListUiEvent.OnTagSelected("T1"))
        assertEquals(1, viewModel.uiState.value.memos.size)

        viewModel.onEvent(MemoListUiEvent.OnSortOptionSelected(MemoSortOption.CREATED_DESC))

        assertEquals("Alpha", viewModel.uiState.value.searchQuery)
        assertEquals("T1", viewModel.uiState.value.selectedTag)
        assertEquals(1, viewModel.uiState.value.memos.size)
    }

    // ------------------------------------------------------------------
    // 对抗性数据集：刻意乱序 + 并列键，验证排序确定性与 id 兜底稳定性
    // ------------------------------------------------------------------

    /**
     * 刻意乱序数据集：插入顺序对任何排序键都既非升序也非降序；
     * 并刻意制造「档位并列」(30/40) 与「到期日并列」(20/50) 以验证 id DESC 兜底稳定性。
     */
    private fun shuffledRepository(): FakeMemoRepository {
        val repo = FakeMemoRepository()
        repo.memosState.value = listOf(
            KnowledgeMemoEntity(id = 30, content = "m30", createdAt = 300, updatedAt = 300),
            KnowledgeMemoEntity(id = 10, content = "m10", createdAt = 500, updatedAt = 100),
            KnowledgeMemoEntity(id = 50, content = "m50", createdAt = 400, updatedAt = 400),
            KnowledgeMemoEntity(id = 20, content = "m20", createdAt = 100, updatedAt = 500),
            KnowledgeMemoEntity(id = 40, content = "m40", createdAt = 200, updatedAt = 200)
        )
        repo.reviewSortInfo[10] = ReviewSortInfo(stageLevel = 3, dueDateEpochDay = 300)
        repo.reviewSortInfo[20] = ReviewSortInfo(stageLevel = 1, dueDateEpochDay = 100)
        repo.reviewSortInfo[30] = ReviewSortInfo(stageLevel = 5, dueDateEpochDay = 200)
        repo.reviewSortInfo[40] = ReviewSortInfo(stageLevel = 5, dueDateEpochDay = 400)
        repo.reviewSortInfo[50] = ReviewSortInfo(stageLevel = 2, dueDateEpochDay = 100)
        return repo
    }

    @Test
    fun fourSorts_onShuffledData_produceExactIdSequences() = runTest {
        val viewModel = MemoListViewModel(shuffledRepository())

        viewModel.onEvent(MemoListUiEvent.OnSortOptionSelected(MemoSortOption.CREATED_DESC))
        assertEquals("创建时间倒序（含 id 兜底）", listOf(10L, 50L, 30L, 40L, 20L), viewModel.ids())

        viewModel.onEvent(MemoListUiEvent.OnSortOptionSelected(MemoSortOption.UPDATED_DESC))
        assertEquals("更新时间倒序（含 id 兜底）", listOf(20L, 50L, 30L, 40L, 10L), viewModel.ids())

        viewModel.onEvent(MemoListUiEvent.OnSortOptionSelected(MemoSortOption.STAGE_DESC))
        assertEquals("档位倒序 + 并列档位按 id 倒序", listOf(40L, 30L, 10L, 50L, 20L), viewModel.ids())

        viewModel.onEvent(MemoListUiEvent.OnSortOptionSelected(MemoSortOption.DUE_ASC))
        assertEquals("到期日正序 + 并列到期日按 id 倒序", listOf(50L, 20L, 30L, 10L, 40L), viewModel.ids())
    }

    @Test
    fun sorting_isDeterministic_acrossRepeatedApplication() = runTest {
        val viewModel = MemoListViewModel(shuffledRepository())
        val expected = listOf(50L, 20L, 30L, 10L, 40L)
        repeat(5) {
            viewModel.onEvent(MemoListUiEvent.OnSortOptionSelected(MemoSortOption.DUE_ASC))
            assertEquals("同一排序反复应用必须稳定", expected, viewModel.ids())
        }
    }

    @Test
    fun sorting_neverChangesMembership_orSoftDeletedLeakIn() = runTest {
        val repo = shuffledRepository()
        // 追加一条软删条目：排序与筛选均不得让它出现
        repo.memosState.value = repo.memosState.value + KnowledgeMemoEntity(
            id = 99, content = "软删", createdAt = 9999, updatedAt = 9999, deletedAt = 12345L
        )
        val viewModel = MemoListViewModel(repo)

        val allIds = mutableSetOf<Long>()
        MemoSortOption.entries.forEach { option ->
            viewModel.onEvent(MemoListUiEvent.OnSortOptionSelected(option))
            val ids = viewModel.ids()
            assertEquals("任何排序下集合大小恒为 5", 5, ids.size)
            assertTrue("软删条目不得出现", !ids.contains(99L))
            allIds += ids
        }
        assertEquals(setOf(10L, 20L, 30L, 40L, 50L), allIds)
    }
}
