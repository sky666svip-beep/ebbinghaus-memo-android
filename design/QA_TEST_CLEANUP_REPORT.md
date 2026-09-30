# 测试资产整理（A+B 档）+ 反回归雷修复 —— 独立验证报告

- **验证人**：Edward / 严过关（QA）
- **验证日期**：2026-09-18
- **验证对象**：`design/TEST_CLEANUP_REPORT.md`（工程师自述）所声称的全部改动
- **验证性质**：**只读复核**。未修改、删除、重命名任何文件；未新增测试。所有结论均附本人亲自核对的命令输出 / 文件:行号。
- **设备声明**：本机**无模拟器 / 真机**，`app/src/androidTest` 无法运行，仅验证其**编译通过**（未启动任何模拟器）。

---

## 1. 最终判定 + 智能路由判定

| 项 | 结论 |
|---|---|
| **最终判定** | **通过（PASS）** —— 构建全绿（271 JVM 唯一用例 / 0 failed）、95 条 `Qa*` 迁移对账**闭合**、45 条删除与 `TEST_AUDIT.md §3.1` **逐条一致**、反回归雷**真修好**、`§6` 必须保留项**一条未少**、生产代码仅 1 处改动。 |
| **智能路由判定** | **NoOne**（无需返工；本轮验收通过） |
| **附带建议** | 发现 **1 处轻微覆盖缺口**（非阻塞，见 §7-F1）+ 1 处可忽略缺口（§7-F2）。若追求「零缺口」，建议路由 **Engineer** 补 2~3 条断言，**但不影响本轮验收**。 |
| **对账是否闭合** | ✅ **闭合**：95（原）= 57（归位）+ 38（Qa 删除）。 |
| **45 条删除是否有问题** | ⚠️ **清单一致（无多删/少删）**，但其中 1 条（#25）的「等价」判定有瑕疵，删后**丢了 2 条断言**（见 §7-F1）。 |
| **反回归雷是否真修好** | ✅ **是**。`catch(CancellationException)` 在 `catch(Exception)` **之前**（`:78` < `:81`）；新断言**强于**旧断言。 |
| **是否发现保护被削弱** | ⚠️ **发现 1 处轻微削弱**（`MemoListBatchFailureTest` 立即删除路径的弹窗状态断言丢失），其余全部**只增不减**。 |

---

## 2. A · 实测构建与测试数字/耗时

### 2.1 命令与真实输出（本人实跑）

```bash
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11
./gradlew test --console=plain --rerun-tasks
```

```
BUILD SUCCESSFUL in 19s
57 actionable tasks: 57 executed
Configuration cache entry reused.
real  0m20.479s
```

### 2.2 XML 结果解析（`app/build/test-results/**`、`core/build/test-results/**`）

| 测试任务 | 用例数 | 失败 | 错误 | 跳过 | 结果文件 |
|---|---:|---:|---:|---:|---:|
| `:app:testDebugUnitTest` | **224** | 0 | 0 | 0 | 26 |
| `:app:testReleaseUnitTest` | **224** | 0 | 0 | 0 | 26 |
| `:core:test` | **47** | 0 | 0 | 0 | 5 |
| **唯一 JVM 用例（debug + core）** | **271** | **0** | **0** | **0** | — |
| `androidTest/MigrationTest`（不执行，需真机） | 1 | — | — | — | — |
| **`@Test` 总数（含 androidTest）** | **272** | | | | |

> ✅ 与工程师自述「272 / 0 failed」**完全一致**。XML 文件 mtime = `09:26:21`（构建 `START 09:26:01`），证明测试**确实重跑**，非缓存跳过。

### 2.3 「19s vs 1m32s」降幅核实 —— 结论：**真实，但属「热跑 vs 冷跑」差异，非「没重跑全部任务」**

本人做了三组对照（均带 `--rerun-tasks`，均 `57 actionable tasks: 57 executed`）：

| 场景 | 命令 | 耗时（real） |
|---|---|---:|
| 热跑（默认：配置缓存 + 构建缓存 + 热守护进程） | `test --rerun-tasks` | **20.479s** |
| 关配置缓存 | `test --rerun-tasks --no-configuration-cache` | **23.197s** |
| **全冷**（`--stop` 停守护进程 + `--no-build-cache` + `--no-configuration-cache`） | 同上 | **1m4.316s** |

