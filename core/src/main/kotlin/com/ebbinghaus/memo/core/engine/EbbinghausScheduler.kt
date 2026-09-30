package com.ebbinghaus.memo.core.engine

import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.core.model.ReviewScheduleResult
import com.ebbinghaus.memo.core.model.ReviewStage
import java.time.LocalDate

/**
 * 艾宾浩斯遗忘曲线纯函数调度算法核心
 *
 * 遵循艾宾浩斯记忆规律与用户交互评级状态转移：
 * 1. 记住：前进1档；第6档再次记住进入60天长周期；长周期再次记住保持60天长周期。
 * 2. 模糊：保持当前档位与对应间隔天数不变。
 * 3. 忘记：回退1档；第1档保持第1档；第7档（长周期）回退至第6档（30天）。
 * 4. 已复习（缺省）：默认等同于“模糊”处理。
 * 5. 跳过：不改变档位，该任务固定顺延 1 天。
 * 6. Day 0 录入：首个复习日为录入日+1天（即 Day 1）。
 */
object EbbinghausScheduler {

    /**
     * 计算单次复习后的新档位与下次到期复习自然日
     *
     * @param currentStage 当前复习档位
     * @param rating 用户评级动作
     * @param reviewDate 执行复习动作的自然日
     * @return 调度计算结果 ReviewScheduleResult
     */
    fun calculateNextReview(
        currentStage: ReviewStage,
        rating: ReviewRating,
        reviewDate: LocalDate
    ): ReviewScheduleResult {
        return when (rating) {
            ReviewRating.FORGET -> {
                val nextStage = when (currentStage) {
                    ReviewStage.STAGE_1 -> ReviewStage.STAGE_1
                    ReviewStage.STAGE_2 -> ReviewStage.STAGE_1
                    ReviewStage.STAGE_3 -> ReviewStage.STAGE_2
                    ReviewStage.STAGE_4 -> ReviewStage.STAGE_3
                    ReviewStage.STAGE_5 -> ReviewStage.STAGE_4
                    ReviewStage.STAGE_6 -> ReviewStage.STAGE_5
                    ReviewStage.LONG_TERM_60 -> ReviewStage.STAGE_6
                }
                ReviewScheduleResult(
                    nextStage = nextStage,
                    nextReviewDate = reviewDate.plusDays(nextStage.intervalDays.toLong()),
                    intervalDays = nextStage.intervalDays
                )
            }

            ReviewRating.VAGUE,
            ReviewRating.DEFAULT_REVIEWED -> {
                // 模糊及缺省“已复习”：保持当前档位不变
                ReviewScheduleResult(
                    nextStage = currentStage,
                    nextReviewDate = reviewDate.plusDays(currentStage.intervalDays.toLong()),
                    intervalDays = currentStage.intervalDays
                )
            }

            ReviewRating.REMEMBER -> {
                val nextStage = when (currentStage) {
                    ReviewStage.STAGE_1 -> ReviewStage.STAGE_2
                    ReviewStage.STAGE_2 -> ReviewStage.STAGE_3
                    ReviewStage.STAGE_3 -> ReviewStage.STAGE_4
                    ReviewStage.STAGE_4 -> ReviewStage.STAGE_5
                    ReviewStage.STAGE_5 -> ReviewStage.STAGE_6
                    ReviewStage.STAGE_6 -> ReviewStage.LONG_TERM_60
                    ReviewStage.LONG_TERM_60 -> ReviewStage.LONG_TERM_60
                }
                ReviewScheduleResult(
                    nextStage = nextStage,
                    nextReviewDate = reviewDate.plusDays(nextStage.intervalDays.toLong()),
                    intervalDays = nextStage.intervalDays
                )
            }

            ReviewRating.SKIP -> {
                // 跳过：保持档位不变，自动顺延 1 天
                ReviewScheduleResult(
                    nextStage = currentStage,
                    nextReviewDate = reviewDate.plusDays(1),
                    intervalDays = 1
                )
            }
        }
    }

    /**
     * 新增知识点时的初始复习调度规则（以录入日为 Day 0，初始复习日在 Day 1）
     *
     * @param createdDate 知识点录入自然日
     */
    fun initialSchedule(createdDate: LocalDate): ReviewScheduleResult {
        return ReviewScheduleResult(
            nextStage = ReviewStage.STAGE_1,
            nextReviewDate = createdDate.plusDays(1),
            intervalDays = 1
        )
    }
}
