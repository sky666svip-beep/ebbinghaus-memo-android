package com.ebbinghaus.memo.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebbinghaus.memo.core.model.ReviewStage
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.ui.component.MathView

/** 卡片高度变化（笔记抽屉展开/收起）的平滑过渡时长（毫秒） */
private const val CARD_SIZE_ANIM_MS = 180

/**
 * 复习卡片与抽屉式即时笔记编辑组件
 *
 * 支持卡片内容翻转展开、记忆线索查看，以及在不打断复习心流的前提下边复习边修改笔记并实时持久化。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewCardComponent(
    item: MemoWithReviewTask,
    isDetailExpanded: Boolean,
    isEditingNotes: Boolean,
    notesDraft: String,
    onToggleDetails: () -> Unit,
    onStartEditNotes: () -> Unit,
    onCancelEditNotes: () -> Unit,
    onNotesDraftChanged: (String) -> Unit,
    onSaveNotes: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val stage = ReviewStage.fromLevel(item.reviewTask.stageLevel)
    val stageLabel = if (stage == ReviewStage.LONG_TERM_60) {
        "长周期复习 (${stage.intervalDays}天/次)"
    } else {
        "第 ${stage.level} 档 (${stage.intervalDays}天后复习)"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            // 笔记抽屉展开/收起时卡片高度平滑过渡，
            // 避免内部 MathView 异步测量导致的高度突变带动下方按钮区抖动
            .animateContentSize(animationSpec = tween(CARD_SIZE_ANIM_MS)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 1. 顶部档位与到期标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stageLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "累计复习 ${item.reviewTask.reviewCount} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 知识点正文（支持 LaTeX 数学与逻辑公式高保真排版）
            MathView(
                text = item.memo.content,
                fontSize = 18.sp,
                minHeight = 44.dp
            )


            // 3. 标签列表
            if (item.memo.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.memo.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. 展开/收起记忆线索与笔记详情控制栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleDetails() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDetailExpanded) "收起个人笔记与线索" else "查看个人笔记与记忆提示",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Icon(
                    imageVector = if (isDetailExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }

            // 5. 个人笔记展示与即时编辑抽屉
            AnimatedVisibility(visible = isDetailExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    if (isEditingNotes) {
                        // 正在编辑笔记
                        OutlinedTextField(
                            value = notesDraft,
                            onValueChange = onNotesDraftChanged,
                            label = { Text("编辑个人笔记") },
                            placeholder = { Text("记录记忆线索、解法要点或关键反思...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onCancelEditNotes) {
                                Text("取消")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { onSaveNotes(notesDraft.trim()) }) {
                                Text("保存笔记")
                            }
                        }
                    } else {
                        // 展示笔记内容
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (item.memo.notes.isNotBlank()) {
                                        MathView(
                                            text = item.memo.notes,
                                            fontSize = 15.sp,
                                            minHeight = 28.dp
                                        )
                                    } else {
                                        Text(
                                            text = "（暂无个人笔记，点击右侧铅笔即可添加）",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }


                                IconButton(
                                    onClick = onStartEditNotes,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "修改笔记",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
