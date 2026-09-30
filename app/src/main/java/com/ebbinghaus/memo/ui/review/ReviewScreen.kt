package com.ebbinghaus.memo.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ebbinghaus.memo.core.model.ReviewRating
import com.ebbinghaus.memo.ui.component.ErrorState
import com.ebbinghaus.memo.ui.scaffold.LocalSnackbarHostState
import com.ebbinghaus.memo.ui.theme.Dimens
import com.ebbinghaus.memo.ui.theme.ForgetRed
import com.ebbinghaus.memo.ui.theme.FuzzyOrange
import com.ebbinghaus.memo.ui.theme.RememberGreen
import com.ebbinghaus.memo.ui.theme.ReviewedTeal
import com.ebbinghaus.memo.ui.theme.SkipBlue
import com.ebbinghaus.memo.ui.util.WindowSizeClass
import com.ebbinghaus.memo.ui.util.rememberIsLandscapeCompact

/**
 * 五类评级对应的无障碍「后果说明」语义文案。
 *
 * 文案必须与 `EbbinghausScheduler.calculateNextReview` 的实际档位流转严格一致，
 * 否则会通过 `contentDescription` 系统性误导 TalkBack 用户（P1-1）。
 * 声明为 `internal` 以便 JVM 单测守护「文案 ↔ 算法」一致性。
 */
internal val RATING_SEMANTICS: Map<ReviewRating, String> = mapOf(
    ReviewRating.FORGET to "忘记：档位回退一档，间隔缩短后再复习",
    ReviewRating.VAGUE to "模糊：保持当前档位，按当前间隔再复习一次",
    ReviewRating.REMEMBER to "记住：前进一档，间隔拉长后再复习；第 6 档后进入 60 天长周期",
    ReviewRating.DEFAULT_REVIEWED to "已复习：按模糊处理，保持当前档位",
    ReviewRating.SKIP to "跳过：不改变档位，顺延到明天再复习"
)

