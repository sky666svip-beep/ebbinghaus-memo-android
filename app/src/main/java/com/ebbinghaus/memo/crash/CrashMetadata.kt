package com.ebbinghaus.memo.crash

import android.content.Context
import android.os.Build

/**
 * 构造崩溃报告头部的设备/版本元信息（App 版本、Android 版本、机型、ABI）。
 *
 * 全部读取均包在 try/catch 内：即便个别字段在特殊 ROM 上读取失败，也**绝不能**
 * 因「收集诊断信息」而干扰崩溃处理主流程。本函数仅在真实运行时被调用（JVM 单测注入替身）。
 *
 * @param context 应用上下文（用于读取包版本信息）
 * @return 多行元信息字符串
 */
fun buildCrashMetadata(context: Context): String = try {
    val packageInfo = @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName ?: "unknown"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    buildString {
        append("App 版本: ").append(versionName).append(" (").append(versionCode).append(")\n")
        append("Android 版本: ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        append("机型: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        append("ABI: ").append(Build.SUPPORTED_ABIS.joinToString(","))
    }
} catch (e: Throwable) {
    "设备信息读取失败: ${e::class.java.simpleName}"
}
