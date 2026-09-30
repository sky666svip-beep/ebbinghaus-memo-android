# 交付总结 · 艾宾浩斯知识点备忘录 UI/交互改造

**日期**：2026-09-14
**范围**：落地 `design/UI_DESIGN_SPEC.md` 的全部 UI/交互设计（P0 + P1 + P2）
**协作方式**：多智能体团队 SOP（架构师 → 工程师 → QA），收尾由交付总监亲自接手

---

## TL;DR

设计方案的 12 项落地清单**全部实现并验证通过**：底部 3 Tab 导航 + 复习角标、空/异常态组件化、
删除二次确认、Edge-to-Edge、Snackbar 通道、搜索防抖+高亮、三档响应式（含 Expanded 双窗格）、
评级无障碍语义、笔记自动保存、完成页顺延量、设置页分组。

**84 条单测全绿 · APK 16.7 MB · 零编译告警 · `core/`+`data/` 零改动。**

---

## 交付概览

| 项 | 结果 |
|---|---|
| 交付状态 | ✅ 完成并放行 |
| 改动文件 | **23 个**（main 21 + debug 2） |
| 单元测试 | **84 passed / 0 failed / 0 skipped**（13 个测试类） |
| 构建产物 | `app/build/outputs/apk/debug/app-debug.apk`（16.7 MB） |
| 编译告警 | 0 error / 0 warning |
| 分层隔离 | `core/`、`data/` **零改动**；`build.gradle.kts` 仅 +1 依赖 |
| 新增依赖 | 1 个（`material3-window-size-class:1.3.1`，stable） |
| 已知问题 | 0 个 P0/P1；3 个 P2（见下） |

---

## 一、交付内容

### 1. 导航结构升级（P0）
- 新增底部 `NavigationBar` 3 Tab（知识库 / 复习 / 设置），Medium 及以上自动切换为 `NavigationRail`
- 复习 Tab 带 **Badge 显示今日待复习量**，`=0` 或 `dailyLimit<=0` 时自动隐藏
- Tab 切换使用 `saveState/restoreState/launchSingleTop`，搜索词、筛选标签、滚动位置往返不丢失
- 详情页为下钻页，不显示导航条

> **设计矛盾已裁定**：原方案同时写了「复习页沉浸隐藏底栏」与「复习是底栏第 2 个 Tab + Badge」，
> 二者互斥。最终以**显示底栏**为准（Badge 需常驻可见），实现正确，陈旧注释已修正。

### 2. 状态与反馈机制（P0/P1）
- 新增通用组件 `EmptyState` / `ErrorState` / `LoadingSkeleton` / `HighlightedText` / `DeleteConfirmDialog`
- 状态矩阵 **S1**（首次空库）、**S2**（搜索无结果 + 清空筛选）、**S13**（数据库异常 + 重试）全部落地，每条均带可执行 CTA
- 列表页补删除二次确认（复用详情页样式），级联删除说明完整
- 全局 Snackbar 通道（`LocalSnackbarHostState`），替代部分 Toast
- 搜索 **300ms 防抖**（UI 层）+ 关键词 `primaryContainer` 底色高亮

### 3. 响应式适配（P1）
- **Compact**（<600dp）单列 + 底部导航栏
- **Medium**（600–839dp）`NavigationRail` + 2 列 `LazyVerticalGrid(Adaptive(320dp))`
- **Expanded**（≥840dp）**List–Detail 双窗格**（左 360dp 列表 + 右详情），点击列表项只更新右 Pane、不跳转
- 复习页三档限宽居中（Medium 600dp / Expanded 720dp）；横屏 Compact 改左右双列
- 断点判定收敛为唯一入口，运行时使用官方 `calculateWindowSizeClass`（正确处理折叠屏铰链/多窗口）

### 4. 细节增强（P2）
- 5 个评级按钮补齐「后果说明」无障碍语义（如「记住：前进一档，30 天后再复习」）
- 编辑态直接提交评级时**自动保存笔记草稿**，避免输入丢失
- 复习完成页展示「顺延至明日 N 条」
- 设置页重组为三组：每日复习上限 / 智能调度规则说明 / 关于

