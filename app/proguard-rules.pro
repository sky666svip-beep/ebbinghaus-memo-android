# =============================================================================
# R8 / ProGuard 规则文件
# -----------------------------------------------------------------------------
# 项目：Android 艾宾浩斯知识点备忘录（EbbinghausMemo）
#
# 设计原则：**保持最小化**。Room 2.6.1、Jetpack Compose、Kotlin stdlib 与
# kotlinx-coroutines 均自带 consumer proguard 规则（AAR 内 META-INF/proguard），
# R8 会自动应用，因此默认无需手写任何 keep 规则。
#
# 只有 `assembleRelease` 因 R8 报错（missing class / 警告升级为错误）时，
# 才在此处追加**最小化**的规则（优先 -dontwarn 精确包名），
# 严禁使用 `-keep class ** { *; }` 之类的全量保留（等同于关闭 R8）。
# =============================================================================

# --- 保留行号信息，便于线上崩溃堆栈还原（体积开销极小）---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- 说明：以下为常见可选规则，当前**未启用**，仅在触发时按需打开 ---
# 若 R8 报 Room 生成类相关警告，再启用下面一条（当前 Room 2.6.1 consumer 规则已覆盖）：
# -keep class * extends androidx.room.RoomDatabase
# 若 R8 报 kotlinx-coroutines 内部 debug 类缺失，再启用：
# -dontwarn kotlinx.coroutines.**
