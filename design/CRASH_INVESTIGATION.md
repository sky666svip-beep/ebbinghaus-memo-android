# 冷启动闪退（一打开就崩）专项排查报告

> 工程师：寇豆码 ｜ 日期：2026-09-16
> 对象：`D:\Projects\androidapk`，崩溃包 = `app-debug.apk`（debug 变体），设备 Android 14~16（API 34~36）
> 立场：**诚实优先于好看**。本报告区分「已定位」与「未定位」，并对每一处改动标注「根因修复 / 防御性加固」。
> 前置：主理人已实测排除 8 类嫌疑（Room 列映射、迁移未注册、`by lazy` 顺序、`PreferenceStore` 构造期同步读、合并清单、JaCoCo 插桩、native 16KB 对齐、入口类），本报告不重复这些项。

---

## 0. 结论速览

| 项 | 结论 |
|---|---|
| **根因是否定位** | **未定位（诚实声明）**。穷举式静态审计未发现任何**必然**导致冷启动崩溃的确定性缺陷；静态可核验的 15 个嫌疑面中，**12 项经实读/反编译证据判定「不会崩」**，**3 项为真实存在的健壮性缺口**（启动期协程无异常边界），已按「防御性加固」处置。 |
| **是否可复现** | **本机无法复现（诚实声明）**。Android Emulator 在本机**无法运行**（证据见 §2）。 |
| **已改动文件** | 3 个（`DashboardViewModel.kt`、`SnapshotManager.kt`、`MemoListViewModel.kt`），**零新增依赖 / 零新增权限 / 零 Room schema 变更 / SDK 仍为 35** |
| **`./gradlew test`** | **267 passed / 0 failed / 0 skipped**（app 220 + core 47；与基线 **267 完全一致**，既有断言零删改） |
| **`./gradlew assembleDebug`** | **BUILD SUCCESSFUL** |
| **`./gradlew :app:lintDebug`** | **0 errors / 9 warnings**（全部为 `ModifierParameter` 代码风格警告，无 `MissingClass`/`NewApi`/`Instantiatable`/`WrongThread`） |

**一句话**：我没能找到「必然导致闪退」的根因；我把启动路径上**真实存在但未确认被触发**的 3 处「未捕获异常可直达进程默认异常处理器」的缺口做了防御性加固，并让加固**可诊断（日志）且不静默**。请按 §6 用最简单的方式取一次崩溃日志，即可把「未定位」变为「已定位」。

---

## 1. 审计范围与方法

- **实读源码**：`app/src/main/**`、`core/src/main/**` 全部启动路径相关文件（入口、6 个 ViewModel、导航宿主、Scaffold、主题、窗口尺寸、MathView、Room/DAO/仓储、PreferenceStore、SnapshotManager）。
- **反编译核验**：对 `androidx.window:window:1.0.0`、`material3-window-size-class:1.3.1`、`emoji2:1.3.0`、`profileinstaller:1.4.1`、`activity:1.9.3` 的 AAR 用 `javap -c` 逐方法核验异常路径（而非凭记忆推断）。
- **构建产物核验**：`unzip -l` 实查 debug/release APK 内容（dex 数、native 库、assets、`assets/dexopt/baseline.prof`）。
- **静态扫描**：`grep` 全项目 `!!` / `lateinit` / `checkNotNull` / `requireNotNull` / `first()` / `single()` / `indexOfFirst` / `error(` / `runBlocking` / `Dispatchers.Main`。
- **真实构建**：`test`、`assembleDebug`、`lintDebug` 全部实跑。

---

## 2. 复现尝试（真实记录，全部失败）

| 尝试 | 命令 / 动作 | 真实结果 |
|---|---|---|
| 1. x86_64 模拟器（硬件加速） | `emulator -avd Android16_API36 -no-window` | `ERROR \| x86_64 emulation currently requires hardware acceleration!` / `CPU acceleration status: Android Emulator hypervisor driver is not installed on this machine`（`emulator-check accel` 返回码 **6**）→ **无法启动** |
| 2. x86_64 模拟器（软件模拟 `-accel off`） | 同上 + `-accel off` | 进程起得来但 **4.5 分钟内仅消耗 0.42s CPU**，`adb devices` 恒为 `offline` → 卡死，**无法完成引导** |
| 3. arm64 系统镜像（规避 hypervisor） | 下载并安装 `system-images;android-36;aosp_atd;arm64-v8a`（1.7 GB），手写 AVD `atd36_arm` 后启动 | `FATAL \| Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host. System image must match the host architecture.` → **无法启动** |
| 4. Robolectric | **未引入** | 项目历史上试过并已回退（`TopLevelDestination` 的 `data object` 静态初始化引用 Compose 图标 → `NoClassDefFoundError`）。按任务要求**未贸然重新引入**（代价：新增 `robolectric` + `androidx.test` 依赖，与「零新增依赖」冲突，且需改造 6 个 ViewModel 的构造路径）。 |

**结论：本机**（无 HAXM/AEHD/WHPX 硬件加速、x86_64 host 不支持 arm64 guest）**在物理上无法运行 Android 模拟器，也无法连接真机 → 本次无法复现、无法取得 logcat。** 这是环境限制，不是「未尝试」。

---

## 3. 审计结论表（A~E 共 15 项）

> 「会不会崩」= 是否存在**可静态确认的**异常路径。「证据」= 实际读到的 `文件:行号` 或反编译/解包结果。

### A. 启动期副作用（最高可疑，均在首帧前由协程触发）

| # | 项 | 会不会崩 | 证据（`文件:行号`） | 处置 |
|---|---|---|---|---|
| **A1** | `DashboardViewModel.checkAppLaunchPrompt()` / `OnCheckAppLaunch` | **加固前：会**（未捕获异常可达进程默认异常处理器）<br>**加固后：不会** | 触发链：`MemoListScreen.kt:306-308`（`LaunchedEffect(Unit)` → `OnCheckAppLaunch`）→ `DashboardViewModel.kt:132` → `:158`。`purgeExpiredTrash` **已被** `runCatching` 包裹（`:168-170`）；E02 快照链路**加固前无**异常边界（`:172-182`）；`settingsRepository.getSettings().first()`（`:184`）、`reviewRepository.getDueReviewTasks(today).first()`（`:203`）、`updateLastActiveDate`（`:248`）**加固前均无**边界 → 任一抛异常即闪退 | **防御性加固（未确认根因）**：整条启动检查包裹异常边界（`:160` try / `:250` CancellationException 原样抛出 / `:253` `catch (e: Exception)` + `Log.e`），失败仅记录日志并降级 |
| **A2** | `SnapshotManager` 全文 | **加固前：会**（`checkAndSnapshotOnLaunch` 自身无边界）<br>**加固后：不会** | `snapshotNow()` 已有 try/catch：`SnapshotManager.kt:97`（`withContext(IO)`）、`:110`（`DocumentsContract.createDocument`，在 try 内）、`:117`（`openOutputStream`，在 try 内）、`:125` `catch (e: SecurityException)` → `PERMISSION_LOST`、`:127` `catch (e: Exception)` → `IO_ERROR`；`pruneTo()` 用 `runCatching`：`:139`、`:171`。**缺口**：`checkAndSnapshotOnLaunch()`（原 `:61-69`）自身未包边界 | **防御性加固（未确认根因）**：`:72` 起改为 `try` 表达式，`:85` 取消信号原样抛出，`:88` 收敛为 `Failure(IO_ERROR)` + `Log.e`。这样**任何调用方**都不可能被本方法抛出异常击穿 |
| **A3** | `TrashViewModel` 上提后 `init` 订阅 | **不会** | `TrashViewModel.kt:118-120`（`init` → `observeTrash`）、`:128-136`（`combine().flatMapLatest().catch{}`）。`.catch` 位于 `collect` **上游**，可捕获 `flatMapLatest` 变换块内的**同步**抛异常；下游 `collect` 体仅 `_uiState.update`（不会抛）。`catch` 不捕获 `Error`，但本链路无 `Error` 来源 | **无需处置**（已完备）。另：该 `catch` 会写 `errorMessage = "回收站读取失败"`，**用户可见，非静默** |
| **A4** | `MemoListViewModel.observeMemos()` / `observeAllTags()` | **不会**（无未捕获路径） | `MemoListViewModel.kt:218-236`（`observeMemos`，`.catch` 于 `:225`，写 `errorMessage = "数据读取失败"`，用户可见）；`:247-258`（`observeAllTags`，`.catch` 于 `:250`）。两者 `.catch` 均在 `collect` 上游，覆盖全部上游抛点 | **`observeAllTags` 原为「静默吞掉」（空 catch）** → **防御性加固（未确认根因）**：`:250-252` 补 `Log.w`，避免「静默失效」，行为（不阻断主列表）不变 |
| **A5** | `lastCheckedDate` 同日去重逻辑 | **不会** | `DashboardViewModel.kt:87`（声明）、`:164-165`（判定 + 赋值）。该字段仅在 `viewModelScope`（`Dispatchers.Main.immediate`）内读写，`onEvent` 亦由 UI 线程调用 → 单线程访问，**无并发/状态竞态** | **无需处置** |

