package com.ebbinghaus.memo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import com.ebbinghaus.memo.data.repository.MemoSortOption
import com.ebbinghaus.memo.ui.theme.Dimens

/**
 * 排序选项中文文案（§8-9：文案硬编码于 Composable 层）
 */
fun MemoSortOption.displayName(): String = when (this) {
    MemoSortOption.CREATED_DESC -> "按创建时间"
    MemoSortOption.UPDATED_DESC -> "按更新时间"
    MemoSortOption.STAGE_DESC -> "按复习档位"
    MemoSortOption.DUE_ASC -> "按到期日"
}

/**
 * 排序底部弹窗（E10）
 *
 * 单选列表，点击即时生效（无「应用/确定」按钮），符合轻量原则。
 *
 * @param current 当前排序选项
 * @param onSelect 选中回调（点击即生效）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    current: MemoSortOption,
    onSelect: (MemoSortOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.SpaceXL)
        ) {
            Text(
                text = "排序方式",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = Dimens.SpaceXL,
                    end = Dimens.SpaceXL,
                    top = Dimens.SpaceS,
                    bottom = Dimens.SpaceS
                )
            )

            MemoSortOption.entries.forEach { option ->
                val selected = option == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(option) }
                        )
                        .padding(horizontal = Dimens.SpaceXL, vertical = Dimens.SpaceM),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelect(option) }
                    )
                    Spacer(modifier = Modifier.width(Dimens.SpaceM))
                    Text(
                        text = option.displayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.SpaceXS))
            }
        }
    }
}
