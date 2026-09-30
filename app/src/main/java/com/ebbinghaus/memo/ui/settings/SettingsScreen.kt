package com.ebbinghaus.memo.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ebbinghaus.memo.crash.CrashLogger
import com.ebbinghaus.memo.ui.theme.Dimens
import com.ebbinghaus.memo.ui.util.WindowSizeClass
import com.ebbinghaus.memo.ui.util.WindowWidthSizeClass

/** 每日复习上限可调区间 */
private const val DAILY_LIMIT_MAX = 50
private const val DAILY_LIMIT_MIN = 0

/**
 * 设置界面（一级 Tab）
 *
 * 提供每日复习上限（0~50）滑块调节、预设快捷按钮及艾宾浩斯智能调度机制深度说明。
 * 作为一级 Tab，TopAppBar **不含返回箭头**（底栏已可回到知识库）。
 *
 * 按断点适配：≥600dp（Medium/Expanded）时滑块卡与规则卡并排两列。
 *
 * @param viewModel 设置 ViewModel
 * @param windowSizeClass 当前窗口尺寸类别
 * @param onNavigateToReview 规则说明中的「去复习」入口
 * @param modifier 外部修饰器
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    windowSizeClass: WindowSizeClass,
    onNavigateToReview: () -> Unit,
    modifier: Modifier = Modifier,
    dataSafetyViewModel: DataSafetyViewModel? = null,
    onNavigateToTrash: (() -> Unit)? = null,
    crashLogger: CrashLogger? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val useTwoColumns = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    // 「关于」分组内的崩溃日志入口：仅在存在崩溃记录时渲染（由 CrashLogSectionHost 自行判定）
    val logger = crashLogger
    val crashLogSection: (@Composable () -> Unit)? = if (logger != null) {
        { CrashLogSectionHost(crashLogger = logger) }
    } else {
        null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "偏好设置",
                        style = MaterialTheme.typography.titleLarge
                    )
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
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXL)
        ) {
            if (useTwoColumns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
                ) {
                    // 分组一：每日复习上限
                    DailyLimitCard(
                        dailyLimit = uiState.dailyLimit,
                        onLimitChange = { viewModel.onEvent(SettingsUiEvent.OnDailyLimitChange(it)) },
                        loadError = uiState.loadError,
                        modifier = Modifier.weight(1f)
                    )
                    // 分组二：智能调度规则说明
                    SchedulingRulesCard(modifier = Modifier.weight(1f))
                }
                // 分组三：关于
                AboutCard(modifier = Modifier.fillMaxWidth(), crashLogSection = crashLogSection)
            } else {
                DailyLimitCard(
                    dailyLimit = uiState.dailyLimit,
                    onLimitChange = { viewModel.onEvent(SettingsUiEvent.OnDailyLimitChange(it)) },
                    loadError = uiState.loadError,
                    modifier = Modifier.fillMaxWidth()
                )
                SchedulingRulesCard(modifier = Modifier.fillMaxWidth())
                AboutCard(modifier = Modifier.fillMaxWidth(), crashLogSection = crashLogSection)
            }

            // 分组四：数据与安全（导出 / 导入 / 自动快照 / 回收站）
            if (dataSafetyViewModel != null && onNavigateToTrash != null) {
                DataSafetySectionHost(
                    viewModel = dataSafetyViewModel,
                    onNavigateToTrash = onNavigateToTrash,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 底部：从设置页直达今日复习批次
            if (uiState.dailyLimit > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "调整完成后，可立即进入今日批次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(Dimens.SpaceS))
                    TextButton(
                        onClick = onNavigateToReview,
                        modifier = Modifier.height(Dimens.TouchTarget)
                    ) {
                        Text(text = "去复习")
                    }
                }
            } else {
                Text(
                    text = "当前已停用复习提醒，调高上限后可从底部「复习」进入今日批次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * 分组一：每日复习数量上限设置卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyLimitCard(
    dailyLimit: Int,
    onLimitChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    loadError: String? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceXL)
        ) {
            SettingsSectionHeader(
                icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(Dimens.SpaceXXL)) },
                title = "每日复习数量上限"
            )

            // 设置读取失败时的可见提示（第 2 轮加固）：避免用户把默认值误认为真实设置
            if (loadError != null) {
                Spacer(modifier = Modifier.height(Dimens.SpaceS))
                Text(
                    text = loadError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 大号数值高亮展示
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (dailyLimit == 0) {
                    Text(
                        text = "0 条（已停用复习提醒）",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                } else if (dailyLimit >= DAILY_LIMIT_MAX) {
                    Text(
                        text = "$dailyLimit",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "条 / 天（每日最多 50 条）",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                } else {
                    Text(
                        text = "$dailyLimit",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "条 / 天",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 0~50 连续滑块
            Slider(
                value = dailyLimit.toFloat(),
                onValueChange = { onLimitChange(it.toInt()) },
                valueRange = DAILY_LIMIT_MIN.toFloat()..DAILY_LIMIT_MAX.toFloat(),
                steps = DAILY_LIMIT_MAX - 1,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(Dimens.ItemSpacing))

            // 快捷预设按钮
            Text(
                text = "快捷选项：",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(0, 10, 20, 30, 50).forEach { preset ->
                    val label = if (preset == 0) "0 (停用)" else "$preset 条"
                    FilterChip(
                        selected = dailyLimit == preset,
                        onClick = { onLimitChange(preset) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

/**
 * 分组二：智能调度与顺延规则说明卡片
 */