- ✅ **19s 是真的**，且**确实重跑了全部 57 个任务**（`57 actionable tasks: 57 executed`，且 XML 时间戳晚于构建开始）。
- ✅ 全冷跑 **1m3s**，与审计报告记录的 **1m32s** 同量级（差值来自 Kotlin 守护进程首次启动 / KSP 首次执行）。
- ⚠️ **口径提醒**：审计 §7.1 的 92s 是**冷跑**基线，工程师的 19s 是**热跑**。二者对比属于 apples-to-oranges，**不能解读为「用例数减少带来的提速」**（用例执行本身仅约 5.35s）。工程师报告 §5.2 未标注「热跑」，建议后续标注环境，但**数字本身未造假**。

---

## 3. B 🔴 · 95 条 `Qa*` 用例迁移对账（**必须闭合**）

### 3.0 口径说明（重要）

- 备份 `Qa*` 文件实际为 **6 个文件 / 9 个测试类 / 95 条用例**（任务书写的「7 个文件」应为笔误；`find` 实测 `Qa*.kt` = 6）。
- 对账采用严格定义：**「归位」= 该 Qa 用例在新结构中仍以任何形态存在（同名/改名/合并/被强版取代）；「删除」= 该 Qa 用例不再存在**。
- 工程师报告 §2.3 称「迁入 50 + 删除 45 = 95」——**该框算有误**：45 条删除里含 **7 条是「功能文件弱版」**（rating 5 + draft 1 + AdversarialM1 1），并非 Qa 用例。**正确口径：Qa 归位 57 + Qa 删除 38 = 95**（下方已闭合）。

### 3.1 迁移对账总表（**闭合**）

| 原 Qa 文件 | 原测试类 | 原数 | 归位 | 删除 |
|---|---|---:|---:|---:|
| `data/QaAdversarialDataTest.kt` | `ExportCodecAdversarialTest` | 5 | 5 | 0 |
| | `TrashRetentionBoundaryAdversarialTest` | 2 | 2 | 0 |
| | `SoftDeleteRestoreAdversarialTest` | 7 | 7 | 0 |
| `qa/QaCrashHardeningVerificationTest.kt` | `QaCrashHardeningVerificationTest` | 8 | 7 | 1 |
| `qa/QaP2IndependentVerificationTest.kt` | `QaTokenizerBoundaryTest` | 7 | 1 | 6 |
| | `QaP2_10_TokenizerContractTest` | 3 | 3 | 0 |
| | `QaP2_10_RepositoryTokenizerParityAdversarialTest` | 4 | 4 | 0 |
| | `QaUndoTokenAdversarialTest` | 6 | 1 | 5 |
| | `QaTrashStateMachineAdversarialTest` | 4 | 1 | 3 |
| | `QaAppendTagBoundaryTest` | 3 | 0 | 3 |
| | `QaDailyLimitBranchTest` | 3 | 1 | 2 |
| `qa/QaRound2FinalVerificationTest.kt` | `QaRound2FinalVerificationTest` | 13 | 6 | 7 |
| `ui/QaAdversarialUiTest.kt` | `MemoSortDeterminismAdversarialTest` | 3 | 3 | 0 |
| | `BatchTagMergeAdversarialTest` | 2 | 2 | 0 |
| `ui/QaFixIndependentVerificationTest.kt` | `QaSelectionModeAdversarialTest` | 5 | 2 | 3 |
| | `QaBatchFailureRollbackAdversarialTest` | 9 | 4 | 5 |
| | `QaRatingSemanticsAlgorithmConsistencyTest` | 5 | 5 | 0 |
| | `QaDraftStateBoundaryTest` | 6 | 3 | 3 |
| **合计** | | **95** | **57** | **38** |

**闭合校验：57 + 38 = 95 ✅**

### 3.2 38 条「Qa 删除」逐条归属（全部落在 `TEST_AUDIT.md §3.1` 清单内，**无清单外误删**）

