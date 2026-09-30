# 冷启动闪退「防御性加固」独立回归验证报告

> QA：严过关（Edward）｜ 日期：2026-09-16 ｜ 对象：`D:\Projects\androidapk`
> 立场：**不采信工程师自述，一切数字实测；找不到就如实说找不到**。
> 范围：仅新增测试文件；**未修改任何 `app/src/main` 业务源码，未改动构建脚本**。

---

## 1. 执行摘要

| 结论项 | 我的独立判定 |
|---|---|
| **本次是否有根因修复** | **没有**。只有 3 处**防御性加固**，根因**仍未定位**。**请勿对外表述为「问题已解决」** |
| 3 处加固是否真实生效 | **是**（逐条实读 + 对抗测试实证，见 §3） |
| 3 处加固是否引入回归 | **否**（既有 267 条断言零删改、全绿；新增 8 条亦全绿） |
| 3 处加固是否「静默失效」 | **分场景**：快照链路**用户可见**（Snackbar）；看板/标签链路**仅有日志、用户侧无感**（属「可诊断降级」，非严格静默，但用户确实无感）——见 §3 逐条 |
| 工程师审计结论是否夸大 | **未夸大**，3 条关键证伪（C8 / baseline.prof / grep）**我独立复核后全部成立**（§4） |
| 是否发现工程师**遗漏**的崩溃面 | **是，且这是本次最大价值**：新发现 **1 处「同文件、同类、同为启动期协程」的无边界缺口**（`DashboardViewModel.init`），工程师报告**完全未提及**；另确认 2 处工程师已列但未改的启动期缺口**确实会崩**（§5） |
| 本机能否复现 | **不能**。无模拟器/真机（x86_64 需硬件加速、arm64 guest 不被 x86_64 host 支持）——**如实声明，未重复尝试** |

**一句话**：3 处加固**做得对**，但**没有把「启动期无边界协程」这一类问题清干净**——工程师在 `DashboardViewModel` 里修了一条协程，**同一文件里另一条同样读 Room、同样在首帧前执行的协程仍然无边界**。这是本次最值得修的点。

---

## 2. A. 构建与测试（全部实测）

环境：`JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11`，Git Bash。

### 2.1 单元测试 —— `./gradlew test --console=plain --rerun-tasks`

```
BUILD SUCCESSFUL in 20s
57 actionable tasks: 57 executed
```

从 `build/test-results/**/*.xml` 逐套件解析（实测）：

| 源集 | classes | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `app:testDebugUnitTest` | 38 | **228** | 0 | 0 | 0 |
| `app:testReleaseUnitTest` | 38 | **228** | 0 | 0 | 0 |
| `core:test` | 5 | **47** | 0 | 0 | 0 |
| **去重合计（debug/release 同一批用例）** | — | **275** | **0** | **0** | **0** |

- 工程师自述 **267**；我实测 **275** = 267（基线）+ **8（我新增的对抗用例）**。**基线 267 完全吻合**，既有断言**零删改**。
- debug / release 两变体结果一致。

### 2.2 构建 —— `./gradlew assembleDebug --console=plain`

```
BUILD SUCCESSFUL
```
- 产物 `app/build/outputs/apk/debug/app-debug.apk` = **17,794,740 B**（与工程师自述**逐字节一致**）。

### 2.3 Lint —— `./gradlew :app:lintDebug --console=plain --rerun-tasks`

```
BUILD SUCCESSFUL in 58s
0 errors, 9 warnings
```
**9 条 warning 逐条核实**（`app/build/reports/lint-results-debug.txt`），**全部为 `ModifierParameter`（Compose 参数顺序风格）**，无一条与崩溃/运行期相关：

| # | 位置 | 规则 |
|---|---|---|
| 1 | `DashboardBanner.kt:56` | `ModifierParameter` |
| 2 | `EmptyState.kt:46` | `ModifierParameter` |
| 3 | `EmptyState.kt:103` | `ModifierParameter` |
| 4 | `ErrorState.kt:37` | `ModifierParameter` |
| 5 | `LoadingSkeleton.kt:128` | `ModifierParameter` |
| 6 | `MemoListScreen.kt:123` | `ModifierParameter` |
| 7 | `MemoListScreen.kt:162` | `ModifierParameter` |
| 8 | `MemoListScreen.kt:299` | `ModifierParameter` |
| 9 | `MemoListScreen.kt:658` | `ModifierParameter` |

**无** `MissingClass` / `NewApi` / `Instantiatable` / `WrongThread`。→ 工程师「9 条 warning 均无关紧要」**成立**。

> 结论：A 全部通过，工程师自述数字**仅「267 vs 275」需按上述口径理解**（差异是我新增用例），其余逐字节吻合。

---

## 3. B. 3 处加固独立复核（逐条）

### 3.1 `DashboardViewModel.checkAppLaunchPrompt()`（`DashboardViewModel.kt:158-258`）

| 复核项 | 独立结论 | 证据 |
|---|---|---|
| 异常边界是否覆盖 `purgeExpiredTrash` + `SnapshotManager` 两条链路 | **是** | `try {`（:160）覆盖 :168-170（回收站清理）、:173-182（SAF 快照）、:184/198/236/249（设置读写）、:202-247（看板判定） |
| **`CancellationException` 是否原样抛出** | **是（代码顺序正确）** | `catch (cancellation: CancellationException) { throw cancellation }`（:250-252）**在前**，`catch (e: Exception)`（:253）**在后** → Kotlin 首个匹配者胜出，取消信号不会被通用分支吞掉 |
| 「静默失效」风险 | **部分**：**有日志、用户侧无感** | 通用异常仅 `Log.e(LOG_TAG, ...)`（:255），**不弹 Snackbar/Toast** → 日志可诊断，但用户看不到任何提示（看板提醒缺失用户无感）。工程师称「不静默」→ **严格说属「可诊断的降级」，不是「用户可感知的提示」** |

**独立实证（新增对抗用例，见 §6）**：
- 注入 `IllegalStateException`（`updateLastActiveDate`）→ **不崩溃**，异常前已写入的状态保留；`Log.eCount == 1`、`tag == "EbbinghausLaunch"`。
- 注入 `CancellationException` → **`Log.eCount == 0`**（若 catch 顺序写反，取消信号会走通用分支并打日志 → 该值会变成 1）。**这是「取消信号未被吞掉」的强证据。**

**回归风险：无。** 既有 `DashboardViewModelTest` 7 条全绿且**仍断言到实质内容**（`isVisible` / `dueTodayCount` / `absentDays` / `isMultiDayAbsence`），未被弱化为「只要不抛异常即通过」。

---

### 3.2 `SnapshotManager.checkAndSnapshotOnLaunch()`（`SnapshotManager.kt:72-92`）

| 复核项 | 独立结论 | 证据 |
|---|---|---|
| `Failure(IO_ERROR)` 收敛是否真实 | **是** | `try` 表达式（:72）；`catch (cancellation: CancellationException) { throw }`（:85-87）；`catch (e: Exception) { Log.e(...); return Failure(IO_ERROR) }`（:88-92）。语义与加固前等价（未开启/未选目录/同日 → `Skipped`） |
| 其它公开方法是否仍有未保护路径 | **有 1 处** | `snapshotNow()` 自身有 try/catch（:101-129，覆盖 `exportJson`/URI 解析/`createDocument`/`openOutputStream`），`pruneTo()` 用 `runCatching`（:139）——**但 `snapshotNow()` 在进入 try 之前（:98）读取 `preferenceStore.snapshotDirUri.value`**，该读取若抛异常**不会被自身 catch 覆盖**。经 `checkAndSnapshotOnLaunch()` 调用时可被外层兜住；但 `DataSafetyViewModel.OnSnapshotNow`（`DataSafetyViewModel.kt:147-157`）**直接调用 `snapshotNow()` 且无外层边界** → 该路径存在未保护点（用户点击路径，非启动路径） |
| 是否静默失效 | **否（用户可见）** | `Failure(IO_ERROR)` 经 `DashboardViewModel` 转为 `disableSnapshot()` + Snackbar「自动快照失败：写入异常，已关闭自动快照」（`DashboardViewModel.kt:175-178, 260-266`）→ **用户可见** |

**JVM 单测：无法覆盖（如实声明）。** `SnapshotManager` 构造依赖 `DataPortRepository`（**final class**，需 `AppDatabase`）与 `android.content.ContentResolver`（**抽象类**，需实现数十个抽象方法），纯 JVM 环境均无法实例化；本机亦无模拟器/真机。→ 改以**代码级证据**复核（上表）。已尝试过的绕过路径（构造假 `AppDatabase`/传 null）均不可行。

---

### 3.3 `MemoListViewModel.observeAllTags()`（`MemoListViewModel.kt:247-264`）

| 复核项 | 独立结论 | 证据 |
|---|---|---|
| `Log.w` 是否真加上（而非空 catch 换写法） | **是** | `:250-252` `.catch { e -> Log.w(LOG_TAG, "标签列表读取失败，标签筛选条将为空（不阻断主列表）", e) }` |
| 行为是否改变 | **否** | 失败时 `allTags` 仍保持空、**不阻断主列表**（原语义） |
| 是否静默失效 | **否，但用户侧无感** | 有 `Log.w` 可诊断；标签条为空用户无提示（可接受：主列表不受影响） |

**独立实证**：注入 `getAllMemos()` 抛异常 → 不崩溃、`allTags` 为空、**主列表仍正常显示 1 条**、`Log.wCount == 1` 且 `tag == "EbbinghausLaunch"`。

**回归风险：无。** `MemoListViewModelTest` 全绿，`testCreateMemo_persistsAndUpdatesTags` 等仍断言 `allTags` 的**实质内容**（`listOf("Android","Kotlin")`）。

---

### 3.4 「吞掉异常导致既有测试变相通过」的风险 —— 重点排查结论

**未发现此风险。** 独立核查：
- 全仓 `app/src/test` **仅 `DashboardViewModelTest` 引用 `DashboardViewModel`**，**无任何测试引用 `SnapshotManager`**（`grep` 0 命中）；
- `DashboardViewModelTest` 7 条**全部为正常路径**（无一断言「依赖抛异常应向外抛」），故加固「吞掉异常」**不会**把任何失败测试变成通过；
- 既有断言未被删改（测试文件 mtime 未变，且 275 全绿）。

---

## 4. C. 工程师审计结论复核（挑刺）

### C8 —— `rememberWindowSizeClass()` 是否真的**不经过** `androidx.window.extensions`：**工程师结论成立（我独立反编译确认）**

我用 `javap -c` 实读了 `androidx.window:window:1.0.0` 的 AAR（依赖实测解析：`material3-window-size-class-android:1.3.1 → androidx.window:window:1.0.0`）：

```
WindowMetricsCalculator$Companion.getOrCreate():
    getstatic  decorator            // Function1
    getstatic  WindowMetricsCalculatorCompat.INSTANCE
    invokeinterface Function1.invoke   // → 返回 Compat.INSTANCE
    areturn

WindowMetricsCalculator$Companion$decorator$1.invoke(WindowMetricsCalculator):
    aload_1; ...; aload_1; areturn     // ← 恒等函数（原样返回入参）

WindowMetricsCalculatorCompat.computeCurrentWindowMetrics(Activity):
    SDK_INT >= 30 → ActivityCompatHelperApi30.INSTANCE.currentWindowBounds(activity)
                  → activity.windowManager.currentWindowMetrics.bounds
```
- `find cls -path '*extensions*'` → **0 命中**：`window-1.0.0.aar` 内**根本不存在 `androidx.window.extensions` 包**。
- `material3-window-size-class-android:1.3.1` 的 `calculateWindowSizeClass(Activity)` 反编译确认：`WindowMetricsCalculator.Companion.getOrCreate().computeCurrentWindowMetrics(activity)`。

→ **链路 = 官方 `calculateWindowSizeClass` → Compat（恒等 decorator）→ `WindowManager.getCurrentWindowMetrics().bounds`，完全不触碰 extensions，无抛点。** 工程师证伪**成立**。（注：合并清单里确有 `uses-library androidx.window.extensions/sidecar`（`required=false`），但代码路径从不加载它们，属库自带声明，非风险。）

