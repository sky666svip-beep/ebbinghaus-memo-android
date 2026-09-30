package com.ebbinghaus.memo.data.export

import androidx.room.withTransaction
import com.ebbinghaus.memo.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 导入结果
 *
 * 说明：在架构给出的 `Success / CorruptedFile / VersionTooNew` 三态基础上，
 * 额外增加 [Failed]，用于区分「解析校验通过但落库失败」（PRD §5-10），
 * 使 UI 能给出准确提示而非误报「文件损坏」。
 */
sealed interface ImportResult {
    /** 导入成功 */
    data class Success(val memoCount: Int, val taskCount: Int) : ImportResult

    /** 解析失败 / 非本 App 文件 */
    data object CorruptedFile : ImportResult

    /** `schemaVersion` 高于当前支持版本 */
    data object VersionTooNew : ImportResult

    /** 落库失败（已整体回滚） */
    data class Failed(val reason: String) : ImportResult
}

/**
 * 数据搬运仓储（E01/E02）
 *
 * 统一承载导出快照与导入（解析校验 → 单事务全量覆盖）。
 * 依据 R10，导出/导入以单一 [Mutex] 串行化，避免读到半更新数据。
 */
class DataPortRepository(
    private val db: AppDatabase,
    private val codec: ExportCodec = ExportCodec(),
    private val appVersion: String = "1.0.0",
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {

    private val ioMutex = Mutex()

    /**
     * 导出全量快照（含软删条目，保证往返完整）
     */
    suspend fun exportSnapshot(): AppSnapshot = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val memos = db.knowledgeMemoDao().getAllMemosIncludingDeleted()
            val tasks = db.reviewTaskDao().getAllTasksSync()
            val settings = db.userSettingsDao().getAllSettings().firstOrNull()

            AppSnapshot(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                exportedAt = nowProvider(),
                appVersion = appVersion,
                memos = memos.map { it.toDto() },
                reviewTasks = tasks.map { it.toDto() },
                userSettings = settings?.toDto()
            )
        }
    }

    /**
     * 导出为 JSON 字符串（供 SAF 写入）
     */
    suspend fun exportJson(): String = codec.encode(exportSnapshot())

    /**
     * 仅解析校验（不落库），用于导入前的预览弹窗与错误分类。
     */
    fun decodeSnapshot(json: String): Result<AppSnapshot> = codec.decode(json)

    /**
     * 导入 JSON：**先全量解析校验、后落库**；落库在单事务内完成，任一步失败整体回滚。
     */
    suspend fun importSnapshot(json: String): ImportResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val decoded = codec.decode(json)
            val snapshot = decoded.getOrElse { error ->
                return@withLock when (error) {
                    is VersionTooNewException -> ImportResult.VersionTooNew
                    else -> ImportResult.CorruptedFile
                }
            }

            try {
                db.withTransaction {
                    val memoDao = db.knowledgeMemoDao()
                    val taskDao = db.reviewTaskDao()
                    val settingsDao = db.userSettingsDao()

                    // 全量覆盖：先清空三张表，再写入快照内容（单事务原子）
                    memoDao.deleteAllMemos()
                    taskDao.deleteAll()
                    settingsDao.deleteAll()

                    if (snapshot.memos.isNotEmpty()) {
                        memoDao.insertAll(snapshot.memos.map { it.toEntity() })
                    }
                    if (snapshot.reviewTasks.isNotEmpty()) {
                        taskDao.insertAll(snapshot.reviewTasks.map { it.toEntity() })
                    }
                    snapshot.userSettings?.let { settingsDao.insertAll(listOf(it.toEntity())) }
                }
                ImportResult.Success(
                    memoCount = snapshot.memos.size,
                    taskCount = snapshot.reviewTasks.size
                )
            } catch (e: Exception) {
                ImportResult.Failed(e.message ?: "导入写入失败")
            }
        }
    }
}
