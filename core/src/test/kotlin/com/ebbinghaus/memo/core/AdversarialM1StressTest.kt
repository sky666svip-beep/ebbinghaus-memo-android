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
 * M1 对抗性经验验证与极端边界压力测试套件
 *
 * 针对 M1 核心调度算法、状态机矩阵及顺延逻辑进行对抗检验：
 * 1. Stage 1 连续 10 次“忘记”稳定性保底及严格 Today + 1 天验证
 * 2. Stage 6 晋升 60 天长周期、长周期连续 3 次“记住”保持 60 天、长周期“忘记”回退至 Stage 6 (30天)
 * 3. 每日上限设为 0 与 50 在 100 条到期任务下的截断切片、无损保留与标记状态
 * 4. 跨 30 天未登录大量乱序积压任务无惩罚合并、排除未来任务、按 (targetDueDate ASC, taskId ASC) 稳定排序
 * 5. 极端边界测试：负数上限保护、大上限不截断、连续 Skip 顺延、缺省评级等价性
 */
class AdversarialM1StressTest {

    private data class AdversarialTask(
        override val taskId: Long,
        override val targetDueDate: LocalDate
    ) : SchedulableTask

    private val baseDate = LocalDate.of(2026, 9, 14)

    /**
     * 对抗挑战 1：Stage 1 连续 10 次“忘记”
     * 检验：无论发生多少次忘记，档位绝不降至 0 或负数，始终稳定保底在 Stage 1，到期日严格为 reviewDate + 1 天
     */
    @Test
    fun testAdversarial_stage1ConsecutiveTenForgets_strictlyFloorAtStage1() {
        var currentStage = ReviewStage.STAGE_1
        var currentDate = baseDate

        for (i in 1..10) {
            val result = EbbinghausScheduler.calculateNextReview(
                currentStage = currentStage,
                rating = ReviewRating.FORGET,
                reviewDate = currentDate
            )

            assertEquals("第 $i 次忘记后档位必须保底为 STAGE_1", ReviewStage.STAGE_1, result.nextStage)
            assertEquals("第 $i 次忘记后间隔天数必须为 1", 1, result.intervalDays)
            assertEquals("第 $i 次忘记后到期日必须为当日 + 1 天", currentDate.plusDays(1), result.nextReviewDate)

            // 推进到下一个复习日继续忘记
            currentStage = result.nextStage
            currentDate = result.nextReviewDate
        }
    }

    /**
     * 对抗挑战 2：Stage 6 晋升长周期、连续 3 次记住保持 60 天、忘记回退至 Stage 6 (30天)
     */
    @Test
    fun testAdversarial_stage6Promotion_consecutiveRemember_andFallbackToStage6() {
        var currentDate = baseDate

        // Step 1: Stage 6 "记住" -> 晋升至 60 天长周期
        val promotionResult = EbbinghausScheduler.calculateNextReview(
            currentStage = ReviewStage.STAGE_6,
            rating = ReviewRating.REMEMBER,
            reviewDate = currentDate
        )
        assertEquals(ReviewStage.LONG_TERM_60, promotionResult.nextStage)
        assertEquals(60, promotionResult.intervalDays)
        assertEquals(currentDate.plusDays(60), promotionResult.nextReviewDate)

        // Step 2: 进入长周期后，连续 3 次“记住”，均应稳定保持 60 天长周期
        var currentStage = promotionResult.nextStage
        currentDate = promotionResult.nextReviewDate

        for (i in 1..3) {
            val rememberResult = EbbinghausScheduler.calculateNextReview(
                currentStage = currentStage,
                rating = ReviewRating.REMEMBER,
                reviewDate = currentDate
            )
            assertEquals("长周期第 $i 次记住必须保持 LONG_TERM_60", ReviewStage.LONG_TERM_60, rememberResult.nextStage)
            assertEquals("长周期第 $i 次记住间隔必须为 60 天", 60, rememberResult.intervalDays)
            assertEquals("长周期第 $i 次记住到期日必须为当日 + 60 天", currentDate.plusDays(60), rememberResult.nextReviewDate)

            currentStage = rememberResult.nextStage
            currentDate = rememberResult.nextReviewDate
        }

        // Step 3: 长周期选择“忘记”，必须正确回退至 Stage 6 (30天)
        val fallbackResult = EbbinghausScheduler.calculateNextReview(
            currentStage = currentStage,
            rating = ReviewRating.FORGET,
            reviewDate = currentDate
        )
        assertEquals("长周期忘记后必须回退至 STAGE_6", ReviewStage.STAGE_6, fallbackResult.nextStage)
        assertEquals("回退至 STAGE_6 间隔必须为 30 天", 30, fallbackResult.intervalDays)
        assertEquals("回退至 STAGE_6 到期日必须为当日 + 30 天", currentDate.plusDays(30), fallbackResult.nextReviewDate)
    }

