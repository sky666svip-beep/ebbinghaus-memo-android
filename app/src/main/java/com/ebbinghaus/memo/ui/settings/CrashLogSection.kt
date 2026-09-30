package com.ebbinghaus.memo.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import com.ebbinghaus.memo.crash.CrashLogSnapshot
import com.ebbinghaus.memo.crash.CrashLogger
import com.ebbinghaus.memo.ui.scaffold.LocalSnackbarHostState
import com.ebbinghaus.memo.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 崩溃日志正文弹窗的最大高度（超出内部滚动） */
private val CRASH_DIALOG_MAX_HEIGHT = 360.dp

/**
 * 「关于」分组内的崩溃日志入口宿主。
 *
 * 行为：
 * 1. 组合时在 IO 线程读取 [CrashLogger.snapshot]（私有目录、零权限）；
 * 2. **仅当存在崩溃记录时**渲染入口（无记录则完全不占位，符合「仅在存在崩溃记录时显示」）；
 * 3. 查看 / 复制到剪贴板 / 分享（`ACTION_SEND` + [ShareCompat]，**不需要 FileProvider、不需要权限**）/ 清空。
 *
 * 之所以放在「关于」分组：崩溃日志属诊断信息，非日常功能，避免打扰普通用户。
 */
@Composable
fun CrashLogSectionHost(
    crashLogger: CrashLogger,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf<CrashLogSnapshot?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(crashLogger) {
        snapshot = withContext(Dispatchers.IO) { crashLogger.snapshot() }
    }

    val current = snapshot
    if (current != null && current.hasRecord) {
        CrashLogEntry(
            recordCount = current.recordCount,
            onViewClick = { showDialog = true },
            onClearClick = { showClearConfirm = true },
            modifier = modifier
        )

        if (showDialog) {
            CrashLogDialog(
                text = current.latestText ?: "（崩溃日志读取失败）",
                onCopy = {
                    copyToClipboard(context, current.latestText.orEmpty())
                    scope.launch { snackbarHostState.showSnackbar("崩溃日志已复制到剪贴板") }
                },
                onShare = { shareCrashLog(context, current.latestText.orEmpty()) },
                onClear = { showClearConfirm = true },
                onDismiss = { showDialog = false }
            )
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("清空崩溃记录？") },
                text = { Text("将删除本机保存的全部崩溃日志，此操作不可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearConfirm = false
                            showDialog = false
                            scope.launch {
                                withContext(Dispatchers.IO) { crashLogger.clear() }
                                snapshot = withContext(Dispatchers.IO) { crashLogger.snapshot() }
                                snackbarHostState.showSnackbar("已清空崩溃记录")
                            }
                        }
                    ) {
                        Text("清空")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

/**
 * 崩溃日志入口（无状态，便于测试与预览）
 */
@Composable
private fun CrashLogEntry(
    recordCount: Int,
    onViewClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.ItemSpacing),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Dimens.SpaceXXL)
                )
                Spacer(modifier = Modifier.width(Dimens.SpaceS))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "崩溃日志",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "检测到 $recordCount 次崩溃记录，可查看、复制或分享给我们定位问题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceS))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
            ) {
                OutlinedButton(onClick = onViewClick, modifier = Modifier.weight(1f)) {
                    Text("查看")
                }
                OutlinedButton(onClick = onClearClick, modifier = Modifier.weight(1f)) {
                    Text("清空")
                }
            }
        }
    }
}

/**
 * 崩溃日志正文弹窗：等宽字体展示完整堆栈（含 `Caused by` 链），可滚动。
 */
@Composable
private fun CrashLogDialog(
    text: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("最近一次崩溃日志") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = CRASH_DIALOG_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onShare) {
                Text("分享")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCopy) {
                    Text("复制")
                }
                TextButton(onClick = onClear) {
                    Text("清空")
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

/** 复制纯文本到系统剪贴板（失败静默，不崩溃） */
private fun copyToClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("崩溃日志", text))
    }
}

/**
 * 分享崩溃日志：`ACTION_SEND` + [ShareCompat]，正文即纯文本。
 *
 * 采用 `EXTRA_TEXT` 直接携带文本，**无需 FileProvider、无需任何权限**（任务硬约束）。
 */
private fun shareCrashLog(context: Context, text: String) {
    if (text.isEmpty()) return
    runCatching {
        ShareCompat.IntentBuilder(context)
            .setType("text/plain")
            .setSubject("艾宾浩斯备忘录 崩溃日志")
            .setText(text)
            .setChooserTitle("分享崩溃日志")
            .startChooser()
    }
}
