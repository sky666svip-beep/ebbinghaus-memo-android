package com.ebbinghaus.memo.data.repository

import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 艾宾浩斯复习调度仓储接口
 */
interface ReviewRepository {

    /**
     * 获取到期与积压待复习知识点数据流 (dueDate <= today)
     */
    fun getDueReviewTasks(today: LocalDate): Flow<List<MemoWithReviewTask>>

    /**
     * 根据知识点 ID 查询对应的复习任务
     */
    suspend fun getTaskByMemoId(memoId: Long): ReviewTaskEntity?

    /**
     * 导出用：读取全量复习任务（同步一次性读取）
     */
    suspend fun getAllTasksSync(): List<ReviewTaskEntity>

    /**
     * 提交复习评级，驱动艾宾浩斯状态机流转并持久化下次复习日期
     *
     * @param taskId 复习任务唯一标识
     * @param rating 用户选择的评级操作 (FORGET, VAGUE, REMEMBER, DEFAULT_REVIEWED, SKIP)
     * @param reviewDate 执行复习动作的自然日
     */
    suspend fun submitReviewRating(taskId: Long, rating: ReviewRating, reviewDate: LocalDate)
}

