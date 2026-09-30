# 测试资产整理报告（TEST_CLEANUP_REPORT）

- **执行人**：寇豆码（Engineer）
- **执行日期**：2026-09-18
- **依据**：`design/TEST_AUDIT.md`（QA 严过关审计报告）§3.1 / §3.3 / §3.4 / §4.1 / §5 / §6
- **范围**：**A 档（Qa\* 语义化归并）+ B 档（删确证冗余/空转）+ 反回归雷修复 + 2 条空转断言修复**
- **未做**：C 档（12 条疑似）、D 档（关闭 release 变体单测）—— 等用户拍板
- **验收命令**：`./gradlew test --console=plain --rerun-tasks`
- **结论**：**BUILD SUCCESSFUL**；唯一 JVM 用例 **316 → 271**，全绿（0 失败 / 0 错误 / 0 跳过）

---

## 0. 备份（删除不可逆，先行核验）

本项目**无 git 仓库**（`git status` = `not a repository`），删除不可逆，故**改动前**已将全部测试源码树整体复制到源码树之外：

| 项 | 值 |
|---|---|
| **备份路径** | `D:\Projects\androidapk\.workbuddy-ai\backup-tests-20260918\` |
| **文件总数** | **40**（39 个 `.kt` + 1 个 `android/util/Log.java` 测试替身） |
| **总字节数** | **322,447 bytes**（`du -sb` 与逐文件求和一致） |
| 子目录 | `app-src-test/`（283,272 B）、`app-src-androidTest/`（3,759 B）、`core-src-test/`（35,416 B） |
| 结构 | 保留原目录结构（`app/src/test` → `app-src-test`，`app/src/androidTest` → `app-src-androidTest`，`core/src/test` → `core-src-test`） |

> 备份在**任何改动之前**完成，为改动前原始快照。

---

## 1. 🔴 反回归雷修复（优先级最高）

### 1.1 源码缺陷修复

**文件**：`app/src/main/java/com/ebbinghaus/memo/ui/detail/MemoDetailViewModel.kt`

| 项 | 位置 |
|---|---|
| 新增诊断 tag 常量 | `:18` `private const val LOG_TAG = "EbbinghausLaunch"` |
| 新增 import | `android.util.Log`、`kotlinx.coroutines.CancellationException` |
| 补边界的方法 | `:65` `fun loadMemoDetails()`（`try` 包裹 `:67–77`；`catch(CancellationException)` `:78–80`；`catch(Exception)` `:81–85`） |

**改动前**（无边界）：

```kotlin
fun loadMemoDetails() {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        val memo = memoRepository.getMemoById(memoId)
        val task = reviewRepository.getTaskByMemoId(memoId)
        _uiState.update { it.copy(isLoading = false, memo = memo, reviewTask = task) }
    }
}
```

**改动后**（按项目已建立的统一模式，与 `DashboardViewModel` / `ReviewViewModel` / `SettingsViewModel` 风格一致）：

```kotlin
fun loadMemoDetails() {
    viewModelScope.launch {
        try {
            _uiState.update { it.copy(isLoading = true) }
            val memo = memoRepository.getMemoById(memoId)
            val task = reviewRepository.getTaskByMemoId(memoId)
            _uiState.update { it.copy(isLoading = false, memo = memo, reviewTask = task) }
        } catch (cancellation: CancellationException) {
            throw cancellation              // 取消信号必须原样抛出（顺序在前，绝不被吞）
        } catch (e: Exception) {
            Log.e(LOG_TAG, "备忘录详情加载失败，已降级为可用状态", e)
            _uiState.update { it.copy(isLoading = false) }   // 降级为可用 UI 状态，不白屏
        }
    }
}
```

- 仅此**一处**生产代码改动；未触碰其他 `app/src/main` 源码。
- 现有 `MemoDetailViewModelTest`（3 条）成功路径断言不受影响（成功路径 `isLoading=false` 行为不变）。

### 1.2 测试翻转（去掉「断言缺陷存在」）

**原用例**：`qa/QaRound2FinalVerificationTest.kt:290` `probe_isCapable_ofDetectingEscape_memoDetailInitStillUnbounded`
（阳性对照：`assertTrue(escapes.isNotEmpty())` —— **把缺陷存在写成契约**，缺陷一旦修复必然变红）

**现用例**：`ui/StartupCoroutineBoundaryTest.kt:414` `memoDetailInit_dependencyThrows_isContainedByBoundary_andLogged`

| | 翻转前 | 翻转后 |
|---|---|---|
| 名称 | `probe_isCapable_ofDetectingEscape_memoDetailInitStillUnbounded`（暗示缺陷存在） | `memoDetailInit_dependencyThrows_isContainedByBoundary_andLogged`（语义化，表达「边界生效」） |
| 断言 | `assertTrue("阳性对照：…必须被检出", escapes.isNotEmpty())` + `escapes.any { it is IllegalStateException }` | `assertTrue("必须就地收敛，不得逃逸", escapes.isEmpty())` + `assertEquals(1, Log.eCount)` + `assertEquals("EbbinghausLaunch", Log.lastTag)` + `assertTrue(Log.lastThrowable is IllegalStateException)` |
| 语义 | 要求缺口继续存在（反回归） | 守护「边界生效（不逃逸）+ 非静默（有日志）」两事实 |
| 结果 | 修复源码后**必红** | **变绿** ✅（实测通过） |

> 改动是**加强**而非削弱：新断言同时覆盖「不逃逸」与「有诊断日志」两个观测点。

---

## 2. A 档 —— `Qa*` 文件按功能语义化归并

**目标**：6 个 `Qa*` 文件（95 条）全部消解为**语义化命名**文件；命名表达「守护什么」，不含 `Qa` / 轮次号 / 日期等交付过程痕迹。

### 2.1 归并映射表（旧 → 新，含用例数迁移）

| 现文件 | 现测试类（用例数） | → 目标文件（语义化） | 迁入 / 删除 |
|---|---|---|---|
| `qa/QaP2IndependentVerificationTest.kt` | `QaTokenizerBoundaryTest`(7) | `core/.../SearchQueryTokenizerTest.kt` | 迁入 1（`veryLongSingleWord_isNotSplit`）／删重 6 |
| | `QaP2_10_TokenizerContractTest`(3) | **`data/MemoRepositoryTokenizerContractTest.kt`（新建）** | 迁入 3（跨层契约，唯一覆盖） |
| | `QaP2_10_RepositoryTokenizerParityAdversarialTest`(4) | 同上（合并为一个类） | 迁入 4 |
| | `QaUndoTokenAdversarialTest`(6) | `ui/MemoListUndoTest.kt` | 迁入 1（`undoTokenReplay_afterSuccessfulUndo_isIgnored`）／删重 5 |
| | `QaTrashStateMachineAdversarialTest`(4) | `ui/TrashViewModelTest.kt` | 迁入 1（`batchDeleteFailure_keepsSelectionAndItems`）／删重 3 |
| | `QaAppendTagBoundaryTest`(3) | `ui/MemoEditorTagInputTest.kt` | 迁入 0（3 条全在 B 档删除） |
| | `QaDailyLimitBranchTest`(3) | `ui/ReviewDailyLimitTest.kt` | 迁入 1（`negativeLimit_isTreatedAsPaused`）／删重 2 |
| `ui/QaFixIndependentVerificationTest.kt` | `QaSelectionModeAdversarialTest`(5) | `ui/MemoListSelectionTest.kt` | 迁入 2／删重 3 |
| | `QaBatchFailureRollbackAdversarialTest`(9) | `ui/MemoListBatchFailureTest.kt` | 迁入 4／删重 5 |
| | `QaRatingSemanticsAlgorithmConsistencyTest`(5) | `ui/RatingSemanticsConsistencyTest.kt` | 并入强版 5，删功能弱版 5（净 −5） |
| | `QaDraftStateBoundaryTest`(6) | `ui/MemoEditorDraftStateTest.kt` | 并入强版/独有 4，删功能弱版 4（净 −4） |
| `data/QaAdversarialDataTest.kt` | `ExportCodecAdversarialTest`(5) | `data/ExportCodecRoundTripTest.kt` | 迁入 5（其中 1 条空转改为实质断言） |
| | `TrashRetentionBoundaryAdversarialTest`(2) | `data/trash/TrashRetentionTest.kt`(1) + `data/MemoRepositoryWriteContractTest.kt`(1) | 拆分迁入（纯函数归纯函数文件；仓储级 cutoff 归写路径契约文件） |
| | `SoftDeleteRestoreAdversarialTest`(7) | **`data/MemoRepositoryWriteContractTest.kt`（新建）** | 迁入 7（仓储级写路径，唯一覆盖） |
| `ui/QaAdversarialUiTest.kt` | `MemoSortDeterminismAdversarialTest`(3) | `ui/MemoListSortTest.kt` | 迁入 3 |
| | `BatchTagMergeAdversarialTest`(2) | `ui/MemoListSelectionTest.kt` | 迁入 2 |
| `qa/QaCrashHardeningVerificationTest.kt` | `QaCrashHardeningVerificationTest`(8) | `ui/StartupCoroutineBoundaryTest.kt`(4, Dashboard checkAppLaunch) + `ui/MemoListViewModelTest.kt`(3, observeAllTags) | 拆分迁入 7／删重 1 |
| `qa/QaRound2FinalVerificationTest.kt` | `QaRound2FinalVerificationTest`(13) | `crash/CrashLoggerTest.kt`(2) + `ui/StartupCoroutineBoundaryTest.kt`(4) | 拆分迁入 6／删重 7（含 1 条反回归雷**改造**） |

### 2.2 归并后文件结构（`Qa*` 文件数 = 0）

```
app/src/test/java/com/ebbinghaus/memo/
├── crash/CrashLoggerTest.kt                      (11)   ← 吸收 QaRound2 的 CrashLogger 对抗用例
├── data/
│   ├── DataLayerContractAdversarialTest.kt       (11)   （保留，未改名）
│   ├── ExportCodecRoundTripTest.kt               (13)   ← 吸收 ExportCodecAdversarialTest
│   ├── MemoRepositoryTokenizerContractTest.kt    ( 7)   ★新建（跨层 tokenizer 契约）
│   ├── MemoRepositoryWriteContractTest.kt        ( 8)   ★新建（仓储写路径契约）
│   ├── SchemaV2ContractTest.kt                   ( 4)   （保留）
│   └── trash/TrashRetentionTest.kt               ( 8)   ← 吸收 remainingDays 边界
├── ui/
│   ├── MemoListSelectionTest.kt                  (12)   ← 吸收 Selection/BatchTagMerge
│   ├── MemoListBatchFailureTest.kt               ( 9)   ← 吸收 BatchFailureRollback
│   ├── MemoListSortTest.kt                       ( 7)   ← 吸收 SortDeterminism
│   ├── MemoListUndoTest.kt                       ( 9)   ← 吸收 UndoToken
│   ├── MemoListViewModelTest.kt                  (11)   ← 吸收 observeAllTags 加固
│   ├── MemoEditorDraftStateTest.kt               ( 8)   ← 吸收 DraftStateBoundary（强版）
│   ├── MemoEditorTagInputTest.kt                 ( 8)   （B 档后无迁入）
│   ├── RatingSemanticsConsistencyTest.kt         ( 5)   ← 换为全档遍历强版
│   ├── ReviewDailyLimitTest.kt                   ( 5)   ← 吸收 DailyLimitBranch
│   ├── StartupCoroutineBoundaryTest.kt           (17)   ← 吸收 CrashHardening + Round2 的边界用例
│   └── TrashViewModelTest.kt                     (27)   ← 吸收 TrashStateMachine
└── （qa/ 目录已空）
core/src/test/kotlin/com/ebbinghaus/memo/core/
├── AdversarialM1StressTest.kt                    ( 6)   （保留，未改名；删 1 条）
└── SearchQueryTokenizerTest.kt                   (17)   ← 吸收超长单词边界
```

> **保留文件**：`data/DataLayerContractAdversarialTest.kt`(11) 与 `core/AdversarialM1StressTest.kt`(6) 按 QA §3.5 / §6 结论**保留**。二者文件名不含 `Qa` / 轮次号 / 日期（`Adversarial` 描述的是测试手法而非交付批次），故**未强制改名**（QA 原文亦为「酌情」）。

### 2.3 A 档覆盖守恒

- 95 条 `Qa*` 用例：**迁入语义化文件 50 条 + B 档删除 45 条**（无一条因「归并」而丢失）。
- 归并过程中**未新增任何依赖**，未改任何断言语义（除 B 档删除与 §1/§4 的指定改造）。

---

## 3. B 档 —— 删除确证冗余 / 空转用例

**实际删除 45 条**（QA §3.1 共列 46 条；其中 #45 按本轮「第 4 件」要求**改为实质断言而非删除**，详见 §3.2）。

### 3.1 已删除清单（逐条：原 `文件:行号` + 用例名）

**A. 搜索切分（重复覆盖 → 保留 `SearchQueryTokenizerTest` 同名断言）**

| # | 原位置 | 用例名 |
|---|---|---|
| 1 | `qa/QaP2IndependentVerificationTest.kt:120` | `fullWidthSpace_isSeparator` |
| 2 | `qa/QaP2IndependentVerificationTest.kt:125` | `newlineAndTab_areSeparators` |
| 3 | `qa/QaP2IndependentVerificationTest.kt:130` | `mixedWhitespaceKinds_collapseIntoOneSeparator` |
| 4 | `qa/QaP2IndependentVerificationTest.kt:138` | `emptyAndSeparatorOnlyInput_yieldZeroWords` |
| 5 | `qa/QaP2IndependentVerificationTest.kt:146` | `exactlySixWords_noHint_sevenWords_oneIgnored` |
| 6 | `qa/QaP2IndependentVerificationTest.kt:159` | `activeKeywords_isPrefixOfTokenize_andCappedAtSix` |

**B. 撤销令牌 / 回收站状态机（重复覆盖）**

| # | 原位置 | 用例名 |
|---|---|---|
| 7 | `qa/QaP2IndependentVerificationTest.kt:334` | `oldTokenAfterSecondDelete_isIgnored` |
| 8 | `qa/QaP2IndependentVerificationTest.kt:356` | `unknownToken_isIgnored` |
| 9 | `qa/QaP2IndependentVerificationTest.kt:380` | `expiredEntry_undoReportsExpiredWithoutCrash` |
| 10 | `qa/QaP2IndependentVerificationTest.kt:394` | `batchUndo_restoresEntireBatchOnly` |
| 11 | `qa/QaP2IndependentVerificationTest.kt:410` | `immediateDelete_neverEmitsUndoSnackbar` |
| 12 | `qa/QaP2IndependentVerificationTest.kt:445` | `selectAll_coversAllVisibleItems` |
| 13 | `qa/QaP2IndependentVerificationTest.kt:452` | `batchRestore_hasNoConfirmGate_andExitsSelection` |
| 14 | `qa/QaP2IndependentVerificationTest.kt:463` | `batchDelete_requiresConfirmBeforePhysicalDelete` |

**C. 标签追加（重复覆盖 → 保留 `MemoEditorTagInputTest`）**

| # | 原位置 | 用例名 |
|---|---|---|
| 15 | `qa/QaP2IndependentVerificationTest.kt:502` | `duplicateAcrossAnySeparator_isNoOp` |
| 16 | `qa/QaP2IndependentVerificationTest.kt:510` | `blankTagOrBlankInput_isHandled` |
| 17 | `qa/QaP2IndependentVerificationTest.kt:518` | `appendedResult_roundTripsThroughEditorParser` |

**D. dailyLimit 分支（重复覆盖 → 保留 `ReviewDailyLimitTest`）**

| # | 原位置 | 用例名 |
|---|---|---|
| 18 | `qa/QaP2IndependentVerificationTest.kt:547` | `zeroLimit_yieldsEmptyBatchAndCompleted` |
| 19 | `qa/QaP2IndependentVerificationTest.kt:563` | `positiveLimit_withEmptyQueue_staysCelebration` |

**E. 多选 / 写失败回滚（重复覆盖 → 保留 `MemoListSelectionTest` / `MemoListBatchFailureTest`）**

| # | 原位置 | 用例名 |
|---|---|---|
| 20 | `ui/QaFixIndependentVerificationTest.kt:78` | `firstLongPress_notInSelectionMode_selectsOnlyThatId` |
| 21 | `ui/QaFixIndependentVerificationTest.kt:87` | `longPress_inSelectionMode_notSelected_addsPreservingOthers` |
| 22 | `ui/QaFixIndependentVerificationTest.kt:101` | `longPress_inSelectionMode_alreadySelected_removesOnlyThat` |
| 23 | `ui/QaFixIndependentVerificationTest.kt:167` | `batchDeleteFailure_rollsBackSelectionAndMode_keepsData_emitsFailure` |
| 24 | `ui/QaFixIndependentVerificationTest.kt:202` | `batchAddTagFailure_rollsBackSelectionAndMode_keepsData_emitsFailure` |
| 25 | `ui/QaFixIndependentVerificationTest.kt:241` | `immediateDeleteFailure_keepsDataAndEmitsFailure_withoutOpeningDialog` |
| 26 | `ui/QaFixIndependentVerificationTest.kt:256` | `confirmDeleteFailure_closesDialogButKeepsDataAndEmitsFailure` |
| 27 | `ui/QaFixIndependentVerificationTest.kt:186` | `batchDeleteSuccess_clearsSelectionExitsMode_emitsSuccess` |

**F. 评级文案一致性（#28–#32：QA 判定「Qa 版为超集，保留强版、删功能弱版」）**

> 按 QA §3.1 尾注「**保留强版本**」的合并方向执行：**删除功能文件中的弱版**，把全档遍历的强版迁入功能文件。

| # | 删除对象（功能弱版） | 保留并迁入的强版（超集） |
|---|---|---|
| 28 | `ui/RatingSemanticsConsistencyTest.kt:99` `allRatings_haveNonBlankSemantics` | `semanticsMap_coversExactlyAllFiveRatings`（额外断言文案条目数==枚举数） |
| 29 | `ui/RatingSemanticsConsistencyTest.kt:24` `forgetSemantics_matchesRollbackOneStageAlgorithm` | `forgetSemantics_rollsBackOneStage_forEveryStage`（全档遍历） |
| 30 | `ui/RatingSemanticsConsistencyTest.kt:49` `rememberSemantics_mentionsLongTermCycle` | `rememberSemantics_advancesOneStage_andEnters60DayCycleAfterStage6`（全档遍历） |
| 31 | `ui/RatingSemanticsConsistencyTest.kt:65` `vagueAndDefaultSemantics_keepCurrentStage` | `vagueAndDefaultSemantics_keepStageAndInterval_forEveryStage`（全档遍历） |
| 32 | `ui/RatingSemanticsConsistencyTest.kt:85` `skipSemantics_defersOneDayWithoutStageChange` | `skipSemantics_defersExactlyOneDayWithoutStageChange`（全档遍历） |

**G. 草稿未保存判定（#33–#36：同上「保留强版、删弱版」）**

| # | 删除对象（功能弱版） | 保留并迁入的版本 |
|---|---|---|
| 33 | `ui/MemoEditorDraftStateTest.kt:17` `noChange_returnsFalse`（与 Qa `identicalInputs_isFalse` ≡，保留 1 条） | 等价（保留 `noChange_returnsFalse`） |
| 34 | `ui/MemoEditorDraftStateTest.kt:73` `newMemoUntouched_returnsFalse`（与 Qa `allEmpty_isFalse` ≡，保留 1 条） | 等价（保留 `newMemoUntouched_returnsFalse`） |
| 35 | `ui/MemoEditorDraftStateTest.kt:59` `tagsChanged_returnsTrue` | `tagsOnlyChange_isTrue`（超集：含「无标签→新增标签」） |
| 36 | `ui/MemoEditorDraftStateTest.kt:45` `notesChanged_returnsTrue`（与 Qa `notesOnlyChange_isTrue` ≡，保留 1 条） | 等价（保留 `notesChanged_returnsTrue`） |

**H. 启动边界 / CrashLogger（重复覆盖）**

| # | 原位置 | 用例名 | 保留的对照 |
|---|---|---|---|
| 37 | `qa/QaCrashHardeningVerificationTest.kt:235` | `dashboard_initCollector_dependencyThrows_isContainedByBoundary_notEscaping` | `StartupCoroutineBoundaryTest.kt:95` `dashboardInit_dependencyThrows_isContainedByBoundary_andLogged` |
| 38 | `qa/QaRound2FinalVerificationTest.kt:191` | `crashLogger_handler_mustInvokeOriginalHandler_andWriteCrash` | `CrashLoggerTest.kt:136` |
| 39 | `qa/QaRound2FinalVerificationTest.kt:212` | `crashLogger_handler_whenWriteFails_stillInvokesOriginalHandler` | `CrashLoggerTest.kt:170` |
| 40 | `qa/QaRound2FinalVerificationTest.kt:240` | `crashLogger_retention_deletesOldestBeyondLimit` | `CrashLoggerTest.kt:68` |
| 41 | `qa/QaRound2FinalVerificationTest.kt:258` | `crashLogger_clear_removesEverything_andEmptyStateIsSafe` | `CrashLoggerTest.kt:103` |
| 42 | `qa/QaRound2FinalVerificationTest.kt:310` | `dashboardInit_dependencyThrows_isContained_andLogged_independentProbe` | `StartupCoroutineBoundaryTest.kt:95` |
| 43 | `qa/QaRound2FinalVerificationTest.kt:354` | `reviewInit_dependencyThrows_setsVisibleErrorState_andDoesNotEscape` | `StartupCoroutineBoundaryTest.kt:153` |
| 44 | `qa/QaRound2FinalVerificationTest.kt:379` | `settingsInit_dependencyThrows_setsVisibleError_andDoesNotEscape` | `StartupCoroutineBoundaryTest.kt:219` |

**I. 领域算法（重复覆盖）**

| # | 原位置 | 用例名 | 保留的对照 |
|---|---|---|---|
| 46 | `core/.../AdversarialM1StressTest.kt:245` | `testAdversarial_negativeDailyLimit_gracefulFallbackToZero` | `RolloverEngineTest.kt:51` `testDailyLimitNegative_handledAsZero` |

### 3.2 我判定「不删」的条目及理由（独立判断权）

| # | QA 原判定 | 我的处理 | 理由 |
|---|---|---|---|
| **45** | `data/QaAdversarialDataTest.kt:238` `decode_isPure_doesNotDependOnPriorEncode` —— 判定「**空转**，建议删除」 | **不删，改为实质断言**（迁入 `data/ExportCodecRoundTripTest.kt:242`，改名 `decode_isStateless_independentOfPriorCalls`） | 本轮任务**第 4 件**明确要求「`QaAdversarialDataTest.kt:241` 附近 …… **改为实质断言**」——该指令与「删除」冲突时，**以更具体的第 4 件为准**。空转的根因是断言写法（同入参自比），而非测试主题；主题（decode 无隐藏状态）本身有守护价值，改为可证伪的实质断言**严格优于删除**。故保留并强化。 |

> ⚠️ **数字说明**：因 #45 由「删除」改为「保留+强化」，**实际删除 = 45 条**（非 46）。唯一 JVM 用例数 `316 → 271`（而非 QA 预估的 270）。若用户确要求「严格 271 总数（含 androidTest）」，仅需再把 `ExportCodecRoundTripTest.decode_isStateless_independentOfPriorCalls` 删除一条即可回到 271；本轮按第 4 件指令保留。
>
> 其余 45 条，我**认同** QA 判定（均为「同一事件序列 + 同一状态断言」的逐断言等价或超集/子集关系，删除后断言集合不变），故全部删除。

---

## 4. 空转断言修复（2 条）

### 4.1 `decode` 恒真断言（`data/QaAdversarialDataTest.kt:241` → `data/ExportCodecRoundTripTest.kt:242`）

**修复前**（恒真：同一纯函数、同入参、调两次再自比）：

```kotlin
@Test
fun decode_isPure_doesNotDependOnPriorEncode() {
    val json = codec.encode(adversarialSnapshot())
    assertEquals(codec.decode(json).getOrThrow(), codec.decode(json).getOrThrow())  // 恒真，无守护力
}
```

**修复后**（实质断言：先解码一个「被污染」载荷，再重解原载荷，须仍等于原始对象 —— 若存在跨调用隐藏状态/缓存，此断言必失败）：

```kotlin
@Test
fun decode_isStateless_independentOfPriorCalls() {
    val json = codec.encode(adversarialSnapshot())
    val polluted = adversarialSnapshot().copy(appVersion = "polluted-9.9.9")
    codec.decode(codec.encode(polluted)).getOrThrow()          // 污染一次
    assertEquals(
        "decode 必须无跨调用隐藏状态：污染一次后重解原载荷仍须逐字段等于原始对象",
        adversarialSnapshot(),
        codec.decode(json).getOrThrow()
    )
}
```

### 4.2 迁移测试恒真断言（`app/src/androidTest/.../MigrationTest.kt:95`）

**修复前**：`assertTrue(true)`

**修复后**（实质断言，核验迁移未篡改数据与关联）：

```kotlin
// 5. 迁移后数据整体无损：memo 内容与 task 关联均保持
db.query("SELECT content FROM knowledge_memos WHERE id = 1").use { cursor ->
    cursor.moveToFirst()
    assertEquals("迁移不得篡改 memo 内容", "知识点A", cursor.getString(0))
}
db.query("SELECT memoId FROM review_tasks WHERE id = 10").use { cursor ->
    cursor.moveToFirst()
    assertEquals("迁移不得破坏 task 与 memo 的关联", 1, cursor.getInt(0))
}
```

> 该文件需真机执行，本机**仅编译不运行**；已用 `./gradlew :app:compileDebugAndroidTestKotlin` 验证编译通过（BUILD SUCCESSFUL）。
> 全仓复扫：`assertTrue(true)` / `assertFalse(false)` / 自比 `assertEquals(x, x)` —— **均为 0 条**。

---

## 5. 真实测试数字（实测，未伪造）

### 5.1 清理前 / 清理后对比

| 口径 | 清理前 | 清理后 | 变化 |
|---|---:|---:|---:|
| `app/src/test` `@Test` | 269 | **224** | −45 |
| `core/src/test` `@Test` | 47 | **47** | 0 |
| **唯一 JVM 用例（`./gradlew test` 执行）** | **316** | **271** | **−45** |
| `app/src/androidTest` `@Test`（需真机，不执行） | 1 | 1 | 0 |
| **`@Test` 总数（含 androidTest）** | **317** | **272** | **−45** |

### 5.2 `./gradlew test --console=plain --rerun-tasks` 实测结果

```
BUILD SUCCESSFUL in 19s
57 actionable tasks: 57 executed
real  0m20.043s
```

| 测试任务 | 用例数 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `:app:testDebugUnitTest` | 224 | 0 | 0 | 0 |
| `:app:testReleaseUnitTest` | 224 | 0 | 0 | 0 |
| `:core:test` | 47 | 0 | 0 | 0 |
| **唯一用例（debug + core）** | **271** | **0** | **0** | **0** |

> 测试结果解析自 `app/build/test-results/**/*.xml`。`:app:compileDebugAndroidTestKotlin` 亦 BUILD SUCCESSFUL。

### 5.3 各文件用例数迁移明细

| 文件 | 前 | 后 | Δ |
|---|---:|---:|---:|
| `crash/CrashLoggerTest` | 9 | 11 | +2 |
| `data/DataLayerContractAdversarialTest` | 11 | 11 | 0 |
| `data/ExportCodecRoundTripTest` | 8 | 13 | +5 |
| `data/MemoRepositoryTokenizerContractTest` ★新建 | — | 7 | +7 |
| `data/MemoRepositoryWriteContractTest` ★新建 | — | 8 | +8 |
| `data/SchemaV2ContractTest` | 4 | 4 | 0 |
| `data/trash/TrashRetentionTest` | 7 | 8 | +1 |
| `qa/QaCrashHardeningVerificationTest` ✗删 | 8 | 0 | −8 |
| `qa/QaP2IndependentVerificationTest` ✗删 | 30 | 0 | −30 |
| `qa/QaRound2FinalVerificationTest` ✗删 | 13 | 0 | −13 |
| `ui/DashboardViewModelTest` | 7 | 7 | 0 |
| `ui/MemoDetailViewModelTest` | 3 | 3 | 0 |
| `ui/MemoEditorDraftStateTest` | 6 | 8 | +2 |
| `ui/MemoEditorTagInputTest` | 8 | 8 | 0 |
| `ui/MemoListBatchFailureTest` | 5 | 9 | +4 |
| `ui/MemoListDeleteConfirmTest` | 6 | 6 | 0 |
| `ui/MemoListSelectionTest` | 8 | 12 | +4 |
| `ui/MemoListSortTest` | 4 | 7 | +3 |
| `ui/MemoListUndoTest` | 8 | 9 | +1 |
| `ui/MemoListViewModelTest` | 8 | 11 | +3 |
| `ui/QaAdversarialUiTest` ✗删 | 5 | 0 | −5 |
| `ui/QaFixIndependentVerificationTest` ✗删 | 25 | 0 | −25 |
| `ui/RatingSemanticsConsistencyTest` | 5 | 5 | 0 |
| `ui/ReviewDailyLimitTest` | 4 | 5 | +1 |
| `ui/ReviewViewModelTest` | 7 | 7 | 0 |
| `ui/SettingsViewModelTest` | 5 | 5 | 0 |
| `ui/StartupCoroutineBoundaryTest` | 9 | 17 | +8 |
| `ui/TrashViewModelTest` | 26 | 27 | +1 |
| `ui/navigation/NavigationRouteInitRegressionTest` | 10 | 10 | 0 |
| `ui/scaffold/NavigationBadgeTest` | 2 | 2 | 0 |
| `ui/util/WindowSizeClassTest` | 4 | 4 | 0 |
| `core/AdversarialM1StressTest` | 7 | 6 | −1 |
| `core/EbbinghausSchedulerTest` | 10 | 10 | 0 |
| `core/MathTextPreprocessorTest` | 6 | 6 | 0 |
| `core/RolloverEngineTest` | 8 | 8 | 0 |
| `core/SearchQueryTokenizerTest` | 16 | 17 | +1 |
| `androidTest/MigrationTest` | 1 | 1 | 0 |
| **合计** | **317** | **272** | **−45** |

---

## 6. 「必须保留」清单核验（QA §6，逐条未被削弱）

| 资产（QA §6） | QA 期望 | 现状 | 核验 |
|---|---:|---:|---|
| `NavigationRouteInitRegressionTest` | 10 | **10** | ✅ 未动 |
| `MemoRepositoryTokenizerContractTest`（原 `QaP2_10_TokenizerContractTest` + Parity） | 7 | **7** | ✅ 语义化改名保留（`data/MemoRepositoryTokenizerContractTest.kt`） |
| `StartupCoroutineBoundaryTest` | 9 | **17** | ✅ 只增不减（吸收 Dashboard/Detail 边界） |
| `CrashLoggerTest` | 9 | **11** | ✅ 只增不减 |
| `DataLayerContractAdversarialTest` | 11 | **11** | ✅ 未动 |
| `MemoRepositoryWriteContractTest`（原 `SoftDeleteRestoreAdversarialTest`） | 7 | **8** | ✅ 语义化改名保留（+1 仓储级 cutoff 边界） |
| `TrashViewModelTest` | 26 | **27** | ✅ 只增不减 |
| `SchemaV2ContractTest` | 4 | **4** | ✅ 未动 |
| `MigrationTest` | 1 | **1** | ✅ 保留（空转断言已改实质，待真机执行） |
| `RatingSemanticsConsistencyTest` | 5 | **5** | ✅ 数量不变，断言换为全档遍历强版 |
| `MemoListUndoTest` | 8 | **9** | ✅ 只增不减 |

> **无一条「必须保留」资产被削弱**；其中 5 个文件（Startup / CrashLogger / WriteContract / TrashVM / Undo）用例数**净增**。

---

## 7. 硬约束核验

| 约束 | 状态 |
|---|---|
| `./gradlew test` 全绿 | ✅ BUILD SUCCESSFUL；271 唯一用例，0 失败/0 错误/0 跳过 |
| 不为凑数削弱保护（§6 清单） | ✅ 逐条核验（见 §6），只增不减 |
| 零新增依赖 | ✅ 未改 `build.gradle.kts` / `libs.versions.toml` |
| `AndroidManifest` 零新增权限 | ✅ 未触碰 |
| `compileSdk` / `targetSdk` 保持 35 | ✅ 未触碰 |
| 不做 Room schema 变更 | ✅ 未触碰 |
| 不启动模拟器 | ✅ androidTest 仅编译（`compileDebugAndroidTestKotlin` 通过），未运行 |
| 生产代码改动范围 | ✅ 仅 `MemoDetailViewModel.kt` 一处（§1.1） |

---

## 8. 遗留（待用户拍板，本轮**未做**）

- **C 档（12 条疑似冗余）**：`design/TEST_AUDIT.md` §3.4 #1–#12。这些用例断言**强于**其功能对应物（更严数据集 / 更多注入点），QA 建议**默认不删**。本轮已将其**迁入语义化文件保留**（未删除），例如：
  - `fourSorts_onShuffledData_produceExactIdSequences`、`sorting_neverChangesMembership_orSoftDeletedLeakIn` → `ui/MemoListSortTest.kt`
  - `batchAddTag_mixedSelection_dedupesAndSkipsExisting` → `ui/MemoListSelectionTest.kt`
  - `roundTrip_adversarialPayload_fieldByFieldEqual`、`versionTooNew_isRejectedWithoutPartialParse`、`corruptedJson_variants_allRejected` → `data/ExportCodecRoundTripTest.kt`
  - `remainingDays_atDay29_30_31` → `data/trash/TrashRetentionTest.kt`
  - `memoList_observeAllTags_happyPath_stillPopulatesTags` → `ui/MemoListViewModelTest.kt`
  - `crashLogger_report_containsFullStackAndNestedCauseChain`、`crashLogger_writeCrash_neverThrows_whenDirUnusable` → `crash/CrashLoggerTest.kt`
  - `longPress_repeatedOnSameId_isIdempotentToggleNotReset` → `ui/MemoListSelectionTest.kt`
  - `allInitBoundaries_cancellation_isRethrown_notSwallowed` → `ui/StartupCoroutineBoundaryTest.kt`
- **D 档（关闭 release 变体单测）**：QA §5-D 建议在 `app/build.gradle.kts` 增加
  `beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }`（全量耗时 92s → ~29s，覆盖零损失）。本轮**未实施**。

---

## 9. 变更文件清单

**生产代码（1）**
- `app/src/main/java/com/ebbinghaus/memo/ui/detail/MemoDetailViewModel.kt`（补异常边界）

**新建测试文件（2）**
- `app/src/test/java/com/ebbinghaus/memo/data/MemoRepositoryTokenizerContractTest.kt`（跨层 tokenizer 契约，7 条）
- `app/src/test/java/com/ebbinghaus/memo/data/MemoRepositoryWriteContractTest.kt`（仓储写路径契约，8 条）

**修改的既有测试文件（14）**
- `core/src/test/.../SearchQueryTokenizerTest.kt`（+1）
- `core/src/test/.../AdversarialM1StressTest.kt`（−1）
- `app/src/test/.../crash/CrashLoggerTest.kt`（+2）
- `app/src/test/.../data/ExportCodecRoundTripTest.kt`（+5，含 1 条空转修复）
- `app/src/test/.../data/trash/TrashRetentionTest.kt`（+1）
- `app/src/test/.../ui/MemoListUndoTest.kt`（+1）
- `app/src/test/.../ui/TrashViewModelTest.kt`（+1）
- `app/src/test/.../ui/ReviewDailyLimitTest.kt`（+1）
- `app/src/test/.../ui/MemoListSelectionTest.kt`（+4）
- `app/src/test/.../ui/MemoListBatchFailureTest.kt`（+4）
- `app/src/test/.../ui/MemoListSortTest.kt`（+3）
- `app/src/test/.../ui/MemoListViewModelTest.kt`（+3）
- `app/src/test/.../ui/RatingSemanticsConsistencyTest.kt`（换强版）
- `app/src/test/.../ui/MemoEditorDraftStateTest.kt`（换强版，净 −4 + 迁入）
- `app/src/test/.../ui/StartupCoroutineBoundaryTest.kt`（+8，含反回归雷翻转）
- `app/src/androidTest/.../data/local/MigrationTest.kt`（空转断言改实质）

**删除的测试文件（6）**
- `app/src/test/java/com/ebbinghaus/memo/qa/QaP2IndependentVerificationTest.kt`
- `app/src/test/java/com/ebbinghaus/memo/qa/QaCrashHardeningVerificationTest.kt`
- `app/src/test/java/com/ebbinghaus/memo/qa/QaRound2FinalVerificationTest.kt`
- `app/src/test/java/com/ebbinghaus/memo/ui/QaFixIndependentVerificationTest.kt`
- `app/src/test/java/com/ebbinghaus/memo/ui/QaAdversarialUiTest.kt`
- `app/src/test/java/com/ebbinghaus/memo/data/QaAdversarialDataTest.kt`

---

## 10. 一致性结论

- **IS_PASS: YES** —— 全仓 `./gradlew test` 全绿；无 Qa\* 残留；无空转断言残留；无跨文件重复类定义；无循环依赖；导入完整（编译通过）；§6「必须保留」清单逐条核验未削弱。

---

## 11. 补正（QA 回归发现）

- **执行人**：寇豆码（Engineer）
- **执行日期**：2026-09-18（第 2 轮补正）
- **触发**：QA（严过关）回归复核 §3.1 B 档删除，发现 **1 处真实覆盖损失（F1）** + 1 处可选边界
- **范围**：**仅测试文件**（`app/src/test/`）；生产代码**零净改动**（变异验证后已还原，见 §11.2）
- **验收命令**：`./gradlew test --console=plain --rerun-tasks`
- **结论**：**BUILD SUCCESSFUL**；唯一 JVM 用例 **271**，全绿（0 失败 / 0 错误 / 0 跳过）

### 11.1 F1 —— 补回 2 条「立即删除不得弹确认窗」断言（真实覆盖损失）

**QA 判定更正**：B 档删除 #25 时，`ui/QaFixIndependentVerificationTest.kt:241`
`immediateDeleteFailure_keepsDataAndEmitsFailure_withoutOpeningDialog`（4 条断言）被判为与保留的
`ui/MemoListBatchFailureTest.kt:112` `immediateDeleteFailure_emitsFailure`（2 条断言）「等价」而删除。

**经实读核对，二者不等价，QA 原「≡」判定不成立**：被删用例多出的 2 条断言在全仓**确无他处覆盖**
（`grep -rn "isDeleteDialogVisible|pendingDeleteId" app/src/test` 全仓复扫，其余命中均为
`OnRequestDeleteMemo` / `OnConfirmDeleteMemo` / `OnCancelDeleteMemo` 路径，**无一在 `OnDeleteMemo` 之后断言**）：
- `assertFalse("立即删除语义不得打开确认弹窗", state.isDeleteDialogVisible)`
- `assertNull("立即删除语义不得产生待删 id", state.pendingDeleteId)`

**补回位置**：`app/src/test/java/com/ebbinghaus/memo/ui/MemoListBatchFailureTest.kt`
- `:132` `assertFalse("立即删除语义不得打开确认弹窗", state.isDeleteDialogVisible)`
- `:133` `assertNull("立即删除语义不得产生待删 id", state.pendingDeleteId)`

**判别力构造（`:116–121` 正控 + `:132–133` 待测）**：`MemoListViewModel` 实现中，
`OnDeleteMemo`（立即删除）**完全不触碰** `pendingDeleteId` / `isDeleteDialogVisible`，故若只从「干净状态」
直接断言 false，会因初始即 false 而缺乏判别力。为使其非空转，用例先加一段**正控**：
1. 发 `OnRequestDeleteMemo(1)` → 断言弹窗状态**确可被观测为 true**（`isDeleteDialogVisible==true`、`pendingDeleteId==1`）——证明该状态字段是「活」的、可被本用例观测；
2. 发 `OnCancelDeleteMemo` 复位；
3. 发 `OnDeleteMemo(1)` → 断言弹窗状态**未被产生**（`false` / `null`）。

> 说明：QA 建议的「先置位再断言被 `OnDeleteMemo` 清掉」在本实现下**不成立**——`OnDeleteMemo` 语义是
> 「**不产生**确认窗」而非「关闭已存在的确认窗」（实现里它根本不写这两个字段）。故采用「正控 + 未产生」
> 构造，既满足「先构造会触发弹窗的状态」，又保持全绿。

### 11.2 判别力证据（变异测试 —— 证明断言非空转）

**做法**：临时把生产代码 `MemoListViewModel.kt` 的 `OnDeleteMemo` 分支改为会置位弹窗
（`_uiState.update { it.copy(pendingDeleteId = event.id, isDeleteDialogVisible = true) }`），运行
`./gradlew :app:testDebugUnitTest --tests "com.ebbinghaus.memo.ui.MemoListBatchFailureTest" --rerun-tasks`：

```
> Task :app:testDebugUnitTest FAILED
MemoListBatchFailureTest > immediateDeleteFailure_emitsFailure FAILED
    java.lang.AssertionError at MemoListBatchFailureTest.kt:112
