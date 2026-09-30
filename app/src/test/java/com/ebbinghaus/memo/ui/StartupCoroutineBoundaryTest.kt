package com.ebbinghaus.memo.ui

import android.util.Log
import com.ebbinghaus.memo.core.model.ReviewStage
import com.ebbinghaus.memo.data.local.entity.KnowledgeMemoEntity
import com.ebbinghaus.memo.data.local.entity.MemoWithReviewTask
import com.ebbinghaus.memo.data.local.entity.ReviewTaskEntity
import com.ebbinghaus.memo.data.local.entity.UserSettingsEntity
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.data.repository.ReviewRepository
import com.ebbinghaus.memo.data.repository.SettingsRepository
import com.ebbinghaus.memo.ui.dashboard.DashboardUiEvent
import com.ebbinghaus.memo.ui.dashboard.DashboardViewModel
import com.ebbinghaus.memo.ui.detail.MemoDetailViewModel
import com.ebbinghaus.memo.ui.review.ReviewUiEvent
import com.ebbinghaus.memo.ui.review.ReviewViewModel
import com.ebbinghaus.memo.ui.settings.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * 启动期无边界协程收敛 —— 加固回归测试。
 *
 * 覆盖启动 / 首帧路径上的全部 ViewModel `init` 协程：
 * - [DashboardViewModel] `init`
 * - [DashboardViewModel.checkAppLaunchPrompt]（`OnCheckAppLaunch` 派发，冷启动首帧之前）
 * - [ReviewViewModel] `loadReviewBatch()`（`init` 触发）
 * - [SettingsViewModel] `init`
 * - [MemoDetailViewModel] `loadMemoDetails()`（详情页 `init` 触发）
 *
 * 断言三件事：
 * 1. 依赖抛异常时**不逃逸**（不冒泡到未捕获处理器 → 不闪退）；
 * 2. 失败**非静默**：写 `Log.e`（tag = `EbbinghausLaunch`）；
 * 3. `CancellationException` **不被吞**（不进通用分支 → `Log.eCount == 0`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupCoroutineBoundaryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today: LocalDate = LocalDate.of(2026, 9, 14)
    private val launchTag = "EbbinghausLaunch"

    /** 可在 `getSettings()` 上注入异常的 [SettingsRepository] 替身（可动态清除以验证重试） */
    private class ThrowingSettingsRepository(
        initial: UserSettingsEntity = UserSettingsEntity()
    ) : SettingsRepository {

        val state = MutableStateFlow(initial)

        /** 非 null 时 `getSettings()` 直接抛出该异常 */
        var throwOnGetSettings: Throwable? = null

        override fun getSettings(): Flow<UserSettingsEntity> =
            throwOnGetSettings?.let { t -> flow { throw t } } ?: state

        override suspend fun updateDailyLimit(newLimit: Int) {
            state.value = state.value.copy(dailyReviewLimit = newLimit.coerceIn(0, 50))
        }

        override suspend fun markDashboardPrompted(promptDate: LocalDate) {
            state.value = state.value.copy(lastPromptedDate = promptDate)
        }

        override suspend fun updateLastActiveDate(activeDate: LocalDate) {
            state.value = state.value.copy(lastActiveDate = activeDate)
        }
    }

    @Before
    fun resetLogProbe() {
        Log.reset()
    }

    /**
     * 触发一次 `runTest {}`：若此前的构造期协程有未捕获异常，coroutines-test 会以
     * `UncaughtExceptionsBeforeTest` 抛出 → 返回该异常；否则返回 null。
     */
    private fun captureUncaught(): Throwable? = try {
        runTest { }
        null
    } catch (t: Throwable) {
        t
    }

    /**
     * 权威逃逸探针：捕获「到达线程默认未捕获异常处理器」的异常。
     *
     * 语义 = 真机上 `FATAL EXCEPTION` → 进程终止（闪退）的必经路径：
     * `viewModelScope.launch` 的未捕获异常 → `handleCoroutineException` → 线程默认处理器。
     *
     * 采集后会排空 coroutines-test 调度器已收集的异常，避免泄漏污染后续用例。
     */
    private fun captureEscapes(block: () -> Unit): List<Throwable> {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        val captured = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, t -> captured.add(t) }
        try {
            block()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
        // 排空调度器已收集的未捕获异常，避免泄漏到后续用例
        try {
            runTest { }
        } catch (_: Throwable) {
            // 预期内：仅为排空，忽略
        }
        return captured.toList()
    }

    private fun dueTask(id: Long): MemoWithReviewTask = MemoWithReviewTask(
        memo = KnowledgeMemoEntity(id = id, content = "知识点 $id"),
        reviewTask = ReviewTaskEntity(
            id = id * 10,
            memoId = id,
            stageLevel = ReviewStage.STAGE_1.level,
            dueDate = today
        )
    )

    // =================================================================================
    // DashboardViewModel.init
    // =================================================================================

    @Test
    fun dashboardInit_dependencyThrows_isContainedByBoundary_andLogged() {
        Log.reset()
        val settingsRepo = ThrowingSettingsRepository().apply {
            throwOnGetSettings = IllegalStateException("模拟 Room 读取失败")
        }

        DashboardViewModel(
            reviewRepository = FakeReviewRepository(),
            settingsRepository = settingsRepo,
            todayProvider = { today }
        )

        assertNull("init 协程异常必须被就地收敛，不得逃逸", captureUncaught())
        assertEquals("init 失败必须写 Log.e（非静默）", 1, Log.eCount)
        assertEquals(launchTag, Log.lastTag)
        assertTrue(Log.lastThrowable is IllegalStateException)
    }

    @Test
    fun dashboardInit_normalPath_unchanged() {
        Log.reset()
        val settingsRepo = ThrowingSettingsRepository(
            UserSettingsEntity(dailyReviewLimit = 15)
        )

        val viewModel = DashboardViewModel(
            reviewRepository = FakeReviewRepository(),
            settingsRepository = settingsRepo,
            todayProvider = { today }
        )

        assertEquals("正常路径统计流应照常落值", 15, viewModel.uiState.value.dailyLimit)
        assertEquals("正常路径不得产生错误日志", 0, Log.eCount)
        assertNull(captureUncaught())
    }

    // =================================================================================
    // ReviewViewModel（init → loadReviewBatch）
    // =================================================================================

    @Test
    fun reviewInit_dependencyThrows_setsVisibleErrorState_andLogged() {
        Log.reset()
        val settingsRepo = ThrowingSettingsRepository().apply {
            throwOnGetSettings = IllegalStateException("模拟 Room 读取失败")
        }

        val viewModel = ReviewViewModel(
            reviewRepository = FakeReviewRepository(),
            settingsRepository = settingsRepo,
            memoRepository = FakeMemoRepository(),
            todayProvider = { today }
        )

        assertNull("init 协程异常必须被就地收敛，不得逃逸", captureUncaught())
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull("必须给出**可见**错误态（不静默降级为空队列）", state.loadError)
        assertEquals("复习批次加载失败，请重试", state.loadError)
        assertEquals(1, Log.eCount)
        assertEquals(launchTag, Log.lastTag)
    }

    @Test
    fun reviewRetry_afterFailure_recoversToNormalState() {
        Log.reset()
        val settingsRepo = ThrowingSettingsRepository().apply {
            throwOnGetSettings = IllegalStateException("模拟 Room 读取失败")
        }
        val viewModel = ReviewViewModel(
            reviewRepository = FakeReviewRepository(),
            settingsRepository = settingsRepo,
            memoRepository = FakeMemoRepository(),
            todayProvider = { today }
        )
        assertNotNull(viewModel.uiState.value.loadError)

        // 修复依赖后重试（复用既有 OnRestartSession → loadReviewBatch）
        settingsRepo.throwOnGetSettings = null
        viewModel.onEvent(ReviewUiEvent.OnRestartSession)

        assertNull("重试成功后应清除错误态", viewModel.uiState.value.loadError)
        assertTrue("空队列 + 成功加载 → 走原「全部达成」分支", viewModel.uiState.value.isCompleted)
    }

    // =================================================================================
    // SettingsViewModel.init
    // =================================================================================

    @Test
    fun settingsInit_dependencyThrows_setsVisibleError_andLogged() {
        Log.reset()
        val repo = ThrowingSettingsRepository().apply {
            throwOnGetSettings = IllegalStateException("模拟 Room 读取失败")
        }

        val viewModel = SettingsViewModel(repo)

        assertNull("init 协程异常必须被就地收敛，不得逃逸", captureUncaught())
        assertEquals(1, Log.eCount)
        assertEquals(launchTag, Log.lastTag)
        assertFalse("失败后不得永久停留在加载态", viewModel.uiState.value.isLoading)
        assertNotNull("必须给出可见提示（避免默认值被误认为真实设置）", viewModel.uiState.value.loadError)
    }

    @Test
    fun settingsInit_normalPath_unchanged() {
        Log.reset()
        val repo = ThrowingSettingsRepository(UserSettingsEntity(dailyReviewLimit = 33))

        val viewModel = SettingsViewModel(repo)

        assertEquals(33, viewModel.uiState.value.dailyLimit)
        assertNull(viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(0, Log.eCount)
        assertNull(captureUncaught())
    }

    // =================================================================================
    // DashboardViewModel.checkAppLaunchPrompt() 启动期副作用链加固
    // =================================================================================

    @Test
    fun dashboard_checkAppLaunch_tailDependencyThrows_doesNotCrashAndKeepsUpdatedState() = runTest {
        val reviewRepo = FakeReviewRepository().apply { tasksState.value = listOf(dueTask(1)) }
        val settingsRepo = ConfigurableSettingsRepository(
            UserSettingsEntity(dailyReviewLimit = 20, lastActiveDate = null, lastPromptedDate = null)
        ).apply {
            throwOnUpdateLastActiveDate = IllegalStateException("模拟 Room 写入失败")
        }

        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepo,
            settingsRepository = settingsRepo,
            todayProvider = { today }
        )

        // 未抛出异常即证明边界生效（否则此调用链会令测试失败）
        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)

        val state = viewModel.uiState.value
        assertTrue("看板应已被唤起", state.isVisible)
        assertEquals(1, state.dueTodayCount)
        assertEquals("异常前的状态写入应保留", today, settingsRepo.state.value.lastPromptedDate)
    }

    @Test
    fun dashboard_checkAppLaunch_genericFailure_isLogged_notSilent() = runTest {
        val reviewRepo = FakeReviewRepository().apply { tasksState.value = listOf(dueTask(1)) }
        val settingsRepo = ConfigurableSettingsRepository(
            UserSettingsEntity(dailyReviewLimit = 20)
        ).apply {
            throwOnUpdateLastActiveDate = IllegalStateException("模拟 Room 写入失败")
        }
        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepo,
            settingsRepository = settingsRepo,
            todayProvider = { today }
        )

        Log.reset()
        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)

        assertEquals("通用异常必须被 Log.e 记录一次（非静默）", 1, Log.eCount)
        assertEquals(launchTag, Log.lastTag)
        assertTrue(Log.lastThrowable is IllegalStateException)
    }

    @Test
    fun dashboard_checkAppLaunch_cancellationException_isRethrown_notLogged() = runTest {
        val reviewRepo = FakeReviewRepository().apply { tasksState.value = listOf(dueTask(1)) }
        val settingsRepo = ConfigurableSettingsRepository(
            UserSettingsEntity(dailyReviewLimit = 20)
        ).apply {
            throwOnUpdateLastActiveDate = CancellationException("模拟协程取消")
        }
        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepo,
            settingsRepository = settingsRepo,
            todayProvider = { today }
        )

        Log.reset()
        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)

        // 证明确实走到了抛出点（状态已更新、弹窗已标记）
        assertTrue(viewModel.uiState.value.isVisible)
        assertEquals(today, settingsRepo.state.value.lastPromptedDate)
        // 关键：取消信号未被降级日志吞掉
        assertEquals("CancellationException 不得进入 catch(Exception)", 0, Log.eCount)
        // 且未被误当作通用失败而中断后续状态
        assertNull("取消信号应原样抛出，lastActiveDate 不应被写入", settingsRepo.state.value.lastActiveDate)
    }

    @Test
    fun dashboard_checkAppLaunch_normalPath_behaviourUnchanged() = runTest {
        val reviewRepo = FakeReviewRepository().apply { tasksState.value = listOf(dueTask(1)) }
        val settingsRepo = ConfigurableSettingsRepository(
            UserSettingsEntity(dailyReviewLimit = 20, lastActiveDate = null, lastPromptedDate = null)
        )
        val viewModel = DashboardViewModel(
            reviewRepository = reviewRepo,
            settingsRepository = settingsRepo,
            todayProvider = { today }
        )

        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)
        assertTrue(viewModel.uiState.value.isVisible)
        assertEquals(today, settingsRepo.state.value.lastActiveDate)
        assertEquals("正常路径不得产生任何错误日志", 0, Log.eCount)

        // 同日再次派发：应被 lastCheckedDate 去重短路，不改变状态
        viewModel.onEvent(DashboardUiEvent.OnDismissDashboard)
        viewModel.onEvent(DashboardUiEvent.OnCheckAppLaunch)
        assertTrue("同日不应重复唤起", !viewModel.uiState.value.isVisible)
    }

    // =================================================================================
    // MemoDetail 详情加载边界 + 逃逸探针 / 独立复核
    // =================================================================================

    /**
     * `MemoDetailViewModel.loadMemoDetails()` 已补齐异常边界（与启动路径同一模式）。
     * 依赖抛异常时必须**就地收敛**（不逃逸到未捕获处理器 → 不闪退），且写入 Log.e（非静默）。
     */
    @Test
    fun memoDetailInit_dependencyThrows_isContainedByBoundary_andLogged() {
        Log.reset()
        val memoRepo = ProbeMemoRepository(FakeMemoRepository(), IllegalStateException("模拟 Room 读取失败"))

        val escapes = captureEscapes {
            MemoDetailViewModel(
                memoId = 1L,
                memoRepository = memoRepo,
                reviewRepository = FakeReviewRepository()
            )
        }

        assertTrue("详情加载异常必须就地收敛，不得逃逸（实际逃逸=${escapes.size}）", escapes.isEmpty())
        assertEquals("必须写一次 Log.e（非静默）", 1, Log.eCount)
        assertEquals(launchTag, Log.lastTag)
        assertTrue(Log.lastThrowable is IllegalStateException)
    }

    /** 新增角度：把失败注入到 reviewRepository.getDueReviewTasks（既有用例只注入 settings） */
    @Test
    fun dashboardInit_reviewRepoThrows_alsoContained_notOnlySettingsPath() {
        Log.reset()
        val reviewRepo = ProbeReviewRepository(
            FakeReviewRepository(),
            IllegalStateException("模拟到期任务查询失败")
        )

        val escapes = captureEscapes {
            DashboardViewModel(
                reviewRepository = reviewRepo,
                settingsRepository = ThrowingSettingsRepository(),
                todayProvider = { today }
            )
        }

        assertTrue("combine 的任一上游抛异常都必须被收敛", escapes.isEmpty())
        assertEquals(1, Log.eCount)
        assertEquals(launchTag, Log.lastTag)
    }

    /**
     * 最易做错点独立复核：三处 `init` 边界注入 `CancellationException` → `Log.e` 一次都不得被调用。
     *
     * 若 `catch(Exception)` 被写在 `catch(CancellationException)` 之前，取消信号会被降级日志吞掉
     * → `Log.eCount` 变为 1（甚至 3）。故 `0` 是「取消信号未被吞」的可判定证据。
     */
    @Test
    fun allInitBoundaries_cancellation_isRethrown_notSwallowed() {
        Log.reset()
        val settingsRepo = ThrowingSettingsRepository().apply {
            throwOnGetSettings = CancellationException("模拟协程取消")
        }

        val escapes = captureEscapes {
            // 1) DashboardViewModel.init
            DashboardViewModel(
                reviewRepository = FakeReviewRepository(),
                settingsRepository = settingsRepo,
                todayProvider = { today }
            )
            assertEquals("Dashboard.init 不得吞取消信号", 0, Log.eCount)

            // 2) ReviewViewModel.init
            ReviewViewModel(
                reviewRepository = FakeReviewRepository(),
                settingsRepository = settingsRepo,
                memoRepository = FakeMemoRepository(),
                todayProvider = { today }
            )
            assertEquals("Review.init 不得吞取消信号", 0, Log.eCount)

            // 3) SettingsViewModel.init
            SettingsViewModel(settingsRepo)
            assertEquals("Settings.init 不得吞取消信号", 0, Log.eCount)
        }
        // 取消信号是协程的正常终止语义，不应被当作未捕获异常上报
        assertTrue("取消信号不得逃逸为未捕获异常", escapes.isEmpty())
    }

    /** 回归守护：正常路径下三处 init 不得产生任何错误日志、不得逃逸 */
    @Test
    fun normalPath_noBoundaryTriggersNoErrorLog() {
        Log.reset()
        val settingsRepo = ThrowingSettingsRepository(UserSettingsEntity(dailyReviewLimit = 25))

        val escapes = captureEscapes {
            DashboardViewModel(
                reviewRepository = FakeReviewRepository(),
                settingsRepository = settingsRepo,
                todayProvider = { today }
            )
            ReviewViewModel(
                reviewRepository = FakeReviewRepository(),
                settingsRepository = settingsRepo,
                memoRepository = FakeMemoRepository(),
                todayProvider = { today }
            )
            SettingsViewModel(settingsRepo)
        }

        assertEquals("正常路径不得产生错误日志", 0, Log.eCount)
        assertTrue("正常路径不得逃逸", escapes.isEmpty())
    }
}

