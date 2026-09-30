# 测试资产审计报告（TEST_AUDIT）

- **审计人**：Edward / 严过关（QA）
- **审计日期**：2026-09-18
- **审计对象**：`EbbinghausMemo` 全部 JVM 单测与仪器化测试资产
- **审计性质**：**只读分析**。未修改、删除、重命名任何测试文件或生产代码；未新增测试。
- **设备声明**：本机无模拟器 / 真机，`app/src/androidTest` 无法执行（`MigrationTest.kt` 1 条如实计入「不可跑」）。

---

## 0. 数据来源与口径

| 项 | 值 | 来源 |
|---|---|---|
| `@Test` 总数 | **317** | 全仓静态扫描（已逐文件复核，与主理人统计一致） |
| 可本机执行 | **316** | 扣除 `androidTest/.../MigrationTest.kt` 1 条 |
| 测试文件 | 36（33 含用例 + 3 辅助：`FakeRepositories.kt` / `FakePreferenceStore.kt` / `MainDispatcherRule.kt`） | 文件扫描 |
| 测试代码行数 | 7,661 | `wc -l` |
| 生产代码行数 | 12,242 | `wc -l` |
| 断言调用数 | 1,021（≈ **3.2 断言/用例**） | `grep assert*` |
| 生产 `fun` 定义数 | 286（≈ **1.1 用例/函数**） | `grep fun` |
| 全量实跑（`./gradlew test --rerun-tasks`） | **BUILD SUCCESSFUL in 1m 32s**，269+269+47 全绿 | 本次实测 |
| 其中纯测试执行耗时 | **≈ 6.5s**（debug 3.25s + release 3.15s + core 0.10s） | XML `time` 求和 |

> ⚠️ **修正主理人事实 #5 的「约 5 分钟」**：实测冷跑（`--rerun-tasks`）为 **1m 32s**，非 5 分钟。且 92s 里只有 ~6.5s 是测试执行，其余为 Gradle 配置 + KSP + Kotlin 编译 + 资源处理。**性能问题不在「用例太多」，在「构建链路重复」**（见 §5-D）。

---

## 1. 总量是否合理 —— 明确结论

### 1.1 客观口径

| 口径 | 数值 | 参照系 | 判读 |
|---|---|---|---|
| 测试/生产 代码行比 | 7,661 / 12,242 = **0.63 : 1** | 健康区间通常 1:1 ~ 2:1 | **偏低**（测试代码量并不臃肿） |
| 用例 / 生产函数 | 317 / 286 = **1.11** | 常规 1~3 用例/函数 | **正常** |
| 用例 / 生产 public API | 317 / 268 = **1.18** | 常规 1~2 | **正常** |
| 平均断言密度 | 3.2 断言/用例 | < 1 为空转嫌疑 | **健康**（无大面积空壳用例） |
| 用例平均执行耗时 | 6.5s / 316 ≈ **20.6 ms/用例** | 纯 JVM 目标 < 50ms | **优秀**（无慢用例） |
| **冗余/重复占比** | **≈ 46 / 317 = 14.5%**（确证）+ 12 条疑似 | — | **结构性冗余显著** |

### 1.2 结论

> **结论：总量「偏多」——但不是数量失控，而是「结构性冗余」。**

判据（三条互相独立，均指向同一结论）：

1. **重复覆盖是唯一的结构性问题**。317 条中约 **46 条（14.5%）可被证伪为与他处等价**（逐条给出对照，见 §3），另有 12 条疑似。剔除后 ≈ **259~271 条**，落回「1 用例/函数」的合理区间。
2. **总量膨胀发生在「按交付轮次」而非「按功能」的维度上**。`Qa*` 文件共 **95 条（30%）**，全部产生于 2026-09-15 / 09-16 两天的历次「独立验证」交付（文件 mtime 可证）。基线（09-14 M3）仅 63 条，5 天膨胀 5 倍，增量几乎全部来自对抗验证类文件。
3. **边际价值明确递减**。`Qa*` 文件中，凡是**功能测试已存在**的场景，重复用例的增量断言 ≈ 0（同一事件序列 + 同一状态断言）；真正带增量价值的只有「功能测试未覆盖的边界 / 更严的数据集 / 更底层的观测点」——这些在 95 条里只占约 **49 条**。

**反向证据（避免误判为「过度测试」）**：断言密度 3.2、平均耗时 20ms、测试/生产行比 0.63:1，说明**并不存在「为凑数写大量空壳用例」**的问题。本次要治的是「重复」，不是「多」。

---

## 2. 分类盘点（按「守护什么」，317 条不重不漏）

### 2.1 主分类表