| 归属批次 | 条数 | 审计 §3.1 编号 |
|---|---:|---|
| QaTokenizerBoundaryTest | 6 | #1–#6 |
| QaUndoTokenAdversarialTest | 5 | #7–#11 |
| QaTrashStateMachineAdversarialTest | 3 | #12–#14 |
| QaAppendTagBoundaryTest | 3 | #15–#17 |
| QaDailyLimitBranchTest | 2 | #18–#19 |
| QaSelectionModeAdversarialTest | 3 | #20–#22 |
| QaBatchFailureRollbackAdversarialTest | 5 | #23–#27 |
| QaCrashHardeningVerificationTest | 1 | #37 |
| QaRound2FinalVerificationTest | 7 | #38–#44 |
| QaDraftStateBoundaryTest | 3 | #33/#34/#36 |
| **合计** | **38** | 全部在清单内 ✅ |

### 3.3 57 条「Qa 归位」去向（按目标文件）

| 目标文件（语义化） | 迁入 Qa 条数 | 来源 |
|---|---:|---|
| `data/ExportCodecRoundTripTest.kt` | 5 | ExportCodecAdversarial（含 1 条空转→实质） |
| `data/MemoRepositoryTokenizerContractTest.kt` ★新建 | 7 | TokenizerContract(3) + Parity(4) |
| `data/MemoRepositoryWriteContractTest.kt` ★新建 | 8 | SoftDeleteRestore(7) + cutoffBoundary(1) |
| `data/trash/TrashRetentionTest.kt` | 1 | remainingDays_atDay29_30_31 |
| `ui/MemoListSortTest.kt` | 3 | SortDeterminism |
| `ui/MemoListSelectionTest.kt` | 4 | Selection(2) + BatchTagMerge(2) |
| `ui/MemoListBatchFailureTest.kt` | 4 | BatchFailureRollback |
| `ui/MemoListUndoTest.kt` | 1 | tokenReplayAfterSuccessfulUndo |
| `ui/MemoListViewModelTest.kt` | 3 | observeAllTags 加固 |
| `ui/RatingSemanticsConsistencyTest.kt` | 5 | RatingSemantics（强版取代弱版） |
| `ui/MemoEditorDraftStateTest.kt` | 3 | DraftStateBoundary |
| `ui/ReviewDailyLimitTest.kt` | 1 | negativeLimit |
| `ui/StartupCoroutineBoundaryTest.kt` | 8 | CrashHardening(4) + Round2(4，含反回归雷改造) |
| `ui/TrashViewModelTest.kt` | 1 | batchDeleteFailure_keepsSelectionAndItems |
| `core/SearchQueryTokenizerTest.kt` | 1 | veryLongSingleWord_isNotSplit |
| **合计** | **57** | ✅ |

### 3.4 两个新建契约文件评估 —— **真材实料，非换壳/空壳**

| 文件 | 条数 | 评估证据 |
|---|---:|---|
| `data/MemoRepositoryTokenizerContractTest.kt` | 7 | 自建 `RecordingMemoDao`（记录 `searchMemos`/`getTrashedMemosFiltered` 实际下发的 6 元关键词）+ `NoopReviewTaskDao`，注入**生产 `MemoRepositoryImpl`**，实证「仓储下发词集 == `SearchQueryTokenizer.activeKeywords`」。断言具体到 `listOf("算法","信息论","","","","")`（逐槽位）。**全仓唯一跨层观测点**，与备份 `QaP2_10_*` 两类逐条等价。✅ |
| `data/MemoRepositoryWriteContractTest.kt` | 8 | 自建 `InMemoryMemoDao`（忠实模拟单列更新 SQL 语义）+ `RecordingTaskDao`（计数写操作），注入**生产 `MemoRepositoryImpl`**。覆盖软删/还原逐字段一致、**绝不触碰 review_tasks**、批量单语句、空表 no-op、`addTagToMemos` 去重/保序、`purgeExpiredTrash` cutoff 边界、`clearTrash` 委派。与备份 `SoftDeleteRestoreAdversarialTest`(7) **逐断言等价**，另 +1 cutoff 边界。✅ |