9 tests completed, 1 failed
```

失败信息（`app/build/test-results/testDebugUnitTest/TEST-…MemoListBatchFailureTest.xml`）：

```
message="java.lang.AssertionError: 立即删除语义不得打开确认弹窗"
    at org.junit.Assert.assertFalse(Assert.java:65)
    at …MemoListBatchFailureTest$immediateDeleteFailure_emitsFailure$1.invokeSuspend(MemoListBatchFailureTest.kt:132)
```

→ 断言在「立即删除被改为弹窗」时**必红**，证明其**非恒真、有真实判别力**。

**还原**：`MemoListViewModel.kt` 已从改动前备份还原，md5 与改动前**一致**
（`e3d853edbc18bf410fb55405688ce483`），全仓 `grep TEMP-MUTATION` = 0；生产代码**零净改动**。

### 11.3 全角空格空白标签（可选边界）—— 判定：**补**

**QA 标注位置**：`qa/QaP2IndependentVerificationTest.kt:514`
`assertEquals("", appendTagToInput("", "　"))`（B 档删除 #16 `blankTagOrBlankInput_isHandled` 中被删）。

**实读核对**：保留的 `ui/MemoEditorTagInputTest.appendBlankTag_isNoOp` 仅覆盖**半角空格** `"   "` 与空串 `""`；
全仓复扫 `U+3000` 命中均为 **tokenizer** 相关（`MemoRepositoryTokenizerContractTest` / `SearchQueryTokenizerTest`），
**标签追加路径的全角空格确无覆盖** → 与 F1 同属真实覆盖损失（虽轻微）。

**判定：值得补**（非冗余）。理由：`appendTagToInput` 用 `tag.trim()`（Kotlin `Char.isWhitespace` 口径，含 U+3000）；
半角用例**无法判别**「误改为仅 trim ASCII 空格 / 用 Java `\s`（默认不含 U+3000）匹配」类回归——这对**中文输入法**
（全角空格为常见输入）是真实边界。

**补回位置**：`app/src/test/java/com/ebbinghaus/memo/ui/MemoEditorTagInputTest.kt:45–46`
- `assertEquals("", appendTagToInput("", "　"))`
- `assertEquals("Android", appendTagToInput("Android", "　"))`

### 11.4 真实测试数字（实测）

```
BUILD SUCCESSFUL in 21s
57 actionable tasks: 57 executed
```

| 测试任务 | 用例数 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `:app:testDebugUnitTest` | 224 | 0 | 0 | 0 |
| `:app:testReleaseUnitTest` | 224 | 0 | 0 | 0 |
| `:core:test` | 47 | 0 | 0 | 0 |
| **唯一 JVM 用例（debug + core）** | **271** | **0** | **0** | **0** |

- 本轮**未新增 / 删除任何 `@Test` 方法**（仅在既有 2 个用例内补断言），故用例总数**保持
  271（唯一 JVM）/ 272（含 androidTest）**，与清理前口径一致。
- 受影响用例（均绿）：`MemoListBatchFailureTest.immediateDeleteFailure_emitsFailure`、
  `MemoEditorTagInputTest.appendBlankTag_isNoOp`。

### 11.5 变更文件清单（本轮，仅测试）

- `app/src/test/java/com/ebbinghaus/memo/ui/MemoListBatchFailureTest.kt`（+2 断言 + 1 正控块）
- `app/src/test/java/com/ebbinghaus/memo/ui/MemoEditorTagInputTest.kt`（+2 断言）

> 生产代码：**零净改动**（§11.2 变异验证已还原）。未动 C 档（12 条疑似）、D 档（release 变体单测），未删任何其它测试。
