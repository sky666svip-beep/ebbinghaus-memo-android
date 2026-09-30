package com.ebbinghaus.memo.ui.memolist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ebbinghaus.memo.core.util.MathTextPreprocessor
import com.ebbinghaus.memo.core.util.SearchQueryTokenizer
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.ui.component.DeleteConfirmDialog
import com.ebbinghaus.memo.ui.component.EmptyState
import com.ebbinghaus.memo.ui.component.EmptyStatePrimary
import com.ebbinghaus.memo.ui.component.ErrorState
import com.ebbinghaus.memo.ui.component.HighlightedText
import com.ebbinghaus.memo.ui.component.MemoListSkeleton
import com.ebbinghaus.memo.ui.component.SortBottomSheet
import com.ebbinghaus.memo.ui.component.TagPickerDialog
import com.ebbinghaus.memo.ui.dashboard.DashboardBanner
import com.ebbinghaus.memo.ui.dashboard.DashboardDialog
import com.ebbinghaus.memo.ui.dashboard.DashboardEffect
import com.ebbinghaus.memo.ui.dashboard.DashboardUiEvent
import com.ebbinghaus.memo.ui.dashboard.DashboardViewModel
import com.ebbinghaus.memo.ui.scaffold.LocalSnackbarHostState
import com.ebbinghaus.memo.ui.theme.Dimens
import com.ebbinghaus.memo.ui.util.WindowSizeClass
import com.ebbinghaus.memo.ui.util.WindowWidthSizeClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull

/** 搜索输入防抖时长（毫秒） */
private const val SEARCH_DEBOUNCE_MS = 300L

/** 撤销型 Snackbar 的展示窗口（毫秒）：5 秒内可撤销，超时换兜底文案（P2-1） */
private const val UNDO_WINDOW_MS = 5_000L

/**
 * 知识点管理主界面（Compact 单列 / Medium 双列网格）
 *
 * 页面级入口（顶部栏 / 排序 / 多选态标题 / FAB）由 [MemoListPageShell] 统一承载，
 * 与 Expanded 双窗格左 Pane 复用同一套壳，避免两条分支的入口能力出现分叉。
 *
 * 说明：系统返回键退出多选态的 [BackHandler] 已上提至导航宿主 `AppNavigation`，
 * 使单列与双窗格两条分支共享同一处返回键处理（P0-3）。
 */
@Composable
fun MemoListScreen(
    memoListViewModel: MemoListViewModel,
    dashboardViewModel: DashboardViewModel,
    onNavigateToReview: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    windowSizeClass: WindowSizeClass,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    MemoListPageShell(
        memoListViewModel = memoListViewModel,
        dashboardViewModel = dashboardViewModel,
        windowSizeClass = windowSizeClass,
        onNavigateToReview = onNavigateToReview,
        onNavigateToDetail = onNavigateToDetail,
        selectedMemoId = null,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier
    )
}

