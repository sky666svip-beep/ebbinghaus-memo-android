package com.ebbinghaus.memo.data.local.entity

import androidx.room.Embedded
import java.time.LocalDate

/**
 * 回收站条目 + 复习进度只读投影（P2-2）
 *
 * 复用**同一条 LEFT JOIN 查询**同时服务「就地预览」与「搜索 / 排序」：
 * - [memo] 为软删知识点本体（`deletedAt` 非空）；
 * - [stageLevel] / [dueDate] / [reviewCount] 来自 `review_tasks`，**可空**（无任务行时为 null，防御式）；
 * - [dueDate] 依赖 `Converters` 的 `LocalDate?` 双向转换（epochDay）。
 *
 * 与 `MemoWithReviewTask` 的差异：后者的 `@Relation reviewTask` 非空，遇无任务行会崩，
 * 故回收站**不复用**该聚合模型（架构 D5）。
 */
data class TrashedMemoWithProgress(
    @Embedded
    val memo: KnowledgeMemoEntity,
    /** 当前复习档位（review_tasks.stageLevel），无任务行为 null */
    val stageLevel: Int?,
    /** 下次复习到期自然日（review_tasks.dueDate），无任务行为 null */
    val dueDate: LocalDate?,
    /** 累计已复习次数（review_tasks.reviewCount），无任务行为 null */
    val reviewCount: Int?
)
