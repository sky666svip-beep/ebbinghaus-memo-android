package com.ebbinghaus.memo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ebbinghaus.memo.ui.navigation.AppNavigation
import com.ebbinghaus.memo.ui.theme.EbbinghausTheme

/**
 * 艾宾浩斯备忘录主 Activity
 *
 * 继承 ComponentActivity，作为全局 Single-Activity 架构载体，获取全局仓储依赖并装配 Compose UI 主题与导航图。
 *
 * **如实说明（第 2 轮更正）**：下方对 [EbbinghausApp] 各 `by lazy` 属性的读取发生在
 * `setContent` **之前**、且位于**主线程**，因此仓储/数据库/Room 实例实际是在**首帧之前同步构建**的
 * ——并非「首次读取时才真正构建」的惰性收益。此处仅为「集中取依赖并显式注入」，不构成启动性能优化。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启用 Edge-to-Edge：内容延伸至系统栏区域，由 Scaffold 统一处理 insets
        enableEdgeToEdge()

        val app = application as EbbinghausApp
        val memoRepository = app.memoRepository
        val reviewRepository = app.reviewRepository
        val settingsRepository = app.settingsRepository
        val preferenceStore = app.preferenceStore
        val dataPortRepository = app.dataPortRepository
        val snapshotManager = app.snapshotManager
        val crashLogger = app.crashLogger

        setContent {
            EbbinghausTheme {
                AppNavigation(
                    memoRepository = memoRepository,
                    reviewRepository = reviewRepository,
                    settingsRepository = settingsRepository,
                    preferenceStore = preferenceStore,
                    dataPortRepository = dataPortRepository,
                    snapshotManager = snapshotManager,
                    crashLogger = crashLogger
                )
            }
        }
    }
}