> 结论：两个新文件均为**注入生产实现 + 记录型替身**的实质契约测试，**不是**把原用例换壳，也**不是**空壳。新增 `StubMemoDao`/`StubTaskDao` 抽象基类只是为「DAO 接口新增方法」提供默认 stub，属合理工程写法。

### 3.5 合并点抽查（≥5 处，逐一核对「断言是否被削弱」）

| # | 合并点 | 备份原断言 | 现状断言 | 判定 |
|---|---|---|---|---|
| 1 | rating 5 条：功能弱版 → Qa 强版 | `allRatings_haveNonBlankSemantics` 仅遍历断言非空 | `semanticsMap_coversExactlyAllFiveRatings` **增** `size == entries.size` | **增强** ✅ |
| 2 | rating：`forgetSemantics_matchesRollbackOneStageAlgorithm`（2 档） | STAGE_5→4、STAGE_1→1 | `..._forEveryStage` **遍历全部 ReviewStage** + 长周期退回 STAGE_6 | **增强** ✅ |
| 3 | draft：`tagsChanged_returnsTrue` → `tagsOnlyChange_isTrue` | 1 条断言 | 2 条断言（含「无标签→新增」） | **增强** ✅ |
| 4 | batch failure：4 条迁入 `MemoListBatchFailureTest` | 与备份逐条相同 | 逐条相同（`saveNew/saveEdited/saveBlank/batchAddTagSuccess`） | **等价** ✅ |
| 5 | selection：4 条迁入 `MemoListSelectionTest` | 与备份逐条相同 | 逐条相同 | **等价** ✅ |
| 6 | sort：3 条迁入 `MemoListSortTest` | 与备份逐条相同（含乱序+并列键） | 逐条相同 | **等价** ✅ |
| 7 | tokenizer contract：7 条迁入新文件 | 与备份逐条相同 | 逐条相同 | **等价** ✅ |
| 8 | crashlogger：2 条迁入 `CrashLoggerTest` | 与备份逐条相同 | 逐条相同 | **等价** ✅ |
| 9 | startup：8 条迁入 `StartupCoroutineBoundaryTest` | 与备份逐条相同 | 逐条相同（含反回归雷改造，见 §5） | **等价/增强** ✅ |
| 10 | exportcodec：5 条迁入 `ExportCodecRoundTripTest` | 与备份逐条相同 | 逐条相同（1 条空转→实质） | **等价/增强** ✅ |

> 抽查结论：**除 §7-F1 的 #25 外，未发现任何合并点断言被削弱**；rating/draft 两组的「保留强版、删弱版」方向执行正确。

---

## 4. C · 45 条删除核对 + 保留第 45 条的判断

### 4.1 实际删除构成（与清单一致性）

- **总净删除 = 45 条**（`317 → 272`，`@Test` 实测闭合）。
- 构成：**38 条 Qa 用例**（§3.2）+ **7 条「功能文件弱版」**（rating 5 + draft `tagsChanged_returnsTrue` 1 + `AdversarialM1StressTest.testAdversarial_negativeDailyLimit...` 1）。
- 对照 `TEST_AUDIT.md §3.1`（46 行）：**45 行被删除 + #45 保留 = 46**，**逐条一致，无多删、无少删**。✅

### 4.2 抽查 ≥8 条被删用例的「等价性」（到备份核对原用例）