| 类别 | 用例数 | 占比 | 代表文件 | 是否核心 |
|---|---:|---:|---|:---:|
| **领域算法**（艾宾浩斯 / 顺延 / 切分 / 数学文本） | **54** | 17% | `EbbinghausSchedulerTest`(10)、`SearchQueryTokenizerTest`(16)、`RolloverEngineTest`(8)、`AdversarialM1StressTest`(7)、`MathTextPreprocessorTest`(6)、`QaTokenizerBoundaryTest`(7) | ✅ 核心 |
| **数据层契约**（Converters / 导出导入 / 保留策略 / Schema / 迁移） | **45** | 14% | `DataLayerContractAdversarialTest`(11)、`QaAdversarialDataTest`(14)、`ExportCodecRoundTripTest`(8)、`TrashRetentionTest`(7)、`SchemaV2ContractTest`(4)、`MigrationTest`(1) | ✅ 核心 |
| **ViewModel 状态机**（列表 / 回收站 / 复习 / 看板 / 设置 / 编辑器） | **146** | 46% | `TrashViewModelTest`(26)、`QaFixIndependentVerificationTest`(25)、`MemoListViewModelTest`(8)、`MemoListSelectionTest`(8)、`MemoListUndoTest`(8)、`MemoEditorTagInputTest`(8) | ✅ 核心 |
| **回归钉扎**（曾导致崩溃 / 曾修复缺陷） | **59** | 19% | `QaRound2FinalVerificationTest`(13)、`NavigationRouteInitRegressionTest`(10)、`StartupCoroutineBoundaryTest`(9)、`CrashLoggerTest`(9)、`QaCrashHardeningVerificationTest`(8)、`RatingSemanticsConsistencyTest`(5)、`MemoListBatchFailureTest`(5) | ✅ 核心 |
| **跨层契约**（UI 高亮词 ↔ 仓储查询词） | **7** | 2% | `QaP2_10_TokenizerContractTest`(3)、`QaP2_10_RepositoryTokenizerParityAdversarialTest`(4) | ✅ 核心（唯一覆盖） |
| **UI 纯函数 / 基础设施** | **6** | 2% | `WindowSizeClassTest`(4)、`NavigationBadgeTest`(2) | ⬜ 外围 |
| **合计** | **317** | 100% | | |

### 2.2 按「来源批次」交叉表（回答「Qa* 是否该留」）

| 来源 | 用例数 | 文件 | 其中「功能测试已有等价」 |
|---|---:|---|---:|
| 基线功能测试（09-14 M3） | 63 | 8 个文件 | — |
| 增量功能测试（09-15 / 09-16） | 159 | `TrashViewModelTest`、`MemoList*`、`MemoEditor*`、`Review*`、`Export*`、`SchemaV2*` 等 | — |
| **`Qa*` 对抗验证（09-15 / 09-16）** | **95** | 6 个文件 | **≈ 46（48%）** |

> **关键事实**：`Qa*` 的 95 条里，**48% 与既有功能测试逐断言等价**；真正独有的价值集中在 3 处：**跨层 tokenizer 契约（7）**、**崩溃加固的注入点扩展（≈ 8）**、**数据层仓储级写路径（7）**。

---

## 3. 🔴 废弃 / 冗余用例清单（本次核心）

**判定类型图例**：`重复覆盖` / `废弃` / `脆弱` / `空转` / `低价值`

> 全仓**未发现**任何用例守护「已删除或已变更的功能」——即严格意义的**「废弃」用例 = 0 条**。所有冗余均为「同一功能的重复实现」或「断言方式缺陷」。**空转断言 2 条**（其中 1 条在不可执行的 androidTest 内）。

### 3.1 B 档候选：确证重复 / 空转（46 条，删除后覆盖不变）

