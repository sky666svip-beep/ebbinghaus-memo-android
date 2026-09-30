package com.ebbinghaus.memo.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局尺寸 Token 集合
 *
 * 所有新建代码一律引用本对象中的常量，禁止在 Composable 内散落裸数字尺寸，
 * 以保证响应式改造时只需在一处调整。
 */
object Dimens {

    // region 间距
    val SpaceXS = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXL = 20.dp
    val SpaceXXL = 24.dp
    val SpaceXXXL = 32.dp
    // endregion

    // region 圆角
    val RadiusCard = 16.dp
    val RadiusReviewCard = 20.dp
    val RadiusButton = 12.dp
    val RadiusChip = 8.dp
    val RadiusFab = 16.dp
    val RadiusDialog = 28.dp
    // endregion

    // region 交互
    /** 所有可点元素的最小触控区边长 */
    val TouchTarget = 48.dp
    /** 页面左右安全边距 */
    val ScreenPadding = 16.dp
    /** 列表项之间的间距 */
    val ItemSpacing = 10.dp
    /** 底部导航栏高度（Material 3 默认 80dp），用于为内容预留安全区 */
    val BottomBarHeight = 80.dp
    // endregion

    // region 响应式
    /** Medium 断点内容区最大宽度 */
    val ContentMaxWidthMedium = 600.dp
    /** Expanded 断点内容区最大宽度 */
    val ContentMaxWidthExpanded = 720.dp
    /** 双窗格左 Pane 固定宽度 */
    val ListPaneWidth = 360.dp
    /** NavigationRail 宽度 */
    val RailWidth = 80.dp
    /** 网格布局单格最小宽度 */
    val GridMinCell = 320.dp
    /** 宽屏弹窗最大宽度 */
    val DialogMaxWidth = 480.dp
    // endregion

    // region 骨架屏与插画
    /** 空/错误状态插画图标边长 */
    val StateIconSize = 72.dp
    /** 骨架屏占位条高度 */
    val SkeletonLineHeight = 14.dp
    /** 骨架屏占位块圆角 */
    val RadiusSkeleton = 8.dp
    // endregion
}
