# Ebbinghaus Memo (艾宾浩斯复习备忘录)

基于**艾宾浩斯遗忘曲线**的 Android 间隔复习应用：以「知识点」为单位记录内容、标签与个人笔记，并按 1~6 档标准间隔（1/2/4/7/15/30 天）及长周期（60 天）自动安排复习，帮助用户高效对抗遗忘。

纯 Kotlin + Jetpack Compose 实现，零重型第三方依赖，轻量且易维护。

---

## 功能特性

- **知识点管理**：新增 / 编辑 / 删除知识点，绑定内容、标签、个人笔记与独立复习进度。
- **多标签分类与筛选**：按标签快速过滤知识点列表。
- **多关键词即时模糊搜索**：空格分隔的多关键词对内容与笔记进行包含检索。
- **艾宾浩斯复习状态机**：
  - 1~6 档标准复习间隔，满档晋升 60 天长周期。
  - 「记住 / 模糊 / 忘记 / 跳过」四类评级驱动档位升降与顺延。
  - 每日复习上限（0~50 条），到期任务按最早优先截断，超量顺延次日。
  - 多天未登录无惩罚合并，所有到期任务自动汇入候选池。
- **应用内看板与提醒**：首页汇总今日待复习量并提供「立即复习」入口；免 `POST_NOTIFICATIONS` 权限的应用内提醒。
- **卡片式沉浸复习流**：逐张复习卡片，复习中可即时编辑笔记不打断心流。
- **数学公式渲染**：内置 KaTeX，支持知识点内容中的 LaTeX 数学公式显示。
- **数据导出 / 导入**：支持本地数据备份与恢复。
- **Baseline Profile**：内置 baseline profile 生成与安装，提升冷启动性能。

---

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose (Material 3) + Navigation Compose |
| 架构 | 单 Activity + MVI/UDF ViewModel + 单向数据流 (Flow) |
| 持久化 | Room 2.6.1 + KSP + Coroutines Flow |
| 构建 | Gradle 8.10.2 (Kotlin DSL) + Android Gradle Plugin 8.7.3 |
| 最低 / 目标 SDK | minSdk 26 (Android 8.0) / targetSdk 35 (Android 15) |
| JDK | 17 |

---

## 模块结构

```
ebbinghaus-memo-android/
├── app/                 # Android 应用模块（UI + 数据装配 + 入口）
│   └── src/main/java/com/ebbinghaus/memo/...
│       ├── data/        # Room 数据库、DAO、Repository
│       ├── ui/          # Compose 界面与 ViewModel
│       └── crash/       # 崩溃日志
├── core/                # 纯 Kotlin JVM 领域模块（与 Android 解耦）
│   └── EbbinghausScheduler / RolloverEngine / ReviewStage / ReviewRating
├── baselineprofile/     # Baseline Profile 录制模块（com.android.test）
└── design/              # 设计文档、架构图与测试报告
```

`core` 模块为纯 JVM 实现，可脱离 Android SDK 进行毫秒级单元测试。

---

## 构建要求

- **JDK 17**（推荐 Eclipse Temurin）
- **Android SDK**（compileSdk 35，build-tools 35.0.0）
- 网络可访问 `google()` 与 `mavenCentral()` 仓库

---

## 本地构建

```bash
# 克隆
git clone https://github.com/sky666svip-beep/ebbinghaus-memo-android.git
cd ebbinghaus-memo-android

# 编译 Debug APK（产物位于 app/build/outputs/apk/debug/）
./gradlew :app:assembleDebug

# 运行单元测试（app 与 core）
./gradlew :app:testDebugUnitTest :core:test

# 安装到已连接的设备 / 模拟器
./gradlew :app:installDebug
```

首次构建会自动通过 Gradle Wrapper 下载 Gradle 8.10.2，无需手动安装。

---

## 测试

- **纯 JVM 单元测试**（`core` 模块）：覆盖艾宾浩斯状态机、超量顺延与多天调度算法。
- **应用单元测试**（`app` 模块 `testDebugUnitTest`）：ViewModel 与业务逻辑。
- **仪器化测试**（`androidTest`）：Room 数据库迁移与契约测试，需连接设备或模拟器后执行 `./gradlew :app:connectedAndroidTest`。

---

## 持续集成

仓库已配置 GitHub Actions：每次 push 到 `main` 或发起 Pull Request 时，自动执行
`./gradlew :app:assembleDebug :app:testDebugUnitTest :core:test`，
完成 Debug 构建与单元测试校验。详见 [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)。

---

## 许可证

本项目仅供学习与个人使用。