/**
 * 沉浸式卡片式复习主界面
 *
 * 提供顶部进度条、卡片翻转与即时笔记编辑、底部 5 类交互评级按键以及打卡庆祝页。
 * 按 [WindowSizeClass] 限宽居中；横屏 Compact 切换为左右双列，避免竖向空间不足。
 *
 * @param onNavigateToSettings 直达设置页（供 `dailyLimit == 0` 暂停态提供恢复入口，P2-9）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    windowSizeClass: WindowSizeClass,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val isLandscapeCompact = rememberIsLandscapeCompact()
    // 加载失败文案：非 null 时展示可重试错误态（避免把失败静默渲染成「全部达成」）
    val loadError = uiState.loadError

    // 一次性消息：笔记自动保存等提示
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ReviewEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "每日复习",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回主页"
                        )
                    }
                },
                actions = {
                    if (!uiState.isCompleted && uiState.totalBatchCount > 0) {
                        Text(
                            text = "${(uiState.currentIndex + 1).coerceAtMost(uiState.totalBatchCount)} / ${uiState.totalBatchCount}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = Dimens.ScreenPadding),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 顶部进度指示器
            if (!uiState.isCompleted && uiState.totalBatchCount > 0) {
                val progress = (uiState.currentIndex.toFloat() / uiState.totalBatchCount).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // 加载失败分支（第 2 轮加固）：优先于「完成/暂停」分支，
            // 保证失败时用户看到的是**可重试的错误态**，而非误以为「今日复习全部达成」。
            if (loadError != null) {
                ErrorState(
                    message = loadError,
                    onRetry = { viewModel.onEvent(ReviewUiEvent.OnRestartSession) }
                )
            } else if (uiState.isCompleted || uiState.reviewQueue.isEmpty()) {
                // 复习结束分支（P2-9）：
                // - `dailyLimit <= 0` → 暂停页（用户主动设置，非「达成」）；
                // - `dailyLimit > 0 且队列空` → 原「全部达成」庆祝页（不误伤）。
                if (uiState.dailyLimit <= 0) {
                    ReviewPausedView(
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateBack = onNavigateBack
                    )
                } else {
                    ReviewCompletedView(
                        totalReviewed = uiState.totalBatchCount,
                        deferredCount = uiState.deferredCount,
                        onNavigateBack = onNavigateBack
                    )
                }
            } else {
                // 复习流中
                val currentItem = uiState.currentItem
                if (currentItem != null) {
                    if (isLandscapeCompact) {
                        // 横屏 Compact：左卡片 / 右按钮双列，避免竖向空间不足
                        ReviewLandscapeLayout(
                            cardContent = {
                                ReviewCardComponent(
                                    item = currentItem,
                                    isDetailExpanded = uiState.isDetailExpanded,
                                    isEditingNotes = uiState.isEditingNotes,
                                    notesDraft = uiState.notesDraft,
                                    onToggleDetails = { viewModel.onEvent(ReviewUiEvent.OnToggleDetails) },
                                    onStartEditNotes = { viewModel.onEvent(ReviewUiEvent.OnStartEditNotes) },
                                    onCancelEditNotes = { viewModel.onEvent(ReviewUiEvent.OnCancelEditNotes) },
                                    onNotesDraftChanged = {
                                        viewModel.onEvent(ReviewUiEvent.OnNotesDraftChanged(it))
                                    },
                                    onSaveNotes = { viewModel.onEvent(ReviewUiEvent.OnSaveNotesDraft(it)) }
                                )
                            },
                            actionsContent = {
                                ReviewActionButtonsSection(
                                    onRating = { rating ->
                                        viewModel.onEvent(ReviewUiEvent.OnSubmitRating(rating))
                                    }
                                )
                            }
                        )
                    } else {
                        // 布局要点（消除展开/收起抖动）：
                        // 卡片区独占「剩余空间」并在内部滚动，操作按钮区固定贴底、不参与滚动。
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthInLimited(windowSizeClass.contentMaxWidth)
                                ) {
                                    ReviewCardComponent(
                                        item = currentItem,
                                        isDetailExpanded = uiState.isDetailExpanded,
                                        isEditingNotes = uiState.isEditingNotes,
                                        notesDraft = uiState.notesDraft,
                                        onToggleDetails = { viewModel.onEvent(ReviewUiEvent.OnToggleDetails) },
                                        onStartEditNotes = { viewModel.onEvent(ReviewUiEvent.OnStartEditNotes) },
                                        onCancelEditNotes = { viewModel.onEvent(ReviewUiEvent.OnCancelEditNotes) },
                                        onNotesDraftChanged = {
                                            viewModel.onEvent(ReviewUiEvent.OnNotesDraftChanged(it))
                                        },
                                        onSaveNotes = { viewModel.onEvent(ReviewUiEvent.OnSaveNotesDraft(it)) }
                                    )
                                }
                            }

                            // 底部 5 类操作按钮区（固定贴底，限宽居中）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthInLimited(windowSizeClass.contentMaxWidth),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                ReviewActionButtonsSection(
                                    onRating = { rating ->
                                        viewModel.onEvent(ReviewUiEvent.OnSubmitRating(rating))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 横屏 Compact 左右双列布局：左半卡片内容，右半笔记/按钮区。
 */
@Composable
private fun ReviewLandscapeLayout(
    cardContent: @Composable () -> Unit,
    actionsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = Dimens.SpaceS),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            cardContent()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.BottomCenter
        ) {
            actionsContent()
        }
    }
}

/**
 * 按断点限宽：Compact 时 [maxWidth] 为 [Dp.Unspecified]，不做限制。
 */
private fun Modifier.widthInLimited(maxWidth: Dp): Modifier =
    if (maxWidth == Dp.Unspecified) this else this.widthIn(max = maxWidth)

/**
 * 底部 5 类复习交互评级按钮区
 *
 * 每个按钮均附带「后果说明」无障碍语义，便于 TalkBack 用户理解评级影响。
 */
