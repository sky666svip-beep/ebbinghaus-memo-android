package com.ebbinghaus.memo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.ebbinghaus.memo.ui.theme.Dimens

/**
 * 通用错误状态组件
 *
 * 覆盖设计状态矩阵 S13（数据库异常）。规范要求「异常态必须可恢复」，
 * 因此 [onRetry] 为必填项（重试即重新订阅 Flow / 重新触发加载）。
 *
 * @param message 错误说明文案
 * @param onRetry 重试回调
 * @param modifier 外部修饰器
 */
@Composable
fun ErrorState(
    message: String = "数据读取失败",
    onRetry: () -> Unit,
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
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(Dimens.StateIconSize)
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceXXL))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceS))

        Text(
            text = "请稍后重试，你的数据不会丢失",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.SpaceXXL))

        Button(
            onClick = onRetry,
            modifier = Modifier.height(Dimens.TouchTarget)
        ) {
            Text(text = "重试")
        }
    }
}

@Preview(name = "错误状态", showBackground = true)
@Composable
private fun ErrorStatePreview() {
    MaterialTheme {
        ErrorState(onRetry = {})
    }
}
