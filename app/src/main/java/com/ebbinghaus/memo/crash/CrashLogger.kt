package com.ebbinghaus.memo.crash

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 归档崩溃文件前缀（后接 19 位零填充的毫秒时间戳，字典序即时间序） */
private const val ARCHIVE_PREFIX = "crash-"

/** 归档崩溃文件后缀 */
private const val ARCHIVE_SUFFIX = ".txt"

/** 最近一次崩溃的固定副本文件名（便于人工按名取用） */
const val LAST_CRASH_FILE: String = "last_crash.txt"

/** 报告内时间戳格式 */
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

/** 生成归档文件名：19 位零填充毫秒时间戳 → 字典序与时间序一致，便于确定性裁剪 */
internal fun archiveName(timestampMillis: Long): String =
    String.format(Locale.ROOT, "%s%019d%s", ARCHIVE_PREFIX, timestampMillis, ARCHIVE_SUFFIX)

/**
 * 应用内置崩溃自诊断记录器（零权限、零新增依赖）。
 *
 * 背景：用户**不方便抓取 logcat**，导致线上「一打开就崩」无法定位根因。本类通过注册全局
 * [Thread.setDefaultUncaughtExceptionHandler]，在进程因未捕获异常终止前，把**完整堆栈**
 * （含 `Caused by` 链）、设备信息、Android 版本、App 版本与时间戳写入**应用私有目录**
 * （默认 `filesDir/crash/`），全程无需任何权限。用户随后可在设置页一键「复制/分享」根因。
 *
 * 设计要点（与任务硬约束一一对应）：
 * 1. **不吞崩溃**：落盘后**必**调用原 handler（系统默认会记录 `FATAL EXCEPTION` 并杀进程）；
 *    仅当原 handler 缺失时才走手动终止兜底 —— 绝不因「写了日志」就吞掉崩溃导致 ANR/无响应。
 * 2. **绝不二次崩溃**：落盘本身包在 try/catch 内，写日志失败仅返回 `null`，不向外抛。
 * 3. **零权限**：只写应用私有目录（`filesDir`），不申请任何存储权限。
 * 4. **可测试**：目录、元信息、时钟、裁剪上限全部依赖注入，纯 JVM 单测可覆盖；
 *    堆栈通过 [PrintWriter]/[StringWriter] 生成（不依赖 `android.util.Log`，JVM 下同样有效）。
 *
 * @param crashDirProvider 崩溃目录提供者（生产为 `filesDir/crash`；测试可注入临时目录）
 * @param metadataProvider 设备/版本元信息提供者（生产为 [buildCrashMetadata]；测试可注入固定串）
 * @param clock 时间戳提供者（毫秒），用于文件名与报告头，便于测试确定性
 * @param maxRecords 归档崩溃记录保留上限（默认保留最近 [DEFAULT_MAX_RECORDS] 次）
 */