/**
 * 知识点列表页面壳（Scaffold + 顶部栏 + 排序弹窗 + 新增 FAB）
 *
 * 供单列页面（[MemoListScreen]）与 Expanded 双窗格左 Pane（[MemoListDetailPane]）复用，
 * 保证两种断点下拥有完全一致的页面级入口：
 * - 顶部栏：标题 / 多选态「已选 N 条」/ 退出多选 / 排序入口（P0-2、P0-4）
 * - 排序底部弹窗（P0-2）
 * - 新增 FAB（P0-1，多选态下自动隐藏）
 *
 * 约束：Scaffold 仅存在于本壳内，[MemoListContent] 始终保持「纯内容」，
 * 避免在内容层再嵌套 Scaffold 造成 insets 双重累加。
 *
 * @param selectedMemoId 双窗格选中项 ID；单列传 null
 * @param onNavigateToDetail 点击卡片回调：单列为跳转详情页，双窗格为更新右侧选中项
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoListPageShell(
    memoListViewModel: MemoListViewModel,
    dashboardViewModel: DashboardViewModel,
    windowSizeClass: WindowSizeClass,
    onNavigateToReview: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    selectedMemoId: Long?,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by memoListViewModel.uiState.collectAsStateWithLifecycle()
    var showSortSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MemoListTopBar(
                uiState = uiState,
                onExitSelection = { memoListViewModel.onEvent(MemoListUiEvent.OnExitSelectionMode) },
                onSortClick = { showSortSheet = true }
            )
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick = { memoListViewModel.onEvent(MemoListUiEvent.OnOpenAddDialog) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(Dimens.RadiusFab),
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新增知识点"
                    )
                }
            }
        }
    ) { innerPadding ->
        MemoListContent(
            memoListViewModel = memoListViewModel,
            dashboardViewModel = dashboardViewModel,
            windowSizeClass = windowSizeClass,
            onNavigateToReview = onNavigateToReview,
            onNavigateToDetail = onNavigateToDetail,
            selectedMemoId = selectedMemoId,
            onNavigateToSettings = onNavigateToSettings,
            modifier = Modifier.padding(innerPadding)
        )
    }

    if (showSortSheet) {
        SortBottomSheet(
            current = uiState.sortOption,
            onSelect = { option ->
                memoListViewModel.onEvent(MemoListUiEvent.OnSortOptionSelected(option))
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }
}

/**
 * 列表页顶部栏（标题 / 多选态「已选 N 条」/ 退出多选 / 排序入口）
 *
 * 抽出为独立组件供单列页面与双窗格左 Pane 共用。标题限单行并省略，
 * 以适配双窗格左 Pane 仅 360dp（[Dimens.ListPaneWidth]）的可用宽度。
 *
 * @param uiState 列表 UI 状态
 * @param onExitSelection 退出多选态
 * @param onSortClick 打开排序底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoListTopBar(
    uiState: MemoListUiState,
    onExitSelection: () -> Unit,
    onSortClick: () -> Unit
) {
    if (uiState.isSelectionMode) {
        TopAppBar(
            title = {
                Text(
                    text = "已选 ${uiState.selectedIds.size} 条",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onExitSelection) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "退出多选")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    } else {
        TopAppBar(
            title = {
                Text(
                    text = "艾宾浩斯备忘录",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            actions = {
                IconButton(onClick = onSortClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "排序"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

/**
 * 知识点列表纯内容区（不含 Scaffold），由页面壳 [MemoListPageShell] 承载，
 * 供单列页面与 Expanded 双窗格左 Pane 复用。
 *
 * @param memoListViewModel 列表 ViewModel
 * @param dashboardViewModel 看板 ViewModel
 * @param windowSizeClass 当前窗口尺寸类别
 * @param onNavigateToReview 一键直达复习页
 * @param onNavigateToDetail 点击卡片回调：单列为跳转详情页，双窗格为更新右侧选中项
 * @param selectedMemoId 双窗格选中项 ID；单列传 null
 * @param modifier 外部修饰器
 */