| # | 用例（文件:行） | 判定 | 依据（与谁重复 / 为何空转） | 删除风险评估 |
|---|---|---|---|---|
| 1 | `fullWidthSpace_isSeparator` `QaP2IndependentVerificationTest.kt:120` | 重复覆盖 | ≡ `SearchQueryTokenizerTest.kt:35` `tokenize_fullWidthSpace_isSeparator`（同输入同断言） | 无（同义） |
| 2 | `newlineAndTab_areSeparators` `:125` | 重复覆盖 | ≡ `SearchQueryTokenizerTest.kt:41` | 无 |
| 3 | `mixedWhitespaceKinds_collapseIntoOneSeparator` `:130` | 重复覆盖 | ≡ `SearchQueryTokenizerTest.kt:46`（仅字面量写法不同） | 无 |
| 4 | `emptyAndSeparatorOnlyInput_yieldZeroWords` `:138` | 重复覆盖 | ⊂ `SearchQueryTokenizerTest.kt:17` + `:22` | 无 |
| 5 | `exactlySixWords_noHint_sevenWords_oneIgnored` `:146` | 重复覆盖 | ⊂ `SearchQueryTokenizerTest.kt:66` + `:85` | 无 |
| 6 | `activeKeywords_isPrefixOfTokenize_andCappedAtSix` `:159` | 重复覆盖 | ≡ `SearchQueryTokenizerTest.kt:72` | 无 |
| 7 | `oldTokenAfterSecondDelete_isIgnored` `:334` | 重复覆盖 | ≡ `MemoListUndoTest.kt:114` `secondDelete_overwritesToken_oldKeyBecomesInvalid`（事件序列与 4 条断言全同） | 无 |
| 8 | `unknownToken_isIgnored` `:356` | 重复覆盖 | ≡ `MemoListUndoTest.kt:139` `unknownKey_isIgnoredWithoutSideEffect` | 无 |
| 9 | `expiredEntry_undoReportsExpiredWithoutCrash` `:380` | 重复覆盖 | ≡ `MemoListUndoTest.kt:150` `undo_whenEntryAlreadyPurged_reportsExpiredWithoutCrash` | 无 |
| 10 | `batchUndo_restoresEntireBatchOnly` `:394` | 重复覆盖 | ≡ `MemoListUndoTest.kt:98` `batchDelete_thenUndo_restoresWholeBatch` | 无 |
| 11 | `immediateDelete_neverEmitsUndoSnackbar` `:410` | 重复覆盖 | ≡ `MemoListUndoTest.kt:178` `immediateDelete_doesNotEmitUndoSnackbar_semanticsPreserved` | 无 |
| 12 | `selectAll_coversAllVisibleItems` `:445` | 重复覆盖 | ⊂ `TrashViewModelTest.kt:258` `selectAll_thenClearSelection` | 无 |
| 13 | `batchRestore_hasNoConfirmGate_andExitsSelection` `:452` | 重复覆盖 | ≡ `TrashViewModelTest.kt:299` `batchRestore_restoresSelectedWithoutConfirm` | 无 |
| 14 | `batchDelete_requiresConfirmBeforePhysicalDelete` `:463` | 重复覆盖 | ≡ `TrashViewModelTest.kt:313` `batchDelete_requiresConfirm_thenHardDeletes` | 无 |
| 15 | `duplicateAcrossAnySeparator_isNoOp` `:502` | 重复覆盖 | ≡ `MemoEditorTagInputTest.kt:46` `appendDetectsExistingAcrossAllSeparators` | 无 |
| 16 | `blankTagOrBlankInput_isHandled` `:510` | 重复覆盖 | ≡ `MemoEditorTagInputTest.kt:40` `appendBlankTag_isNoOp` | 无 |
| 17 | `appendedResult_roundTripsThroughEditorParser` `:518` | 重复覆盖 | ≡ `MemoEditorTagInputTest.kt:63` `repeatedAppends_neverProduceDuplicates` | 无 |
| 18 | `zeroLimit_yieldsEmptyBatchAndCompleted` `:547` | 重复覆盖 | ≡ `ReviewDailyLimitTest.kt:73` `zeroLimit_withPendingTasks_yieldsEmptyBatchAndCompleted` | 无 |
| 19 | `positiveLimit_withEmptyQueue_staysCelebration` `:563` | 重复覆盖 | ≡ `ReviewDailyLimitTest.kt:85` `positiveLimit_withEmptyQueue_staysCompletedWithPositiveLimit` | 无 |
| 20 | `firstLongPress_notInSelectionMode_selectsOnlyThatId` `QaFixIndependentVerificationTest.kt:78` | 重复覆盖 | ≡ `MemoListSelectionTest.kt:42` `longPress_entersSelectionModeWithSingleSelection` | 无 |
| 21 | `longPress_inSelectionMode_notSelected_addsPreservingOthers` `:87` | 重复覆盖 | ≡ `MemoListSelectionTest.kt:51` `longPress_whileAlreadyInSelectionMode_togglesInsteadOfResetting` | 无 |
| 22 | `longPress_inSelectionMode_alreadySelected_removesOnlyThat` `:101` | 重复覆盖 | ≡ `MemoListSelectionTest.kt:51`（第 62–64 行分支） | 无 |
| 23 | `batchDeleteFailure_rollsBackSelectionAndMode_keepsData_emitsFailure` `:167` | 重复覆盖 | ≡ `MemoListBatchFailureTest.kt:64` `batchDeleteFailure_keepsSelectionAndEmitsFailure` | 无 |
| 24 | `batchAddTagFailure_rollsBackSelectionAndMode_keepsData_emitsFailure` `:202` | 重复覆盖 | ≡ `MemoListBatchFailureTest.kt:88` `batchAddTagFailure_keepsSelectionAndEmitsFailure` | 无 |
| 25 | `immediateDeleteFailure_keepsDataAndEmitsFailure_withoutOpeningDialog` `:241` | 重复覆盖 | ≡ `MemoListBatchFailureTest.kt:112` `immediateDeleteFailure_emitsFailure` | 无 |
| 26 | `confirmDeleteFailure_closesDialogButKeepsDataAndEmitsFailure` `:256` | 重复覆盖 | ≡ `MemoListBatchFailureTest.kt:127` `confirmDeleteFailure_emitsFailureAndKeepsData` | 无 |
| 27 | `batchDeleteSuccess_clearsSelectionExitsMode_emitsSuccess` `:186` | 重复覆盖 | ≡ `MemoListBatchFailureTest.kt:145` `batchDeleteSuccess_stillExitsSelectionAndEmitsSuccess` | 无 |
| 28 | `semanticsMap_coversExactlyAllFiveRatings` `:336` | 重复覆盖 | ≡ `RatingSemanticsConsistencyTest.kt:99` `allRatings_haveNonBlankSemantics` | 无 |
| 29 | `forgetSemantics_rollsBackOneStage_forEveryStage` `:344` | 重复覆盖 | ⊃ `RatingSemanticsConsistencyTest.kt:24`（**Qa 版是功能版的超集**，全档遍历） | 无（保留 Qa 版即可） |
| 30 | `rememberSemantics_advancesOneStage_andEnters60DayCycleAfterStage6` `:363` | 重复覆盖 | ⊃ `RatingSemanticsConsistencyTest.kt:49`（超集） | 无 |
| 31 | `vagueAndDefaultSemantics_keepStageAndInterval_forEveryStage` `:386` | 重复覆盖 | ⊃ `RatingSemanticsConsistencyTest.kt:65`（超集） | 无 |
| 32 | `skipSemantics_defersExactlyOneDayWithoutStageChange` `:405` | 重复覆盖 | ⊃ `RatingSemanticsConsistencyTest.kt:85`（超集） | 无 |
| 33 | `identicalInputs_isFalse` `:422` | 重复覆盖 | ≡ `MemoEditorDraftStateTest.kt:17` `noChange_returnsFalse` | 无 |
| 34 | `allEmpty_isFalse` `:427` | 重复覆盖 | ≡ `MemoEditorDraftStateTest.kt:73` `newMemoUntouched_returnsFalse` | 无 |
| 35 | `tagsOnlyChange_isTrue` `:440` | 重复覆盖 | ⊃ `MemoEditorDraftStateTest.kt:59` `tagsChanged_returnsTrue` | 无 |
| 36 | `notesOnlyChange_isTrue` `:446` | 重复覆盖 | ≡ `MemoEditorDraftStateTest.kt:45` `notesChanged_returnsTrue` | 无 |
| 37 | `dashboard_initCollector_dependencyThrows_isContainedByBoundary_notEscaping` `QaCrashHardeningVerificationTest.kt:235` | 重复覆盖 | ≡ `StartupCoroutineBoundaryTest.kt:95` `dashboardInit_dependencyThrows_isContainedByBoundary_andLogged`。**附加缺陷**：本用例采用「`runTest{}` → `UncaughtExceptionsBeforeTest`」探针，而同批的 `QaRound2FinalVerificationTest.kt:118` 注释已自证该探针「在同一无边界协程上表现不稳定」 | 无（保留 `StartupCoroutineBoundaryTest` 版） |
| 38 | `crashLogger_handler_mustInvokeOriginalHandler_andWriteCrash` `QaRound2FinalVerificationTest.kt:191` | 重复覆盖 | ≡ `CrashLoggerTest.kt:136` `handler_delegatesToPreviousHandler_andWritesCrash` | 无 |
| 39 | `crashLogger_handler_whenWriteFails_stillInvokesOriginalHandler` `:212` | 重复覆盖 | ≡ `CrashLoggerTest.kt:170` `handler_whenWriteFails_stillDelegatesToPrevious` | 无 |
| 40 | `crashLogger_retention_deletesOldestBeyondLimit` `:240` | 重复覆盖 | ≡ `CrashLoggerTest.kt:68` `writeCrash_retainsOnlyLatestThreeRecords`（仅 maxRecords 取值 2 vs 3） | 无 |
| 41 | `crashLogger_clear_removesEverything_andEmptyStateIsSafe` `:258` | 重复覆盖 | ≡ `CrashLoggerTest.kt:103` `hasCrashRecord_and_clear_behaveAsExpected` | 无 |
| 42 | `dashboardInit_dependencyThrows_isContained_andLogged_independentProbe` `:310` | 重复覆盖 | ≡ `StartupCoroutineBoundaryTest.kt:95` | 无 |
| 43 | `reviewInit_dependencyThrows_setsVisibleErrorState_andDoesNotEscape` `:354` | 重复覆盖 | ≡ `StartupCoroutineBoundaryTest.kt:153` | 无 |
| 44 | `settingsInit_dependencyThrows_setsVisibleError_andDoesNotEscape` `:379` | 重复覆盖 | ≡ `StartupCoroutineBoundaryTest.kt:219` | 无 |
| 45 | `decode_isPure_doesNotDependOnPriorEncode` `QaAdversarialDataTest.kt:238` | **空转** | 断言为 `assertEquals(codec.decode(json).getOrThrow(), codec.decode(json).getOrThrow())` —— **同一纯函数、同一入参、调用两次再自比，恒真**。既未在两次 `decode` 之间插入任何状态变更，也未调用 `encode`，故**无法证伪「隐藏状态/缓存」**（这正是注释声称要验证的东西）。 | 无（该用例不具备守护力） |
| 46 | `testAdversarial_negativeDailyLimit_gracefulFallbackToZero` `core/.../AdversarialM1StressTest.kt:245` | 重复覆盖 | ≡ `RolloverEngineTest.kt:51` `testDailyLimitNegative_handledAsZero`（仅 -100/2 条 vs -5/1 条） | 无 |

