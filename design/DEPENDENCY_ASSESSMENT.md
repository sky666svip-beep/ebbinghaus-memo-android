# 第三方依赖引入评估报告

**项目**：Android 艾宾浩斯知识点备忘录（EbbinghausMemo）
**评估人**：高见远（Architect）
**日期**：2026-09-14
**性质**：只读评估，**未改动任何代码或 `build.gradle.kts`**

---

## 0. 评估基调（先立规矩）

本项目的硬性定位（`ORIGINAL_REQUEST.md` §R4）明确要求：

> 「轻量级依赖，**避免引入冗余大型第三方库**」

因此本报告的唯一准绳是：**该依赖是否解决一个已经真实暴露的痛点**，而不是"这个库很流行 / 业界都在用"。
凡不能追溯到下面 6 个真实痛点的推荐，一律归入「不建议引入」。
本报告刻意区分两类依赖，这个区分对轻量级项目至关重要：

| 类别 | 是否进入 APK | 是否影响启动/体积 | 决策尺度 |
|---|---|---|---|
| **开发期工具**（Jacoco / 测试库 / benchmark / LeakCanary） | ❌ 不进 release APK | ❌ 不影响 | 只要收益明确即可引入 |
| **运行时依赖**（DI / 序列化 / 日志 / 图片 / 崩溃监控） | ✅ 进 APK | ✅ 影响 | **必须解决真实痛点，且无更轻替代** |

---

## 1. 依赖现状盘点

**一句话结论：当前依赖策略健康，已接近"最小可行集合"，无冗余运行时第三方库，无需为"精简"而做任何削减。**

事实核对（已亲自读取 `app/build.gradle.kts` + `build.gradle.kts` + `settings.gradle.kts`）：

- **运行时依赖 16 项，100% 为 AndroidX 官方 / JetBrains 官方，第三方库数量 = 0。**
- **KSP 处理器**：仅 `room-compiler`（官方）。
- **测试依赖**：`junit:4.13.2` + `kotlinx-coroutines-test:1.9.0`（均为纯 JVM，无 Android 测试栈）。
- **Debug 依赖**：`ui-tooling`、`ui-test-manifest`（官方）。

**盘点中发现的可优化项（非缺陷，仅记录，均不属于"引入依赖"范畴）：**

| # | 观察 | 影响 | 建议 |
|---|---|---|---|
| O1 | `release { isMinifyEnabled = false }` —— release 未开 R8 收缩 | `material-icons-extended` 未被裁剪，APK 体积偏大（当前 debug 16.7 MB） | **打开 R8/minify** 是比"引入依赖"更高性价比的体积优化；属构建配置，非依赖问题 |
| O2 | `androidx.compose.material:material-icons-extended` —— 全量图标库 | 是 APK 体积的主要贡献者之一 | 若打开 O1 的 R8，未用图标会被自动剥离；否则可评估仅用到的图标 |
| O3 | `androidx.compose.ui:ui-test-manifest`（debugImplementation） | 当前**无任何仪器化测试**使用它，实为闲置 | 引入 androidTest 栈后即有意义；否则可移除 |
| O4 | Compose BOM `2024.10.01`、Room `2.6.1` | 相对 2026 年属偏旧，但**稳定、自洽、零告警** | **保持现状**。升级需联动 Kotlin/AGP/KSP，收益低风险高，违背轻量定位 |
| O5 | 无版本目录（`libs.versions.toml`），版本硬编码 | 多模块时维护性略差 | 单模块项目无必要；可选改进，非依赖问题 |

> 结论：现状无需"增强"，只需可选地**打开 R8**（O1）以进一步瘦身。

---

## 2. 痛点 → 依赖映射（判断"是否该用依赖解决"）