| 审计编号 | 被删用例（备份） | 保留的等价/更强对照（现状） | 等价性核验 |
|---|---|---|---|
| #7 | `oldTokenAfterSecondDelete_isIgnored` | `MemoListUndoTest.secondDelete_overwritesToken_oldKeyBecomesInvalid` | 同事件序列 + 4 断言（令牌不同 / 旧令牌不还原 / 新令牌还原 #2 / #1 保持删除）**逐断言一致** ✅ |
| #8 | `unknownToken_isIgnored` | `MemoListUndoTest.unknownKey_isIgnoredWithoutSideEffect` | 同 ✅ |
| #9 | `expiredEntry_undoReportsExpiredWithoutCrash` | `MemoListUndoTest.undo_whenEntryAlreadyPurged_reportsExpiredWithoutCrash` | 同 ✅ |
| #12 | `selectAll_coversAllVisibleItems` | `TrashViewModelTest.selectAll_thenClearSelection` | Qa 是子集（对方另断言清空后保持多选态）✅ |
| #15 | `duplicateAcrossAnySeparator_isNoOp` | `MemoEditorTagInputTest.appendDetectsExistingAcrossAllSeparators` | 3 条分隔符断言一致 ✅ |
| #18 | `zeroLimit_yieldsEmptyBatchAndCompleted` | `ReviewDailyLimitTest.zeroLimit_withPendingTasks_yieldsEmptyBatchAndCompleted` | 保留版**更强**（补了 pending 任务）✅ |
| #38 | `crashLogger_handler_mustInvokeOriginalHandler_andWriteCrash` | `CrashLoggerTest.handler_delegatesToPreviousHandler_andWritesCrash` | 同（previousCalled / assertSame / hasCrashRecord）✅ |
| #40 | `crashLogger_retention_deletesOldestBeyondLimit`（max=2） | `CrashLoggerTest.writeCrash_retainsOnlyLatestThreeRecords`（max=3） | 保留策略语义一致 ✅ |
| #46 | `testAdversarial_negativeDailyLimit_gracefulFallbackToZero`（-100/2 条） | `RolloverEngineTest.testDailyLimitNegative_handledAsZero`（-5/1 条） | 同 4 断言（0 批 / 顺延 / 总数 / isLimitReached）✅ |
| **#25** | `immediateDeleteFailure_keepsDataAndEmitsFailure_withoutOpeningDialog` | `MemoListBatchFailureTest.immediateDeleteFailure_emitsFailure` | ⚠️ **Qa 版是超集**（多 2 条弹窗断言），删后**丢失** → 见 §7-F1 |

> 8/9 条抽查**等价成立**；**#25 例外**（审计原判「≡」不准确，实为「Qa ⊃ 功能版」）。

### 4.3 工程师「保留第 45 条」的判断 —— **本人认同**

- 第 45 条 `decode_isPure_doesNotDependOnPriorEncode` 的**空转根因是断言写法**（`assertEquals(decode(json), decode(json))` 同入参自比，恒真），**而非主题无价值**（主题「decode 无跨调用隐藏状态」确有守护意义）。
- 本轮任务第 4 件明确要求把它「改为实质断言」，与 B 档「删除」冲突时**以更具体指令为准**——该取舍**合理**。
- 迁移后 `ExportCodecRoundTripTest.decode_isStateless_independentOfPriorCalls`（`:242`）**确为可证伪的实质断言**：先解码「被污染载荷」，再解原载荷，断言仍逐字段等于原始对象（若存在隐藏状态必失败）。✅
- **结论：认同保留 + 强化**，且这是本轮**唯一**「删除 vs 强化」的独立判断，判断质量高。

---

## 5. D · 反回归雷修复复核

### 5.1 生产代码 `ui/detail/MemoDetailViewModel.kt`（本人实读）

| 检查项 | 位置 | 结论 |
|---|---|---|
| `catch (CancellationException)` **在** `catch (Exception)` **之前** | `:78` `catch (cancellation: CancellationException)` → `:80` `throw cancellation`；`:81` `catch (e: Exception)` | ✅ 顺序**正确**（若写反会吞掉取消信号） |
| 失败时**保留可用 UI 状态**（不白屏） | `:84` `_uiState.update { it.copy(isLoading = false) }` | ✅ 结束加载态、数据留空，**不白屏 / 不闪退** |
| 诊断 tag 统一 | `:18` `private const val LOG_TAG = "EbbinghausLaunch"`；`:83` `Log.e(LOG_TAG, ...)` | ✅ 与启动路径统一 |
| 生产改动范围 | 全仓 `app/src/main` + `core/src/main` + `baselineprofile/src/main` 中 **2026-09-17 之后**修改的文件 | ✅ **仅 `MemoDetailViewModel.kt`**（mtime `2026-09-18 09:19`），其余最新为 `09-16` |

### 5.2 翻转用例 `StartupCoroutineBoundaryTest.kt:414` —— **新断言强于旧断言**

