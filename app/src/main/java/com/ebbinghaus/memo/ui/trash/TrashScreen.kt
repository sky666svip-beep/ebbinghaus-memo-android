package com.ebbinghaus.memo.ui.trash

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ebbinghaus.memo.core.util.MathTextPreprocessor
import com.ebbinghaus.memo.data.local.entity.TrashedMemoWithProgress
import com.ebbinghaus.memo.data.repository.TrashSortOption
import com.ebbinghaus.memo.data.trash.TrashRetention
import com.ebbinghaus.memo.ui.component.EmptyState
import com.ebbinghaus.memo.ui.component.MemoListSkeleton
import com.ebbinghaus.memo.ui.component.SelectionActionBar
import com.ebbinghaus.memo.ui.component.SelectionActionItem
import com.ebbinghaus.memo.ui.scaffold.LocalSnackbarHostState
import com.ebbinghaus.memo.ui.theme.Dimens

/**
 * 回收站排序选项中文文案（文案硬编码于 Composable 层）
 */
private fun TrashSortOption.displayName(): String = when (this) {
    TrashSortOption.DELETED_DESC -> "删除时间倒序"
    TrashSortOption.DELETED_ASC -> "删除时间正序"
    TrashSortOption.TAG_ASC -> "按标签"
}

/**
 * 回收站页面（E06 / P2-2 / P2-3 / P2-4）
 *
 * 独立下钻页（不占 Tab），能力：
 * - 顶部搜索栏（内容 / 笔记 / 标签命中）+ 排序弹窗（删除时间倒 / 正序、按标签）；
 * - 点击条目**就地展开**预览（完整内容 + 笔记 + 标签 + 复习进度），再点收起；
 * - 长按进入多选，底部操作栏支持批量还原（免确认）与批量彻底删除（二次确认含条数）；
 * - 多选态禁用搜索栏；系统返回键退出多选（由 `AppNavigation` 上提的 `BackHandler` 承载）。
 *
 * @param viewModel 回收站 ViewModel
 * @param onNavigateBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: TrashViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    var showSortSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TrashEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val allSelected = uiState.items.isNotEmpty() && uiState.selectedIds.size == uiState.items.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
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
                        IconButton(onClick = { viewModel.onEvent(TrashUiEvent.OnExitSelectionMode) }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "退出多选")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(text = "回收站", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSortSheet = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "排序"
                            )
                        }
                        if (uiState.items.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onEvent(TrashUiEvent.OnRequestClearTrash) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "清空回收站"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (uiState.isSelectionMode) {
                SelectionActionBar(
                    actions = listOf(
                        SelectionActionItem(
                            icon = Icons.Default.RestoreFromTrash,
                            label = "还原",
                            enabled = uiState.selectedIds.isNotEmpty(),
                            onClick = { viewModel.onEvent(TrashUiEvent.OnBatchRestore) }
                        ),
                        SelectionActionItem(
                            icon = Icons.Default.DeleteForever,
                            label = "彻底删除",
                            enabled = uiState.selectedIds.isNotEmpty(),
                            onClick = { viewModel.onEvent(TrashUiEvent.OnRequestBatchDelete) }
                        ),
                        SelectionActionItem(
                            icon = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                            label = if (allSelected) "取消全选" else "全选当前结果",
                            enabled = true,
                            onClick = {
                                if (allSelected) {
                                    viewModel.onEvent(TrashUiEvent.OnClearSelection)
                                } else {
                                    viewModel.onEvent(TrashUiEvent.OnSelectAll)
                                }
                            }
                        ),
                        SelectionActionItem(
                            icon = Icons.Default.Close,
                            label = "关闭",
                            enabled = true,
                            onClick = { viewModel.onEvent(TrashUiEvent.OnExitSelectionMode) }
                        )
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 搜索栏（多选态下置灰禁用，避免选择集随筛选漂移）
            TrashSearchField(
                query = uiState.searchQuery,
                enabled = !uiState.isSelectionMode,
                onQueryChanged = { viewModel.onEvent(TrashUiEvent.OnSearchQueryChanged(it)) },
                onClear = { viewModel.onEvent(TrashUiEvent.OnClearSearch) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS)
            )

            // 「复习进度将一并还原」核心价值提示
            if (uiState.items.isNotEmpty() && !uiState.isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXS),
                    shape = RoundedCornerShape(Dimens.RadiusCard),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "回收站中的知识点保留 ${TrashRetention.RETENTION_DAYS} 天，还原时复习进度将一并恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(Dimens.SpaceM)
                    )
                }
            }

            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    MemoListSkeleton(modifier = Modifier.fillMaxSize())
                }

                uiState.errorMessage != null -> {
                    EmptyState(
                        icon = Icons.Default.DeleteForever,
                        title = uiState.errorMessage ?: "回收站读取失败",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                uiState.items.isEmpty() && uiState.searchQuery.isNotBlank() -> {
                    // 双空态之一：搜索无结果（P2-4 / PRD 边界 6）
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "没有匹配的知识点",
                        subtitle = "换个关键词试试，或清空搜索浏览全部回收站条目",
                        actionLabel = "清空搜索",
                        onAction = { viewModel.onEvent(TrashUiEvent.OnClearSearch) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                uiState.items.isEmpty() -> {
                    // 双空态之二：回收站本为空
                    EmptyState(
                        icon = Icons.Default.RestorePage,
                        title = "回收站是空的",
                        subtitle = "删除的知识点会先来到这里，30 天内可随时还原",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Dimens.ScreenPadding,
                            end = Dimens.ScreenPadding,
                            top = Dimens.SpaceXS,
                            bottom = Dimens.SpaceXXL
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)
                    ) {
                        items(uiState.items, key = { it.memo.id }) { item ->
                            TrashItem(
                                item = item,
                                expanded = uiState.expandedIds.contains(item.memo.id),
                                isSelectionMode = uiState.isSelectionMode,
                                isChecked = uiState.selectedIds.contains(item.memo.id),
                                remainingDays = viewModel.remainingDays(item.memo.deletedAt ?: 0L),
                                elapsedDays = viewModel.elapsedDays(item.memo.deletedAt ?: 0L),
                                onToggleExpand = { viewModel.onEvent(TrashUiEvent.OnToggleExpand(item.memo.id)) },
                                onLongPress = { viewModel.onEvent(TrashUiEvent.OnEnterSelectionMode(item.memo.id)) },
                                onToggleCheck = { viewModel.onEvent(TrashUiEvent.OnToggleSelection(item.memo.id)) },
                                onRestore = { viewModel.onEvent(TrashUiEvent.OnRestore(item.memo.id)) },
                                onDeleteForever = {
                                    viewModel.onEvent(TrashUiEvent.OnRequestDeleteForever(item.memo.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 排序底部弹窗
    if (showSortSheet) {
        TrashSortBottomSheet(
            current = uiState.sortOption,
            onSelect = { option ->
                viewModel.onEvent(TrashUiEvent.OnSortOptionSelected(option))
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }

    // 单条彻底删除二次确认
    if (uiState.pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(TrashUiEvent.OnCancelDeleteForever) },
            title = { Text("彻底删除该知识点？") },
            text = { Text("此操作将立即物理删除该知识点及其复习任务，不可恢复。") },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(TrashUiEvent.OnConfirmDeleteForever) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(TrashUiEvent.OnCancelDeleteForever) }) {
                    Text("取消")
                }
            }
        )
    }

    // 批量彻底删除二次确认（文案含条数，P2-3③）
    if (uiState.isBatchDeleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(TrashUiEvent.OnCancelBatchDelete) },
            title = { Text("彻底删除已选的 ${uiState.selectedIds.size} 条？") },
            text = {
                Text("将彻底删除 ${uiState.selectedIds.size} 条知识点及其复习任务，此操作不可恢复。")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(TrashUiEvent.OnConfirmBatchDelete) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(TrashUiEvent.OnCancelBatchDelete) }) {
                    Text("取消")
                }
            }
        )
    }

    // 清空回收站二次确认
    if (uiState.isClearConfirmVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(TrashUiEvent.OnCancelClearTrash) },
            title = { Text("清空回收站？") },
            text = {
                Text("将彻底删除回收站内全部 ${uiState.items.size} 条知识点及其复习任务，此操作不可恢复。")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(TrashUiEvent.OnConfirmClearTrash) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(TrashUiEvent.OnCancelClearTrash) }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 回收站搜索输入框
 */
