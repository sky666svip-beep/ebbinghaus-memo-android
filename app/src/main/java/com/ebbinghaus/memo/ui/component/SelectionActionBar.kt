package com.ebbinghaus.memo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ebbinghaus.memo.ui.theme.Dimens

/**
 * 多选操作项（E11 泛化）
 *
 * @param icon 操作图标
 * @param label 操作文案（同时作为无障碍 `contentDescription`）
 * @param enabled 是否可用
 * @param onClick 点击回调
 */
data class SelectionActionItem(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * 通用多选操作栏（E11 / P2-3）
 *
 * 由「操作项列表」驱动，列表页与回收站各自组装（架构 D10：不抽基类，泛化为 Composable）：
 * - 列表页：删除 / 打标签 / 全选当前结果 / 关闭；
 * - 回收站：还原 / 彻底删除 / 全选当前结果 / 关闭。
 *
 * 说明：「已选 N 条」的数量显示统一交由各页顶部栏承载，本栏不再重复展示（P1-3）。
 *
 * @param actions 操作项（按展示顺序）
 */
@Composable
fun SelectionActionBar(
    actions: List<SelectionActionItem>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.BottomBarHeight)
                .padding(horizontal = Dimens.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
            // 均匀分布，保证 Compact 底栏与 Rail 顶部两种形态下操作项均铺满可用宽度。
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            actions.forEach { item ->
                IconAction(
                    icon = item.icon,
                    label = item.label,
                    enabled = item.enabled,
                    onClick = item.onClick
                )
            }
        }
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(Dimens.TouchTarget)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    }
}