| | 旧（备份 `QaRound2FinalVerificationTest.kt:290`） | 新（`StartupCoroutineBoundaryTest.kt:414`） |
|---|---|---|
| 名称 | `probe_isCapable_ofDetectingEscape_memoDetailInitStillUnbounded` | `memoDetailInit_dependencyThrows_isContainedByBoundary_andLogged` |
| 断言 1 | `assertTrue(escapes.isNotEmpty())` —— **断言缺陷存在** | `assertTrue(escapes.isEmpty())` —— **断言不逃逸** |
| 断言 2 | `escapes.any { it is IllegalStateException }` | `assertEquals(1, Log.eCount)` —— **非静默** |
| 断言 3 | — | `assertEquals(launchTag, Log.lastTag)`（=`"EbbinghausLaunch"`） |
| 断言 4 | — | `assertTrue(Log.lastThrowable is IllegalStateException)` |
| 语义 | 要求缺口继续存在（**反回归雷**，修复后必红） | 守护「边界生效 + 非静默」两事实，修复后**变绿** |
| 观测点 | 1 个 | **2 个**（不逃逸 + 有诊断日志） |

> ✅ 新断言**严格强于**旧断言（从「断言缺陷存在」翻转为「断言边界生效 + 非静默」），**不是削弱**。
> ✅ 探针机制稳健：新用例使用 `captureEscapes`（`Thread.setDefaultUncaughtExceptionHandler`，`:108-124`），**未**使用审计 §3.1 #37 指出的不稳定探针（`runTest{} → UncaughtExceptionsBeforeTest`）；且用例开头 `Log.reset()`（`:415`），无顺序依赖。
> ✅ 实跑通过（含在 224 条 debug 用例中，0 failed）。

---

## 6. E · 「必须保留」清单逐条核验 + 硬约束

### 6.1 `TEST_AUDIT.md §6` 必须保留项（**一条未少，多数只增不减**）

| 资产 | QA 期望 | 现状 | 核验 |
|---|---:|---:|---|
| `NavigationRouteInitRegressionTest` | 10 | **10** | ✅ 未动 |
| 跨层 tokenizer 契约（`MemoRepositoryTokenizerContractTest`） | 7 | **7** | ✅ 语义化改名保留 |
| `StartupCoroutineBoundaryTest` | 9 | **17** | ✅ 只增不减（吸收 Dashboard/Detail/Review/Settings 边界） |
| `CrashLoggerTest` | 9 | **11** | ✅ 只增不减 |
| `DataLayerContractAdversarialTest` | 11 | **11** | ✅ 未动 |
| `MemoRepositoryWriteContractTest`（原 `SoftDeleteRestoreAdversarialTest`） | 7 | **8** | ✅ 改名保留 +1 |
| `TrashViewModelTest` | 26 | **27** | ✅ 只增不减 |
| `SchemaV2ContractTest` | 4 | **4** | ✅ 未动 |
| `MigrationTest` | 1 | **1** | ✅ 保留（空转断言已改实质，编译通过） |
| `RatingSemanticsConsistencyTest` | 5 | **5** | ✅ 数量不变，断言换全档遍历强版 |
| `MemoListUndoTest` | 8 | **9** | ✅ 只增不减 |

> **无一条「必须保留」资产被削弱**；5 个文件用例数**净增**。

### 6.2 `AdversarialM1StressTest` 7 → 6 的核实

- 删除的 1 条 = `testAdversarial_negativeDailyLimit_gracefulFallbackToZero`（`-100` / 2 条任务）。
- 等价物 `RolloverEngineTest.testDailyLimitNegative_handledAsZero`（`-5` / 1 条任务）**仍在且断言不弱**（同样 4 条：0 批 / 顺延 / 总数 / isLimitReached）。
- 其余 6 条是「大规模/长迭代不变量」（100 条任务、连续 10 次忘记、30 天积压、连续 5 次跳过），**未动**。
- **结论：该 1 条确属冗余，删除成立。** ✅

### 6.3 硬约束核验