@Composable
fun MemoListContent(
    memoListViewModel: MemoListViewModel,
    dashboardViewModel: DashboardViewModel,
    windowSizeClass: WindowSizeClass,
    onNavigateToReview: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    selectedMemoId: Long?,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by memoListViewModel.uiState.collectAsStateWithLifecycle()
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    // 每次进入主界面时触发看板检查
    LaunchedEffect(Unit) {
        dashboardViewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)
    }

    // 一次性消息：删除/批量操作结果提示。
    // 采用 collectLatest：新的 effect 取消上一处理块 → 实现「新删覆盖旧删」（架构 D3/R3，**有意行为**：
    // 对本 App 的瞬时消息语义合理，旧撤销回调随之失效）。
    LaunchedEffect(Unit) {
        memoListViewModel.effect.collectLatest { effect ->
            when (effect) {
                is MemoListEffect.ShowSnackbar -> {
                    val key = effect.actionKey
                    if (key == null) {
                        // 普通消息：直接展示，不进入撤销窗口
                        snackbarHostState.showSnackbar(effect.message)
                        return@collectLatest
                    }
                    // 撤销型消息：Indefinite + withDismissAction + 自定义 5000ms 超时窗口
                    val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                        snackbarHostState.showSnackbar(
                            message = effect.message,
                            actionLabel = effect.actionLabel,
                            withDismissAction = true,
                            duration = SnackbarDuration.Indefinite
                        )
                    }
                    when (result) {
                        SnackbarResult.ActionPerformed ->
                            memoListViewModel.onEvent(MemoListUiEvent.OnSnackbarAction(key))

                        null -> {
                            // 超时：取消当前 Snackbar 后换兜底文案（无撤销按钮）
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar("已移入回收站，可在设置中还原")
                        }

                        // 用户主动收起（withDismissAction）→ 不追加任何提示
                        else -> Unit
                    }
                }
            }
        }
    }

    // 一次性消息：启动清理 / 自动快照失败等看板提示
    LaunchedEffect(Unit) {
        dashboardViewModel.effect.collect { effect ->
            when (effect) {
                is DashboardEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    // 搜索输入本地防抖：ViewModel 内保持同步生效以兼容既有单测
    var queryText by rememberSaveable { mutableStateOf(uiState.searchQuery) }
    LaunchedEffect(queryText) {
        if (queryText.isEmpty()) {
            memoListViewModel.onEvent(MemoListUiEvent.OnSearchQueryChanged(""))
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            memoListViewModel.onEvent(MemoListUiEvent.OnSearchQueryChanged(queryText))
        }
    }

    // 依据搜索词切分关键词用于高亮（P2-10：与仓储共用 SearchQueryTokenizer，取前 6 个，集合恒等）
    val keywords = remember(uiState.searchQuery) {
        SearchQueryTokenizer.activeKeywords(uiState.searchQuery)
    }

    // 6 词上限轻提示（P2-6）：仅超限时出现，非模态
    val ignoredKeywordCount = SearchQueryTokenizer.ignoredCount(uiState.searchQuery)

    val selectionEnabled = uiState.isSelectionMode
    val activeTag = uiState.selectedTag

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // 1. 顶部 Hero 看板（展示今日待复习量与一键直达按钮）
        DashboardBanner(
            dueTodayCount = dashboardState.dueTodayCount,
            deferredCount = dashboardState.deferredCount,
            dailyLimit = dashboardState.dailyLimit,
            onNavigateToReview = onNavigateToReview,
            onNavigateToSettings = onNavigateToSettings
        )

        // 2. 多关键词即时模糊搜索栏（多选态下置灰禁用）
        MemoSearchField(
            query = queryText,
            enabled = !selectionEnabled,
            onQueryChanged = { queryText = it },
            onClear = { queryText = "" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXS)
        )

        // 2b. 6 词上限轻提示（仅超限时显示，不弹 Snackbar、不遮挡列表）
        if (ignoredKeywordCount > 0) {
            Text(
                text = "最多支持 6 个关键词，已忽略多余 $ignoredKeywordCount 个",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenPadding)
            )
        }

        // 3. 横向标签筛选 Chips 栏（多选态下置灰禁用）
        if (uiState.allTags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXS),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedTag == null,
                        enabled = !selectionEnabled,
                        onClick = { memoListViewModel.onEvent(MemoListUiEvent.OnTagSelected(null)) },
                        label = { Text("全部") }
                    )
                }
                items(uiState.allTags) { tag ->
                    FilterChip(
                        selected = uiState.selectedTag == tag,
                        enabled = !selectionEnabled,
                        onClick = { memoListViewModel.onEvent(MemoListUiEvent.OnTagSelected(tag)) },
                        label = { Text(tag) }
                    )
                }
            }
        }

        // 3b. 当前标签筛选可清除指示（P2-5④：仅 selectedTag != null 时出现，点击即清除）
        if (activeTag != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = true,
                    onClick = { memoListViewModel.onEvent(MemoListUiEvent.OnTagSelected(activeTag)) },
                    label = { Text("标签：$activeTag") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除标签筛选",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        // 4. 列表主体：错误 / 加载 / 空 / 正常 四态
        val listModifier = Modifier
            .fillMaxWidth()
            .weight(1f)

        when {
            uiState.errorMessage != null -> {
                ErrorState(
                    message = uiState.errorMessage ?: "数据读取失败",
                    onRetry = { memoListViewModel.onEvent(MemoListUiEvent.OnRetry) },
                    modifier = listModifier
                )
            }

            uiState.isLoading && uiState.memos.isEmpty() -> {
                MemoListSkeleton(modifier = listModifier)
            }

            uiState.memos.isEmpty() -> {
                MemoEmptyState(
                    hasFilters = uiState.searchQuery.isNotBlank() || uiState.selectedTag != null,
                    onClearFilters = {
                        // 同步清空本地输入，避免输入框残留旧关键词
                        queryText = ""
                        memoListViewModel.onEvent(MemoListUiEvent.OnClearFilters)
                    },
                    onAddMemo = { memoListViewModel.onEvent(MemoListUiEvent.OnOpenAddDialog) },
                    modifier = listModifier
                )
            }

            windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium -> {
                val gridState = rememberLazyGridState()
                // P2-5③：标签筛选变化后回到首项（键控 selectedTag，避免打字时滚动）
                LaunchedEffect(uiState.selectedTag) {
                    if (uiState.memos.isNotEmpty()) gridState.scrollToItem(0)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = Dimens.GridMinCell),
                    state = gridState,
                    modifier = listModifier,
                    contentPadding = PaddingValues(
                        start = Dimens.ScreenPadding,
                        end = Dimens.ScreenPadding,
                        top = Dimens.SpaceXS,
                        bottom = Dimens.BottomBarHeight
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)
                ) {
                    items(uiState.memos, key = { it.id }) { memo ->
                        MemoCardItem(
                            memo = memo,
                            keywords = keywords,
                            isSelected = memo.id == selectedMemoId,
                            isSelectionMode = selectionEnabled,
                            isChecked = uiState.selectedIds.contains(memo.id),
                            onClick = { onNavigateToDetail(memo.id) },
                            onLongClick = { memoListViewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(memo.id)) },
                            onToggleCheck = { memoListViewModel.onEvent(MemoListUiEvent.OnToggleSelection(memo.id)) },
                            onEdit = { memoListViewModel.onEvent(MemoListUiEvent.OnOpenEditDialog(memo)) },
                            onDelete = {
                                memoListViewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(memo.id))
                            },
                            onTagClick = { tag ->
                                memoListViewModel.onEvent(MemoListUiEvent.OnTagSelected(tag))
                            }
                        )
                    }
                }
            }

            else -> {
                val listState = rememberLazyListState()
                // P2-5③：标签筛选变化后回到首项（键控 selectedTag，避免打字时滚动）
                LaunchedEffect(uiState.selectedTag) {
                    if (uiState.memos.isNotEmpty()) listState.scrollToItem(0)
                }
                LazyColumn(
                    state = listState,
                    modifier = listModifier,
                    contentPadding = PaddingValues(
                        start = Dimens.ScreenPadding,
                        end = Dimens.ScreenPadding,
                        top = Dimens.SpaceXS,
                        bottom = Dimens.BottomBarHeight
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)
                ) {
                    items(uiState.memos, key = { it.id }) { memo ->
                        MemoCardItem(
                            memo = memo,
                            keywords = keywords,
                            isSelected = memo.id == selectedMemoId,
                            isSelectionMode = selectionEnabled,
                            isChecked = uiState.selectedIds.contains(memo.id),
                            onClick = { onNavigateToDetail(memo.id) },
                            onLongClick = { memoListViewModel.onEvent(MemoListUiEvent.OnEnterSelectionMode(memo.id)) },
                            onToggleCheck = { memoListViewModel.onEvent(MemoListUiEvent.OnToggleSelection(memo.id)) },
                            onEdit = { memoListViewModel.onEvent(MemoListUiEvent.OnOpenEditDialog(memo)) },
                            onDelete = {
                                memoListViewModel.onEvent(MemoListUiEvent.OnRequestDeleteMemo(memo.id))
                            },
                            onTagClick = { tag ->
                                memoListViewModel.onEvent(MemoListUiEvent.OnTagSelected(tag))
                            }
                        )
                    }
                }
            }
        }
    }

    // 新增/编辑弹窗（P2-8：传入全部标签作为候选 Chip）
    if (uiState.isEditorDialogVisible) {
        MemoEditDialog(
            memo = uiState.editingMemo,
            allTags = uiState.allTags,
            onDismiss = { memoListViewModel.onEvent(MemoListUiEvent.OnDismissDialog) },
            onSave = { id, content, notes, tags ->
                memoListViewModel.onEvent(MemoListUiEvent.OnSaveMemo(id, content, notes, tags))
            }
        )
    }

    // 删除二次确认弹窗（单条）
    if (uiState.isDeleteDialogVisible) {
        val pendingMemo = uiState.memos.firstOrNull { it.id == uiState.pendingDeleteId }
        DeleteConfirmDialog(
            memoPreview = pendingMemo?.content.orEmpty(),
            onConfirm = { memoListViewModel.onEvent(MemoListUiEvent.OnConfirmDeleteMemo) },
            onDismiss = { memoListViewModel.onEvent(MemoListUiEvent.OnCancelDeleteMemo) }
        )
    }

    // 批量删除二次确认弹窗
    if (uiState.isBatchDeleteDialogVisible) {
        DeleteConfirmDialog(
            memoPreview = "",
            title = "确认删除已选的 ${uiState.selectedIds.size} 条知识点？",
            message = "删除后将移入回收站，30 天内可随时逐条还原，其复习进度会一并保留。",
            onConfirm = { memoListViewModel.onEvent(MemoListUiEvent.OnConfirmBatchDelete) },
            onDismiss = { memoListViewModel.onEvent(MemoListUiEvent.OnCancelBatchDelete) }
        )
    }

    // 批量打标签弹窗
    if (uiState.isTagPickerVisible) {
        TagPickerDialog(
            candidates = uiState.allTags,
            onConfirm = { tag -> memoListViewModel.onEvent(MemoListUiEvent.OnBatchAddTag(tag)) },
            onDismiss = { memoListViewModel.onEvent(MemoListUiEvent.OnDismissTagPicker) }
        )
    }

    // 启动/次日看板弹窗提醒
    if (dashboardState.isVisible) {
        DashboardDialog(
            dueTodayCount = dashboardState.dueTodayCount,
            isMultiDayAbsence = dashboardState.isMultiDayAbsence,
            absentDays = dashboardState.absentDays,
            onConfirm = {
                dashboardViewModel.onEvent(DashboardUiEvent.OnConfirmNavigateToReview)
                onNavigateToReview()
            },
            onDismiss = {
                dashboardViewModel.onEvent(DashboardUiEvent.OnDismissDashboard)
            }
        )
    }
}

