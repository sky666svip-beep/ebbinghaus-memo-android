package com.ebbinghaus.memo.ui.memolist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.preference.PreferenceStore
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.data.repository.MemoSortOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

/** 启动期副作用诊断日志标签（冷启动崩溃排查用） */
private const val LOG_TAG = "EbbinghausLaunch"

/**
 * 知识点列表界面 UI 状态模型
 *
 * 新增字段一律带默认值（§8-7），保证既有构造点零破坏。
 */
data class MemoListUiState(
    val memos: List<KnowledgeMemoEntity> = emptyList(),
    val allTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val searchQuery: String = "",
    /** 当前排序选项（视图状态，单点持有，单列/双窗格共享） */
    val sortOption: MemoSortOption = MemoSortOption.DEFAULT,
    val isLoading: Boolean = false,
    val isEditorDialogVisible: Boolean = false,
    val editingMemo: KnowledgeMemoEntity? = null,
    /** 待确认删除的知识点 ID；为 null 时无待确认项 */
    val pendingDeleteId: Long? = null,
    /** 删除确认弹窗是否可见，与 [pendingDeleteId] 同步维护 */
    val isDeleteDialogVisible: Boolean = false,
    /** 数据库异常等错误文案（状态矩阵 S13） */
    val errorMessage: String? = null,
    /** 是否处于多选态（E11） */
    val isSelectionMode: Boolean = false,
    /** 已选中的知识点 ID 集合 */
    val selectedIds: Set<Long> = emptySet(),
    /** 批量打标签弹窗是否可见 */
    val isTagPickerVisible: Boolean = false,
    /** 批量删除二次确认弹窗是否可见 */
    val isBatchDeleteDialogVisible: Boolean = false
)

/**
 * 知识点列表界面用户交互事件
 *
 * 注意：[MemoListUiEvent.OnDeleteMemo] 为「立即删除」语义（不弹确认窗），被既有单元测试依赖，
 * **不得修改其语义**；删除确认流程请使用 [MemoListUiEvent.OnRequestDeleteMemo] / [OnConfirmDeleteMemo] / [OnCancelDeleteMemo]。
 */
sealed interface MemoListUiEvent {
    data class OnSearchQueryChanged(val query: String) : MemoListUiEvent
    data class OnTagSelected(val tag: String?) : MemoListUiEvent
    data object OnOpenAddDialog : MemoListUiEvent
    data class OnOpenEditDialog(val memo: KnowledgeMemoEntity) : MemoListUiEvent
    data object OnDismissDialog : MemoListUiEvent
    data class OnSaveMemo(
        val id: Long?,
        val content: String,
        val notes: String,
        val tags: List<String>
    ) : MemoListUiEvent

    /** 立即删除（不弹确认窗），保留以兼容既有单测与程序化调用 */
    data class OnDeleteMemo(val id: Long) : MemoListUiEvent

    /** 请求删除：仅打开二次确认弹窗，不执行删除 */
    data class OnRequestDeleteMemo(val id: Long) : MemoListUiEvent

    /** 确认删除：执行软删除（移入回收站）并派发一次性消息 */
    data object OnConfirmDeleteMemo : MemoListUiEvent

    /** 取消删除：关闭二次确认弹窗 */
    data object OnCancelDeleteMemo : MemoListUiEvent

    /** 清空搜索与标签筛选（状态矩阵 S2 的恢复路径） */
    data object OnClearFilters : MemoListUiEvent

    /** 错误态重试：重新订阅数据流（状态矩阵 S13 的恢复路径） */
    data object OnRetry : MemoListUiEvent

    // ---------------- 排序（E10） ----------------

    /** 选择排序方式：即时生效并持久化，不重置搜索词与标签筛选 */
    data class OnSortOptionSelected(val option: MemoSortOption) : MemoListUiEvent

    // ---------------- 多选（E11） ----------------

    /** 长按进入多选态并选中该条 */
    data class OnEnterSelectionMode(val id: Long) : MemoListUiEvent

    /** 切换某条的选中状态 */
    data class OnToggleSelection(val id: Long) : MemoListUiEvent