### baseline.prof —— debug APK 是否不含：**工程师结论成立**

| APK | `unzip -l | grep -iE 'baseline|dexopt|\.prof'` 实测 |
|---|---|
| `app-debug.apk` | **仅** `META-INF/androidx.profileinstaller_profileinstaller.version` → **无 `assets/dexopt/baseline.prof`** |
| `app-release.apk` | `assets/dexopt/baseline.prof` **4636 B** + `assets/dexopt/baseline.profm` **588 B** |

→ debug 下 `ProfileInstaller` 直接 no-op，**成立**。
**小偏差**：工程师称 `app/src/main/baseline-prof.txt`「**8 行**」；实测为 **7 条 `HSPL` 规则**（文件共 19 行，含注释）。**不影响结论**，仅数字口径略有出入。

### grep 命中 —— **工程师结论成立**

| 模式 | main `.kt` 源集实测 |
|---|---|
| `!!` | **0 命中** |
| `lateinit` | **0 命中** |
| `runBlocking` | **0 命中** |
| 显式 `Dispatchers.Main` | **0 命中** |
| `requireNotNull` | 仅 `ExportModels.kt:119`（**导入**路径） |
| `error(` | 仅 `DataSafetyViewModel.kt:170,188`（**导出/导入**路径） |
| `first()` | `DashboardViewModel.kt:184,202`（**已加固**）+ `ReviewViewModel.kt:93,94`（**未加固**，见 §5） |
| `throw ` | 仅 4 处**取消信号重抛**（`DashboardViewModel:252` / `SnapshotManager:87` / `MemoListViewModel:612` / `TrashViewModel:340`）+ JS 资产 |
| `TODO`/`FIXME` | **0 命中**（grep 命中的 `SpaceXXXL` 是 `Dimens` 常量名，非 TODO） |

→ 工程师 E14/E15 **成立**。

---

## 5. D. 工程师**遗漏**的崩溃面（本次重点）

### 5.1 🔴【新发现·最强】`DashboardViewModel.init` 的实时同步协程**没有任何异常边界**，工程师报告**完全未提及**

- **位置**：`DashboardViewModel.kt:94-124`
  ```kotlin
  init {
      viewModelScope.launch {                       // ← 无 try / 无 .catch
          val today = todayProvider()
          combine(
              reviewRepository.getDueReviewTasks(today),   // 与 checkAppLaunchPrompt 同一依赖
              settingsRepository.getSettings()             // 与 checkAppLaunchPrompt 同一依赖
          ) { ... }.collect { ... }
      }
  }
  ```
- **为什么这是「工程师遗漏」而非「已知遗留」**：工程师报告 §3-A1/§5 只改了 `checkAppLaunchPrompt()`（:158-258），**报告全文未出现 `init` 块（:94-124）**；而它与已加固项**同文件、同类（`viewModelScope.launch`）、同一批依赖（`getSettings`/`getDueReviewTasks`）、同在首帧前执行**。若 Room 抛异常（正是工程师假设的「未预期 DB 异常」），**已加固的那条被兜住，这条仍会击穿进程 → 照样闪退**。
- **独立实证**：我新增对抗用例注入 `getSettings()` 抛异常 → 该协程的未捕获异常**逃逸**（以 coroutines-test 的 `UncaughtExceptionsBeforeTest` 上报为证，且 `Log.eCount == 0` 说明**没有任何日志边界**）。用例 `dashboard_initCollector_dependencyThrows_escapesUncaught_provingMissingBoundary` 全绿。
- **影响**：这是**与本次加固目标完全同类**的缺口，**加固覆盖率不足**。

### 5.2 🟠 `ReviewViewModel.init → loadReviewBatch()` 无异常边界（工程师已列遗留，**我确认其在启动路径上且确实会崩**）

- `ReviewViewModel.kt:82-84`（`init`）→ `:89-119`；`getSettings().first()`（:93）、`getDueReviewTasks().first()`（:94）**均无 catch**。
- **启动路径确认**：`AppNavigation.kt:108-119` 在**首帧组合时**创建 `ReviewViewModel` → `init` 立即执行。任一 Room 查询抛异常 → 未捕获 → 闪退。
- 工程师以「需新增错误态 UI，超出最小变更」为由未改 —— **理由成立，但风险真实存在**。

### 5.3 🟠 `SettingsViewModel.init` 无异常边界（工程师已列遗留，**我确认会崩**）

- `SettingsViewModel.kt:41-53`：`viewModelScope.launch { settingsRepository.getSettings().collect { ... } }` **无 catch**。
- **启动路径确认**：`AppNavigation.kt:135-142` 首帧创建 → 若 Room 抛异常 → 闪退（或 `isLoading` 永久 `true`，若未来加静默 catch）。

### 5.4 🟡 `MainActivity.onCreate` 在**主线程同步读取全部 `by lazy` 仓储**（被误描述为「惰性化」）

- `MainActivity.kt:25-30` 在 `setContent` **之前**同步读取 `memoRepository/reviewRepository/settingsRepository/preferenceStore/dataPortRepository/snapshotManager`。
- `EbbinghausApp.kt` 注释称「`by lazy` → `onCreate` 不再构建任何对象，首帧不被数据库初始化阻塞」，但**实际只是把初始化从 `Application.onCreate` 挪到了 `Activity.onCreate`，仍在主线程、仍在首帧前**：
  - `preferenceStore` 构造 → `SharedPreferencesPreferenceStore` → `getSharedPreferences` + 同步读（**主线程文件 I/O**）；
  - `database` → `AppDatabase.getInstance` → `Room.databaseBuilder(...).build()`（主线程构建 Room 实例）。
- 若其中任一步抛异常 → `onCreate` 未捕获 → 闪退。**这不是新缺陷，但「惰性化避免阻塞首帧」的描述与实现不符**（对排查有误导性）。

### 5.5 🟡 `DataSafetyViewModel.OnSnapshotNow` 直接调 `snapshotNow()`，无外层边界

- `DataSafetyViewModel.kt:147-157` → `snapshotManager.snapshotNow()`。而 `snapshotNow()` 在 try 之外读 `preferenceStore.snapshotDirUri.value`（`SnapshotManager.kt:98`）→ 该读取抛异常时**无人兜住** → 崩溃。（**用户点击路径**，非启动路径，但与加固 2 属同类缺口。）

### 5.6 我**排除**的疑似崩溃面（独立复核「不构成启动崩溃」）

| 疑似点 | 独立结论 | 证据 |
|---|---|---|
| `TopLevelDestination` 的 3 个 `data object` 静态初始化引用 Compose 图标（历史 Robolectric `NoClassDefFoundError`） | **真机不构成崩溃** | 图标类随 APK 打包（`assembleDebug` 成功；release 混淆保留引用类）。Robolectric 的问题是**测试环境缺 `material-icons-extended` 的 Android 资源类**，与真机无关 |
| `MathView` 首帧创建 WebView | **首帧不创建** | `MathView.kt:75-86`：`needsRichRendering(text)==false` 走原生 `Text` 快路径；且富文本路径有 `delay(120ms)`（:100-103）。**空 DB 冷启动列表无条目 → 首帧零 WebView** |
| `assets/katex/` 完整性 | **齐全且已打包** | 源码 `app/src/main/assets/katex/`：`math_renderer.html`(9204B)、`katex.min.js`(275414B)、`marked.min.js`(35479B)、`auto-render.min.js`、`katex.min.css`、`fonts/`(6 个 woff2)；`unzip -l app-debug.apk` 确认**全部在包内** |
| native 16KB 对齐（Android 15/16 经典启动崩溃源） | **安全** | 4 个 `libandroidx.graphics.path.so`：**ELF `p_align=0x4000`（16KB）**，且 zip 内**数据偏移 `mod 16384 == 0`**（本地头实测）；清单 `extractNativeLibs="false"` → 直映射安全 |
| `TrashViewModel.init` | **已完备** | `.catch`（`TrashViewModel.kt:132`）位于 `collect` 上游 |
| `MemoListViewModel.init` | **已完备** | `observeMemos` `.catch`（:225）、`observeAllTags` `.catch`（:250） |
| `TrashRetention` / `Dimens` 等 `object` 静态初始化 | **无风险** | 纯常量/纯函数，无 Android 依赖 |

### 5.7 附：`AppNavigation` 首帧实际创建 **6 个** ViewModel（任务描述写「5 个」）

`MemoList`(:99) / `Review`(:108) / `Dashboard`(:121) / `Settings`(:135) / `DataSafety`(:144) / `Trash`(:160) —— **6 个**。Trash 上提后同样在首帧构造，其 `init` 订阅也参与启动期协程。

> **D 汇总**：启动路径上**仍无异常边界**的协程共 **3 条**（`DashboardViewModel.init`、`ReviewViewModel.init`、`SettingsViewModel.init`）。其中 **`DashboardViewModel.init` 是工程师本次遗漏（同文件、同类、同依赖）**，另 2 条工程师已如实列为遗留。**加固尚未覆盖全部同类缺口。**

---

## 6. E. 对抗性测试（已真实写入项目并跑通）

### 6.1 新增文件（均在 `src/test`，**未改业务源码/构建脚本**）

| 文件 | 作用 |
|---|---|
| `app/src/test/java/com/ebbinghaus/memo/qa/QaCrashHardeningVerificationTest.kt` | 8 条对抗用例 |
| `app/src/test/java/android/util/Log.java` | **测试源集**的 `android.util.Log` 记录型替身 |

> **为什么需要 `Log` 替身**：实测探针确认，本仓库未开启 `testOptions.unitTests.returnDefaultValues`，`android.util.Log` 在纯 JVM 单测中**抛 `RuntimeException: Method e in android.util.Log not mocked`**（探针输出：`PROBE_RESULT: Log.e threw = java.lang.RuntimeException: Method e ... not mocked`）。若不替换，**加固的 catch 分支一进入就会因 `Log.e` 抛异常而无法验证**。该替身把 Log 变为**可记录**的 no-op（记录 `eCount/wCount/lastTag/lastThrowable`），从而把「是否写了日志（非静默）」「取消信号是否被吞」变成**可断言事实**。**该文件仅存在于测试源集，绝不进入 APK；未修改 `app/build.gradle.kts`。**

### 6.2 用例清单与结果（8/8 通过）

| # | 用例 | 断言要点 | 结果 |
|---|---|---|---|
| 1 | `dashboard_checkAppLaunch_tailDependencyThrows_doesNotCrashAndKeepsUpdatedState` | 依赖抛异常 → 不崩溃；异常前状态（`isVisible`/`dueTodayCount`/`lastPromptedDate`）保留 | ✅ |
| 2 | `dashboard_checkAppLaunch_genericFailure_isLogged_notSilent` | 通用异常 → `Log.eCount==1`、`tag=="EbbinghausLaunch"`（**非静默**） | ✅ |
| 3 | `dashboard_checkAppLaunch_cancellationException_isRethrown_notLogged` | 注入 `CancellationException` → **`Log.eCount==0`**（证明取消信号原样抛出、未被 `catch(Exception)` 吞） | ✅ |
| 4 | `dashboard_checkAppLaunch_normalPath_behaviourUnchanged` | 正常路径行为不变 + 同日去重短路 + `Log.eCount==0` | ✅ |
| 5 | **`dashboard_initCollector_dependencyThrows_escapesUncaught_provingMissingBoundary`** | **`init` 协程未捕获异常逃逸**（`UncaughtExceptionsBeforeTest` 上报）+ `Log.eCount==0` → **坐实 §5.1 缺口** | ✅ |
| 6 | `memoList_observeAllTags_dependencyThrows_doesNotCrashAndMainListStillWorks` | 标签流抛异常 → 不崩溃、`allTags` 空、**主列表仍显示 1 条**、`Log.wCount==1` | ✅ |
| 7 | `memoList_observeAllTags_happyPath_stillPopulatesTags` | 正常路径标签去重排序不变 | ✅ |
| 8 | `memoList_observeAllTagsFailure_doesNotAffectMainListErrorState` | 标签失败不污染主列表 `errorMessage` | ✅ |

