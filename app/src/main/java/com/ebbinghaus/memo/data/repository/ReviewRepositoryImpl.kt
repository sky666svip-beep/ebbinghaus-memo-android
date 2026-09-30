package com.ebbinghaus.memo.data.repository

import com.ebbinghaus.memo.core.engine.EbbinghausScheduler
import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.core.model.ReviewStage
import com.ebbinghaus.memo.data.local.dao.ReviewTaskDao
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 艾宾浩斯复习调度仓储实现类
 */
class ReviewRepositoryImpl(
    private val reviewTaskDao: ReviewTaskDao
) : ReviewRepository {

    override fun getDueReviewTasks(today: LocalDate): Flow<List<MemoWithReviewTask>> {
        return reviewTaskDao.getDueMemosWithTasks(today)
    }

    override suspend fun getTaskByMemoId(memoId: Long): ReviewTaskEntity? {
        return reviewTaskDao.getTaskByMemoId(memoId)
    }

    override suspend fun getAllTasksSync(): List<ReviewTaskEntity> {
        return reviewTaskDao.getAllTasksSync()
    }

    override suspend fun submitReviewRating(
        taskId: Long,
        rating: ReviewRating,
        reviewDate: LocalDate
    ) {
        val task = reviewTaskDao.getTaskById(taskId) ?: return
        val currentStage = ReviewStage.fromLevel(task.stageLevel)
        val scheduleResult = EbbinghausScheduler.calculateNextReview(
            currentStage = currentStage,
            rating = rating,
            reviewDate = reviewDate
        )

        // 跳过不计入 reviewCount 累计，其他评级增加复习计数
        val isSkip = rating == ReviewRating.SKIP
        val updatedTask = task.copy(
            stageLevel = scheduleResult.nextStage.level,
            dueDate = scheduleResult.nextReviewDate,
            lastReviewDate = if (isSkip) task.lastReviewDate else reviewDate,
            reviewCount = if (isSkip) task.reviewCount else task.reviewCount + 1,
            updatedAt = System.currentTimeMillis()
        )
        reviewTaskDao.update(updatedTask)
    }
}
