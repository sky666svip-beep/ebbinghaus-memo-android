package com.ebbinghaus.memo.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ebbinghaus.memo.crash.CrashLogger
import com.ebbinghaus.memo.data.export.DataPortRepository
import com.ebbinghaus.memo.data.preference.PreferenceStore
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.data.repository.ReviewRepository
import com.ebbinghaus.memo.data.repository.SettingsRepository
import com.ebbinghaus.memo.data.snapshot.SnapshotManager
import com.ebbinghaus.memo.ui.component.SelectionActionBar
import com.ebbinghaus.memo.ui.component.SelectionActionItem
import com.ebbinghaus.memo.ui.dashboard.DashboardViewModel
import com.ebbinghaus.memo.ui.detail.MemoDetailPane
import com.ebbinghaus.memo.ui.detail.MemoDetailScreen
import com.ebbinghaus.memo.ui.detail.MemoDetailViewModel
import com.ebbinghaus.memo.ui.memolist.MemoListDetailPane
import com.ebbinghaus.memo.ui.memolist.MemoListScreen
import com.ebbinghaus.memo.ui.memolist.MemoListUiEvent
import com.ebbinghaus.memo.ui.memolist.MemoListViewModel
import com.ebbinghaus.memo.ui.review.ReviewScreen
import com.ebbinghaus.memo.ui.review.ReviewViewModel
import com.ebbinghaus.memo.ui.scaffold.AppScaffold
import com.ebbinghaus.memo.ui.settings.DataSafetyViewModel
import com.ebbinghaus.memo.ui.settings.SettingsScreen
import com.ebbinghaus.memo.ui.settings.SettingsViewModel
import com.ebbinghaus.memo.ui.theme.Dimens
import com.ebbinghaus.memo.ui.trash.TrashScreen
import com.ebbinghaus.memo.ui.trash.TrashUiEvent
import com.ebbinghaus.memo.ui.trash.TrashViewModel
import com.ebbinghaus.memo.ui.util.rememberWindowSizeClass

/** 双窗格「未选中」哨兵值 */
private const val NO_MEMO_SELECTED = -1L

/** 一级 Tab 之间切换的淡入淡出时长（毫秒） */
private const val NAV_FADE_MS = 180

/** 层级下钻（进入知识点详情）的横向滑动时长（毫秒） */
private const val NAV_SLIDE_MS = 260

/**
 * 应用全局导航宿主
 *
 * 管理知识点列表 (MemoList)、卡片式复习流 (Review)、偏好设置 (Settings) 与回收站 (Trash) 间的流转过渡。
 */
@Composable
fun AppNavigation(
    memoRepository: MemoRepository,
    reviewRepository: ReviewRepository,
    settingsRepository: SettingsRepository,
    preferenceStore: PreferenceStore,
    dataPortRepository: DataPortRepository,
    snapshotManager: SnapshotManager,
    modifier: Modifier = Modifier,
    crashLogger: CrashLogger? = null,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current

    val memoListViewModel: MemoListViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MemoListViewModel(memoRepository, preferenceStore) as T
            }
        }
    )

    val reviewViewModel: ReviewViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReviewViewModel(
                    reviewRepository = reviewRepository,
                    settingsRepository = settingsRepository,
                    memoRepository = memoRepository
                ) as T
            }
        }
    )

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(
                    reviewRepository = reviewRepository,
                    settingsRepository = settingsRepository,
                    memoRepository = memoRepository,
                    snapshotManager = snapshotManager
                ) as T
            }
        }
    )

    val settingsViewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(settingsRepository) as T
            }
        }
    )

    val dataSafetyViewModel: DataSafetyViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DataSafetyViewModel(
                    dataPortRepository = dataPortRepository,
                    snapshotManager = snapshotManager,
                    preferenceStore = preferenceStore,
                    contentResolver = context.contentResolver
                ) as T
            }
        }
    )

    // 回收站 ViewModel 上提至导航宿主（M13）：
    // 使其生命周期与导航宿主一致，从而让顶部 BackHandler 能读取回收站的多选态并统一处理返回键。
    val trashViewModel: TrashViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TrashViewModel(memoRepository) as T
            }
        }
    )

    AppNavigation(
        navController = navController,
        memoListViewModel = memoListViewModel,
        reviewViewModel = reviewViewModel,
        dashboardViewModel = dashboardViewModel,
        settingsViewModel = settingsViewModel,
        dataSafetyViewModel = dataSafetyViewModel,
        trashViewModel = trashViewModel,
        memoRepository = memoRepository,
        reviewRepository = reviewRepository,
        crashLogger = crashLogger,
        modifier = modifier
    )
}