| # | 真实痛点 | 该不该用第三方依赖解决？ | 理由 |
|---|---|---|---|
| P1 | **无 Compose UI 自动化测试**（Robolectric 路线已因 `TopLevelDestination` 静态初始化崩溃回退；无 emulator 故 androidTest 也跑不了） | **部分** —— 正确解法是**开发期测试栈 + 模拟器**，**不是**运行时依赖 | 真正的 blocker 是「**本机没装 emulator**」，不是「缺库」。加任何库都跑不了 UI 测试。代码侧还有一个零依赖的可选修复（见 §3-C） |
| P2 | **无代码覆盖率工具**（无 Jacoco，给不出覆盖率数字） | **是**，且是**开发期工具** | Jacoco 不进 APK、零启动成本、AGP 8.7.3 已内置，收益明确。**本报告最推荐的一项** |
| P3 | **无 DI 框架**（`AppNavigation.kt` 手写 4 个 `ViewModelProvider.Factory` 匿名对象） | **否**（对本项目规模） | 项目已有可用的服务定位器 `EbbinghausApp`；4 个 Factory 是**约 20 行样板**。用**零依赖的 `viewModelFactory {}` 助手**即可消除 90% 样板（见 §3-C）。引入 Hilt 属过度工程 |
| P4 | **无序列化库**（导入/导出功能未实现，列为 v1.1 可选） | **视需求** | 痛点尚不存在（功能未做）。等真做导入/导出时再定，届时 `kotlinx-serialization` 或**零依赖的 `org.json`/`JsonWriter`** 均可。**现在不该加** |
| P5 | **无崩溃监控 / 结构化日志**（仅 `android.util.Log` + `Toast`） | **否**（作为运行时依赖） | 应用**无 INTERNET 权限、纯离线**（见 §3 事实）；崩溃上报 SDK 需联网 + 第三方后端，与"离线、隐私、轻量"定位冲突。结构化日志用**20 行 `Logger` 包装 `Log`** 即可，无需 Timber |
| P6 | **无 emulator**，无法真机/模拟器走查 | **完全无关依赖** | 这是**环境缺口**，不是软件缺陷。任何依赖都解决不了。唯一正解：安装 emulator 组件或接真机 |

> 关键判断：**6 个痛点中，只有 P2 明确该用依赖解决（且是开发期工具）；P1/P4 是"有触发条件才引入"；P3/P5/P6 都不该用依赖解决。**

---

## 3. 分级建议表

> 等级：🟢 建议引入 ｜ 🟡 视需求引入（附触发条件）｜ 🔴 不建议引入（附理由）
> 坐标与兼容性见 §4。

### A. Jacoco（代码覆盖率）—— 🟢 建议引入

| 维度 | 评估 |
|---|---|
| 解决什么真实问题 | **P2**：当前无任何覆盖率数字，84 条单测的"覆盖广度"无法量化 |
| 引入成本 | **开发期工具，不进 APK**；AGP 8.7.3 **已内置 JaCoCo**，通常只需开启 `testCoverageEnabled` + 一个 `JacocoReport` 任务，**无需新增运行时依赖** |
| 风险 | 极低。Kotlin/AGP 8.7 下偶需 `includeNoLocationClasses = true` 才能采集 Kotlin 类；与 minSdk 26 无关（纯 JVM） |
| 更轻替代 | 无（覆盖率本身就得靠它） |
| **结论** | 🟢 **建议引入** —— 零 APK 成本、零启动影响、收益直接可交付 |

### B. Compose 仪器化测试栈（androidx.test + ui-test-junit4）—— 🟡 视需求引入

| 维度 | 评估 |
|---|---|
| 解决什么真实问题 | **P1**：Compose UI 层无自动化测试，三档响应式/导航/Badge 只靠 Preview 与人工走查 |
| 引入成本 | **开发期工具**（`androidTest` 源集，不进 release APK）；构建时长中等；学习成本中等（semantics 断言） |
| 风险 | **前置硬阻塞**：本机无 emulator → 当前**跑不起来**。另 `ui-test-junit4` 版本须随 BOM（2024.10.01 → `1.7.4`），与 minSdk 26 兼容 |
| 更轻替代 | 见 C（`TopLevelDestination` 惰性化 + Robolectric）—— 但 Robolectric 路线已被本项目验证不可行 |
| 触发条件 | **一旦具备 emulator / 真机 / CI 设备**即可引入 |
| **结论** | 🟡 **视需求引入** —— 方向正确，但**先解决 P6（装 emulator）**，否则引了也白引 |

### C. Hilt（依赖注入）—— 🔴 不建议引入

| 维度 | 评估 |
|---|---|
| 解决什么真实问题 | **P3**：`AppNavigation.kt` 4 个手写 Factory 的样板 |
| 引入成本 | **运行时依赖**（Hilt 运行时 + 生成的组件，数百 KB 级）；**构建时长明显增加**（KSP 处理）；学习成本高（注解、Scope、Module） |
| 风险 | 版本需与 KSP `2.0.21-1.0.28` / AGP 8.7.3 / Kotlin 2.0.21 精确对齐；Hilt 2.52+ 才支持 KSP，升级链复杂 |
| **更轻替代（强烈推荐）** | 用官方 `androidx.lifecycle:lifecycle-viewmodel-compose` 自带的 **`viewModelFactory { initializer { ... } }`**（**零新增依赖**），把 4 个匿名 Factory 收敛为一个可复用助手，样板减少 ~90% |
| **结论** | 🔴 **不建议引入** —— 为 20 行样板引入一套编译期 DI 框架，是典型的过度工程。已有 `EbbinghausApp` 服务定位器 + `viewModelFactory{}` 助手足够 |

