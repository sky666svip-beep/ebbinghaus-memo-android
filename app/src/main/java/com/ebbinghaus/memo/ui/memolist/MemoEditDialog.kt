package com.ebbinghaus.memo.ui.memolist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity

/** 知识点内容输入框最小高度：全屏模式下应占据主要编辑区 */
private val CONTENT_FIELD_MIN_HEIGHT = 240.dp

/** 笔记输入框最小高度 */
private val NOTES_FIELD_MIN_HEIGHT = 132.dp

/**
 * 知识点新增 / 编辑 —— **全屏沉浸式编辑器**。
 *
 * 设计取舍（替代此前的 AlertDialog 方案）：
 * - **全屏承载**：以 `Dialog(usePlatformDefaultWidth = false)` + `fillMaxSize` 铺满屏幕，
 *   去掉对话框的宽度/高度上限与外部遮罩，为长文本、Markdown 与 LaTeX 公式提供最大编辑区；
 * - **零干扰**：仅保留顶部一条 `TopAppBar`（关闭 / 标题 / 保存），无多余装饰与次级操作；
 * - **键盘友好**：`imePadding()` 让内容区随输入法抬升，长文编辑时正文始终可见；
 * - **内容区滚动**：整体超高时由内容区滚动承接，顶部栏与保存入口恒定可达。
 *
 * 标签区（P2-8）：**文本框（自由输入）+ 候选 Chip（点击追加去重）** 混合模式，
 * 既保留「自由输入新标签」能力，又补齐「从已有标签点选」体验。
 *
 * 遵循艾宾浩斯规范：修改内容时完全保留既有复习进度。
 *
 * @param memo 为 null 表示新增，非 null 表示编辑
 * @param allTags 全部已有标签（用于候选 Chip）；默认空列表以保护既有调用点
 * @param onDismiss 关闭编辑器（不保存）
 * @param onSave 保存回调：知识点 ID（新增为 null）、内容、笔记、标签列表
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoEditDialog(
    memo: KnowledgeMemoEntity?,
    allTags: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (id: Long?, content: String, notes: String, tags: List<String>) -> Unit
) {
    val initialContent = remember(memo) { memo?.content ?: "" }
    val initialNotes = remember(memo) { memo?.notes ?: "" }
    val initialTagsInput = remember(memo) { memo?.tags?.joinToString(", ") ?: "" }

    var content by remember(memo) { mutableStateOf(initialContent) }
    var notes by remember(memo) { mutableStateOf(initialNotes) }
    var tagsInput by remember(memo) { mutableStateOf(initialTagsInput) }
    var isError by remember { mutableStateOf(false) }

    // 关闭前「放弃编辑？」二次确认是否可见（P1-5）
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val isEditing = memo != null

    val submit: () -> Unit = {
        if (content.trim().isBlank()) {
            isError = true
        } else {
            val parsedTags = tagsInput
                .split(",", "，", "、", " ")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            onSave(
                memo?.id,
                content.trim(),
                notes.trim(),
                parsedTags
            )
        }
    }

    // 关闭请求：有未保存改动时先弹二次确认，避免长文本误触即丢（P1-5）；
    // 无改动时直接关闭，不打扰用户。
    val requestDismiss: () -> Unit = {
        if (hasUnsavedEdits(
                initialContent = initialContent,
                initialNotes = initialNotes,
                initialTagsInput = initialTagsInput,
                currentContent = content,
                currentNotes = notes,
                currentTagsInput = tagsInput
            )
        ) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (isEditing) "编辑知识点" else "新增知识点",
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = requestDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭编辑器"
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = submit) {
                                Text(
                                    text = "保存",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))

                    OutlinedTextField(
                        value = content,
                        onValueChange = {
                            content = it
                            if (it.isNotBlank()) isError = false
                        },
                        label = { Text("知识点内容 *") },
                        placeholder = {
                            Text(
                                "输入需要记忆的知识点内容\n\n" +
                                    "支持 Markdown（# 标题、**粗体**、- 列表、> 引用、`代码`）\n" +
                                    "与 LaTeX 公式（如 \\frac{a}{b}、\$x^2\$）"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = CONTENT_FIELD_MIN_HEIGHT),
                        minLines = 8,
                        maxLines = 30,
                        isError = isError,
                        supportingText = {
                            if (isError) {
                                Text(
                                    text = "知识点内容不能为空",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { tagsInput = it },
                        label = { Text("分类标签") },
                        placeholder = { Text("用逗号或空格分隔，如：Android, Compose") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // 候选 Chip：点击追加到文本框（去重），P2-8
                    if (allTags.isNotEmpty()) {
                        Text(
                            text = "从已有标签快速添加",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            allTags.forEach { tag ->
                                SuggestionChip(
                                    onClick = { tagsInput = appendTagToInput(tagsInput, tag) },
                                    label = {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("个人笔记与记忆提示") },
                        placeholder = { Text("可记录解题思路、联想记忆法或核心摘要，同样支持 Markdown 与公式") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = NOTES_FIELD_MIN_HEIGHT),
                        minLines = 4,
                        maxLines = 16
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // 未保存改动关闭确认（P1-5）：仅在存在改动时出现，无改动直接关闭
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃编辑？") },
            text = { Text("当前修改尚未保存，关闭后将会丢失。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    }
                ) {
                    Text("放弃")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("继续编辑")
                }
            }
        )
    }
}