/**
 * 接受显式 ViewModel 实例的导航宿主，便于解耦、测试与预览
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    memoListViewModel: MemoListViewModel,
    reviewViewModel: ReviewViewModel,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    dataSafetyViewModel: DataSafetyViewModel,
    trashViewModel: TrashViewModel,
    memoRepository: MemoRepository,
    reviewRepository: ReviewRepository,
    modifier: Modifier = Modifier,
    crashLogger: CrashLogger? = null
) {
    val windowSizeClass = rememberWindowSizeClass()
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val memoListState by memoListViewModel.uiState.collectAsStateWithLifecycle()
    val trashState by trashViewModel.uiState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 多选态下系统返回键退出多选（§4.2 / P2-3）
    // 上提至导航宿主：单列（Compact/Medium）与双窗格（Expanded）两条分支共用同一处处理，
    // 修复 Expanded 左 Pane 缺少 BackHandler 导致返回键直接退出列表页的问题（P0-3）；
    // 回收站多选态同样在此统一处理，避免误退出回收站页。
    BackHandler(
        enabled = (currentRoute == Screen.MemoList.route && memoListState.isSelectionMode) ||
            (currentRoute == Screen.Trash.route && trashState.isSelectionMode)
    ) {
        if (currentRoute == Screen.Trash.route) {
            trashViewModel.onEvent(TrashUiEvent.OnExitSelectionMode)
        } else {
            memoListViewModel.onEvent(MemoListUiEvent.OnExitSelectionMode)
        }
    }

    // 双窗格选中项；NO_MEMO_SELECTED 表示未选中
    var selectedMemoIdRaw by rememberSaveable { mutableLongStateOf(NO_MEMO_SELECTED) }
    val selectedMemoId: Long? = selectedMemoIdRaw.takeIf { it != NO_MEMO_SELECTED }

    // Badge 唯一真源：DashboardViewModel.dueTodayCount（dailyLimit > 0 时才展示）
    val reviewBadgeCount = if (dashboardState.dailyLimit > 0) dashboardState.dueTodayCount else 0

    val onNavigateToTopLevel: (TopLevelDestination) -> Unit = { destination ->
        if (currentRoute != destination.route) {
            navController.navigate(destination.route) {
                // 回退到起始目的地并保存/恢复各 Tab 的滚动与搜索状态
                popUpTo(navController.graph.startDestinationRoute ?: Screen.MemoList.route) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val onNavigateToReview: () -> Unit = {
        // R7：从任意入口进入复习页都必须重新装载批次，避免看到上一次的残留
        reviewViewModel.loadReviewBatch()
        if (currentRoute != Screen.Review.route) {
            navController.navigate(Screen.Review.route) {
                launchSingleTop = true
            }
        }
    }

    val onNavigateToSettings: () -> Unit = {
        onNavigateToTopLevel(TopLevelDestination.Settings)
    }

    // 多选操作栏：仅在知识库列表页且处于多选态时替换导航条（E11 / §4.5）
    val allSelected = memoListState.memos.isNotEmpty() &&
        memoListState.selectedIds.size == memoListState.memos.size
    val selectionBar: (@Composable () -> Unit)? =
        if (currentRoute == Screen.MemoList.route && memoListState.isSelectionMode) {
            {
                SelectionActionBar(
                    actions = listOf(
                        SelectionActionItem(
                            icon = Icons.Default.Delete,
                            label = "删除",
                            enabled = memoListState.selectedIds.isNotEmpty(),
                            onClick = { memoListViewModel.onEvent(MemoListUiEvent.OnBatchDelete) }
                        ),
                        SelectionActionItem(
                            icon = Icons.AutoMirrored.Filled.Label,
                            label = "打标签",
                            enabled = memoListState.selectedIds.isNotEmpty(),
                            onClick = { memoListViewModel.onEvent(MemoListUiEvent.OnOpenTagPicker) }
                        ),
                        SelectionActionItem(
                            icon = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                            label = if (allSelected) "取消全选" else "全选当前结果",
                            enabled = true,
                            onClick = {
                                if (allSelected) {
                                    memoListViewModel.onEvent(MemoListUiEvent.OnClearSelection)
                                } else {
                                    memoListViewModel.onEvent(MemoListUiEvent.OnSelectAll)
                                }
                            }
                        ),
                        SelectionActionItem(
                            icon = Icons.Default.Close,
                            label = "关闭",
                            enabled = true,
                            onClick = { memoListViewModel.onEvent(MemoListUiEvent.OnExitSelectionMode) }
                        )
                    )
                )
            }
        } else {
            null
        }

    AppScaffold(
        windowSizeClass = windowSizeClass,
        currentRoute = currentRoute,
        reviewBadgeCount = reviewBadgeCount,
        onNavigateToTopLevel = onNavigateToTopLevel,
        selectionBar = selectionBar,
        modifier = modifier
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.MemoList.route,
            modifier = Modifier.fillMaxSize(),
            // 一级 Tab 之间切换采用淡入淡出：
            // ① 符合 Tab 语义（Tab 切换并非层级推入）；
            // ② 开销远低于横向 slide，不会与目标页的组合（含 WebView 初始化）叠加造成掉帧。
            // 层级下钻（进入详情页）的横向滑动动画在对应 composable 上单独声明。
            enterTransition = { fadeIn(animationSpec = tween(NAV_FADE_MS)) },
            exitTransition = { fadeOut(animationSpec = tween(NAV_FADE_MS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(NAV_FADE_MS)) },
            popExitTransition = { fadeOut(animationSpec = tween(NAV_FADE_MS)) }
        ) {
            // 知识点列表主页面
            composable(route = Screen.MemoList.route) {
                if (windowSizeClass.useListDetail) {
                    // Expanded：列表-详情双窗格，点击列表项只更新右 Pane
                    MemoListDetailPane(
                        selectedMemoId = selectedMemoId,
                        detailPane = { memoId ->
                            if (memoId == null) {
                                DetailPanePlaceholder()
                            } else {
                                MemoDetailPane(
                                    memoId = memoId,
                                    memoRepository = memoRepository,
                                    reviewRepository = reviewRepository
                                )
                            }
                        },
                        memoListViewModel = memoListViewModel,
                        dashboardViewModel = dashboardViewModel,
                        windowSizeClass = windowSizeClass,
                        onNavigateToReview = onNavigateToReview,
                        onNavigateToSettings = onNavigateToSettings,
                        onSelectMemo = { memoId -> selectedMemoIdRaw = memoId }
                    )
                } else {
                    MemoListScreen(
                        memoListViewModel = memoListViewModel,
                        dashboardViewModel = dashboardViewModel,
                        windowSizeClass = windowSizeClass,
                        onNavigateToReview = onNavigateToReview,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToDetail = { memoId ->
                            navController.navigate(Screen.MemoDetail.createRoute(memoId))
                        }
                    )
                }
            }

            // 知识点全屏完整查看详情页（层级下钻：保留横向滑动语义）
            composable(
                route = Screen.MemoDetail.route,
                arguments = listOf(
                    navArgument(Screen.MemoDetail.ARG_MEMO_ID) {
                        type = NavType.LongType
                    }
                ),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(NAV_SLIDE_MS)
                    )
                },
                exitTransition = { fadeOut(animationSpec = tween(NAV_FADE_MS)) },
                popEnterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(NAV_SLIDE_MS)
                    )
                },
                popExitTransition = { fadeOut(animationSpec = tween(NAV_FADE_MS)) }
            ) { backStackEntry ->
                val memoId = backStackEntry.arguments?.getLong(Screen.MemoDetail.ARG_MEMO_ID) ?: 0L
                val detailViewModel: MemoDetailViewModel = viewModel(
                    key = "memo_detail_$memoId",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MemoDetailViewModel(
                                memoId = memoId,
                                memoRepository = memoRepository,
                                reviewRepository = reviewRepository
                            ) as T
                        }
                    }
                )
                MemoDetailScreen(
                    viewModel = detailViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            // 沉浸式卡片复习流
            composable(route = Screen.Review.route) {
                ReviewScreen(
                    viewModel = reviewViewModel,
                    windowSizeClass = windowSizeClass,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            // 偏好设置页面（一级 Tab，无返回箭头）
            composable(route = Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    windowSizeClass = windowSizeClass,
                    onNavigateToReview = onNavigateToReview,
                    dataSafetyViewModel = dataSafetyViewModel,
                    onNavigateToTrash = {
                        navController.navigate(Screen.Trash.route) { launchSingleTop = true }
                    },
                    crashLogger = crashLogger
                )
            }

            // 回收站（独立下钻页，隐藏导航条）
            composable(
                route = Screen.Trash.route,
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(NAV_SLIDE_MS)
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(NAV_SLIDE_MS)
                    )
                }
            ) {
                TrashScreen(
                    viewModel = trashViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * 双窗格右 Pane 的空态占位：提示用户先在左侧选择知识点。
 */
@Composable
private fun DetailPanePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Dimens.SpaceXXXL)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(Dimens.StateIconSize)
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceXXL))
            Text(
                text = "从左侧选择一条知识点查看详情",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
