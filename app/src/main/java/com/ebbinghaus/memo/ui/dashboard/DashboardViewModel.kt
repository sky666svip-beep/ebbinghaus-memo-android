package com.ebbinghaus.memo.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebbinghaus.memo.core.engine.RolloverEngine
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.data.repository.ReviewRepository
import com.ebbinghaus.memo.data.repository.SettingsRepository
import com.ebbinghaus.memo.data.snapshot.SnapshotFailureReason
import com.ebbinghaus.memo.data.snapshot.SnapshotManager
import com.ebbinghaus.memo.data.snapshot.SnapshotResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 启动期副作用诊断日志标签（冷启动崩溃排查用） */
private const val LOG_TAG = "EbbinghausLaunch"

/**
 * 每日看板与应用内提醒 UI 状态模型
 */
data class DashboardUiState(
    val isVisible: Boolean = false,
    val dueTodayCount: Int = 0,
    val totalBacklogCount: Int = 0,
    /** 因超出每日上限而顺延至次日的任务数，供 Banner 副文案与复习完成页复用 */
    val deferredCount: Int = 0,
    val isMultiDayAbsence: Boolean = false,
    val absentDays: Int = 0,
    val dailyLimit: Int = 20
)

/**
 * 看板交互事件
 */
sealed interface DashboardUiEvent {
    data object OnCheckAppLaunch : DashboardUiEvent
    data object OnDismissDashboard : DashboardUiEvent
    data object OnConfirmNavigateToReview : DashboardUiEvent
}

/**
 * 看板一次性消息（启动清理 / 快照失败的 Snackbar 提示）
 */
sealed interface DashboardEffect {
    data class ShowSnackbar(val message: String) : DashboardEffect
}

/**
 * 每日看板与启动提醒 ViewModel
 *
 * 负责应用冷启动与回到前台时的时间基准比对、超量顺延聚合计算、免权限看板提醒判定，
 * 以及首页 Hero Banner 实时状态流输出。
 *
 * 依据架构 §5.3，启动检查（`OnCheckAppLaunch`，同日去重）还承担：
 * 1. E06 过期回收站物理清理（IO，失败不阻断启动）；
 * 2. E02 自动快照检查（失败自动关闭快照开关并提示）。
 */