### D. kotlinx-serialization（序列化）—— 🟡 视需求引入

| 维度 | 评估 |
|---|---|
| 解决什么真实问题 | **P4**：知识库导入/导出（v1.1 可选，**当前未实现**） |
| 引入成本 | 运行时依赖（**体积很小**，约数百 KB）；编译器插件（`org.jetbrains.kotlin.plugin.serialization:2.0.21`，与 Kotlin 同版本）；构建时长小幅增加 |
| 风险 | 低。JetBrains 官方、维护活跃；`1.7.x` 与 Kotlin 2.0.21 兼容；minSdk 26 无问题 |
| 更轻替代 | **`org.json`（Android 平台自带）/ `android.util.JsonWriter`** —— 对"导出为 JSON 文本"这种简单结构**零依赖即可完成** |
| 触发条件 | **真正开始实现导入/导出功能时**；且仅当数据结构复杂到手写 JSON 不划算时 |
| **结论** | 🟡 **视需求引入** —— 现在加=为不存在的功能付体积。真要做时优先评估零依赖方案 |

### E. 结构化日志（Timber / Kermit）—— 🟡 视需求引入（倾向不加）

| 维度 | 评估 |
|---|---|
| 解决什么真实问题 | **P5**：当前 `Log` 散落、无统一 tag/开关（实测 `Log` 仅 1 个文件用到，量极小） |
| 引入成本 | 运行时依赖（Timber 极小，~几十 KB）；几乎无学习成本 |
| 风险 | Timber `5.0.1` 发布于 **2021 年**，长期无更新（但 API 极稳定，minSdk 26 无问题）；Kermit 为 KMP 方案，对本项目偏重 |
| **更轻替代（推荐）** | 写一个 **~20 行的 `Logger` object**：debug 变体输出 `Log.d`，release 变体空实现（或按 `BuildConfig.DEBUG` 短路）。**零依赖、可控、可裁剪** |
| 触发条件 | 若日志需求增长到需要"树形多目标输出"（如同时写文件/上报）再考虑 |
| **结论** | 🟡 **视需求引入**（当前倾向不加）—— 20 行包装即可覆盖现有需求 |

### F. 崩溃监控（Firebase Crashlytics / Sentry / ACRA）—— 🔴 不建议引入

| 维度 | 评估 |
|---|---|
| 解决什么真实问题 | **P5** 的"崩溃监控"部分（**但该痛点并不紧迫**：应用已功能闭环、零 P0/P1） |
| 引入成本 | **重运行时依赖**。Firebase Crashlytics 需 `google-services.json` + Firebase 工程 + 大量传递依赖；Sentry 需 SDK + 网络 |
| 风险 | **与项目定位冲突**：`AndroidManifest.xml` **当前无 `INTERNET` 权限**，是纯离线、隐私友好应用；上报 SDK 会**新增网络权限 + 第三方后端 + 用户数据外发**，违背 §R4「轻量级」与离线设计。minSdk 兼容性无碍，但**架构方向相悖** |
| 更轻替代 | 本地崩溃日志文件（`Thread.setDefaultUncaughtExceptionHandler` 写入 app 私有目录），零依赖、零网络 |
| **结论** | 🔴 **不建议引入** —— 无后端、无账号、无分析诉求的离线应用，引入崩溃上报是"为库而库" |

### G. Baseline Profile / Macrobenchmark（性能）—— 🔴 暂不建议

| 维度 | 评估 |
|---|---|
| 解决什么真实问题 | **不属于 6 个真实痛点之一**（性能尚未被测量出问题；且 **P6 无 emulator** 导致连基准都跑不了） |
| 引入成本 | 开发/测试期工具（新增 benchmark 模块 + 插件），不进 release APK；但**需真机或模拟器**执行 |
| 风险 | Macrobenchmark 需 emulator/真机（**本机没有**）；Baseline Profile 生成会改构建结构，增加复杂度 |
| 更轻替代 | **先做 O1（打开 R8/minify）** —— 这是当前性价比最高的启动/体积优化，且零依赖 |
| 触发条件 | 有真机 + 实测**冷启动明显偏慢**时，再作为专项引入 |
| **结论** | 🔴 **暂不建议** —— 无实测痛点、无执行设备。先开 R8，观察后再定 |

