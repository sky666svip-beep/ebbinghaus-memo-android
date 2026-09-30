package com.ebbinghaus.memo.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ebbinghaus.memo.core.model.ReviewStage
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.data.repository.ReviewRepository
import com.ebbinghaus.memo.ui.component.DeleteConfirmDialog
import com.ebbinghaus.memo.ui.component.EmptyState
import com.ebbinghaus.memo.ui.component.MathView
import com.ebbinghaus.memo.ui.memolist.MemoEditDialog
import com.ebbinghaus.memo.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 全屏知识点完整详情查看页面
 *
 * 完整展示知识点正文（集成 KaTeX 数学与逻辑公式高保真排版）、个人笔记、标签列表与艾宾浩斯复习状态。
 * 支持在阅读状态下一键唤起编辑或删除操作。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoDetailScreen(
    viewModel: MemoDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 监听删除成功，自动返回列表页（状态矩阵 S11）
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            Toast.makeText(context, "知识点已删除", Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "知识点详情",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    val memo = uiState.memo
                    if (memo != null) {
                        // 复制正文
                        IconButton(onClick = {
                            copyToClipboard(context, memo.content)
                            Toast.makeText(context, "正文已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制正文"
                            )
                        }
                        // 编辑按钮
                        IconButton(onClick = { viewModel.onEvent(MemoDetailUiEvent.OnOpenEditDialog) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑知识点",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        // 删除按钮
                        IconButton(onClick = { viewModel.onEvent(MemoDetailUiEvent.OnOpenDeleteDialog) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除知识点",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MemoDetailContent(viewModel = viewModel)
        }
    }
}

/**
 * 知识点详情纯内容区（不含 Scaffold）
 *
 * 供全屏详情页与 Expanded 双窗格右 Pane 复用；内部自行处理加载 / 空 / 正常三态。
 *
 * @param viewModel 详情 ViewModel
 * @param modifier 外部修饰器
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoDetailContent(
    viewModel: MemoDetailViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.isDeleted || uiState.memo == null -> {
            EmptyState(
                icon = Icons.Default.Info,
                title = "该知识点不存在或已被移除",
                subtitle = "可以从左侧列表重新选择一条知识点查看",
                modifier = modifier
            )
        }

        else -> {
            val memo = uiState.memo
            val task = uiState.reviewTask
            if (memo == null) return

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.ScreenPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
            ) {
                Spacer(modifier = Modifier.height(Dimens.SpaceXS))

                // 1. 核心正文卡片（支持完整 LaTeX 数学与逻辑公式高保真排版）
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.RadiusCard),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = Dimens.SpaceM)
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimens.SpaceXL)
                            )
                            Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                            Text(
                                text = "知识点正文",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 完整无截断的数学/文本渲染
                        MathView(
                            text = memo.content,
                            fontSize = 17.sp,
                            minHeight = 40.dp
                        )
                    }
                }

                // 2. 标签分类卡片
                if (memo.tags.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "标签分类",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(Dimens.SpaceS))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
                            ) {
                                memo.tags.forEach { tag ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(tag) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 个人笔记与提示卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.RadiusCard),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = Dimens.SpaceM)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoteAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(Dimens.SpaceXL)
                            )
                            Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                            Text(
                                text = "个人笔记与记忆线索",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (memo.notes.isNotBlank()) {
                            MathView(
                                text = memo.notes,
                                fontSize = 15.sp,
                                minHeight = 32.dp
                            )
                        } else {
                            Text(
                                text = "暂无笔记内容，点击下方编辑按钮可随时追加个人心得或助记线索。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // 4. 艾宾浩斯复习状态卡片
                if (task != null) {
                    val stage = ReviewStage.fromLevel(task.stageLevel)
                    val stageText = if (stage == ReviewStage.LONG_TERM_60) {
                        "长周期复习 (每 60 天一次)"
                    } else {
                        "第 ${stage.level} 档 (间隔 ${stage.intervalDays} 天)"
                    }

                    val createdDateStr = remember(memo.createdAt) {
                        val instant = Instant.ofEpochMilli(memo.createdAt)
                        val zonedDateTime = instant.atZone(ZoneId.systemDefault())
                        zonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Dimens.RadiusCard),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                                Text(
                                    text = "艾宾浩斯复习进度追踪",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("当前档位：", style = MaterialTheme.typography.bodyMedium)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = stageText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = Dimens.SpaceS, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("下次复习日期：", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = task.dueDate.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("累计复习次数：", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${task.reviewCount} 次",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("录入创建时间：", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = createdDateStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                // 5. 底部操作按钮组
                // 注：不叠加 navigationBarsPadding()——父 Column 已消费 Scaffold 的 innerPadding
                // （其 bottom 即系统导航条高度），重复叠加会造成双倍留白
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.SpaceM),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
                ) {
                    Button(
                        onClick = { viewModel.onEvent(MemoDetailUiEvent.OnOpenEditDialog) },
                        modifier = Modifier
                            .weight(1f)
                            .height(Dimens.TouchTarget),
                        shape = RoundedCornerShape(Dimens.RadiusButton)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                        Text("编辑知识点")
                    }

                    OutlinedButton(
                        onClick = { viewModel.onEvent(MemoDetailUiEvent.OnOpenDeleteDialog) },
                        modifier = Modifier.height(Dimens.TouchTarget),
                        shape = RoundedCornerShape(Dimens.RadiusButton),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                        Text("删除")
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.SpaceXXL))
            }

            // 编辑弹窗
            if (uiState.isEditorDialogVisible) {
                MemoEditDialog(
                    memo = memo,
                    onDismiss = { viewModel.onEvent(MemoDetailUiEvent.OnDismissEditDialog) },
                    onSave = { id, content, notes, tags ->
                        val targetId = id ?: memo.id
                        viewModel.onEvent(MemoDetailUiEvent.OnSaveMemo(targetId, content, notes, tags))
                    }
                )
            }

            // 双窗格模式下的删除确认弹窗（全屏模式由 MemoDetailScreen 负责）
            if (uiState.isDeleteDialogOpen) {
                DeleteConfirmDialog(
                    memoPreview = memo.content,
                    onConfirm = { viewModel.onEvent(MemoDetailUiEvent.OnConfirmDelete) },
                    onDismiss = { viewModel.onEvent(MemoDetailUiEvent.OnDismissDeleteDialog) }
                )
            }
        }
    }
}

/**
 * 双窗格右 Pane 专用的详情容器
 *
 * 按 `memoId` 创建目的地级 `MemoDetailViewModel`（key = `memo_detail_{id}`），
 * 并渲染无 Scaffold 的 [MemoDetailContent]。
 *
 * @param memoId 目标知识点 ID
 * @param memoRepository 知识点仓储
 * @param reviewRepository 复习仓储
 * @param modifier 外部修饰器
 */
@Composable
fun MemoDetailPane(
    memoId: Long,
    memoRepository: MemoRepository,
    reviewRepository: ReviewRepository,
    modifier: Modifier = Modifier
) {
    val detailViewModel: MemoDetailViewModel = viewModel(
        key = "memo_detail_$memoId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MemoDetailViewModel(
                    memoId = memoId,
                    memoRepository = memoRepository,
                    reviewRepository = reviewRepository
                ) as T
            }
        }
    )
    MemoDetailContent(viewModel = detailViewModel, modifier = modifier)
}

/**
 * 复制文本到系统剪贴板
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("KnowledgeMemo", text))
}
