package com.ebbinghaus.memo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 艾宾浩斯复习任务数据访问接口
 *
 * 复习口径同源（D4）：`getDueMemosWithTasks` 增加 `knowledge_memos.deletedAt IS NULL`，
 * 使复习页 / 看板 / 底部 Badge 三处消费方共享同一过滤口径。
 */
@Dao
interface ReviewTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ReviewTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<ReviewTaskEntity>)

    @Update
    suspend fun update(task: ReviewTaskEntity)

    @Query("SELECT * FROM review_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): ReviewTaskEntity?

    @Query("SELECT * FROM review_tasks WHERE memoId = :memoId")
    suspend fun getTaskByMemoId(memoId: Long): ReviewTaskEntity?

    /**
     * 到期复习任务（唯一查询入口，三处消费方共用）。
     *
     * 软删除的知识点从复习队列 / 今日待复习计数 / Badge 一并消失。
     */
    @Transaction
    @Query(
        """
        SELECT knowledge_memos.* FROM knowledge_memos 
        INNER JOIN review_tasks ON knowledge_memos.id = review_tasks.memoId 
        WHERE review_tasks.dueDate <= :targetDate 
          AND knowledge_memos.deletedAt IS NULL
        ORDER BY review_tasks.dueDate ASC, review_tasks.id ASC
        """
    )
    fun getDueMemosWithTasks(targetDate: LocalDate): Flow<List<MemoWithReviewTask>>

    @Transaction
    @Query(
        """
        SELECT knowledge_memos.* FROM knowledge_memos 
        INNER JOIN review_tasks ON knowledge_memos.id = review_tasks.memoId 
        WHERE review_tasks.dueDate <= :targetDate 
          AND knowledge_memos.deletedAt IS NULL
        ORDER BY review_tasks.dueDate ASC, review_tasks.id ASC
        """
    )
    suspend fun getDueMemosWithTasksSync(targetDate: LocalDate): List<MemoWithReviewTask>

    @Transaction
    @Query(
        """
        SELECT knowledge_memos.* FROM knowledge_memos 
        INNER JOIN review_tasks ON knowledge_memos.id = review_tasks.memoId 
        WHERE knowledge_memos.deletedAt IS NULL
        ORDER BY review_tasks.dueDate ASC
        """
    )
    fun getAllMemosWithTasks(): Flow<List<MemoWithReviewTask>>

    @Query("DELETE FROM review_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ------------------------------------------------------------------
    // 导出 / 导入（E01）
    // ------------------------------------------------------------------

    @Query("SELECT * FROM review_tasks")
    suspend fun getAllTasksSync(): List<ReviewTaskEntity>

    @Query("DELETE FROM review_tasks")
    suspend fun deleteAll()
}