/**
 * Expanded 断点下的列表-详情双窗格布局
 *
 * 左 Pane 固定 360dp，复用 [MemoListPageShell] 承载列表，因而与单列页面拥有**完全相同**的
 * 页面级入口：新增 FAB（P0-1）、排序入口与弹窗（P0-2）、多选态「已选 N 条」标题（P0-4）；
 * 右 Pane 承载详情内容，点击列表项仅更新选中项，**不再跳转** `memo_detail` 路由。
 *
 * @param selectedMemoId 当前选中的知识点 ID
 * @param detailPane 右 Pane 内容（由调用方提供，通常为 `MemoDetailPane`）
 */
@Composable
fun MemoListDetailPane(
    selectedMemoId: Long?,
    detailPane: @Composable (Long?) -> Unit,
    memoListViewModel: MemoListViewModel,
    dashboardViewModel: DashboardViewModel,
    windowSizeClass: WindowSizeClass,
    onNavigateToReview: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    onSelectMemo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize()) {
        // 左 Pane：知识库列表（复用页面壳，保证与单列一致的页面级入口）
        Box(modifier = Modifier.width(Dimens.ListPaneWidth)) {
            MemoListPageShell(
                memoListViewModel = memoListViewModel,
                dashboardViewModel = dashboardViewModel,
                windowSizeClass = windowSizeClass,
                onNavigateToReview = onNavigateToReview,
                onNavigateToDetail = onSelectMemo,
                selectedMemoId = selectedMemoId,
                onNavigateToSettings = onNavigateToSettings
            )
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        // 右 Pane：知识点详情
        // 左 Pane 的顶部栏已消费状态栏内边距，此处对右 Pane 施加同等的状态栏内边距，
        // 使两个 Pane 的内容顶端对齐，避免右 Pane 内容被系统状态栏遮挡。
        Box(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            detailPane(selectedMemoId)
        }
    }
}

