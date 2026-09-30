# QA 独立回归验证报告（9 项交互缺陷修复）

- **验证人**：Edward（严过关），QA 工程师
- **日期**：2026-09-15
- **范围**：`design/INTERACTION_REVIEW.md` 中 4×P0 + 5×P1 共 9 项缺陷的修复复核
- **立场**：**不采信工程师自述**，所有结论附独立实测证据（原始命令输出 / `文件:行号` / 用例名）
- **约束遵守**：未修改任何 `app/src/main` 业务源码；仅新增/修改 `app/src/test` 下的测试代码

---

## 1. 执行摘要

| 项 | 结论 |
|---|---|
| **总体判定** | **有条件 PASS**（9 项修复经代码路径逐条核实为真实修复；JVM 可测部分全绿；**Compose UI 交互部分因无设备未做真机验证**） |
| **实测测试数字** | 修复后（**未含**本次 QA 新增）：`app` **119 / 0 failed / 0 skipped（22 类）** ×2 变体，`:core` **31 / 0 / 0（4 类）** → 每变体 **150**。**与工程师自述「119 + 31 = 150」一致** |
| **含本次 QA 新增 25 条后** | `app` **144 / 0 / 0（26 类）** ×2 变体，`:core` **31 / 0 / 0** → 每变体 **175** |
| **构建** | `assembleDebug` **BUILD SUCCESSFUL**；`app-debug.apk` = **17,819,188 字节（≈16.99 MiB）** |
| **编译告警** | 主源码 `w: file://` = **0**（强制 `--rerun-tasks` 重编译实测）；全日志唯一 `w:` 为基础设施提示「multiple Kotlin daemon sessions」，非源码告警 |
| **源码功能缺陷** | **0 项** |
| **智能路由判定** | **NoOne**（无源码缺陷；唯一失败系我自己的测试代码 bug，已自修） |
| **与自述差异** | 数字全部一致；仅 `FIX_SUMMARY` 的 `AppNavigation.kt:186-191` 行号实际为 **188-192**（±2，无实质影响） |

---

## 2. A. 构建与测试（真实执行）

环境：Git Bash，`JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11`，`cd /d/Projects/androidapk`。

### A1. `./gradlew test --console=plain --rerun-tasks`

```
BUILD SUCCESSFUL in 4m 1s
```
按 `build/test-results/**/*.xml` 逐文件统计（排除 UP-TO-DATE 陈旧结果，已加 `--rerun-tasks`）：

| 变体 | 类数 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|---:|
| `app:testDebugUnitTest` | 22 | **119** | 0 | 0 | 0 |
| `app:testReleaseUnitTest` | 22 | **119** | 0 | 0 | 0 |
| `core:test` | 4 | **31** | 0 | 0 | 0 |

> **核对结论**：确为 **119（app）+ 31（core）= 150**，**0 失败 / 0 错误 / 0 跳过**。与工程师自述完全一致（非照抄，系我独立解析 XML 得出）。
> `./gradlew test` 实际执行 **119×2 + 31 = 269** 个用例。

### A2. `./gradlew :core:test --console=plain --rerun-tasks`

```
BUILD SUCCESSFUL in 33s
```
`core:test` = **31 passed / 0 failed / 0 skipped（4 类：AdversarialM1Stress 7 / EbbinghausScheduler 10 / MathTextPreprocessor 6 / RolloverEngine 8）**。`:core` 可独立运行，无 app 依赖。

### A3. `./gradlew assembleDebug --console=plain`

```
BUILD SUCCESSFUL
app/build/outputs/apk/debug/app-debug.apk = 17,819,188 bytes (≈16.99 MiB)
```
（另以 `assembleDebug --rerun-tasks` 强制重编译验证，同样 SUCCESSFUL。）

### A4. 编译告警数

