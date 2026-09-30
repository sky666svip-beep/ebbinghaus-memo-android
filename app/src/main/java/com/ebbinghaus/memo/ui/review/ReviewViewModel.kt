package com.ebbinghaus.memo.ui.review

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebbinghaus.memo.core.engine.RolloverEngine
import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.data.repository.ReviewRepository
import com.ebbinghaus.memo.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 启动期副作用诊断日志标签（冷启动崩溃排查用） */
private const val LOG_TAG = "EbbinghausLaunch"

/**
 * 复习流 UI 状态模型
 */
data class ReviewUiState(
    val reviewQueue: List<MemoWithReviewTask> = emptyList(),
    val currentIndex: Int = 0,
    val isDetailExpanded: Boolean = false,
    val isEditingNotes: Boolean = false,
    val notesDraft: String = "",
    val isCompleted: Boolean = false,
    val totalBatchCount: Int = 0,
    val totalPendingCount: Int = 0,
    val deferredCount: Int = 0,
    /** 每日复习上限（0 表示用户主动暂停复习，P2-9） */
    val dailyLimit: Int = 20,
    val isLoading: Boolean = false,
    /**
     * 批次加载失败文案；非 null 时 UI **必须**展示可重试的错误态。
     *
     * 新增字段带默认值（§8-7），保证既有构造点零破坏。
     * 之所以要有**可见**错误态而非仅打日志：复习是用户可感知的核心功能，
     * 若静默降级，空队列会被渲染成「今日复习全部达成」，属严重误导（静默失效）。
     */
    val loadError: String? = null
) {
    val currentItem: MemoWithReviewTask?
        get() = reviewQueue.getOrNull(currentIndex)
}

/**
 * 复习流用户交互事件
 */
sealed interface ReviewUiEvent {
    data object OnToggleDetails : ReviewUiEvent
    data object OnStartEditNotes : ReviewUiEvent
    data object OnCancelEditNotes : ReviewUiEvent
    data class OnNotesDraftChanged(val draft: String) : ReviewUiEvent
    data class OnSaveNotesDraft(val newNotes: String) : ReviewUiEvent
    data class OnSubmitRating(val rating: ReviewRating) : ReviewUiEvent
    data object OnSkipCurrent : ReviewUiEvent
    data object OnRestartSession : ReviewUiEvent
}

/**
 * 复习流一次性消息（Snackbar / Toast）
 */
sealed interface ReviewEffect {
    data class ShowSnackbar(val message: String) : ReviewEffect
}

/**
 * 复习交互模式 ViewModel
 *
 * 负责组装今日待复习批次、卡片推进索引管理、即时笔记编辑持久化以及 5 类评级（忘记/模糊/记住/已复习/跳过）状态机驱动。
 */