**用例 3 的证伪设计说明**：正确实现下 `CancellationException` 走取消分支（不写日志）→ `eCount==0`；若 catch 顺序写反（先 `catch(Exception)`），取消信号会被降级日志吞掉 → `eCount==1`。因此 `eCount==0` 是「取消信号未被吞」的**可判定证据**，而非「没抛异常」的巧合（用例同时断言了确实走到抛出点：`isVisible==true` 且 `lastPromptedDate==today`）。

### 6.3 无法 JVM 覆盖的部分（如实声明）

- **`SnapshotManager.checkAndSnapshotOnLaunch()`**：构造依赖 `DataPortRepository`（final class，需 `AppDatabase`）+ `ContentResolver`（抽象类），纯 JVM 无法实例化 → **无法写 JVM 单测**，改以 §3.2 的**代码级证据**复核。
- **真机/模拟器行为**（Compose 首帧、WebView、SharedPreferences I/O、SAF）：**本机无模拟器/真机，无法复现**（未重复尝试启动模拟器）。

---

## 7. 智能路由判定

### 判定：**Engineer**（附 QA 已完成部分）

**依据**：

1. **3 处加固本身无 QA 缺陷** → **不需要**把任何失败用例「改测试自己修」（QA 路径不适用）。
2. **但源码存在真实缺口**：`DashboardViewModel.init`（`DashboardViewModel.kt:94-124`）**与已加固项同文件、同类、同依赖，却无异常边界**，且工程师报告**完全未提及** → 这是**实现侧需补的代码**，路由 **Engineer**：
   - **必做（P0）**：为 `DashboardViewModel.init` 的 `combine(...).collect{}` 补异常边界（**同 `checkAppLaunchPrompt` 的模式**：`CancellationException` 原样抛 + `catch(Exception)` 降级 + `Log.e`），否则「启动期 Room 异常导致闪退」**仍未闭环**。
   - **建议（P1，工程师已列遗留）**：`ReviewViewModel.init` / `SettingsViewModel.init` 按「带错误态的正确加固」补边界（需配 UI 错误态，避免静默）。
   - **可选（P2）**：`DataSafetyViewModel.OnSnapshotNow` 包边界；`MainActivity` 的「惰性化」描述与实现对齐（或真正移出主线程）。
3. **不是 NoOne**：全部收敛尚未达成（仍有 3 条启动期无边界协程）。
4. **无 QA 侧待修项**：8 条新用例全绿，无需回炉。

> 注：本 QA 轮次**未修改任何 `app/src/main` 业务源码**（硬约束遵守），缺口仅报告，不代改。

---

## 8. 给用户的建议：如何**最高效**拿到崩溃日志

> 目标：把「未定位」变成「已定位」。只需一份崩溃时的 logcat（哪怕 30 行）。

### 方案 A（**最推荐，无需电脑、无需 root**）：Android 自带「错误报告」
1. 设置 → 关于手机 → 连点 **7 次「版本号」** 打开开发者选项；
2. 设置 → 系统 → 开发者选项 → **错误报告 → 完整报告**；
3. 等 1~2 分钟生成 → 分享/保存 zip；
4. 在 `bugreport-*.txt` 中搜 **`FATAL EXCEPTION`** 与 **`Caused by:`**（**不要只搜 `EbbinghausLaunch`**）。

### 方案 B（有任意一台电脑）：`adb logcat`
```bash
adb logcat -b crash -d            # 只导崩溃缓冲区（最快）
# 或复现前先抓全量：
adb logcat | grep -E "AndroidRuntime|FATAL|Caused by|EbbinghausLaunch"
```
包名 = **`com.ebbinghaus.memo.debug`**（debug 变体带 `.debug` 后缀，别过滤错）。

> ⚠️ **更新（2026-09-16）**：debug 与 release 已统一包名，debug **不再带 `.debug` 后缀**，两个变体现均为 `com.ebbinghaus.memo`。详见 `design/PACKAGE_UNIFY.md`。

### ⚠️ 关键提醒（本次加固带来的「排查陷阱」）
- **本次新增 tag 仅 `EbbinghausLaunch`**，且**只在已加固的 3 处链路**会打印。
- **若崩溃发生在 `DashboardViewModel.init` / `ReviewViewModel` / `SettingsViewModel`（§5），日志里将完全看不到 `EbbinghausLaunch`**（因为这些协程**根本没有 catch/日志**）。
- 因此：**「日志里没有 `EbbinghausLaunch`」绝不能排除上述链路** —— 请务必抓 **`FATAL EXCEPTION` 完整栈 + `Caused by` 链**。

### 同时请补充这 3 条信息（能极大加速定位）
1. 是否曾**开启过「自动快照」并选过目录**？（决定 SAF 链路是否被激活）
2. 崩溃前是否**安装过旧版本 / 有旧数据**？（决定 Room v1→v2 迁移路径是否被走到）
3. 同一台设备上 **release 包是否也崩**？（若 release 不崩 → 收敛到 debug-only 差异：`applicationIdSuffix` / `ui-tooling` / `ui-test-instrument` / `enableUnitTestCoverage`）
   > ⚠️ **更新（2026-09-16）**：`applicationIdSuffix` 已删除（debug/release 包名统一为 `com.ebbinghaus.memo`），**不再是 debug-only 差异**；且两变体现互为覆盖安装，同一台设备无法并存对比。详见 `design/PACKAGE_UNIFY.md`。

---

## 9. 附：本次实测命令（可复现）

```bash
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11
./gradlew test --console=plain --rerun-tasks            # → 275 tests / 0 failed
./gradlew assembleDebug --console=plain                 # → BUILD SUCCESSFUL，APK 17,794,740 B
./gradlew :app:lintDebug --console=plain --rerun-tasks   # → 0 errors / 9 warnings

# 加固专项
./gradlew :app:testDebugUnitTest --tests 'com.ebbinghaus.memo.qa.QaCrashHardeningVerificationTest'

# 独立反编译核验（window 1.0.0 不走 extensions）
unzip -o window-1.0.0.aar classes.jar && unzip -o classes.jar -d cls
javap -p -c cls/androidx/window/layout/WindowMetricsCalculator\$Companion.class
javap -p -c cls/androidx/window/layout/WindowMetricsCalculatorCompat.class
find cls -path '*extensions*' -name '*.class'          # → 0 命中

# APK 内容 / baseline.prof / 16KB 对齐
unzip -l app/build/outputs/apk/debug/app-debug.apk   | grep -iE 'baseline|dexopt|\.prof'
unzip -l app/build/outputs/apk/release/app-release.apk | grep -iE 'baseline|dexopt|\.prof'
```

---

### 附：本次新增/改动的文件清单（**均在 `src/test`，未动业务源码**）

- 新增 `app/src/test/java/com/ebbinghaus/memo/qa/QaCrashHardeningVerificationTest.kt`（8 用例）
- 新增 `app/src/test/java/android/util/Log.java`（测试源集 Log 记录型替身）
- 未修改：`app/src/main/**`、`core/src/main/**`、`app/build.gradle.kts`、`gradle.properties`、任何既有测试文件

---
---

# 第 2 轮终审（独立回归 · 最终）

> QA：严过关（Edward）｜ 日期：2026-09-16 ｜ 对象：`D:\Projects\androidapk`
> 立场：**不采信工程师自述，一切数字实测**；本轮**重点审查「反转 1 条断言」是否正当**。
> 范围：仅新增/清理**测试文件**；**未修改任何 `app/src/main` 业务源码，未改动构建脚本**。

## 0. 最终判定（结论先行）

| 结论项 | 我的独立判定 |
|---|---|
| **本次是否有根因修复** | **没有**。本轮 = 「启动期协程收敛」+「内置崩溃自诊断」两类**防御性加固**；**冷启动闪退根因仍未定位**（本机无模拟器/真机，**无法复现**）。**请勿表述为「问题已解决」** |
| 7 处协程边界是否真实生效 | **是**（逐处实读 `文件:行号` + 对抗测试实证，见 §2） |
| `CancellationException` 顺序是否全部正确 | **是，7/7 全部「取消分支在前、通用分支在后」**（见 §2 表） |
| 第 1 轮发现的 `DashboardViewModel.init` 缺口是否已覆盖 | **是**（独立探针实证「不再逃逸」，见 §2.2） |
| 「反转 1 条断言」是否正当 | **接受，正当**（**非**掩盖问题、**非**削弱；理由见 §3） |
| 既有用例是否被削弱 | **否**：`assertTrue(true)`/`@Ignore`/`@Disabled`/`assumeTrue` = **0 命中**；用例数守恒（见 §3.2） |
| 崩溃自诊断是否扎实 | **是**（不吞崩溃 / 写失败不二次崩 / 零权限 / 保留策略正确；对抗测试 13/13 通过，见 §4） |
| 启动路径是否仍有未覆盖协程 | **否**（启动期无边界协程 = **0**；仅剩 1 处**非启动期**遗留，见 §5） |
| **智能路由判定** | **`NoOne`**（全部通过；无阻断性源码缺陷；反转正当）。附 P2 建议见 §6 |

**一句话**：第 1 轮我发现的 `DashboardViewModel.init` 缺口**已被正确补齐**，7 处边界**顺序全对**；工程师**自曝的反转 1 条断言经我逐行审查——正当且更强**。崩溃自诊断实现**扎实**。但**根因依旧未定位**，本轮仍属加固。

---

## 1. A. 构建与测试（全部实测）

环境：`JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11`，Git Bash。

### 1.1 单元测试 —— `./gradlew test --console=plain --rerun-tasks`

```
BUILD SUCCESSFUL in 19s
57 actionable tasks: 57 executed
```

从 `build/test-results/**/*.xml` 逐套件解析（实测）：

| 源集 | classes | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `app:testDebugUnitTest` | 41 | **259** | 0 | 0 | 0 |
| `app:testReleaseUnitTest` | 41 | **259** | 0 | 0 | 0 |
| `core:test` | 5 | **47** | 0 | 0 | 0 |
| **去重合计（debug/release 同一批用例）** | — | **306** | **0** | **0** | **0** |

**口径核对（关键）**：
- 工程师自述 **293 = app 246 + core 47** → **完全吻合**：`259（我的实测 app）− 13（我本轮新增用例） = 246`；`246 + 47 = 293`。✅
- 与第 1 轮（275 = app 228 + core 47）之差 = **+18**，**全部由 2 个新增文件解释**：`CrashLoggerTest`（9）+ `StartupCoroutineBoundaryTest`（9）。→ **既有用例数零变化**。
- 我本轮新增 `QaRound2FinalVerificationTest`（13 条）→ 终值 **306 / 0 failed**。

### 1.2 构建 —— `./gradlew assembleDebug --console=plain`

```
BUILD SUCCESSFUL
```
- `app/build/outputs/apk/debug/app-debug.apk` = **18,012,344 B**（**与工程师自述逐字节一致**；第 1 轮 17,794,740 B → 增量 +217,604 B，来自崩溃记录器 + 设置页入口 UI + 各错误态文案，量级合理）。

### 1.3 Lint —— `./gradlew :app:lintDebug --console=plain --rerun-tasks`

```
BUILD SUCCESSFUL in 56s
0 errors, 9 warnings
```
- **9 条 warning 逐条核实**：`DashboardBanner:56` / `EmptyState:46,103` / `ErrorState:37` / `LoadingSkeleton:128` / `MemoListScreen:123,162,299,658` —— **全部 `ModifierParameter`**，与第 1 轮**同一批、同一数量**。
- 新增的 `CrashLogSection.kt`（`CrashLogSectionHost(crashLogger, modifier)`）**未触发**新增 `ModifierParameter`（`modifier` 已是首个可选参数）。→ **warning 零新增**，工程师「9 条均无关紧要」**成立**。

---

## 2. B. 7 处协程边界逐条复核（含 `CancellationException` 顺序证据）

> 复核方法：`grep -n "catch (" app/src/main/java` 全量列出 → 逐处实读源码。**判定标准**：`catch(CancellationException)` 必须**物理行号在前**于 `catch(Exception)`（Kotlin 首个匹配者胜出；写反 = 吞掉取消信号）。

