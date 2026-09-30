package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.core.model.ReviewStage
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import com.ebbinghaus.memo.ui.review.ReviewViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * 每日复习上限透出与 `dailyLimit == 0` 暂停态（P2-9）单元测试
 *
 * 覆盖：`ReviewUiState.dailyLimit` 落值；`dailyLimit == 0` 时批次为空且 `isCompleted == true`
 * （即 UI 走「暂停页」分支而非「达成页」）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewDailyLimitTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDate: LocalDate = LocalDate.of(2026, 9, 14)

    private lateinit var reviewRepository: FakeReviewRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var memoRepository: FakeMemoRepository

    @Before
    fun setUp() {
        reviewRepository = FakeReviewRepository()
        settingsRepository = FakeSettingsRepository()
        memoRepository = FakeMemoRepository()
    }

    private fun createTask(id: Long): MemoWithReviewTask = MemoWithReviewTask(
        memo = KnowledgeMemoEntity(id = id, content = "任务$id"),
        reviewTask = ReviewTaskEntity(
            id = id * 10,
            memoId = id,
            stageLevel = ReviewStage.STAGE_1.level,
            dueDate = testDate,
            reviewCount = 0
        )
    )

    private fun newViewModel() = ReviewViewModel(
        reviewRepository = reviewRepository,
        settingsRepository = settingsRepository,
        memoRepository = memoRepository,
        todayProvider = { testDate }
    )

    @Test
    fun dailyLimit_reflectsSettings_zero() = runTest {
        settingsRepository.updateDailyLimit(0)
        assertEquals(0, newViewModel().uiState.value.dailyLimit)
    }

    @Test
    fun dailyLimit_reflectsSettings_positive() = runTest {
        settingsRepository.updateDailyLimit(5)
        assertEquals(5, newViewModel().uiState.value.dailyLimit)
    }

    @Test
    fun zeroLimit_withPendingTasks_yieldsEmptyBatchAndCompleted() = runTest {
        settingsRepository.updateDailyLimit(0)
        reviewRepository.tasksState.value = listOf(createTask(1), createTask(2))

        val state = newViewModel().uiState.value

        assertTrue("上限为 0 时不得排入任何复习项", state.reviewQueue.isEmpty())
        assertTrue("上限为 0 时应视为「本轮结束」，由 UI 走暂停页分支", state.isCompleted)
        assertEquals(0, state.dailyLimit)
    }

    @Test
    fun positiveLimit_withEmptyQueue_staysCompletedWithPositiveLimit() = runTest {
        settingsRepository.updateDailyLimit(20)
        val state = newViewModel().uiState.value

        assertTrue(state.isCompleted)
        assertEquals(">0 且队列空应走原「全部达成」分支", 20, state.dailyLimit)
    }

    @Test
    fun negativeLimit_isTreatedAsPaused() = runTest {
        // 负数上限同样落入 `dailyLimit <= 0` 的暂停分支（直接以负值构造，绕过 coerce）
        val viewModel = ReviewViewModel(
            reviewRepository = FakeReviewRepository(),
            settingsRepository = FakeSettingsRepository(UserSettingsEntity(dailyReviewLimit = -5)),
            memoRepository = FakeMemoRepository(),
            todayProvider = { testDate }
        )

        val state = viewModel.uiState.value
        assertTrue("负上限同样应视为暂停（UI 分支 dailyLimit <= 0）", state.dailyLimit <= 0)
        assertTrue(state.reviewQueue.isEmpty())
        assertTrue(state.isCompleted)
    }
}
