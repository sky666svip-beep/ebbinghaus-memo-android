package com.ebbinghaus.memo.ui.scaffold

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.ebbinghaus.memo.ui.navigation.TopLevelDestination

/**
 * 底部导航栏（Compact 断点）
 *
 * 三个一级 Tab：知识库 / 复习 / 设置。复习 Tab 在存在待复习任务时展示数字角标。
 *
 * @param destinations 一级目的地列表
 * @param currentRoute 当前路由，用于高亮选中项
 * @param badgeCount 复习角标计数；<= 0 时隐藏
 * @param onNavigate 选中回调
 * @param modifier 外部修饰器
 */
@Composable
fun AppNavigationBar(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    badgeCount: Int,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        destinations.forEach { destination ->
            val selected = destination.route == currentRoute
            val showBadge = TopLevelDestination.isBadgeTarget(destination) && badgeCount > 0

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    if (showBadge) {
                        BadgedBox(
                            badge = { Badge { Text(text = formatBadgeCount(badgeCount)) } }
                        ) {
                            AppDestinationIcon(destination = destination)
                        }
                    } else {
                        AppDestinationIcon(destination = destination)
                    }
                },
                label = {
                    Text(
                        text = destination.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

/**
 * 侧边导航栏（Medium / Expanded 断点）
 *
 * 与底部导航栏共享同一套目的地与角标逻辑，仅呈现形态不同。
 *
 * @param destinations 一级目的地列表
 * @param currentRoute 当前路由，用于高亮选中项
 * @param badgeCount 复习角标计数；<= 0 时隐藏
 * @param onNavigate 选中回调
 * @param modifier 外部修饰器
 */
@Composable
fun AppNavigationRail(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    badgeCount: Int,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        destinations.forEach { destination ->
            val selected = destination.route == currentRoute
            val showBadge = TopLevelDestination.isBadgeTarget(destination) && badgeCount > 0

            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    if (showBadge) {
                        BadgedBox(
                            badge = { Badge { Text(text = formatBadgeCount(badgeCount)) } }
                        ) {
                            AppDestinationIcon(destination = destination)
                        }
                    } else {
                        AppDestinationIcon(destination = destination)
                    }
                },
                label = {
                    Text(
                        text = destination.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

/**
 * 目的地图标统一渲染
 */
@Composable
private fun AppDestinationIcon(destination: TopLevelDestination) {
    Icon(
        imageVector = destination.icon,
        contentDescription = destination.label
    )
}

/**
 * 角标数字格式化：超过 99 显示 "99+"，避免 Badge 被撑破。
 */
internal fun formatBadgeCount(count: Int): String = if (count > 99) "99+" else count.toString()
