package com.ebbinghaus.memo.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.data.repository.ReviewRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 详情加载诊断日志标签（与启动路径统一，便于崩溃排查检索） */
private const val LOG_TAG = "EbbinghausLaunch"

data class MemoDetailUiState(
    val isLoading: Boolean = true,
    val memo: KnowledgeMemoEntity? = null,
    val reviewTask: ReviewTaskEntity? = null,
    val isEditorDialogVisible: Boolean = false,
    val isDeleteDialogOpen: Boolean = false,
    val isDeleted: Boolean = false
)

sealed interface MemoDetailUiEvent {
    data object OnOpenEditDialog : MemoDetailUiEvent
    data object OnDismissEditDialog : MemoDetailUiEvent
    data class OnSaveMemo(
        val id: Long,
        val content: String,
        val notes: String,
        val tags: List<String>
    ) : MemoDetailUiEvent
    data object OnOpenDeleteDialog : MemoDetailUiEvent
    data object OnDismissDeleteDialog : MemoDetailUiEvent
    data object OnConfirmDelete : MemoDetailUiEvent
}

class MemoDetailViewModel(
    private val memoId: Long,
    private val memoRepository: MemoRepository,
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoDetailUiState())
    val uiState: StateFlow<MemoDetailUiState> = _uiState.asStateFlow()

    init {
        loadMemoDetails()
    }

    /**
     * 加载详情。
     *
     * **异常边界（防御性加固）**：本方法由 `init` 触发，其 `viewModelScope` 协程内的未捕获异常
     * 会冒泡到进程默认异常处理器 → 闪退。故包裹与启动路径（`DashboardViewModel` /
     * `ReviewViewModel` / `SettingsViewModel`）一致的异常边界：
     * 非取消类异常仅记录日志并降级为可用 UI 状态（结束加载态、数据留空，避免白屏 / 闪退）；
     * `CancellationException` 原样抛出，保持结构化并发语义。
     */
    fun loadMemoDetails() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val memo = memoRepository.getMemoById(memoId)
                val task = reviewRepository.getTaskByMemoId(memoId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        memo = memo,
                        reviewTask = task
                    )
                }
            } catch (cancellation: CancellationException) {
                // 取消信号必须原样抛出，不得吞掉，否则破坏结构化并发
                throw cancellation
            } catch (e: Exception) {
                // 防御性加固：详情加载失败降级为可用 UI 状态（结束加载态、数据留空），不白屏 / 不闪退
                Log.e(LOG_TAG, "备忘录详情加载失败，已降级为可用状态", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onEvent(event: MemoDetailUiEvent) {
        when (event) {
            MemoDetailUiEvent.OnOpenEditDialog -> {
                _uiState.update { it.copy(isEditorDialogVisible = true) }
            }
            MemoDetailUiEvent.OnDismissEditDialog -> {
                _uiState.update { it.copy(isEditorDialogVisible = false) }
            }
            is MemoDetailUiEvent.OnSaveMemo -> {
                viewModelScope.launch {
                    memoRepository.updateMemo(
                        id = event.id,
                        content = event.content,
                        notes = event.notes,
                        tags = event.tags
                    )
                    _uiState.update { it.copy(isEditorDialogVisible = false) }
                    loadMemoDetails()
                }
            }
            MemoDetailUiEvent.OnOpenDeleteDialog -> {
                _uiState.update { it.copy(isDeleteDialogOpen = true) }
            }
            MemoDetailUiEvent.OnDismissDeleteDialog -> {
                _uiState.update { it.copy(isDeleteDialogOpen = false) }
            }
            MemoDetailUiEvent.OnConfirmDelete -> {
                viewModelScope.launch {
                    // E06：删除即软删除（移入回收站），review_tasks 保留以便还原后进度复原
                    memoRepository.softDeleteMemo(memoId)
                    _uiState.update { it.copy(isDeleteDialogOpen = false, isDeleted = true) }
                }
            }
        }
    }
}
