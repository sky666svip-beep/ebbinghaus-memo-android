package com.ebbinghaus.memo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 知识点艾宾浩斯复习任务实体
 *
 * 与 KnowledgeMemoEntity 建立外键关联，知识点删除时触发 SQLite 底层级联删除 (CASCADE)。
 *
 * @property id 任务唯一标识
 * @property memoId 关联的知识点唯一标识
 * @property stageLevel 当前复习档位等级 (1~7 对应 ReviewStage)
 * @property dueDate 下次复习到期自然日
 * @property lastReviewDate 上次执行复习的自然日
 * @property reviewCount 累计已复习次数
 * @property updatedAt 状态最后更新毫秒时间戳
 */
@Entity(
    tableName = "review_tasks",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeMemoEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["memoId"], unique = true),
        Index(value = ["dueDate"])
    ]
)
data class ReviewTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memoId: Long,
    val stageLevel: Int,
    val dueDate: LocalDate,
    val lastReviewDate: LocalDate? = null,
    val reviewCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