/**
 * 搜索输入框
 */
@Composable
private fun MemoSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier,
        enabled = enabled,
        placeholder = { Text("搜索知识点内容或笔记（空格切分多词）...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索"
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清空搜索"
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(Dimens.RadiusButton)
    )
}

/**
 * 列表空状态：区分 S1「首次空库」与 S2「搜索/筛选无结果」
 */
@Composable
private fun MemoEmptyState(
    hasFilters: Boolean,
    onClearFilters: () -> Unit,
    onAddMemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (hasFilters) {
        EmptyState(
            icon = Icons.Default.SearchOff,
            title = "未检索到匹配的知识点",
            subtitle = "试试更换关键词，或清空筛选条件重新浏览",
            actionLabel = "清空筛选",
            onAction = onClearFilters,
            modifier = modifier
        )
    } else {
        EmptyStatePrimary(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = "知识库空空如也，点击右下角 + 开始添加吧！",
            subtitle = "每录入一条知识点，系统会自动按艾宾浩斯曲线为你安排复习节奏",
            actionLabel = "新增知识点",
            onAction = onAddMemo,
            modifier = modifier
        )
    }
}

/**
 * 单条知识点卡片项
 *
 * @param memo 知识点实体
 * @param keywords 搜索关键词，用于正文与笔记高亮
 * @param isSelected 双窗格模式下是否为当前选中项
 * @param isSelectionMode 是否处于多选态
 * @param isChecked 多选态下是否被勾选
 * @param onClick 点击回调（多选态下为切换勾选）
 * @param onLongClick 长按回调（非多选态进入多选态；多选态下退化为切换勾选）
 * @param onToggleCheck 勾选切换回调
 * @param onTagClick 点击标签 Chip 回调（P2-5：按该标签筛选）
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MemoCardItem(
    memo: KnowledgeMemoEntity,
    keywords: List<String>,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isChecked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleCheck: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayContent = remember(memo.content) {
        MathTextPreprocessor.formatToUnicode(memo.content)
    }
    val displayNotes = remember(memo.notes) {
        MathTextPreprocessor.formatToUnicode(memo.notes)
    }

    val containerColor: Color = when {
        isChecked -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleCheck() else onClick() },
                // 多选态下长按退化为「切换勾选」，避免重置整个选择集（P1-2）
                onLongClick = { if (isSelectionMode) onToggleCheck() else onLongClick() }
            ),
        shape = RoundedCornerShape(Dimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceM)
        ) {
            // 内容正文（已转换为可读数学符号，命中关键词高亮）
            Row(verticalAlignment = Alignment.Top) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { onToggleCheck() },
                        modifier = Modifier.size(Dimens.TouchTarget)
                    )
                    Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (keywords.isEmpty()) {
                        Text(
                            text = displayContent,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        HighlightedText(
                            text = displayContent,
                            keywords = keywords,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 4
                        )
                    }
                }
            }

            // 个人笔记提示区
            if (memo.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(Dimens.SpaceS))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Note,
                        contentDescription = "笔记",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )

                    Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                    if (keywords.isEmpty()) {
                        Text(
                            text = displayNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            HighlightedText(
                                text = displayNotes,
                                keywords = keywords,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.tertiary
                                ),
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceS))

            // 标签列表与操作按钮区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 标签展示（P2-5：点击 Chip 即按该标签筛选，复用「再点取消」语义）
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
                ) {
                    memo.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = { onTagClick(tag) },
                            label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                // 编辑与删除按钮（多选态下隐藏，避免与勾选语义冲突）
                if (!isSelectionMode) {
                    Row {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(Dimens.TouchTarget)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimens.SpaceXL)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(Dimens.TouchTarget)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(Dimens.SpaceXL)
                            )
                        }
                    }
                }
            }
        }
    }
}