| # | 文件:行号（`CancellationException`） | 文件:行号（`Exception`） | 顺序 | `Log` tag | 失败处置 | 可用 UI 状态是否保留 |
|---|---|---|---|---|---|---|
| 1 | `DashboardViewModel.kt:131` | `:134` | ✅ 取消在前 | `EbbinghausLaunch` | `Log.e`（**仅日志**，见 §2.3） | ✅ 角标/待复习数降级为旧值，不整屏空白 |
| 2 | `DashboardViewModel.kt:265` | `:268` | ✅ 取消在前 | `EbbinghausLaunch` | `Log.e` | ✅ 看板可能不弹，界面照常 |
| 3 | `ReviewViewModel.kt:142` | `:145` | ✅ 取消在前 | `EbbinghausLaunch` | `loadError` 可见错误态 + `tryEmit` Snackbar | ✅ **新增可重试 ErrorState 分支**（`ReviewScreen.kt:168`，优先于「完成/暂停」） |
| 4 | `SettingsViewModel.kt:72` | `:75` | ✅ 取消在前 | `EbbinghausLaunch` | `loadError` + `isLoading=false` | ✅ 滑块可用，`DailyLimitCard` 顶部行内提示（`SettingsScreen.kt:210`） |
| 5 | `DataSafetyViewModel.kt:167` | `:170` | ✅ 取消在前 | `EbbinghausLaunch` | Snackbar「快照失败，请重试」+ 复位 `isBusy` | ✅ 用户可见 |
| 6 | `SnapshotManager.kt:85` | `:88` | ✅ 取消在前 | `EbbinghausLaunch` | 收敛为 `Failure(IO_ERROR)` | ✅ 经 #2 转 Snackbar |
| 7 | `SnapshotManager.kt:129` | `:132`（`SecurityException`）→ `:134`（`Exception`） | ✅ 取消在前 | （`SecurityException`/`IO_ERROR` 分支，无 `Log`） | 分型返回 `Failure` | ✅ 用户可见（经 #5 通道） |

- **`LOG_TAG` 统一性**：6 个文件均定义 `private const val LOG_TAG = "EbbinghausLaunch"`（`SnapshotManager:16` / `DashboardViewModel:28` / `MemoListViewModel:26` / `ReviewViewModel:25` / `DataSafetyViewModel:29` / `SettingsViewModel:15`）。**统一 ✅**。
- **#7 补修确认**：`snapshotNow()` 的 `preferenceStore.snapshotDirUri.value` 读取已从 `try` 外**移入 `try` 内**（现 `:102-104` 位于 `:102` 起的 `try` 块内）→ 第 1 轮指出的「try 外读取无人兜住」**已闭环**；且 `:129` 取消信号重抛在前。

### 2.2 重点：`DashboardViewModel.init` 缺口（第 1 轮我发现的）是否真的被覆盖

- **源码实读**：`DashboardViewModel.kt:103-138`，`viewModelScope.launch { try { … combine(...).collect{} … } catch (cancellation: CancellationException) { throw } catch (e: Exception) { Log.e(...) } }` → **边界真实存在，顺序正确**。
- **独立实证（我复用第 1 轮注入手法，但换用更可靠的探针）**：
  - `dashboardInit_dependencyThrows_isContained_andLogged_independentProbe`：注入 `getSettings()` 抛 `IllegalStateException` → **逃逸异常数 = 0**、`Log.eCount == 1`、`tag == "EbbinghausLaunch"`、异常类型 = `IllegalStateException`。**通过 ✅**
  - `dashboardInit_reviewRepoThrows_alsoContained_notOnlySettingsPath`：**新增角度**——把失败注入 `reviewRepository.getDueReviewTasks()`（工程师既有用例只注入 settings）→ 同样**不逃逸 + 1 条日志**。**通过 ✅**
- **阳性对照（关键）**：`probe_isCapable_ofDetectingEscape_memoDetailInitStillUnbounded` 用**同一探针**构造仍无边界的 `MemoDetailViewModel.loadMemoDetails()` → **探针检出逃逸（非空）**。**证明「探针有能力检出逃逸」，故上面「不逃逸」的结论不是假阴性。** **通过 ✅**

> ⚠️ **本轮方法学发现（重要）**：工程师既有用例所用的「`runTest{}` → `UncaughtExceptionsBeforeTest`」探针**行为不稳定**。我实测：**同一个无边界协程**（`MemoDetailViewModel`），在 A 类可被检出（`caught`）、在隔离运行/某些配置下却返回 `null`（**假阴性**）。因此本终审**改以「线程默认未捕获异常处理器」捕获为权威探针**（语义 = 真机 `FATAL EXCEPTION` → 进程终止的必经路径），并经阳性对照验证其可靠性。此发现**不影响**工程师用例的最终有效性（其 `Log.eCount == 1` 断言为强判据，见 §3.1），但**其「不逃逸」断言本身属弱断言**，已记入 §6 建议。

### 2.3 「静默失效」独立评估：`DashboardViewModel.init` **仅日志、无可见反馈**

工程师理由：「冷启动首帧前 Snackbar 无可靠订阅者」。**我的独立评估：理由「大体成立，但表述不够准确，且残留一处真实静默风险」。**

- ✅ **成立的部分**：该 `init` 只影响**首页角标 / 待复习计数**（非阻断的便利指示）。且失败多为**瞬时**：用户点「复习」Tab 会触发 `loadReviewBatch()` 以真实数据重载，`MemoListScreen:307` 的 `LaunchedEffect` 也会派发 `OnCheckAppLaunch` 重读并**覆盖**该状态 → 瞬时失败可**自愈**。
- ⚠️ **表述不准确**：`_effect` 是 `MutableSharedFlow(replay = 0, extraBufferCapacity = 1)`（`DashboardViewModel.kt:81`）——**可缓冲 1 条**。因此「Snackbar 无可靠订阅者」并不完全成立：一条 emission **会被缓冲并投递给首个订阅者**（`MemoListScreen.kt:352`）。真正站得住的理由是「该计数属非阻断便利指示」，而非「技术上无法提示」。
- 🔴 **残留真实风险（用户无感的功能失效）**：若 Room **持续**不可用，则 `init` 与 `checkAppLaunchPrompt` **双双失败**（均仅 `Log.e`）→ 角标/看板**长期静默为 0**，用户**完全无感**（会误以为「今天没有要复习的」）。这属**用户可感知功能的静默降级**，严格说未完全满足「不静默」。**建议（P2）**：给 Banner 增加一个**可见的降级指示**（如显示「—」或一行浅色「统计暂不可用」），成本极低。

---

## 3. C. 「反转 1 条断言」正当性（**本次最重要的一项**）

### 3.1 结论：**接受，反转正当** —— **非掩盖、非削弱，实为加强**

**改动前后对照（逐行实读）**：

| | 第 1 轮（旧） | 第 2 轮（新） |
|---|---|---|
| 用例名 | `..._dependencyThrows_escapesUncaught_provingMissingBoundary` | `..._dependencyThrows_isContainedByBoundary_notEscaping` |
| 断言① | `reported != null`（**要求异常逃逸** = 要求缺口存在） | `reported == null`（要求**不逃逸**） |
| 断言② | `Log.eCount == 0`（要求**无日志** = 无边界） | `Log.eCount == 1`（要求**有日志** = 非静默） |
| 断言③ | — | `Log.lastTag == "EbbinghausLaunch"` |
| 断言④ | — | `Log.lastThrowable is IllegalStateException` |

**三条判定理由**：

1. **反转是逻辑必然，而非偷懒**：旧断言编码的是**缺陷本身**（「缺口存在」）。缺口修好后，旧断言**在逻辑上不可能同时成立**（不可能既「已收敛」又「逃逸」）。**任何正确修复都必然要求该用例改写**——这是回归测试的标准做法（bug-encoding 用例在 bug 修复后必须翻转）。
2. **新断言更强，且含一个「强判据」**：断言数 2 → 4。尤其 `Log.eCount == 1` **只能**在 `catch(Exception)` 分支真正执行时为真——若边界缺失，异常逃逸、`Log.e` **永不调用** → `eCount == 0` → **新用例必然失败**。故 `eCount == 1` 是「边界存在」的**可靠判别器**，反转**没有削弱**判别力。（相较之下 `assertNull(reported)` 因探针脆弱属弱断言，但**强判据由 `eCount` 承担**。）
3. **我做了独立交叉验证（不依赖该反转用例）**：
   - 源码实读：边界存在（§2.2）；
   - **独立探针**（线程处理器）：`DashboardViewModel.init` 注入异常 → **逃逸 = 0**；
   - **阳性对照**：同一探针能检出「真正无边界」的 `MemoDetailViewModel` → 证明上一条非假阴性；
   - 工程师另新增 `StartupCoroutineBoundaryTest`（9 条，独立文件）覆盖同一行为 → **双重确认**。

> **裁定：接受该反转。** 它**没有掩盖任何问题**（缺口已由源码 + 独立探针 + 阳性对照三方证实闭合），**也没有削弱测试**（判别力反而增强）。若不接受，唯一「替代方案」是**保留旧用例并让它失败**——那等于要求缺陷复现，与本轮修复目标直接冲突，**不可取**。**替代方案（我给出的更强版本）**：以「线程默认未捕获异常处理器」探针替换脆弱的 `runTest` 探针，并保留 `Log.eCount` 强判据（我已在本轮 `QaRound2FinalVerificationTest` 中落地示范）。

### 3.2 其余既有用例未被削弱（独立扫描）

| 检查项 | 命令/方法 | 结果 |
|---|---|---|
| 恒真断言 | `grep -rn "assertTrue(true)\|assertFalse(false)\|assertNotNull(null)\|assertNull(null)"` | **0 命中** ✅ |
| 跳过/禁用 | `grep -rn "@Ignore\|@Disabled\|assumeTrue\|Assume\."` | **0 命中** ✅ |
| 用例数守恒 | 逐文件 `grep -c "@Test"` + 逐套件 XML | `QaCrashHardeningVerificationTest` 仍 **8**；app 228→246 的 **+18 全部由 2 个新文件解释**（9+9）→ **既有用例数零变化** ✅ |
| 第 1 轮基线回归 | `275 = 246 + 47 − 18` | **完全吻合** ✅ |

> 受限说明：本仓库**无 git 基线**，无法对全部既有测试做逐行 `diff`。但「用例数守恒 + 零跳过/零恒真 + 基线数值吻合 + 工程师声明唯一改动处已逐行核验」四点**交叉印证**，结论可靠。

---

## 4. D. 崩溃自诊断复核 + 对抗测试

### 4.1 `crash/CrashLogger.kt` 实读（4 项硬约束）

| 硬约束 | 独立结论 | 证据（行号） |
|---|---|---|
| **绝不吞崩溃**（必须调用原 handler） | ✅ **是** | `createHandler` 先 `runCatching { writeCrash(...) }`（`:79`），再 `if (previous != null) previous.uncaughtException(thread, throwable)`（`:81-82`）；仅当 `previous == null` 才 `terminateProcess()`（`:85`，`killProcess` + `exitProcess(10)`）。**落盘在前、交还在后** → 进程终止前日志已落盘 |
| **写文件全包 try/catch**（不二次崩溃） | ✅ **是（双保险）** | `writeCrash` 整体 `try { … } catch (e: Throwable) { null }`（`:98-116`，`Throwable` 级）；handler 侧再套 `runCatching`（`:79`） |
| **零权限** | ✅ **是** | 仅写 `crashDirProvider()`（生产 = `filesDir/crash`，`EbbinghausApp.kt:70`）；**无外部存储、无 `FileProvider`、无 `<provider>` 声明** |
| **保留策略（最近 3 次）** | ✅ **正确** | `prune`：`listArchives(dir).drop(maxRecords)`（`:201-205`）；`listArchives` 按文件名（内嵌 19 位零填充毫秒时间戳，字典序=时间序）`sortedByDescending`（`:207-212`）→ 删除更旧；`DEFAULT_MAX_RECORDS = 3`（`:226`）；`clear()` 删除全部（`:147-155`） |

> 附加优点：`readLatest()`/`hasCrashRecord()`/`recordCount()`/`clear()` 全部包 `try/catch`，失败返回安全默认值（`:119-155`）——UI 触碰文件系统不会崩。

