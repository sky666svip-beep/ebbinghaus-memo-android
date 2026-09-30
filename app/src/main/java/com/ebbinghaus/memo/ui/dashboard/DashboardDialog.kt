package com.ebbinghaus.memo.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 应用内醒目看板提示弹窗（免系统通知权限）
 *
 * 在次日或多天未登录重新打开时触发，清晰呈现今日待复习量与一键复习直达入口。
 */
@Composable
fun DashboardDialog(
    dueTodayCount: Int,
    isMultiDayAbsence: Boolean,
    absentDays: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val messageText = if (isMultiDayAbsence) {
        "欢迎回来！您已有 $absentDays 天未打开应用，根据艾宾浩斯智能调度，今天有 $dueTodayCount 个知识点等待复习巩固。"
    } else {
        "欢迎回来！今天有 $dueTodayCount 个知识点等待复习巩固，保持节奏，记忆更牢固。"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "待复习提醒",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "今日复习看板",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "艾宾浩斯顺延保护已生效：未标记逾期，不扣减记忆档位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("立即复习")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}
