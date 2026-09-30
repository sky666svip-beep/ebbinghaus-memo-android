package com.ebbinghaus.memo.core.engine

import com.ebbinghaus.memo.core.model.RolloverPlan
import java.time.LocalDate

/**
 * 每日限额与超量顺延引擎
 *
 * 核心调度规范：
 * 1. 每日上限配置（0~50 条）：上限为 0 时今日待复习量为 0，不提醒不推送。
 * 2. 候选池合并（多天未登录）：所有 targetDueDate <= today 的任务自动合并入候选池，不标记逾期，不惩罚降档。
 * 3. 排序策略：严格遵循「到期时间最早优先」（dueDate ASC），同到期日按 taskId ASC 排序。
 * 4. 截断与顺延：今日批次截取前 dailyLimit 条，超出部分作为超量顺延任务留待次日及以后。
 */
object RolloverEngine {

    /**
     * 对候选任务集执行每日配额划分与超量顺延计算
     *
     * @param pendingTasks 所有待复习任务列表（含积压任务与未到期任务）
     * @param today 当前判定自然日
     * @param dailyLimit 每日复习上限（0~50）
     * @return 划分完成的 RolloverPlan
     */
    fun <T : SchedulableTask> planReviewBatch(
        pendingTasks: List<T>,
        today: LocalDate,
        dailyLimit: Int
    ): RolloverPlan<T> {
        // 1. 过滤出历史积压及今日到期的任务 (dueDate <= today)
        val dueTasks = pendingTasks.filter { !it.targetDueDate.isAfter(today) }

        // 2. 每日上限为 0 或负数：今日批次清空，全量归入待延期
        if (dailyLimit <= 0) {
            return RolloverPlan(
                todayBatch = emptyList(),
                deferredTasks = dueTasks,
                totalPendingCount = dueTasks.size,
                isLimitReached = dueTasks.isNotEmpty()
            )
        }

        // 3. 严格遵循「到期时间最早优先」（Earliest Due Date First）排序
        val sortedTasks = dueTasks.sortedWith(
            compareBy<T> { it.targetDueDate }
                .thenBy { it.taskId }
        )

        // 4. 按上限截取今日批次与超量顺延批次
        val todayBatch = sortedTasks.take(dailyLimit)
        val deferredTasks = sortedTasks.drop(dailyLimit)

        return RolloverPlan(
            todayBatch = todayBatch,
            deferredTasks = deferredTasks,
            totalPendingCount = dueTasks.size,
            isLimitReached = deferredTasks.isNotEmpty()
        )
    }
}
