package com.ebbinghaus.memo.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 应用一级导航目的地定义
 *
 * 与底部 `NavigationBar` / 侧边 `NavigationRail` 一一对应。
 * 「复习」同样是常驻一级 Tab（而非沉浸式页面），以保证待复习量角标常驻可见。
 * 仅二级页（知识点详情）不属于一级目的地，不显示导航条。
 *
 * @property route 导航路由，取自 [Screen]
 * @property label 中文展示文案
 * @property icon 导航图标
 */
sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {

    /** 知识库（知识点列表） */
    data object Library : TopLevelDestination(
        route = Screen.MemoList.route,
        label = "知识库",
        icon = Icons.AutoMirrored.Filled.MenuBook
    )

    /** 今日复习 */
    data object Review : TopLevelDestination(
        route = Screen.Review.route,
        label = "复习",
        icon = Icons.AutoMirrored.Filled.ListAlt
    )

    /** 偏好设置 */
    data object Settings : TopLevelDestination(
        route = Screen.Settings.route,
        label = "设置",
        icon = Icons.Default.Settings
    )

    companion object {

        /**
         * 全部一级目的地，顺序即导航条展示顺序。
         *
         * **必须惰性求值**：与 [Screen] 完全同型的 JVM 类静态初始化循环（JVM Spec §5.5）。
         * 若首个触碰点是 `Library` / `Review` / `Settings` 中的任意一个，JVM 会先把该嵌套
         * `data object` 标记为「正在被当前线程初始化」再初始化父类 `TopLevelDestination`，
         * 随后本 `Companion.<clinit>` 读到的就是**尚未赋值的 `INSTANCE`（null）**，
         * 结果是 `entries` 变成 `[null, Review, Settings]` 这类**静默污染**（不抛异常，
         * 但 `fromRoute` 会因 `it.route` 抛 NPE、导航条少一个 Tab）。
         *
         * 这正是历史上 Robolectric 下 `NoClassDefFoundError: Could not initialize class
         * TopLevelDestination` 与「导航测试连带失败」的**真正成因** —— 此前被误判为
         * 「data object 在类静态初始化阶段引用 Compose 图标」，详见
         * `design/CRASH_INVESTIGATION.md` 的「根因定案与修复」一节。
         *
         * **线程安全**：同 [Screen]，采用 [LazyThreadSafetyMode.PUBLICATION]。
         */
        val entries: List<TopLevelDestination> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            listOf(Library, Review, Settings)
        }

        /**
         * 依据路由反查一级目的地；非一级路由返回 null。
         *
         * @param route 当前回退栈路由
         */
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }

        /** 该目的地是否承载复习角标 */
        fun isBadgeTarget(destination: TopLevelDestination): Boolean =
            destination is Review
    }
}
