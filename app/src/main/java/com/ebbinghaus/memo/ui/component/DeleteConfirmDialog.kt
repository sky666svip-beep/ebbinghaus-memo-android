package com.ebbinghaus.memo.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties

/** 弹窗中展示的内容预览最大长度，超出部分以省略号截断 */
private const val PREVIEW_MAX_LENGTH = 20

/** 单条删除的默认说明文案（E06 起：软删除，可到回收站还原） */
private const val DEFAULT_MESSAGE =
    "删除后将移入回收站，30 天内可随时还原，其复习进度会一并保留。"

/**
 * 删除知识点二次确认弹窗
 *
 * 列表页与详情页共用，样式对齐 Material 3 规范：确认按钮使用 `error` 配色。
 *
 * E06 起删除为**软删除**（移入回收站），文案已从原「级联清除、不可恢复」更新为
 * 「移入回收站、可还原」，避免对用户造成不可逆的误导。
 *
 * @param memoPreview 待删除知识点的内容预览，用于「确认删除「xxx」？」
 * @param onConfirm 确认删除回调
 * @param onDismiss 取消或点击外部关闭回调
 * @param title 自定义标题；为 null 时按 [memoPreview] 自动生成
 * @param message 自定义正文说明；默认展示回收站说明
 */
@Composable
fun DeleteConfirmDialog(
    memoPreview: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    message: String = DEFAULT_MESSAGE
) {
    val preview = memoPreview.trim().let { raw ->
        if (raw.length > PREVIEW_MAX_LENGTH) {
            raw.take(PREVIEW_MAX_LENGTH) + "…"
        } else {
            raw
        }
    }

    val resolvedTitle = title ?: if (preview.isBlank()) {
        "确认删除该知识点？"
    } else {
        "确认删除「$preview」？"
    }

    AlertDialog(
        modifier = Modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = resolvedTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Text(message)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("确认删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    )
}

@Preview(name = "删除确认弹窗", showBackground = true)
@Composable
private fun DeleteConfirmDialogPreview() {
    MaterialTheme {
        DeleteConfirmDialog(
            memoPreview = "Jetpack Compose 状态提升原则",
            onConfirm = {},
            onDismiss = {}
        )
    }
}
