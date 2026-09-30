package com.ebbinghaus.memo.core.util

/**
 * 搜索关键词切分唯一真源（P2-10 / P2-6）
 *
 * 设计口径（架构 §8-1 / D7）：
 * - **只做**「空白切分 + trim + 去空」三件事，**不做**大小写归一（大小写不敏感由匹配层各自承担：
 *   SQL `LIKE` 与 [com.ebbinghaus.memo.ui.component.HighlightedText] 的 `lowercase` 比较）；
 * - 切分空白集合 = 常规空白（空格 / `\t` / `\n` / `\r` / `\f` 等，等价 `\s`）**并额外包含全角空格 `U+3000`**，
 *   以覆盖中文输入法场景（PRD 边界 9：全角空格 / 换行 / Tab 输入下，UI 高亮词集合必须等于仓储实际查询词集合）；
 * - 上限固定 [MAX_KEYWORDS]（=6）个，与仓储 6 元参数、UI 高亮共用同一集合，杜绝「高亮 ≠ 筛选」。
 *
 * 共用点：`MemoRepositoryImpl.searchMemos`（仓储 6 元参数）、`MemoListScreen` 高亮、
 * 6 词超限提示（[ignoredCount]），以及全部测试替身的按词切分。
 */
object SearchQueryTokenizer {

    /** 单次查询参与匹配的最大关键词数量（与 DAO 6 元参数一一对应） */
    const val MAX_KEYWORDS: Int = 6

    /**
     * 空白切分正则：`\s` 全量 + 全角空格 `U+3000`。
     *
     * 说明：Java/Kotlin 的 `\s` 默认不匹配 `U+3000`（全角空格），故显式追加。
     */
    private val WHITESPACE = Regex("[\\s\\u3000]+")

    /**
     * 将原始搜索串切分为关键词列表（保留原始大小写，去除首尾空白，过滤空项）。
     *
     * @param raw 原始搜索输入（可能含多空格、全角空格、换行、Tab 或纯空白）
     * @return 切分后的关键词列表；无有效词时返回空列表
     */
    fun tokenize(raw: String): List<String> =
        raw.trim()
            .split(WHITESPACE)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * 实际参与查询的关键词（前 [MAX_KEYWORDS] 个），供仓储 6 元参数与 UI 高亮共用。
     *
     * @param raw 原始搜索输入
     */
    fun activeKeywords(raw: String): List<String> = tokenize(raw).take(MAX_KEYWORDS)

    /**
     * 因超出 [MAX_KEYWORDS] 上限而被忽略的关键词数量（用于 P2-6 轻提示）。
     *
     * @param raw 原始搜索输入
     * @return `max(0, 词数 - MAX_KEYWORDS)`
     */
    fun ignoredCount(raw: String): Int = (tokenize(raw).size - MAX_KEYWORDS).coerceAtLeast(0)
}
