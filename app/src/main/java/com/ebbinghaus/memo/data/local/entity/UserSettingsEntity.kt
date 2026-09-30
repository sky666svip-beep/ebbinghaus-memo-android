package com.ebbinghaus.memo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 用户配置偏好实体（单行配置记录，id 固定为 1）
 *
 * @property id 主键，默认固定为 1
 * @property dailyReviewLimit 每日复习上限（0~50条，默认 20 条）
 * @property lastActiveDate 上次应用打开活跃自然日（用于多天未登录检测）
 * @property lastPromptedDate 上次弹出应用内看板提示的自然日
 */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val dailyReviewLimit: Int = 20,
    val lastActiveDate: LocalDate? = null,
    val lastPromptedDate: LocalDate? = null
)