**B 档小计：46 条**（占 317 的 14.5%）。全部满足「删除后断言集合不变或仅保留更强版本」。

> 说明 #29–#32、#33–#36 两组的正确操作是**合并**：Qa 版是超集，应把 Qa 版并入功能文件、删除功能文件中的弱版本，净减 5 条（rating）+ 4 条（draft）。无论「删 Qa 弱版」还是「删功能弱版」，净减少都是 9 条，已计入 46。

### 3.2 空转断言（全量扫描结果）

| 位置 | 内容 | 判定 |
|---|---|---|
| `QaAdversarialDataTest.kt:241` | `assertEquals(codec.decode(json).getOrThrow(), codec.decode(json).getOrThrow())` | **空转（恒真）**——见 §3.1 #45 |
| `MigrationTest.kt:95` | `assertTrue(true)` | **空转**，但该文件整体**不可在本机执行**（需真机）；且其上方 4 组 `db.query(...).assertEquals(...)` 是真实断言。属「无害残留」，**随该文件待设备验证时一并清理**即可 |

> 主理人关心的「历史上 `SearchQueryTokenizerTest` 曾有函数与自身比较的恒真断言」——**已确认被修复**：现 `SearchQueryTokenizerTest.kt:96-108` 已改为显式断言切分口径（注释明确写「而非『函数与自身比较』（恒真无守护力）」）。全仓**未再发现同类问题**，仅上述 2 处。

### 3.3 脆弱 / 反回归用例（1 条，需改造而非删除）

| 用例 | 判定 | 依据 | 建议 |
|---|---|---|---|
| `probe_isCapable_ofDetectingEscape_memoDetailInitStillUnbounded` `QaRound2FinalVerificationTest.kt:290` | **脆弱 / 反回归** | 该用例是「阳性对照」，**断言 `MemoDetailViewModel.loadMemoDetails()` 的协程仍无异常边界**（`assertTrue(escapes.isNotEmpty())`）。经查生产代码 `MemoDetailViewModel.kt:51-64` 确实无 try/catch。**一旦有人给该 VM 补上边界（正确的修复），此用例必然变红**——即它把「缺陷存在」写成了契约。 | **不应保留为常规回归**。建议：改为「探针自检」用例——用一个测试内自造的、确定无边界的内联协程（而非生产类）来证明探针有效；生产类补边界后即可安全删除本用例。 |