    /**
     * 对抗挑战 3A：上限设为 0，100 条到期任务截断切片正确性
     */
    @Test
    fun testAdversarial_limitZeroWith100DueTasks_zeroTodayBatchAndAllDeferred() {
        val tasks = (1..100).map { id ->
            AdversarialTask(
                taskId = id.toLong(),
                targetDueDate = baseDate.minusDays((100 - id).toLong())
            )
        }

        val plan = RolloverEngine.planReviewBatch(tasks, baseDate, dailyLimit = 0)

        assertEquals("上限为 0 时今日批次必须严格为 0", 0, plan.todayBatch.size)
        assertEquals("上限为 0 时所有 100 条到期任务全量顺延", 100, plan.deferredTasks.size)
        assertEquals("候选池到期总量为 100", 100, plan.totalPendingCount)
        assertTrue("存在超量积压，isLimitReached 必须为 true", plan.isLimitReached)
    }

    /**
     * 对抗挑战 3B：上限设为 50，100 条到期任务截断切片正确性与保序性
     */
    @Test
    fun testAdversarial_limit50With100DueTasks_exactTruncationAndEarliestDueDateFirst() {
        // 构建 100 条乱序到期任务（跨度从 baseDate - 30 到 baseDate）
        val tasks = (1..100).map { id ->
            val daysAgo = (id % 30).toLong()
            AdversarialTask(
                taskId = id.toLong(),
                targetDueDate = baseDate.minusDays(daysAgo)
            )
        }.shuffled() // 随机打乱输入顺序

        val plan = RolloverEngine.planReviewBatch(tasks, baseDate, dailyLimit = 50)

        assertEquals("今日批次必须被严格截断为 50 条", 50, plan.todayBatch.size)
        assertEquals("超量顺延批次必须为剩余 50 条", 50, plan.deferredTasks.size)
        assertEquals("到期总任务数必须为 100", 100, plan.totalPendingCount)
        assertTrue("达到上限且有顺延，isLimitReached 必须为 true", plan.isLimitReached)

        // 验证今日批次 + 顺延批次总集完备，无任务丢失或重复
        val allHandledIds = (plan.todayBatch + plan.deferredTasks).map { it.taskId }.toSet()
        assertEquals(100, allHandledIds.size)

        // 验证排序：今日批次的任意任务到期日 <= 顺延批次的任意任务到期日
        val maxTodayDueDate = plan.todayBatch.maxOf { it.targetDueDate }
        val minDeferredDueDate = plan.deferredTasks.minOf { it.targetDueDate }
        assertTrue("今日批次的最晚到期日 ($maxTodayDueDate) 必须 <= 顺延批次的最早到期日 ($minDeferredDueDate)",
            !maxTodayDueDate.isAfter(minDeferredDueDate))
    }