### 4.2 `ui/settings/CrashLogSection.kt` 实读

| 检查项 | 结论 | 证据 |
|---|---|---|
| **仅存在崩溃记录时显示入口** | ✅ | `if (current != null && current.hasRecord)`（`:82`）——无记录**完全不占位** |
| 「复制」用 `ClipboardManager` | ✅ | `context.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager` + `setPrimaryClip(ClipData.newPlainText(...))`（`:241-242`），`runCatching` 包裹 |
| 「分享」用 `ShareCompat` + `EXTRA_TEXT` | ✅ | `ShareCompat.IntentBuilder(context).setType("text/plain").setSubject(...).setText(text).startChooser()`（`:254-259`）——**纯文本，无 FileProvider、无权限** |
| 「清空」真的删除记录 | ✅ | 确认弹窗 → `withContext(IO) { crashLogger.clear() }` 后重读快照（`:113-116`） |

### 4.3 零新增权限 / 零新增依赖（实测）

| 检查项 | 结果 |
|---|---|
| 源 `AndroidManifest.xml` `uses-permission` | **0 条**（`grep -c` = 0） |
| 合并后 Manifest | 仅 1 条**自动注入**的 `com.ebbinghaus.memo.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（`androidx.core` 在 API 33+ 为动态接收器自动生成，**非存储权限、非本轮新增**） |
| `<provider>` / `FileProvider` | **0 命中** |
| `app/build.gradle.kts` / `core/build.gradle.kts` | 崩溃自诊断相关 import 全部落在**既有依赖**上（`android.content.*`、`androidx.core.app.ShareCompat` ← 来自既有 `androidx.core:core-ktx:1.15.0`、`kotlinx.coroutines.*`）；**未见任何新增依赖声明** |

### 4.4 对抗测试（**已真实写入项目并跑通**）：`QaRound2FinalVerificationTest`（13 条，全绿）

| # | 用例 | 覆盖点 | 结果 |
|---|---|---|---|
| 1 | `crashLogger_report_containsFullStackAndNestedCauseChain` | 落盘含**三层**异常消息 + **≥2 级 `Caused by`** + 真实 `at` 栈帧（定位到调用点）+ 元信息/时间戳 | ✅ |
| 2 | `crashLogger_handler_mustInvokeOriginalHandler_andWriteCrash` | **原 handler 被调用**（假 handler 断言被调）+ 交还**同一异常对象** + 同时落盘 | ✅ |
| 3 | `crashLogger_handler_whenWriteFails_stillInvokesOriginalHandler` | **写失败仍必须委托原 handler** | ✅ |
| 4 | `crashLogger_writeCrash_neverThrows_whenDirUnusable` | **写文件失败不抛异常**（返回 `null`） | ✅ |
| 5 | `crashLogger_retention_deletesOldestBeyondLimit` | **保留策略**：上限 2、写 5 次 → 仅存最新 2，最旧 3 份被删 | ✅ |
| 6 | `crashLogger_clear_removesEverything_andEmptyStateIsSafe` | 清空删除归档+固定副本；空态各接口安全 | ✅ |
| 7 | `probe_isCapable_ofDetectingEscape_memoDetailInitStillUnbounded` | **阳性对照**：探针能检出真逃逸 | ✅ |
| 8 | `dashboardInit_dependencyThrows_isContained_andLogged_independentProbe` | init 缺口已覆盖（不逃逸 + 非静默） | ✅ |
| 9 | `dashboardInit_reviewRepoThrows_alsoContained_notOnlySettingsPath` | **新角度**：`getDueReviewTasks` 失败也被收敛 | ✅ |
| 10 | `reviewInit_dependencyThrows_setsVisibleErrorState_andDoesNotEscape` | Review 边界 + **可见错误态** | ✅ |
| 11 | `settingsInit_dependencyThrows_setsVisibleError_andDoesNotEscape` | Settings 边界 + 可见提示 + 不停留加载态 | ✅ |
| 12 | `allInitBoundaries_cancellation_isRethrown_notSwallowed` | 三处 init 注入 `CancellationException` → **`Log.eCount == 0`**（取消信号未被吞） | ✅ |
| 13 | `normalPath_noBoundaryTriggersNoErrorLog` | 正常路径零错误日志、零逃逸 | ✅ |

**无法 JVM 测的部分（如实声明）**：
- `createHandler(previous = null)` → `terminateProcess()` 分支（`killProcess` + `exitProcess`）**会终止 JVM**，纯 JVM 不可测；该分支仅在生产（系统必已设置默认 handler）作极端兜底。
- `ClipboardManager` / `ShareCompat` 分享、`buildCrashMetadata(context)`、真机 handler 注册时机 —— 均依赖 Android 运行时，**本机无模拟器/真机，无法复现**（**未重复尝试启动模拟器**）。

---

## 5. E. 是否仍有遗漏

### 5.1 工程师自述遗留项独立评估

| 遗留项 | 工程师判断 | 我的独立评估 |
|---|---|---|
| `MemoDetailViewModel.loadMemoDetails()` 无边界 | 「**非启动期创建，与一打开就崩无关**」 | ✅ **判断成立**：该类仅在**导航进入详情页**时创建（`AppNavigation.kt:387-399` 的 `composable(MemoDetail)` 内），**不在首帧组合期**。我的阳性对照亦证实其异常**确实会逃逸**（即它是一处**真实但非启动期**的崩溃面）。**接受其作为已记录遗留**；建议 P2 顺手补齐（成本极低，与已建立的统一模式一致） |

### 5.2 启动路径再扫描（独立）

`AppNavigation.kt:101-169` 首帧创建 **6 个** ViewModel，逐一核对其启动期协程：

| ViewModel | 启动期协程 | 边界 | 结论 |
|---|---|---|---|
| `MemoListViewModel` | `observeMemos()` / `observeAllTags()` | `.catch`（`:225` / `:250`） | ✅ 完备 |
| `ReviewViewModel` | `init → loadReviewBatch()` | try/catch（`:114-155`） | ✅ 本轮补齐 |
| `DashboardViewModel` | `init`（`:103`）+ `checkAppLaunchPrompt()`（`:174`） | 两处均 try/catch | ✅ 本轮补齐 |
| `SettingsViewModel` | `init`（`:61`） | try/catch（`:62-84`） | ✅ 本轮补齐 |
| `DataSafetyViewModel` | **无 `init` 协程** | — | ✅ 构造仅建 `MutableStateFlow`（`:92-99`），读 `preferenceStore.*.value` 为**同步 StateFlow 读**，非启动期协程缺口 |
| `TrashViewModel` | `observeTrash()` | `.catch`（`:132`，位于 `collect` 上游） | ✅ 完备 |

> **结论：启动路径上「无异常边界的启动期协程」= 0。** 第 1 轮报告中列出的 3 条（`DashboardViewModel.init` / `ReviewViewModel.init` / `SettingsViewModel.init`）**全部闭环**。

**未改动项（如实，非本轮引入）**：
- `MainActivity.onCreate`（`:27-34`）在 `setContent` **之前**、**主线程**同步读取全部 `by lazy` 仓储（触发 `Room.databaseBuilder(...).build()` 与 `SharedPreferences` 同步读）。若其中任一步抛异常 → `onCreate` 未捕获 → 闪退。**这不是协程边界问题，而是既有的主线程初始化架构问题**；工程师本轮**仅更正了 KDoc 描述**（`EbbinghausApp` / `MainActivity` 已如实说明「惰性化≠避免首帧阻塞」），**未改实现**。属**已知遗留**，建议 P2 另立任务。

---

## 6. 智能路由判定

### 判定：**`NoOne`**（全部通过；附 P2 建议）

**依据**：

1. **无失败用例**：终值 **306 / 0 failed**（工程师基线 293 已核实）；新增 13 条对抗用例全绿。
2. **无阻断性源码缺陷**：7 处边界**顺序全对**、`DashboardViewModel.init` 缺口**已闭合**（源码 + 独立探针 + 阳性对照三方证实）。
3. **反转断言正当**（§3）——**不是** QA 侧需要「改测试自己修」的缺陷，也**不是**需要打回工程师的缺陷。
4. **非 `Engineer`**：本轮未发现任何需回炉的实现 bug。
5. **非 `QA`（自身回炉）**：我的 13 条新用例**无需回炉**（全绿）。

**P2 建议（不阻断交付，供后续任务评估）**：
- **P2-1（可见性）**：`DashboardViewModel.init` 失败时给 Banner 增加**可见降级指示**，消除「持久失败下的静默失效」（§2.3）。
- **P2-2（遗留边界）**：`MemoDetailViewModel.loadMemoDetails()` 按统一模式补边界（§5.1）。
- **P2-3（测试质量）**：将工程师用例中的「`runTest{}` 探针」替换为「线程默认未捕获异常处理器」探针（`assertNull(captureUncaught())` 属弱断言、已实测存在假阴性）；`Log.eCount` 强判据予以保留。（示范见 `QaRound2FinalVerificationTest.captureEscapes`）
- **P2-4（架构）**：`MainActivity.onCreate` 主线程同步初始化（§5.2）——真正消除首帧阻塞需另立任务。

> 注：本 QA 轮次**未修改任何 `app/src/main` 业务源码**（硬约束遵守），上述建议仅报告、不代改。

---

## 7. 给用户的最终操作指引：如何用崩溃日志把根因交回

> 目标：把「未定位」变成「已定位」。**本轮已内置自诊断——用户无需电脑、无需 root、无需 adb**。

### 7.1 最简路径（推荐，零门槛）

1. **复现崩溃**（打开 App 即崩）；
2. **再次打开 App**（崩溃日志在**下次启动后**仍保留在私有目录）；
3. 进入底部 **「设置」** Tab → 拉到底部 **「关于」** 分组；
4. 会看到 **「崩溃日志」** 入口（**仅当存在崩溃记录时出现**），副文案显示「检测到 N 次崩溃记录」；
5. 点 **「查看」** → 弹窗展示**最近一次崩溃的完整堆栈**（等宽字体、可滚动，含 `Caused by` 链）；
6. 点 **「复制」** 或 **「分享」**（微信/邮件等）→ 把全文发回即可。

> 日志落在**应用私有目录** `filesDir/crash/`（`last_crash.txt` + `crash-<时间戳>.txt`，**保留最近 3 次**），**零权限**、无需外部存储。

### 7.2 若 App 崩到「连设置页都进不去」

- 用 Android 自带 **错误报告**：设置 → 关于手机 → 连点 7 次「版本号」→ 开发者选项 → **错误报告 → 完整报告** → 在 `bugreport-*.txt` 搜 **`FATAL EXCEPTION`** 与 **`Caused by:`**。
- 或有电脑时：`adb logcat -b crash -d`（包名 = **`com.ebbinghaus.memo.debug`**，debug 变体带 `.debug` 后缀）。
  > ⚠️ **更新（2026-09-16）**：包名已于 2026-09-16 统一为 `com.ebbinghaus.memo`（移除了 debug 的 `applicationIdSuffix`）。此处旧包名仅作历史记录，**实际抓取请使用 `com.ebbinghaus.memo`**（若仍用 `com.ebbinghaus.memo.debug` 会过滤不到任何日志）。详见 `design/PACKAGE_UNIFY.md`。

### 7.3 请同时补充这 3 条信息（极大加速定位）

1. 是否曾**开启过「自动快照」并选过目录**？（决定 SAF 链路是否被激活）
2. 崩溃前是否**安装过旧版本 / 有旧数据**？（决定 Room v1→v2 迁移路径是否被走到）
3. **release 包是否也崩**？（若 release 不崩 → 收敛到 debug-only 差异）

---

## 8. 附：本轮实测命令（可复现）

```bash
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11

./gradlew test --console=plain --rerun-tasks              # → 306 tests / 0 failed（app 259 + core 47）
./gradlew assembleDebug --console=plain                   # → BUILD SUCCESSFUL，APK 18,012,344 B
./gradlew :app:lintDebug --console=plain --rerun-tasks     # → 0 errors / 9 warnings（全 ModifierParameter）