### 3.4 疑似冗余（12 条，**需人工拍板**，不做默认删除）

| # | 用例（文件:行） | 疑似类型 | 不确定点 |
|---|---|---|---|
| 1 | `fourSorts_onShuffledData_produceExactIdSequences` `QaAdversarialUiTest.kt:50` | 重复覆盖（**Qa 更强**） | 与 `MemoListSortTest.kt:47` 重复，但 Qa 版用「乱序 + 并列键」数据集，断言更严。删哪条取决于团队偏好「简洁」还是「强度」 |
| 2 | `sorting_neverChangesMembership_orSoftDeletedLeakIn` `:77` | 重复覆盖（Qa 更强） | 与 `MemoListSortTest.kt:103` 重叠，但额外覆盖「软删条目不得混入」 |
| 3 | `batchAddTag_mixedSelection_dedupesAndSkipsExisting` `:115` | 重复覆盖（Qa 更强） | 与 `MemoListSelectionTest.kt:124` 重叠，但覆盖「已含/多标签/空标签/未选中」四象限 |
| 4 | `roundTrip_adversarialPayload_fieldByFieldEqual` `QaAdversarialDataTest.kt:180` | 重复覆盖（Qa 更强） | 与 `ExportCodecRoundTripTest.kt:78` 重叠，但载荷含引号/反斜杠/U+001F/超长/软删 |
| 5 | `versionTooNew_isRejectedWithoutPartialParse` `:210` | 重复覆盖（Qa 更强） | 与 `ExportCodecRoundTripTest.kt:116` 重叠，但额外断言 `VersionTooNewException.version` |
| 6 | `corruptedJson_variants_allRejected` `:220` | 重复覆盖（Qa 更强） | ⊃ `ExportCodecRoundTripTest.kt:124/131/136` 三条 |
| 7 | `remainingDays_atDay29_30_31` `:254` | 重复覆盖 | ⊂ `TrashRetentionTest.kt:40/33`，边界日 29/30/31 已被覆盖 |
| 8 | `memoList_observeAllTags_happyPath_stillPopulatesTags` `QaCrashHardeningVerificationTest.kt:293` | 疑似重复 | 标签去重排序与 `MemoListViewModelTest.kt:46` 重叠，但本用例额外断言 `Log.w+e == 0`（非静默的反向守护），有独立价值 |
| 9 | `crashLogger_report_containsFullStackAndNestedCauseChain` `QaRound2FinalVerificationTest.kt:161` | 重复覆盖（Qa 更强） | ⊃ `CrashLoggerTest.kt:44`，额外断言「≥2 级 Caused by」「堆栈含本测试类名」 |
| 10 | `crashLogger_writeCrash_neverThrows_whenDirUnusable` `:226` | 重复覆盖（Qa 更强） | ⊃ `CrashLoggerTest.kt:156`，额外断言 `recordCount()==0` |
| 11 | `longPress_repeatedOnSameId_isIdempotentToggleNotReset` `QaFixIndependentVerificationTest.kt:128` | 疑似重复 | 「同一 id 连续长按两次 = 加→减」的净效果已隐含于 `MemoListSelectionTest.kt:51`，但显式幂等性断言略强 |
| 12 | `allInitBoundaries_cancellation_isRethrown_notSwallowed` `QaRound2FinalVerificationTest.kt:402` | 重复覆盖（Qa 更强） | ⊃ 三条 `*Init_cancellation_isRethrown_notLogged`，但把三处边界合并为一次断言，失败定位粒度变粗 |

> **保守处理**：#1–#12 中多数是「Qa 版更强」。**建议不删，而是反向合并**（用强版替换弱版），这样既不减保护、又能消重。若用户只想快速瘦身，则这 12 条可作 C 档处理。

### 3.5 关于「重复覆盖」的两个重点澄清（避免误伤）

- **`AdversarialM1StressTest` 与 `EbbinghausSchedulerTest` / `RolloverEngineTest` 的重叠（主理人信号 #2）**：7 条中**仅 1 条**（`negativeDailyLimit`）是真重复。其余 6 条是「**同一规则在更大规模/更长迭代下的不变量验证**」（100 条任务、连续 10 次忘记、30 天积压、连续 5 次跳过），单元测试的规模远小于此。**结论：整体保留，仅删 1 条。**
- **`SchemaV2ContractTest`（主理人信号 #3）**：4 条**仍有价值，建议保留**。它钉扎 `AppDatabase.MIGRATION_1_2_SQL` 常量与 Room 导出的 `1.json/2.json` schema，是**无设备环境下迁移的唯一守门**。它不是「迁移测试的替代品」，而是「迁移 SQL 未被误改」的哨兵。删除后，若有人改动迁移语句，在下次上真机前**不会有任何信号**。

---

## 4. 组织结构问题

### 4.1 `Qa*` 命名应语义化（具体改名映射）

现状：7 个 `Qa*` 文件 / 9 个 `Qa*` 测试类，按「谁、哪一轮」命名，**读者无法从名字得知它守护什么**，且与功能测试形成「影子套件」。

**建议改名 / 归并映射表**（A 档，纯组织调整，不改断言）：