强制重编译（`--rerun-tasks`）后：
```
grep -c 'w: file://'  →  0
grep -c '^w:'         →  0   （本次运行；另一次运行出现 1 条 "Detected multiple Kotlin daemon sessions"，属 Gradle 基础设施提示，非源码告警）
```
**核对结论**：主源码 **零编译告警** 属实。

---

## 3. B. 9 项缺陷逐条复核表

> 证据栏中的「✅ 用例名」= 我实测通过的独立用例；「代码级」= 经调用链/行号核实但无设备执行。

| # | 缺陷 | 是否真实修复 | 独立证据（调用链 / 文件:行号 / 用例） | 备注 |
|---|---|---|---|---|
| **P0-1** | Expanded 下无新增入口 | ✅ 是 | 调用链：`AppNavigation.kt:276` `useListDetail==true` → `MemoListDetailPane`（`AppNavigation.kt:278`）→ 左 Pane `MemoListPageShell`（`MemoListScreen.kt:564`）→ `Scaffold.floatingActionButton`（`MemoListScreen.kt:166-181`）。单列分支 `MemoListScreen`→同一壳（`MemoListScreen.kt:115`）。**两分支共用同一 FAB** | FAB 在多选态自动隐藏（`:167`）。真机未验证 |
| **P0-2** | Expanded 下无排序入口 | ✅ 是 | 排序 IconButton 在共享 `MemoListTopBar`（`MemoListScreen.kt:254-259`），`SortBottomSheet` 在共享壳（`MemoListScreen.kt:195-204`）；两分支经同一壳，均可达 | 代码级 |
| **P0-3** | Expanded 下返回键不退出多选 | ✅ 是 | `BackHandler` 上提至 `AppNavigation.kt:188-192`，`enabled = currentRoute==Screen.MemoList.route && memoListState.isSelectionMode`；`MemoListScreen` 原 `BackHandler` 已移除（全项目仅剩 1 处） | **误拦截回归专项**：非列表路由（Review/Settings/Trash/MemoDetail）时 `enabled=false`，不注册回调，**不误拦截**。`Screen.kt:7` 路由常量核实 |
| **P0-4** | Expanded 下无「已选 N 条」 | ✅ 是 | 标题并入共享 `MemoListTopBar`（`MemoListScreen.kt:224-242`），`maxLines=1` + `TextOverflow.Ellipsis`（`:230-231`），适配 360dp 左 Pane | 代码级 |
| **P1-1** | 「忘记」无障碍文案与算法不符 | ✅ 是 | `ReviewScreen.kt:71-77` FORGET 已改为「回退一档…」、REMEMBER 补「第 6 档后进入 60 天长周期」 | ✅ **`QaRatingSemanticsAlgorithmConsistencyTest`（5 条，我新增）**：逐档用 `EbbinghausScheduler` 实际输出核对全部 5 条文案 |
| **P1-2** | 多选态长按重置选择集 | ✅ 是 | **双保险**：UI `MemoListScreen.kt:705` `onLongClick = { if (isSelectionMode) onToggleCheck() else onLongClick() }`；VM `MemoListViewModel.kt:402-419` 多选态下退化为切换 | ✅ `MemoListSelectionTest.longPress_whileAlreadyInSelectionMode_togglesInsteadOfResetting` + 我的 `QaSelectionModeAdversarialTest`（5 条边界） |
| **P1-3** | 「已选 N 条」重复显示 | ✅ 是 | `SelectionActionBar.kt:44-94` 已移除数量文本，仅保留 4 操作；`Arrangement.SpaceEvenly`（`:66`） | 全项目 grep「已选」：UI 文本仅 `MemoListScreen.kt:228` **一处**（余为注释/`已选择目录`/`已选中`）。布局未真机验证 |
| **P1-4** | 批量操作失败无反馈且状态不一致 | ✅ 是 | `catching{}`（`MemoListViewModel.kt:531-538`）**显式 `throw cancellation`，确不吞 `CancellationException`**（工程师说法属实）。批删 `:462-485`、批标签 `:496-520`、保存 `:282-317`、确认删除 `:338-367` | ✅ `MemoListBatchFailureTest`（5）+ 我的 `QaBatchFailureRollbackAdversarialTest`（9，含保存失败） |
| **P1-5** | 编辑器关闭无未保存确认 | ✅ 是 | `requestDismiss`（`MemoEditDialog.kt:104-118`）同时接 **✕**（`:145`）与 **返回键**（`onDismissRequest`，`:121`，配 `dismissOnBackPress=true`）；`AlertDialog`（`:235-256`）。纯函数 `hasUnsavedEdits`（`MemoEditorDraftState.kt:17-27`） | ✅ `MemoEditorDraftStateTest`（6）+ 我的 `QaDraftStateBoundaryTest`（6） |

