package com.ebbinghaus.memo.data.export

import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 全量数据快照（E01/E02 的传输根对象）
 *
 * 与存储格式解耦：`LocalDate` → ISO-8601 字符串；时间戳 → epoch 毫秒；`tags` → JSON 数组。
 *
 * @property schemaVersion 格式版本；当前为 1，`>1` 一律拒绝导入
 * @property exportedAt 导出时刻（epoch 毫秒，仅信息用途）
 * @property appVersion 应用版本名（仅信息用途）
 * @property memos 全量知识点（含软删条目，保证往返完整）
 * @property reviewTasks 全量复习任务
 * @property userSettings 用户配置（空库时为 null）
 */
data class AppSnapshot(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val memos: List<MemoDto>,
    val reviewTasks: List<ReviewTaskDto>,
    val userSettings: SettingsDto?
)

/**
 * 知识点传输对象
 */
data class MemoDto(
    val id: Long,
    val content: String,
    val notes: String,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

/**
 * 复习任务传输对象
 *
 * @property dueDate 到期日（ISO-8601 yyyy-MM-dd）
 * @property lastReviewDate 上次复习日（ISO-8601 或 null）
 */
data class ReviewTaskDto(
    val id: Long,
    val memoId: Long,
    val stageLevel: Int,
    val dueDate: String,
    val lastReviewDate: String?,
    val reviewCount: Int,
    val updatedAt: Long
)

/**
 * 用户配置传输对象
 */
data class SettingsDto(
    val id: Int,
    val dailyReviewLimit: Int,
    val lastActiveDate: String?,
    val lastPromptedDate: String?
)

/** ISO-8601 自然日格式器 */
private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** 自然日 → ISO-8601 字符串 */
fun LocalDate?.toIsoString(): String? = this?.format(ISO_DATE)

/** ISO-8601 字符串 → 自然日（非法/空返回 null） */
fun String?.toLocalDateOrNull(): LocalDate? {
    if (this.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(this, ISO_DATE) }.getOrNull()
}

// ---------------------------------------------------------------------------
// 实体 ↔ DTO 映射
// ---------------------------------------------------------------------------

fun KnowledgeMemoEntity.toDto(): MemoDto = MemoDto(
    id = id,
    content = content,
    notes = notes,
    tags = tags,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun MemoDto.toEntity(): KnowledgeMemoEntity = KnowledgeMemoEntity(
    id = id,
    content = content,
    notes = notes,
    tags = tags,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun ReviewTaskEntity.toDto(): ReviewTaskDto = ReviewTaskDto(
    id = id,
    memoId = memoId,
    stageLevel = stageLevel,
    dueDate = dueDate.format(ISO_DATE),
    lastReviewDate = lastReviewDate?.format(ISO_DATE),
    reviewCount = reviewCount,
    updatedAt = updatedAt
)

fun ReviewTaskDto.toEntity(): ReviewTaskEntity = ReviewTaskEntity(
    id = id,
    memoId = memoId,
    stageLevel = stageLevel,
    dueDate = requireNotNull(dueDate.toLocalDateOrNull()) { "非法到期日：$dueDate" },
    lastReviewDate = lastReviewDate.toLocalDateOrNull(),
    reviewCount = reviewCount,
    updatedAt = updatedAt
)

fun UserSettingsEntity.toDto(): SettingsDto = SettingsDto(
    id = id,
    dailyReviewLimit = dailyReviewLimit,
    lastActiveDate = lastActiveDate.toIsoString(),
    lastPromptedDate = lastPromptedDate.toIsoString()
)

fun SettingsDto.toEntity(): UserSettingsEntity = UserSettingsEntity(
    id = id,
    dailyReviewLimit = dailyReviewLimit,
    lastActiveDate = lastActiveDate.toLocalDateOrNull(),
    lastPromptedDate = lastPromptedDate.toLocalDateOrNull()
)