| 现文件 | 现测试类 | 用例 | → 目标文件（语义化） | 建议动作 |
|---|---|---|---:|---|
| `qa/QaP2IndependentVerificationTest.kt` | `QaTokenizerBoundaryTest` | 7 | `core/.../SearchQueryTokenizerTest.kt` | 并入（其中 6 条删重，见 §3.1） |
| | `QaP2_10_TokenizerContractTest` | 3 | **`data/MemoRepositoryTokenizerContractTest.kt`（新建名）** | 独立保留（唯一跨层契约） |
| | `QaP2_10_RepositoryTokenizerParityAdversarialTest` | 4 | 同上，合并为一个类 | 独立保留 |
| | `QaUndoTokenAdversarialTest` | 6 | `ui/MemoListUndoTest.kt` | 并入 |
| | `QaTrashStateMachineAdversarialTest` | 4 | `ui/TrashViewModelTest.kt` | 并入 |
| | `QaAppendTagBoundaryTest` | 3 | `ui/MemoEditorTagInputTest.kt` | 并入 |
| | `QaDailyLimitBranchTest` | 3 | `ui/ReviewDailyLimitTest.kt` | 并入 |
| `ui/QaFixIndependentVerificationTest.kt` | `QaSelectionModeAdversarialTest` | 5 | `ui/MemoListSelectionTest.kt` | 并入 |
| | `QaBatchFailureRollbackAdversarialTest` | 9 | `ui/MemoListBatchFailureTest.kt` | 并入 |
| | `QaRatingSemanticsAlgorithmConsistencyTest` | 5 | `ui/RatingSemanticsConsistencyTest.kt` | 并入（保留全档遍历强版） |
| | `QaDraftStateBoundaryTest` | 6 | `ui/MemoEditorDraftStateTest.kt` | 并入 |
| `data/QaAdversarialDataTest.kt` | `ExportCodecAdversarialTest` | 5 | `data/ExportCodecRoundTripTest.kt` | 并入 |
| | `TrashRetentionBoundaryAdversarialTest` | 2 | `data/trash/TrashRetentionTest.kt` | 并入 |
| | `SoftDeleteRestoreAdversarialTest` | 7 | **`data/MemoRepositoryWriteContractTest.kt`（新建名）** | 独立保留（唯一仓储级写路径） |
| `ui/QaAdversarialUiTest.kt` | `MemoSortDeterminismAdversarialTest` | 3 | `ui/MemoListSortTest.kt` | 并入 |
| | `BatchTagMergeAdversarialTest` | 2 | `ui/MemoListSelectionTest.kt` | 并入 |
| `qa/QaCrashHardeningVerificationTest.kt` | `QaCrashHardeningVerificationTest` | 8 | `ui/StartupCoroutineBoundaryTest.kt` + `ui/MemoListViewModelTest.kt` | 拆分并入（1 条删重） |
| `qa/QaRound2FinalVerificationTest.kt` | `QaRound2FinalVerificationTest` | 13 | `crash/CrashLoggerTest.kt` + `ui/StartupCoroutineBoundaryTest.kt` | 拆分并入（7 条删重；1 条改造） |

**收益**：`Qa*` 文件数 7 → **0**；新增 2 个**语义化**契约文件（跨层 tokenizer / 仓储写路径）；其余 95 条全部回归到「按功能组织」的家。

### 4.2 同一功能的测试散落多文件（举例）

| 功能 | 散落位置 | 问题 |
|---|---|---|
| **备忘录列表多选** | `MemoListSelectionTest`(8) + `QaFix…::QaSelectionModeAdversarialTest`(5) + `QaAdversarialUiTest::BatchTagMergeAdversarialTest`(2) | 3 个文件测同一状态机 |
| **写操作失败回滚** | `MemoListBatchFailureTest`(5) + `QaFix…::QaBatchFailureRollbackAdversarialTest`(9) | 2 文件，5 条逐断言等价 |
| **撤销令牌** | `MemoListUndoTest`(8) + `QaP2…::QaUndoTokenAdversarialTest`(6) | 2 文件，5 条等价 |
| **搜索切分** | `SearchQueryTokenizerTest`(16) + `QaP2…::QaTokenizerBoundaryTest`(7) | 2 文件，6 条等价 |
| **CrashLogger** | `CrashLoggerTest`(9) + `QaRound2…`(6 条) | 2 文件，6 条等价（Qa 多为超集） |
| **启动协程边界** | `StartupCoroutineBoundaryTest`(9) + `QaCrashHardening…`(1 条) + `QaRound2…`(6 条) | **3 个文件**，7 条等价 |
| **导出编解码** | `ExportCodecRoundTripTest`(8) + `QaAdversarialDataTest::ExportCodecAdversarialTest`(5) | 2 文件 |
| **标签追加去重** | `MemoEditorTagInputTest`(8) + `QaP2…::QaAppendTagBoundaryTest`(3) | 2 文件，3 条等价 |
| **草稿未保存判定** | `MemoEditorDraftStateTest`(6) + `QaFix…::QaDraftStateBoundaryTest`(6) | 2 文件，4 条等价 |

---

## 5. 精简建议（分档）

### A 档 · 零风险（纯改名 / 合并，不减少覆盖）

- **动作**：按 §4.1 映射表重组文件；`Qa*` 文件全部消解为语义化归属。
- **涉及用例**：**95 条**（全部 `Qa*` 的归属调整），**净减少 0 条**。
- **收益**：命名可表达守护意图；消除「影子套件」；测试与生产模块一一对应；为 B/C 档铺路。
- **风险**：**零**（不改任何断言、不改测试语义）。
- **耗时影响**：0（用例数不变）。
- **可验证方式**：重组后 `./gradlew test` 必须仍为 **316 passed / 0 failed**。