# 本轮新增对抗用例（终审）
./gradlew :app:testDebugUnitTest --tests 'com.ebbinghaus.memo.qa.QaRound2FinalVerificationTest'

# 关键静态证据
grep -rn "catch (" app/src/main/java --include=*.kt        # 逐处核对取消分支顺序
grep -rn "const val LOG_TAG" app/src/main/java --include=*.kt   # tag 统一性
grep -c "uses-permission" app/src/main/AndroidManifest.xml      # → 0
grep -rn "FileProvider\|provider" app/src/main/AndroidManifest.xml  # → 0
```

### 本轮新增/改动文件清单（**均在 `src/test`，未动业务源码**）

- 新增 `app/src/test/java/com/ebbinghaus/memo/qa/QaRound2FinalVerificationTest.kt`（13 用例）
- 未修改：`app/src/main/**`、`core/src/main/**`、`app/build.gradle.kts`、`core/build.gradle.kts`、`AndroidManifest.xml`、任何既有测试文件（含工程师反转的那条——**仅审查，未改动**）
- 临时实验文件（`ScratchProbeExperiment*.kt`）**已创建用于方法学验证并已删除**，不留在仓库

---

# 第 3 轮终审（根因修复）—— 启动崩溃根因定案与修复的独立验证

> QA：严过关（Edward）｜ 日期：2026-09-16 ｜ 对象：`D:\Projects\androidapk`
> 立场：**不采信工程师自述**。本轮对其「根因定案 + 4 处修复 + 10 条回归」做**独立复核**，
> 并以**变异测试（先红后绿）**验证回归用例真的在守护缺陷。
> 方法：全部数字实测；变异测试**已完整还原**，净改动为零（校验见 §3.4）。

## 0. 最终判定（结论先行）

| 结论项 | 我的独立判定 |
|---|---|
| **根因定案是否成立** | **成立**。确为 `Screen` / `TopLevelDestination` 的 **JVM 类静态初始化循环（JVM Spec §5.5）**；与用户 logcat 的 `Caused by` **逐字吻合**（我自己复现出来的，非转述） |
| 4 处修复是否真实、是否够 | **是**。字节码级证实 `Screen.<clinit>` 已不再触碰任何嵌套 `data object` |
| **`LazyThreadSafetyMode.PUBLICATION` 选型是否正确** | **正确**（独立评估见 §2.2）。`SYNCHRONIZED` 每次取值都加锁、`NONE` 无 volatile 发布，均劣于 `PUBLICATION` |
| **回归用例是否恒真空转** | **否**。变异测试 3 组，**先红后绿**证据确凿（§3） |
| `URLClassLoader` 隔离是否真的让复现与顺序无关 | **是**。单独运行该测试类即可稳定复现（§3.3） |
| **历史悬案更正（`DELIVERY_SUMMARY.md` §五-2）是否成立** | **成立，我确认**。我亲自复现了 `entries == [null, Review, Settings]` 静默污染（§5） |
| 是否有遗漏的同型风险 | **未发现**。我独立补扫了 6 个顶层 `object` 单例 + 全部 58 处 `data object`（§6） |
| **智能路由判定** | **`NoOne`** —— 源码无 bug、测试无 bug，无需返工。仅 1 项 **P2 文档数字更正**（APK 体积，§7） |

### 遗留红线

- **未做真机验证**：本机无模拟器/真机，**未启动、也未尝试启动**任何设备。
  全部结论基于 JVM 单元测试 + 字节码 + 静态分析。**必须由用户安装 debug APK 实机确认启动不再崩溃。**
- 本轮**唯一无法用 JVM 证明**的环节是「release 因 R8 重排而不崩」——该条属**推断**（合理但与本次修复的正确性无关，因为修复后 debug/release 都不触碰循环）。

## 1. A. 构建与测试（全部实测，非转述）

环境：`JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11`，POSIX / Git Bash，`cmd.exe` 未使用。

### 1.1 `./gradlew test --console=plain --rerun-tasks`

```
BUILD SUCCESSFUL in 4m 55s
57 actionable tasks: 57 executed      ← 确认 --rerun-tasks 生效，无 UP-TO-DATE 取巧
```

解析 `build/test-results/**/*.xml` 实测（**两变体分别统计**）：

| 模块 / 变体 | 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `app` `testDebugUnitTest` | 42 | **269** | 0 | 0 | 0 |
| `app` `testReleaseUnitTest` | 42 | **269** | 0 | 0 | 0 |
| `core` `test` | 5 | **47** | 0 | 0 | 0 |
| **单变体合计** | — | **316**（269+47） | **0** | **0** | **0** |
| **全量执行次数** | — | **585** | **0** | **0** | **0** |

> ✅ 与工程师自述 **316 / 0 failed** 一致；其「585 次执行」亦一致（269×2+47）。
> ✅ 新增回归类在**两个变体**中均为 `tests=10 failures=0`。

### 1.2 `./gradlew assembleDebug --console=plain --rerun-tasks`

```
BUILD SUCCESSFUL in 2m 39s     （第一次全新构建）
BUILD SUCCESSFUL in 3m 22s     （第二次全新构建，用于验证可重现）
```

| 项 | 实测 |
|---|---|
| 结果 | **BUILD SUCCESSFUL**（两次） |
| APK 体积 | **17,817,608 B**（两次完全一致） |
| APK md5 | `5b60aad71e9d5893c413cb8612270cf4` |
| zip 条目 | 145；`classes.dex` + `classes2~18.dex`；**无** `baseline.prof` |

> ⚠️ **与工程师自述不一致**：其记录的 **18,012,344 B** 我**未能复现**，差 **194,736 B**。
> 已排除源码差异：4 个修复文件 md5 与基线**逐字节一致**（§3.4），且 `find -newermt "15:53"` 显示
> 15:52 之后除我还原 cp 的两个文件（内容不变）外**无任何源文件变动**。
> 判定：属**构建/打包状态差异**（增量 vs `--rerun-tasks` 全新打包），**不影响交付**，
> 但 `DELIVERY_SUMMARY.md` / `CRASH_INVESTIGATION.md` 中的 18,012,344 应更正为 **17,817,608**（P2，见 §7）。

### 1.3 `./gradlew :app:lintDebug --console=plain --rerun-tasks`

```
BUILD SUCCESSFUL in 2m 52s
30 actionable tasks: 30 executed      ← 确认全新分析
```

解析 `app/build/reports/lint-results-debug.xml`（**全新生成**）实测：

| severity | 数量 |
|---|---|
| **Error** | **0** ✅ |
| Warning | **23** |

**23 条 warning 逐类核实**：

| id | 数量 | 位置 | 是否既有 |
|---|---|---|---|
| `GradleDependency` | 14 | 全部指向 `app/build.gradle.kts` | ✅ **既有**。内容全为「A newer version of X than Y is available」（core-ktx 1.15.0→1.19.0、room 2.6.1→2.8.5、navigation 2.8.4→2.10.1 等 14 条）。`app/build.gradle.kts` mtime = **2026-09-15 11:49**，本轮**未修改** |
| `ModifierParameter` | 9 | `MemoListScreen.kt`×4、`EmptyState.kt`×2、`DashboardBanner.kt`×1、`ErrorState.kt`×1、`LoadingSkeleton.kt`×1 | ✅ **既有**。Compose 惯例告警，与第 2 轮记录的「9 条」**完全同位** |

**「比第 2 轮多了 14 条」的独立结论**：14 条 `GradleDependency` 是 lint 联网比对 Maven 元数据后产生的
版本可用性提示——**第 2 轮那次跑 lint 时网络/缓存未命中，故只有 9 条**。它与本轮改动**零相关**：
本轮改动的 4 个文件（`Screen.kt` / `TopLevelDestination.kt` / `MemoSortOption.kt` / `TrashSortOption.kt`）
**在 23 条告警中 0 命中**。**不影响交付。**

## 2. B. 独立复核修复

### 2.1 实读 `companion object`：确为 `by lazy`

| 文件 | 行 | 实测内容 |
|---|---|---|
| `Screen.kt` | 48 | `private val topLevelRoutes: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { setOf(MemoList.route, Review.route, Settings.route) }` |
| `TopLevelDestination.kt` | 66 | `val entries: List<TopLevelDestination> by lazy(LazyThreadSafetyMode.PUBLICATION) { listOf(Library, Review, Settings) }` |
| `MemoSortOption.kt` | 34 | `val DEFAULT: MemoSortOption by lazy(LazyThreadSafetyMode.PUBLICATION) { UPDATED_DESC }` |
| `TrashSortOption.kt` | 30 | `val DEFAULT: TrashSortOption by lazy(LazyThreadSafetyMode.PUBLICATION) { DELETED_DESC }` |

✅ 4/4 均为 `by lazy` + `PUBLICATION`。

**字节码铁证**（`javap -p -c`，修复后全新编译产物）：

```
Screen.<clinit>:
   0: new  Screen$Companion
   8: putstatic Companion
  11: getstatic kotlin/LazyThreadSafetyMode.PUBLICATION
  19: invokestatic kotlin/LazyKt.lazy
  22: putstatic topLevelRoutes$delegate
  25: return                      ← 全程零次 getstatic Screen$MemoList

TopLevelDestination.<clinit>:    ← 同构，仅创建 Companion + Lazy 委托后即 return
```

> 这是**比"读源码"更强的证据**：修复后 `Screen.<clinit>` 的指令流中**不存在任何**
> `Screen$MemoList` / `Screen$Review` / `Screen$Settings` 的引用，循环在字节码层面被彻底切断。
> （作为对照，变异态下该指令流**确实存在** `getstatic Screen$MemoList.INSTANCE` —— 见 §3.1。）

### 2.2 独立评估：`PUBLICATION` 选型是否安全？

工程师理由：「不能假设只主线程访问 → 不用 `NONE`；为省锁竞争 → 不用 `SYNCHRONIZED`」。
我**独立复核后判定：选型正确**，逐项给出我自己的理由（非复述）：

| 模式 | 实现 | 高频重组期的代价 / 风险 | 我的判定 |
|---|---|---|---|
| `SYNCHRONIZED` | `SynchronizedLazyImpl`，**每次 `getValue` 都进 `synchronized(this)`** | `isTopLevel` 在重组期被高频调用 → 每次一个 monitor 进出，无谓争用 | 劣 |
| `NONE` | `UnsafeLazyImpl`，**普通（非 volatile）字段** | 若率先由后台线程触发并缓存，主线程**可能读到 null 或未安全发布的引用** | 劣（有真实可见性风险） |
| **`PUBLICATION`** | `SafePublicationLazyImpl`，value 存于 **volatile 字段**，命中后仅一次 volatile 读，首次无锁 | 安全发布由 volatile 写/读保证；命中路径无锁 | **优** |

**关于「可见性 / 重复初始化风险」这一具体质疑，我的结论是无实际风险**：

1. **可见性**：`PUBLICATION` 的值字段是 volatile，一旦某线程观察到非 null，JMM 保证其看到的对象已**完全构造**（安全发布）。不存在半初始化可见。
2. **重复初始化**：并发首次访问下 lambda 可能被求值多次，但 `setOf(...)` / `listOf(...)` 是**纯函数**，结果内容恒等；且三个元素是 `data object` 的单例 `INSTANCE`，**元素身份不受影响**。
3. **唯一可观测差异**（如实披露）：极端竞态下两个线程可能拿到**两个不同的 `List` 实例**（内容相等）。
   `topLevelRoutes` 是 `private` 且只用 `route in topLevelRoutes`；`entries` 的调用点用 `firstOrNull { it.route == route }`
   （返回**元素**身份）与 `assertEquals`（内容比较）。**既有代码中不存在对 `List` 本身做 `assertSame` 的调用**，故无实际影响。
4. **顺序稳定性**：`listOf(Library, Review, Settings)` 是字面量顺序，惰性化不改变求值顺序。
   实测 `topLevelDestination_entries_preservesTabOrder` 断言 `[Library, Review, Settings]` **通过**（含隔离类加载器下的那份断言）。

### 2.3 对外语义零改动（字节码级核对）

`javap -p` 导出 Companion 的 **public** 成员，逐项比对：

```
Screen$Companion              : isTopLevel(String) : boolean
                                isMemoDetail(String) : boolean