### B. `androidx.startup` 自动初始化（在 `Application` 之前跑）

| # | 项 | 会不会崩 | 证据 | 处置 |
|---|---|---|---|---|
| **B6** | 3 个 initializer + 手写 `baseline-prof.txt` | **不会** | 清单：`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml:46-59`（`EmojiCompatInitializer` / `ProcessLifecycleInitializer` / `ProfileInstallerInitializer`）。<br>① **EmojiCompat**（`emoji2:1.3.0`，`javap` 核验）：`create()` 仅做 `EmojiCompat.init(BackgroundDefaultConfig)` + `delayUntilFirstResume()`；**真正加载 emoji 字体被推迟 500ms 到 `LoadEmojiCompatRunnable`**（后台、失败仅日志），`create()` 内无字体加载抛点 → **不需要 `app:emojiCompatEnabled="false"`**。<br>② **ProfileInstaller**（`profileinstaller:1.4.1`，`javap` 核验）：`create()` 只 `Choreographer.postFrameCallback{}`（首帧**之后**）→ `postDelayed(5000+rand)` → 后台线程 → `ProfileInstaller.writeProfile`（内部自捕获）。且 **debug APK 内根本不含 profile**：`unzip -l app-debug.apk \| grep -iE 'baseline\|dexopt'` → **仅命中 `META-INF/...version`，无 `assets/dexopt/baseline.prof`** → debug 下该 initializer 直接 no-op。（对照：release APK **有** `assets/dexopt/baseline.prof` 4636 B / `.profm` 588 B。）<br>③ **手写 profile 合法性**：`app/src/main/baseline-prof.txt` 8 行，仅引用本项目类；AGP 已成功将其编译进 release 产物 → 语法合法；ART 对未知条目按规范忽略，**不会因解析失败抛异常** | **无需处置** |

### C. 首帧 Compose

| # | 项 | 会不会崩 | 证据 | 处置 |
|---|---|---|---|---|
| **C7** | `EbbinghausTheme` 的 `Build.VERSION` / `dynamicColor` 分支 | **不会** | `Theme.kt:57-70`：`dynamicColor` 默认 **`false`**，而 `MainActivity.kt:33` 以 `EbbinghausTheme { }` **无参**调用 → 运行期 `dynamicColor && SDK_INT >= S` 分支**恒不成立**（死分支）。`isSystemInDarkTheme()` 无抛点 | **无需处置** |
| **C8** | `rememberWindowSizeClass()` → `calculateWindowSizeClass(activity)`（**主理人最怀疑项**） | **不会（已证伪）** | `WindowSizeClass.kt:99-137`。反编译 `AndroidWindowSizeClass_androidKt.calculateWindowSizeClass`：仅调用 `WindowMetricsCalculator.Companion.getOrCreate().computeCurrentWindowMetrics(activity)`；反编译 `WindowMetricsCalculator$Companion.getOrCreate()`：**恒返回 `WindowMetricsCalculatorCompat.INSTANCE`**（decorator 为恒等函数）→ **完全不经过 `androidx.window.extensions`**；`WindowMetricsCalculatorCompat.computeCurrentWindowMetrics` 在 `SDK_INT>=30` 分支 → `ActivityCompatHelperApi30.currentWindowBounds` → `activity.windowManager.currentWindowMetrics.bounds`（**无抛点**）。解析依赖版本实测为 `androidx.window:window:1.0.0`。另外 `activity` 恒非 null（`MainActivity` 本身即 Activity，`findActivity()` 命中第一个分支） | **无需处置**（此项可**排除**） |
| **C9** | `Icons.AutoMirrored.Filled.Label` / `Icons.Default.Deselect` / `SelectAll` 等图标是否存在 | **不会** | `AppNavigation.kt:16-21`（import）、`:266-289`（使用）；`TopLevelDestination.kt:3-6,30,37,44`。图标若不存在是**编译期**错误——`assembleDebug` 实测 **BUILD SUCCESSFUL** → 全部存在 | **无需处置** |
| **C10** | `MathView`（WebView）首帧创建 / `assets/katex/` 完整性 | **不会** | `MathView.kt:75-86`：`needsRichRendering(text)==false` 走原生 `Text` **快路径，完全不创建 WebView**；仅富文本路径才延迟 `WEBHOOK_INIT_DELAY_MS=120ms`（`:100-103`）后创建（`:130`）。**首次冷启动 DB 为空 → 列表无条目 → 首帧不创建任何 WebView**。<br>assets 实查齐全：`math_renderer.html` / `katex.min.js` / `marked.min.js` / `auto-render.min.js` / `katex.min.css` / `fonts/`(6 个 woff2)，且 `unzip -l app-debug.apk` 确认全部已打包 | **无需处置** |

### D. 构建配置

| # | 项 | 会不会崩 | 证据 | 处置 |
|---|---|---|---|---|
| **D11** | `debug` 块与 `packaging` | **不会** | `app/build.gradle.kts:38-43`：`applicationIdSuffix=".debug"`（仅改包名 → 合并清单 `package="com.ebbinghaus.memo.debug"`，条目正常）、`isDebuggable=true`（预期）、`enableUnitTestCoverage=true`（**仅配置单测 JVM 的 JaCoCo agent，不插桩 APK**；主理人已证 dex 内 `org/jacoco` 0 命中）。`packaging`（`:59-63`）仅排除 `META-INF/{AL2.0,LGPL2.1}` → 无运行期影响 | **无需处置** |
| **D12** | framework 主题 `@android:style/Theme.Material.Light.NoActionBar` + `enableEdgeToEdge()` 在 Android 15/16 | **不会** | `AndroidManifest.xml:11,17`（主题）、`MainActivity.kt:22`（`enableEdgeToEdge()`）。`ComponentActivity`（非 AppCompat）配平台主题合法。反编译 `activity:1.9.3` 的 `EdgeToEdge`：实现按 `SDK_INT` 选择，**最高档为 `EdgeToEdgeApi30`**（无 `EdgeToEdgeApi35`）→ 在 API 34~36 上走 `EdgeToEdgeApi30`，其行为为 `setDecorFitsSystemWindows(false)` + 设置系统栏色（API 35+ 已弃用为 no-op，仅打日志）→ **无抛点** | **无需处置** |
| **D13** | `./gradlew :app:lintDebug` | **不会**（无 Error/Fatal） | **实跑输出**：`0 errors, 9 warnings`，全部为 `ModifierParameter from androidx.compose.ui`（`DashboardBanner.kt:56`、`EmptyState.kt:46,103`、`ErrorState.kt:37`、`LoadingSkeleton.kt:128`、`MemoListScreen.kt:123,162,299,658`）。**无** `MissingClass` / `NewApi` / `Instantiatable` / `WrongThread`。报告：`app/build/reports/lint-results-debug.txt` | **无需处置** |

### E. 自查其他

