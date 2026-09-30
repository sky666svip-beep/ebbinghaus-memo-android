package com.ebbinghaus.memo.data.export

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** 当前支持的导出格式版本 */
const val CURRENT_SCHEMA_VERSION: Int = 1

/**
 * 文件已损坏或不是本 App 的导出文件
 */
class CorruptedExportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 文件来自更新版本（`schemaVersion > CURRENT_SCHEMA_VERSION`）
 */
class VersionTooNewException(val version: Int) : Exception("文件 schemaVersion=$version 高于当前支持版本 $CURRENT_SCHEMA_VERSION")

/**
 * 全量数据 JSON 编解码器（D8/D9）
 *
 * 运行时由 Android framework 提供 `org.json`（APK 零字节）；测试注入真实 `org.json` 实现，
 * 使编解码可在纯 JVM 中往返验证。导出与导入**共用同一 codec**，杜绝二次实现漂移。
 */
class ExportCodec {

    /**
     * 将快照编码为 JSON 字符串
     */
    fun encode(snapshot: AppSnapshot): String {
        val root = JSONObject()
        root.put("schemaVersion", snapshot.schemaVersion)
        root.put("exportedAt", snapshot.exportedAt)
        root.put("appVersion", snapshot.appVersion)

        val memosArray = JSONArray()
        snapshot.memos.forEach { memo ->
            val obj = JSONObject()
            obj.put("id", memo.id)
            obj.put("content", memo.content)
            obj.put("notes", memo.notes)
            obj.put("tags", JSONArray(memo.tags))
            obj.put("createdAt", memo.createdAt)
            obj.put("updatedAt", memo.updatedAt)
            obj.put("deletedAt", memo.deletedAt ?: JSONObject.NULL)
            memosArray.put(obj)
        }
        root.put("memos", memosArray)

        val tasksArray = JSONArray()
        snapshot.reviewTasks.forEach { task ->
            val obj = JSONObject()
            obj.put("id", task.id)
            obj.put("memoId", task.memoId)
            obj.put("stageLevel", task.stageLevel)
            obj.put("dueDate", task.dueDate)
            obj.put("lastReviewDate", task.lastReviewDate ?: JSONObject.NULL)
            obj.put("reviewCount", task.reviewCount)
            obj.put("updatedAt", task.updatedAt)
            tasksArray.put(obj)
        }
        root.put("reviewTasks", tasksArray)

        val settings = snapshot.userSettings
        if (settings == null) {
            root.put("userSettings", JSONObject.NULL)
        } else {
            val obj = JSONObject()
            obj.put("id", settings.id)
            obj.put("dailyReviewLimit", settings.dailyReviewLimit)
            obj.put("lastActiveDate", settings.lastActiveDate ?: JSONObject.NULL)
            obj.put("lastPromptedDate", settings.lastPromptedDate ?: JSONObject.NULL)
            root.put("userSettings", obj)
        }

        return root.toString()
    }

    /**
     * 将 JSON 字符串解码为快照。
     *
     * 约定：**先全量解析校验、后落库**。解析失败返回 [CorruptedExportException]；
     * `schemaVersion` 高于当前支持版本返回 [VersionTooNewException]（不尝试部分解析）。
     */
    fun decode(json: String): Result<AppSnapshot> {
        return try {
            val root = JSONObject(json)

            val version = root.optInt("schemaVersion", -1)
            if (version < 0) {
                return Result.failure(CorruptedExportException("缺少 schemaVersion 字段"))
            }
            if (version > CURRENT_SCHEMA_VERSION) {
                return Result.failure(VersionTooNewException(version))
            }

            val exportedAt = root.optLong("exportedAt", 0L)
            val appVersion = root.optString("appVersion", "")

            val memos = parseMemos(root.getJSONArray("memos"))
            val tasks = parseTasks(root.getJSONArray("reviewTasks"))
            val settings = parseSettings(root)

            Result.success(
                AppSnapshot(
                    schemaVersion = version,
                    exportedAt = exportedAt,
                    appVersion = appVersion,
                    memos = memos,
                    reviewTasks = tasks,
                    userSettings = settings
                )
            )
        } catch (e: JSONException) {
            Result.failure(CorruptedExportException("JSON 解析失败：${e.message}", e))
        } catch (e: IllegalArgumentException) {
            Result.failure(CorruptedExportException("字段非法：${e.message}", e))
        } catch (e: NullPointerException) {
            Result.failure(CorruptedExportException("字段缺失：${e.message}", e))
        }
    }

    private fun parseMemos(array: JSONArray): List<MemoDto> {
        val result = ArrayList<MemoDto>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
            val tags = ArrayList<String>(tagsArray.length())
            for (j in 0 until tagsArray.length()) {
                tags.add(tagsArray.getString(j))
            }
            result.add(
                MemoDto(
                    id = obj.getLong("id"),
                    content = obj.getString("content"),
                    notes = obj.optString("notes", ""),
                    tags = tags,
                    createdAt = obj.optLong("createdAt", 0L),
                    updatedAt = obj.optLong("updatedAt", 0L),
                    deletedAt = if (obj.isNull("deletedAt")) null else obj.getLong("deletedAt")
                )
            )
        }
        return result
    }

    private fun parseTasks(array: JSONArray): List<ReviewTaskDto> {
        val result = ArrayList<ReviewTaskDto>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                ReviewTaskDto(
                    id = obj.getLong("id"),
                    memoId = obj.getLong("memoId"),
                    stageLevel = obj.optInt("stageLevel", 1),
                    dueDate = obj.getString("dueDate"),
                    lastReviewDate = if (obj.isNull("lastReviewDate")) null else obj.getString("lastReviewDate"),
                    reviewCount = obj.optInt("reviewCount", 0),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            )
        }
        return result
    }

    private fun parseSettings(root: JSONObject): SettingsDto? {
        if (root.isNull("userSettings")) return null
        val obj = root.optJSONObject("userSettings") ?: return null
        return SettingsDto(
            id = obj.optInt("id", 1),
            dailyReviewLimit = obj.optInt("dailyReviewLimit", 20),
            lastActiveDate = if (obj.isNull("lastActiveDate")) null else obj.optString("lastActiveDate"),
            lastPromptedDate = if (obj.isNull("lastPromptedDate")) null else obj.optString("lastPromptedDate")
        )
    }
}