@Composable
private fun ReviewActionButtonsSection(
    onRating: (ReviewRating) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceM),
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)
    ) {
        // 第一行：三大核心评级（忘记、模糊、记住）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
        ) {
            // 忘记 (FORGET)
            RatingButton(
                text = "忘记",
                containerColor = ForgetRed,
                icon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                semanticsDescription = RATING_SEMANTICS.getValue(ReviewRating.FORGET),
                onClick = { onRating(ReviewRating.FORGET) },
                modifier = Modifier.weight(1f)
            )

            // 模糊 (VAGUE)
            RatingButton(
                text = "模糊",
                containerColor = FuzzyOrange,
                icon = { Icon(Icons.Default.QuestionMark, contentDescription = null, modifier = Modifier.size(16.dp)) },
                semanticsDescription = RATING_SEMANTICS.getValue(ReviewRating.VAGUE),
                onClick = { onRating(ReviewRating.VAGUE) },
                modifier = Modifier.weight(1f)
            )

            // 记住 (REMEMBER)
            RatingButton(
                text = "记住",
                containerColor = RememberGreen,
                icon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                semanticsDescription = RATING_SEMANTICS.getValue(ReviewRating.REMEMBER),
                onClick = { onRating(ReviewRating.REMEMBER) },
                modifier = Modifier.weight(1f)
            )
        }

        // 第二行：缺省操作与跳过机制
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
        ) {
            // 已复习（缺省操作：默认等价于“模糊”）
            OutlinedButton(
                onClick = { onRating(ReviewRating.DEFAULT_REVIEWED) },
                modifier = Modifier
                    .weight(1.2f)
                    .heightIn(min = Dimens.TouchTarget)
                    .semantics {
                        contentDescription = RATING_SEMANTICS.getValue(ReviewRating.DEFAULT_REVIEWED)
                    },
                shape = RoundedCornerShape(Dimens.RadiusButton)
            ) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = null,
                    tint = ReviewedTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                Text(
                    text = "已复习 (默认模糊)",
                    color = ReviewedTeal,
                    fontWeight = FontWeight.Medium
                )
            }

            // 跳过（不改档位，顺延1天）
            OutlinedButton(
                onClick = { onRating(ReviewRating.SKIP) },
                modifier = Modifier
                    .weight(0.8f)
                    .heightIn(min = Dimens.TouchTarget)
                    .semantics {
                        contentDescription = RATING_SEMANTICS.getValue(ReviewRating.SKIP)
                    },
                shape = RoundedCornerShape(Dimens.RadiusButton)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = null,
                    tint = SkipBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                Text(
                    text = "跳过",
                    color = SkipBlue,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 实心评级按钮统一封装，保证 48dp 触控目标与无障碍语义一致。
 */
@Composable
private fun RatingButton(
    text: String,
    containerColor: Color,
    semanticsDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = Dimens.TouchTarget)
            .semantics { contentDescription = semanticsDescription },
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(Dimens.RadiusButton)
    ) {
        icon()
        Spacer(modifier = Modifier.width(Dimens.SpaceXS))
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

/**
 * 今日复习完成打卡庆祝视图
 *
 * @param totalReviewed 今日已完成复习数量
 * @param deferredCount 顺延至明日的数量（状态矩阵 S5 在完成页的复用）
 * @param onNavigateBack 返回知识库
 */
@Composable
private fun ReviewCompletedView(
    totalReviewed: Int,
    deferredCount: Int,
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SpaceXXL),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Celebration,
                contentDescription = "完成庆祝",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.StateIconSize)
            )

            Spacer(modifier = Modifier.height(Dimens.SpaceXXL))

            Text(
                text = "🎉 今日复习全部达成！",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Dimens.ItemSpacing))

            Text(
                text = if (totalReviewed > 0) {
                    "今日已成功巩固 $totalReviewed 条知识点。\n遵循艾宾浩斯曲线，记忆已注入长效固化区。"
                } else {
                    "今日暂无到期待复习的知识点，去知识库添加新的知识吧！"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
            )

            if (deferredCount > 0) {
                Spacer(modifier = Modifier.height(Dimens.SpaceM))
                Text(
                    text = "另有 $deferredCount 条已自动顺延至明日，继续保持！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceXXXL))

            Button(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(Dimens.TouchTarget)
            ) {
                Text(
                    text = "返回知识库主页",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * 复习暂停视图（P2-9）：`dailyLimit == 0` 时展示。
 *
 * 语义与「全部达成」严格区分——这是用户主动设置的结果，并提供直达设置页的恢复入口。
 * 文案不含「逾期 / 失败 / 落后」（文案红线 §8-12）。
 *
 * @param onNavigateToSettings 直达设置页（调整每日上限）
 * @param onNavigateBack 返回知识库
 */
@Composable
private fun ReviewPausedView(
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.SpaceXXL),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.StateIconSize)
            )

            Spacer(modifier = Modifier.height(Dimens.SpaceXXL))

            Text(
                text = "今日已按设置暂停复习",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Dimens.ItemSpacing))

            Text(
                text = "当前每日复习上限为 0，可在设置中调整上限后随时恢复。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
            )

            Spacer(modifier = Modifier.height(Dimens.SpaceXXXL))

            Button(
                onClick = onNavigateToSettings,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(Dimens.TouchTarget)
            ) {
                Text(
                    text = "去设置",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceM))

            OutlinedButton(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(Dimens.TouchTarget)
            ) {
                Text(
                    text = "返回知识库主页",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