| # | 项 | 会不会崩 | 证据 | 处置 |
|---|---|---|---|---|
| **E14** | `!!` / `checkNotNull` / `requireNotNull` / `lateinit` / `first()` / `single()` / `indexOfFirst` 未判空 | **启动路径上不会** | `!!` → main 源集 **0 命中**；`lateinit` → main 源集 **0 命中**；`requireNotNull` → 仅 `ExportModels.kt:119`（**导入**路径，非启动）；`error(` → 仅 `DataSafetyViewModel.kt:170,188`（**导出/导入**路径，非启动）；`first()` → `DashboardViewModel.kt:184,203`（**已加固**）、`ReviewViewModel.kt:93,94`（见 §4 遗留项）；`single()` / `indexOfFirst` → **0 命中**。`getSettings()`/`getDueReviewTasks()` 为 Room Flow，**必然先发一个值**（`SettingsRepositoryImpl.kt:16-20` 用 `?: UserSettingsEntity()` 兜底）→ `first()` **不会**抛 `NoSuchElementException` | 无需处置（`ReviewViewModel` 见 §4） |
| **E15** | `Dispatchers.Main` 上的阻塞操作 | **不会** | `runBlocking` → **0 命中**；显式 `Dispatchers.Main` → **0 命中**；`viewModelScope` 默认 Main，但其内**无**主线程 DB/文件 I/O：快照 I/O 显式在 `Dispatchers.IO`（`SnapshotManager.kt:97,138`），Room 查询由 Room 自身 executor 执行；所有 `.value` 读取均为 `MutableStateFlow`/`StateFlow`（非阻塞，`PreferenceStore.kt:54-71` 仅构造期同步读 `SharedPreferences`，主理人已排除） | **无需处置** |

**合计：15 项中 12 项判定「不会崩」并给出证据；3 项（A1 / A2 / A4-静默吞掉）为真实健壮性缺口，已做防御性加固。**

---

## 4. 根因判定

### 判定：**未定位（未找到「必然导致冷启动闪退」的确定性根因）**

**为什么未定位**：
1. 静态可核验的 15 个嫌疑面中，**12 项已用实读源码 / 反编译 / 解包证据证伪**（含主理人最怀疑的 `calculateWindowSizeClass`，见 C8）。
2. 剩余 3 项是**「条件触发」型**缺口（需要一个未预期异常真的被抛出才会崩），而**我无法证明该异常在当前场景下真的被抛出**——本机无法复现、无法取 logcat。
3. 因此我**不会**把任何一项谎称为「根因」。它们被如实标注为**「防御性加固（未确认根因）」**。

**我确实能给出的、可被证据支持的最强推断（非结论，仅推断）**：
- 崩溃时机「一打开就崩、连主界面都看不到」，与 `MemoListScreen.kt:306-308` 的 `LaunchedEffect(Unit) → OnCheckAppLaunch` **在首帧前派发启动期副作用**这一时序**高度吻合**：该链路会（a）在启动时写数据库、（b）做 SAF 文件 I/O（`snapshotEnabled=true` 且授权失效时）、（c）读设置；而这些在加固前**全部没有异常边界**，任一抛异常即冒泡到进程默认异常处理器 → 闪退。
- 但**前提条件**（如用户曾开启快照且目录授权已失效、或存在未预期的 DB 异常）**无法在本机验证**，故不构成「已定位」。

### 遗留风险（本次**有意未改**，理由如下）

| 遗留项 | 位置 | 为何不改 |
|---|---|---|
| `ReviewViewModel.loadReviewBatch()` 无异常边界 | `ReviewViewModel.kt:89-119`（`init` 于 `:82-84`，随 `AppNavigation` 组合**在启动期**执行） | 要**正确**加固必须给 `ReviewUiState` 增加错误态字段并在 `ReviewScreen` 呈现，否则会把异常吞掉、让复习页**静默**显示「今日已全部搞定」（即任务明令禁止的「静默失效」）→ 属 UI 改造，超出「最小变更 / 不顺手重构」边界。**如实列出，待确认** |
| `SettingsViewModel.init` 无异常边界 | `SettingsViewModel.kt:41-53` | 同上：吞掉异常会令 `isLoading` 永久为 `true`（无限骨架屏），需新增错误态才能正确加固 → 超出本次范围 |
| `MemoDetailViewModel.loadMemoDetails()` 无异常边界 | `MemoDetailViewModel.kt:51-64` | 该类**不在启动期创建**（仅在导航到详情页时创建），与「一打开就崩」无关 |

> 以上 3 项均**未改动**，符合「仅在确认有风险时才改」「最小变更」的硬约束。若后续拿到崩溃日志指向其中任一，再按「带错误态的正确加固」实施。

---

## 5. 已做的改动（逐条，区分根因修复 / 防御性加固）

> **本次全部改动均为「防御性加固（未确认根因）」，无一条是「根因修复」**（因为根因未定位）。
> 全部改动**零新增依赖、零新增权限、零 Room schema 变更、SDK 保持 35**。

### 改动 1 — `app/src/main/java/com/ebbinghaus/memo/ui/dashboard/DashboardViewModel.kt`
**性质：防御性加固（未确认根因）**

- `:6` 新增 `import android.util.Log`；`:23` 新增 `import kotlinx.coroutines.CancellationException`；`:27-28` 新增 `private const val LOG_TAG = "EbbinghausLaunch"`。
- `checkAppLaunchPrompt()`（`:158`）的 `viewModelScope.launch` 体整体包裹异常边界：
  - `:160` `try {`（覆盖 E06 回收站清理 + E02 SAF 快照 + 设置读写 + 看板判定）
  - `:250` `catch (cancellation: CancellationException) { throw cancellation }` —— **取消信号原样抛出**，不破坏结构化并发
  - `:253-255` `catch (e: Exception) { Log.e(LOG_TAG, "启动检查失败，已降级继续启动（看板提醒可能缺失）", e) }` —— **仅记录，不阻断启动**
- **为什么只 catch `Exception` 而不 catch `Throwable`**：避免吞掉 `OutOfMemoryError` / `StackOverflowError` 等致命 `Error`（与主理人「`catch (e: Exception)` 不捕获 `Error`」的口径一致）。
- **为什么不静默**：失败写 `Log.e`（tag `EbbinghausLaunch`），可被 §6 的方式抓到；且看板提醒属**便利通知**，其降级不影响列表/复习/设置任何功能 → 不构成「功能静默失效」。
- **行为影响**：**正常路径零变化**（`DashboardViewModelTest` 7 条用例全部覆盖正常路径，实测全绿）。

### 改动 2 — `app/src/main/java/com/ebbinghaus/memo/data/snapshot/SnapshotManager.kt`
**性质：防御性加固（未确认根因）**

- `:5` 新增 `import android.util.Log`；`:9` 新增 `import kotlinx.coroutines.CancellationException`；`:15-16` 新增 `LOG_TAG`。
- `checkAndSnapshotOnLaunch()`（`:72`）由「裸 return 序列」改为 `try` 表达式：
  - 语义**完全等价**（未开启 → `Skipped`；未选目录 → `Skipped`；同日已快照 → `Skipped`；否则 `snapshotNow()`）
  - `:85` `catch (cancellation: CancellationException)` → 原样抛出
  - `:88-90` `catch (e: Exception)` → `Log.e` + `Failure(IO_ERROR)`
- **为什么**：本方法在**冷启动首帧前**被调用；原实现自身无边界（只有被它调用的 `snapshotNow()` 有）。加固后**任何调用方**都不可能被本方法击穿。
- **失败可感知**：返回 `Failure(IO_ERROR)` 会被 `DashboardViewModel` 转为 `disableSnapshot()` + Snackbar「自动快照失败：写入异常，已关闭自动快照」→ **用户可见，非静默**（符合 PRD §5-11 原设计）。
- **行为影响**：正常路径零变化（该类无单测覆盖，见 §7 说明）。

### 改动 3 — `app/src/main/java/com/ebbinghaus/memo/ui/memolist/MemoListViewModel.kt`
**性质：防御性加固（未确认根因）**

