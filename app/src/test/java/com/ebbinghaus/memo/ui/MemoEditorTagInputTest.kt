package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.ui.memolist.appendTagToInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑器标签输入追加去重纯函数（P2-8）单元测试
 *
 * 覆盖：空输入追加 / 已存在去重 / 多分隔符（逗号 / 中文逗号 / 顿号 / 空格）/ 空白标签无操作 /
 * 追加结果可被编辑器同一解析口径还原。
 */
class MemoEditorTagInputTest {

    /** 与编辑器 `submit` 相同的解析口径 */
    private fun parse(input: String): List<String> =
        input.split(",", "，", "、", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    @Test
    fun appendToEmptyInput_producesSingleTag() {
        assertEquals("算法", appendTagToInput("", "算法"))
    }

    @Test
    fun appendToExisting_appendsWithCommaSeparator() {
        assertEquals("Android, Compose", appendTagToInput("Android", "Compose"))
    }

    @Test
    fun appendDuplicate_isNoOp() {
        assertEquals("Android, Compose", appendTagToInput("Android, Compose", "Compose"))
        assertEquals("Android, Compose", appendTagToInput("Android, Compose", "  Compose  "))
    }

    @Test
    fun appendBlankTag_isNoOp() {
        assertEquals("Android", appendTagToInput("Android", "   "))
        assertEquals("", appendTagToInput("", ""))
        // 补正（QA 回归发现）：中文输入法常见的全角空格（U+3000）同属空白标签，须 trim 后视为无操作。
        // 半角空格用例无法判别「仅 trim ASCII 空格 / 用 Java \s（不含 U+3000）匹配」类回归，故本条不可省。
        assertEquals("", appendTagToInput("", "　"))
        assertEquals("Android", appendTagToInput("Android", "　"))
    }

    @Test
    fun appendDetectsExistingAcrossAllSeparators() {
        // 已存在标签应被识别（无论原文用何种分隔符），不得重复追加
        assertEquals("A，B", appendTagToInput("A，B", "B"))
        assertEquals("A、B", appendTagToInput("A、B", "A"))
        assertEquals("A B", appendTagToInput("A B", "B"))
    }

    @Test
    fun appendedResult_isParsedBackWithSameTagSet() {
        val input = appendTagToInput("Android", "算法")
        val parsed = parse(input)
        assertTrue("追加结果必须含新标签", parsed.contains("算法"))
        assertTrue("追加结果必须保留原标签", parsed.contains("Android"))
        assertEquals(2, parsed.size)
    }

    @Test
    fun repeatedAppends_neverProduceDuplicates() {
        var input = ""
        input = appendTagToInput(input, "A")
        input = appendTagToInput(input, "B")
        input = appendTagToInput(input, "A")
        input = appendTagToInput(input, "C")
        assertEquals(listOf("A", "B", "C"), parse(input))
    }

    @Test
    fun appendTrimsTrailingWhitespaceBeforeSeparator() {
        assertEquals("A, B", appendTagToInput("A   ", "B"))
    }
}