### B-补充：P1-4 成功/失败路径状态变更明细（第 13 项要求）

| 操作 | 成功路径改动的状态 | 失败路径改动的状态 |
|---|---|---|
| 批量删除 `OnConfirmBatchDelete` | 先 `isBatchDeleteDialogVisible=false`；成功后 `isSelectionMode=false`、`selectedIds=∅`，派发成功 Snackbar | 仅 `isBatchDeleteDialogVisible=false`；**保留 `isSelectionMode=true` 与原 `selectedIds`**，派发「操作失败，请重试」 |
| 批量打标签 `OnBatchAddTag` | 先 `isTagPickerVisible=false`；成功后同上退出多选，派发成功 Snackbar | 仅 `isTagPickerVisible=false`；**保留多选态与选择集**，派发失败提示 |
| 单条删除 `OnDeleteMemo` / `OnConfirmDeleteMemo` | 不弹窗/关弹窗，落库成功无提示（`OnConfirm` 有成功提示） | 数据不变，派发「删除失败，请重试」 |
| 保存 `OnSaveMemo` | `isEditorDialogVisible=false`、`editingMemo=null` | **编辑器与已输入内容保留**（状态不动），派发「保存失败，请重试」 |

> `selectedIds`/`isSelectionMode` 在失败时**被正确保留**（实测断言通过）。

---

## 4. C. 回归检查

### C1. 既有 133 条用例是否被削弱（**本次最重要检查**）

**核查方法（三重独立证据，因仓库无 `.git` 无法做字节级 diff，已如实说明）**：

1. **文件 mtime 取证**（修复时间窗 ≈ 12:54–13:05）：
   - 被修改的既有测试文件**仅 1 个**：`MemoListSelectionTest.kt`（12:55）；`FakeRepositories.kt`（12:55，**纯新增** 3 个失败开关）。
   - 其余既有测试文件 mtime **均 ≤ 12:20**（早于修复窗口），**未被触碰**：`MemoListViewModelTest`、`MemoListDeleteConfirmTest`、`MemoListSortTest`、`QaAdversarialDataTest`(12:20)、`QaAdversarialUiTest`(12:14) 等。
2. **数量算术闭合**：基线 app = 102（83 基线 + 19 QA 对抗）= 133 − core 31；现 app = 119。增量 **+17 = 3 个新文件（5+5+6）+ `MemoListSelectionTest` +1**。**若任一既有用例被删，总数无法精确闭合到 119**。
3. **断言强度检查**：全测试目录 grep `assertTrue(true)` / `@Ignore` / `@Disabled` / `assumeTrue` → **0 命中**；`MemoListSelectionTest` 现 8 条用例断言均为强断言（精确集合/精确 ID 序列），未放宽。

**结论**：**未发现既有用例被削弱/删除/放宽**。（**局限**：无 git 历史，无法排除「删 1 加 2」这类净增 1 的极端情形；但该文件 7 条原有语义用例均在且断言严格，风险极低。）

### C2. `OnDeleteMemo`「立即删除、不弹确认」语义保留