- `:3` 新增 `import android.util.Log`；`:25-26` 新增 `LOG_TAG`。
- `observeAllTags()` 的 `.catch { }`（原**空 catch，静默吞掉**）改为 `.catch { e -> Log.w(LOG_TAG, "标签列表读取失败，标签筛选条将为空（不阻断主列表）", e) }`（`:250-252`）。
- **为什么**：直接命中任务约束「**不要把异常吞掉后让功能静默失效**——加固要有日志/提示」。行为（不阻断主列表、`allTags` 保持为空）**不变**，仅补可诊断性。
- **行为影响**：正常路径零变化；无任何单测触发该 `catch`（见 §7）。

### 未改动（明确声明）
- `AndroidManifest.xml`：**零改动**（零新增权限）。
- `app/build.gradle.kts` / `core/build.gradle.kts`：**零改动**（零新增依赖，`compileSdk`/`targetSdk` 仍为 35）。
- Room 实体/DAO/schema：**零改动**。
- 既有测试文件：**零改动**（未删改任何断言）。

---

## 6. 修复后的测试与构建结果（真实数字）

### 6.1 单元测试 —— `./gradlew test --console=plain --rerun-tasks`

```
BUILD SUCCESSFUL in 48s
55 actionable tasks: 55 executed
```

从 `build/test-results/**/*.xml` 逐套件解析：

| 源集 | classes | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `app:testDebugUnitTest` | 37 | **220** | 0 | 0 | 0 |
| `app:testReleaseUnitTest` | 37 | **220** | 0 | 0 | 0 |
| `core:test` | 5 | **47** | 0 | 0 | 0 |
| **合计（去重 debug/release 重复计数）** | — | **267** | **0** | **0** | **0** |

- **基线 267 → 修复后 267，完全一致**；既有断言**零删改**（未新增、未删除任何 `@Test`）。
- debug / release 两个变体结果一致。

### 6.2 构建 —— `./gradlew assembleDebug --console=plain`

```
> Task :app:packageDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL in 3s
```

- 产物：`app/build/outputs/apk/debug/app-debug.apk` = **17,794,740 B**（修复前 17,784,781 B，**+9,959 B**，为新增日志字符串 + 异常表 + dex 重排所致，量级可忽略）。

### 6.3 Lint —— `./gradlew :app:lintDebug --console=plain`

```
BUILD SUCCESSFUL
0 errors, 9 warnings
```

- 9 条警告**全部**为 `ModifierParameter`（Jetpack Compose 代码风格：`modifier` 应为第一个可选参数），与本次改动无关，且**修复前即为 9 条**（改动未引入新警告）。
- **无** `MissingClass` / `NewApi` / `Instantiatable` / `WrongThread`。

### 6.4 编译告警
本次改动未引入新的 `warning:`（`assembleDebug` 输出无告警行）。

---

## 7. 给用户的下一步：如何用**最简单的方式**拿到崩溃日志

> 目标：把本次的「**未定位**」变成「**已定位**」。只需要一份崩溃时的 logcat（哪怕只有 50 行）。

### 方案 A（**最推荐，无需电脑、无需 root**）：Android 自带「错误报告」
1. 手机 **设置 → 关于手机 → 连续点 7 次「版本号」** 打开开发者选项；
2. **设置 → 系统 → 开发者选项 → 错误报告（Bug report）→ 完整报告（Full report）**；
3. 等待 1~2 分钟生成，选择「分享/保存」→ 用微信/邮件把 zip 发出来即可；
4. zip 里的 `bugreport-*.txt` 中搜索 `FATAL EXCEPTION` 或 `EbbinghausLaunch` 即可定位崩溃栈。

### 方案 B（无需电脑）：手机端 logcat 类 App
- 安装任意「Logcat / 日志查看器」类 App（如 F-Droid 上的 *Logcat Reader*）。
- ⚠️ 注意：**Android 4.1+ 起读取他人日志需 `READ_LOGS` 权限**，普通 App 拿不到；这类 App 通常需要**先用电脑 `adb` 授权一次**。若手头完全没有电脑，**请优先用方案 A**。

### 方案 C（有任意一台电脑，含朋友/网吧）：`adb logcat`
```bash
adb logcat -b crash -d            # 只导出崩溃缓冲区（最快）
# 或持续抓取，再在手机上复现崩溃：
adb logcat | grep -E "AndroidRuntime|FATAL|EbbinghausLaunch"
```
> 包名是 **`com.ebbinghaus.memo.debug`**（debug 变体带 `.debug` 后缀），排查时注意别过滤错包名。
>
> ⚠️ **更新（2026-09-16）**：debug 与 release 已统一包名，**debug 不再带 `.debug` 后缀**，当前两个变体包名均为 `com.ebbinghaus.memo`。排查请按新包名过滤；详见 `design/PACKAGE_UNIFY.md`。本文件其余含 `.debug` 的 logcat 片段为改动前的历史记录，保持原样。

### 我已经为「取日志」做的准备
- 本次新增了统一诊断 tag **`EbbinghausLaunch`**。若崩溃发生在启动检查链路，日志里会先出现：
  - `E EbbinghausLaunch: 启动检查失败，已降级继续启动（看板提醒可能缺失）` + 堆栈 → 说明命中的是 A1；
  - `E EbbinghausLaunch: 启动快照检查异常，已收敛为 IO_ERROR` + 堆栈 → 说明命中的是 A2；
  - `W EbbinghausLaunch: 标签列表读取失败...` → 说明命中的是 A4。
- 拿到日志后，把 `FATAL EXCEPTION` 那一整段（含 `Caused by:` 链）发回即可。

### 同时请补充这 3 个信息（能极大加速定位）
1. **是否曾开启过「自动快照」并选过目录？**（决定 A2 链路是否被激活）
2. **崩溃前是否安装过旧版本 / 是否有旧数据？**（决定 Room 迁移路径是否被走到）
3. **同一台设备上，release 包（`app-release.apk`）是否也崩？**（若 release 不崩，则范围可收敛到 debug-only 差异：`applicationIdSuffix` / `ui-tooling` / `ui-test-manifest` / `enableUnitTestCoverage`）
   > ⚠️ **更新（2026-09-16）**：`applicationIdSuffix` 已删除（debug/release 包名统一为 `com.ebbinghaus.memo`），**不再是 debug-only 差异**；且两变体现互为覆盖安装，同一台设备无法并存对比。其余差异（`ui-tooling` / `ui-test-manifest` / `enableUnitTestCoverage`）仍成立。详见 `design/PACKAGE_UNIFY.md`。

---

## 8. 环境改动披露（诚实声明，超出项目目录）

为尝试复现，本次在项目目录之外做了以下改动，**请知悉**：

| 位置 | 改动 | 说明 |
|---|---|---|
| `D:\Agent\Android\Sdk\system-images\android-36\aosp_atd\arm64-v8a` | **新增**（下载，约 **1.7 GB**） | 为规避缺失的 hypervisor 而尝试 arm64 镜像；最终因「x86_64 host 不支持 arm64 guest」而失败。**可安全删除**（`sdkmanager --uninstall "system-images;android-36;aosp_atd;arm64-v8a"`） |
| `C:\Users\AMX\.android\avd\atd36_arm.avd` + `atd36_arm.ini` | 新增后又**已删除** | 上述尝试用的手写 AVD，已确认不可用，**本次已清理**（`ls ~/.android/avd` 现仅剩原有 `Android16_API36`） |
| `D:\Projects\androidapk` 之外无其他改动 | — | 未修改 SDK 其他组件、未安装驱动、未改环境变量 |

**项目目录内**仅改动 3 个 `.kt` 文件 + 新增本报告 `design/CRASH_INVESTIGATION.md`。

---

## 9. 附：本次审计用到的关键命令（可复现）

