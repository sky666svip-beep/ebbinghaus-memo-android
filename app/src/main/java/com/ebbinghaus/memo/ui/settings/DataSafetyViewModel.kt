package com.ebbinghaus.memo.ui.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebbinghaus.memo.data.export.DataPortRepository
import com.ebbinghaus.memo.data.export.ImportResult
import com.ebbinghaus.memo.data.export.VersionTooNewException
import com.ebbinghaus.memo.data.preference.PreferenceStore
import com.ebbinghaus.memo.data.snapshot.SnapshotFailureReason
import com.ebbinghaus.memo.data.snapshot.SnapshotManager
import com.ebbinghaus.memo.data.snapshot.SnapshotResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 启动期副作用诊断日志标签（冷启动崩溃排查用） */
private const val LOG_TAG = "EbbinghausLaunch"

/**
 * 待确认导入的预览信息
 *
 * @property memoCount 将导入的知识点条数
 * @property taskCount 将导入的复习记录条数
 * @property json 已通过校验的原始 JSON（确认后落库）
 */
data class PendingImport(
    val memoCount: Int,
    val taskCount: Int,
    val json: String
)

/**
 * 「数据与安全」UI 状态模型
 */
data class DataSafetyUiState(
    val snapshotEnabled: Boolean = false,
    val snapshotDirUri: String? = null,
    val snapshotKeepCount: Int = 7,
    val lastSnapshotDate: String? = null,
    /** 是否有正在进行的导出/导入/快照操作 */
    val isBusy: Boolean = false,
    /** 导入预览（非 null 时展示确认弹窗） */
    val pendingImport: PendingImport? = null
)

/**
 * 「数据与安全」交互事件
 */
sealed interface DataSafetyUiEvent {
    data class OnToggleSnapshot(val enabled: Boolean) : DataSafetyUiEvent
    data class OnSnapshotDirPicked(val uri: Uri) : DataSafetyUiEvent
    data class OnKeepCountChanged(val count: Int) : DataSafetyUiEvent
    data object OnSnapshotNow : DataSafetyUiEvent
    data class OnExportRequested(val uri: Uri) : DataSafetyUiEvent
    data class OnImportPicked(val uri: Uri) : DataSafetyUiEvent
    data object OnConfirmImport : DataSafetyUiEvent
    data object OnCancelImport : DataSafetyUiEvent
}

/**
 * 「数据与安全」一次性消息
 */
sealed interface DataSafetyEffect {
    data class ShowSnackbar(val message: String) : DataSafetyEffect
}

/**
 * 「数据与安全」ViewModel（E01/E02 UI 编排）
 *
 * 刻意与已测的 [SettingsViewModel] 分离，避免污染既有单测契约。
 * 全部 IO 走 `Dispatchers.IO`；错误不崩溃，统一转 Snackbar 文案（区分四类失败）。
 */
