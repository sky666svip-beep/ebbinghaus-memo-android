package com.ebbinghaus.memo.core.model

/**
 * 艾宾浩斯复习档位
 * 标准 1~6 档复习间隔依次为：第1档 1天、第2档 2天、第3档 4天、第4档 7天、第5档 15天、第6档 30天；
 * 第 7 档为长周期，每 60 天复习一次。
 */
enum class ReviewStage(val level: Int, val intervalDays: Int) {
    STAGE_1(1, 1),
    STAGE_2(2, 2),
    STAGE_3(3, 4),
    STAGE_4(4, 7),
    STAGE_5(5, 15),
    STAGE_6(6, 30),
    LONG_TERM_60(7, 60);

    companion object {
        fun fromLevel(level: Int): ReviewStage =
            entries.find { it.level == level } ?: STAGE_1
    }
}
