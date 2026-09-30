package com.ebbinghaus.memo.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 内置崩溃自诊断记录器单元测试（纯 JVM）。
 *
 * 覆盖：落盘内容（堆栈 + `Caused by` 链 + 设备/版本信息 + 时间戳）、保留策略（最近 3 次）、
 * 读取最近一次、清空、以及「写日志失败绝不二次崩溃且仍按原逻辑终止」两条硬约束。
 *
 * 说明：**不**测试「原 handler 为 null 时的手动终止分支」——该分支会调用
 * `android.os.Process.killProcess` + `exitProcess`，在 JVM 单测中会直接终止测试进程；
 * 该分支仅在生产环境（系统一定已设置默认 handler）作为极端兜底存在。
 */
class CrashLoggerTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private fun crashDir(): File = File(tempFolder.root, "crash")

    private fun newLogger(
        dir: File = crashDir(),
        metadata: String = DEFAULT_METADATA,
        clock: () -> Long = { 1_000L },
        maxRecords: Int = 3
    ): CrashLogger = CrashLogger(
        crashDirProvider = { dir },
        metadataProvider = { metadata },
        clock = clock,
        maxRecords = maxRecords
    )

    @Test
    fun writeCrash_retainsOnlyLatestThreeRecords() {
        val dir = crashDir()
        val times = listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L)
        var index = 0
        val logger = newLogger(dir = dir, clock = { times[index++] }, maxRecords = 3)

        repeat(times.size) {
            logger.writeCrash(Thread.currentThread(), RuntimeException("第 $it 次"))
        }

        assertEquals("仅保留最近 3 份归档", 3, logger.recordCount())
        val archives = dir.listFiles { f -> f.isFile && f.name.startsWith("crash-") }!!
            .sortedBy { it.name }
        assertEquals(3, archives.size)
        assertTrue("应保留 3000ms 记录", archives[0].name.contains("3000"))
        assertTrue("应保留 5000ms 记录（最新）", archives[2].name.contains("5000"))
    }

    @Test
    fun readLatest_returnsNewestReport() {
        val dir = crashDir()
        val times = listOf(1_000L, 2_000L)
        var index = 0
        val logger = newLogger(dir = dir, clock = { times[index++] })

        logger.writeCrash(Thread.currentThread(), RuntimeException("第一次崩溃"))
        logger.writeCrash(Thread.currentThread(), RuntimeException("第二次崩溃"))

        val latest = logger.readLatest()
        assertNotNull(latest)
        assertTrue("readLatest 应返回最近一次", latest!!.contains("第二次崩溃"))
        assertFalse(latest.contains("第一次崩溃"))
    }

    @Test
    fun hasCrashRecord_and_clear_behaveAsExpected() {
        val dir = crashDir()
        val logger = newLogger(dir = dir)

        assertFalse("初始无记录", logger.hasCrashRecord())
        assertEquals(0, logger.recordCount())
        assertNull(logger.readLatest())

        logger.writeCrash(Thread.currentThread(), RuntimeException("崩溃"))
        assertTrue(logger.hasCrashRecord())
        assertEquals(1, logger.recordCount())

        logger.clear()
        assertFalse("清空后应无记录", logger.hasCrashRecord())
        assertEquals(0, logger.recordCount())
        assertNull(logger.readLatest())
    }

    @Test
    fun snapshot_reflectsStore() {
        val dir = crashDir()
        val logger = newLogger(dir = dir)

        assertFalse(logger.snapshot().hasRecord)

        logger.writeCrash(Thread.currentThread(), RuntimeException("崩溃"))
        val snapshot = logger.snapshot()
        assertTrue(snapshot.hasRecord)
        assertEquals(1, snapshot.recordCount)
        assertTrue(snapshot.latestText!!.contains("RuntimeException"))
    }

    @Test
    fun handler_delegatesToPreviousHandler_andWritesCrash() {
        val dir = crashDir()
        val logger = newLogger(dir = dir)
        var delegated: Throwable? = null
        var previousCalled = false
        val previous = Thread.UncaughtExceptionHandler { _, throwable ->
            previousCalled = true
            delegated = throwable
        }

        val handler = logger.createHandler(previous)
        val error = RuntimeException("致命崩溃")
        handler.uncaughtException(Thread.currentThread(), error)

        assertTrue("必须调用原 handler（保持系统原终止逻辑，不吞崩溃）", previousCalled)
        assertSame(error, delegated)
        assertTrue("落盘与委托应同时发生", logger.hasCrashRecord())
    }

    @Test
    fun handler_whenWriteFails_stillDelegatesToPrevious() {
        val blocker = File(tempFolder.root, "blocker2").apply { writeText("x") }
        val badDir = File(blocker, "crash")
        val logger = newLogger(dir = badDir)
        var called = false
        val handler = logger.createHandler(Thread.UncaughtExceptionHandler { _, _ -> called = true })

        // 落盘失败也绝不能吞掉崩溃
        handler.uncaughtException(Thread.currentThread(), RuntimeException("崩溃"))

        assertTrue("写日志失败时仍必须委托原 handler", called)
    }

    @Test
    fun install_isIdempotent_andRestoresOriginalHandler() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        try {
            val logger = newLogger()
            logger.install()
            val first = Thread.getDefaultUncaughtExceptionHandler()
            assertNotNull(first)

            // 幂等：二次 install 不得重复包装
            logger.install()
            assertSame(first, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    // ------------------------------------------------------------------
    // 对抗性补充：完整堆栈 / 多级 Caused by 链；不可写目录不残留记录
    // ------------------------------------------------------------------

    @Test
    fun writeCrash_report_containsFullStackAndNestedCauseChain() {
        val dir = crashDir()
        val logger = newLogger(
            dir = dir,
            metadata = "App 版本: 9.9.9 (99)\nAndroid 版本: 16 (API 36)\n机型: QA Probe"
        )

        val root = IllegalStateException("根因：数据库文件损坏")
        val mid = RuntimeException("中间层：仓储初始化失败", root)
        val outer = RuntimeException("最外层：冷启动失败", mid)

        val archive = logger.writeCrash(Thread.currentThread(), outer)

        assertNotNull("落盘必须成功", archive)
        // 反向合并自 writeCrash_writesArchiveAndLastCrash_withFullReport（C 档 2026-09-18）：
        // 以下 3 条断言原仅存于弱版，合并后不得丢失
        assertTrue("归档文件必须实际存在于磁盘", archive!!.exists())
        assertTrue("last_crash 副本必须存在", File(dir, LAST_CRASH_FILE).exists())
        val report = File(dir, LAST_CRASH_FILE).readText()
        // 三层异常类型与消息均须出现
        assertTrue("必须含异常类型", report.contains("RuntimeException"))
        assertTrue(report.contains("最外层：冷启动失败"))
        assertTrue(report.contains("中间层：仓储初始化失败"))
        assertTrue(report.contains("根因：数据库文件损坏"))
        // 两级 Caused by 链（三层异常 → 两个 Caused by）
        val causedByCount = Regex("Caused by:").findAll(report).count()
        assertTrue("应含 ≥2 级 Caused by（实际=$causedByCount）", causedByCount >= 2)
        // 真实堆栈帧（PrintWriter 生成的 "at ..." 行，且含本测试类名）
        assertTrue("必须含真实堆栈帧", report.contains("\tat "))
        assertTrue("堆栈须定位到调用点", report.contains(this::class.java.name))
        // 元信息与时间戳
        assertTrue(report.contains("App 版本: 9.9.9"))
        assertTrue(report.contains("Android 版本: 16"))
        assertTrue(report.contains("时间:"))
        assertTrue("必须含线程名", report.contains("线程:"))
    }

    @Test
    fun writeCrash_whenDirUnusable_returnsNullAndRecordsNothing() {
        val blocker = File(tempFolder.root, "blocker3").apply { writeText("x") }
        val logger = newLogger(dir = File(blocker, "crash"))

        val result = logger.writeCrash(Thread.currentThread(), RuntimeException("崩溃"))

        assertNull("不可写目录必须返回 null 而非抛异常", result)
        assertFalse(logger.hasCrashRecord())
        assertNull(logger.readLatest())
        assertEquals(0, logger.recordCount())
    }

    private companion object {
        const val DEFAULT_METADATA =
            "App 版本: 1.0.0 (1)\nAndroid 版本: 15 (API 35)\n机型: Test Model"
    }
}