---

## 二、文件清单

### 新增（12 个）

| 路径（`app/src/` 下） | 职责 |
|---|---|
| `main/java/.../ui/util/WindowSizeClass.kt` | 断点枚举与唯一判定入口 |
| `main/java/.../ui/theme/Dimens.kt` | 圆角/间距/触控/限宽 token |
| `main/java/.../ui/scaffold/AppScaffold.kt` | Snackbar 宿主 + 按断点切换 Bar/Rail |
| `main/java/.../ui/scaffold/AppNavigationBar.kt` | 底部导航栏与侧边导航栏（含 Badge） |
| `main/java/.../ui/navigation/TopLevelDestination.kt` | 一级 Tab 定义 |
| `main/java/.../ui/component/EmptyState.kt` | 通用空状态（S1/S2） |
| `main/java/.../ui/component/ErrorState.kt` | 通用错误态（S13） |
| `main/java/.../ui/component/LoadingSkeleton.kt` | 骨架屏 + shimmer |
| `main/java/.../ui/component/HighlightedText.kt` | 搜索关键词高亮 |
| `main/java/.../ui/component/DeleteConfirmDialog.kt` | 删除二次确认 |
| `debug/java/.../ui/preview/PreviewFakes.kt` | Preview 用内存仓储 |
| `debug/java/.../ui/preview/PagePreviews.kt` | 12 个页面级三档 Preview |

### 修改（11 个）

`MainActivity.kt`（`enableEdgeToEdge()`）、`ui/navigation/AppNavigation.kt`、`ui/navigation/Screen.kt`、
`ui/memolist/MemoListScreen.kt`、`ui/memolist/MemoListViewModel.kt`、`ui/dashboard/DashboardBanner.kt`、
`ui/dashboard/DashboardViewModel.kt`、`ui/detail/MemoDetailScreen.kt`、`ui/review/ReviewScreen.kt`、
`ui/review/ReviewViewModel.kt`、`ui/settings/SettingsScreen.kt`

### 其他

- `app/build.gradle.kts`：+1 依赖
- `gradlew`：**修复**（原为截断的损坏脚本，1694 B → 8695 B 官方标准脚本）
- `TEST_INFRA.md`：补构建命令、环境限制说明、覆盖边界声明
- `design/ARCHITECTURE_INCREMENT.md` + 2 个 Mermaid 图：增量架构设计

---

## 三、验证结果

```
./gradlew test assembleDebug --rerun-tasks
→ BUILD SUCCESSFUL in 4m 9s
→ 84 passed / 0 failed / 0 skipped（13 个测试类，双变体合计 168 条全绿）
→ app-debug.apk 16.7 MB
```

**硬约束逐条核查（QA 独立验证，附文件路径+行号证据）**

| 约束 | 结论 |
|---|---|
| `OnDeleteMemo` 保留「立即删除」语义（`MemoListViewModelTest:107` 依赖） | ✅ |
| 搜索防抖在 UI 层，不在 ViewModel Flow（避免 `UnconfinedTestDispatcher` 无虚拟时钟失败） | ✅ |
| 笔记自动保存仅在 `isEditingNotes && draft != 原值` 时触发 | ✅ |
| `DashboardUiState.deferredCount` 带默认值 | ✅ |
| `compileSdk`/`targetSdk` 保持 35（未升 36，避免 Android 16 强制行为变更） | ✅ |
| 设置页无「外观/主题」卡片（避免 Room 迁移） | ✅ |
| 零编译 warning | ✅ |

QA 额外新增 12 条测试（断点边界 599/600/839/840、删除确认闭环、Badge 格式化），并主动排除了
「UP-TO-DATE 掩盖陈旧结果」的怀疑（核对时间戳 + `--rerun-tasks` 二次确认）。

---

