# 交互缺陷修复摘要（9 项：4×P0 + 5×P1）

- **任务类型**：BugFix（最小变更原则）
- **项目**：`D:\Projects\androidapk`（Kotlin 2.0.21 + Compose + M3 + Room，Gradle 8.10.2 / AGP 8.7.3 / JDK 17）
- **依据**：`design/INTERACTION_REVIEW.md`
- **日期**：2026-09-15
- **结论**：**IS_PASS: YES**（9 项全部修复，`./gradlew test` 全绿，零编译告警）

---

## 一、9 项缺陷逐条修复对照表

| 缺陷 | 根因 | 改动文件:行号 | 修复方式 | 新增测试 |
|---|---|---|---|---|
| **P0-1** Expanded 下无法新增（无 FAB） | `MemoListScreen` 的 FAB 未进入 Expanded 分支 | `ui/memolist/MemoListScreen.kt:144`（新壳）、`:168`（FAB）、`:564`（左 Pane 复用） | 抽出 `MemoListPageShell`（含 FAB），`MemoListScreen` 与 `MemoListDetailPane` 左 Pane 共用同一壳 | ❌（Compose UI，见"遗留"） |
| **P0-2** Expanded 下无法排序 | 排序入口/弹窗仅在 `MemoListScreen` | `MemoListScreen.kt:219`（`MemoListTopBar` 排序入口）、`:196`（`SortBottomSheet`） | 排序入口与弹窗下沉到共享壳，两分支共用 | ❌（同上） |
| **P0-3** Expanded 下返回键不退出多选 | 唯一 `BackHandler` 在 `MemoListScreen` | `ui/navigation/AppNavigation.kt:186-191`（上提）；`MemoListScreen.kt` 移除原 `BackHandler` | `BackHandler` 上提到导航宿主，`enabled = 在列表路由 && 多选态`，一处覆盖两条分支 | ❌（同上） |
| **P0-4** Expanded 下无「已选 N 条」 | 多选态 TopAppBar 仅在 `MemoListScreen` | `MemoListScreen.kt:224-231`（`MemoListTopBar` 多选态标题） | 多选态标题并入共享 `MemoListTopBar` | ❌（同上） |
| **P1-1** 「忘记」无障碍文案与算法不符 | 文案写「回到第 1 档」，实际为「回退 1 档」 | `ui/review/ReviewScreen.kt:71-76` | FORGET 改为「回退一档…」；REMEMBER 补充「第 6 档后进入 60 天长周期」；`RATING_SEMANTICS` 改 `internal` 以便单测 | ✅ `RatingSemanticsConsistencyTest`（5） |
| **P1-2** 多选态长按重置整个选择集 | UI 无条件触发 `onLongClick`；VM `OnEnterSelectionMode` 直接 `selectedIds = setOf(id)` | `MemoListScreen.kt:705`（UI）；`ui/memolist/MemoListViewModel.kt:402-419`（VM） | UI：多选态长按退化为切换勾选；VM：多选态下 `OnEnterSelectionMode` 防御性退化为切换（可单测覆盖） | ✅ `MemoListSelectionTest`（+1） |
| **P1-3** 「已选 N 条」Compact 下重复显示 | TopAppBar 与 `SelectionActionBar` 同时渲染数量 | `ui/component/SelectionActionBar.kt:44-92` | 去掉数量文本，仅保留四操作；`SpaceBetween`→`SpaceEvenly` 均匀分布，两断点均不空旷；数量统一由顶部栏承载 | ❌（纯布局，见"遗留"） |
| **P1-4** 批量操作失败无反馈且状态不一致 | 先改 UI 状态后落库、落库无 try/catch | `MemoListViewModel.kt:462-485`（批删）、`:496-519`（批标签）、`:282-318`（保存/立即删除）、`:338-380`（确认删除）、`:531`（`catching`） | 用 `catching{}`（等价 runCatching，但保留取消信号）包裹落库：**成功才改 UI 状态并提示成功，失败保留多选态/选择集并提示「操作失败，请重试」** | ✅ `MemoListBatchFailureTest`（5） |
| **P1-5** 编辑器关闭无未保存确认 | 关闭直接丢弃内容，无草稿 | `ui/memolist/MemoEditDialog.kt:69-118, 235-256`；新增 `ui/memolist/MemoEditorDraftState.kt` | 新增 `hasUnsavedEdits` 纯函数；点 ✕ 或返回键触发 `requestDismiss`，有改动弹 `AlertDialog`「放弃编辑？」，无改动直接关闭 | ✅ `MemoEditorDraftStateTest`（6） |

---

## 二、P0 修复要点与"不破坏 Compact/Medium"的保证

**根因**：`MemoListScreen`（含页面壳）与 `MemoListContent`（纯内容）职责切分后，Expanded 分支只用了纯内容。

**修法**：把 `MemoListScreen` 的 `Scaffold` 部分下沉为 **`MemoListPageShell`**（`Scaffold` + `MemoListTopBar` + `SortBottomSheet` + FAB），两条分支共用：