class ReviewViewModel(
    private val reviewRepository: ReviewRepository,
    private val settingsRepository: SettingsRepository,
    private val memoRepository: MemoRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState(isLoading = true))
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ReviewEffect>(replay = 0, extraBufferCapacity = 1)
    val effect: SharedFlow<ReviewEffect> = _effect.asSharedFlow()

    init {
        loadReviewBatch()
    }

    /**
     * 加载今日复习批次
     *
     * **异常边界（防御性加固 · 第 2 轮补齐）**：本方法由 `init` 在**冷启动首帧之前**
     * 随 `AppNavigation` 组合而触发，且依赖 `getSettings()` / `getDueReviewTasks()` 两条 Room 流。
     * 任一抛异常时，未捕获异常会冒泡到进程默认异常处理器 → 冷启动闪退。
     *
     * 失败处置（**不静默**）：`CancellationException` 原样抛出；其余异常写入
     * `loadError` 可见错误态（`ReviewScreen` 展示可重试的 `ErrorState`），并尽力发一条
     * Snackbar 提示。**绝不能**静默降级为空队列——否则界面会把失败渲染成
     * 「今日复习全部达成」，属严重误导。
     */
    fun loadReviewBatch() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            try {
                val today = todayProvider()
                val settings = settingsRepository.getSettings().first()
                val dueTasks = reviewRepository.getDueReviewTasks(today).first()

                val plan = RolloverEngine.planReviewBatch(
                    pendingTasks = dueTasks,
                    today = today,
                    dailyLimit = settings.dailyReviewLimit
                )

                val batch = plan.todayBatch
                _uiState.update {
                    it.copy(
                        reviewQueue = batch,
                        currentIndex = 0,
                        isDetailExpanded = false,
                        isEditingNotes = false,
                        notesDraft = "",
                        isCompleted = batch.isEmpty(),
                        totalBatchCount = batch.size,
                        totalPendingCount = plan.totalPendingCount,
                        deferredCount = plan.deferredTasks.size,
                        dailyLimit = settings.dailyReviewLimit,
                        isLoading = false,
                        loadError = null
                    )
                }
            } catch (cancellation: CancellationException) {
                // 取消信号必须原样抛出，不得吞掉，否则破坏结构化并发
                throw cancellation
            } catch (e: Exception) {
                Log.e(LOG_TAG, "复习批次加载失败，已切换为可重试错误态（不静默降级）", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = "复习批次加载失败，请重试"
                    )
                }
                // 尽力提示（无订阅者时会被丢弃，属可接受的尽力而为；错误态本身已保证可见）
                _effect.tryEmit(ReviewEffect.ShowSnackbar("复习批次加载失败，请重试"))
            }
        }
    }

    /**
     * UI 事件处理分发
     */
    fun onEvent(event: ReviewUiEvent) {
        when (event) {
            is ReviewUiEvent.OnToggleDetails -> {
                _uiState.update { it.copy(isDetailExpanded = !it.isDetailExpanded) }
            }

            is ReviewUiEvent.OnStartEditNotes -> {
                val currentNotes = _uiState.value.currentItem?.memo?.notes ?: ""
                _uiState.update {
                    it.copy(
                        isEditingNotes = true,
                        notesDraft = currentNotes
                    )
                }
            }

            is ReviewUiEvent.OnCancelEditNotes -> {
                _uiState.update {
                    it.copy(
                        isEditingNotes = false,
                        notesDraft = ""
                    )
                }
            }

            is ReviewUiEvent.OnNotesDraftChanged -> {
                _uiState.update { it.copy(notesDraft = event.draft) }
            }

            is ReviewUiEvent.OnSaveNotesDraft -> {
                val current = _uiState.value.currentItem ?: return
                viewModelScope.launch {
                    memoRepository.updateNotesOnly(current.memo.id, event.newNotes)
                    // 同步刷新本地队列中的笔记，保证用户视觉一致性
                    val updatedQueue = _uiState.value.reviewQueue.mapIndexed { index, item ->
                        if (index == _uiState.value.currentIndex) {
                            item.copy(memo = item.memo.copy(notes = event.newNotes))
                        } else {
                            item
                        }
                    }
                    _uiState.update {
                        it.copy(
                            reviewQueue = updatedQueue,
                            isEditingNotes = false,
                            notesDraft = ""
                        )
                    }
                }
            }

            is ReviewUiEvent.OnSubmitRating -> {
                submitRating(event.rating)
            }

            is ReviewUiEvent.OnSkipCurrent -> {
                submitRating(ReviewRating.SKIP)
            }

            is ReviewUiEvent.OnRestartSession -> {
                loadReviewBatch()
            }
        }
    }

    /**
     * 提交单条复习评级并推进题号索引
     *
     * 若用户正处于笔记编辑态且草稿与原文不同，则先自动保存笔记（状态矩阵 S14），
     * 再提交评级，避免编辑内容丢失。非编辑态不受影响，保证既有单测行为一致。
     */
    private fun submitRating(rating: ReviewRating) {
        val state = _uiState.value
        val current = state.currentItem ?: return
        val today = todayProvider()

        // 编辑态自动保存判定：仅 isEditingNotes == true 且草稿有变更时才触发
        val shouldAutoSaveNotes = state.isEditingNotes && state.notesDraft != current.memo.notes
        val draftToSave = state.notesDraft

        viewModelScope.launch {
            if (shouldAutoSaveNotes) {
                memoRepository.updateNotesOnly(current.memo.id, draftToSave)
                // 同步刷新本地队列，保证提交后卡片不回退到旧笔记
                val updatedQueue = _uiState.value.reviewQueue.mapIndexed { index, item ->
                    if (index == _uiState.value.currentIndex) {
                        item.copy(memo = item.memo.copy(notes = draftToSave))
                    } else {
                        item
                    }
                }
                _uiState.update { it.copy(reviewQueue = updatedQueue) }
                _effect.emit(ReviewEffect.ShowSnackbar("笔记已自动保存"))
            }

            // 异步持久化下次复习日期与档位变更
            reviewRepository.submitReviewRating(
                taskId = current.reviewTask.id,
                rating = rating,
                reviewDate = today
            )

            val nextIndex = _uiState.value.currentIndex + 1
            val isFinished = nextIndex >= _uiState.value.reviewQueue.size

            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    isCompleted = isFinished,
                    isDetailExpanded = false,
                    isEditingNotes = false,
                    notesDraft = ""
                )
            }
        }
    }
}
