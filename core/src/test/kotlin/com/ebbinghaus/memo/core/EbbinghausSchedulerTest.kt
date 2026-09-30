package com.ebbinghaus.memo.core

import com.ebbinghaus.memo.core.engine.EbbinghausScheduler
import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.core.model.ReviewStage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 艾宾浩斯遗忘曲线纯函数调度算法单元测试套件
 *
 * 覆盖：
 * 1. 1~6 档标准升档（记住）
 * 2. 第 6 档晋升 60 天长周期；长周期再次记住保持 60 天
 * 3. 忘记降档（第 1 档保底，长周期退回第 6 档 30 天）
 * 4. 模糊保持当前档位与天数
 * 5. 已复习缺省操作等价于模糊
 * 6. 跳过保持档位且仅延期 1 天
 * 7. Day 0 录入初始到期日在 Day 1
 * 8. 月末、跨年与闰年自然日计算边界
 */
class EbbinghausSchedulerTest {

    private val baseDate = LocalDate.of(2026, 9, 14)

    @Test
    fun testRemember_standardAdvancement() {
        // Stage 1 -> 2 (2天)
        val res1 = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_1, ReviewRating.REMEMBER, baseDate)
        assertEquals(ReviewStage.STAGE_2, res1.nextStage)
        assertEquals(baseDate.plusDays(2), res1.nextReviewDate)
        assertEquals(2, res1.intervalDays)

