package com.ebbinghaus.memo.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebbinghaus.memo.data.local.entity.TrashedMemoWithProgress
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.data.repository.TrashSortOption
import com.ebbinghaus.memo.data.trash.TrashRetention
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 回收站 UI 状态模型（P2-2 / P2-3 / P2-4）
 *
 * 新增字段一律带默认值（§8-9），保证既有构造点零破坏。
 */
data class TrashUiState(
    /** 回收站条目（含复习进度只读投影） */
    val items: List<TrashedMemoWithProgress> = emptyList(),
    /** 回收站独立搜索词（与列表页物理隔离，P2-4③） */
    val searchQuery: String = "",
    /** 回收站排序（默认删除时间倒序） */
    val sortOption: TrashSortOption = TrashSortOption.DEFAULT,
    /** 就地展开的条目 ID 集合（支持同时展开多条） */
    val expandedIds: Set<Long> = emptySet(),
    /** 是否处于多选态（P2-3） */
    val isSelectionMode: Boolean = false,
    /** 已选中的条目 ID 集合 */
    val selectedIds: Set<Long> = emptySet(),
    /** 待确认「彻底删除」的条目 ID */
    val pendingDeleteId: Long? = null,
    /** 批量彻底删除二次确认弹窗是否可见 */
    val isBatchDeleteDialogVisible: Boolean = false,
    /** 清空回收站二次确认弹窗是否可见 */
    val isClearConfirmVisible: Boolean = false,
    val isLoading: Boolean = true,
    /** 当前时刻（epoch 毫秒），用于计算剩余天数 */
    val nowMillis: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

/**
 * 回收站交互事件
 */
sealed interface TrashUiEvent {
    data class OnSearchQueryChanged(val query: String) : TrashUiEvent
    data object OnClearSearch : TrashUiEvent
    data class OnSortOptionSelected(val option: TrashSortOption) : TrashUiEvent
    data class OnToggleExpand(val id: Long) : TrashUiEvent

    // 多选（P2-3）
    data class OnEnterSelectionMode(val id: Long) : TrashUiEvent
    data class OnToggleSelection(val id: Long) : TrashUiEvent
    data object OnSelectAll : TrashUiEvent
    data object OnClearSelection : TrashUiEvent
    data object OnExitSelectionMode : TrashUiEvent

    // 批量操作
    data object OnBatchRestore : TrashUiEvent
    data object OnRequestBatchDelete : TrashUiEvent
    data object OnConfirmBatchDelete : TrashUiEvent
    data object OnCancelBatchDelete : TrashUiEvent

    // 单条操作
    data class OnRestore(val id: Long) : TrashUiEvent
    data class OnRequestDeleteForever(val id: Long) : TrashUiEvent
    data object OnConfirmDeleteForever : TrashUiEvent
    data object OnCancelDeleteForever : TrashUiEvent

    // 清空
    data object OnRequestClearTrash : TrashUiEvent
    data object OnConfirmClearTrash : TrashUiEvent
    data object OnCancelClearTrash : TrashUiEvent
}

/**
 * 回收站一次性消息
 */
sealed interface TrashEffect {
    data class ShowSnackbar(val message: String) : TrashEffect
}

/**
 * 回收站 ViewModel（E06 / P2-2 / P2-3 / P2-4）
 *
 * **独立查询域**：数据源为 `getTrashedMemosFiltered`（`deletedAt IS NOT NULL`），
 * 不复用列表页的搜索 / 标签状态，进出回收站不读写列表页的搜索词与标签筛选。
 *
 * 还原与彻底删除均走 IO（`viewModelScope` + Room suspend）；失败用 `catching{}` 包裹，
 * 保留多选态与选择集并提示「操作失败，请重试」（与列表页 P1-4 同款），不崩溃。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModel(
    private val memoRepository: MemoRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<TrashEffect>(replay = 0, extraBufferCapacity = 1)
    val effect: SharedFlow<TrashEffect> = _effect.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(TrashSortOption.DEFAULT)

    init {
        observeTrash()
    }

    /**
     * 监听搜索词与排序变化，驱动回收站独立查询管线。
     *
     * 每次数据流刷新后，将选择集 / 展开集与当前可见条目求交，剔除已被外部清理的 id
     * （PRD 边界 5 / 11），避免「已选 N 条」与实际不符或残留悬空展开态。
     */
    private fun observeTrash() {
        viewModelScope.launch {
            combine(_searchQuery, _sortOption) { query, sort -> query to sort }
                .flatMapLatest { (query, sort) -> memoRepository.getTrashedMemosFiltered(query, sort) }
                .catch {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "回收站读取失败")
                    }
                }
                .collect { items ->
                    _uiState.update { current ->
                        val visibleIds = items.map { it.memo.id }.toSet()
                        current.copy(
                            items = items,
                            isLoading = false,
                            nowMillis = nowProvider(),
                            errorMessage = null,
                            selectedIds = current.selectedIds.intersect(visibleIds),
                            expandedIds = current.expandedIds.intersect(visibleIds)
                        )
                    }
                }
        }
    }

    /** 计算某条目的剩余保留天数 */
    fun remainingDays(deletedAtMillis: Long): Int =
        TrashRetention.remainingDays(deletedAtMillis, _uiState.value.nowMillis)

    /** 计算某条目已删除天数 */
    fun elapsedDays(deletedAtMillis: Long): Int =
        TrashRetention.elapsedDays(deletedAtMillis, _uiState.value.nowMillis)

    fun onEvent(event: TrashUiEvent) {
        when (event) {
            is TrashUiEvent.OnSearchQueryChanged -> {
                _searchQuery.value = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
            }

            is TrashUiEvent.OnClearSearch -> {
                _searchQuery.value = ""
                _uiState.update { it.copy(searchQuery = "") }
            }

            is TrashUiEvent.OnSortOptionSelected -> {
                _sortOption.value = event.option
                _uiState.update { it.copy(sortOption = event.option) }
            }

            is TrashUiEvent.OnToggleExpand -> {
                _uiState.update { current ->
                    val next = if (current.expandedIds.contains(event.id)) {
                        current.expandedIds - event.id
                    } else {
                        current.expandedIds + event.id
                    }
                    current.copy(expandedIds = next)
                }
            }

            is TrashUiEvent.OnEnterSelectionMode -> {
                _uiState.update { current ->
                    if (current.isSelectionMode) {
                        // 多选态下长按退化为「切换勾选」，不得重置选择集（与列表页 P1-2 一致）
                        val next = if (current.selectedIds.contains(event.id)) {
                            current.selectedIds - event.id
                        } else {
                            current.selectedIds + event.id
                        }
                        current.copy(selectedIds = next)
                    } else {
                        current.copy(isSelectionMode = true, selectedIds = setOf(event.id))
                    }
                }
            }

            is TrashUiEvent.OnToggleSelection -> {
                _uiState.update { current ->
                    val next = if (current.selectedIds.contains(event.id)) {
                        current.selectedIds - event.id
                    } else {
                        current.selectedIds + event.id
                    }
                    current.copy(selectedIds = next)
                }
            }

            is TrashUiEvent.OnSelectAll -> {
                _uiState.update { current ->
                    current.copy(selectedIds = current.items.map { it.memo.id }.toSet())
                }
            }

            is TrashUiEvent.OnClearSelection -> {
                _uiState.update { it.copy(selectedIds = emptySet()) }
            }

            is TrashUiEvent.OnExitSelectionMode -> {
                _uiState.update {
                    it.copy(
                        isSelectionMode = false,
                        selectedIds = emptySet(),
                        isBatchDeleteDialogVisible = false
                    )
                }
            }

            is TrashUiEvent.OnBatchRestore -> {
                val ids = _uiState.value.selectedIds.toList()
                if (ids.isEmpty()) return
                viewModelScope.launch {
                    val result = catching { memoRepository.restoreMemos(ids) }
                    if (result.isSuccess) {
                        val restored = result.getOrDefault(0)
                        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
                        _effect.emit(TrashEffect.ShowSnackbar("已还原 $restored 条，复习进度一并恢复"))
                    } else {
                        // 失败：保留多选态与选择集，提示重试
                        _effect.emit(TrashEffect.ShowSnackbar("操作失败，请重试"))
                    }
                }
            }

            is TrashUiEvent.OnRequestBatchDelete -> {
                if (_uiState.value.selectedIds.isEmpty()) return
                _uiState.update { it.copy(isBatchDeleteDialogVisible = true) }
            }

            is TrashUiEvent.OnCancelBatchDelete -> {
                _uiState.update { it.copy(isBatchDeleteDialogVisible = false) }
            }

            is TrashUiEvent.OnConfirmBatchDelete -> {
                val ids = _uiState.value.selectedIds.toList()
                if (ids.isEmpty()) return
                _uiState.update { it.copy(isBatchDeleteDialogVisible = false) }
                viewModelScope.launch {
                    val result = catching { memoRepository.hardDeleteMemos(ids) }
                    if (result.isSuccess) {
                        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
                        _effect.emit(TrashEffect.ShowSnackbar("已彻底删除 ${ids.size} 条"))
                    } else {
                        _effect.emit(TrashEffect.ShowSnackbar("操作失败，请重试"))
                    }
                }
            }

            is TrashUiEvent.OnRestore -> {
                viewModelScope.launch {
                    val result = catching { memoRepository.restoreMemo(event.id) }
                    if (result.isSuccess) {
                        _effect.emit(TrashEffect.ShowSnackbar("已还原，复习进度一并恢复"))
                    } else {
                        _effect.emit(TrashEffect.ShowSnackbar("操作失败，请重试"))
                    }
                }
            }

            is TrashUiEvent.OnRequestDeleteForever -> {
                _uiState.update { it.copy(pendingDeleteId = event.id) }
            }

            is TrashUiEvent.OnCancelDeleteForever -> {
                _uiState.update { it.copy(pendingDeleteId = null) }
            }

            is TrashUiEvent.OnConfirmDeleteForever -> {
                val id = _uiState.value.pendingDeleteId ?: return
                _uiState.update { it.copy(pendingDeleteId = null) }
                viewModelScope.launch {
                    val result = catching { memoRepository.hardDeleteMemo(id) }
                    if (result.isSuccess) {
                        _effect.emit(TrashEffect.ShowSnackbar("已彻底删除，不可恢复"))
                    } else {
                        _effect.emit(TrashEffect.ShowSnackbar("操作失败，请重试"))
                    }
                }
            }

            is TrashUiEvent.OnRequestClearTrash -> {
                _uiState.update { it.copy(isClearConfirmVisible = true) }
            }

            is TrashUiEvent.OnCancelClearTrash -> {
                _uiState.update { it.copy(isClearConfirmVisible = false) }
            }

            is TrashUiEvent.OnConfirmClearTrash -> {
                val count = _uiState.value.items.size
                _uiState.update { it.copy(isClearConfirmVisible = false) }
                viewModelScope.launch {
                    val result = catching { memoRepository.clearTrash() }
                    if (result.isSuccess) {
                        _effect.emit(TrashEffect.ShowSnackbar("已清空回收站（$count 条），不可恢复"))
                    } else {
                        _effect.emit(TrashEffect.ShowSnackbar("操作失败，请重试"))
                    }
                }
            }
        }
    }
}

/**
 * 包裹可失败的落库调用（P1-4）：捕获业务异常并转为 [Result]，
 * 但**不吞掉协程取消信号**（[CancellationException] 原样抛出，避免破坏结构化并发）。
 */
private inline fun <T> catching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
