package com.ebbinghaus.memo.data.repository

import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 用户配置偏好仓储接口
 */
interface SettingsRepository {

    /**
     * 获取用户偏好配置响应式流
     */
    fun getSettings(): Flow<UserSettingsEntity>

    /**
     * 更新每日复习上限（区间严格限制在 0~50 条）
     */
    suspend fun updateDailyLimit(newLimit: Int)

    /**
     * 标记今日已完成看板弹窗提示
     */
    suspend fun markDashboardPrompted(promptDate: LocalDate)

    /**
     * 更新最后活跃自然日
     */
    suspend fun updateLastActiveDate(activeDate: LocalDate)
}