class DataSafetyViewModel(
    private val dataPortRepository: DataPortRepository,
    private val snapshotManager: SnapshotManager,
    private val preferenceStore: PreferenceStore,
    private val contentResolver: ContentResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DataSafetyUiState(
            snapshotEnabled = preferenceStore.snapshotEnabled.value,
            snapshotDirUri = preferenceStore.snapshotDirUri.value,
            snapshotKeepCount = preferenceStore.snapshotKeepCount.value,
            lastSnapshotDate = preferenceStore.lastSnapshotDate.value
        )
    )
    val uiState: StateFlow<DataSafetyUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<DataSafetyEffect>(replay = 0, extraBufferCapacity = 1)
    val effect: SharedFlow<DataSafetyEffect> = _effect.asSharedFlow()

    private suspend fun snackbar(message: String) {
        _effect.emit(DataSafetyEffect.ShowSnackbar(message))
    }

    fun onEvent(event: DataSafetyUiEvent) {
        when (event) {
            is DataSafetyUiEvent.OnToggleSnapshot -> {
                if (event.enabled && preferenceStore.snapshotDirUri.value.isNullOrBlank()) {
                    // 开关开启但未选目录时不允许开启并提示（PRD §4.4）
                    viewModelScope.launch { snackbar("请先选择快照目录，再开启自动快照") }
                    return
                }
                preferenceStore.setSnapshotEnabled(event.enabled)
                _uiState.update { it.copy(snapshotEnabled = event.enabled) }
            }

            is DataSafetyUiEvent.OnSnapshotDirPicked -> {
                viewModelScope.launch {
                    val granted = runCatching {
                        contentResolver.takePersistableUriPermission(
                            event.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        true
                    }.getOrDefault(false)

                    if (granted) {
                        preferenceStore.setSnapshotDirUri(event.uri.toString())
                        _uiState.update { it.copy(snapshotDirUri = event.uri.toString()) }
                        snackbar("已设置快照目录")
                    } else {
                        snackbar("目录授权失败，请重新选择")
                    }
                }
            }

            is DataSafetyUiEvent.OnKeepCountChanged -> {
                preferenceStore.setSnapshotKeepCount(event.count)
                _uiState.update { it.copy(snapshotKeepCount = preferenceStore.snapshotKeepCount.value) }
            }

            is DataSafetyUiEvent.OnSnapshotNow -> {
                if (preferenceStore.snapshotDirUri.value.isNullOrBlank()) {
                    viewModelScope.launch { snackbar("请先选择快照目录") }
                    return
                }
                _uiState.update { it.copy(isBusy = true) }
                // **异常边界（防御性加固 · 第 2 轮补齐）**：`snapshotNow()` 在旧实现中
                // 于 try 之外读取 `snapshotDirUri`，该读取一旦抛异常将无人兜住。
                // 现同时补齐：① SnapshotManager 内部把读取移入 try；② 此处再包一层外层边界，
                // 任何异常都降级为 Snackbar 提示（用户可见，不静默），并复位 isBusy。
                viewModelScope.launch {
                    try {
                        val result = snapshotManager.snapshotNow()
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                lastSnapshotDate = preferenceStore.lastSnapshotDate.value,
                                snapshotEnabled = preferenceStore.snapshotEnabled.value
                            )
                        }
                        snackbar(snapshotMessage(result))
                    } catch (cancellation: CancellationException) {
                        // 取消信号必须原样抛出，不得吞掉，否则破坏结构化并发
                        throw cancellation
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "立即快照失败，已降级为可见提示（不崩溃）", e)
                        _uiState.update { it.copy(isBusy = false) }
                        snackbar("快照失败，请重试")
                    }
                }
            }

            is DataSafetyUiEvent.OnExportRequested -> {
                _uiState.update { it.copy(isBusy = true) }
                viewModelScope.launch {
                    val result = runCatching {
                        val json = dataPortRepository.exportJson()
                        val count = dataPortRepository.exportSnapshot().memos.size
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(event.uri)?.use { output ->
                                output.write(json.toByteArray(Charsets.UTF_8))
                                output.flush()
                            } ?: error("无法打开输出流")
                        }
                        count
                    }
                    _uiState.update { it.copy(isBusy = false) }
                    result.fold(
                        onSuccess = { count -> snackbar("已导出 $count 条知识点") },
                        onFailure = { snackbar("导出失败：无法写入所选位置") }
                    )
                }
            }

            is DataSafetyUiEvent.OnImportPicked -> {
                viewModelScope.launch {
                    val readResult = runCatching {
                        withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(event.uri)?.use { input ->
                                input.readBytes().toString(Charsets.UTF_8)
                            } ?: error("无法打开输入流")
                        }
                    }
                    val json = readResult.getOrElse {
                        snackbar("读取文件失败，请重试")
                        return@launch
                    }
                    val decoded = dataPortRepository.decodeSnapshot(json)
                    decoded.fold(
                        onSuccess = { snapshot ->
                            _uiState.update {
                                it.copy(
                                    pendingImport = PendingImport(
                                        memoCount = snapshot.memos.size,
                                        taskCount = snapshot.reviewTasks.size,
                                        json = json
                                    )
                                )
                            }
                        },
                        onFailure = { error ->
                            snackbar(
                                if (error is VersionTooNewException) {
                                    "文件来自更新版本，请升级 App 后再导入"
                                } else {
                                    "文件已损坏或不是本 App 的导出文件"
                                }
                            )
                        }
                    )
                }
            }

            is DataSafetyUiEvent.OnCancelImport -> {
                _uiState.update { it.copy(pendingImport = null) }
            }

            is DataSafetyUiEvent.OnConfirmImport -> {
                val pending = _uiState.value.pendingImport ?: return
                _uiState.update { it.copy(pendingImport = null, isBusy = true) }
                viewModelScope.launch {
                    val result = dataPortRepository.importSnapshot(pending.json)
                    _uiState.update { it.copy(isBusy = false) }
                    snackbar(
                        when (result) {
                            is ImportResult.Success ->
                                "已导入 ${result.memoCount} 条知识点 / ${result.taskCount} 条复习记录"

                            is ImportResult.VersionTooNew -> "文件来自更新版本，请升级 App"
                            is ImportResult.CorruptedFile -> "文件已损坏或不是本 App 的导出文件"
                            is ImportResult.Failed -> "导入失败：${result.reason}（数据未变更）"
                        }
                    )
                }
            }
        }
    }

    private fun snapshotMessage(result: SnapshotResult): String = when (result) {
        is SnapshotResult.Skipped -> "已跳过（同日已快照或未开启）"
        is SnapshotResult.Success -> "已生成快照：${result.fileName}"
        is SnapshotResult.Failure -> when (result.reason) {
            SnapshotFailureReason.DIR_NOT_SET -> "请先选择快照目录"
            SnapshotFailureReason.NOT_WRITABLE -> "快照目录不可写，请重新选择"
            SnapshotFailureReason.PERMISSION_LOST -> "快照目录授权已失效，请重新选择目录"
            SnapshotFailureReason.IO_ERROR -> "快照写入失败，请重试"
        }
    }
}
