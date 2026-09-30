package com.ebbinghaus.memo.data.local.converter

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room 数据库类型转换器
 *
 * 负责 List<String> 与 LocalDate (toEpochDay) 的序列化与反序列化，零第三方库依赖。
 */
class Converters {

    companion object {
        // 使用专用的 ASCII 单元分隔符 Unit Separator (U+001F)，彻底杜绝常规字符、章节符号(§)及标点碰撞
        private const val DELIMITER = "\u001F"
        // 历史旧数据分隔符，用于平滑向后兼容
        private const val LEGACY_DELIMITER = "§"
    }

    @TypeConverter
    fun fromTagList(tags: List<String>?): String {
        val validTags = tags?.filter { it.isNotBlank() }
            ?.map { it.replace(DELIMITER, "").trim() }
            ?.filter { it.isNotEmpty() } ?: return ""
        if (validTags.isEmpty()) return ""
        // 前缀保留 DELIMITER，确保即使单标签内含旧版分隔符(如 "Chapter §1")，反序列化时也能精确识别为新格式
        return DELIMITER + validTags.joinToString(DELIMITER)
    }

    @TypeConverter
    fun toTagList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        // 优先使用新版不可见控制字符分隔符；若不包含新分隔符但包含旧版分隔符，自动 fallback 兼容旧数据
        val delimiter = if (data.contains(DELIMITER)) DELIMITER else LEGACY_DELIMITER
        return data.split(delimiter).map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? {
        return epochDay?.let { LocalDate.ofEpochDay(it) }
    }
}
