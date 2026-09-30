package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.core.engine.EbbinghausScheduler
import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.core.model.ReviewStage
import com.ebbinghaus.memo.ui.review.RATING_SEMANTICS
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复习评级无障碍语义文案与调度算法一致性单元测试（P1-1）
 *
 * 评级文案会作为 `contentDescription` 下发给 TalkBack 用户，必须与
 * [EbbinghausScheduler.calculateNextReview] 的真实档位流转一致，否则将系统性误导视障用户。
 *
 * 断言一律以算法「实际输出」逐档推导，而非照抄文案；对全部 [ReviewStage] 遍历。
 */
class RatingSemanticsConsistencyTest {

    private val today: LocalDate = LocalDate.of(2026, 1, 1)

    @Test
    fun semanticsMap_coversExactlyAllFiveRatings() {
        assertEquals("文案条目数应等于评级枚举数", ReviewRating.entries.size, RATING_SEMANTICS.size)
        ReviewRating.entries.forEach { rating ->
            assertTrue("评级 $rating 缺少文案", !RATING_SEMANTICS[rating].isNullOrBlank())
        }
    }

    @Test
    fun forgetSemantics_rollsBackOneStage_forEveryStage() {
        // 文案关键信息：回退一档；且不得再声称「回到第 1 档」
        val text = RATING_SEMANTICS.getValue(ReviewRating.FORGET)
        assertTrue("FORGET 文案应含「回退」", text.contains("回退"))
        assertFalse("FORGET 文案不得声称回到第 1 档", text.contains("第 1 档"))

        // 逐档用算法实际输出核对「回退一档」这一表述
        ReviewStage.entries.forEach { stage ->
            val actual = EbbinghausScheduler.calculateNextReview(stage, ReviewRating.FORGET, today).nextStage
            val expected = when (stage) {
                ReviewStage.STAGE_1 -> ReviewStage.STAGE_1          // 已是第 1 档则保持
                ReviewStage.LONG_TERM_60 -> ReviewStage.STAGE_6     // 长周期退回第 6 档
                else -> ReviewStage.fromLevel(stage.level - 1)
            }
            assertEquals("FORGET@$stage 应回退一档", expected, actual)
        }
    }

    @Test
    fun rememberSemantics_advancesOneStage_andEnters60DayCycleAfterStage6() {
        val text = RATING_SEMANTICS.getValue(ReviewRating.REMEMBER)
        assertTrue("REMEMBER 文案应含「前进一档」", text.contains("前进一档"))
        assertTrue("REMEMBER 文案应说明 60 天长周期", text.contains("60 天"))

        ReviewStage.entries.forEach { stage ->
            val result = EbbinghausScheduler.calculateNextReview(stage, ReviewRating.REMEMBER, today)
            val expected = when (stage) {
                ReviewStage.LONG_TERM_60 -> ReviewStage.LONG_TERM_60
                ReviewStage.STAGE_6 -> ReviewStage.LONG_TERM_60
                else -> ReviewStage.fromLevel(stage.level + 1)
            }
            assertEquals("REMEMBER@$stage 应前进一档", expected, result.nextStage)
            assertEquals("REMEMBER@$stage 间隔应等于目标档间隔", expected.intervalDays, result.intervalDays)
        }
        // 第 6 档记住 → 确实进入 60 天长周期
        assertEquals(
            ReviewStage.LONG_TERM_60,
            EbbinghausScheduler.calculateNextReview(ReviewStage.STAGE_6, ReviewRating.REMEMBER, today).nextStage
        )
    }

    @Test
    fun vagueAndDefaultSemantics_keepStageAndInterval_forEveryStage() {
        assertTrue(RATING_SEMANTICS.getValue(ReviewRating.VAGUE).contains("保持当前档位"))
        assertTrue(RATING_SEMANTICS.getValue(ReviewRating.DEFAULT_REVIEWED).contains("保持当前档位"))

        ReviewStage.entries.forEach { stage ->
            listOf(ReviewRating.VAGUE, ReviewRating.DEFAULT_REVIEWED).forEach { rating ->
                val result = EbbinghausScheduler.calculateNextReview(stage, rating, today)
                assertEquals("$rating@$stage 应保持档位", stage, result.nextStage)
                assertEquals("$rating@$stage 应保持间隔", stage.intervalDays, result.intervalDays)
                assertEquals(
                    "$rating@$stage 下次复习日应为 today+interval",
                    today.plusDays(stage.intervalDays.toLong()),
                    result.nextReviewDate
                )
            }
        }
    }

    @Test
    fun skipSemantics_defersExactlyOneDayWithoutStageChange() {
        assertTrue(RATING_SEMANTICS.getValue(ReviewRating.SKIP).contains("顺延"))

        ReviewStage.entries.forEach { stage ->
            val result = EbbinghausScheduler.calculateNextReview(stage, ReviewRating.SKIP, today)
            assertEquals("SKIP@$stage 应保持档位", stage, result.nextStage)
            assertEquals("SKIP@$stage 应固定顺延 1 天", today.plusDays(1), result.nextReviewDate)
        }
    }
}
