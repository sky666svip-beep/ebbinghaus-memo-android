package com.ebbinghaus.memo.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.ebbinghaus.memo.core.engine.SchedulableTask
import java.time.LocalDate

/**
 * 知识点与复习任务关联聚合模型
 *
 * 实现了 SchedulableTask 接口，可直接传入 RolloverEngine 执行每日配额与超量顺延计算。
 */
data class MemoWithReviewTask(
    @Embedded
    val memo: KnowledgeMemoEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "memoId"
    )
    val reviewTask: ReviewTaskEntity
) : SchedulableTask {
    override val taskId: Long get() = reviewTask.id
    override val targetDueDate: LocalDate get() = reviewTask.dueDate
}
