package com.ebbinghaus.memo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 用户配置偏好数据访问接口
 */
@Dao
interface UserSettingsDao {

    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getSettingsSync(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: UserSettingsEntity)

    @Query("UPDATE user_settings SET dailyReviewLimit = :newLimit WHERE id = 1")
    suspend fun updateDailyLimit(newLimit: Int)

    @Query("UPDATE user_settings SET lastPromptedDate = :promptedDate WHERE id = 1")
    suspend fun updateLastPromptedDate(promptedDate: LocalDate)

    @Query("UPDATE user_settings SET lastActiveDate = :activeDate WHERE id = 1")
    suspend fun updateLastActiveDate(activeDate: LocalDate)

    // ------------------------------------------------------------------
    // 导出 / 导入（E01）
    // ------------------------------------------------------------------

    @Query("SELECT * FROM user_settings")
    suspend fun getAllSettings(): List<UserSettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<UserSettingsEntity>)

    @Query("DELETE FROM user_settings")
    suspend fun deleteAll()
}