```bash
# 测试 / 构建 / 静态检查
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11
./gradlew test --console=plain --rerun-tasks
./gradlew assembleDebug --console=plain
./gradlew :app:lintDebug --console=plain

# debug APK 是否含 baseline profile（结论：不含）
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -iE 'baseline|dexopt|prof'
# release APK 是否含（结论：含 assets/dexopt/baseline.prof 4636 B）
unzip -l app/build/outputs/apk/release/app-release.apk | grep -iE 'baseline|dexopt|prof'

# 反编译核验 WindowMetricsCalculator（结论：恒走 Compat，不碰 window.extensions）
#   androidx.window:window:1.0.0 → WindowMetricsCalculator$Companion.getOrCreate()
#   → WindowMetricsCalculatorCompat.INSTANCE → ActivityCompatHelperApi30.currentWindowBounds()

# 模拟器可行性核验（结论：不可运行）
"$ANDROID_HOME/emulator/emulator-check.exe" accel      # → accel: 6（无 hypervisor 驱动）
```

---

## 第 2 轮：收敛启动期协程 + 内置崩溃自诊断

> 工程师：寇豆码 ｜ 日期：2026-09-16（第 2 轮）
> 触发：QA 独立回归（`design/QA_CRASH_REPORT.md`）**实证**「第 1 轮只加固了 `checkAppLaunchPrompt()`，漏了同文件的 `init`」，并确认另 2 处启动期 `init` 无边界；同时用户明确「不方便抓 logcat」。
> 本轮目标：① 把启动路径上**全部**无边界协程收敛干净；② 内置崩溃自诊断，让用户零门槛把根因交回。

### 0. 结论速览（第 2 轮）

| 项 | 结论 |
|---|---|
| 启动期无边界协程是否收敛干净 | **是**。`DashboardViewModel.init`（QA 新发现）/ `ReviewViewModel` / `SettingsViewModel` / `DataSafetyViewModel.OnSnapshotNow` 全部按同一模式补齐；另修 `SnapshotManager.snapshotNow()` 的 try 外读取 |
| 「静默失效」是否解决 | **部分解决（有取舍，如实说明）**：`ReviewViewModel`（核心功能）→ **可见可重试错误态 + Snackbar**；`SettingsViewModel` → **可见行内提示**；`DataSafetyViewModel` → **可见 Snackbar**；`DashboardViewModel.init` → **仅日志**（理由见 §3） |
| 内置崩溃自诊断 | **已实现**：全局 `UncaughtExceptionHandler` → 写 `filesDir/crash/`（零权限、保留最近 3 次）；设置页「关于 → 崩溃日志」可查看/复制/分享/清空 |
| 新增依赖 / 新增权限 | **0 / 0** |
| `compileSdk`/`targetSdk` | **35 / 35**（未变） |
| Room schema | **未变更** |
| `./gradlew test` | **293 passed / 0 failed**（app 246 + core 47；口径见 §4.1） |
| `./gradlew :app:lintDebug` | **0 errors / 9 warnings**（全部为既有 `ModifierParameter`，未新增） |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |

### 1. 第一部分：逐处修复对照表

| # | 位置 | 第 1 轮状态 | 本轮修法 | 是否补可见反馈 |
|---|---|---|---|---|
| 1 | `ui/dashboard/DashboardViewModel.kt` `init`（原 :94-124） | 🔴 **无任何边界**（QA 新发现；与已加固的 `checkAppLaunchPrompt` 同文件 / 同类 / 同依赖） | `viewModelScope.launch` 体包裹 `try`；`catch (CancellationException) { throw }` 在**前**，`catch (Exception) { Log.e(LOG_TAG, …) }` 在**后** | ❌ 仅日志（**取舍见 §3**） |
| 2 | `ui/review/ReviewViewModel.kt` `loadReviewBatch()`（`init` 于 :82-84） | 🟠 无边界（第 1 轮已列遗留） | 同上模式；新增 `ReviewUiState.loadError`；失败置错误态 + `_effect.tryEmit` | ✅ **可见**：`ReviewScreen` 新增**可重试 `ErrorState`** 分支（**优先于**「完成/暂停」分支，避免把失败渲染成「今日复习全部达成」） |
| 3 | `ui/settings/SettingsViewModel.kt` `init`（:41-53） | 🟠 无边界（第 1 轮已列遗留） | 同上模式；新增 `SettingsUiState.loadError`；失败置 `isLoading=false` + 错误文案 | ✅ **可见**：`DailyLimitCard` 顶部新增错误行内提示（避免默认值被误认为真实设置） |
| 4 | `ui/settings/DataSafetyViewModel.kt` `OnSnapshotNow`（:141-158） | 🟠 直接调 `snapshotNow()`，无外层边界 | `viewModelScope.launch` 体包裹同一模式；异常降级为 Snackbar + 复位 `isBusy` | ✅ **可见**：复用既有 Snackbar 通道（「快照失败，请重试」） |
| 5 | `data/snapshot/SnapshotManager.kt` `snapshotNow()`（:97-130） | 🟠 `snapshotDirUri` 在 `try` **之外**读取 | 把该读取**移入 `try`**；补 `catch (CancellationException) { throw }` | ✅（经 #4 通道可见） |

**统一模式**（与第 1 轮一致）：`try { … } catch (c: CancellationException) { throw c } catch (e: Exception) { Log.e("EbbinghausLaunch", …) }`。
- **catch 顺序**：取消分支**必须在前**（QA 以 `Log.eCount == 0` 验证取消信号未被通用分支吞掉）。
- **只 catch `Exception`**：不吞 `Error`（OOM / StackOverflow 等致命错误仍应终止进程）。
- **统一 tag**：`EbbinghausLaunch`。

**关于「AppNavigation 首帧实际创建 6 个 ViewModel」**：已核实 —— `MemoList`(:99) / `Review`(:108) / `Dashboard`(:121) / `Settings`(:135) / `DataSafety`(:144) / `Trash`(:160)，共 **6 个**。其中 `MemoListViewModel`（`observeMemos` / `observeAllTags`，`.catch` 位于 `collect` 上游）与 `TrashViewModel`（`observeTrash`，`.catch` 于 :132）**第 1 轮已完备**；`DataSafetyViewModel` 的 `init` **无协程**（仅构造 `MutableStateFlow`），不构成启动期协程缺口。本轮需补的即上表 1~5。

### 2. 第二部分：内置崩溃自诊断实现说明

**新增文件**
- `app/src/main/java/com/ebbinghaus/memo/crash/CrashLogger.kt`（记录器，可注入、纯 JVM 可测）
- `app/src/main/java/com/ebbinghaus/memo/crash/CrashMetadata.kt`（设备 / 版本元信息）
- `app/src/main/java/com/ebbinghaus/memo/ui/settings/CrashLogSection.kt`（设置页入口 UI）

**接线**
- `EbbinghausApp`：新增 `crashLogger`（`by lazy`，`crashDirProvider = { File(filesDir, "crash") }`），`onCreate()` 调用 `crashLogger.install()`。
- `MainActivity` → `AppNavigation` → `SettingsScreen` 逐层传入 `crashLogger`。
- `SettingsScreen` 的「关于」分组新增 `crashLogSection` 插槽。

**文件路径与保留策略**
- 目录：`filesDir/crash/`（应用私有目录，**零权限**）。
- 每次崩溃写两份：归档 `crash-<19 位零填充毫秒时间戳>.txt` + 固定副本 `last_crash.txt`（始终为最近一次）。
- 保留策略：按文件名（内嵌时间戳，字典序即时间序）降序，**仅保留最近 3 次**（`DEFAULT_MAX_RECORDS = 3`），更旧的删除。

**报告内容**：时间戳、线程名 / ID、设备 / 版本元信息（App 版本 + versionCode、Android 版本 + API、机型、ABI）、异常 `toString()`、**完整堆栈（含 `Caused by` 链）**。堆栈用 `StringWriter` / `PrintWriter` 生成（纯 JVM，无需 Android 运行时）。

**如何保证「不吞崩溃」**
1. `install()` 捕获安装时刻的**原 handler**，落盘后**必**调用 `previous.uncaughtException(thread, throwable)` → 系统默认流程（记录 `FATAL EXCEPTION` + 杀进程）照常执行；
2. 仅当原 handler 缺失（生产环境几乎不可能）才走 `terminateProcess()` 手动兜底（`killProcess` + `exitProcess(10)`）；
3. 落盘 `writeCrash()` **自身绝不抛**：整个函数体包 `try/catch`，任何失败仅返回 `null`（不二次崩溃）；
4. handler 内 `writeCrash` 外层再套 `runCatching {}`，双保险。