class DashboardViewModel(
    private val reviewRepository: ReviewRepository,
    private val settingsRepository: SettingsRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val memoRepository: MemoRepository? = null,
    private val snapshotManager: SnapshotManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<DashboardEffect>(replay = 0, extraBufferCapacity = 1)
    val effect: SharedFlow<DashboardEffect> = _effect.asSharedFlow()

    /**
     * 本次会话内已执行过启动检查的自然日。
     *
     * 用于避免同一天内的重复检查：列表页的 `LaunchedEffect` 在每次进入页面
     * （含底部 Tab 往返切换）时都会派发 `OnCheckAppLaunch`，若不拦截会反复查询数据库
     * 并重复写入 `lastActiveDate`，是 Tab 切换卡顿的隐藏来源之一。
     * 跨天后 `today` 变化，仍会正常执行完整检查。
     */
    private var lastCheckedDate: LocalDate? = null

    init {
        // 实时同步今日待复习量与限额，驱动首页 Banner
        //
        // **异常边界（防御性加固 · 第 2 轮补齐）**：本 `init` 协程与 `checkAppLaunchPrompt()`
        // 同文件、同类（`viewModelScope.launch`）、同一批依赖（`getSettings()` / `getDueReviewTasks()`），
        // 且同样在**冷启动首帧之前**随 `AppNavigation` 组合而执行。第 1 轮仅加固了后者，遗漏了本处；
        // 若 Room 抛异常，未捕获异常会冒泡到进程默认异常处理器 → 冷启动闪退。故此处补齐同一模式：
        // `CancellationException` 原样抛出（保持结构化并发），其余异常仅记录日志并降级
        // （角标 / 待复习数可能暂显为 0，但应用可正常启动；详见 CRASH_INVESTIGATION.md 的取舍说明）。
        viewModelScope.launch {
            try {
                val today = todayProvider()
                combine(
                    reviewRepository.getDueReviewTasks(today),
                    settingsRepository.getSettings()
                ) { dueTasks, settings ->
                    val plan = RolloverEngine.planReviewBatch(
                        pendingTasks = dueTasks,
                        today = today,
                        dailyLimit = settings.dailyReviewLimit
                    )
                    Triple(
                        plan.todayBatch.size,
                        plan.totalPendingCount,
                        settings.dailyReviewLimit
                    ) to plan.deferredTasks.size
                }.collect { (counts, deferred) ->
                    val (dueToday, totalBacklog, limit) = counts
                    _uiState.update { current ->
                        current.copy(
                            dueTodayCount = dueToday,
                            totalBacklogCount = totalBacklog,
                            deferredCount = deferred,
                            dailyLimit = limit
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                // 取消信号必须原样抛出，不得吞掉，否则破坏结构化并发
                throw cancellation
            } catch (e: Exception) {
                // 启动期统计流失败一律降级：仅记录日志，不阻断启动。
                Log.e(LOG_TAG, "首页看板统计加载失败，已降级（角标/待复习数可能暂为 0）", e)
            }
        }
    }

    /**
     * UI 事件分发入口
     */
    fun onEvent(event: DashboardUiEvent) {
        when (event) {
            is DashboardUiEvent.OnCheckAppLaunch -> {
                checkAppLaunchPrompt()
            }

            is DashboardUiEvent.OnDismissDashboard -> {
                _uiState.update { it.copy(isVisible = false) }
            }

            is DashboardUiEvent.OnConfirmNavigateToReview -> {
                _uiState.update { it.copy(isVisible = false) }
            }
        }
    }

    /**
     * 执行冷启动/前台唤起时的检查：
     * 0. （每日一次）清理过期回收站条目 + 执行自动快照检查。
     * 1. 比较 lastActiveDate 与当前自然日。
     * 2. 若跨天且今日尚未弹窗提示过，并且上限 > 0 且存在到期复习任务，唤起醒目单条看板弹窗。
     * 3. 记录 lastActiveDate 与 lastPromptedDate。
     *
     * **异常边界（防御性加固）**：本方法由列表页 `LaunchedEffect` 在**冷启动首帧之前**派发，
     * 其 `viewModelScope` 协程内的未捕获异常会直接冒泡到进程默认异常处理器 → 冷启动闪退。
     * 故整条启动期副作用链（回收站物理清理 / SAF 快照 / 设置读写）统一包裹异常边界：
     * 任何非取消类异常仅记录日志并降级（看板提醒可能缺失，但应用可正常启动与使用）；
     * `CancellationException` 原样抛出，保持结构化并发语义。
     */
    private fun checkAppLaunchPrompt() {
        viewModelScope.launch {
            try {
                val today = todayProvider()
                // 同日去重：底部 Tab 往返切换会反复触发本检查，此处直接短路，
                // 避免冗余的数据库查询与 lastActiveDate 写入（跨天时仍会完整执行）
                if (lastCheckedDate == today) return@launch
                lastCheckedDate = today

                // E06：过期回收站物理清理（IO 线程，失败仅忽略，不阻断启动）
                memoRepository?.let { repo ->
                    runCatching { repo.purgeExpiredTrash(System.currentTimeMillis()) }
                }

                // E02：自动快照检查（失败自动关闭开关并提示，不崩溃）
                snapshotManager?.let { manager ->
                    when (val result = manager.checkAndSnapshotOnLaunch()) {
                        is SnapshotResult.Failure -> {
                            manager.disableSnapshot()
                            _effect.emit(DashboardEffect.ShowSnackbar(snapshotFailureMessage(result)))
                        }

                        else -> Unit
                    }
                }

                val settings = settingsRepository.getSettings().first()

                val limit = settings.dailyReviewLimit
                if (limit <= 0) {
                    // 上限为 0 视为停用复习提醒
                    _uiState.update {
                        it.copy(
                            isVisible = false,
                            dueTodayCount = 0,
                            totalBacklogCount = 0,
                            deferredCount = 0,
                            dailyLimit = 0
                        )
                    }
                    settingsRepository.updateLastActiveDate(today)
                    return@launch
                }

                val dueTasks = reviewRepository.getDueReviewTasks(today).first()
                val plan = RolloverEngine.planReviewBatch(
                    pendingTasks = dueTasks,
                    today = today,
                    dailyLimit = limit
                )
                val dueToday = plan.todayBatch.size
                val totalBacklog = plan.totalPendingCount

                val lastActive = settings.lastActiveDate
                val lastPrompted = settings.lastPromptedDate

                val isNewDay = lastActive == null || lastActive.isBefore(today)
                val notPromptedToday = lastPrompted == null || lastPrompted.isBefore(today)

                if (isNewDay && notPromptedToday && dueToday > 0) {
                    val absentDays = if (lastActive != null) {
                        ChronoUnit.DAYS.between(lastActive, today).toInt().coerceAtLeast(1)
                    } else {
                        1
                    }
                    val isMultiDay = absentDays > 1

                    _uiState.update { current ->
                        current.copy(
                            isVisible = true,
                            dueTodayCount = dueToday,
                            totalBacklogCount = totalBacklog,
                            deferredCount = plan.deferredTasks.size,
                            isMultiDayAbsence = isMultiDay,
                            absentDays = absentDays,
                            dailyLimit = limit
                        )
                    }
                    settingsRepository.markDashboardPrompted(today)
                } else {
                    _uiState.update { current ->
                        current.copy(
                            dueTodayCount = dueToday,
                            totalBacklogCount = totalBacklog,
                            deferredCount = plan.deferredTasks.size,
                            dailyLimit = limit
                        )
                    }
                }

                // 更新活跃日期为今日
                settingsRepository.updateLastActiveDate(today)
            } catch (cancellation: CancellationException) {
                // 取消信号必须原样抛出，不得吞掉，否则破坏结构化并发
                throw cancellation
            } catch (e: Exception) {
                // 防御性加固（未确认根因）：启动期副作用失败一律降级，仅记录日志，不阻断启动。
                Log.e(LOG_TAG, "启动检查失败，已降级继续启动（看板提醒可能缺失）", e)
            }
        }
    }

    private fun snapshotFailureMessage(result: SnapshotResult.Failure): String =
        when (result.reason) {
            SnapshotFailureReason.DIR_NOT_SET -> "自动快照已跳过：请重新选择快照目录"
            SnapshotFailureReason.NOT_WRITABLE -> "自动快照失败：目录不可写，已关闭自动快照"
            SnapshotFailureReason.PERMISSION_LOST -> "自动快照失败：目录授权已失效，已关闭自动快照"
            SnapshotFailureReason.IO_ERROR -> "自动快照失败：写入异常，已关闭自动快照"
        }
}
