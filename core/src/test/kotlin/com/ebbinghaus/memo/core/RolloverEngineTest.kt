package com.ebbinghaus.memo.core

import com.ebbinghaus.memo.core.engine.EbbinghausScheduler
import com.ebbinghaus.memo.core.engine.RolloverEngine
import com.ebbinghaus.memo.core.engine.SchedulableTask
import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.core.model.ReviewStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 每日限额与超量顺延引擎纯 JVM 单元测试套件
 *
 * 覆盖：
 * 1. 每日上限为 0 时返回 0 条且截断
 * 2. 每日上限为负数时的保护
 * 3. 超量任务按 dueDate ASC (最早到期优先) 严格截断顺延
 * 4. 相同到期日稳定按 taskId 升序排序
 * 5. 跨多天未登录任务全量合并计算且不降档、不扣分
 * 6. 未到期任务不进入今日队列
 * 7. 跳过任务后重新排期（移出今日队列）
 * 8. 任务量小于上限时不截断且全量排入
 */
class RolloverEngineTest {

    private data class FakeTask(
        override val taskId: Long,
        override val targetDueDate: LocalDate
    ) : SchedulableTask

    private val today = LocalDate.of(2026, 9, 14)

    @Test
    fun testDailyLimitZero_returnsZeroBatchAndTriggersLimitReached() {
        val tasks = listOf(
            FakeTask(1, today.minusDays(1)),
            FakeTask(2, today)
        )
        val plan = RolloverEngine.planReviewBatch(tasks, today, dailyLimit = 0)

        assertEquals(0, plan.todayBatch.size)
        assertEquals(2, plan.deferredTasks.size)
        assertEquals(2, plan.totalPendingCount)
        assertTrue(plan.isLimitReached)
    }

    @Test
    fun testDailyLimitNegative_handledAsZero() {
        val tasks = listOf(
            FakeTask(1, today)
        )
        val plan = RolloverEngine.planReviewBatch(tasks, today, dailyLimit = -5)

        assertEquals(0, plan.todayBatch.size)
        assertEquals(1, plan.deferredTasks.size)
        assertEquals(1, plan.totalPendingCount)
        assertTrue(plan.isLimitReached)
    }

    @Test
    fun testEarliestDueDateFirst_truncatesAtLimitAndDefersOverflow() {
        val taskOldest = FakeTask(1, today.minusDays(4))
        val taskOlder = FakeTask(2, today.minusDays(2))
        val taskYesterday = FakeTask(3, today.minusDays(1))
        val taskToday = FakeTask(4, today)

        val pending = listOf(taskToday, taskOlder, taskOldest, taskYesterday)
        val plan = RolloverEngine.planReviewBatch(pending, today, dailyLimit = 2)

        assertEquals(4, plan.totalPendingCount)
        assertEquals(2, plan.todayBatch.size)
        assertEquals(2, plan.deferredTasks.size)
        assertTrue(plan.isLimitReached)

        // 验证今日批次为到期时间最早的 2 个任务
        assertEquals(1L, plan.todayBatch[0].taskId)
        assertEquals(2L, plan.todayBatch[1].taskId)

        // 验证顺延批次为后续到期的 2 个任务
        assertEquals(3L, plan.deferredTasks[0].taskId)
        assertEquals(4L, plan.deferredTasks[1].taskId)
    }

    @Test
    fun testSameDueDate_sortedByTaskIdAscending() {
        val task3 = FakeTask(30, today)
        val task1 = FakeTask(10, today)
        val task2 = FakeTask(20, today)

        val plan = RolloverEngine.planReviewBatch(listOf(task3, task1, task2), today, dailyLimit = 3)
        assertEquals(3, plan.todayBatch.size)
        assertEquals(10L, plan.todayBatch[0].taskId)
        assertEquals(20L, plan.todayBatch[1].taskId)
        assertEquals(30L, plan.todayBatch[2].taskId)
    }