### H. 图片加载（Coil / Glide）—— 🔴 不建议引入

| 维度 | 评估 |
|---|---|
| 解决什么真实问题 | **无** —— 本应用是**纯文本知识点备忘录**，无网络图片、无远程资源加载 |
| 事实核对 | 已确认：无 `AsyncImage`/图片加载调用；数学公式渲染走**本地 KaTeX 资源 + WebView**（`MathView.kt` + `assets/katex/`），**不联网、不需图片库** |
| **结论** | 🔴 **不建议引入** —— 经典"看起来该加其实完全不需要"的例子 |

### I. 日期时间（kotlinx-datetime / ThreeTenABP）—— 🔴 不建议引入

| 维度 | 评估 |
|---|---|
| 解决什么真实问题 | **无** —— 项目已用 **`java.time`（`LocalDate`/`Clock`）** 处理自然日粒度（已在 `core/engine`、`data/repository`、`data/local` 等 20 个文件中确认使用） |
| 关键事实 | **minSdk 26 起 `java.time` 原生可用**，无需 desugaring、无需 ThreeTenABP、无需 kotlinx-datetime |
| **结论** | 🔴 **不建议引入** —— 又一个"看似需要、实则平台已内置"的例子 |

---

## 4. 候选清单（Maven 坐标 + 版本 + 兼容性）

> 兼容性基准：**Kotlin 2.0.21 ｜ KSP 2.0.21-1.0.28 ｜ AGP 8.7.3 ｜ Gradle 8.10.2 ｜ JDK 17 ｜ compose-bom 2024.10.01（Compose UI 1.7.x / material3 1.3.1）｜ minSdk 26**

