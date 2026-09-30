package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.core.model.ReviewStage
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import com.ebbinghaus.memo.ui.review.ReviewUiEvent
import com.ebbinghaus.memo.ui.review.ReviewViewModel
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
 * ReviewViewModel 交互流转与 5 类评级提交单元测试套件
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDate = LocalDate.of(2026, 9, 14)
    private lateinit var reviewRepository: FakeReviewRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var memoRepository: FakeMemoRepository

    @Before
    fun setUp() {
        reviewRepository = FakeReviewRepository()
        settingsRepository = FakeSettingsRepository()
        memoRepository = FakeMemoRepository()
    }

    private fun createSampleTask(
        id: Long,
        content: String,
        stage: ReviewStage = ReviewStage.STAGE_1,
        dueDate: LocalDate = testDate,
        notes: String = ""
    ): MemoWithReviewTask {
        return MemoWithReviewTask(
            memo = KnowledgeMemoEntity(
                id = id,
                content = content,
                notes = notes,
                tags = listOf("测试")
            ),
            reviewTask = ReviewTaskEntity(
                id = id * 10,
                memoId = id,
                stageLevel = stage.level,
                dueDate = dueDate,
                reviewCount = 0
            )
        )
    }

    @Test
    fun testEmptyQueue_startsAsCompleted() = runTest {
        val viewModel = ReviewViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            memoRepository = memoRepository,
            todayProvider = { testDate }
        )

        val state = viewModel.uiState.value
        assertTrue(state.reviewQueue.isEmpty())
        assertTrue(state.isCompleted)
        assertEquals(0, state.totalBatchCount)
    }

    @Test
    fun testBatchLoading_respectsDailyLimitAndEarliestDueDate() = runTest {
        settingsRepository.updateDailyLimit(2)
        // 构造 3 个到期任务，dueDate 分别为 9-12, 9-13, 9-14
        val t1 = createSampleTask(1, "任务1", dueDate = testDate.minusDays(2))
        val t2 = createSampleTask(2, "任务2", dueDate = testDate.minusDays(1))
        val t3 = createSampleTask(3, "任务3", dueDate = testDate)
        reviewRepository.tasksState.value = listOf(t3, t1, t2) // 乱序放入

        val viewModel = ReviewViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            memoRepository = memoRepository,
            todayProvider = { testDate }
        )

        val state = viewModel.uiState.value
        assertEquals(2, state.reviewQueue.size)
        assertEquals(3, state.totalPendingCount)
        assertEquals(1, state.deferredCount)
        // 验证 Earliest Due Date First：先安排任务1，再安排任务2
        assertEquals(1L, state.reviewQueue[0].memo.id)
        assertEquals(2L, state.reviewQueue[1].memo.id)
        assertFalse(state.isCompleted)
    }

    @Test
    fun testSubmitRating_advancesQueueAndCompletesAtEnd() = runTest {
        settingsRepository.updateDailyLimit(2)
        val t1 = createSampleTask(1, "任务1")
        val t2 = createSampleTask(2, "任务2")
        reviewRepository.tasksState.value = listOf(t1, t2)

        val viewModel = ReviewViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            memoRepository = memoRepository,
            todayProvider = { testDate }
        )

        assertEquals(0, viewModel.uiState.value.currentIndex)
        assertNotNull(viewModel.uiState.value.currentItem)
        assertEquals(1L, viewModel.uiState.value.currentItem?.memo?.id)

        // 提交第一个任务评级：记住 (REMEMBER)
        viewModel.onEvent(ReviewUiEvent.OnSubmitRating(ReviewRating.REMEMBER))
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(2L, viewModel.uiState.value.currentItem?.memo?.id)
        assertFalse(viewModel.uiState.value.isCompleted)

        // 提交第二个任务评级：模糊 (VAGUE)
        viewModel.onEvent(ReviewUiEvent.OnSubmitRating(ReviewRating.VAGUE))
        assertEquals(2, viewModel.uiState.value.currentIndex)
        assertTrue(viewModel.uiState.value.isCompleted)

        // 验证仓储层确实收到了对应的两次评级提交
        assertEquals(2, reviewRepository.submittedRatings.size)
        assertEquals(ReviewRating.REMEMBER, reviewRepository.submittedRatings[0].second)
        assertEquals(ReviewRating.VAGUE, reviewRepository.submittedRatings[1].second)
    }

    @Test
    fun testSkipCurrent_submitsSkipRating() = runTest {
        val t1 = createSampleTask(1, "任务1")
        reviewRepository.tasksState.value = listOf(t1)

        val viewModel = ReviewViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            memoRepository = memoRepository,
            todayProvider = { testDate }
        )

        viewModel.onEvent(ReviewUiEvent.OnSkipCurrent)

        assertTrue(viewModel.uiState.value.isCompleted)
        assertEquals(1, reviewRepository.submittedRatings.size)
        assertEquals(ReviewRating.SKIP, reviewRepository.submittedRatings[0].second)
    }

    @Test
    fun testDefaultReviewed_submitsDefaultReviewedRating() = runTest {
        val t1 = createSampleTask(1, "任务1")
        reviewRepository.tasksState.value = listOf(t1)

        val viewModel = ReviewViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            memoRepository = memoRepository,
            todayProvider = { testDate }
        )

        viewModel.onEvent(ReviewUiEvent.OnSubmitRating(ReviewRating.DEFAULT_REVIEWED))

        assertTrue(viewModel.uiState.value.isCompleted)
        assertEquals(1, reviewRepository.submittedRatings.size)
        assertEquals(ReviewRating.DEFAULT_REVIEWED, reviewRepository.submittedRatings[0].second)
    }

    @Test
    fun testInlineNotesEditing_andSaveUpdatesStateAndRepo() = runTest {
        val t1 = createSampleTask(1, "任务1", notes = "初始笔记")
        reviewRepository.tasksState.value = listOf(t1)
        memoRepository.memosState.value = listOf(t1.memo)

        val viewModel = ReviewViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            memoRepository = memoRepository,
            todayProvider = { testDate }
        )

        // 开始编辑笔记
        viewModel.onEvent(ReviewUiEvent.OnStartEditNotes)
        assertTrue(viewModel.uiState.value.isEditingNotes)
        assertEquals("初始笔记", viewModel.uiState.value.notesDraft)

        // 修改草稿
        viewModel.onEvent(ReviewUiEvent.OnNotesDraftChanged("更新后的绝妙记忆提示"))
        assertEquals("更新后的绝妙记忆提示", viewModel.uiState.value.notesDraft)

        // 保存草稿
        viewModel.onEvent(ReviewUiEvent.OnSaveNotesDraft("更新后的绝妙记忆提示"))
        assertFalse(viewModel.uiState.value.isEditingNotes)
        assertEquals("更新后的绝妙记忆提示", viewModel.uiState.value.currentItem?.memo?.notes)

        // 验证 memoRepository 也同步写入了更新
        assertEquals("更新后的绝妙记忆提示", memoRepository.memosState.value[0].notes)
    }

    @Test
    fun testToggleDetails_togglesBoolean() = runTest {
        val t1 = createSampleTask(1, "任务1")
        reviewRepository.tasksState.value = listOf(t1)

        val viewModel = ReviewViewModel(
            reviewRepository = reviewRepository,
            settingsRepository = settingsRepository,
            memoRepository = memoRepository,
            todayProvider = { testDate }
        )

        assertFalse(viewModel.uiState.value.isDetailExpanded)
        viewModel.onEvent(ReviewUiEvent.OnToggleDetails)
        assertTrue(viewModel.uiState.value.isDetailExpanded)
        viewModel.onEvent(ReviewUiEvent.OnToggleDetails)
        assertFalse(viewModel.uiState.value.isDetailExpanded)
    }
}
