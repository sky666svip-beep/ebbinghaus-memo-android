package com.ebbinghaus.memo.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ebbinghaus.memo.ui.theme.Dimens

/**
 * 知识点列表页顶部 Hero Banner 概览看板
 *
 * 三态分支：
 * - **态 C（停用）**：`dailyLimit <= 0` → 提示已暂停，并提供「去设置」链接；
 * - **态 B（清空）**：`dueTodayCount == 0` → 提示今日已全部搞定；
 * - **态 A（待复习）**：其余情况 → 展示今日待复习量 + 立即开始按钮 + 顺延副文案（S5）。
 *
 * @param dueTodayCount 今日待复习数量
 * @param deferredCount 因超出每日上限而顺延至次日的数量
 * @param dailyLimit 每日复习上限
 * @param onNavigateToReview 一键直达复习页
 * @param onNavigateToSettings 「去设置」链接回调；为 null 时隐藏该链接
 * @param modifier 外部修饰器
 */
@Composable
fun DashboardBanner(
    dueTodayCount: Int,
    deferredCount: Int,
    dailyLimit: Int,
    onNavigateToReview: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val horizontalPadding = Modifier.padding(
        horizontal = Dimens.ScreenPadding,
        vertical = Dimens.SpaceS
    )

    if (dailyLimit <= 0) {
        // 态 C：每日上限设为 0，复习功能已停用
        Card(
            modifier = modifier
                .fillMaxWidth()
                .then(horizontalPadding),
            shape = RoundedCornerShape(Dimens.RadiusCard),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceL),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "复习已暂停",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(Dimens.SpaceXXXL)
                )
                Spacer(modifier = Modifier.width(Dimens.SpaceM))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "今日复习已暂停",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "每日复习上限已设为 0 条，可在设置中调整",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (onNavigateToSettings != null) {
                        TextButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.height(Dimens.TouchTarget)
                        ) {
                            Text(
                                text = "去设置",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        }
                    }
                }
            }
        }
        return
    }

    if (dueTodayCount == 0) {
        // 态 B：今日无待复习或已全部完成
        Card(
            modifier = modifier
                .fillMaxWidth()
                .then(horizontalPadding),
            shape = RoundedCornerShape(Dimens.RadiusCard),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceL),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已完成",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.SpaceM))
                Column {
                    Text(
                        text = "今日复习已全部搞定！",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "暂无到期待复习知识点，继续保持！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
        return
    }

    // 态 A：存在待复习任务，醒目 Hero Banner
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(horizontalPadding)
            .clickable { onNavigateToReview() },
        shape = RoundedCornerShape(Dimens.RadiusCard),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceL)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "今日待复习",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(Dimens.SpaceXS))
                    Text(
                        text = "$dueTodayCount 条知识点",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Button(
                    onClick = onNavigateToReview,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(Dimens.RadiusButton)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                    Text("立即开始")
                }
            }

            if (deferredCount > 0) {
                Spacer(modifier = Modifier.height(Dimens.SpaceS))
                Text(
                    text = "（今日上限 $dailyLimit 条，已自动顺延 $deferredCount 条至明日）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Preview(name = "Banner 态A - 有待复习", showBackground = true)
@Composable
private fun DashboardBannerDuePreview() {
    MaterialTheme {
        DashboardBanner(
            dueTodayCount = 8,
            deferredCount = 3,
            dailyLimit = 20,
            onNavigateToReview = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(name = "Banner 态B - 今日清空", showBackground = true)
@Composable
private fun DashboardBannerClearPreview() {
    MaterialTheme {
        DashboardBanner(
            dueTodayCount = 0,
            deferredCount = 0,
            dailyLimit = 20,
            onNavigateToReview = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(name = "Banner 态C - 已停用", showBackground = true)
@Composable
private fun DashboardBannerDisabledPreview() {
    MaterialTheme {
        DashboardBanner(
            dueTodayCount = 0,
            deferredCount = 0,
            dailyLimit = 0,
            onNavigateToReview = {},
            onNavigateToSettings = {}
        )
    }
}
