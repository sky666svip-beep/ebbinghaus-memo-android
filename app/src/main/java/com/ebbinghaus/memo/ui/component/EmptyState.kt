package com.ebbinghaus.memo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.ebbinghaus.memo.ui.theme.Dimens

/**
 * 通用空状态组件
 *
 * 覆盖设计状态矩阵 S1（首次空库）与 S2（搜索/筛选无结果）。
 * 视觉规范：outline 色 72dp 线性图标 + bodyLarge 主文案 + bodySmall 说明 + 次级 CTA。
 *
 * @param icon 空状态插画图标
 * @param title 主文案
 * @param subtitle 副文案，为空则不展示
 * @param actionLabel CTA 按钮文案，为空则不展示按钮
 * @param onAction CTA 点击回调
 * @param modifier 外部修饰器
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.SpaceXXXL, vertical = Dimens.SpaceXXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(Dimens.StateIconSize)
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceXXL))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Dimens.SpaceS))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }

        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(Dimens.SpaceXXL))
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.height(Dimens.TouchTarget)
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

/**
 * 带主色按钮的变体，用于 S1「首次空库」这类需要强引导的场景。
 */
@Composable
fun EmptyStatePrimary(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.SpaceXXXL, vertical = Dimens.SpaceXXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(Dimens.StateIconSize)
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceXXL))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Dimens.SpaceS))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }

        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(Dimens.SpaceXXL))
            Button(
                onClick = onAction,
                modifier = Modifier.height(Dimens.TouchTarget)
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Preview(name = "空状态 - 无 CTA", showBackground = true)
@Composable
private fun EmptyStatePreview() {
    MaterialTheme {
        EmptyState(
            icon = Icons.Default.SearchOff,
            title = "未检索到匹配的知识点",
            subtitle = "试试更换关键词或清空筛选条件"
        )
    }
}

@Preview(name = "空状态 - 带 CTA", showBackground = true)
@Composable
private fun EmptyStateWithActionPreview() {
    MaterialTheme {
        EmptyStatePrimary(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = "知识库空空如也，点击右下角 + 开始添加吧！",
            subtitle = "每录入一条知识点，系统会自动为你安排 6 档复习节奏",
            actionLabel = "新增知识点",
            onAction = {}
        )
    }
}
