package com.ebbinghaus.memo.core.model

/**
 * 每日限额顺延调度计划
 *
 * @param T 可排期任务类型
 * @property todayBatch 今日分配执行的复习批次（最多 dailyLimit 条）
 * @property deferredTasks 超量顺延至次日及以后的任务
 * @property totalPendingCount 候选池到期待复习总数（包含今日与顺延部分）
 * @property isLimitReached 是否触发了每日上限截断
 */
data class RolloverPlan<T>(
    val todayBatch: List<T>,
    val deferredTasks: List<T>,
    val totalPendingCount: Int,
    val isLimitReached: Boolean
)
