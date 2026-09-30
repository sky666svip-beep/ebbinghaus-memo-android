package com.ebbinghaus.memo.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass as OfficialHeightClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass as OfficialWidthClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.ebbinghaus.memo.ui.theme.Dimens

/**
 * 窗口宽度断点枚举
 *
 * 与 Material 3 WindowSizeClass 官方阈值保持一致：
 * - Compact：< 600dp（手机竖屏）
 * - Medium：600 ~ 839dp（折叠屏展开、小平板竖屏）
 * - Expanded：≥ 840dp（平板横屏、桌面窗口化）
 */
enum class WindowWidthSizeClass {
    Compact,
    Medium,
    Expanded
}

/**
 * 窗口高度断点枚举
 *
 * - Compact：< 480dp
 * - Medium：480 ~ 899dp
 * - Expanded：≥ 900dp
 */
enum class WindowHeightSizeClass {
    Compact,
    Medium,
    Expanded
}

/**
 * 窗口尺寸类别聚合结果
 *
 * 全项目断点判定的唯一入口，任何位置都不得再直接比较 `screenWidthDp`。
 *
 * @property widthSizeClass 宽度断点
 * @property heightSizeClass 高度断点
 */
data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass
) {

    /** 是否使用侧边 NavigationRail（Medium 及以上） */
    val useRail: Boolean
        get() = widthSizeClass != WindowWidthSizeClass.Compact

    /** 是否启用列表-详情双窗格（仅 Expanded） */
    val useListDetail: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.Expanded

    /**
     * 内容区最大宽度；Compact 返回 [Dp.Unspecified] 表示不限宽（满宽铺展）。
     */
    val contentMaxWidth: Dp
        get() = when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> Dp.Unspecified
            WindowWidthSizeClass.Medium -> Dimens.ContentMaxWidthMedium
            WindowWidthSizeClass.Expanded -> Dimens.ContentMaxWidthExpanded
        }
}

/**
 * 查找当前 Context 所依附的 Activity。
 *
 * [calculateWindowSizeClass] 需要 Activity 才能读取真实的窗口度量（含折叠屏铰链、
 * 多窗口与自由缩放窗口）。若当前处于 Preview / 非 Activity 环境则返回 null，
 * 由调用方降级为基于 [LocalConfiguration] 的纯 dp 判定。
 */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 计算当前窗口尺寸类别。
 *
 * **优先采用官方 [calculateWindowSizeClass]**：它基于真实窗口度量，能正确处理折叠屏铰链、
 * 分屏多窗口与 Android 桌面窗口化缩放，优于单纯比较 `screenWidthDp`。
 * 若取不到 Activity（如 Compose Preview），则降级为 [widthSizeClassOf] / [heightSizeClassOf]
 * 的纯 dp 判定，阈值与官方一致，保证行为等价。
 *
 * 在旋转、分屏、窗口缩放时均会触发 recomposition。
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // calculateWindowSizeClass 是 @Composable 且内部自带缓存，必须在 Composable 上下文直接调用，
    // 不可包进 remember {} 的 lambda（否则报「@Composable invocations can only happen from ...」）。
    val official = if (activity != null) calculateWindowSizeClass(activity) else null

    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        official?.widthSizeClass,
        official?.heightSizeClass
    ) {
        if (official != null) {
            WindowSizeClass(
                widthSizeClass = when (official.widthSizeClass) {
                    OfficialWidthClass.Compact -> WindowWidthSizeClass.Compact
                    OfficialWidthClass.Medium -> WindowWidthSizeClass.Medium
                    else -> WindowWidthSizeClass.Expanded
                },
                heightSizeClass = when (official.heightSizeClass) {
                    OfficialHeightClass.Compact -> WindowHeightSizeClass.Compact
                    OfficialHeightClass.Medium -> WindowHeightSizeClass.Medium
                    else -> WindowHeightSizeClass.Expanded
                }
            )
        } else {
            // 降级路径：无 Activity 场景（Preview），阈值与官方一致
            WindowSizeClass(
                widthSizeClass = widthSizeClassOf(configuration.screenWidthDp),
                heightSizeClass = heightSizeClassOf(configuration.screenHeightDp)
            )
        }
    }
}

/**
 * 是否为「横屏 + 宽度 Compact」场景（如 800×360），此类场景竖向空间不足，需改左右双列布局。
 */
@Composable
fun rememberIsLandscapeCompact(): Boolean {
    val configuration = LocalConfiguration.current
    val windowSizeClass = rememberWindowSizeClass()
    return remember(configuration.orientation, windowSizeClass) {
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
        isLandscape && windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    }
}

/**
 * 依据宽度 dp 值映射宽度断点。
 *
 * @param widthDp 可用窗口宽度（dp）
 */
internal fun widthSizeClassOf(widthDp: Int): WindowWidthSizeClass = when {
    widthDp < 600 -> WindowWidthSizeClass.Compact
    widthDp < 840 -> WindowWidthSizeClass.Medium
    else -> WindowWidthSizeClass.Expanded
}

/**
 * 依据高度 dp 值映射高度断点。
 *
 * @param heightDp 可用窗口高度（dp）
 */
internal fun heightSizeClassOf(heightDp: Int): WindowHeightSizeClass = when {
    heightDp < 480 -> WindowHeightSizeClass.Compact
    heightDp < 900 -> WindowHeightSizeClass.Medium
    else -> WindowHeightSizeClass.Expanded
}
