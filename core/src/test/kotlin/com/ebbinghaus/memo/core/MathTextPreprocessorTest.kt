package com.ebbinghaus.memo.core

import com.ebbinghaus.memo.core.util.MathTextPreprocessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数学与逻辑公式预处理器单元测试
 *
 * 验证对用户实际输入的计算机逻辑判断式与 LaTeX 命令的自适应识别与 Unicode 可读化。
 */
class MathTextPreprocessorTest {

    @Test
    fun testUserExpression1_UnsignedComparison() {
        val raw = "uA\\le uB \\iff CF=1 \\lor ZF=1"

        assertTrue("必须识别包含 LaTeX 语法", MathTextPreprocessor.containsLatex(raw))

        val unicode = MathTextPreprocessor.formatToUnicode(raw)
        assertEquals("uA ≤ uB ⟺ CF=1 ∨ ZF=1", unicode.trim())

        val normalized = MathTextPreprocessor.normalizeLatex(raw)
        assertTrue("自适应包裹行内公式定界符", normalized.startsWith("$") && normalized.endsWith("$"))
    }

    @Test
    fun testUserExpression2_SignedComparison() {
        val raw = "A\\le B \\iff (SF\\oplus OF=1)\\lor ZF=1"

        assertTrue("必须识别包含 LaTeX 语法", MathTextPreprocessor.containsLatex(raw))

        val unicode = MathTextPreprocessor.formatToUnicode(raw)
        assertEquals("A ≤ B ⟺ (SF ⊕ OF=1) ∨ ZF=1", unicode.trim())

        val normalized = MathTextPreprocessor.normalizeLatex(raw)
        assertTrue("自适应包裹行内公式定界符", normalized.startsWith("$") && normalized.endsWith("$"))
    }

    @Test
    fun testMixedChineseAndLatex() {
        val raw = "无符号比较：uA\\le uB \\iff CF=1 \\lor ZF=1，有符号比较：A\\le B \\iff (SF\\oplus OF=1)\\lor ZF=1"

        assertTrue(MathTextPreprocessor.containsLatex(raw))

        val unicode = MathTextPreprocessor.formatToUnicode(raw)
        assertTrue(unicode.contains("uA ≤ uB ⟺ CF=1 ∨ ZF=1"))
        assertTrue(unicode.contains("A ≤ B ⟺ (SF ⊕ OF=1) ∨ ZF=1"))
    }

    @Test
    fun testCommonLatexSymbols() {
        val raw = "\\alpha + \\beta \\neq \\gamma \\times \\pi \\pm \\infty"
        val unicode = MathTextPreprocessor.formatToUnicode(raw)
        assertEquals("α + β ≠ γ × π ± ∞", unicode.trim())
    }

    @Test
    fun testAlreadyWrappedFormulaKeepsIntegrity() {
        val raw = "\$x \\le y\$"
        val normalized = MathTextPreprocessor.normalizeLatex(raw)
        assertEquals("已包裹公式定界符不重复添加", "\$x \\le y\$", normalized)
    }


    @Test
    fun testPlainTextWithoutLatex() {
        val raw = "这是一条普通的知识点纯文本，不包含任何特殊符号。"
        assertFalse(MathTextPreprocessor.containsLatex(raw))
        assertEquals(raw, MathTextPreprocessor.normalizeLatex(raw))
        assertEquals(raw, MathTextPreprocessor.formatToUnicode(raw))
    }
}