        // Stage 2 -> 3 (4天)
        val res2 = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_2, ReviewRating.REMEMBER, baseDate)
        assertEquals(ReviewStage.STAGE_3, res2.nextStage)
        assertEquals(baseDate.plusDays(4), res2.nextReviewDate)
        assertEquals(4, res2.intervalDays)

        // Stage 3 -> 4 (7天)
        val res3 = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_3, ReviewRating.REMEMBER, baseDate)
        assertEquals(ReviewStage.STAGE_4, res3.nextStage)
        assertEquals(baseDate.plusDays(7), res3.nextReviewDate)
        assertEquals(7, res3.intervalDays)

        // Stage 4 -> 5 (15天)
        val res4 = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_4, ReviewRating.REMEMBER, baseDate)
        assertEquals(ReviewStage.STAGE_5, res4.nextStage)
        assertEquals(baseDate.plusDays(15), res4.nextReviewDate)
        assertEquals(15, res4.intervalDays)

        // Stage 5 -> 6 (30天)
        val res5 = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_5, ReviewRating.REMEMBER, baseDate)
        assertEquals(ReviewStage.STAGE_6, res5.nextStage)
        assertEquals(baseDate.plusDays(30), res5.nextReviewDate)
        assertEquals(30, res5.intervalDays)
    }

    @Test
    fun testRemember_stage6PromotesToLongTerm60Days() {
        // Stage 6 记住 -> 进入长周期 60 天
        val res = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_6, ReviewRating.REMEMBER, baseDate)
        assertEquals(ReviewStage.LONG_TERM_60, res.nextStage)
        assertEquals(baseDate.plusDays(60), res.nextReviewDate)
        assertEquals(60, res.intervalDays)
    }

    @Test
    fun testRemember_longTermMaintains60Days() {
        // 长周期再次记住 -> 维持长周期 60 天
        val res = EbbinghausScheduler.calculateNextReview(ReviewStage.LONG_TERM_60, ReviewRating.REMEMBER, baseDate)
        assertEquals(ReviewStage.LONG_TERM_60, res.nextStage)
        assertEquals(baseDate.plusDays(60), res.nextReviewDate)
        assertEquals(60, res.intervalDays)
    }

    @Test
    fun testForget_stage1StaysStage1() {
        // Stage 1 忘记 -> 最低维持 Stage 1 (+1天)
        val res = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_1, ReviewRating.FORGET, baseDate)
        assertEquals(ReviewStage.STAGE_1, res.nextStage)
        assertEquals(baseDate.plusDays(1), res.nextReviewDate)
        assertEquals(1, res.intervalDays)
    }

    @Test
    fun testForget_standardFallback() {
        // Stage 2 -> Stage 1 (1天)
        val res2 = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_2, ReviewRating.FORGET, baseDate)
        assertEquals(ReviewStage.STAGE_1, res2.nextStage)
        assertEquals(baseDate.plusDays(1), res2.nextReviewDate)

        // Stage 3 -> Stage 2 (2天)
        val res3 = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_3, ReviewRating.FORGET, baseDate)
        assertEquals(ReviewStage.STAGE_2, res3.nextStage)
        assertEquals(baseDate.plusDays(2), res3.nextReviewDate)

        // Stage 4 -> Stage 3 (4天)
        val res4 = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_4, ReviewRating.FORGET, baseDate)
        assertEquals(ReviewStage.STAGE_3, res4.nextStage)
        assertEquals(baseDate.plusDays(4), res4.nextReviewDate)

        // Stage 5 -> Stage 4 (7天)
        val res5 = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_5, ReviewRating.FORGET, baseDate)
        assertEquals(ReviewStage.STAGE_4, res5.nextStage)
        assertEquals(baseDate.plusDays(7), res5.nextReviewDate)

        // Stage 6 -> Stage 5 (15天)
        val res6 = EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_6, ReviewRating.FORGET, baseDate)
        assertEquals(ReviewStage.STAGE_5, res6.nextStage)
        assertEquals(baseDate.plusDays(15), res6.nextReviewDate)

        // LongTerm (7) -> Stage 6 (30天)
        val res7 = EbbinghausScheduler.calculateNextReview(ReviewStage.LONG_TERM_60, ReviewRating.FORGET, baseDate)
        assertEquals(ReviewStage.STAGE_6, res7.nextStage)
        assertEquals(baseDate.plusDays(30), res7.nextReviewDate)
        assertEquals(30, res7.intervalDays)
    }

    @Test
    fun testVague_maintainsCurrentStage() {
        for (stage in ReviewStage.entries) {
            val res = EbbinghausScheduler.calculateNextReview(stage, ReviewRating.VAGUE, baseDate)
            assertEquals(stage, res.nextStage)
            assertEquals(baseDate.plusDays(stage.intervalDays.toLong()), res.nextReviewDate)
            assertEquals(stage.intervalDays, res.intervalDays)
        }
    }

    @Test
    fun testDefaultReviewed_actsAsVague() {
        for (stage in ReviewStage.entries) {
            val resVague = EbbinghausScheduler.calculateNextReview(stage, ReviewRating.VAGUE, baseDate)
            val resDefault = EbbinghausScheduler.calculateNextReview(stage, ReviewRating.DEFAULT_REVIEWED, baseDate)
            assertEquals(resVague.nextStage, resDefault.nextStage)
            assertEquals(resVague.nextReviewDate, resDefault.nextReviewDate)
            assertEquals(resVague.intervalDays, resDefault.intervalDays)
        }
    }

    @Test
    fun testSkip_defersByOneDayWithoutChangingStage() {
        for (stage in ReviewStage.entries) {
            val res = EbbinghausScheduler.calculateNextReview(stage, ReviewRating.SKIP, baseDate)
            assertEquals(stage, res.nextStage)
            assertEquals(baseDate.plusDays(1), res.nextReviewDate)
            assertEquals(1, res.intervalDays)
        }
    }

    @Test
    fun testInitialSchedule_day0CreatedDay1FirstDue() {
        val createdDate = LocalDate.of(2026, 9, 14)
        val initRes = EbbinghausScheduler.initialSchedule(createdDate)
        assertEquals(ReviewStage.STAGE_1, initRes.nextStage)
        assertEquals(createdDate.plusDays(1), initRes.nextReviewDate)
        assertEquals(1, initRes.intervalDays)
    }

    @Test
    fun testDateBoundaries_leapYearAndMonthEnd() {
        // 闰年 2月28日录入，Stage 1 为 +1天 -> 2月29日
        val leapFeb28 = LocalDate.of(2024, 2, 28)
        val leapRes = EbbinghausScheduler.initialSchedule(leapFeb28)
        assertEquals(LocalDate.of(2024, 2, 29), leapRes.nextReviewDate)

        // 平年 2月28日录入，Stage 1 为 +1天 -> 3月1日
        val nonLeapFeb28 = LocalDate.of(2025, 2, 28)
        val nonLeapRes = EbbinghausScheduler.initialSchedule(nonLeapFeb28)
        assertEquals(LocalDate.of(2025, 3, 1), nonLeapRes.nextReviewDate)

        // 跨年：12月31日 + 1天 -> 次年 1月1日
        val dec31 = LocalDate.of(2026, 12, 31)
        val yearEndRes = EbbinghausScheduler.initialSchedule(dec31)
        assertEquals(LocalDate.of(2027, 1, 1), yearEndRes.nextReviewDate)
    }
}