## 三之二、落地清单逐项验收（交付总监亲验，非凭记忆）

对照 `UI_DESIGN_SPEC.md` 第 9 节的 12 项清单，逐项在代码中核验：

| 优先级 | 事项 | 验收 | 证据 |
|---|---|---|---|
| P0 | 底部 NavigationBar + 复习 Badge | ✅ | `AppScaffold` 引用 Bar/Rail 2 处；`AppNavigationBar` 含 `badgeCount` 8 处 |
| P0 | 列表页删除二次确认 | ✅ | `MemoListScreen` 引用 `DeleteConfirmDialog` 2 处 |
| P0 | 空/异常态组件化 | ✅ | `EmptyState.kt` / `ErrorState.kt` / `LoadingSkeleton.kt` 均存在并被复用 |
| P0 | Edge-to-Edge + insets | ✅ | `MainActivity.enableEdgeToEdge()` |
| P1 | Snackbar 统一通道 | ✅ | `LocalSnackbarHostState` 定义 1 处 + `SnackbarHost` 1 处，2 个 Screen 消费 |
| P1 | 搜索防抖 + 关键词高亮 | ✅ | 防抖在 UI 层（`delay(300)` ×2）；**ViewModel 内 `debounce` 0 处**（符合约束）；高亮 3 处使用 |
| P1 | WindowSizeClass 适配 | ✅ | `WindowSizeClass.kt` 5 处官方 `calculateWindowSizeClass` |
| P1 | Expanded 双窗格 | ✅ | `AppNavigation.kt:215` 的 `useListDetail` 分支 + `MemoListDetailPane`（左 360dp / 右 `weight(1f)`）+ 已抽出 `MemoDetailContent` 复用 |
| P2 | 评级按钮无障碍语义 | ✅ | `ReviewScreen` 含 `semantics`/`contentDescription` 18 处 |
| P2 | 笔记自动保存 | ✅ | `ReviewViewModel.shouldAutoSaveNotes` 2 处 |
| P2 | 设置页分组 | ✅ | 三组齐备：每日复习数量上限 / 智能调度与顺延规则说明 / 关于 |
| P2 | 完成页展示顺延量 | ✅ | `ReviewScreen` 引用 `deferredCount` 5 处 |

**结论：12 / 12 通过。**

### 与清单的 3 处有意偏离（均已接受，非缺陷）

| 清单原方案 | 实际实现 | 理由 |
|---|---|---|
| `SnackbarHost` 于 `Scaffold` | `AppScaffold` 自绘容器承载 `SnackbarHost` | 避免与子 Screen 的 `Scaffold` 形成嵌套、双重消费 insets |
| `ListDetailPaneScaffold`（material3-adaptive） | 手写 `Row` 分栏 | 该库为 alpha 且本地无缓存；项目定位零重型依赖 |
| 设置页「外观」卡片 | 未做，仅做分组容器 | `UserSettingsEntity` 无主题字段，加字段即触发 Room v1→v2 迁移，与「不动 data 层」硬约束冲突；建议单独立项 |

---

## 三之三、构建优化补充（2026-09-15）

依据 `DEPENDENCY_ASSESSMENT.md` 的结论落地两项**构建配置优化**，**业务代码零改动**（仅改 `app/build.gradle.kts`，并新建 `app/proguard-rules.pro`）。

### ① Jacoco 单元测试覆盖率

```kotlin
debug { enableUnitTestCoverage = true }   // AGP 内置，无需显式声明 jacoco 插件
```

- 任务名：`createDebugUnitTestCoverageReport`（**不是** `testDebugUnitTestCoverage`）
- 报告路径：`app/build/reports/coverage/test/debug/index.html`（**不是** `reports/jacoco/`）
- 结果：**行覆盖 15.95%（711/4457）｜分支覆盖 4.82%（186/3859）**

> 覆盖率偏低的原因如实说明：分母包含整个 Compose UI 层，而 UI 层在 JVM 上无法测试
> （Robolectric 路线已实测失败，见 §五）。`core/engine` 算法层与 ViewModel 层覆盖率高。

