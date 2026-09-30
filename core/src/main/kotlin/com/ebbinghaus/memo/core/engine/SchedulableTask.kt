package com.ebbinghaus.memo.core.engine

import java.time.LocalDate

/**
 * 待排期复习任务契约接口，解耦具体数据持久层实体与纯领域调度引擎
 */
interface SchedulableTask {
    val taskId: Long
    val targetDueDate: LocalDate
}