**如何「零权限分享」**
- 分享：`ShareCompat.IntentBuilder(context).setType("text/plain").setSubject(...).setText(报告全文).setChooserTitle(...).startChooser()` —— 正文即 `EXTRA_TEXT` 纯文本，**不需要 FileProvider、不需要任何权限**。
- 复制：`ClipboardManager.setPrimaryClip(ClipData.newPlainText(...))` —— 同样零权限。
- 因未使用 FileProvider，**无需 `<provider>` 声明**，`AndroidManifest` 零改动。

**用户侧如何使用（拿到根因的操作步骤）**
1. 崩溃后重新打开 App → 底部进入「**设置**」；
2. 「关于」分组会出现**「崩溃日志」**入口（**仅当存在崩溃记录时显示**），副文案显示「检测到 N 次崩溃记录」；
3. 点「**查看**」→ 弹窗展示最近一次崩溃的完整堆栈（等宽字体、可滚动）；
4. 点「**复制**」→ 一键复制全文到剪贴板；或点「**分享**」→ 用系统分享（微信 / 邮件等）把全文发回；
5. （可选）点「**清空**」→ 确认后删除全部崩溃记录。

### 3. 关于「静默失效」的取舍（如实说明）

任务要求「对用户能感知的功能给出可见反馈」。逐处权衡如下：

| 位置 | 用户能否感知 | 处置 | 理由 |
|---|---|---|---|
| `ReviewViewModel` | **能**（复习是核心功能；失败若静默，空队列会被渲染成「今日复习全部达成」，属**严重误导**） | **可见错误态 + 重试**（+ 尽力 Snackbar） | 必须可见，否则误导性最强 |
| `SettingsViewModel` | **能**（滑块显示值可能非真实设置） | **可见行内提示** | 代价极小（一行文字），能防「默认值被误认为真实设置」 |
| `DataSafetyViewModel.OnSnapshotNow` | **能**（用户主动点击「立即快照」） | **可见 Snackbar** | 复用既有通道，零新增 UI |
| `DashboardViewModel.init` | **弱**（仅影响首页角标 / 待复习计数） | **仅日志**（`Log.e` + `EbbinghausLaunch`） | **有意取舍**：① 该计数为**非阻断的便利指示**，用户进入「复习」Tab 会立即以真实数据纠正；② 冷启动首帧前 Snackbar **无可靠订阅者**（`init` 协程在 `AppNavigation` 组合期即执行，早于 `MemoListScreen` 的 `LaunchedEffect`），强行提示会「时有时无」，反而不诚实；③ 改 `replay=1` 会引入重复提示回归风险。故如实选择「可诊断降级」，**并在此明示其局限**。 |

> 换言之：**用户可感知的功能失败 → 可见**；**纯便利指示失败 → 仅日志**（且已明示）。这是本轮「最小变更」与「不静默」的权衡结果，**不是遗漏**。

### 4. 真实测试与构建结果（第 2 轮实测）

#### 4.1 单元测试 `./gradlew test --console=plain --rerun-tasks`

| 源集 | classes | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `app:testDebugUnitTest` | 40 | **246** | 0 | 0 | 0 |
| `app:testReleaseUnitTest` | 40 | **246** | 0 | 0 | 0 |
| `core:test` | 5 | **47** | 0 | 0 | 0 |
| **去重合计** | — | **293** | **0** | **0** | **0** |

**口径说明（诚实）**：
- QA 基线 **275**（app 228 + core 47）。本轮 **293 = 275 + 18**（新增 18 条：`StartupCoroutineBoundaryTest` 9 条 + `CrashLoggerTest` 9 条）。
- ⚠️ **本轮修改了 1 条既有断言**（唯一一处）：`QaCrashHardeningVerificationTest` 中原 `dashboard_initCollector_dependencyThrows_escapesUncaught_provingMissingBoundary` 被**反转为** `dashboard_initCollector_dependencyThrows_isContainedByBoundary_notEscaping`。原因：该用例以「异常逃逸」**证明缺口存在**；本轮已按 QA 路由修复该缺口，若保留原断言等于要求「缺口继续存在」，与本轮任务直接冲突。反转后断言**更强**（同时覆盖「边界生效不逃逸」+「非静默有日志」）。**除此之外未删改任何既有断言。**

#### 4.2 构建 `./gradlew assembleDebug --console=plain`
**BUILD SUCCESSFUL**；产物 `app/build/outputs/apk/debug/app-debug.apk` = **18,012,344 B**（第 1 轮 17,794,740 B；增量来自崩溃记录器 + 设置页入口 UI + 各错误态文案）。

#### 4.3 Lint `./gradlew :app:lintDebug`
**0 errors / 9 warnings**，全部为既有 `ModifierParameter`（`DashboardBanner` / `EmptyState`×2 / `ErrorState` / `LoadingSkeleton` / `MemoListScreen`×4），**未新增任何警告**（`DailyLimitCard` 参数顺序已调整以规避新增 `ModifierParameter`）。

#### 4.4 硬约束核对

| 约束 | 结果 |
|---|---|
| `./gradlew test` 全绿 | ✅ 293 / 0 |
| 零新增依赖 | ✅ `app/build.gradle.kts`、`core/build.gradle.kts` 未改 |
| `AndroidManifest` 零新增权限 | ✅ 未改（未使用 FileProvider） |
| `compileSdk` / `targetSdk` = 35 | ✅ 未改 |
| 无 Room schema 变更 | ✅ 未改 |
| `lintDebug` 0 errors | ✅ 0 errors / 9 warnings |
| 不伪造测试 / 崩溃日志 | ✅ 全部为实测；本机无模拟器 / 真机，**未重复尝试启动** |

### 5. 本轮遗留（如实）

1. **根因仍未定位**：本轮仍属「防御性加固」，且**无法在本机复现**（无模拟器 / 真机）。真正的定位依赖用户用「关于 → 崩溃日志」把栈交回（§2 步骤）。
2. **`DashboardViewModel.init` 仅日志**：如 §3 所述为有意取舍；若 QA 认为角标失败也须可见，可再评估（需把 effect 通道改为可重放，成本与回归风险更高）。
3. **`MemoDetailViewModel.loadMemoDetails()`** 仍无边界：该类**不在启动期创建**（仅进入详情页时），与「一打开就崩」无关，本轮未改（沿用第 1 轮判断）。
4. **`MainActivity.onCreate` 主线程同步读全部 `by lazy` 仓储**：本轮**未改实现**，仅**如实修正了 KDoc**（`EbbinghausApp` / `MainActivity`）——它确实在首帧前、主线程构建 Room / SharedPreferences，「惰性化避免阻塞首帧」的描述与实现不符。若需真正消除首帧阻塞，应另立任务（异步预热或按需注入）。

### 6. 本轮改动文件清单

| 文件 | 类型 |
|---|---|
| `app/.../ui/dashboard/DashboardViewModel.kt` | 修改（补 `init` 边界） |
| `app/.../ui/review/ReviewViewModel.kt` | 修改（补边界 + `loadError` 错误态） |
| `app/.../ui/review/ReviewScreen.kt` | 修改（新增可重试错误态分支） |
| `app/.../ui/settings/SettingsViewModel.kt` | 修改（补边界 + `loadError`） |
| `app/.../ui/settings/SettingsScreen.kt` | 修改（错误行内提示 + 「关于」崩溃日志插槽） |
| `app/.../ui/settings/DataSafetyViewModel.kt` | 修改（补 `OnSnapshotNow` 边界） |
| `app/.../ui/settings/CrashLogSection.kt` | **新增**（崩溃日志入口 UI） |
| `app/.../data/snapshot/SnapshotManager.kt` | 修改（`snapshotNow` 读取移入 try + 取消重抛） |
| `app/.../crash/CrashLogger.kt` | **新增**（崩溃记录器） |
| `app/.../crash/CrashMetadata.kt` | **新增**（设备 / 版本元信息） |
| `app/.../EbbinghausApp.kt` | 修改（注册崩溃处理器 + 更正 KDoc） |
| `app/.../MainActivity.kt` | 修改（传入 crashLogger + 更正 KDoc） |
| `app/.../ui/navigation/AppNavigation.kt` | 修改（透传 crashLogger） |
| `app/src/test/.../ui/StartupCoroutineBoundaryTest.kt` | **新增**（9 条） |
| `app/src/test/.../crash/CrashLoggerTest.kt` | **新增**（9 条） |
| `app/src/test/.../qa/QaCrashHardeningVerificationTest.kt` | 修改（1 条诊断用例断言反转，见 §4.1） |
| `design/CRASH_INVESTIGATION.md` | 修改（追加本节） |