/** 可配置失败点的 [SettingsRepository] 替身（含副作用链尾部方法），用于精确命中加固 catch 分支。 */
private class ConfigurableSettingsRepository(
    initial: UserSettingsEntity = UserSettingsEntity()
) : SettingsRepository {

    val state = MutableStateFlow(initial)

    var throwOnGetSettings: Throwable? = null
    var throwOnUpdateLastActiveDate: Throwable? = null
    var throwOnMarkPrompted: Throwable? = null

    override fun getSettings(): Flow<UserSettingsEntity> =
        throwOnGetSettings?.let { t -> flow { throw t } } ?: state

    override suspend fun updateDailyLimit(newLimit: Int) {
        state.value = state.value.copy(dailyReviewLimit = newLimit.coerceIn(0, 50))
    }

    override suspend fun markDashboardPrompted(promptDate: LocalDate) {
        throwOnMarkPrompted?.let { throw it }
        state.value = state.value.copy(lastPromptedDate = promptDate)
    }

    override suspend fun updateLastActiveDate(activeDate: LocalDate) {
        throwOnUpdateLastActiveDate?.let { throw it }
        state.value = state.value.copy(lastActiveDate = activeDate)
    }
}

/** 仅在 `getDueReviewTasks()` 上抛异常的 [ReviewRepository] 替身，其余委托给内存替身。 */
private class ProbeReviewRepository(
    private val delegate: FakeReviewRepository,
    private val error: Throwable
) : ReviewRepository by delegate {
    override fun getDueReviewTasks(today: LocalDate): Flow<List<MemoWithReviewTask>> = flow { throw error }
}

/** 仅在 `getMemoById()` 上抛异常的 [MemoRepository] 替身，其余委托给内存替身。 */
private class ProbeMemoRepository(
    private val delegate: FakeMemoRepository,
    private val error: Throwable
) : MemoRepository by delegate {
    override suspend fun getMemoById(id: Long): KnowledgeMemoEntity? = throw error
}
