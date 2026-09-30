package com.ebbinghaus.memo.ui

import com.ebbinghaus.memo.ui.memolist.hasUnsavedEdits
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑器「未保存改动」判定单元测试（P1-5）
 *
 * 关闭编辑器（点 ✕ 或按返回键）前需据此判断是否弹出「放弃编辑？」二次确认：
 * 有改动才打扰用户，无改动直接关闭。
 *
 * 覆盖：内容 / 笔记 / 标签任一字段变化即视为有改动；初值全空且未输入时视为无改动；
 * 仅尾部空白差异、正文清空亦属「有改动」（避免静默丢弃用户输入）。
 */
class MemoEditorDraftStateTest {

    @Test
    fun noChange_returnsFalse() {
        assertFalse(
            hasUnsavedEdits(
                initialContent = "内容",
                initialNotes = "笔记",
                initialTagsInput = "A, B",
                currentContent = "内容",
                currentNotes = "笔记",
                currentTagsInput = "A, B"
            )
        )
    }

    @Test
    fun contentChanged_returnsTrue() {
        assertTrue(
            hasUnsavedEdits(
                initialContent = "内容",
                initialNotes = "笔记",
                initialTagsInput = "A",
                currentContent = "内容（已修改）",
                currentNotes = "笔记",
                currentTagsInput = "A"
            )
        )
    }

    @Test
    fun notesChanged_returnsTrue() {
        assertTrue(
            hasUnsavedEdits(
                initialContent = "内容",
                initialNotes = "笔记",
                initialTagsInput = "A",
                currentContent = "内容",
                currentNotes = "笔记（已修改）",
                currentTagsInput = "A"
            )
        )
    }

    @Test
    fun tagsOnlyChange_isTrue() {
        // 仅标签变化即视为有改动（含「原本无标签 → 新增标签」场景）
        assertTrue(hasUnsavedEdits("内容", "笔记", "A", "内容", "笔记", "A, B"))
        assertTrue(hasUnsavedEdits("", "", "A", "", "", ""))
    }

    @Test
    fun newMemoUntouched_returnsFalse() {
        // 新增场景：初值均为空，未输入任何内容时关闭不应打扰用户
        assertFalse(
            hasUnsavedEdits(
                initialContent = "",
                initialNotes = "",
                initialTagsInput = "",
                currentContent = "",
                currentNotes = "",
                currentTagsInput = ""
            )
        )
    }

    @Test
    fun newMemoWithTypedContent_returnsTrue() {
        assertTrue(
            hasUnsavedEdits(
                initialContent = "",
                initialNotes = "",
                initialTagsInput = "",
                currentContent = "新输入的知识点",
                currentNotes = "",
                currentTagsInput = ""
            )
        )
    }

    @Test
    fun whitespaceOnlyDifference_isTrue() {
        // 仅尾部空格差异也应视为「有改动」，避免静默丢弃用户输入
        assertTrue(hasUnsavedEdits("内容", "笔记", "A", "内容 ", "笔记", "A"))
        assertTrue(hasUnsavedEdits("内容", "笔记", "A", "内容", "笔记 ", "A"))
        assertTrue(hasUnsavedEdits("内容", "笔记", "A", "内容", "笔记", "A "))
    }

    @Test
    fun contentClearedToEmpty_isTrue() {
        // 编辑场景把正文清空同样属于未保存改动
        assertTrue(hasUnsavedEdits("原内容", "笔记", "A", "", "笔记", "A"))
    }
}
