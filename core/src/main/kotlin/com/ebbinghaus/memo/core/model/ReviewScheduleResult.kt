package com.ebbinghaus.memo.core.model

import java.time.LocalDate

/**
 * 艾宾浩斯单次复习计算结果
 *
 * @property nextStage 计算后的新档位
 * @property nextReviewDate 下次到期复习自然日
 * @property intervalDays 距本次复习动作的间隔天数
 */
data class ReviewScheduleResult(
    val nextStage: ReviewStage,
    val nextReviewDate: LocalDate,
    val intervalDays: Int
)
