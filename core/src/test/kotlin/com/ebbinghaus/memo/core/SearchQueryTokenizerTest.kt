package com.ebbinghaus.memo.core

import com.ebbinghaus.memo.core.util.SearchQueryTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索关键词切分唯一真源（P2-10 / P2-6）单元测试
 *
 * 覆盖：空串 / 纯空白 / 全角空格 / 换行 / Tab / 多空格 / 恰好 6 词 / 7 词 /
 * 大小写不归一 / [SearchQueryTokenizer.activeKeywords] 取前 6 / [SearchQueryTokenizer.ignoredCount]。
 */
class SearchQueryTokenizerTest {

    @Test
    fun tokenize_emptyString_returnsEmptyList() {
        assertEquals(emptyList<String>(), SearchQueryTokenizer.tokenize(""))
    }

    @Test
    fun tokenize_whitespaceOnly_returnsEmptyList() {
        assertEquals(emptyList<String>(), SearchQueryTokenizer.tokenize("   "))
        assertEquals(emptyList<String>(), SearchQueryTokenizer.tokenize("\t\n\r "))
        // 纯全角空格同样视为无关键词（PRD 边界 8）
        assertEquals(emptyList<String>(), SearchQueryTokenizer.tokenize("　　"))
    }

    @Test
    fun tokenize_plainSpaces_splitsAndTrims() {
        assertEquals(listOf("Jetpack", "Compose"), SearchQueryTokenizer.tokenize("  Jetpack   Compose  "))
    }

    @Test
    fun tokenize_fullWidthSpace_isSeparator() {
        // 全角空格 U+3000 必须作为分隔符（PRD 边界 9）
        assertEquals(listOf("算法", "信息论"), SearchQueryTokenizer.tokenize("算法　信息论"))
    }

    @Test
    fun tokenize_newlineAndTab_areSeparators() {
        assertEquals(listOf("a", "b", "c"), SearchQueryTokenizer.tokenize("a\nb\tc"))
    }

    @Test
    fun tokenize_mixedWhitespaceKinds_collapseIntoSingleSeparator() {
        assertEquals(
            listOf("alpha", "beta", "gamma"),
            SearchQueryTokenizer.tokenize("alpha \u3000\tbeta\n\n  gamma")
        )
    }

    @Test
    fun tokenize_doesNotNormalizeCase() {
        // 口径：不归一大小写，匹配层各自负责大小写不敏感
        assertEquals(listOf("Compose", "compose"), SearchQueryTokenizer.tokenize("Compose compose"))
    }

    @Test
    fun tokenize_veryLongSingleWord_isNotSplit() {
        // 超长单词（无分隔符）必须原样作为单个关键词，不得被截断或切分
        val long = "x".repeat(5000)
        assertEquals(listOf(long), SearchQueryTokenizer.tokenize(long))
    }

    @Test
    fun activeKeywords_takesAtMostSix() {
        val seven = "k1 k2 k3 k4 k5 k6 k7"
        assertEquals(listOf("k1", "k2", "k3", "k4", "k5", "k6"), SearchQueryTokenizer.activeKeywords(seven))
    }

    @Test
    fun activeKeywords_exactlySix_keepsAll() {
        val six = "k1 k2 k3 k4 k5 k6"
        assertEquals(listOf("k1", "k2", "k3", "k4", "k5", "k6"), SearchQueryTokenizer.activeKeywords(six))
    }

    @Test
    fun activeKeywords_isPrefixOfTokenize() {
        val raw = "a　b c\td e f g h"
        assertEquals(SearchQueryTokenizer.tokenize(raw).take(SearchQueryTokenizer.MAX_KEYWORDS), SearchQueryTokenizer.activeKeywords(raw))
    }

    @Test
    fun ignoredCount_zeroAtOrBelowLimit() {
        assertEquals(0, SearchQueryTokenizer.ignoredCount(""))
        assertEquals(0, SearchQueryTokenizer.ignoredCount("a b c"))
        assertEquals(0, SearchQueryTokenizer.ignoredCount("k1 k2 k3 k4 k5 k6"))
    }

    @Test
    fun ignoredCount_countsWordsBeyondLimit() {
        assertEquals(1, SearchQueryTokenizer.ignoredCount("k1 k2 k3 k4 k5 k6 k7"))
        assertEquals(3, SearchQueryTokenizer.ignoredCount("k1 k2 k3 k4 k5 k6 k7 k8 k9"))
    }

    @Test
    fun ignoredCount_blankOnlyInput_isZeroAndNoError() {
        assertEquals(0, SearchQueryTokenizer.ignoredCount("　　 \n\t"))
    }

    @Test
    fun activeKeywords_pinsWhitespaceContract_forMixedInput() {
        // 契约守护（P2-10 / D-03）：显式断言切分口径本身，而非「函数与自身比较」（恒真无守护力）。
        // 若 WHITESPACE 口径被改动（如误删全角空格 U+3000 / \t / \n），下列断言必须失败。
        val raw = "全角\u3000换行\nTab\twords beyond six limit"
        assertEquals(
            "含全角空格/换行/Tab 时，activeKeywords 必须切出前 6 个词（口径唯一真源）",
            listOf("全角", "换行", "Tab", "words", "beyond", "six"),
            SearchQueryTokenizer.activeKeywords(raw)
        )
        // 逐项守护各类空白均须为分隔符（全角空格 / Tab / 换行）
        assertEquals(listOf("a", "b"), SearchQueryTokenizer.tokenize("a\u3000b"))
        assertEquals(listOf("a", "b", "c"), SearchQueryTokenizer.tokenize("a\tb\nc"))
        assertTrue("上限恒为 6", SearchQueryTokenizer.activeKeywords(raw).size <= SearchQueryTokenizer.MAX_KEYWORDS)
    }

    @Test
    fun ignoredCount_agreesWithActiveKeywords_forSevenHalfWidthWords() {
        // P2-6 / D-02：ignoredCount 与 activeKeywords 计数口径必须一致，不得分歧
        val seven = "k1 k2 k3 k4 k5 k6 k7"
        assertEquals(1, SearchQueryTokenizer.ignoredCount(seven))
        assertEquals(6, SearchQueryTokenizer.activeKeywords(seven).size)
        assertEquals(
            "ignoredCount 必须等于「总词数 - 生效词数」",
            SearchQueryTokenizer.tokenize(seven).size - SearchQueryTokenizer.activeKeywords(seven).size,
            SearchQueryTokenizer.ignoredCount(seven)
        )
    }

    @Test
    fun ignoredCount_agreesWithActiveKeywords_forSevenFullWidthWords() {
        // P2-6 / D-02：全角空格分隔的 7 词同样应「忽略 1、生效 6」，与 UI 提示口径一致
        val seven = "a\u3000b\u3000c\u3000d\u3000e\u3000f\u3000g"
        assertEquals(1, SearchQueryTokenizer.ignoredCount(seven))
        assertEquals(6, SearchQueryTokenizer.activeKeywords(seven).size)
        assertEquals(
            "ignoredCount 必须等于「总词数 - 生效词数」（全角空格场景）",
            SearchQueryTokenizer.tokenize(seven).size - SearchQueryTokenizer.activeKeywords(seven).size,
            SearchQueryTokenizer.ignoredCount(seven)
        )
    }
}
