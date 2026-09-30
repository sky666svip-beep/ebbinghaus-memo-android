package com.ebbinghaus.memo.core.util

/**
 * 数学与逻辑符号智能预处理器
 *
 * 提供两种核心能力：
 * 1. [normalizeLatex]: 智能识别无 $ 包裹的 LaTeX 公式表达式并自适应补全 $ 标识，供 KaTeX 渲染引擎解析；
 * 2. [formatToUnicode]: 将常见 LaTeX 命令映射转换为标准可读 Unicode 数学符号，为纯文本组件（如列表卡片）提供流畅的原生排版。
 */
object MathTextPreprocessor {

    private val LATEX_CMD_REGEX = Regex(
        """\\(le|leq|ge|geq|iff|implies|lor|land|oplus|otimes|odot|neq|ne|approx|equiv|pm|mp|times|div|cdot|to|rightarrow|leftarrow|leftrightarrow|forall|exists|in|notin|subset|subseteq|cap|cup|emptyset|infty|sqrt|frac|sum|prod|int|alpha|beta|gamma|delta|epsilon|theta|lambda|mu|pi|sigma|omega|Delta|Sigma|Omega)(?![a-zA-Z])"""
    )

    private val UNICODE_MAP: List<Pair<Regex, String>> = listOf(
        Regex("""\\iff(?![a-zA-Z])""") to " ⟺ ",
        Regex("""\\implies(?![a-zA-Z])""") to " ⟹ ",
        Regex("""\\(le|leq)(?![a-zA-Z])""") to " ≤ ",
        Regex("""\\(ge|geq)(?![a-zA-Z])""") to " ≥ ",
        Regex("""\\lor(?![a-zA-Z])""") to " ∨ ",
        Regex("""\\land(?![a-zA-Z])""") to " ∧ ",
        Regex("""\\oplus(?![a-zA-Z])""") to " ⊕ ",
        Regex("""\\otimes(?![a-zA-Z])""") to " ⊗ ",
        Regex("""\\odot(?![a-zA-Z])""") to " ⊙ ",
        Regex("""\\(neq|ne)(?![a-zA-Z])""") to " ≠ ",
        Regex("""\\approx(?![a-zA-Z])""") to " ≈ ",
        Regex("""\\equiv(?![a-zA-Z])""") to " ≡ ",
        Regex("""\\pm(?![a-zA-Z])""") to " ± ",
        Regex("""\\mp(?![a-zA-Z])""") to " ∓ ",
        Regex("""\\times(?![a-zA-Z])""") to " × ",
        Regex("""\\div(?![a-zA-Z])""") to " ÷ ",
        Regex("""\\cdot(?![a-zA-Z])""") to " · ",
        Regex("""\\(to|rightarrow)(?![a-zA-Z])""") to " → ",
        Regex("""\\leftarrow(?![a-zA-Z])""") to " ← ",
        Regex("""\\leftrightarrow(?![a-zA-Z])""") to " ↔ ",
        Regex("""\\forall(?![a-zA-Z])""") to "∀",
        Regex("""\\exists(?![a-zA-Z])""") to "∃",
        Regex("""\\in(?![a-zA-Z])""") to " ∈ ",
        Regex("""\\notin(?![a-zA-Z])""") to " ∉ ",
        Regex("""\\subset(?![a-zA-Z])""") to " ⊂ ",
        Regex("""\\subseteq(?![a-zA-Z])""") to " ⊆ ",
        Regex("""\\cap(?![a-zA-Z])""") to " ∩ ",
        Regex("""\\cup(?![a-zA-Z])""") to " ∪ ",
        Regex("""\\infty(?![a-zA-Z])""") to "∞",
        Regex("""\\alpha(?![a-zA-Z])""") to "α",
        Regex("""\\beta(?![a-zA-Z])""") to "β",
        Regex("""\\gamma(?![a-zA-Z])""") to "γ",
        Regex("""\\delta(?![a-zA-Z])""") to "δ",
        Regex("""\\theta(?![a-zA-Z])""") to "θ",
        Regex("""\\lambda(?![a-zA-Z])""") to "λ",
        Regex("""\\mu(?![a-zA-Z])""") to "μ",
        Regex("""\\pi(?![a-zA-Z])""") to "π",
        Regex("""\\sigma(?![a-zA-Z])""") to "σ",
        Regex("""\\omega(?![a-zA-Z])""") to "ω",
        Regex("""\\Delta(?![a-zA-Z])""") to "Δ",
        Regex("""\\Sigma(?![a-zA-Z])""") to "Σ",
        Regex("""\\Omega(?![a-zA-Z])""") to "Ω"
    )

