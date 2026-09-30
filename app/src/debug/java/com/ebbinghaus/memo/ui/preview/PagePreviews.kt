package com.ebbinghaus.memo.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import com.ebbinghaus.memo.ui.dashboard.DashboardViewModel
import com.ebbinghaus.memo.ui.detail.MemoDetailScreen
import com.ebbinghaus.memo.ui.detail.MemoDetailViewModel
import com.ebbinghaus.memo.ui.memolist.MemoListScreen
import com.ebbinghaus.memo.ui.memolist.MemoListViewModel
import com.ebbinghaus.memo.ui.review.ReviewScreen
import com.ebbinghaus.memo.ui.review.ReviewViewModel
import com.ebbinghaus.memo.ui.settings.SettingsScreen
import com.ebbinghaus.memo.ui.settings.SettingsViewModel
import com.ebbinghaus.memo.ui.theme.EbbinghausTheme
import com.ebbinghaus.memo.ui.util.WindowHeightSizeClass
import com.ebbinghaus.memo.ui.util.WindowSizeClass
import com.ebbinghaus.memo.ui.util.WindowWidthSizeClass

/**
 * 页面级三档响应式 Preview（Compact / Medium / Expanded）。
 *
 * 依赖 [PreviewFakes] 提供的内存仓储构造真实 ViewModel，从而无需改造生产代码的
 * ViewModel 形参即可预览真实页面。仅在 `debug` 变体编译。
 *
 * 对应设计规范验收口径：「所有新增界面必须提供 Compact / Medium / Expanded 三档预览」。
 */

// ---------------------------------------------------------------------------
// 断点尺寸常量：与 WindowSizeClass.widthSizeClassOf 的阈值保持一致
// ---------------------------------------------------------------------------

private const val COMPACT_W = 360
private const val MEDIUM_W = 700
private const val EXPANDED_W = 1000

private fun windowSizeOf(width: WindowWidthSizeClass) = WindowSizeClass(
    widthSizeClass = width,
    heightSizeClass = WindowHeightSizeClass.Medium
)

/** 统一注入主题，并把断点显式传入页面（Preview 无 Activity，不依赖 calculateWindowSizeClass） */
@Composable
private fun PreviewHost(
    width: WindowWidthSizeClass,
    content: @Composable (WindowSizeClass) -> Unit
) {
    EbbinghausTheme {
        content(windowSizeOf(width))
    }
}

// ---------------------------------------------------------------------------
// 1. 知识库主页 MemoListScreen
// ---------------------------------------------------------------------------

@Composable
private fun MemoListPreview(width: WindowWidthSizeClass) {
    PreviewHost(width) { windowSizeClass ->
        val memoRepo = remember { PreviewMemoRepository(previewMemos()) }
        val reviewRepo = remember { PreviewReviewRepository(previewDueTasks()) }
        val settingsRepo = remember { PreviewSettingsRepository(UserSettingsEntity(dailyReviewLimit = 20)) }
        val memoViewModel = remember { MemoListViewModel(memoRepo) }
        val dashboardViewModel = remember { DashboardViewModel(reviewRepo, settingsRepo) }

        MemoListScreen(
            memoListViewModel = memoViewModel,
            dashboardViewModel = dashboardViewModel,
            onNavigateToReview = {},
            onNavigateToDetail = {},
            windowSizeClass = windowSizeClass,
            onNavigateToSettings = {}
        )
    }
}

@Preview(name = "知识库 · Compact", widthDp = COMPACT_W, heightDp = 800, showBackground = true)
@Composable
private fun MemoListCompactPreview() = MemoListPreview(WindowWidthSizeClass.Compact)

@Preview(name = "知识库 · Medium", widthDp = MEDIUM_W, heightDp = 900, showBackground = true)
@Composable
private fun MemoListMediumPreview() = MemoListPreview(WindowWidthSizeClass.Medium)

@Preview(name = "知识库 · Expanded", widthDp = EXPANDED_W, heightDp = 800, showBackground = true)
@Composable
private fun MemoListExpandedPreview() = MemoListPreview(WindowWidthSizeClass.Expanded)

// ---------------------------------------------------------------------------
// 2. 知识点详情 MemoDetailScreen
// ---------------------------------------------------------------------------

