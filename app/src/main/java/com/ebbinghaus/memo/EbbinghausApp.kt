package com.ebbinghaus.memo

import android.app.Application
import com.ebbinghaus.memo.crash.CrashLogger
import com.ebbinghaus.memo.crash.buildCrashMetadata
import com.ebbinghaus.memo.data.export.DataPortRepository
import com.ebbinghaus.memo.data.local.AppDatabase
import com.ebbinghaus.memo.data.preference.PreferenceStore
import com.ebbinghaus.memo.data.preference.SharedPreferencesPreferenceStore
import com.ebbinghaus.memo.data.repository.MemoRepository
import com.ebbinghaus.memo.data.repository.MemoRepositoryImpl
import com.ebbinghaus.memo.data.repository.ReviewRepository
import com.ebbinghaus.memo.data.repository.ReviewRepositoryImpl
import com.ebbinghaus.memo.data.repository.SettingsRepository
import com.ebbinghaus.memo.data.repository.SettingsRepositoryImpl
import com.ebbinghaus.memo.data.snapshot.SnapshotManager
import java.io.File

/**
 * 艾宾浩斯备忘录全局 Application 入口
 *
 * **关于「惰性化」的如实说明（第 2 轮更正）**：数据库与各业务仓储确实改为了 `by lazy`，
 * 但需要澄清其真实效果——`MainActivity.onCreate` 在 `setContent` **之前**会**同步读取全部**
 * `by lazy` 属性（`MainActivity.kt:25-30`），因此：
 * 1. 这些对象**仍会在主线程、且在首帧渲染之前**被构建（`Room.databaseBuilder(...).build()`、
 *    `SharedPreferences` 同步读取等），**并非**「首帧不被初始化阻塞」；
 * 2. 真实变化只是把初始化时机从 `Application.onCreate` 挪到了 `Activity.onCreate`，
 *    二者都在主线程、都在首帧前，**不能**据此宣称「启动惰性化避免阻塞首帧」。
 * 若后续确需消除首帧阻塞，应把仓储读取真正移出 `onCreate`（例如按需注入或异步预热）。
 *
 * 说明：历史上的 `companion object.instance` 在全项目零读取，属死代码，已移除。
 */
class EbbinghausApp : Application() {

    /** 全局单例数据库，首次访问时初始化 */
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    /** 知识点仓储，首次访问时装配 */
    val memoRepository: MemoRepository by lazy { MemoRepositoryImpl(database) }

    /** 复习调度仓储，首次访问时装配 */
    val reviewRepository: ReviewRepository by lazy { ReviewRepositoryImpl(database.reviewTaskDao()) }

    /** 用户偏好仓储，首次访问时装配 */
    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(database.userSettingsDao()) }

    /** 轻量偏好存储（排序 / 快照开关 / SAF 目录 URI），零依赖零迁移 */
    val preferenceStore: PreferenceStore by lazy { SharedPreferencesPreferenceStore(this) }

    /** 数据搬运仓储（导出 / 导入），复用同一 ExportCodec */
    val dataPortRepository: DataPortRepository by lazy { DataPortRepository(database) }

    /** 自动本地快照管理器（E02） */
    val snapshotManager: SnapshotManager by lazy {
        SnapshotManager(
            dataPortRepository = dataPortRepository,
            preferenceStore = preferenceStore,
            contentResolver = contentResolver
        )
    }

    /**
     * 内置崩溃自诊断记录器（零权限）。
     *
     * 崩溃报告写入应用私有目录 `filesDir/crash/`（归档 `crash-<ts>.txt` + 固定副本
     * `last_crash.txt`，保留最近 3 次），供设置页「关于 → 崩溃日志」查看/复制/分享。
     */
    val crashLogger: CrashLogger by lazy {
        CrashLogger(
            crashDirProvider = { File(filesDir, CRASH_DIR_NAME) },
            metadataProvider = { buildCrashMetadata(this) }
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 仅注册全局未捕获异常处理器：写日志本身零权限、零新增依赖，
        // 且落盘后仍会交还系统原终止逻辑（不吞崩溃）。
        crashLogger.install()
    }

    private companion object {
        /** 崩溃日志私有子目录名（相对 `filesDir`） */
        const val CRASH_DIR_NAME = "crash"
    }
}
