package com.ebbinghaus.memo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ebbinghaus.memo.ui.theme.Dimens

/**
 * 批量打标签弹窗（E11）
 *
 * 支持输入新标签或从候选标签中点选；确认后由 ViewModel 去重合并（已含该标签的条目跳过）。
 *
 * @param candidates 候选标签（来自当前全部标签）
 * @param onConfirm 确认回调，携带目标标签
 * @param onDismiss 取消回调
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerDialog(
    candidates: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text("批量添加标签") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("输入标签名称…") }
                )

                if (candidates.isNotEmpty()) {
                    Text(
                        text = "或选择已有标签：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = Dimens.SpaceM, bottom = Dimens.SpaceS)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        candidates.forEach { tag ->
                            FilterChip(
                                selected = input == tag,
                                onClick = { input = tag },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input.trim()) },
                enabled = input.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