@Composable
private fun MemoDetailPreview(width: WindowWidthSizeClass) {
    PreviewHost(width) { _ ->
        val memos = previewMemos()
        val memoRepo = remember { PreviewMemoRepository(memos) }
        val reviewRepo = remember { PreviewReviewRepository(previewDueTasks()) }
        val viewModel = remember { MemoDetailViewModel(memoId = memos.first().id, memoRepository = memoRepo, reviewRepository = reviewRepo) }

        MemoDetailScreen(viewModel = viewModel, onNavigateBack = {})
    }
}

@Preview(name = "详情 · Compact", widthDp = COMPACT_W, heightDp = 900, showBackground = true)
@Composable
private fun MemoDetailCompactPreview() = MemoDetailPreview(WindowWidthSizeClass.Compact)

@Preview(name = "详情 · Medium", widthDp = MEDIUM_W, heightDp = 900, showBackground = true)
@Composable
private fun MemoDetailMediumPreview() = MemoDetailPreview(WindowWidthSizeClass.Medium)

@Preview(name = "详情 · Expanded", widthDp = EXPANDED_W, heightDp = 800, showBackground = true)
@Composable
private fun MemoDetailExpandedPreview() = MemoDetailPreview(WindowWidthSizeClass.Expanded)

// ---------------------------------------------------------------------------
// 3. 沉浸式复习流 ReviewScreen
// ---------------------------------------------------------------------------

@Composable
private fun ReviewPreview(width: WindowWidthSizeClass) {
    PreviewHost(width) { windowSizeClass ->
        val memoRepo = remember { PreviewMemoRepository(previewMemos()) }
        val reviewRepo = remember { PreviewReviewRepository(previewDueTasks()) }
        val settingsRepo = remember { PreviewSettingsRepository(UserSettingsEntity(dailyReviewLimit = 20)) }
        val viewModel = remember {
            ReviewViewModel(
                reviewRepository = reviewRepo,
                settingsRepository = settingsRepo,
                memoRepository = memoRepo
            )
        }

        ReviewScreen(
            viewModel = viewModel,
            windowSizeClass = windowSizeClass,
            onNavigateBack = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(name = "复习 · Compact", widthDp = COMPACT_W, heightDp = 800, showBackground = true)
@Composable
private fun ReviewCompactPreview() = ReviewPreview(WindowWidthSizeClass.Compact)

@Preview(name = "复习 · Medium", widthDp = MEDIUM_W, heightDp = 900, showBackground = true)
@Composable
private fun ReviewMediumPreview() = ReviewPreview(WindowWidthSizeClass.Medium)

@Preview(name = "复习 · Expanded", widthDp = EXPANDED_W, heightDp = 800, showBackground = true)
@Composable
private fun ReviewExpandedPreview() = ReviewPreview(WindowWidthSizeClass.Expanded)

// ---------------------------------------------------------------------------
// 4. 设置 SettingsScreen（含「上限为 0 已停用」态）
// ---------------------------------------------------------------------------

@Composable
private fun SettingsPreview(width: WindowWidthSizeClass, dailyLimit: Int) {
    PreviewHost(width) { windowSizeClass ->
        val settingsRepo = remember { PreviewSettingsRepository(UserSettingsEntity(dailyReviewLimit = dailyLimit)) }
        val viewModel = remember { SettingsViewModel(settingsRepo) }

        SettingsScreen(
            viewModel = viewModel,
            windowSizeClass = windowSizeClass,
            onNavigateToReview = {}
        )
    }
}

@Preview(name = "设置 · Compact", widthDp = COMPACT_W, heightDp = 900, showBackground = true)
@Composable
private fun SettingsCompactPreview() = SettingsPreview(WindowWidthSizeClass.Compact, 20)

@Preview(name = "设置 · Medium", widthDp = MEDIUM_W, heightDp = 900, showBackground = true)
@Composable
private fun SettingsMediumPreview() = SettingsPreview(WindowWidthSizeClass.Medium, 20)

@Preview(name = "设置 · Expanded", widthDp = EXPANDED_W, heightDp = 800, showBackground = true)
@Composable
private fun SettingsExpandedPreview() = SettingsPreview(WindowWidthSizeClass.Expanded, 20)

@Preview(name = "设置 · Compact（上限 0 已停用）", widthDp = COMPACT_W, heightDp = 900, showBackground = true)
@Composable
private fun SettingsCompactDisabledPreview() = SettingsPreview(WindowWidthSizeClass.Compact, 0)
