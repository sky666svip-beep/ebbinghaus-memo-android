package com.ebbinghaus.memo.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ebbinghaus.memo.data.preference.SharedPreferencesPreferenceStore
import com.ebbinghaus.memo.ui.scaffold.LocalSnackbarHostState
import com.ebbinghaus.memo.ui.theme.Dimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 快照保留份数预设 */
private val KEEP_COUNT_PRESETS = listOf(3, 5, 7, 14, 30)

/**
 * 「数据与安全」有状态宿主
 *
 * 负责接线三个 SAF 启动器、导入预览弹窗与 Snackbar，并向下委托给无状态 [DataSafetySection]。
 * 全部走 SAF，**Manifest 零新增权限**。
 */
@Composable
fun DataSafetySectionHost(
    viewModel: DataSafetyViewModel,
    onNavigateToTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is DataSafetyEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    // 导出：CreateDocument（默认名 ebbinghaus-YYYYMMDD.json）
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.onEvent(DataSafetyUiEvent.OnExportRequested(it)) }
    }

    // 导入：OpenDocument
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onEvent(DataSafetyUiEvent.OnImportPicked(it)) }
    }

    // 快照目录：OpenDocumentTree
    val dirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.onEvent(DataSafetyUiEvent.OnSnapshotDirPicked(it)) }
    }

    DataSafetySection(
        snapshotEnabled = uiState.snapshotEnabled,
        snapshotDirLabel = uiState.snapshotDirUri?.let { labelOf(it) } ?: "未选择",
        keepCount = uiState.snapshotKeepCount,
        isBusy = uiState.isBusy,
        onExportClick = {
            val name = "ebbinghaus-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json"
            exportLauncher.launch(name)
        },
        onImportClick = {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        },
        onToggleSnapshot = { viewModel.onEvent(DataSafetyUiEvent.OnToggleSnapshot(it)) },
        onPickDirClick = { dirLauncher.launch(null) },
        onKeepCountChange = { viewModel.onEvent(DataSafetyUiEvent.OnKeepCountChanged(it)) },
        onSnapshotNowClick = { viewModel.onEvent(DataSafetyUiEvent.OnSnapshotNow) },
        onNavigateToTrash = onNavigateToTrash,
        modifier = modifier
    )

    // 导入预览弹窗（含全量覆盖警示）
    uiState.pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(DataSafetyUiEvent.OnCancelImport) },
            title = { Text("确认导入？") },
            text = {
                Text(
                    "将导入 ${pending.memoCount} 条知识点 / ${pending.taskCount} 条复习记录。\n\n" +
                        "【全量覆盖】当前设备上的全部数据将被替换，此操作不可撤销。"
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.onEvent(DataSafetyUiEvent.OnConfirmImport) }) {
                    Text("确认覆盖导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(DataSafetyUiEvent.OnCancelImport) }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 从 SAF URI 中提取可读的目录名（仅用于展示）
 */
private fun labelOf(uriString: String): String {
    val decoded = runCatching { Uri.decode(uriString) }.getOrDefault(uriString)
    val tail = decoded.substringAfterLast(":", decoded).substringAfterLast("/")
    return tail.ifBlank { "已选择目录" }
}

/**
 * 「数据与安全」分组（无状态，便于 Preview 与测试）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DataSafetySection(
    snapshotEnabled: Boolean,
    snapshotDirLabel: String,
    keepCount: Int,
    isBusy: Boolean,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onToggleSnapshot: (Boolean) -> Unit,
    onPickDirClick: () -> Unit,
    onKeepCountChange: (Int) -> Unit,
    onSnapshotNowClick: () -> Unit,
    onNavigateToTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceXL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.SpaceXXL)
                )
                Spacer(modifier = Modifier.width(Dimens.SpaceS))
                Text(
                    text = "数据与安全",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = "全部操作走系统文件选择器，不申请任何存储权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // 导出 / 导入
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
            ) {
                OutlinedButton(
                    onClick = onExportClick,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(Dimens.SpaceL))
                    Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                    Text("导出数据")
                }
                OutlinedButton(
                    onClick = onImportClick,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(Dimens.SpaceL))
                    Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                    Text("导入数据")
                }
            }

            // 回收站入口
            OutlinedButton(
                onClick = onNavigateToTrash,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(Dimens.SpaceL))
                Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                Text("回收站（30 天内可还原）")
            }

            Spacer(modifier = Modifier.height(Dimens.SpaceXS))

            // 自动快照
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "自动快照",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(checked = snapshotEnabled, onCheckedChange = onToggleSnapshot)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "快照目录：$snapshotDirLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPickDirClick) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(Dimens.SpaceL))
                    Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                    Text("选择目录")
                }
            }

            Text(
                text = "保留份数：",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                modifier = Modifier.fillMaxWidth()
            ) {
                KEEP_COUNT_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = keepCount == preset,
                        onClick = { onKeepCountChange(preset) },
                        label = { Text("$preset 份") }
                    )
                }
            }

            OutlinedButton(
                onClick = onSnapshotNowClick,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(Dimens.SpaceL))
                Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                Text("立即快照一次")
            }
        }
    }
}

@Preview(name = "数据与安全 · 关闭态", showBackground = true)
@Composable
private fun DataSafetySectionPreview() {
    MaterialTheme {
        DataSafetySection(
            snapshotEnabled = false,
            snapshotDirLabel = "未选择",
            keepCount = SharedPreferencesPreferenceStore.DEFAULT_KEEP_COUNT,
            isBusy = false,
            onExportClick = {},
            onImportClick = {},
            onToggleSnapshot = {},
            onPickDirClick = {},
            onKeepCountChange = {},
            onSnapshotNowClick = {},
            onNavigateToTrash = {}
        )
    }
}

@Preview(name = "数据与安全 · 开启态", showBackground = true)
@Composable
private fun DataSafetySectionEnabledPreview() {
    MaterialTheme {
        DataSafetySection(
            snapshotEnabled = true,
            snapshotDirLabel = "Documents/Backup",
            keepCount = 7,
            isBusy = false,
            onExportClick = {},
            onImportClick = {},
            onToggleSnapshot = {},
            onPickDirClick = {},
            onKeepCountChange = {},
            onSnapshotNowClick = {},
            onNavigateToTrash = {}
        )
    }
}