- 源码：`MemoListViewModel.kt:319-327` 保持立即软删；`MemoListUiEvent.kt:74-75` 与类注释 `:57-59` 声明该语义被既有单测依赖。
- 独立守护：我的 `QaBatchFailureRollbackAdversarialTest.immediateDeleteFailure_keepsDataAndEmitsFailure_withoutOpeningDialog` 断言「**不打开确认弹窗**（`isDeleteDialogVisible=false`）」。✅

### C3. 依赖 / 权限 / SDK

| 项 | 实测 | 结论 |
|---|---|---|
| 新增依赖 | `app/build.gradle.kts`(11:49)、`core/build.gradle.kts`(11:24)、根 `build.gradle.kts`(11:48)、`settings.gradle.kts`(11:49) **mtime 全部早于修复窗口** | **无新增依赖** ✅ |
| `AndroidManifest` 权限 | 源码 manifest `uses-permission` = **0**；合并后 debug manifest 含 1 条 `com.ebbinghaus.memo.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（`signature` 级，`androidx.core` 自动注入，非本次新增、非敏感） | 源码零权限 ✅（与上一轮 QA D2 一致） |
| SDK | `compileSdk=35`、`targetSdk=35`、`minSdk=26` | 仍为 35 ✅ |

### C4. 附带改动「右 Pane `windowInsetsPadding(statusBars)`」评估（第 18 项）

- 位置：`MemoListScreen.kt:580-586`，仅存在于 `MemoListDetailPane`。
- **只影响 Expanded**：`MemoListDetailPane` 仅在 `AppNavigation.kt:276` 的 `useListDetail==true`（= Expanded）分支调用；Compact/Medium 走 `MemoListScreen`，**不经过该代码路径** → 不产生额外留白 ✅。
- **无 insets 双重累加**：右 Pane 内容为 `MemoDetailPane`→`MemoDetailContent`（`MemoDetailScreen.kt:172`，**无 Scaffold**），故此处 `statusBars` 是右 Pane 唯一顶部 inset；`AppScaffold` 本身不用 Scaffold（`AppScaffold.kt:41-42` 注释），无嵌套 Scaffold。
- **轻微措辞偏差（🔵）**：该改动使「两 Pane 顶端（状态栏下沿）对齐」，但右 Pane 内容自状态栏下沿即开始，而左 Pane 内容在 TopAppBar（≈64dp）之下，**内容首元素并非严格齐平**。属预期（右 Pane 无顶栏），非缺陷。

---

## 5. D. 对抗性测试（我新增，已写入项目并跑通）

- 新增文件：`app/src/test/java/com/ebbinghaus/memo/ui/QaFixIndependentVerificationTest.kt`（4 个测试类，**25 条用例**）。
- 测试基建增强（**纯新增**）：`FakeRepositories.kt` 增加 `failOnCreateMemo` / `failOnUpdateMemo` 两个开关（默认 false，向后兼容）。
- 运行：`./gradlew testDebugUnitTest --rerun-tasks` → **144 passed / 0 failed**；全量 `./gradlew test --rerun-tasks` → **app 144×2 + core 31，全绿**。

| 类 | 用例 | 证伪目标 | 结果 |
|---|---|---|---|
| `QaSelectionModeAdversarialTest`（5） | `firstLongPress_notInSelectionMode_selectsOnlyThatId` | 首次长按仍为单选（不破坏既有语义） | ✅ |
| | `longPress_inSelectionMode_notSelected_addsPreservingOthers` | 边界：不含该条 → 追加而非重置 | ✅ |
| | `longPress_inSelectionMode_alreadySelected_removesOnlyThat` | 边界：已含该条 → 仅取消该条 | ✅ |
| | `longPress_inSelectionMode_withEmptySelection_addsInsteadOfStayingEmpty` | 边界：选择集为空 → 新增 | ✅ |
| | `longPress_repeatedOnSameId_isIdempotentToggleNotReset` | 同 id 连续长按 = 加/减切换 | ✅ |
| `QaBatchFailureRollbackAdversarialTest`（9） | `batchDeleteFailure_rollsBackSelectionAndMode_keepsData_emitsFailure` | 批删失败：回滚 UI + 保留选择 + 不落库 + 提示 | ✅ |
| | `batchDeleteSuccess_clearsSelectionExitsMode_emitsSuccess` | 批删成功路径不变 | ✅ |
| | `batchAddTagFailure_rollsBackSelectionAndMode_keepsData_emitsFailure` | 批标签失败同上 | ✅ |
| | `batchAddTagSuccess_clearsSelectionExitsMode_emitsSuccess` | 批标签成功 + 去重合并 | ✅ |
| | `immediateDeleteFailure_keepsDataAndEmitsFailure_withoutOpeningDialog` | `OnDeleteMemo` 立即语义 + 失败提示 | ✅ |
| | `confirmDeleteFailure_closesDialogButKeepsDataAndEmitsFailure` | 确认删除失败数据不变 | ✅ |
| | `saveNewMemoFailure_keepsEditorOpenAndEmitsFailure` | **保存（新增）失败保留编辑器** | ✅ |
| | `saveEditedMemoFailure_keepsEditorAndEditingMemo` | **保存（编辑）失败保留编辑器与 editingMemo** | ✅ |
| | `saveBlankContent_isRejectedSilentlyWithoutFailureSnackbar` | 空白内容为前置校验，不误报「失败」 | ✅ |
| `QaRatingSemanticsAlgorithmConsistencyTest`（5） | `semanticsMap_coversExactlyAllFiveRatings` | 5 条文案全覆盖 | ✅ |
| | `forgetSemantics_rollsBackOneStage_forEveryStage` | **FORGET 逐档 = 回退一档**（含第1档保持、长周期→第6档）；且不含「第 1 档」 | ✅ |
| | `rememberSemantics_advancesOneStage_andEnters60DayCycleAfterStage6` | **REMEMBER 逐档 = 前进一档；第 6 档→60 天长周期** | ✅ |
| | `vagueAndDefaultSemantics_keepStageAndInterval_forEveryStage` | 模糊/已复习保档保间隔 | ✅ |
| | `skipSemantics_defersExactlyOneDayWithoutStageChange` | 跳过保档 + 固定顺延 1 天 | ✅ |
| `QaDraftStateBoundaryTest`（6） | `identicalInputs_isFalse` / `allEmpty_isFalse` | 无改动 → 不打扰 | ✅ |
| | `whitespaceOnlyDifference_isTrue` | **仅空格差异视为有改动** | ✅ |
| | `tagsOnlyChange_isTrue` / `notesOnlyChange_isTrue` / `contentClearedToEmpty_isTrue` | 各字段单独变更边界 | ✅ |

> **对抗结论**：25 条攻击**未能证伪任何修复**。测试过程中出现的唯一失败（`saveNewMemoFailure...`）经定位为**我自己的测试代码 bug**（并行编辑导致 `createMemo` 失败开关未写入 → 新增反而成功 → 编辑器被关闭），已自行修复；**与业务源码无关**。

---

## 6. 智能路由判定

### 判定：**`NoOne`**

**依据**：
1. **无源码缺陷**：9 项修复经调用链与行号逐条核实为**真实修复**；25 条独立对抗用例全部通过，未能证伪。
2. **无测试代码缺陷遗留**：唯一失败系**我新增测试代码**的编辑遗漏（并行工具调用竞争导致一处 guard 丢失），已按规则**自行修复**并复跑全绿 —— 属 QA 自修范畴，**不涉及 `app/src/main`**。
3. **未通过项均为环境限制**：6 项涉及 Compose UI 交互（P0-1/2/3/4、P1-3、P1-5 的点击/布局），**无设备无法执行 instrumentation**，如实声明「未验证」，非实现缺陷。

---

## 7. 缺陷清单

> **源码功能缺陷 = 0。** 以下为提示/覆盖缺口类，非阻断。

| ID | 严重度 | 标题 | 期望 vs 实际 | 证据 |
|---|---|---|---|---|
| **Q1** | 🟠（覆盖缺口，非缺陷） | Compose UI 交互未经真机验证 | 期望：P0-1/2/3/4、P1-3、P1-5 的点击/返回/布局有设备级证据；实际：无模拟器/真机，**仅代码级 + 编译通过 + 两分支复用同壳** | 本环境无 `adb` 设备；与工程师自述「遗留」一致 |
| **Q2** | 🔵（提示） | `FIX_SUMMARY` 行号轻微偏差 | 期望：`AppNavigation.kt:186-191`；实际：`188-192`（±2）。`MemoListTopBar` 排序入口自述 `:219` 实为函数体起始行 | `AppNavigation.kt:188` |
| **Q3** | 🔵（提示） | 「Manifest 零权限」在合并产物层不成立 | 期望：APK 无 `uses-permission`；实际：合并 manifest 含 1 条 `signature` 级 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（`androidx.core` 自动注入，**非本次新增、非敏感**）。源码 manifest 确为 0 | `merged_manifest/debug/.../AndroidManifest.xml` |
| **Q4** | 🔵（提示） | 右 Pane insets 对齐措辞 | 自述「两 Pane 内容顶端对齐」；实际仅「状态栏下沿对齐」，内容首元素因左 Pane 有 TopAppBar 而错开 ≈64dp（预期行为） | `MemoListScreen.kt:580-586` |

**严重度分布**：🔴 0 ｜ 🟠 1（覆盖缺口）｜ 🟡 0 ｜ 🔵 3。**功能缺陷 = 0。**

---

## 8. 遗留与未验证项（如实列出）

| # | 项 | 原因 | 后续动作 |
|---|---|---|---|
| L1 | **P0-1/P0-2/P0-3/P0-4 的 Compose 交互未真机验证** | 无模拟器/真机 | 有设备后于 Expanded（≥840dp）实测：FAB 新增、排序弹窗、多选标题、返回键退出多选 |
| L2 | **P1-3 布局（Compact 底栏 / Rail 顶部不空旷）未真机验证** | 同上 | 有设备后目视/截图核对 `SpaceEvenly` 分布 |
| L3 | **P1-5 点 ✕ / 返回键两条路径的 UI 行为未真机验证** | 同上 | 有设备后验证「有改动弹确认 / 无改动直关」 |
| L4 | **P0-3 误拦截回归仅在代码级排除** | 无设备 | 有设备后于 Review/Settings/Trash 页按返回键，确认不被列表 BackHandler 拦截 |
| L5 | **既有 133 条用例「未被削弱」缺字节级 diff** | 仓库无 `.git`，无历史副本 | 建议纳入版本控制后补 diff 审计（本次已用 mtime + 数量算术 + 断言强度三重证据替代） |
| L6 | **`assembleRelease` 体积未复核** | 本次仅要求 `assembleDebug` | 如需可补跑（上一轮 QA 记录 release ≈1,726,594 B） |

---

## 9. 复现命令（本机实测）

```bash
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11
cd /d/Projects/androidapk

# A1 全量（强制重跑，排除 UP-TO-DATE）：app 119×2 + core 31
./gradlew test --console=plain --rerun-tasks

# A2 core 独立
./gradlew :core:test --console=plain --rerun-tasks

# A3 构建 + 体积
./gradlew assembleDebug --console=plain
stat -c '%s bytes' app/build/outputs/apk/debug/app-debug.apk   # 17819188

# A4 告警（强制重编译）
./gradlew assembleDebug --console=plain --rerun-tasks 2>&1 | grep -c 'w: file://'   # 0

# D 含 QA 新增对抗用例
./gradlew testDebugUnitTest --console=plain --rerun-tasks      # app 144 / 0 failed
```