> **未改动**：`AndroidManifest.xml`、`app/build.gradle.kts`、`core/build.gradle.kts`、Room 实体 / DAO / schema、其余既有测试文件。

---

# 第 3 轮：根因定案与修复 —— `Screen` 类静态初始化循环（启动崩溃）

> 本轮**不再**是防御性加固。根因已定案，且已用纯 JVM 单测**先复现（红）→ 后修复（绿）**闭环验证。
> 记录人：寇豆码（Engineer）｜2026-09-16

## 0. 结论速览

| 项 | 结论 |
|---|---|
| 真实根因 | **JVM 类静态初始化循环**（JVM Spec §5.5）：`Screen` 的 `companion object` 在 `<clinit>` 期读取**自身的嵌套 `data object`** |
| 崩溃异常 | `ExceptionInInitializerError` ← `NullPointerException: ...Screen$MemoList.INSTANCE is null` |
| 为什么只在 debug 崩 | R8 会重排 / 内联这段静态初始化，把循环打破；未优化的 debug 包原样保留 |
| 是否真机复现 | **否**——但已用「全新类加载器 + 纯 JVM 单测」在**本机稳定复现**，栈与用户 logcat 逐字一致 |
| 修复方式 | 2 处崩溃点 + 2 处同型隐患改为 `by lazy` 惰性求值 |
| 回归测试 | 新增 10 条，实测**先红 4 条 → 后绿 10/10** |
| 历史结论更正 | `DELIVERY_SUMMARY.md` §五-2 把 `TopLevelDestination` 的 `NoClassDefFoundError` 归因于「data object 引用 Compose 图标」——**该归因是错的** |

## 1. 真实根因

### 1.1 用户 logcat 关键行

```
E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: Process: com.ebbinghaus.memo.debug, PID: 23253
E AndroidRuntime: java.lang.ExceptionInInitializerError
E AndroidRuntime:   at com.ebbinghaus.memo.ui.navigation.AppNavigationKt.AppNavigation(AppNavigation.kt:215)
E AndroidRuntime:   at com.ebbinghaus.memo.ui.navigation.AppNavigationKt.AppNavigation(AppNavigation.kt:171)
E AndroidRuntime:   at com.ebbinghaus.memo.MainActivity$onCreate$1$1.invoke(MainActivity.kt:38)
E AndroidRuntime: Caused by: java.lang.NullPointerException:
E AndroidRuntime:   Attempt to invoke virtual method 'java.lang.String com.ebbinghaus.memo.ui.navigation.Screen$MemoList.getRout...'
E AndroidRuntime:   at com.ebbinghaus.memo.ui.navigation.Screen.<clinit>(Screen.kt:29)
```

`Screen.kt:29` 即 `companion object` 中的 `MemoList.route`。

### 1.2 触发机制（逐步推演）

问题代码（`app/.../ui/navigation/Screen.kt`）：

```kotlin
sealed class Screen(val route: String) {
    data object MemoList : Screen("memo_list")   // 嵌套在 Screen 内，且【继承 Screen】
    // ...
    companion object {
        private val topLevelRoutes = setOf(MemoList.route, ...)   // ← Screen.kt:29
    }
}
```

`AppNavigation.kt:215` 的 `BackHandler(enabled = currentRoute == Screen.MemoList.route ...)` 是首个触碰点：

1. 读 `Screen$MemoList.INSTANCE` → JVM 开始初始化 `Screen$MemoList`；
   按 JVM Spec §5.5，**先**把它标记为「正在被**当前线程**初始化」，**然后**才去初始化其父类 `Screen`；
2. `Screen.<clinit>` 创建 `Companion` 实例 → `Companion.<clinit>` 读 `Screen$MemoList.INSTANCE.route`；
3. JVM 发现 `Screen$MemoList`「正被当前线程初始化」→ 判定为**递归初始化请求** → **直接放行、不等待**；
4. 此刻 `Screen$MemoList.INSTANCE` **仍为 null**（其 `<clinit>` 体还卡在等父类）→ `null.getRoute()` → **NPE**；
5. NPE 发生在 `<clinit>` 中 → JVM 包成 `ExceptionInInitializerError` 抛出，进程启动即崩。

### 1.3 为什么 release 完全正常

release 开启了 R8（`isMinifyEnabled = true`）。R8 会重排 / 内联这段静态初始化，
把「父类 `<clinit>` 读子类 `INSTANCE`」的循环打破。
这与用户反馈「release 完全正常、debug 必崩」**完全吻合**，也是本 bug 长期潜伏的原因。

## 2. 历史结论更正：`TopLevelDestination` 的 `NoClassDefFoundError`

`design/DELIVERY_SUMMARY.md` §五-2 原文：

> `NoClassDefFoundError: Could not initialize class TopLevelDestination` ——
> `TopLevelDestination` 的 `data object` 在类静态初始化阶段即引用 `Icons.AutoMirrored.Filled.MenuBook`
> 等 Compose 图标，Robolectric 下该类初始化失败，导致所有引用它的导航测试连带失败。

**该归因是错的。** 真实原因是**同一个静态初始化循环**（`TopLevelDestination.kt:50`）：

```kotlin
sealed class TopLevelDestination(route, label, icon) {
    data object Library  : TopLevelDestination(route = Screen.MemoList.route, ...)
    // ...
    companion object {
        val entries: List<TopLevelDestination> = listOf(Library, Review, Settings)  // ← 同样的循环
    }
}
```

`TopLevelDestination` 的嵌套 `data object` 同样**继承自它自己**。首个触碰点若是 `Library` / `Review` /
`Settings` 中的任意一个，`Companion.<clinit>` 读到的就是**尚未赋值的 `INSTANCE`（null）**。

本次已用测试**实测**到该污染的两种形态（见 §4 红条证据）：

- **形态 A**：经由 `Screen.MemoList.route` 触发 `Screen` 的循环 → `ExceptionInInitializerError`；
- **形态 B**（更隐蔽）：`entries` 静默变成 **[`null`, `Review`, `Settings`]** ——
  不抛异常，但 `fromRoute` 里 `it.route` 会 NPE，导航条少一个 Tab。
  这才是当年「导航测试连带失败」的直接现象，与 Compose 图标**毫无关系**。

> 结论：**Compose 图标不是原因**。证据：把 `entries` 改为惰性求值后，
> 图标依旧在类初始化期被引用，**测试全部转绿**；反之仅惰性化图标则不解决 `Screen` 的循环。

## 3. 修复方案

| # | 文件 | 位置 | 处理 |
|---|---|---|---|
| 1 | `app/.../ui/navigation/Screen.kt` | `companion object.topLevelRoutes` | 改 `by lazy`（崩溃点） |
| 2 | `app/.../ui/navigation/TopLevelDestination.kt` | `companion object.entries` | 改 `by lazy`（同型） |
| 3 | `app/.../data/repository/MemoSortOption.kt` | `companion object.DEFAULT` | 改 `by lazy`（同型隐患） |
| 4 | `app/.../data/repository/TrashSortOption.kt` | `companion object.DEFAULT` | 改 `by lazy`（同型隐患） |

```kotlin
// Screen.kt
private val topLevelRoutes: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    setOf(MemoList.route, Review.route, Settings.route)
}

// TopLevelDestination.kt
val entries: List<TopLevelDestination> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    listOf(Library, Review, Settings)
}
```

