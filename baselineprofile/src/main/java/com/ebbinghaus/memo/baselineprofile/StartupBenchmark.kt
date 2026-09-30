package com.ebbinghaus.memo.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 冷启动关键路径 Benchmark（Baseline Profile 录制源）
 *
 * 录制冷启动：从桌面启动应用并等待首屏（知识库列表标题）出现。
 *
 * ⚠️ 需真实设备/模拟器（本环境无设备，未执行）。有设备时执行：
 * ```
 * ./gradlew :baselineprofile:pmp   # 或 :app:generateBaselineProfile
 * ```
 * 生成结果会写入 `app/src/main/baseline-prof.txt`（或 generated 目录）。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * 冷启动 + Baseline Profile 生效模式：度量启动耗时。
     */
    @Test
    fun startupColdWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require
        )
    ) {
        pressHome()
        startActivityAndWait()

        // 等待首屏标题渲染，确保度量覆盖到首帧之后的关键路径
        val title = device.wait(Until.findObject(By.text(APP_TITLE)), LAUNCH_TIMEOUT_MS)
        assertTrue("首屏标题应可见", title != null)
    }

    /**
     * 冷启动（无编译优化）作为对照组。
     */
    @Test
    fun startupColdWithoutCompilation() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = DEFAULT_ITERATIONS,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None()
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        const val TARGET_PACKAGE = "com.ebbinghaus.memo"
        const val APP_TITLE = "艾宾浩斯备忘录"
        const val DEFAULT_ITERATIONS = 5
        const val LAUNCH_TIMEOUT_MS = 5_000L
    }
}