| 约束 | 状态 | 证据 |
|---|---|---|
| `./gradlew test` 全绿 | ✅ | BUILD SUCCESSFUL；271 唯一用例，0 失败/0 错误/0 跳过 |
| 零新增依赖 | ✅ | `app/build.gradle.kts` mtime `2026-09-16 17:11`（改动前），无 `libs.versions.toml` |
| `AndroidManifest` 零新增权限 | ✅ | `grep uses-permission` = 0 命中 |
| `compileSdk`/`targetSdk` = 35 | ✅ | `app/build.gradle.kts:11,16` |
| 无 Room schema 变更 | ✅ | `1.json`/`2.json` mtime `2026-09-15`，未动 |
| 不做 release 变体单测关闭（D 档） | ✅ 未做（符合「遗留」声明） | — |
| 不启动模拟器 | ✅ | 仅 `:app:compileDebugAndroidTestKotlin`（BUILD SUCCESSFUL），未运行 |
| 生产代码改动范围 | ✅ | **仅 `MemoDetailViewModel.kt`**（见 §5.1） |
| 全仓 test 源 `Qa` 残留 | ✅ **0** | `grep -rn "Qa" app/src/test core/src/test app/src/androidTest` = 0 |
| 恒真断言残留 | ✅ **0** | `assertTrue(true)`/`assertFalse(false)` = 0；自比 `assertEquals(x,x)` = 0 |

---

## 7. F · 是否发现保护被削弱 / 新引入脆弱断言

### F1 ⚠️【轻微覆盖缺口，唯一实质发现】立即删除路径的「不弹窗」断言丢失

- **现象**：被删的 Qa #25 `immediateDeleteFailure_keepsDataAndEmitsFailure_withoutOpeningDialog`（备份 `QaFixIndependentVerificationTest.kt:241`）含 **4 条断言**；保留的 `MemoListBatchFailureTest.immediateDeleteFailure_emitsFailure`（`:112`）只有 **2 条**。丢失的 2 条为：
  - `assertFalse("立即删除语义不得打开确认弹窗", state.isDeleteDialogVisible)`
  - `assertNull(state.pendingDeleteId)`
- **证据**：本人实读备份 `:249-250` 与现状 `:112-124`；并全仓 grep 确认**无任何其他用例**在 `OnDeleteMemo`（立即删除）路径上断言 `isDeleteDialogVisible == false` / `pendingDeleteId == null`（`MemoListDeleteConfirmTest:72-73` 属 `cancelDelete` 路径；`:135-136` 属 `confirmDelete` 路径）。
- **成因**：审计 §3.1 #25 的「≡ 等价」判定**不准确**——实为「Qa 版 ⊃ 功能版」，工程师据清单删除导致轻微削弱。
- **影响评估**：**轻微、非阻塞**。删除语义本身仍被 `MemoListDeleteConfirmTest`（请求删除→弹窗）与 `MemoListViewModelTest.testDeleteMemo_removesEntity`（立即删除→移除）区分覆盖，**唯一丢失的是「立即删除不得弹窗」这条反向断言**。
- **恢复建议**：在 `MemoListBatchFailureTest.immediateDeleteFailure_emitsFailure`（`:119` 后）补 2 行：
  ```kotlin
  assertFalse("立即删除语义不得打开确认弹窗", viewModel.uiState.value.isDeleteDialogVisible)
  assertNull("立即删除不得设置 pendingDeleteId", viewModel.uiState.value.pendingDeleteId)
  ```

### F2 ⚠️【可忽略缺口】全角空格空白标签用例丢失

- 被删 Qa #16 `blankTagOrBlankInput_isHandled`（备份 `QaP2:510`）含 `assertEquals("", appendTagToInput("", "　"))`（全角空格 U+3000）。保留的 `MemoEditorTagInputTest.appendBlankTag_isNoOp` 只测了半角空白，**全角空白分支未覆盖**。
- **影响**：可忽略（生产 `appendTagToInput` 用 Kotlin `trim()`，对 U+3000 与半角空白行为一致；半角已覆盖）。
- **恢复建议**（可选）：在 `appendBlankTag_isNoOp` 补 `assertEquals("", appendTagToInput("", "　"))`。

### F3 未发现「新引入的脆弱断言」