TopLevelDestination$Companion : getEntries() : List<TopLevelDestination>
                                fromRoute(String) : TopLevelDestination
                                isBadgeTarget(TopLevelDestination) : boolean
```

| API | 签名/语义 | 实测 |
|---|---|---|
| `isTopLevel` | `fun(String?): Boolean` | ✅ 未变（JVM 签名 `isTopLevel(String)`，`String?` 即 `String`） |
| `isMemoDetail` | `fun(String?): Boolean` | ✅ 未变 |
| `entries` | `List<TopLevelDestination>` | ✅ 类型与 getter 名（`getEntries`）均未变 |
| `fromRoute` | `fun(String?): TopLevelDestination?` | ✅ 未变 |
| `isBadgeTarget` | `fun(TopLevelDestination): Boolean` | ✅ 未变 |
| `createRoute` | `MemoDetail.createRoute(Long): String` | ✅ 未变（`Screen.MemoDetail` 未触及） |

`topLevelRoutes` 仍为 **`private`**（`javap` 显示 `private final Set<String> getTopLevelRoutes()`），未外泄。
调用点全量 grep（`AppNavigation.kt` 等）**零改动**，本轮改动文件清单中不含任何调用方文件。

## 3. B. 变异测试：先红后绿的**独立**证据（本轮核心）

我不采信工程师的截图/表格，**亲自**做了 3 组变异。每组都是：改源码 → 跑测试 → 记录 → **立刻还原 + md5 校验**。

### 3.1 变异 M1：仅还原 `Screen.kt` 的 `by lazy`

```kotlin
// 变异后（Screen.kt:48）
private val topLevelRoutes: Set<String> = setOf(MemoList.route, Review.route, Settings.route)
```

**结果：BUILD FAILED，10 tests / 2 failed**

```
Caused by: java.lang.ExceptionInInitializerError
Caused by: java.lang.NullPointerException:
    Cannot invoke "com.ebbinghaus.memo.ui.navigation.Screen$MemoList.getRoute()"
    because "com.ebbinghaus.memo.ui.navigation.Screen$MemoList.INSTANCE" is null
  at com.ebbinghaus.memo.ui.navigation.Screen.<clinit>(Screen.kt:48)
```

**与用户 logcat 的逐字对照**：

| 用户 logcat（`Caused by`） | 我复现出的（`Caused by`） | 一致 |
|---|---|---|
| `java.lang.NullPointerException` | `java.lang.NullPointerException` | ✅ |
| `Attempt to invoke virtual method 'java.lang.String com.ebbinghaus.memo.ui.navigation.Screen$MemoList.getRout...'` | `Cannot invoke "com.ebbinghaus.memo.ui.navigation.Screen$MemoList.getRoute()" because "...Screen$MemoList.INSTANCE" is null` | ✅ 同类名、同方法 `getRoute()`、同为 `INSTANCE is null` |
| `at com.ebbinghaus.memo.ui.navigation.Screen.<clinit>(Screen.kt:29)` | `at ...Screen.<clinit>(Screen.kt:48)` | ✅ 同语句；**行号位移 29→48** 是因为本轮新增了约 19 行 KDoc 注释 |

> **结论：用户 logcat 的崩溃被我在本机 JVM 上原样复现，根因定案无误。**
> 附：另一条失败经 `TopLevelDestination$Library.<init>(TopLevelDestination.kt:28)` 触发——即
> `route = Screen.MemoList.route` 这一构造参数，正是 `AppNavigation.kt:215` 同型的首触路径。

### 3.2 变异 M2：仅还原 `TopLevelDestination.kt` 的 `by lazy`（`Screen` 保持修复态）

**结果：10 tests / 3 failed**

```
topLevelDestination_entries_preservesTabOrder
  java.lang.AssertionError: 顺序即一级 Tab 展示顺序，不可调整
  expected:<[Library, Review, Settings]> but was:<[null, Review, Settings]>      ★★★

topLevelDestination_nestedObjectTouchedFirst_initializesCleanly
  java.lang.AssertionError: entries[0] 不应为 null（companion 在类初始化期读到了尚未赋值的嵌套 object）

topLevelDestination_fromRoute_resolvesKnownRoutesOnly
  java.lang.NullPointerException: Cannot invoke "...TopLevelDestination.getRoute()" because "it" is null
      at ...TopLevelDestination$Companion.fromRoute(TopLevelDestination.kt:74)