- 单列/Medium：`MemoListScreen` → `MemoListPageShell`（行为与改动前**逐字等价**）。
- Expanded：`MemoListDetailPane` 左 Pane → `MemoListPageShell`（新增入口）。
- `MemoListContent` **保持纯内容**，未内嵌 Scaffold（遵守 `AppScaffold.kt:40-42` 的 insets 约束）。
- 标题加 `maxLines=1` + `TextOverflow.Ellipsis`，适配左 Pane 360dp 宽度。
- 右 Pane 增加 `windowInsetsPadding(WindowInsets.statusBars)`，使两 Pane 内容顶端对齐（原先 Expanded 两 Pane 内容均在状态栏之下）。

**Compact/Medium 不受影响**：二者走 `MemoListScreen` 分支，最终渲染树与改动前一致（同一 `Scaffold`/`TopAppBar`/`FAB`/`SortBottomSheet`）。

---

## 三、测试结果（真实数字）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `app` 单元测试 | 102 | **119** |
| `core` 单元测试 | 31 | **31** |
| **合计** | **133** | **150** |
| 失败 / 错误 / 跳过 | 0 / 0 / 0 | **0 / 0 / 0** |

命令与结果：
```
$ export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11
$ ./gradlew test --console=plain --rerun-tasks
BUILD SUCCESSFUL in 4m 59s
```
（app-debug / app-release 各 119 条，core 31 条；均由 `build/test-results/**/*.xml` 统计得出）

新增用例（+17，只增不减，未删改任何既有断言）：
- `MemoListSelectionTest.longPress_whileAlreadyInSelectionMode_togglesInsteadOfResetting`（+1，P1-2）
- `MemoListBatchFailureTest`（+5，P1-4）
- `RatingSemanticsConsistencyTest`（+5，P1-1）
- `MemoEditorDraftStateTest`（+6，P1-5）

---

## 四、构建结果与告警数

```
$ ./gradlew assembleDebug --console=plain --rerun-tasks
BUILD SUCCESSFUL in 3m 24s
```
- **Kotlin/Java 编译告警数：0**（全日志 `grep -i warning` 命中 0 行）
- 唯一非告警提示：`stripDebugDebugSymbols` 的 `Unable to strip … libandroidx.graphics.path.so`（NDK strip 信息，非编译告警，改动前即存在）
- `compileSdk`/`targetSdk` 保持 **35**；**未新增任何依赖**；`AndroidManifest` **零新增权限**

---

## 五、一致性审查（全局，一次）

| 检查项 | 结论 |
|---|---|
| 跨文件 import 一致 | ✅ 移除 `MemoListScreen` 的 `BackHandler`、`SelectionActionBar` 的 `Spacer/width`；新增 import 全部被使用 |
| 接口契约 | ✅ `MemoListScreen` / `MemoListDetailPane` / `SelectionActionBar` 对外签名均未变，调用点零改动 |
| 数据流正确 | ✅ 两分支经同一 `MemoListPageShell` → `MemoListContent`，`selectedMemoId` 单列传 null、双窗格传选中项 |
| 无重复实现 | ✅ `MemoListContent` 每分支仅渲染一次；`SelectionActionBar` 不再重复渲染数量 |
| 既有语义守护 | ✅ `OnDeleteMemo`「立即删除、不弹确认」语义未变；`OnConfirmBatchDelete`/`OnBatchAddTag` 成功路径行为不变 |
| **IS_PASS** | **YES** |

---

## 六、遗留问题与未完成项（如实列出）

1. **无模拟器/真机**：本环境无法执行 instrumentation 验证。P0-1~P0-4 与 P1-3、P1-5 属 Compose UI 行为，仅经「代码审查 + 编译通过 + 两分支复用同一壳」保证，**未做真机点击验证**。建议在有设备的环境补一轮手工验收（Expanded ≥840dp：FAB / 排序 / 多选标题 / 返回键）。
2. **P0/P1-3/P1-5 无 JVM 单测**：项目未接入 Compose UI 测试（无 `androidTest` UI 用例），上述缺陷无法在 JVM 层断言；已为可测部分（P1-1/P1-2/P1-4/P1-5 的纯逻辑）补齐单测。
3. **轻微偏离建议**：P1-4 建议用 `runCatching`，实际用等价私有函数 `catching{}`，差异是**不吞掉 `CancellationException`**（保持结构化并发），已在代码注释说明。
4. **P0 附带改动**：右 Pane 新增 `statusBars` 内边距（对齐左 Pane）。这是修复左 Pane 顶部栏后为消除两 Pane 顶端错位而做的必要补充，属 Expanded 分支内的正向修正，未触及 Compact/Medium。
5. **P2 项未处理**：审计报告中的 10 项 P2（删除无撤销、回收站能力补齐、标签 Chip 死区、6 词上限提示等）不在本次范围，未改动。