    /**
     * Markdown 语法特征（仅匹配真正需要富文本渲染的结构，避免把普通标点误判为 Markdown）。
     */
    private val MARKDOWN_PATTERNS = listOf(
        Regex("""^#{1,6}\s""", RegexOption.MULTILINE),              // ATX 标题 # ~ ######
        Regex("""\*\*[^\s*][^*]*\*\*"""),                          // 粗体 **text**
        Regex("""__[^\s_][^_]*__"""),                              // 粗体 __text__
        Regex("""(?<![*\w])\*(?!\s)[^*\n]+\*(?![*\w])"""),         // 斜体 *text*
        Regex("""`[^`\n]+`"""),                                    // 行内代码 `code`
        Regex("""^\s*```""", RegexOption.MULTILINE),               // 代码块 ```
        Regex("""^\s*[-*+]\s+\S""", RegexOption.MULTILINE),        // 无序列表 - / * / +
        Regex("""^\s*\d+\.\s+\S""", RegexOption.MULTILINE),        // 有序列表 1.
        Regex("""^\s*>\s+\S""", RegexOption.MULTILINE),            // 引用 >
        Regex("""\[[^\]\n]+\]\([^)\n]+\)"""),                      // 链接 [text](url)
        Regex("""^\s*([-*_])(\s*\1){2,}\s*$""", RegexOption.MULTILINE) // 分割线 --- / *** / ___
    )

    /**
     * 判断文本是否包含数学/LaTeX 表达式
     */
    fun containsLatex(text: String): Boolean {
        if (text.isBlank()) return false
        return text.contains("$") || LATEX_CMD_REGEX.containsMatchIn(text)
    }

    /**
     * 判断文本是否包含 Markdown 语法。
     *
     * 用于让纯文本内容走原生 `Text` 快路径（零 WebView 开销），
     * 只有真正需要富文本排版时才启用 KaTeX/Markdown 渲染管线。
     */
    fun containsMarkdown(text: String): Boolean {
        if (text.isBlank()) return false
        return MARKDOWN_PATTERNS.any { it.containsMatchIn(text) }
    }

    /**
     * 是否需要富文本渲染（Markdown 或 LaTeX 任一命中）。
     *
     * 这是 [com.ebbinghaus.memo.ui.component.MathView] 选择渲染路径的唯一判据：
     * 返回 false 时使用原生文本渲染，完全不创建 WebView。
     */
    fun needsRichRendering(text: String): Boolean =
        containsLatex(text) || containsMarkdown(text)

    /**
     * 自适应将非 $ 包裹的 LaTeX 公式规整化，添加行内公式 $ 定界符
     */
    fun normalizeLatex(text: String): String {
        if (text.isBlank()) return ""
        if (text.contains("$")) return text
        if (!LATEX_CMD_REGEX.containsMatchIn(text)) return text

        // 针对没有 $ 的内容，按常见中文分句和换行进行切分处理
        val segments = text.split(Regex("(?<=[，。；\n\r])|(?=[，。；\n\r])"))
        return segments.joinToString("") { segment ->
            if (LATEX_CMD_REGEX.containsMatchIn(segment)) {
                val trimmed = segment.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("$")) {
                    "$$trimmed$"
                } else {
                    segment
                }
            } else {
                segment
            }
        }
    }

    /**
     * 将包含 LaTeX 语法的文本转换为高可读的 Unicode 字符串
     *
     * 例如：
     * `uA\le uB \iff CF=1 \lor ZF=1` -> `uA ≤ uB ⟺ CF=1 ∨ ZF=1`
     * `A\le B \iff (SF\oplus OF=1)\lor ZF=1` -> `A ≤ B ⟺ (SF ⊕ OF=1) ∨ ZF=1`
     */
    fun formatToUnicode(text: String): String {
        if (text.isBlank()) return ""
        // 去除多余的 $ 或 $$
        var result = text.replace("$$", "").replace("$", "")
        for ((regex, replacement) in UNICODE_MAP) {
            result = regex.replace(result, replacement)
        }
        // 处理多个连续空格规范化
        return result.replace(Regex("[ ]{2,}"), " ")
    }
}
