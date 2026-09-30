package com.ebbinghaus.memo.data.snapshot

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.ebbinghaus.memo.data.export.DataPortRepository
import com.ebbinghaus.memo.data.preference.PreferenceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 启动期副作用诊断日志标签（冷启动崩溃排查用） */
private const val LOG_TAG = "EbbinghausLaunch"

/**
 * 快照执行结果
 */
sealed interface SnapshotResult {
    /** 未开启 / 未选目录 / 同日已快照 */
    data object Skipped : SnapshotResult

    /** 成功，携带落盘文件名 */
    data class Success(val fileName: String) : SnapshotResult

    /** 失败，携带失败原因（用于 UI 区分文案） */
    data class Failure(val reason: SnapshotFailureReason) : SnapshotResult
}

/**
 * 快照失败原因
 */
enum class SnapshotFailureReason {
    /** 未选择快照目录 */
    DIR_NOT_SET,

    /** 目录不可写 / 创建文档失败 */
    NOT_WRITABLE,

    /** SAF URI 授权失效 */
    PERMISSION_LOST,

    /** IO 异常 */
    IO_ERROR
}

/**
 * 自动本地快照管理器（E02）
 *
 * 复用 E01 的同一序列化层（[DataPortRepository.exportJson] → 同一 `ExportCodec`），不二次实现。
 * 写入用户经 SAF 授权的目录（`DocumentsContract`，零新依赖，Manifest 零新增权限）。
 * 文件名含日期；按文件名日期排序仅保留最近 N 份。
 */
class SnapshotManager(
    private val dataPortRepository: DataPortRepository,
    private val preferenceStore: PreferenceStore,
    private val contentResolver: ContentResolver,
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) {

    /**
     * 启动检查：未开启 / 未选目录 / 同日已快照 → 跳过；跨天且已开启 → 执行快照。
     *
     * **异常边界（防御性加固）**：本方法在冷启动首帧前被 `DashboardViewModel` 调用，
     * 任何未预期异常（SAF 授权失效、目录不可写、`ContentResolver` 抛
     * `SecurityException` / `IllegalArgumentException` 等）都必须就地收敛为
     * [SnapshotResult.Failure]，由调用方自动关闭快照开关并提示，绝不冒泡导致冷启动闪退。
     * `CancellationException` 原样抛出，保持结构化并发语义。
     */
    suspend fun checkAndSnapshotOnLaunch(): SnapshotResult = try {
        if (!preferenceStore.snapshotEnabled.value) {
            SnapshotResult.Skipped
        } else if (preferenceStore.snapshotDirUri.value.isNullOrBlank()) {
            SnapshotResult.Skipped
        } else {
            val today = todayProvider().format(ISO_DATE)
            if (preferenceStore.lastSnapshotDate.value == today) {
                SnapshotResult.Skipped
            } else {
                snapshotNow()
            }
        }
    } catch (cancellation: CancellationException) {
        // 取消信号必须原样抛出，不得吞掉
        throw cancellation
    } catch (e: Exception) {
        // 防御性加固（未确认根因）：仅记录日志并降级为 IO_ERROR，不阻断启动
        Log.e(LOG_TAG, "启动快照检查异常，已收敛为 IO_ERROR", e)
        SnapshotResult.Failure(SnapshotFailureReason.IO_ERROR)
    }

    /**
     * 立即快照一次（供设置页「立即快照一次」按钮使用）。
     *
     * **异常边界（防御性加固 · 第 2 轮补齐）**：`snapshotDirUri` 的读取已移入 `try` 之内
     * （原实现在 `try` 之前读取，该读取一旦抛异常将无人兜住，直接冒泡给调用方）。
     * `CancellationException` 原样抛出，保持结构化并发语义。
     */
    suspend fun snapshotNow(): SnapshotResult = withContext(Dispatchers.IO) {
        try {
            val dirUriString = preferenceStore.snapshotDirUri.value
                ?: return@withContext SnapshotResult.Failure(SnapshotFailureReason.DIR_NOT_SET)

            val json = dataPortRepository.exportJson()
            val treeUri = Uri.parse(dirUriString)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

            val today = todayProvider().format(ISO_DATE)
            val fileName = "$SNAPSHOT_PREFIX$today.json"

            val createdUri = DocumentsContract.createDocument(
                contentResolver,
                parentUri,
                "application/json",
                fileName
            ) ?: return@withContext SnapshotResult.Failure(SnapshotFailureReason.NOT_WRITABLE)

            contentResolver.openOutputStream(createdUri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: return@withContext SnapshotResult.Failure(SnapshotFailureReason.IO_ERROR)

            preferenceStore.setLastSnapshotDate(today)
            pruneTo(preferenceStore.snapshotKeepCount.value, treeUri)
            SnapshotResult.Success(fileName)
        } catch (cancellation: CancellationException) {
            // 取消信号必须原样抛出，不得吞掉
            throw cancellation
        } catch (e: SecurityException) {
            SnapshotResult.Failure(SnapshotFailureReason.PERMISSION_LOST)
        } catch (e: Exception) {
            SnapshotResult.Failure(SnapshotFailureReason.IO_ERROR)
        }
    }

    /**
     * 按文件名日期排序，仅保留最近 [keep] 份，删除更旧的快照。
     *
     * 删除失败仅忽略，不阻塞新快照（PRD §5-12）。
     */
    suspend fun pruneTo(keep: Int, treeUri: Uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

                val names = mutableListOf<String>()
                contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        if (idIdx < 0 || nameIdx < 0) break
                        val name = cursor.getString(nameIdx) ?: continue
                        if (name.startsWith(SNAPSHOT_PREFIX) && name.endsWith(".json")) {
                            names.add(cursor.getString(idIdx))
                        }
                    }
                }

                if (names.size <= keep) return@runCatching

                // 文件名内嵌 ISO 日期，字典序即时间序；升序后删除最旧的 (size - keep) 份
                val sorted = names.sorted()
                val toDelete = sorted.take(sorted.size - keep)
                toDelete.forEach { docId ->
                    runCatching {
                        DocumentsContract.deleteDocument(
                            contentResolver,
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        )
                    }
                }
            }
        }
    }

    /**
     * 关闭自动快照开关。
     *
     * 用于快照失败（目录不可写 / 授权失效）时自动关闭，避免静默失效后用户误以为有兜底（PRD §5-11）。
     */
    fun disableSnapshot() {
        preferenceStore.setSnapshotEnabled(false)
    }

    companion object {
        /** 快照文件名前缀（后接 ISO 日期） */
        const val SNAPSHOT_PREFIX = "ebbinghaus-snapshot-"

        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
