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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 删除后 5 秒撤销（P2-1）单元测试
 *
 * 覆盖：单条 / 批量删除派发撤销型 Snackbar（`actionLabel` + `actionKey`）、
 * 撤销令牌比对（连删两条仅最新可撤销、旧回调失效）、过期条目不崩溃、
 * 还原失败提示、以及「立即删除」[MemoListUiEvent.OnDeleteMemo] 语义不被破坏。
 *
 * 说明：5 秒窗口与 Snackbar 交互属 Compose 行为，无法在 JVM 断言（架构 R9），
 * 此处以 ViewModel 状态机与令牌契约守护可测部分。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoListUndoTest {

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

    private fun TestScope.collectEffects(): MutableList<MemoListEffect> {
        val collected = mutableListOf<MemoListEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.toList(collected)
        }
        return collected
    }

    private fun List<MemoListEffect>.lastUndoSnackbar(): MemoListEffect.ShowSnackbar? =
        filterIsInstance<MemoListEffect.ShowSnackbar>().lastOrNull { it.actionKey != null }

    private fun List<MemoListEffect>.anyMessageContains(fragment: String): Boolean =
        any { it is MemoListEffect.ShowSnackbar && it.message.contains(fragment) }

    private fun deletedAtOf(id: Long): Long? =
        repository.memosState.value.first { it.id == id }.deletedAt

    @Test
    fun singleDelete_emitsUndoSnackbar_withActionLabelAndKey() = runTest {
        val effects = collectEffects()

        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(1))
        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)

        val snackbar = effects.lastUndoSnackbar()
        assertNotNull("单条删除应派发撤销型 Snackbar", snackbar)
        assertEquals("撤销", snackbar?.actionLabel)
        assertNotNull("撤销型 Snackbar 必须带批次令牌", snackbar?.actionKey)
        assertTrue("消息应仍含「回收站」", snackbar?.message?.contains("回收站") == true)
        assertNotNull("单条删除后该条应处于回收站", deletedAtOf(1))
    }

    @Test
    fun singleDelete_thenUndo_restoresDeletedAtNull() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(1))
        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)
        val key = effects.lastUndoSnackbar()?.actionKey ?: error("缺少撤销令牌")

        viewModel.onEvent(MemoListUiEvent.OnSnackbarAction(key))

        assertNull("撤销后 deletedAt 应恢复为 null", deletedAtOf(1))
        assertTrue("应提示已还原", effects.anyMessageContains("已还原"))
    }

    @Test
    fun batchDelete_thenUndo_restoresWholeBatch() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(1))
        viewModel.onEvent(MemoListUiEvent.OnToggleSelection(2))
        viewModel.onEvent(MemoListUiEvent.OnBatchDelete)
        viewModel.onEvent(MemoListUiEvent.OnConfirmBatchDelete)
        val key = effects.lastUndoSnackbar()?.actionKey ?: error("缺少撤销令牌")

        viewModel.onEvent(MemoListUiEvent.OnSnackbarAction(key))

        assertNull(deletedAtOf(1))
        assertNull(deletedAtOf(2))
        assertTrue("批量撤销应提示还原条数", effects.anyMessageContains("已还原 2 条"))
    }

    @Test
    fun secondDelete_overwritesToken_oldKeyBecomesInvalid() = runTest {
        val effects = collectEffects()

        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(1))
        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)
        val firstKey = effects.lastUndoSnackbar()?.actionKey ?: error("缺少第一次令牌")

        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(2))
        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)
        val secondKey = effects.lastUndoSnackbar()?.actionKey ?: error("缺少第二次令牌")

        assertNotEquals("连删两条应产生不同令牌", firstKey, secondKey)

        // 旧令牌失效 → 无操作（不得误还原第 1 条）
        viewModel.onEvent(MemoListUiEvent.OnSnackbarAction(firstKey))
        assertNotNull("旧令牌不得还原第 1 条", deletedAtOf(1))
        assertNotNull("旧令牌不得还原第 2 条", deletedAtOf(2))

        // 新令牌 → 仅还原最新一批（第 2 条）
        viewModel.onEvent(MemoListUiEvent.OnSnackbarAction(secondKey))
        assertNull("新令牌应还原第 2 条", deletedAtOf(2))
        assertNotNull("第 1 条应保持删除态", deletedAtOf(1))
    }

    @Test
    fun unknownKey_isIgnoredWithoutSideEffect() = runTest {
        collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(1))
        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)

        viewModel.onEvent(MemoListUiEvent.OnSnackbarAction("undo-not-exist"))

        assertNotNull("未知令牌不得触发还原", deletedAtOf(1))
    }

    @Test
    fun undo_whenEntryAlreadyPurged_reportsExpiredWithoutCrash() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(1))
        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)
        val key = effects.lastUndoSnackbar()?.actionKey ?: error("缺少撤销令牌")

        // 模拟该条已被 purgeExpiredTrash 物理清除（PRD 边界 2）
        repository.memosState.value = repository.memosState.value.filterNot { it.id == 1L }

        viewModel.onEvent(MemoListUiEvent.OnSnackbarAction(key))

        assertTrue("过期条目应提示无法还原", effects.anyMessageContains("已过期"))
    }

    @Test
    fun undo_failure_emitsFailureSnackbar() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(1))
        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)
        val key = effects.lastUndoSnackbar()?.actionKey ?: error("缺少撤销令牌")

        repository.failOnRestoreMemos = true
        viewModel.onEvent(MemoListUiEvent.OnSnackbarAction(key))

        assertTrue(effects.anyMessageContains("失败"))
    }

    @Test
    fun immediateDelete_doesNotEmitUndoSnackbar_semanticsPreserved() = runTest {
        val effects = collectEffects()

        // OnDeleteMemo 为「立即删除、不弹确认」语义，且不派发撤销型 Snackbar
        viewModel.onEvent(MemoListUiEvent.OnDeleteMemo(1))

        assertNull("立即删除不得派发撤销型 Snackbar", effects.lastUndoSnackbar())
        assertNotNull("立即删除后该条应处于回收站", deletedAtOf(1))
    }

    @Test
    fun undoTokenReplay_afterSuccessfulUndo_isIgnored() = runTest {
        val effects = collectEffects()
        viewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(1))
        viewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo)
        val key = effects.lastUndoSnackbar()?.actionKey ?: error("缺少撤销令牌")

        viewModel.onEvent(MemoListUiEvent.OnSnackbarAction(key))
        assertNull("首次撤销应还原", deletedAtOf(1))

        // 令牌已在撤销成功后清空 → 重放同一令牌应无副作用（不得重复还原 / 抛异常）
        viewModel.onEvent(MemoListUiEvent.OnSnackbarAction(key))
        assertNull("重放后仍为存活态，不产生副作用", deletedAtOf(1))
    }
}
