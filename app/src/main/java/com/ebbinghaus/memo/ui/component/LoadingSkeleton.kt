package com.ebbinghaus.memo.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ebbinghaus.memo.ui.theme.Dimens

/**
 * Shimmer 扫光刷子
 *
 * 由无限循环动画驱动 0f→1f 的平移比例，生成沿 X 轴滑动的线性渐变，
 * 模拟 Material 骨架屏的呼吸效果，零三方依赖。
 */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_shimmer_translate"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surface

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(x = -400f + translateAnim * 1200f, y = 0f),
        end = Offset(x = translateAnim * 1200f, y = 0f)
    )
}

/**
 * 单条知识点卡片骨架占位
 */
@Composable
fun MemoCardSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusCard),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
        ) {
            // 正文第一行
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.SkeletonLineHeight)
                    .background(brush = brush, shape = RoundedCornerShape(Dimens.RadiusSkeleton))
            )
            // 正文第二行（略短，模拟文本换行）
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(Dimens.SkeletonLineHeight)
                    .background(brush = brush, shape = RoundedCornerShape(Dimens.RadiusSkeleton))
            )

            Spacer(modifier = Modifier.height(Dimens.SpaceXS))

            // 笔记提示行
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(Dimens.SkeletonLineHeight)
                    .background(brush = brush, shape = RoundedCornerShape(Dimens.RadiusSkeleton))
            )

            Spacer(modifier = Modifier.height(Dimens.SpaceS))

            // 标签与操作区
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(Dimens.SkeletonLineHeight)
                    .background(brush = brush, shape = RoundedCornerShape(Dimens.RadiusSkeleton))
            )
        }
    }
}

/**
 * 知识点列表骨架屏
 *
 * @param count 骨架卡片数量，默认 4 张
 * @param modifier 外部修饰器
 */
@Composable
fun MemoListSkeleton(count: Int = 4, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)
    ) {
        items(count) {
            MemoCardSkeleton()
        }
    }
}

@Preview(name = "列表骨架屏", showBackground = true)
@Composable
private fun MemoListSkeletonPreview() {
    MaterialTheme {
        MemoListSkeleton()
    }
}

@Preview(name = "单卡骨架屏", showBackground = true)
@Composable
private fun MemoCardSkeletonPreview() {
    MaterialTheme {
        MemoCardSkeleton(modifier = Modifier.padding(Dimens.ScreenPadding))
    }
}