@Composable
private fun TrashSearchField(
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
        placeholder = { Text("搜索回收站内容、笔记或标签...") },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = "搜索")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "清空搜索")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(Dimens.RadiusButton)
    )
}

/**
 * 回收站排序底部弹窗（3 项：删除时间倒序 / 删除时间正序 / 按标签）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashSortBottomSheet(
    current: TrashSortOption,
    onSelect: (TrashSortOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.SpaceXL)
        ) {
            Text(
                text = "排序方式",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = Dimens.SpaceXL,
                    end = Dimens.SpaceXL,
                    top = Dimens.SpaceS,
                    bottom = Dimens.SpaceS
                )
            )

            TrashSortOption.entries.forEach { option ->
                val selected = option == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, onClick = { onSelect(option) })
                        .padding(horizontal = Dimens.SpaceXL, vertical = Dimens.SpaceM),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    RadioButton(selected = selected, onClick = { onSelect(option) })
                    Spacer(modifier = Modifier.width(Dimens.SpaceM))
                    Text(
                        text = option.displayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.SpaceXS))
            }
        }
    }
}

/**
 * 回收站单条卡片
 *
 * 折叠态：3 行摘要 + 标签 + 「删除于 X 天前 · 剩余 Y 天」；
 * 展开态（就地，P2-2）：完整内容 + 笔记 + 标签 + 复习进度（档位 / 下次到期 / 已复习次数）。
 *
 * @param expanded 是否就地展开
 * @param isSelectionMode 是否处于多选态（多选态下单击 = 切换勾选，不再展开）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashItem(
    item: TrashedMemoWithProgress,
    expanded: Boolean,
    isSelectionMode: Boolean,
    isChecked: Boolean,
    remainingDays: Int,
    elapsedDays: Int,
    onToggleExpand: () -> Unit,
    onLongPress: () -> Unit,
    onToggleCheck: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier
) {
    val memo = item.memo
    val containerColor = if (isChecked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleCheck() else onToggleExpand() },
                onLongClick = { if (isSelectionMode) onToggleCheck() else onLongPress() }
            ),
        shape = RoundedCornerShape(Dimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(Dimens.SpaceM)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { onToggleCheck() },
                        modifier = Modifier.size(Dimens.TouchTarget)
                    )
                    Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                }
                Column(modifier = Modifier.weight(1f)) {
                    // 完整内容（折叠态 3 行）
                    Text(
                        text = MathTextPreprocessor.formatToUnicode(memo.content),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 笔记（展开态完整显示）
                    if (memo.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Dimens.SpaceXS))
                        Text(
                            text = MathTextPreprocessor.formatToUnicode(memo.notes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 标签
                    if (memo.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Dimens.SpaceXS))
                        Text(
                            text = memo.tags.joinToString("  ") { "#$it" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 展开态：复习进度（档位 / 下次到期 / 已复习次数）
                    if (expanded) {
                        Spacer(modifier = Modifier.height(Dimens.SpaceS))
                        TrashProgressSection(item)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceXS))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 「剩余天数」用 outline 色，不用 error 红（§8-12）
                Text(
                    text = "删除于 $elapsedDays 天前 · 剩余 $remainingDays 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )

                if (!isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onRestore, modifier = Modifier.height(Dimens.TouchTarget)) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.SpaceXL)
                            )
                            Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                            Text("还原")
                        }
                        IconButton(
                            onClick = onDeleteForever,
                            modifier = Modifier.size(Dimens.TouchTarget)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "彻底删除",
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

/**
 * 展开态复习进度区：档位 / 下次到期 / 已复习次数（字段与实体逐项对应，P2-2②）
 */
@Composable
private fun TrashProgressSection(item: TrashedMemoWithProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SpaceXS),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "复习进度",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "当前档位：${item.stageLevel ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "下次到期：${item.dueDate?.toString() ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "已复习次数：${item.reviewCount ?: 0}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