    @Test
    fun testMultiDayAbsence_accumulatesAllTasksWithoutPenaltyOrStageReduction() {
        // 用户 7 天未登录，各阶段到期任务均保留并汇入候选池
        val task7DaysAgo = FakeTask(1, today.minusDays(7))
        val task5DaysAgo = FakeTask(2, today.minusDays(5))
        val task3DaysAgo = FakeTask(3, today.minusDays(3))
        val taskToday = FakeTask(4, today)
        val taskFuture = FakeTask(5, today.plusDays(2))

        val allTasks = listOf(taskToday, task7DaysAgo, taskFuture, task3DaysAgo, task5DaysAgo)
        val plan = RolloverEngine.planReviewBatch(allTasks, today, dailyLimit = 10)

        // 排除未来任务后，到期的 4 个任务全部被无损归入今日待复习
        assertEquals(4, plan.totalPendingCount)
        assertEquals(4, plan.todayBatch.size)
        assertEquals(0, plan.deferredTasks.size)
        assertFalse(plan.isLimitReached)

        assertEquals(1L, plan.todayBatch[0].taskId)
        assertEquals(2L, plan.todayBatch[1].taskId)
        assertEquals(3L, plan.todayBatch[2].taskId)
        assertEquals(4L, plan.todayBatch[3].taskId)
    }

    @Test
    fun testFutureTasks_notIncludedInBatch() {
        val futureTask1 = FakeTask(1, today.plusDays(1))
        val futureTask2 = FakeTask(2, today.plusDays(5))

        val plan = RolloverEngine.planReviewBatch(listOf(futureTask1, futureTask2), today, dailyLimit = 10)
        assertEquals(0, plan.totalPendingCount)
        assertEquals(0, plan.todayBatch.size)
        assertEquals(0, plan.deferredTasks.size)
        assertFalse(plan.isLimitReached)
    }

    @Test
    fun testSkipTaskRepositioning_movesOutOfTodayQueue() {
        val taskDueToday = FakeTask(1, today)
        val taskDueTomorrow = FakeTask(2, today.plusDays(1))

        // 初始状态：taskDueToday 排入今日批次
        val initialPlan = RolloverEngine.planReviewBatch(listOf(taskDueToday, taskDueTomorrow), today, dailyLimit = 10)
        assertEquals(1, initialPlan.todayBatch.size)
        assertEquals(1L, initialPlan.todayBatch[0].taskId)

        // 用户对 taskDueToday 点击“跳过”
        val skipResult = EbbinghausScheduler.calculateNextReview(
            currentStage = ReviewStage.STAGE_3,
            rating = ReviewRating.SKIP,
            reviewDate = today
        )
        // 跳过的新到期日为 today + 1 天
        assertEquals(today.plusDays(1), skipResult.nextReviewDate)

        // 数据库更新后，taskDueToday 的 targetDueDate 变为 today + 1
        val updatedTask1 = FakeTask(1, skipResult.nextReviewDate)

        // 再次为今天重新排期：task1 已顺延至明日，今日待复习队列自动清空
        val refreshedPlan = RolloverEngine.planReviewBatch(listOf(updatedTask1, taskDueTomorrow), today, dailyLimit = 10)
        assertEquals(0, refreshedPlan.todayBatch.size)
        assertEquals(0, refreshedPlan.totalPendingCount)
    }

    @Test
    fun testLimitGreaterThanPendingCount_includesAllWithoutTruncation() {
        val tasks = listOf(
            FakeTask(1, today.minusDays(1)),
            FakeTask(2, today)
        )
        val plan = RolloverEngine.planReviewBatch(tasks, today, dailyLimit = 50)
        assertEquals(2, plan.todayBatch.size)
        assertEquals(0, plan.deferredTasks.size)
        assertEquals(2, plan.totalPendingCount)
        assertFalse(plan.isLimitReached)
    }
}
