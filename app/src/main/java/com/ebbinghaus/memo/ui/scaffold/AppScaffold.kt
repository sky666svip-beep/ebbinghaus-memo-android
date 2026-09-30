package com.ebbinghaus.memo.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ebbinghaus.memo.ui.navigation.Screen
import com.ebbinghaus.memo.ui.navigation.TopLevelDestination
import com.ebbinghaus.memo.ui.theme.Dimens
import com.ebbinghaus.memo.ui.util.WindowSizeClass

/**
 * 全局唯一 Snackbar 宿主状态
 *
 * 由 [AppScaffold] 在 NavHost 之上持有，不随导航目的地销毁。
 * 所有 Screen 通过本 CompositionLocal 取用，**禁止**自建 `SnackbarHost` 或第二个 Scaffold 承载 Snackbar。
 */
val LocalSnackbarHostState = compositionLocalOf { SnackbarHostState() }

/**
 * 应用级脚手架
 *
 * 职责：
 * 1. 持有全局唯一 `SnackbarHostState` 并通过 [LocalSnackbarHostState] 下发；
 * 2. 按 `WindowSizeClass` 在底部导航栏与侧边导航栏之间切换（Compact→Bar，Medium/Expanded→Rail）；
 * 3. 仅在**一级 Tab 路由**显示导航条。一级 Tab 共三个：知识库 / 复习 / 设置；
 * 4. 多选态（E11）下，可选的 [selectionBar] 插槽**替换**导航条：Compact 替换底部 `NavigationBar`，
 *    Rail 断点隐藏 `NavigationRail` 并置于内容区顶部（`TopAppBar` 下方）。
 *
 * 说明：本容器不使用 `Scaffold`（改为自绘导航条 + SnackbarHost），因而不消费系统栏 insets，
 * 全部 insets 交由各 Screen 自身的 Scaffold 处理，避免嵌套 Scaffold 导致的双重 insets 累加。
 *
 * @param windowSizeClass 当前窗口尺寸类别
 * @param currentRoute 当前回退栈路由
 * @param reviewBadgeCount 复习 Tab 角标计数
 * @param onNavigateToTopLevel 一级 Tab 切换回调
 * @param selectionBar 多选操作栏插槽；非 null 时进入多选态并替换导航条
 * @param modifier 外部修饰器
 * @param content 内容区（内含 NavHost）
 */
@Composable
fun AppScaffold(
    windowSizeClass: WindowSizeClass,
    currentRoute: String?,
    reviewBadgeCount: Int,
    onNavigateToTopLevel: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    selectionBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // 仅一级 Tab 显示导航条（知识库 / 复习 / 设置）；详情页为沉浸式下钻页，隐藏导航条
    val showNavigation = Screen.isTopLevel(currentRoute)
    val useRail = windowSizeClass.useRail
    val inSelection = selectionBar != null

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Row(modifier = modifier.fillMaxSize()) {
            if (showNavigation && useRail && !inSelection) {
                AppNavigationRail(
                    destinations = TopLevelDestination.entries,
                    currentRoute = currentRoute,
                    badgeCount = reviewBadgeCount,
                    onNavigate = onNavigateToTopLevel,
                    modifier = Modifier.navigationBarsPadding()
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                // 一级页面：为底部导航栏预留安全空间，避免内容被遮挡
                val contentModifier = if (showNavigation && !useRail) {
                    Modifier.padding(bottom = Dimens.BottomBarHeight)
                } else {
                    Modifier
                }

                if (showNavigation && useRail && inSelection) {
                    // Rail 断点：操作栏置于内容区顶部（TopAppBar 之下）
                    Column(modifier = Modifier.fillMaxSize()) {
                        selectionBar?.invoke()
                        Box(modifier = contentModifier) {
                            content()
                        }
                    }
                } else {
                    Box(modifier = contentModifier) {
                        content()
                    }
                }

                if (showNavigation && !useRail) {
                    if (inSelection) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                        ) {
                            selectionBar?.invoke()
                        }
                    } else {
                        AppNavigationBar(
                            destinations = TopLevelDestination.entries,
                            currentRoute = currentRoute,
                            badgeCount = reviewBadgeCount,
                            onNavigate = onNavigateToTopLevel,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                        )
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(
                            bottom = if (showNavigation && !useRail) Dimens.BottomBarHeight else 0.dp,
                            start = Dimens.ScreenPadding,
                            end = Dimens.ScreenPadding
                        )
                )
            }
        }
    }
}
