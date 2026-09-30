package com.ebbinghaus.memo.ui.memolist

/**
 * 编辑器「未保存改动」判定（P1-5）
 *
 * 从 Compose 组件中抽出的纯函数，便于 JVM 单元测试直接覆盖：只要正文 / 笔记 / 标签输入
 * 任一项相对进入编辑器时的初值发生变化，即视为存在未保存改动，关闭前需要二次确认。
 *
 * @param initialContent 进入编辑器时的正文初值
 * @param initialNotes 进入编辑器时的笔记初值
 * @param initialTagsInput 进入编辑器时的标签输入框初值
 * @param currentContent 当前正文
 * @param currentNotes 当前笔记
 * @param currentTagsInput 当前标签输入框文本
 * @return 是否存在未保存改动
 */
internal fun hasUnsavedEdits(
    initialContent: String,
    initialNotes: String,
    initialTagsInput: String,
    currentContent: String,
    currentNotes: String,
    currentTagsInput: String
): Boolean =
    currentContent != initialContent ||
        currentNotes != initialNotes ||
        currentTagsInput != initialTagsInput

/**
 * 将候选标签**追加**到标签输入框文本（P2-8，纯函数，可单测）。
 *
 * 语义（PRD §4.7 / 决策 8）：
 * - 点击候选 Chip = 追加而非替换，自动去重（已存在的标签不重复追加）；
 * - 空 / 空白标签为无操作（返回原文）；
 * - 输入框仍按 `split(",", "，", "、", " ")` 解析为 `List<String>`（去重去空），
 *   故追加分隔符统一使用 `", "`，保证追加结果可被同一解析口径还原。
 *
 * @param input 当前标签输入框文本
 * @param tag 待追加的候选标签
 * @return 追加后的标签输入框文本（已存在或空白时原样返回）
 */
internal fun appendTagToInput(input: String, tag: String): String {
    val cleanTag = tag.trim()
    if (cleanTag.isEmpty()) return input
    val existing = input
        .split(",", "，", "、", " ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (existing.contains(cleanTag)) return input
    return if (input.isBlank()) cleanTag else input.trimEnd() + ", " + cleanTag
}