    /** 全选当前列表 */
    data object OnSelectAll : MemoListUiEvent

    /** 取消全选（保持多选态） */
    data object OnClearSelection : MemoListUiEvent

    /** 退出多选态并清空选择 */
    data object OnExitSelectionMode : MemoListUiEvent

    /** 批量删除：打开二次确认弹窗 */
    data object OnBatchDelete : MemoListUiEvent

    /** 确认批量删除（软删除，单事务） */
    data object OnConfirmBatchDelete : MemoListUiEvent

    /** 取消批量删除 */
    data object OnCancelBatchDelete : MemoListUiEvent

    /** 打开批量打标签弹窗 */
    data object OnOpenTagPicker : MemoListUiEvent

    /** 关闭批量打标签弹窗 */
    data object OnDismissTagPicker : MemoListUiEvent

    /** 确认批量打标签（去重合并） */
    data class OnBatchAddTag(val tag: String) : MemoListUiEvent

    // ---------------- 撤销（P2-1） ----------------

    /**
     * 撤销型 Snackbar 的动作回传。
     *
     * 仅当 [actionKey] 与 ViewModel 当前持有的撤销令牌一致时生效（旧批次回调自动失效）。
     */
    data class OnSnackbarAction(val actionKey: String) : MemoListUiEvent
}

/**
 * 知识点列表一次性消息（Snackbar / Toast）
 *
 * [actionLabel] / [actionKey] 带默认值（§8-5），保证既有构造点与 `.message` 断言零破坏；
 * [actionKey] 非空表示「撤销型 Snackbar」，由 UI 走 5 秒撤销窗口。
 */
sealed interface MemoListEffect {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val actionKey: String? = null
    ) : MemoListEffect
}