| 检查项 | 结论 |
|---|---|
| 依赖执行顺序 | ✅ 未发现。`StartupCoroutineBoundaryTest` 有 `@Before resetLogProbe()` + 用例内 `Log.reset()`；新契约文件 `dao` 为**每用例新实例**（JUnit 每方法新建测试类），无跨用例状态泄漏 |
| 依赖具体实现细节 | ✅ 新契约测试断言的是**契约**（词集恒等、逐字段一致、cutoff 边界），非实现内部结构 |
| 全局副作用 | ⚠️ `captureEscapes` 会临时改写 `Thread.setDefaultUncaughtExceptionHandler`、`Log` 替身为静态共享状态——**属既有测试基建模式，非本轮新引入**；当前 `maxParallelForks` 未开启并行（默认串行），**不构成 flaky**。建议后续若开启测试并行需复核。 |

---

## 8. 遗留与未验证项

1. **C 档（12 条疑似冗余）**：工程师**未删除**，已按语义化迁入（`MemoListSortTest` / `MemoListSelectionTest` / `ExportCodecRoundTripTest` / `TrashRetentionTest` / `MemoListViewModelTest` / `CrashLoggerTest` / `StartupCoroutineBoundaryTest`）。本人已逐条确认 12 条**全部存在**（§3.3 归位表）。**未做 = 符合声明**。
2. **D 档（关闭 release 变体单测）**：**未实施**（符合声明）。实测 release 变体仍跑 224 条（与 debug 逐条重复）。
3. **`androidTest/MigrationTest` 未运行**：本机无设备，**仅编译通过**（`:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL）。其空转断言已改为实质断言（`assertTrue(true)` → 4 组真实 `db.query(...).assertEquals(...)`），**待有真机时执行**。
4. **未验证项**：release 变体的 224 条与 debug 是否逐条等价——本轮仅确认**用例数一致**（224 = 224），未做逐条 diff（与审计 §7.4 结论一致）。
5. **数字口径提醒**：工程师报告 §2.3「迁入 50 + 删除 45」的**归位/删除拆分口径有误**（正确为 Qa 归位 57 + Qa 删除 38；45 条删除 = 38 Qa + 7 功能弱版），但**总量与构建结果均正确**；建议后续修订该行表述。§9「修改的既有测试文件（14）」亦与所列 16 条不符（实为 16 改 + 2 新建 + 6 删除），属笔误。

---

## 9. 附：本人亲自执行的核验命令（关键摘录）

```bash
# 1) 全量测试（热跑）
./gradlew test --console=plain --rerun-tasks        # BUILD SUCCESSFUL in 19s / 57:57 executed

# 2) XML 计数
grep -ho 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | ...   # 224
#    core/build/test-results/test/*.xml → 47；testReleaseUnitTest → 224；failures/errors/skipped = 0

# 3) 冷跑对照
./gradlew --stop && ./gradlew test --rerun-tasks --no-build-cache --no-configuration-cache   # 1m3s

# 4) 备份核对
find .workbuddy-ai/backup-tests-20260918 -type f | wc -l                          # 40
find .workbuddy-ai/backup-tests-20260918 -type f -printf '%s\n' | awk '{s+=$1}END{print s}'  # 322447

# 5) 残留/恒真扫描
grep -rn "Qa" app/src/test core/src/test app/src/androidTest | wc -l              # 0
grep -rn "assertTrue(true)\|assertFalse(false)" app core                          # (空)
grep -rEn "assertEquals\(([^,]+),\s*\1\)" app core | wc -l                        # 0

# 6) 生产改动范围
find app/src/main core/src/main baselineprofile/src/main -name '*.kt' -newermt '2026-09-17'   # 仅 MemoDetailViewModel.kt
```

---

**验证人签署**：Edward / 严过关（QA）　**日期**：2026-09-18
**结论**：本轮 A+B 档 + 反回归雷修复 **整体通过**；`Qa*` 迁移对账**闭合**；45 条删除与清单**一致**；反回归雷**真修好**；`§6` 必须保留项**一条未少**。**唯一发现**：1 处轻微覆盖缺口（立即删除路径弹窗断言，§7-F1），**非阻塞**，建议按 §7-F1 补 2 条断言。
