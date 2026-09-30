package com.ebbinghaus.memo.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

/**
 * 搜索关键词高亮文本
 *
 * 将命中的关键词以 `primaryContainer` 底色 + `onPrimaryContainer` 前景色标注，
 * 便于用户在搜索结果中快速定位匹配片段。
 *
 * 匹配规则：大小写不敏感；空关键词自动跳过；多个关键词逐一匹配。
 *
 * @param text 原始文本
 * @param keywords 需要高亮的关键词列表（通常由搜索词按空格切分而来）
 * @param modifier 外部修饰器
 * @param style 文本样式
 * @param maxLines 最大行数
 * @param overflow 溢出处理策略
 */
@Composable
fun HighlightedText(
    text: String,
    keywords: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    val annotatedText = rememberHighlightedAnnotatedString(
        text = text,
        keywords = keywords,
        highlightBackground = MaterialTheme.colorScheme.primaryContainer,
        highlightForeground = MaterialTheme.colorScheme.onPrimaryContainer
    )

    Text(
        text = annotatedText,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * 构建带高亮样式的 [AnnotatedString]
 *
 * @param text 原始文本
 * @param keywords 关键词列表
 * @param highlightBackground 命中片段底色
 * @param highlightForeground 命中片段前景色
 */
@Composable
internal fun rememberHighlightedAnnotatedString(
    text: String,
    keywords: List<String>,
    highlightBackground: Color,
    highlightForeground: Color
): AnnotatedString {
    return remember(text, keywords, highlightBackground, highlightForeground) {
        buildHighlightedAnnotatedString(
            text = text,
            keywords = keywords,
            highlightBackground = highlightBackground,
            highlightForeground = highlightForeground
        )
    }
}

/**
 * 纯函数版本的高亮文本构建器，便于单元测试。
 *
 * 采用「逐字符扫描 + 优先最长命中」策略：在每个位置尝试所有关键词，
 * 命中则整体标记并跳到命中片段尾部，避免嵌套重叠导致的样式错乱。
 */
internal fun buildHighlightedAnnotatedString(
    text: String,
    keywords: List<String>,
    highlightBackground: Color,
    highlightForeground: Color
): AnnotatedString {
    val validKeywords = keywords
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedByDescending { it.length }

    if (text.isEmpty() || validKeywords.isEmpty()) {
        return AnnotatedString(text)
    }

    val lowerText = text.lowercase()
    val lowerKeywords = validKeywords.map { it.lowercase() }

    return buildAnnotatedString {
        appendHighlightedSegments(
            text = text,
            lowerText = lowerText,
            lowerKeywords = lowerKeywords,
            highlightBackground = highlightBackground,
            highlightForeground = highlightForeground
        )
    }
}

/**
 * 向内联构建器追加分段文本
 */
private fun AnnotatedString.Builder.appendHighlightedSegments(
    text: String,
    lowerText: String,
    lowerKeywords: List<String>,
    highlightBackground: Color,
    highlightForeground: Color
) {
    var index = 0
    while (index < text.length) {
        var matchedLength = 0
        for (keyword in lowerKeywords) {
            if (lowerText.startsWith(keyword, startIndex = index)) {
                matchedLength = keyword.length
                break
            }
        }

        if (matchedLength > 0) {
            val start = length
            append(text.substring(index, index + matchedLength))
            addStyle(
                style = SpanStyle(
                    background = highlightBackground,
                    color = highlightForeground,
                    fontWeight = FontWeight.Bold
                ),
                start = start,
                end = length
            )
            index += matchedLength
        } else {
            append(text[index])
            index += 1
        }
    }
}

@Preview(name = "关键词高亮", showBackground = true)
@Composable
private fun HighlightedTextPreview() {
    MaterialTheme {
        HighlightedText(
            text = "Jetpack Compose 架构规范与状态提升",
            keywords = listOf("Compose", "状态")
        )
    }
}