| 方向 | Maven 坐标 | 版本 | 与当前栈兼容性 | 类别 | 建议 |
|---|---|---|---|---|---|
| 覆盖率 | `org.jacoco`（Gradle 插件，AGP 已内置） | 随 AGP（0.8.12 级） | ✅ 纯 JVM；Kotlin 类需 `includeNoLocationClasses` | 开发期 | 🟢 |
| UI 测试 | `androidx.test:runner` | `1.6.2` | ✅ | 开发期 | 🟡 |
| UI 测试 | `androidx.test.ext:junit` | `1.2.1` | ✅ | 开发期 | 🟡 |
| UI 测试 | `androidx.test.espresso:espresso-core` | `3.6.1` | ✅ | 开发期 | 🟡 |
| UI 测试 | `androidx.compose.ui:ui-test-junit4` | 随 BOM → `1.7.4` | ✅ 必须随 BOM，勿手写版本 | 开发期 | 🟡 |
| UI 测试 | `androidx.compose.ui:ui-test-manifest` | 随 BOM | ✅（**已在** debugImplementation） | 开发期 | 🟡 |
| DI | `com.google.dagger:hilt-android` + `hilt-android-compiler`（KSP） | `2.57.2` | ⚠️ 需与 KSP 2.0.21-1.0.28 对齐；Hilt 2.52+ 支持 KSP | 运行时 | 🔴 |
| DI（可选配套） | `androidx.hilt:hilt-navigation-compose` | `1.2.0` | ✅ | 运行时 | 🔴 |
| 序列化 | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.3` | ✅ 与 Kotlin 2.0.21 兼容；插件 `org.jetbrains.kotlin.plugin.serialization:2.0.21` | 运行时 | 🟡 |
| 日志 | `com.jakewharton.timber:timber` | `5.0.1` | ✅ minSdk 26 无碍；**2021 年后未更新** | 运行时 | 🟡（倾向不加） |
| 性能 | `androidx.benchmark:benchmark-macro-junit4` | `1.3.4` | ⚠️ 需 emulator/真机（本机无） | 开发期 | 🔴 |
| 性能 | `androidx.baselineprofile:baselineprofile`（Gradle 插件） | `1.3.4` 级 | ⚠️ 需真机生成，改构建结构 | 开发期 | 🔴 |
| 图片 | `io.coil-kt.coil3:coil-compose` | `3.6.2` | ✅（但**项目无此需求**） | 运行时 | 🔴 |
| 日期时间 | `org.jetbrains.kotlinx:kotlinx-datetime` | `0.6.2` | ✅（但**java.time 已够用**） | 运行时 | 🔴 |
| 崩溃监控 | `com.google.firebase:firebase-crashlytics`（+ plugin） | 随 Firebase BOM | ⚠️ 需联网 + `google-services.json` + INTERNET 权限 | 运行时 | 🔴 |
| 崩溃监控 | `io.sentry:sentry-android` | `7.x` | ⚠️ 需联网 + INTERNET 权限 + 第三方后端 | 运行时 | 🔴 |
| 内存（补充） | `com.squareup.leakcanary:leakcanary-android` | `2.14` | ✅ debugImplementation，不进 APK | 开发期 | 🟡（可选） |

---

## 5. 明确结论

**一句话回答用户的问题：**

> **当前项目不需要引入任何"运行时"第三方依赖；唯一值得引入的是"开发期工具"，首推 Jacoco（代码覆盖率）。**

**如果只允许引入 1~3 个，选哪几个：**

| 优先级 | 选择 | 为什么 |
|---|---|---|
| **1（必选）** | **Jacoco** | 唯一"零 APK 成本、零启动影响、收益明确"的项；AGP 已内置，改动最小 |
| **2（条件性）** | **androidx.test + compose ui-test-junit4** | UI 测试是真痛点，但**前置条件是先装 emulator**（P6）；设备就绪即引入 |
| **3（条件性）** | **kotlinx-serialization** | 仅当**真正实现导入/导出**时才引入；否则用零依赖的 `org.json` |

> 其余方向（DI、日志、崩溃监控、图片、日期时间、性能）**均不建议引入**——要么有更轻的零依赖替代，要么根本不解决真实痛点。

---

## 6. 不建议引入清单（"看起来该加其实不该加"）

> 这一节与 §3 同等重要，目的是**防止过度工程**。

| 依赖 | 直觉上"该加"的理由 | 为什么不该加 |
|---|---|---|
| **Hilt / Dagger** | "Android 标准 DI，代码更整洁" | 4 个 ViewModel + 3 个 Repository，已有 `EbbinghausApp` 服务定位器；用官方 `viewModelFactory{}`（**零依赖**）即可消除样板。Hilt 只带来编译时长 + 学习成本 + 运行时体积 |
| **kotlinx-datetime** | "处理日期时间更 Kotlin 化" | **minSdk 26 原生支持 `java.time`**，项目已在用，无任何收益 |
| **Coil / Glide** | "现代 Android 都该有图片库" | 纯文本应用，**无任何图片加载需求**；公式走本地 KaTeX + WebView |
| **Firebase Crashlytics / Sentry** | "上线就该有崩溃监控" | **纯离线、无 INTERNET 权限**的隐私友好应用；上报需联网 + 第三方后端，违背 §R4 轻量定位与离线设计。用本地崩溃日志文件即可 |
| **Timber** | "日志框架，比 Log 优雅" | 现有 `Log` 使用量极小（1 个文件）；**20 行 `Logger` 包装**即可覆盖，无需依赖 |
| **androidx.compose.material3.adaptive** | "官方响应式布局库" | **alpha，API 不稳定**；双窗格已手写 `Row` 实现（已评估拒绝，勿重复建议） |
| **Robolectric** | "JVM 上跑 Compose UI 测试，省去模拟器" | **已实测与本项目不兼容**：`TopLevelDestination` 的 `data object` 静态初始化即引用 Compose 图标 → `NoClassDefFoundError`。已完整回退（勿重复建议） |
| **Accompanist 系列** | "官方实验库，功能多" | 多数能力已被官方 API 取代；本地无缓存。已评估拒绝 |
| **WorkManager** | "调度复习提醒" | §R3 明确要求**免系统级通知权限的应用内看板**，无后台任务需求；引入即违背需求 |
| **Retrofit / OkHttp / Ktor** | "网络层标准配置" | 应用**完全离线、无后端、无 INTERNET 权限**，无任何网络调用 |
| **Paging 3** | "列表分页更规范" | 个人知识点备忘录数据量小，`LazyColumn` 足够；引入增加复杂度无收益 |
| **DataStore（迁移自 Room）** | "现代键值存储" | 项目已用 Room 统一持久化，`UserSettings` 已在 Room 内；引入第二套存储 = 双写/迁移成本 |
| **升级 Room 2.7+ / Compose BOM** | "用最新版本" | 需联动 Kotlin/AGP/KSP，收益低、风险高（Room 2.7 转向 KMP 制品结构），违背"稳定自洽"现状 |

---

## 7. 一句话总纲

**这个项目已经"足够轻"，真正的短板是「测试/度量工具缺失（Jacoco、androidTest）」与「环境缺口（无 emulator）」，而不是"缺第三方库"。**
**结论：引入 Jacoco 即可；其余等真实触发条件出现再说。任何运行时依赖的引入都必须先回答——"它解决了哪个真实痛点，且没有更轻的替代？"**
