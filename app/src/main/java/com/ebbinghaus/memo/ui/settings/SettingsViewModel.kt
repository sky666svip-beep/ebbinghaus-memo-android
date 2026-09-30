package com.ebbinghaus.memo.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebbinghaus.memo.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 启动期副作用诊断日志标签（冷启动崩溃排查用） */
private const val LOG_TAG = "EbbinghausLaunch"

/**
 * 设置界面 UI 状态模型
 */
data class SettingsUiState(
    val dailyLimit: Int = 20,
    val isSavedSuccess: Boolean = false,
    val isLoading: Boolean = false,
    /**
     * 设置读取失败文案；非 null 时 UI 展示一行可见提示。
     *
     * 新增字段带默认值（§8-7），保证既有构造点零破坏。
     * 之所以要**可见**：读取失败时滑块会显示默认值（20），可能并非用户真实设置，
     * 仅打日志会让用户误以为「当前就是 20」（静默失效）。
     */
    val loadError: String? = null
)

/**
 * 设置界面用户交互事件
 */
sealed interface SettingsUiEvent {
    data class OnDailyLimitChange(val newLimit: Int) : SettingsUiEvent
    data object OnSaveSettings : SettingsUiEvent
}

/**
 * 设置界面 ViewModel
 *
 * 负责每日复习上限（0~50条）动态响应、合法值范围钳制（0~50）及持久化存储。
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 响应式收集用户设置
        //
        // **异常边界（防御性加固 · 第 2 轮补齐）**：本 `init` 协程在**冷启动首帧之前**
        // 随 `AppNavigation` 组合而创建。加固前若 `getSettings()` 抛异常，未捕获异常会
        // 冒泡到进程默认异常处理器 → 冷启动闪退（或 `isLoading` 永久为 true）。
        // `CancellationException` 原样抛出；其余异常写入可见 `loadError` 并保持界面可用。
        viewModelScope.launch {
            try {
                settingsRepository.getSettings().collect { settings ->
                    _uiState.update { current ->
                        current.copy(
                            dailyLimit = settings.dailyReviewLimit,
                            isLoading = false,
                            loadError = null
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                // 取消信号必须原样抛出，不得吞掉，否则破坏结构化并发
                throw cancellation
            } catch (e: Exception) {
                Log.e(LOG_TAG, "设置读取失败，已降级为默认值（每日上限可能显示为默认）", e)
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        loadError = "设置读取失败，当前显示为默认值"
                    )
                }
            }
        }
    }

    /**
     * UI 事件分发入口
     */
    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.OnDailyLimitChange -> {
                val clamped = event.newLimit.coerceIn(0, 50)
                _uiState.update {
                    it.copy(
                        dailyLimit = clamped,
                        isSavedSuccess = false
                    )
                }
                // 实时持久化保存
                viewModelScope.launch {
                    settingsRepository.updateDailyLimit(clamped)
                }
            }

            is SettingsUiEvent.OnSaveSettings -> {
                val currentLimit = _uiState.value.dailyLimit.coerceIn(0, 50)
                viewModelScope.launch {
                    settingsRepository.updateDailyLimit(currentLimit)
                    _uiState.update { it.copy(isSavedSuccess = true) }
                }
            }
        }
    }
}