```

> ★★★ **我亲自复现了工程师声称的「静默污染」：`entries` 变成 `[null, Review, Settings]`。**
> 这是 §5（历史悬案更正）成立与否的关键证据，我**没有采信他的转述，自己跑出来的**。
> 注意：**这次失败时 Compose 图标引用一行未动**，崩溃纯由初始化循环造成。

### 3.3 变异 M3：两个文件**同时**还原为修复前（复刻工程师「修复前」场景）

**结果：10 tests / 4 failed** —— 与工程师自述的「修复前 10 tests / 4 failed」**完全一致**：

| 用例 | 我的实测失败原因 | 与工程师表格 |
|---|---|---|
| `screen_nestedObjectTouchedFirst_initializesCleanly` | `ExceptionInInitializerError` ← NPE `Screen$MemoList.INSTANCE is null` @ `Screen.<clinit>` | ✅ 一致 |
| `topLevelDestination_nestedObjectTouchedFirst_initializesCleanly` | `ExceptionInInitializerError` @ `TopLevelDestination$Review.<init>(:35)` → `TopLevelDestination.<clinit>(TopLevelDestination.kt:66)` ← NPE **`Screen$Review.INSTANCE is null`** @ `Screen.<clinit>` | ✅ 一致（连 `Screen$Review` 这个细节都对上了） |
| `topLevelDestination_entries_preservesTabOrder` | `expected:<[Library, Review, Settings]> but was:<[null, Review, Settings]>` | ✅ 一致 |
| `topLevelDestination_fromRoute_resolvesKnownRoutesOnly` | NPE `TopLevelDestination.getRoute()` because `"it" is null` @ `fromRoute(:74)` | ✅ 一致 |

> 其 6 条行为契约用例在变异态下**依然通过**——说明这 10 条用例是**有区分度的组合**：
> 2 条钉扎崩溃、2 条钉扎污染、6 条钉扎语义，**不是恒真空转**。

### 3.4 ⚠️ 源码完整还原（硬性要求）——校验证据

| 文件 | 基线 md5（16:03 备份） | 还原后实测 md5 | `diff` |
|---|---|---|---|
| `Screen.kt` | `fca76cf1d34a6d74461f0f6ca14934a1` | `fca76cf1d34a6d74461f0f6ca14934a1` | **空** |
| `TopLevelDestination.kt` | `1cd4204980898f6f5e86b517d7e407d0` | `1cd4204980898f6f5e86b517d7e407d0` | **空** |
| `MemoSortOption.kt` | `3b8c8e20423a5b98fb180f72e76dc4a7` | `3b8c8e20423a5b98fb180f72e76dc4a7` | 未改动 |
| `TrashSortOption.kt` | `c21a9c11c1f43989cdaf5c516c617568` | `c21a9c11c1f43989cdaf5c516c617568` | 未改动 |

- 4/4 md5 **完全一致**，`diff /tmp/*.orig <当前>` **输出为空**。
- **净改动为零**：本轮我对 `app/src/main/` 的临时修改已 100% 还原。
- 变异用的备份文件放在 **`/tmp`（项目目录之外）**，**未污染仓库**；报告完成后删除。
- 还原后重新跑全量 `test --rerun-tasks` → **BUILD SUCCESSFUL / 585 执行 / 0 失败**（§1.1 数字即还原后测得）。
- 我也**未新增、未删除、未修改**任何项目内文件（唯一被"写"过的两个业务文件已还原为字节相同）。

### 3.5 测试独立性验证：`URLClassLoader` 是否真的与执行顺序无关？

工程师称崩溃依赖「先碰嵌套 object」的顺序。我用**单独运行该类**来验证（若依赖顺序，单跑时大概率因别的类先触发 `Companion` 而不复现）：

```bash
./gradlew :app:testDebugUnitTest --tests "*NavigationRouteInitRegressionTest" --console=plain --rerun
```

| 场景 | 是否单跑该类 | 实测结果 |
|---|---|---|
| 修复态 | ✅ 单跑 | **10 tests / 0 failed / 0 errors**，耗时 0.632 s |
| M1（Screen 还原） | ✅ 单跑 | **10 / 2 failed**（复现） |
| M2（TLD 还原） | ✅ 单跑 | **10 / 3 failed**（复现） |
| M3（两处均还原） | ✅ 单跑 | **10 / 4 failed**（复现） |

> ✅ **结论成立**：`URLClassLoader(urls, null)`（父加载器为 bootstrap）确实让每次初始化都发生在
> **全新、未被污染的静态状态**中，复现**与用例执行顺序无关**——单跑也能红，修复后单跑也绿。
> 顺带确认：该套件耗时 **0.4~0.6 s**，毫秒级，不拖慢 CI。

## 4. C. 回归检查

| 检查项 | 实测 | 判定 |
|---|---|---|
| 用例数守恒 | `app` 269 + `core` 47 = **316**；新增文件 `@Test` = **10**；269−10 = **259**，与第 2 轮记录的 `app 259 + core 47 = 306` **闭合** | ✅ 既有 306 条一条不少 |
| 削弱标记 | `assertTrue(true)` / `@Ignore` / `@Disabled` / `assumeTrue` / `Assume.assume` 全库 grep = **0 命中** | ✅ 未削弱 |
| 参数化用例 | `@ParameterizedTest` 等 = 0（不存在"隐藏的跳过"） | ✅ |
| 零新增依赖 | `app/build.gradle.kts` mtime **2026-09-15 11:49**、`core/build.gradle.kts` **2026-09-14 17:50**（均早于本轮 09-16 15:43 的修复） | ✅ 未改 |
| 零新增权限 | `grep -c "uses-permission" app/src/main/AndroidManifest.xml` = **0**；Manifest mtime **2026-09-14 18:27** | ✅ 未改 |
| `compileSdk` / `targetSdk` | `compileSdk = 35`、`targetSdk = 35`、`minSdk = 26`（`app/build.gradle.kts:11/15/16`） | ✅ |
| 无 Room schema 变更 | `app/schemas/.../1.json`、`2.json` mtime 仍为 **2026-09-15 11:28 / 11:32**，md5 `956a0254…` / `8b83ecd3…`；本轮跑了 **多轮 KSP 全量构建后仍未变动** | ✅ 未改 |
| 语义/签名零改动 | 见 §2.3 字节码级核对 | ✅ |
| 调用点未受影响 | 本轮改动文件 = 4 个（2 导航 + 2 enum），**不含任何调用方** | ✅ |

> `data` 层（`KnowledgeMemoDao.kt` 09:09、`MemoRepository.kt` 09:10）确有今日改动，但发生在
> **第 2 轮上午**，与第 3 轮（15:43）无关。

## 5. D. 历史悬案更正是否成立（独立结论：**成立，我确认**）

`DELIVERY_SUMMARY.md` §五-2 原归因：「`TopLevelDestination` 的 `data object` 在类静态初始化阶段引用
`Icons.AutoMirrored.Filled.MenuBook` 等 Compose 图标 → Robolectric 下初始化失败」。
工程师更正为「**JVM 类静态初始化循环**」。

我**独立复核后判定：更正成立**。三条独立证据，全部我自己取得：

**证据 1 —— Compose 图标不是原因（反证法，决定性）**
在**修复态**（`entries` 已惰性化）下，回归用例 `topLevelDestination_nestedObjectTouchedFirst_initializesCleanly`
会在全新类加载器中 `Class.forName("...TopLevelDestination$Library", true, loader)` —— 这一步**必然完整执行
`Library` 的构造函数**，其中 `icon = Icons.AutoMirrored.Filled.MenuBook`（`TopLevelDestination.kt:30`）
**照旧在类初始化期被求值**。该用例 **PASS**。
→ **图标在类初始化期被引用，初始化照样成功。原归因被直接证伪。**

**证据 2 —— 静默污染真实存在（我亲自复现）**
M2 实测：`expected:<[Library, Review, Settings]> but was:<[null, Review, Settings]>`（§3.2）。
→ 工程师所述「`entries` 被污染成 `[null, Review, Settings]`」**属实，非空口**。

**证据 3 —— 历史 `NoClassDefFoundError` 的成因链条完整闭合**
M3 实测的失败栈完整给出了链条：

```
TopLevelDestination$Review.<init>(TopLevelDestination.kt:35)      ← 构造参数 route = Screen.Review.route，
                                                                    注意：图标在第 37 行，异常发生在它之前
  ← TopLevelDestination$Review.<clinit>
  ← TopLevelDestination.<clinit>(TopLevelDestination.kt:66)       ← 外层类 <clinit> 抛 ExceptionInInitializerError
Caused by: NPE ... "Screen$Review.INSTANCE" is null @ Screen.<clinit>
```

`TopLevelDestination.<clinit>` 一旦抛 `ExceptionInInitializerError`，JVM 就把该类标记为 **erroneous**，
此后任何对它的访问都变成 **`NoClassDefFoundError: Could not initialize class TopLevelDestination`** ——
**这正是当年 Robolectric 报的那句话**。

> **结论**：原归因（Compose 图标）**错误**；新归因（静态初始化循环）**证据充分且可复现**。
> 工程师就地更正 `DELIVERY_SUMMARY.md` §五-2 **正确，我确认**。
> 补充一点我认为值得写进文档的：当年之所以误判，是因为 `NoClassDefFoundError` 掩盖了真正的
> `ExceptionInInitializerError`（JVM 只在**首次**失败时抛后者，之后一律降级为 `NoClassDefFoundError`），
> 而图标恰好定义在崩溃帧**之后**的构造参数上（第 35 行 route vs 第 37 行 icon），极易被误读为因果。

## 6. E. 同类扫描复核 + 是否发现遗漏

工程师列了「安全未改」清单，我**逐项抽查（≥5 项）**，并**额外补扫**了他的清单外范围。

### 6.1 抽查工程师的「安全」判定

| # | 他的判定对象 | 我的抽查方式 | 我的结论 |
|---|---|---|---|
| 1 | `ReviewStage.fromLevel` | 实读 `ReviewStage.kt:16-19`：`entries.find {…} ?: STAGE_1` 位于**函数体内** | ✅ **同意**。`STAGE_1` 在调用时求值，不参与 `<clinit>` |
| 2 | 15 处 `sealed interface` + `data object` | 全量 grep：`sealed class` **仅 2 处**（`Screen`、`TopLevelDestination`，均已修）；其余 15 处为 `sealed interface`，**均无 `companion object`**；全库 `data object` **58 处** | ✅ **同意**。关键在 `sealed class` 才有「子类初始化→父类初始化」这条依赖，`sealed interface` 没有 |
| 3 | `WindowWidthSizeClass` / `WindowHeightSizeClass` / `ReviewRating` / `SnapshotFailureReason` | 实读 4 个 enum | ✅ **同意**，均无 `companion object` |
| 4 | `SnapshotManager.Companion` | 实读 `SnapshotManager.kt:198-203`：仅 `const val SNAPSHOT_PREFIX` + `private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE`（JDK 类，无自引用） | ✅ **同意** |
| 5 | `CrashLogger` / `AppDatabase` / `Converters` / `PreferenceStore` / `EbbinghausApp` companion | 全量 grep companion 内的 `val`/`var`：`DEFAULT_MAX_RECORDS`、`MIGRATION_1_2_SQL`、`DELIMITER`、`PREFS_NAME`、`CRASH_DIR_NAME` —— **全部 `const val`** | ✅ **同意**。`const` 编译期内联，运行时不产生字段读取，不可能构成循环 |
| 6 | `MemoSortOption` / `TrashSortOption`（他称「侥幸不崩」） | **`javap -c` 看 `<clinit>` 指令序**：`putstatic CREATED_DESC@10` … `putstatic UPDATED_DESC@23` … `putstatic $VALUES@55` … `putstatic $ENTRIES@67` → **`putstatic Companion@78`** | ✅ **同意，且他的"依赖代码生成顺序"说法被字节码坐实**：枚举常量确实**先于** `Companion` 赋值。惰性化后不再依赖该隐式契约，处理恰当 |

### 6.2 我额外补扫的范围（他的清单**未列**）

| 范围 | 结果 |
|---|---|
| 6 个顶层 `object` 单例：`TrashRetention` / `Dimens` / `EbbinghausScheduler` / `RolloverEngine` / `MathTextPreprocessor` / `SearchQueryTokenizer` | ✅ 全部**自包含**（`const` / `Regex` / `.dp`），无跨 object 初始化循环 |
| `app/src/debug`（Preview 源集）、`baselineprofile`、`app/src/androidTest` 的 `companion object` | ✅ 仅 `StartupBenchmark`、`MigrationTest`、`CrashLoggerTest`，无自引用模式 |
| 嵌套 `data object` 引用**兄弟**嵌套 object（构造参数里出现 `Screen.`/`TopLevelDestination.`） | ✅ grep 0 命中 |
| 全库 `companion object` 内的非 `const` `val`/`var` | ✅ 仅剩 `Screen.topLevelRoutes`、`TopLevelDestination.entries`、`MemoSortOption.DEFAULT`、`TrashSortOption.DEFAULT` —— **即本轮已修的 4 处，无遗漏** |

### 6.3 我发现的**残余脆弱性**（**不是缺陷**，P3 建议）

`TopLevelDestination.Library/Review/Settings` 的**构造参数仍在类初始化期读取 `Screen.X.route`**
（`TopLevelDestination.kt:28/35/42`），这是一条**跨类静态初始化依赖**。当前 `Screen` 已惰性化，故无害；
但一旦将来有人在 `Screen.companion` 里再写一个 eager 自引用，`Screen.<clinit>` 失败会**连带把
`TopLevelDestination` 标记 erroneous**，又变成那个 `NoClassDefFoundError`——**这正是当年踩坑的同一条链路**。

建议（P3，不阻塞交付）：把 `NavigationRouteInitRegressionTest` 的两条钉扎**参数化**，覆盖全部 8 个首触顺序
（`Screen.MemoList / Review / Settings / Trash / MemoDetail` + `TopLevelDestination.Library / Review / Settings`）。
当前只钉扎了 `MemoList` 与 `Library` 两个入口；其余 6 个是"因同一段代码已修好而顺带安全"，缺少显式守护。

> **对「是否发现他漏掉的同型风险」的直接回答：没有。** 其扫描覆盖完整，我的独立补扫未发现新的同型风险点。

## 7. 遗留与未验证项

### 7.1 必须如实声明的未验证项

| 项 | 状态 |
|---|---|
| **真机 / 模拟器验证** | ❌ **未做**。本机无设备，**未启动也未尝试启动**任何模拟器。本轮所有结论均来自 JVM 单元测试 + 字节码 + 静态分析 |
| release 包实际行为 | ❌ 未验证（未构建/安装 release）；「release 因 R8 重排而不崩」属**合理推断**，非实测 |
| Compose UI 运行时表现 | ❌ 未验证（无仪器化测试、无 Robolectric） |

> **用户行动项**：安装 `app/build/outputs/apk/debug/app-debug.apk`（**17,817,608 B**）并冷启动，
> 确认不再出现 `ExceptionInInitializerError`。这是**唯一能闭合本轮的验证**。

### 7.2 待更正/待办（均 P2/P3，不阻塞）

| # | 级别 | 事项 |
|---|---|---|
| 1 | **P2** | `DELIVERY_SUMMARY.md` / `CRASH_INVESTIGATION.md` 中的 debug APK 体积 **18,012,344 B** → 应更正为 **17,817,608 B**（我两次全新构建一致，md5 `5b60aad71e9d5893c413cb8612270cf4`） |
| 2 | P3 | 回归用例参数化，覆盖 8 个首触顺序（§6.3） |
| 3 | P3 | `DELIVERY_SUMMARY.md` §五-2 仍写着「Robolectric 路线已验证不可行，正确路径是仪器化测试」——**该结论建立在错误归因之上**。既然真因已修，Robolectric 路线**值得重新评估**（不要求本轮做） |

### 7.3 本轮我的改动清单（自证清白）

- **新增/修改的项目文件：0**。唯一追加内容是本报告 `design/QA_CRASH_REPORT.md` 的本节。
- **临时修改**：`Screen.kt`（M1/M3）、`TopLevelDestination.kt`（M2/M3）—— **均已还原，md5 与基线逐字节一致**（§3.4）。
- **备份文件**：`/tmp/Screen.kt.orig`、`/tmp/TopLevelDestination.kt.orig`（**项目目录之外**，未污染仓库）。
- **未修改**：`app/src/main/**`（除上述临时变异并已还原）、`core/src/main/**`、构建脚本、`AndroidManifest.xml`、任何既有用例。

## 8. 附：本轮实测命令（可复现）

```bash
cd /d/Projects/androidapk
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11

# A. 构建与测试
./gradlew test --console=plain --rerun-tasks                       # BUILD SUCCESSFUL，585 执行 / 0 失败
./gradlew assembleDebug --console=plain --rerun-tasks              # APK 17,817,608 B
./gradlew :app:lintDebug --console=plain --rerun-tasks             # 0 errors / 23 warnings

# B. 变异测试（先红后绿）—— 每次跑完立即 cp 还原并 md5 校验
cp app/src/main/java/com/ebbinghaus/memo/ui/navigation/Screen.kt /tmp/Screen.kt.orig
#   把 Screen.kt:48 的 `by lazy(...) { setOf(...) }` 改回 `= setOf(...)`，以及/或
#   把 TopLevelDestination.kt:66 的 `by lazy(...) { listOf(...) }` 改回 `= listOf(...)`
./gradlew :app:testDebugUnitTest --tests "*NavigationRouteInitRegressionTest" --console=plain --rerun
#   M1 → 2 failed ／ M2 → 3 failed ／ M3 → 4 failed
cp /tmp/Screen.kt.orig app/src/main/java/com/ebbinghaus/memo/ui/navigation/Screen.kt   # 还原

# B. 测试独立性（单跑）
./gradlew :app:testDebugUnitTest --tests "*NavigationRouteInitRegressionTest" --console=plain --rerun   # 10/10，0.632s

# 结果解析（避免肉眼看控制台）
python - <<'PY'
import glob, xml.etree.ElementTree as ET
for d in ['app/build/test-results/testDebugUnitTest','app/build/test-results/testReleaseUnitTest','core/build/test-results/test']:
    fs=glob.glob(d+'/*.xml'); t=f=e=s=0
    for p in fs:
        r=ET.parse(p).getroot()
        t+=int(r.get('tests',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0)); s+=int(r.get('skipped',0))
    print(d, len(fs), t, f, e, s)
PY

# 字节码铁证
"$JAVA_HOME/bin/javap" -p -c app/build/tmp/kotlin-classes/debug/com/ebbinghaus/memo/ui/navigation/Screen.class
"$JAVA_HOME/bin/javap" -p    app/build/tmp/kotlin-classes/debug/com/ebbinghaus/memo/ui/navigation/Screen\$Companion.class

# 静态证据
grep -c "uses-permission" app/src/main/AndroidManifest.xml                                  # → 0
grep -rn "assertTrue(true)\|@Ignore\|@Disabled\|assumeTrue" app/src/test core/src/test      # → 0
grep -rn "sealed class" app/src core/src baselineprofile/src --include=*.kt                 # → 仅 Screen / TopLevelDestination
md5sum app/src/main/java/com/ebbinghaus/memo/ui/navigation/Screen.kt                        # → fca76cf1d34a6d74461f0f6ca14934a1
```

---

### 本轮最终路由判定：**`NoOne`**

- 源码无 bug：4 处修复经字节码 + 3 组变异测试 + 585 次用例执行验证，全部成立。
- 测试无 bug：10 条回归有真实区分度（变异态 2/3/4 条红），非恒真空转；独立性已验证。
- 无需返工。仅 1 项 P2（APK 体积数字更正）与 2 项 P3 建议，均不阻塞交付。
- **唯一红线：未做真机验证，须用户安装 debug APK 实测确认。**