class CrashLogger(
    private val crashDirProvider: () -> File,
    private val metadataProvider: () -> String = { "" },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxRecords: Int = DEFAULT_MAX_RECORDS
) {

    @Volatile
    private var installed: Boolean = false

    /**
     * 注册全局未捕获异常处理器（幂等）。
     *
     * 捕获安装时刻的「原 handler」，并在处理完成后**交还**给它，从而保持系统的
     * 原始崩溃终止语义（记录 `FATAL EXCEPTION` + 杀进程），避免被系统误判为 ANR。
     */
    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(createHandler(previous))
    }

    /**
     * 构造未捕获异常处理器（独立暴露以便单测直接驱动，无需污染全局状态）。
     *
     * @param previous 安装时刻的「原 handler」；非 null 时必须被调用以维持原终止逻辑
     */
    fun createHandler(previous: Thread.UncaughtExceptionHandler?): Thread.UncaughtExceptionHandler =
        Thread.UncaughtExceptionHandler { thread, throwable ->
            // 1) 尽最大努力落盘；写日志自身的异常绝不允许引发二次崩溃
            runCatching { writeCrash(thread, throwable) }
            // 2) 按原逻辑终止：优先交还原 handler
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                // 极端兜底：无原 handler 时手动终止，绝不吞掉崩溃
                terminateProcess()
            }
        }

    /**
     * 把一次崩溃写入私有目录。
     *
     * 同时产出两份：归档文件 `crash-<ts>.txt`（受 [maxRecords] 裁剪）与固定副本
     * [LAST_CRASH_FILE]（始终为最近一次）。**本方法自身绝不抛异常**：任何失败（目录不可写等）
     * 仅返回 `null`，以满足「写文件本身必须包 try/catch，绝不能引发二次崩溃」的要求。
     *
     * @return 写入的归档文件；失败返回 `null`
     */
    fun writeCrash(thread: Thread, throwable: Throwable): File? = try {
        val dir = crashDirProvider()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        if (!dir.isDirectory) {
            throw IllegalStateException("崩溃目录不可用: ${dir.absolutePath}")
        }
        val timestamp = clock()
        val report = buildReport(thread, throwable, timestamp)
        val archive = File(dir, archiveName(timestamp))
        archive.writeText(report)
        File(dir, LAST_CRASH_FILE).writeText(report)
        prune(dir)
        archive
    } catch (e: Throwable) {
        // 写日志失败绝不引发二次崩溃
        null
    }

    /** 读取最近一次崩溃的完整报告文本；不存在或读取失败返回 `null` */
    fun readLatest(): String? = try {
        val dir = crashDirProvider()
        val last = File(dir, LAST_CRASH_FILE)
        if (last.isFile) {
            last.readText()
        } else {
            listArchives(dir).firstOrNull()?.readText()
        }
    } catch (e: Throwable) {
        null
    }

    /** 是否存在任何崩溃记录 */
    fun hasCrashRecord(): Boolean = try {
        val dir = crashDirProvider()
        File(dir, LAST_CRASH_FILE).isFile || listArchives(dir).isNotEmpty()
    } catch (e: Throwable) {
        false
    }

    /** 归档崩溃记录数量 */
    fun recordCount(): Int = try {
        listArchives(crashDirProvider()).size
    } catch (e: Throwable) {
        0
    }

    /** 清空全部崩溃记录（含归档与固定副本） */
    fun clear() {
        try {
            crashDirProvider().listFiles()?.forEach { file ->
                runCatching { file.delete() }
            }
        } catch (e: Throwable) {
            // 清空失败不崩溃、不抛
        }
    }

    /** 供 UI 一次性读取的快照（避免 UI 多次触碰文件系统） */
    fun snapshot(): CrashLogSnapshot = CrashLogSnapshot(
        hasRecord = hasCrashRecord(),
        recordCount = recordCount(),
        latestText = readLatest()
    )

    // ---------------------------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------------------------

    private fun buildReport(thread: Thread, throwable: Throwable, timestampMillis: Long): String =
        buildString {
            append("================ 崩溃报告 ================\n")
            append("时间: ").append(formatTime(timestampMillis)).append('\n')
            append("线程: ").append(thread.name).append(" (id=").append(thread.id).append(")\n")
            val metadata = runCatching { metadataProvider() }.getOrDefault("")
            if (metadata.isNotBlank()) {
                append("----------------------------------------\n")
                append(metadata.trimEnd('\n')).append('\n')
            }
            append("----------------------------------------\n")
            append("异常: ").append(throwable.toString()).append('\n')
            append("----------------------------------------\n")
            append(stackTraceOf(throwable))
            append("=========================================\n")
        }

    /** 生成含 `Caused by` 链的完整堆栈（纯 JVM，无需 Android 运行时） */
    private fun stackTraceOf(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    private fun formatTime(millis: Long): String = try {
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)
    } catch (e: Throwable) {
        millis.toString()
    }

    /** 按文件名（内嵌零填充时间戳）降序排列，仅保留最近 [maxRecords] 份 */
    private fun prune(dir: File) {
        listArchives(dir).drop(maxRecords).forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun listArchives(dir: File): List<File> {
        val files = dir.listFiles { file ->
            file.isFile && file.name.startsWith(ARCHIVE_PREFIX) && file.name.endsWith(ARCHIVE_SUFFIX)
        } ?: return emptyList()
        return files.sortedByDescending { it.name }
    }

    /** 与系统默认未捕获异常处理一致：杀进程并按约定退出码退出，避免被误判为 ANR */
    private fun terminateProcess() {
        try {
            android.os.Process.killProcess(android.os.Process.myPid())
        } catch (e: Throwable) {
            // 忽略：下面仍会走 exitProcess 兜底
        }
        kotlin.system.exitProcess(10)
    }

    companion object {
        /** 默认保留的最近崩溃记录份数 */
        const val DEFAULT_MAX_RECORDS: Int = 3
    }
}

/**
 * 崩溃日志快照（供 UI 渲染）
 *
 * @property hasRecord 是否存在崩溃记录（决定设置页入口是否显示）
 * @property recordCount 归档记录份数
 * @property latestText 最近一次崩溃报告全文（供查看/复制/分享）
 */
data class CrashLogSnapshot(
    val hasRecord: Boolean,
    val recordCount: Int,
    val latestText: String?
)