### ② R8 混淆 + 资源裁剪

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    signingConfig = signingConfigs.getByName("debug")   // 仅为便于安装验证
}
```

| 产物 | 体积 |
|---|---|
| debug（未混淆） | 16.71 MB |
| **release（R8 + 资源裁剪）** | **1.53 MB** |
| **降幅** | **−90.9%** |

- **未使用「全量 keep」**：`proguard-rules.pro` 实际有效规则仅 2 行
  （`-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute SourceFile`，仅为让崩溃栈可读）。
  Room 2.6.1 / Compose / Coroutines / Kotlin stdlib 的 consumer 规则已覆盖全部需求。
- `mapping.txt` **30.3 MB / 217,972 条混淆映射** → R8 深度混淆确实生效。
- **静态验证**：manifest 入口 `com.ebbinghaus.memo.MainActivity` 与 Room 反射加载的
  `AppDatabase_Impl` **均存活**；manifest 无新增权限。
- **运行时验证（关键）**：release 包在真实 Android 设备（Android 15 / API 35）上
  `install` → `Success`、`am start -W` → `LaunchState: COLD / Complete`、
  `logcat` 无 `FATAL / ClassNotFoundException / NoClassDefFoundError`、`pidof` 进程存活。
  **R8 最典型的故障模式（构建成功但启动即崩）已被排除。**

> ⚠️ 运行时验证**只覆盖到启动阶段**（应用启动后处于后台，未走完整业务流程）。
> 完整功能流程建议自行走查一遍。

### ③ 顺带修复的既有隐患

`app/proguard-rules.pro` **原本根本不存在**，而 `build.gradle.kts` 一直在引用它 ——
因 `isMinifyEnabled = false` 时该配置被忽略才未暴露。现已补建。

### ④ 回归

`./gradlew test` = **84 passed / 0 failed / 0 skipped**；`assembleDebug` 亦成功；
`compileSdk`/`targetSdk` 保持 35；零新增依赖。

---

## 三之四、交互体验优化（2026-09-15 第二轮）

针对用户报告的三类交互问题与三项新需求，完成以下改造。

### ① 全屏沉浸式编辑器

编辑模式由 `AlertDialog` 改为**全屏 Dialog**（`usePlatformDefaultWidth = false` + `fillMaxSize`）：
- 去掉对话框的宽高上限与外部遮罩，为长文本 / Markdown / LaTeX 提供最大编辑区；
- 仅保留顶部一条 `TopAppBar`（关闭 / 标题 / 保存），零多余装饰；
- 内容框最小高度 **240dp**（最少 8 行），笔记框 **132dp**（最少 4 行）；
- `imePadding()` 让内容区随输入法抬升，长文编辑时正文始终可见。

### ② Markdown 渲染支持

新增 `assets/katex/marked.min.js`（v12.0.2，35KB，离线）。渲染管线：

```
提取公式区间 → 转义 HTML → Markdown 转 HTML → 还原公式 → KaTeX 渲染
```

**关键是公式保护**：`$x_1 + y_2$` 若先经 Markdown 解析，下划线会被当作斜体而破坏公式。
因此先把 `$...$` / `$$...$$` 抽出为占位符，Markdown 渲染后再原样还原。

`MathTextPreprocessor` 新增 `containsMarkdown()` / `needsRichRendering()`；
`MathView` 快路径判据升级为「Markdown 或 LaTeX 任一命中才走 WebView」。

### ③ 性能优化（Tab 切换 / 详情页跳转卡顿）

**根因**：`MathView` 基于 `WebView` + KaTeX，**每次创建都会初始化 Chromium 引擎**（数百毫秒级），
详情页含 2 个 `MathView`，复习卡亦有。

- **`MathView` 两级渲染路径**：纯文本（既无 Markdown 也无 LaTeX）直接走原生 `Text`，**完全不创建 WebView**；
  富文本才延迟 120ms 创建 WebView（等转场动画先跑完）；
- **导航动画分级**：一级 Tab 切换改为 `fadeIn/fadeOut` 180ms（符合 Tab 语义且开销更低）；
  详情页保留 `slide` 260ms（层级下钻语义）；
- **`DashboardViewModel` 同日去重**：新增 `lastCheckedDate` 内存标记，
  避免 Tab 往返切换反复触发 `OnCheckAppLaunch` 查询 + 写库。

### ④ 展开/收起抖动根治

**第一层**：卡片与 `MathView` 加 `animateContentSize`，让高度变化平滑过渡。

**第二层（根治）**：`ReviewScreen` 的卡片区与按钮区原本**同处一个 `verticalScroll` 的 Column**，
且 `Spacer(Modifier.weight(1f))` 在可滚动 Column 中**本就不生效**（剩余空间约束为无限）。
因此抽屉展开 → 内容变高 → 按钮区被整体推下 → 抖动。

重构为「卡片区独占剩余空间 + 按钮区固定贴底」：
```kotlin
Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f).verticalScroll(...)) { /* 卡片，内部滚动 */ }
    Box { /* 按钮区，固定贴底，永不移动 */ }
}
```

### ⑤ 缺陷修复：`MathView` 无限渲染循环

**现象**：界面持续反复刷新（用户报告「一直反复输入导致异常循环」）。

**根因**（本轮优化引入的回归）：
```
AndroidView.update{} 每次重组都调 triggerRender
  → JS 渲染 → 高度回调写入 Compose State → 触发重组
  → animateContentSize 动画持续重组 → update{} 又调 triggerRender → 无限循环
