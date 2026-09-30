package com.ebbinghaus.memo.data.repository

import com.ebbinghaus.memo.data.local.dao.UserSettingsDao
import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * 用户配置偏好仓储实现类
 */
class SettingsRepositoryImpl(
    private val settingsDao: UserSettingsDao
) : SettingsRepository {

    override fun getSettings(): Flow<UserSettingsEntity> {
        return settingsDao.getSettingsFlow().map { entity ->
            entity ?: UserSettingsEntity()
        }
    }

    override suspend fun updateDailyLimit(newLimit: Int) {
        // 边界保护：严格钳制在 0~50 范围内
        val clampedLimit = newLimit.coerceIn(0, 50)
        val current = settingsDao.getSettingsSync()
        if (current == null) {
            settingsDao.saveSettings(UserSettingsEntity(id = 1, dailyReviewLimit = clampedLimit))
        } else {
            settingsDao.updateDailyLimit(clampedLimit)
        }
    }

    override suspend fun markDashboardPrompted(promptDate: LocalDate) {
        val current = settingsDao.getSettingsSync()
        if (current == null) {
            settingsDao.saveSettings(
                UserSettingsEntity(
                    id = 1,
                    lastPromptedDate = promptDate,
                    lastActiveDate = promptDate
                )
            )
        } else {
            settingsDao.updateLastPromptedDate(promptDate)
        }
    }

    override suspend fun updateLastActiveDate(activeDate: LocalDate) {
        val current = settingsDao.getSettingsSync()
        if (current == null) {
            settingsDao.saveSettings(
                UserSettingsEntity(
                    id = 1,
                    lastActiveDate = activeDate
                )
            )
        } else {
            settingsDao.updateLastActiveDate(activeDate)
        }
    }
}