### B 档 · 低风险（删除确证重复 / 空转）

- **动作**：删除 §3.1 所列 **46 条**（含 1 条空转 `decode_isPure`）。
- **涉及用例**：46 条；**预计减少 46 条（317 → 271）**。
- **覆盖影响**：**零**——每条均已逐条给出「与之等价的另一条」。
- **收益**：
  - 用例数 −14.5%，测试代码约 −1,100 行（估）；
  - 纯测试执行时间约 −0.5s（占 6.5s 的 ~8%）；
  - **真实收益是认知成本**：消除「同一断言看两遍」的维护负担，避免改一处漏改一处。
- **风险**：低。唯一需注意的是 §3.1 #29–#36 两组的**合并方向**——必须保留强版本（全档遍历 / 四象限），否则会真丢覆盖。
- **前置**：建议先做 A 档（合并到同一文件后再删，避免跨文件误删）。

### C 档 · 需谨慎（价值存疑，需用户拍板）

- **动作**：处理 §3.4 的 **12 条疑似** + §3.3 的 **1 条脆弱用例（改造）**。
- **涉及用例**：13 条；**预计减少 12 条 + 改造 1 条**（317 → 259，相对 A+B 后）。
- **收益**：进一步压缩 ~4%。
- **风险**：**中**。这些用例的断言**强于**其功能对应物（更严的数据集、更多注入点、更底层观测），删除属于「用简洁换强度」。**建议默认不删**；若删，须同时确认强版本已被功能版吸收（如把 Qa 的乱序数据集移植进 `MemoListSortTest`）。
- **建议**：C 档**不在本轮执行**，仅在团队明确「追求最小用例集」时再做。

### D 档 · 构建链路（非用例问题，但收益最大）

- **问题**：`app` 同时执行 `testDebugUnitTest`(269) 与 `testReleaseUnitTest`(269)，**同一批用例跑两遍**。
- **核实结论（主理人信号 #4）**：**release 变体单测确无必要**。依据：
  1. `app/src/release` 目录**为空**（无 release 专属源码 / 资源），`app/src/debug` 仅有 3 个 Preview 文件且**无任何测试引用**；
  2. **R8 / minify 不参与单元测试**——本地单测跑的是未混淆的 class，release 变体与 debug 变体**被测字节码一致**（实测两变体各 269 条、各 42 个结果文件、耗时 3.25s vs 3.15s）；
  3. 释放 release 变体可省去 `kspReleaseKotlin`、`compileReleaseKotlin`、`processReleaseResources`、`kspReleaseUnitTestKotlin`、`compileReleaseUnitTestKotlin`、`bundleReleaseClassesToRuntimeJar` 等 6+ 个任务。
- **建议**（仅建议，未实施）：在 `app/build.gradle.kts` 增加
  ```kotlin
  androidComponents {
      beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
  }
  ```
  或将 CI 固定为 `./gradlew :app:testDebugUnitTest :core:test`。
- **收益（已实测）**：全量 `./gradlew test` **92s → `:app:testDebugUnitTest :core:test` 29s**，**节省 63s（−68%）**；用例数不变（仍 316 条唯一用例）。实测对比见 §7.4。
- **风险**：低。唯一代价是失去「release 变体编译通过」的顺带校验——该职责本应由 `assembleRelease`（含 R8）承担，而非单测。

---

## 6. 反向建议：**不该精简的地方**

以下测试「看起来多」，但**删除会实质削弱保护**，明确建议保留：

| 资产 | 用例 | 为何必须保留 |
|---|---:|---|
| **`NavigationRouteInitRegressionTest`** | 10 | 守护一次**真实的 debug 包启动崩溃**（JVM 类静态初始化循环，`Screen.<clinit>` → NPE → `ExceptionInInitializerError`）。它是全仓**唯一触碰 `Screen` / `TopLevelDestination` 的测试**，并用隔离类加载器做到「与执行顺序无关」的稳定复现。删掉 = 该崩溃的回归网彻底消失。 |
| **`QaP2_10_TokenizerContractTest` + `QaP2_10_RepositoryTokenizerParityAdversarialTest`** | 7 | 全仓**唯一**以「记录型 DAO + 生产 `MemoRepositoryImpl`」实证「**UI 高亮词集合 == 仓储实际查询词集合**」的跨层契约。P2-10 曾因此**真实出过缺陷**（第 1 轮 `assertNotEquals` 证伪、第 2 轮修复）。其余切分测试都只测纯函数，测不到「仓储有没有真的用 tokenizer」。**建议改名保留，不要删。** |
| **`StartupCoroutineBoundaryTest`** | 9 | 守护启动路径（`DashboardViewModel` / `ReviewViewModel` / `SettingsViewModel` 三处 `init`）的异常边界：不逃逸（不闪退）+ 非静默（有 `Log.e`）+ 取消信号不被吞。**直接对应一次冷启动崩溃专项。** |
| **`CrashLoggerTest`** | 9 | 崩溃自诊断是「用户交回根因的唯一载体」。含两条硬约束：「**原 handler 必须被调用**（不得吞崩溃）」与「**写日志失败不得二次崩溃**」。这类断言一旦丢失，故障将无法从用户侧回收。 |
| **`DataLayerContractAdversarialTest`** | 11 | Converters 分隔符碰撞（`§`）、LocalDate 纪元/负数/闰年边界、`updateMemo` 绝不触碰复习进度、`createMemo` 跨表双写、外键 CASCADE 失效实证。**数据层契约无其他测试覆盖。** |
| **`SoftDeleteRestoreAdversarialTest`**（现位于 `QaAdversarialDataTest`） | 7 | 全仓**唯一**直接测 `MemoRepositoryImpl` 写路径（软删/还原逐字段一致、批量单语句、`purgeExpiredTrash` cutoff、`addTagToMemos` 去重、`clearTrash` 委派）。 |
| **`TrashViewModelTest`** | 26 | 单文件最大，但 26 条**各自守护不同事件 / 排序键 / 筛选域**（数据域隔离、独立搜索 AND、三种排序、就地展开、多选、筛选后求交、批量还原/删除、失败保留选择集、剩余天数）。**无重复，不要因「文件大」而砍。** |
| **`SchemaV2ContractTest`** | 4 | 无设备环境下迁移的**唯一 JVM 守门**（钉扎迁移 SQL 常量 + Room schema JSON）。 |
| **`MigrationTest`** | 1 | 需真机，**保留待有设备时执行**；是迁移的最终权威验证。 |
| **`RatingSemanticsConsistencyTest`** | 5 | 无障碍文案（TalkBack `contentDescription`）与调度算法的一致性——文案错误会**系统性误导视障用户**，且无法靠 UI 目测发现。 |
| **`MemoListUndoTest`** | 8 | 撤销令牌状态机（连删仅最新可撤销、旧令牌失效、过期不崩、失败提示）。含 `undo_failure_emitsFailureSnackbar` 等**功能测试未覆盖**的分支。 |