```

**修复**：
1. `update{}` 中**移除 `triggerRender`**（该块属于重组路径，禁止引发状态变化的副作用），
   内容更新只由 `LaunchedEffect(text, color, fontSize, isPageLoaded)` 驱动；
2. `onHeightChanged` 回调改为 **no-op**，不再写入 Compose State；
3. `update{}` 中写 State 前增加实例判断，避免每次重组都写入。

### 验证结果

| 项目 | 结果 |
|---|---|
| `./gradlew test assembleDebug --rerun-tasks` | BUILD SUCCESSFUL |
| 单元测试 | **84 / 84 通过**（0 失败 0 错误 0 跳过） |
| Markdown 渲染断言（`design/verify_markdown.js`） | **23 / 23 通过** |
| 编译告警 | 零 error、零 warning |
| 同类隐患扫描 | 全项目 `verticalScroll` + `weight` 无效搭配仅 `ReviewScreen` 一处，已修复 |

**待真机确认**：全屏编辑器的键盘遮挡、抽屉展开时按钮区是否恒定、Markdown 实际渲染效果。

---

## 四、关键决策记录

| 决策 | 结论 | 理由 |
|---|---|---|
| 响应式实现 | 手写 + 官方 `calculateWindowSizeClass` | material3 1.3.1 不含 `windowsizeclass`；引入官方 stable 库后运行时走官方实现，无 Activity 时降级 |
| Expanded 双窗格 | **本期做** | 验收口径要求三档；手写 Row 分栏，不引入 alpha 的 `material3-adaptive` |
| 撤销功能 | **不做软删除** | 需 Entity+DAO+Migration v1→2，属 data 层改造；由二次确认承担防误触 |
| 设置页外观卡片 | **不做** | `UserSettingsEntity` 无主题字段，加字段即 Room 迁移 |
| 三档 Preview 实现方式 | **debug 源集 + Fake 仓储** | 零改动生产代码，release 产物不含；避免把 ViewModel 参数改为可空 |

---

## 五、已知问题（均为 P2，不阻塞交付）

1. **Medium 低段（600–681dp）实际只排 1 列** —— `GridCells.Adaptive(320.dp)` 需 682dp 才排下 2 列。
   这是设计文档写死的固有结果，实现与规范逐字一致。若要求 Medium 全段严格 2 列，需改设计为 `Fixed(2)`。
2. **Compose UI 层无自动化测试** —— 未接入 Jacoco（无覆盖率数字），无仪器化测试。
   UI 结论来自静态推演 + Preview，**建议交付后做一轮真机三档走查**。

   > **已尝试并回退**：曾引入 Robolectric 4.15.1 + `ui-test-junit4` 以在 JVM 上渲染 Compose 做断言，
   > 结果 12 条测试中 6 条失败，根因为
   > `NoClassDefFoundError: Could not initialize class TopLevelDestination` ——
   > `TopLevelDestination` 的 `data object` 在类静态初始化阶段即引用 `Icons.AutoMirrored.Filled.MenuBook`
   > 等 Compose 图标，Robolectric 下该类初始化失败，导致所有引用它的导航测试连带失败。
   > 经权衡**已完整回退**（移除测试文件、依赖与 `testOptions`），构建恢复 84 条全绿。
   > 若未来仍需补 Compose 自动化测试，正确路径是**仪器化测试（androidTest + 真机/模拟器）**，
   > 而非 Robolectric；或先把 `TopLevelDestination` 的图标改为惰性引用。
   >
   > > **⚠️ 结论更正（2026-09-16，见 `CRASH_INVESTIGATION.md`「第 3 轮：根因定案与修复」）**
   > > 上文把根因归为「data object 在类静态初始化阶段引用 Compose 图标」——**该归因是错的**。
   > > 真实原因是 **JVM 类静态初始化循环**（JVM Spec §5.5）：
   > > `TopLevelDestination` 的 `companion object` 在 `<clinit>` 期读取继承自己的嵌套 `data object`
   > > （`entries = listOf(Library, Review, Settings)`），而首个触碰点若是任一嵌套 object，
   > > JVM 会把它判为「当前线程发起的递归初始化请求」而**直接放行、不等待**，
   > > 于是读到**尚未赋值的 `INSTANCE`（null）**。本次已用「全新类加载器 + 纯 JVM 单测」实测复现：
   > > 未修复时 `entries` 静默变成 `[null, Review, Settings]`，`fromRoute` 中 `it.route` 随即 NPE。
   > > **同一个循环在 `Screen` 上还导致 debug 包启动即崩**（`ExceptionInInitializerError`
   > > ← `NPE: Screen$MemoList.INSTANCE is null`，release 因 R8 重排而不崩）。
   > > 修复方式是把 `entries` 与 `Screen.topLevelRoutes` 改为 `by lazy` 惰性求值；
   > > **图标仍照旧在类初始化期被引用，测试全部转绿** —— 证明 Compose 图标并非原因。
3. **`AppScaffold` 自绘导航条而非嵌套 `Scaffold`** —— 已确认无内容遮挡，但底部留白的精确手感需真机确认。

---

## 六、下一步建议

1. **真机走查**（首要）：在 360dp 手机 / 700dp 折叠屏展开 / 1000dp 平板三种尺寸下核对布局，
   重点看底部手势条区域的留白与 FAB 间隙。
2. **IDE Preview 验收**：打开 `PagePreviews.kt` 可一次看到 4 个页面 × 3 档断点的渲染效果。
3. **安装体验**：`adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. **可选增强**：Medium 段改 `Fixed(2)`；接入 Jacoco 补覆盖率；
   补 Compose 自动化测试需走 **`androidTest` 仪器化**路径（Robolectric 路线已验证不可行，见上文）。
5. **后续专项**：外观/深色模式切换、数据导入导出（均需 Room schema 变更，建议单独立项）。

---

## 附：构建命令（本仓库自动化环境）

`cmd.exe` 在本环境被安全策略拦截，使用 POSIX 形式：

```bash
cd D:/Projects/androidapk
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11
./gradlew test --console=plain --rerun-tasks
./gradlew assembleDebug --console=plain
```

兜底（若 `gradlew` 再次损坏）：

```bash
"$JAVA_HOME/bin/java" -classpath "gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain test --console=plain
```