**为什么不用字符串字面量**：会与 `Screen` 常量重复定义，埋下不一致隐患。
**为什么用 `PUBLICATION` 而非默认 `SYNCHRONIZED`**：`isTopLevel` 在重组期被高频读取，
PUBLICATION 以 volatile + CAS 发布，命中后只是一次 volatile 读、首次访问也无锁竞争；
选中它而非 `NONE`，是因为**不能假设「只在主线程访问」**——`NONE` 一旦被后台线程率先触发
就有跨线程可见性风险。即使并发导致 lambda 被重复求值，`setOf(...)` / `listOf(...)` 是纯函数、
结果恒等，无副作用。

**语义保持不变**：`isTopLevel` / `isMemoDetail` / `entries` / `fromRoute` / `isBadgeTarget`
的签名与返回值语义均未改动，既有调用点零改动。

## 4. 回归测试：先红后绿（实测证据）

新增 `app/src/test/.../ui/navigation/NavigationRouteInitRegressionTest.kt`（10 条）。

**难点**：该崩溃只在「先触碰嵌套 object、后触碰外层类」的顺序下出现。
若先触碰 `Screen.isTopLevel(...)`（即先触发 `Companion`），JVM 会正常完成初始化、反而不崩，
因此**用例执行顺序会决定结果**。为此本套件用 `URLClassLoader(..., null)` 在**全新类加载器**
中做初始化，保证每次都是干净的静态状态，从而**与顺序无关地稳定复现**。

### 4.1 修复前（红）——`10 tests completed, 4 failed`

| 用例 | 失败原因 |
|---|---|
| `screen_nestedObjectTouchedFirst_initializesCleanly` | `AssertionError` ← `ExceptionInInitializerError` ← **`NullPointerException: Cannot invoke "Screen$MemoList.getRoute()" because "Screen$MemoList.INSTANCE" is null` at `Screen.<clinit>(Screen.kt:29)`** |
| `topLevelDestination_nestedObjectTouchedFirst_initializesCleanly` | `AssertionError` ← `ExceptionInInitializerError` at `TopLevelDestination.<clinit>(TopLevelDestination.kt:50)` ← `NPE: ... "Screen$Review.INSTANCE" is null` at `Screen.<clinit>(Screen.kt:30)` |
| `topLevelDestination_entries_preservesTabOrder` | `expected:<[Library, Review, Settings]> but was:<`**`[null, Review, Settings]`**`>` |
| `topLevelDestination_fromRoute_resolvesKnownRoutesOnly` | `NPE: Cannot invoke "TopLevelDestination.getRoute()" because "it" is null` at `TopLevelDestination$Companion.fromRoute(TopLevelDestination.kt:58)` |

> 第一行与用户 logcat 的 `Caused by` **逐字一致**；第三行直接印证了 §2 的「形态 B」静默污染。

### 4.2 修复后（绿）——`tests=10 failures=0 errors=0 skipped=0`

10 条用例全部通过，覆盖：`isTopLevel` 三态 / `isMemoDetail` / `entries` 顺序 /
`fromRoute` / `isBadgeTarget` / `createRoute(5L)` / 两个 enum `DEFAULT` / `ReviewStage.fromLevel`，
以及 2 条「全新类加载器优先触碰嵌套 object」的回归钉扎。

## 5. 全项目同类扫描结果

扫描范围：`app/src/{main,debug,test}`、`core/src/{main,test}`、`baselineprofile/src`。
判定标准：**外层类/枚举的 `companion object` 是否在类初始化期（`<clinit>`）引用本类的
嵌套 object / 枚举常量**。

| # | 位置 | 判定 | 处理 |
|---|---|---|---|
| 1 | `Screen.topLevelRoutes` | **危险**（已崩溃） | 已修 `by lazy` |
| 2 | `TopLevelDestination.entries` | **危险**（静默污染） | 已修 `by lazy` |
| 3 | `MemoSortOption.DEFAULT` | **同型隐患**：enum 的 companion 在 `<clinit>` 读自身常量 `UPDATED_DESC`。当前侥幸不崩，仅因 Kotlin 恰好把枚举常量赋值排在 `<clinit>` 之前——属**依赖代码生成顺序的隐式契约** | 已修 `by lazy` |
| 4 | `TrashSortOption.DEFAULT` | 同 #3（`DELETED_DESC`） | 已修 `by lazy` |
| 5 | `ReviewStage.fromLevel` 中的 `STAGE_1` | **安全**：引用位于**函数体内**，惰性求值，不参与 `<clinit>` | 不改，已加测试钉扎 |
| 6 | 15 处 `sealed interface` + `data object`（`MemoListUiEvent` / `ReviewUiEvent` / `TrashUiEvent` / `ImportResult` / `SnapshotResult` 等） | **安全**：均无 `companion object`；且 JVM 初始化实现类**不会**触发父接口初始化（除非接口声明 default 方法） | 不改 |
| 7 | `WindowWidthSizeClass` / `WindowHeightSizeClass` / `ReviewRating` / `SnapshotFailureReason` | **安全**：无 `companion object` | 不改 |
| 8 | `SnapshotManager.Companion` | **安全**：仅 `const val` 与 `DateTimeFormatter`，无自身常量引用 | 不改 |
| 9 | `CrashLogger` / `AppDatabase` / `Converters` / `PreferenceStore` / `EbbinghausApp` 的 companion | **安全**：均为 `const val` 或纯静态工具，无自引用 | 不改 |

## 6. 本轮实测数据

| 项 | 结果 |
|---|---|
| `./gradlew test --rerun-tasks` | **BUILD SUCCESSFUL**，`app` 269 + `core` 47 = **316 条/变体**（原 306，**+10**）；含 debug/release 两变体共 **585 次执行，0 failures / 0 errors / 0 skipped** |
| `./gradlew :app:lintDebug` | **0 errors**（23 warnings，均为既有：`GradleDependency` ×14、`ModifierParameter` ×9 于 `MemoListScreen.kt`，**无一来自本轮改动文件**） |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| 零新增依赖 | ✅ `app/build.gradle.kts`、`core/build.gradle.kts` 未改 |
| `AndroidManifest` 零新增权限 | ✅ 未改 |
| `compileSdk` / `targetSdk` = 35 | ✅ 未改 |
| 无 Room schema 变更 | ✅ 未改 |
| 未启动模拟器 / 真机 | ✅ 本机无设备，全程用 JVM 回归测试证明 |

## 7. 本轮改动文件清单

| 文件 | 类型 |
|---|---|
| `app/.../ui/navigation/Screen.kt` | **修改**（崩溃点 `topLevelRoutes` 惰性化） |
| `app/.../ui/navigation/TopLevelDestination.kt` | **修改**（`entries` 惰性化） |
| `app/.../data/repository/MemoSortOption.kt` | **修改**（同型隐患 `DEFAULT` 惰性化） |
| `app/.../data/repository/TrashSortOption.kt` | **修改**（同型隐患 `DEFAULT` 惰性化） |
| `app/src/test/.../ui/navigation/NavigationRouteInitRegressionTest.kt` | **新增**（10 条回归） |
| `design/CRASH_INVESTIGATION.md` | 修改（追加本节） |

> **未改动**：`AndroidManifest.xml`、`app/build.gradle.kts`、`core/build.gradle.kts`、
> Room 实体 / DAO / schema、`AppNavigation.kt` 及全部既有测试断言。

## 8. 给后续同事的提醒

1. **不要在 `sealed class` 的 `companion object` 里直接引用它自己的嵌套 `data object`。**
   只要嵌套 object **继承自外层类**，就构成 JVM 静态初始化循环（JVM Spec §5.5）。
   需要聚合时一律 `by lazy`。
2. **该 bug 只有 debug 包会暴露**（R8 会掩盖），因此「release 正常」**不能**作为无此问题的证据。
3. 判据很简单：**只要有一条测试触碰过 `Screen` 或 `TopLevelDestination` 就会崩**。
   之前 306 条用例全绿，仅仅是因为**一条都没触碰过**——这就是盲区的代价。