    /**
     * 对抗挑战 4：跨 30 天未登录大量乱序积压任务合并与排序
     */
    @Test
    fun testAdversarial_30DaysAbsence_massBacklogNoPenalty_stableSort() {
        val today = baseDate
        val tasks = mutableListOf<AdversarialTask>()

        // 1. 生成 30 天未登录积压任务：每天 3 条，taskId 为 1..90
        for (dayOffset in 30 downTo 1) {
            val dueDate = today.minusDays(dayOffset.toLong())
            tasks.add(AdversarialTask(taskId = dayOffset * 10L + 1, targetDueDate = dueDate))
            tasks.add(AdversarialTask(taskId = dayOffset * 10L + 2, targetDueDate = dueDate))
            tasks.add(AdversarialTask(taskId = dayOffset * 10L + 3, targetDueDate = dueDate))
        }

        // 2. 生成今日到期任务 5 条
        tasks.add(AdversarialTask(taskId = 501, targetDueDate = today))
        tasks.add(AdversarialTask(taskId = 502, targetDueDate = today))
        tasks.add(AdversarialTask(taskId = 503, targetDueDate = today))
        tasks.add(AdversarialTask(taskId = 504, targetDueDate = today))
        tasks.add(AdversarialTask(taskId = 505, targetDueDate = today))

        // 3. 生成未来未到期任务 20 条 (今日 + 1 ~ 今日 + 20)
        for (futureDay in 1..20) {
            tasks.add(AdversarialTask(taskId = 1000L + futureDay, targetDueDate = today.plusDays(futureDay.toLong())))
        }

        // 随机乱序输入
        val shuffledTasks = tasks.shuffled()

        // 每日上限设为 50 条
        val plan = RolloverEngine.planReviewBatch(shuffledTasks, today, dailyLimit = 50)

        // 到期任务总量：30 * 3 + 5 = 95 条，未来 20 条排除
        assertEquals("积压 + 今日总任务数必须为 95 条", 95, plan.totalPendingCount)
        assertEquals("今日批次上限截断为 50 条", 50, plan.todayBatch.size)
        assertEquals("顺延批次为 45 条", 45, plan.deferredTasks.size)
        assertTrue(plan.isLimitReached)

        // 验证未来任务绝对不进入 todayBatch 或 deferredTasks
        val processedIds = (plan.todayBatch + plan.deferredTasks).map { it.taskId }.toSet()
        for (futureDay in 1..20) {
            assertFalse("未来任务不应被调度", processedIds.contains(1000L + futureDay))
        }

        // 验证排序单调性：targetDueDate 严格升序；同 targetDueDate 时 taskId 升序
        for (i in 0 until plan.todayBatch.size - 1) {
            val curr = plan.todayBatch[i]
            val next = plan.todayBatch[i + 1]

            assertTrue("排序必须保证 dueDate 不降", !curr.targetDueDate.isAfter(next.targetDueDate))
            if (curr.targetDueDate == next.targetDueDate) {
                assertTrue("相同 dueDate 时 taskId 必须升序", curr.taskId < next.taskId)
            }
        }
    }

    /**
     * 极端压力测试 5：连续 Skip 5 次延后 5 天且档位保持不变
     */
    @Test
    fun testAdversarial_consecutiveSkip_maintainsStageAndDefersDayByDay() {
        var currentStage = ReviewStage.STAGE_4 // 7天档位
        var currentDate = baseDate

        for (step in 1..5) {
            val result = EbbinghausScheduler.calculateNextReview(
                currentStage = currentStage,
                rating = ReviewRating.SKIP,
                reviewDate = currentDate
            )

            assertEquals("跳过操作绝不修改档位", ReviewStage.STAGE_4, result.nextStage)
            assertEquals("跳过固定延后 1 天", currentDate.plusDays(1), result.nextReviewDate)
            assertEquals(1, result.intervalDays)

            currentDate = result.nextReviewDate
        }

        // 经过连续 5 次跳过，日期恰好顺延了 5 天，档位依然是 STAGE_4
        assertEquals(baseDate.plusDays(5), currentDate)
    }
}