> **一句话原则**：本次精简的目标是「**同一断言只留一处**」，而不是「**同一功能只留一条**」。凡守护**唯一观测点**（跨层、崩溃、迁移、无障碍）的用例，一律保留。

---

## 7. 附：实测数据

### 7.1 全量测试（`./gradlew test --rerun-tasks`）

```
BUILD SUCCESSFUL in 1m 32s
57 actionable tasks: 57 executed
real 1m33.549s
```

| 测试任务 | 用例数 | 失败 | 跳过 | 套件耗时合计 |
|---|---:|---:|---:|---:|
| `:app:testDebugUnitTest` | 269 | 0 | 0 | 3.25s |
| `:app:testReleaseUnitTest` | 269 | 0 | 0 | 3.15s |
| `:core:test` | 47 | 0 | 0 | 0.10s |
| **合计（含重复）** | **585** | 0 | 0 | **6.50s** |

> **唯一用例数 = 316**（269 debug + 47 core）；`testReleaseUnitTest` 的 269 条为**完全重复执行**。

### 7.2 单套件耗时 Top（debug 变体）

| 耗时 | 用例 | 套件 |
|---:|---:|---|
| 0.762s | 9 | `crash.CrashLoggerTest`（含文件 I/O） |
| 0.573s | 8 | `qa.QaCrashHardeningVerificationTest` |
| 0.421s | 11 | `data.DataLayerContractAdversarialTest` |
| 0.401s | 10 | `ui.navigation.NavigationRouteInitRegressionTest`（隔离类加载器） |
| 0.138s | 13 | `qa.QaRound2FinalVerificationTest` |
| 0.136s | 26 | `ui.TrashViewModelTest` |

> 无慢用例：最慢 0.76s，全部 < 1s。

### 7.3 全量扫描命中的「可疑断言」原始输出

```
$ grep -rn "assertTrue(true\|assertFalse(false)" --include=*.kt .
app/src/androidTest/java/com/ebbinghaus/memo/data/local/MigrationTest.kt:95:  assertTrue(true)

$ grep -rEn "assertEquals\(([^,]+),\s*\1\)" --include=*.kt .
app/src/test/java/com/ebbinghaus/memo/data/QaAdversarialDataTest.kt:241:  assertEquals(codec.decode(json).getOrThrow(), codec.decode(json).getOrThrow())
```

### 7.4 变体对比实测（验证「release 变体单测是否必要」）

| 命令 | 结果 | 耗时 | 执行任务数 |
|---|---|---:|---:|
| `./gradlew test --rerun-tasks`（全量，含两变体） | BUILD SUCCESSFUL | **1m 32s** | 57 |
| `./gradlew :app:testDebugUnitTest :core:test --rerun-tasks`（仅 debug + core） | BUILD SUCCESSFUL | **29s** | 30 |

- **结论**：关闭 release 变体单测，**节省 63s / −68%**，且**唯一用例覆盖完全不变**（两变体的 269 条实测逐条等价：各 42 个结果文件、3.25s vs 3.15s）。
- 根因：`app/src/release` 为空目录；R8/minify **不参与**本地单测；debug 专属源集（3 个 Preview 文件）**无任何测试引用**。

---

## 8. 结论速览

| 问题 | 结论 |
|---|---|
| 317 条是否过多？ | **偏多，但属「结构性冗余」而非「数量失控」**。约 46 条（14.5%）确证冗余，12 条疑似；剔除后 ≈ 259~271 条，落在合理区间。 |
| 是否有废弃测试？ | **严格意义的「废弃」（守护已删除功能）= 0 条**。存在 2 条空转断言、1 条「断言缺陷存在」的脆弱反回归用例。 |
| 最值得先做的一件事 | **执行 A 档（`Qa*` 文件语义化归并）+ B 档（删 46 条确证重复）**，并把 `./gradlew test` 的验收基线固定为「**316 passed / 0 failed**」。收益最大、风险最低、可一次验证。 |
| 第二件事 | **关闭 release 变体单测**（`app/src/release` 为空、R8 不参与单测）——全量耗时 92s → ~60s，覆盖零损失。 |
