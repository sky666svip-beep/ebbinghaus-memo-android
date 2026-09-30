package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.core.model.ReviewStage
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import com.ebbinghaus.memo.ui.dashboard.DashboardUiEvent
import com.ebbinghaus.memo.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * DashboardViewModel 启动看板提醒与多天未登录检测单元测试套件
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 9, 14)
    private lateinit var reviewRepository: FakeReviewRepository
    private lateinit var settingsRepository: FakeSettingsRepository

    @Before
    fun setUp() {
        reviewRepository = FakeReviewRepository()
        settingsRepository = FakeSettingsRepository()
    }

    private fun addDueTask(id: Long, dueDate: LocalDate = today) {
        val task = MemoWithReviewTask(
            memo = KnowledgeMemoEntity(id = id, content = "知识点 $id"),
            reviewTask = ReviewTaskEntity(
                id = id * 10,
                memoId = id,
                stageLevel = ReviewStage.STAGE_1.level,
                dueDate = dueDate
            )
        )
        reviewRepository.tasksState.value = reviewRepository.tasksState.value + task
    }

    @Test
    fun testColdStart_withDueTasks_triggersDashboard() = runTest {
        addDueTask(1)
        settingsRepository.settingsState.value = UserSettingsEntity(
            dailyReviewLimit = 20,
            lastActiveDate = null,
            lastPromptedDate = null
        )

        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            todayProvider = { today }
        )

        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)

        val state = viewModel.uiState.value
        assertTrue(state.isVisible)
        assertEquals(1, state.dueTodayCount)
        assertFalse(state.isMultiDayAbsence)
        assertEquals(1, state.absentDays)
    }

    @Test
    fun testSameDayReopen_alreadyPromptedToday_doesNotTriggerDialog() = runTest {
        addDueTask(1)
        // 今天已经提示过
        settingsRepository.settingsState.value = UserSettingsEntity(
            dailyReviewLimit = 20,
            lastActiveDate = today,
            lastPromptedDate = today
        )

        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            todayProvider = { today }
        )

        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)

        val state = viewModel.uiState.value
        assertFalse(state.isVisible) // 不重复弹出弹窗骚扰
        assertEquals(1, state.dueTodayCount) // 但首页看板仍正确统计待复习数量
    }

    @Test
    fun testNextDayReopen_withDueTasks_triggersDialog() = runTest {
        addDueTask(1)
        val yesterday = today.minusDays(1)
        settingsRepository.settingsState.value = UserSettingsEntity(
            dailyReviewLimit = 20,
            lastActiveDate = yesterday,
            lastPromptedDate = yesterday
        )

        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            todayProvider = { today }
        )

        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)

        val state = viewModel.uiState.value
        assertTrue(state.isVisible)
        assertEquals(1, state.dueTodayCount)
        assertFalse(state.isMultiDayAbsence) // 相隔 1 天为次日，非多天
        assertEquals(1, state.absentDays)
    }

    @Test
    fun testMultiDayAbsence_detectsAbsenceDaysAndTriggers() = runTest {
        addDueTask(1)
        val fiveDaysAgo = today.minusDays(5)
        settingsRepository.settingsState.value = UserSettingsEntity(
            dailyReviewLimit = 20,
            lastActiveDate = fiveDaysAgo,
            lastPromptedDate = fiveDaysAgo
        )

        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            todayProvider = { today }
        )

        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)

        val state = viewModel.uiState.value
        assertTrue(state.isVisible)
        assertTrue(state.isMultiDayAbsence)
        assertEquals(5, state.absentDays)
        assertEquals(1, state.dueTodayCount)
    }

    @Test
    fun testDailyLimitZero_neverTriggersDashboard() = runTest {
        addDueTask(1)
        val fiveDaysAgo = today.minusDays(5)
        settingsRepository.settingsState.value = UserSettingsEntity(
            dailyReviewLimit = 0, // 每日上限为 0，停用复习
            lastActiveDate = fiveDaysAgo,
            lastPromptedDate = fiveDaysAgo
        )

        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            todayProvider = { today }
        )

        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)

        val state = viewModel.uiState.value
        assertFalse(state.isVisible)
        assertEquals(0, state.dueTodayCount)
    }

    @Test
    fun testDismissDialog_hidesDialog() = runTest {
        addDueTask(1)
        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            todayProvider = { today }
        )
        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)
        assertTrue(viewModel.uiState.value.isVisible)

        // 点击稍后 -> 关闭弹窗
        viewModel.onEvent(DashboardUiEvent.OnDismissDashboard)
        assertFalse(viewModel.uiState.value.isVisible)
    }

    @Test
    fun testConfirmNavigateToReview_hidesDialog() = runTest {
        addDueTask(1)
        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            todayProvider = { today }
        )
        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)
        assertTrue(viewModel.uiState.value.isVisible)

        // 点击立即复习 -> 关闭弹窗
        viewModel.onEvent(DashboardUiEvent.OnConfirmNavigateToReview)
        assertFalse(viewModel.uiState.value.isVisible)
    }
}