/**
 * 知识点管理 ViewModel
 *
 * 负责响应式收集数据流、多关键词模糊搜索、多标签筛选、排序、多选批量操作、删除撤销以及增删改生命周期事件。
 *
 * 约束：搜索词在 ViewModel 中**同步立即生效**，300ms 防抖由 UI 层（`MemoListScreen`）实现，
 * 以兼容使用 `UnconfinedTestDispatcher`（无虚拟时钟）的既有单测。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoListViewModel(
    private val memoRepository: MemoRepository,
    private val preferenceStore: PreferenceStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MemoListUiState(
            isLoading = true,
            sortOption = preferenceStore?.sortOption?.value ?: MemoSortOption.DEFAULT
        )
    )
    val uiState: StateFlow<MemoListUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<MemoListEffect>(replay = 0, extraBufferCapacity = 1)
    val effect: SharedFlow<MemoListEffect> = _effect.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _sortOption = MutableStateFlow(preferenceStore?.sortOption?.value ?: MemoSortOption.DEFAULT)

    /**
     * 撤销批次令牌（单值持有，新删覆盖旧删）。
     *
     * `null` 表示当前无可撤销批次；[OnSnackbarAction] 先比对令牌，不匹配即忽略（旧回调失效）。
     */
    private var pendingUndoToken: String? = null

    /** 撤销目标 ID 集合（与 [pendingUndoToken] 同步覆盖） */
    private var pendingUndoIds: List<Long> = emptyList()

    /** 单调递增的撤销序号，用于生成唯一令牌 */
    private var undoSeq: Long = 0

    /** 列表数据流订阅句柄，用于错误态重试时取消旧订阅 */
    private var memosJob: Job? = null
    /** 标签数据流订阅句柄，用于错误态重试时取消旧订阅 */
    private var tagsJob: Job? = null

    init {
        observeMemos()
        observeAllTags()
    }

    /**
     * 监听搜索词、标签与排序变化，驱动仓储统一查询管线。
     *
     * 排序只改变 ORDER BY，不参与过滤；切换排序不清空搜索词与标签（E10-③）。
     * 仓储抛异常时切换为错误态（S13），保留上一次数据以便重试后快速恢复。
     */
    private fun observeMemos() {
        memosJob?.cancel()
        memosJob = viewModelScope.launch {
            combine(_searchQuery, _selectedTag, _sortOption) { query, tag, sort ->
                Triple(query, tag, sort)
            }.flatMapLatest { (query, tag, sort) ->
                memoRepository.searchMemos(query, tag, sort)
            }.catch {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = "数据读取失败"
                    )
                }
            }.collect { filteredMemos ->
                _uiState.update { current ->
                    current.copy(
                        memos = filteredMemos,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }
        }
    }

    /**
     * 监听全量知识点，提取去重标签列表供筛选 Chips 使用。
     */
    private fun observeAllTags() {
        tagsJob?.cancel()
        tagsJob = viewModelScope.launch {
            memoRepository.getAllMemos().catch { e ->
                // 标签提取失败不阻断主列表，但**不得静默失效**：记录日志以便定位。
                Log.w(LOG_TAG, "标签列表读取失败，标签筛选条将为空（不阻断主列表）", e)
            }.collect { allMemos ->
                val distinctTags = allMemos
                    .flatMap { it.tags }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                _uiState.update { current ->
                    current.copy(allTags = distinctTags)
                }
            }
        }
    }

    /**
     * UI 事件分发入口
     */
    fun onEvent(event: MemoListUiEvent) {
        when (event) {
            is MemoListUiEvent.OnSearchQueryChanged -> {
                _searchQuery.value = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
            }

            is MemoListUiEvent.OnTagSelected -> {
                // 点击相同标签则取消筛选，否则选中该标签
                val nextTag = if (_selectedTag.value == event.tag) null else event.tag
                _selectedTag.value = nextTag
                _uiState.update { it.copy(selectedTag = nextTag) }
            }

            is MemoListUiEvent.OnSortOptionSelected -> {
                _sortOption.value = event.option
                preferenceStore?.setSortOption(event.option)
                _uiState.update { it.copy(sortOption = event.option) }
            }

            is MemoListUiEvent.OnOpenAddDialog -> {
                _uiState.update {
                    it.copy(
                        isEditorDialogVisible = true,
                        editingMemo = null
                    )
                }
            }

            is MemoListUiEvent.OnOpenEditDialog -> {
                _uiState.update {
                    it.copy(
                        isEditorDialogVisible = true,
                        editingMemo = event.memo
                    )
                }
            }

            is MemoListUiEvent.OnDismissDialog -> {
                _uiState.update {
                    it.copy(
                        isEditorDialogVisible = false,
                        editingMemo = null
                    )
                }
            }

            is MemoListUiEvent.OnSaveMemo -> {
                viewModelScope.launch {
                    val trimmedContent = event.content.trim()
                    if (trimmedContent.isEmpty()) return@launch

                    val result = catching {
                        if (event.id == null) {
                            // 新增知识点，自动派发 Day 1 初始复习任务
                            memoRepository.createMemo(
                                content = trimmedContent,
                                notes = event.notes.trim(),
                                tags = event.tags
                            )
                        } else {
                            // 编辑知识点，严格保留原有复习进度与档位
                            memoRepository.updateMemo(
                                id = event.id,
                                content = trimmedContent,
                                notes = event.notes.trim(),
                                tags = event.tags
                            )
                        }
                    }
                    if (result.isSuccess) {
                        _uiState.update {
                            it.copy(
                                isEditorDialogVisible = false,
                                editingMemo = null
                            )
                        }
                    } else {
                        // 保存失败：保留编辑器与已输入内容，明确提示用户重试
                        _effect.emit(MemoListEffect.ShowSnackbar("保存失败，请重试"))
                    }
                }
            }

            is MemoListUiEvent.OnDeleteMemo -> {
                viewModelScope.launch {
                    // E06：立即软删除（移入回收站）；语义保持「立即删除、不弹确认」
                    // 注意：此路径**不派发撤销型 Snackbar**，以守护既有单测语义
                    val result = catching { memoRepository.softDeleteMemo(event.id) }
                    if (result.isFailure) {
                        _effect.emit(MemoListEffect.ShowSnackbar("删除失败，请重试"))
                    }
                }
            }

            is MemoListUiEvent.OnRequestDeleteMemo -> {
                _uiState.update {
                    it.copy(
                        pendingDeleteId = event.id,
                        isDeleteDialogVisible = true
                    )
                }
            }

            is MemoListUiEvent.OnConfirmDeleteMemo -> {
                val targetId = _uiState.value.pendingDeleteId ?: return
                val targetMemo = _uiState.value.memos.firstOrNull { it.id == targetId }
                _uiState.update {
                    it.copy(
                        pendingDeleteId = null,
                        isDeleteDialogVisible = false
                    )
                }
                viewModelScope.launch {
                    // E06：软删除（移入回收站），review_tasks 保留以便还原
                    val result = catching { memoRepository.softDeleteMemo(targetId) }
                    if (result.isFailure) {
                        _effect.emit(MemoListEffect.ShowSnackbar("删除失败，请重试"))
                        return@launch
                    }
                    val preview = targetMemo?.content?.trim().orEmpty().let { raw ->
                        if (raw.length > 20) raw.take(20) + "…" else raw
                    }
                    val token = nextUndoToken()
                    pendingUndoToken = token
                    pendingUndoIds = listOf(targetId)
                    _effect.emit(
                        MemoListEffect.ShowSnackbar(
                            message = if (preview.isBlank()) {
                                "已移入回收站，可在「设置 · 数据与安全 · 回收站」中还原"
                            } else {
                                "已移入回收站「$preview」，可随时还原"
                            },
                            actionLabel = "撤销",
                            actionKey = token
                        )
                    )
                }
            }

            is MemoListUiEvent.OnCancelDeleteMemo -> {
                _uiState.update {
                    it.copy(
                        pendingDeleteId = null,
                        isDeleteDialogVisible = false
                    )
                }
            }

            is MemoListUiEvent.OnClearFilters -> {
                _searchQuery.value = ""
                _selectedTag.value = null
                _uiState.update {
                    it.copy(
                        searchQuery = "",
                        selectedTag = null
                    )
                }
            }

            is MemoListUiEvent.OnRetry -> {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null
                    )
                }
                observeMemos()
                observeAllTags()
            }

            // ---------------- 多选（E11） ----------------

            is MemoListUiEvent.OnEnterSelectionMode -> {
                _uiState.update { current ->
                    if (current.isSelectionMode) {
                        // 已处于多选态：长按退化为「切换该条勾选」，不得重置整个选择集（P1-2）
                        val next = if (current.selectedIds.contains(event.id)) {
                            current.selectedIds - event.id
                        } else {
                            current.selectedIds + event.id
                        }
                        current.copy(selectedIds = next)
                    } else {
                        current.copy(
                            isSelectionMode = true,
                            selectedIds = setOf(event.id)
                        )
                    }
                }
            }

            is MemoListUiEvent.OnToggleSelection -> {
                _uiState.update { current ->
                    val next = if (current.selectedIds.contains(event.id)) {
                        current.selectedIds - event.id
                    } else {
                        current.selectedIds + event.id
                    }
                    current.copy(selectedIds = next)
                }
            }

            is MemoListUiEvent.OnSelectAll -> {
                _uiState.update { current ->
                    current.copy(selectedIds = current.memos.map { it.id }.toSet())
                }
            }

            is MemoListUiEvent.OnClearSelection -> {
                _uiState.update { it.copy(selectedIds = emptySet()) }
            }

            is MemoListUiEvent.OnExitSelectionMode -> {
                _uiState.update {
                    it.copy(
                        isSelectionMode = false,
                        selectedIds = emptySet(),
                        isTagPickerVisible = false,
                        isBatchDeleteDialogVisible = false
                    )
                }
            }

            is MemoListUiEvent.OnBatchDelete -> {
                if (_uiState.value.selectedIds.isEmpty()) return
                _uiState.update { it.copy(isBatchDeleteDialogVisible = true) }
            }

            is MemoListUiEvent.OnCancelBatchDelete -> {
                _uiState.update { it.copy(isBatchDeleteDialogVisible = false) }
            }

            is MemoListUiEvent.OnConfirmBatchDelete -> {
                val ids = _uiState.value.selectedIds.toList()
                if (ids.isEmpty()) return
                // 先仅关闭二次确认弹窗；成功落库后再退出多选态，失败则保留选择以便重试（P1-4）
                _uiState.update { it.copy(isBatchDeleteDialogVisible = false) }
                viewModelScope.launch {
                    // 单事务批量软删除，均可从回收站还原
                    val result = catching { memoRepository.softDeleteMemos(ids) }
                    if (result.isSuccess) {
                        _uiState.update {
                            it.copy(
                                isSelectionMode = false,
                                selectedIds = emptySet()
                            )
                        }
                        val token = nextUndoToken()
                        pendingUndoToken = token
                        pendingUndoIds = ids
                        _effect.emit(
                            MemoListEffect.ShowSnackbar(
                                message = "已将 ${ids.size} 条移入回收站，可随时还原",
                                actionLabel = "撤销",
                                actionKey = token
                            )
                        )
                    } else {
                        // 失败：保留多选态与选择集，明确提示重试
                        _effect.emit(MemoListEffect.ShowSnackbar("操作失败，请重试"))
                    }
                }
            }

            is MemoListUiEvent.OnOpenTagPicker -> {
                if (_uiState.value.selectedIds.isEmpty()) return
                _uiState.update { it.copy(isTagPickerVisible = true) }
            }

            is MemoListUiEvent.OnDismissTagPicker -> {
                _uiState.update { it.copy(isTagPickerVisible = false) }
            }

            is MemoListUiEvent.OnBatchAddTag -> {
                val ids = _uiState.value.selectedIds.toList()
                val tag = event.tag.trim()
                if (ids.isEmpty() || tag.isEmpty()) return
                // 先仅关闭候选弹窗；成功落库后再退出多选态，失败则保留选择以便重试（P1-4）
                _uiState.update { it.copy(isTagPickerVisible = false) }
                viewModelScope.launch {
                    // 单事务 + 去重合并
                    val result = catching { memoRepository.addTagToMemos(ids, tag) }
                    if (result.isSuccess) {
                        _uiState.update {
                            it.copy(
                                isSelectionMode = false,
                                selectedIds = emptySet()
                            )
                        }
                        _effect.emit(
                            MemoListEffect.ShowSnackbar("已为 ${ids.size} 条添加标签「$tag」")
                        )
                    } else {
                        // 失败：保留多选态与选择集，明确提示重试
                        _effect.emit(MemoListEffect.ShowSnackbar("操作失败，请重试"))
                    }
                }
            }

            // ---------------- 撤销（P2-1） ----------------

            is MemoListUiEvent.OnSnackbarAction -> {
                // 旧批次令牌已失效：忽略（连删两条时仅最新一条可撤销，PRD 决策 2）
                if (event.actionKey != pendingUndoToken) return
                val ids = pendingUndoIds
                pendingUndoToken = null
                pendingUndoIds = emptyList()
                if (ids.isEmpty()) return
                viewModelScope.launch {
                    val result = catching { memoRepository.restoreMemos(ids) }
                    if (result.isFailure) {
                        _effect.emit(MemoListEffect.ShowSnackbar("操作失败，请重试"))
                        return@launch
                    }
                    val restored = result.getOrDefault(0)
                    if (restored >= ids.size) {
                        _effect.emit(
                            MemoListEffect.ShowSnackbar("已还原 ${ids.size} 条，复习进度一并恢复")
                        )
                    } else {
                        // 部分/全部条目已被 purgeExpiredTrash 物理清理（PRD 边界 2），不崩溃
                        _effect.emit(MemoListEffect.ShowSnackbar("该条目已过期，无法还原"))
                    }
                }
            }
        }
    }

    /** 生成下一个撤销批次令牌 */
    private fun nextUndoToken(): String = "undo-${++undoSeq}"
}

/**
 * 包裹可失败的落库调用（P1-4）：捕获业务异常并转为 [Result]，
 * 但**不吞掉协程取消信号**（[CancellationException] 原样抛出，避免破坏结构化并发）。
 *
 * 说明：标准库 `runCatching` 会把 `CancellationException` 一并捕获，故此处显式区分处理。
 */
private inline fun <T> catching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