@Composable
private fun SchedulingRulesCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceXL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
        ) {
            SettingsSectionHeader(
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(Dimens.SpaceXXL)) },
                title = "智能调度与顺延规则说明"
            )

            RuleItem(
                title = "1. 最早到期优先（Earliest Due Date First）",
                description = "当到期复习任务数大于每日上限时，系统按到期时间最早优先排序截取额度，防止旧知识点彻底遗忘。"
            )

            RuleItem(
                title = "2. 超量任务自动顺延",
                description = "超出每日上限的任务平滑滑动顺延至次日，动态窗口流转，不会堆叠丢失，保障每天复习量轻松可控。"
            )

            RuleItem(
                title = "3. 上限为 0 暂停提醒",
                description = "当每日上限设为 0 时，当日待复习数量为 0，系统不会弹出提醒看板，适合休息或专注录入新知识点。"
            )

            RuleItem(
                title = "4. 跨天与多天未登录零惩罚合并",
                description = "长时间未打开应用后重新开启，所有到期未复习任务自动合并入候选池，不标记逾期，不扣减记忆档位。"
            )

            RuleItem(
                title = "5. 标准 6 档与长周期晋升",
                description = "标准复习间隔为 1天、2天、4天、7天、15天、30天；第 6 档再次“记住”进入 60 天长周期深度固化。"
            )
        }
    }
}

/**
 * 分组三：关于卡片
 *
 * @param crashLogSection 「崩溃日志」入口插槽（仅当存在崩溃记录时由宿主渲染内容）
 */
@Composable
private fun AboutCard(
    modifier: Modifier = Modifier,
    crashLogSection: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceXL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
        ) {
            SettingsSectionHeader(
                icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(Dimens.SpaceXXL)) },
                title = "关于"
            )

            AboutRow(label = "应用名称", value = "艾宾浩斯知识点备忘录")
            AboutRow(label = "版本", value = "1.0.0")
            AboutRow(label = "数据存储", value = "全部数据保存在本机数据库，无需联网")
            AboutRow(label = "调度算法", value = "6 档间隔 + 60 天长周期固化")

            Spacer(modifier = Modifier.height(Dimens.SpaceXS))

            Text(
                text = "本应用遵循艾宾浩斯遗忘曲线设计，只做温和提醒，不会标记逾期或制造焦虑。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // 崩溃日志入口（仅存在崩溃记录时可见）
            if (crashLogSection != null) {
                Spacer(modifier = Modifier.height(Dimens.SpaceM))
                crashLogSection()
            }
        }
    }
}

/**
 * 分组卡片标题行
 */
@Composable
private fun SettingsSectionHeader(
    icon: @Composable () -> Unit,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(Dimens.SpaceS))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

/**
 * 规则解释子项
 */
@Composable
private fun RuleItem(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.ItemSpacing),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceM)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(Dimens.SpaceXS))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 关于卡片中的一行键值对
 */
@Composable
private fun AboutRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
