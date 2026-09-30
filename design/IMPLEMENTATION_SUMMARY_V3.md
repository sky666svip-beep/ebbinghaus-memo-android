# 实现总结 V3 · 消除交互死区与语义不一致 + 回收站可管理（P2-1 ~ P2-10）

> 工程师：寇豆码 ｜ 依据：`ARCHITECTURE_INCREMENT_V3.md`（唯一实现基准）、`PRD_INCREMENT_V3.md`、`FIX_SUMMARY.md`
> 范围：**P2-1 ~ P2-10 共 10 项**，按任务列表 **T01 → T05** 严格顺序实现，每任务后编译/测试验证。

---

## 0. 结论速览

| 项 | 结果 |
|---|---|
| **任务完成** | **T01 / T02 / T03 / T04 / T05 全部完成** |
| **测试** | `./gradlew test` = **235 passed / 0 failed**（app **190** + core **45**；基线 **175**，**+60 净增**） |
| **编译告警** | **0**（`grep -c 'warning:'` = 0） |
| **APK 体积** | debug **17,784,781 B**（16.96 MiB）／ release **1,742,978 B**（1.6622 MiB） |
| **release 体积增幅** | **+16,384 B ≈ +16.0 KiB**（红线 ≤ 100 KB ✅） |
| **Manifest 权限** | **零新增**（仅 androidx.core 自动注入的自签名权限，非敏感、非本次新增） |
| **SDK** | minSdk 26 ／ compileSdk 35 ／ targetSdk 35（**未升 36**） |
| **新增依赖** | **零**（`app/build.gradle.kts`、`core/build.gradle.kts` **未改动**） |
| **Room schema** | **无变更**（仅新增只读 / 批量 DAO 方法） |
| **全局一致性审查** | **IS_PASS: YES**（一轮通过） |

**一句话**：10 项 P2 需求全部落地，既有 175 条断言**零删改**，零告警、零新依赖、零新权限、无 schema 变更，release 体积增幅 16 KB 远低于 100 KB 红线。

---

## 1. 任务完成表

| 编号 | 任务 | 状态 | 验收要点 | 证据 |
|---|---|---|---|---|
| **T01** | 口径统一 + 数据层增量 | ✅ | `SearchQueryTokenizer` 落地并被仓储使用；3 个 DAO + 3 个 Repository 方法；**4 处测试替身同步**；`getTrashedMemosFiltered` 关键词含 tags、3 种排序；`restoreMemos` 单事务返回影响行数；`hardDeleteMemos` CASCADE | `core:test` 45 全绿；`app:test` 编译通过 |
| **T02** | 列表页撤销 + 标签筛选 + 6 词提示 + 全选文案 + 高亮口径 | ✅ | 撤销型 Snackbar（`actionLabel="撤销"` + `actionKey`）、5000ms 窗口、令牌覆盖、标签 Chip→筛选、6 词轻提示、「全选当前结果」、高亮 == 前 6 词 | app 152 → 加 `MemoListUndoTest`(8) |
| **T03** | 编辑器标签候选 + 复习页暂停态 | ✅ | `MemoEditDialog(allTags)` 默认值保护；候选 Chip 追加去重；`dailyLimit<=0` 暂停页 + 去设置；`>0 且队列空` 仍庆祝页 | app 164（+`MemoEditorTagInputTest`(8) + `ReviewDailyLimitTest`(4)） |
| **T04** | 回收站能力补齐 + 多选 | ✅ | 就地展开预览（含进度）、独立搜索（content+notes+tags）、3 种排序、多选批量还原/彻底删除（二次确认）、`BackHandler` 扩展、双空态 | app 190（+`TrashViewModelTest`(26)） |
| **T05** | 集成回归 | ✅ | 全量 `test --rerun-tasks`、`assembleDebug`/`assembleRelease`、零告警、Manifest/sdk、既有断言未删改 | 见 §4 / §5 |

---

## 2. 文件清单（含行数）

### 2.1 新增（8 个）

| # | 路径 | 行数 | 职责 |
|---|---|---|---|
| N1 | `core/src/main/kotlin/com/ebbinghaus/memo/core/util/SearchQueryTokenizer.kt` | 54 | 关键词切分唯一真源（`tokenize`/`activeKeywords`/`ignoredCount`） |
| N2 | `core/src/test/kotlin/com/ebbinghaus/memo/core/SearchQueryTokenizerTest.kt` | 104 | 切分口径单测（14 例） |
| N3 | `app/src/main/java/com/ebbinghaus/memo/data/local/entity/TrashedMemoWithProgress.kt` | 26 | 回收站只读投影（`@Embedded memo` + 可空 `stageLevel/dueDate/reviewCount`） |
| N4 | `app/src/main/java/com/ebbinghaus/memo/data/repository/TrashSortOption.kt` | 29 | 回收站排序枚举（ordinal = sortKey） |
| N5 | `app/src/test/java/com/ebbinghaus/memo/ui/MemoListUndoTest.kt` | 187 | 撤销单测（8 例） |
| N6 | `app/src/test/java/com/ebbinghaus/memo/ui/TrashViewModelTest.kt` | 428 | 回收站单测（26 例） |
| N7 | `app/src/test/java/com/ebbinghaus/memo/ui/MemoEditorTagInputTest.kt` | 76 | 标签追加去重纯函数单测（8 例） |
| N8 | `app/src/test/java/com/ebbinghaus/memo/ui/ReviewDailyLimitTest.kt` | 92 | `dailyLimit` 分支单测（4 例） |

> 注：架构文件清单的 N2/N5/N6/N7 已在表内；N8 为 P2-9 暂停态补强单测（架构 M11/M12 未显式列测试，属加固项）。

### 2.2 修改（18 个）

| # | 路径 | 行数 | 改动摘要 |
|---|---|---|---|
| M1 | `app/src/main/java/com/ebbinghaus/memo/data/local/dao/KnowledgeMemoDao.kt` | 178 | +`restoreByIds`(130)、`hardDeleteByIds`、`getTrashedMemosFiltered`(LEFT JOIN + 显式别名 + tags 入搜索) |
| M2 | `app/src/main/java/com/ebbinghaus/memo/data/repository/MemoRepository.kt` | 110 | +3 接口方法 |
| M3 | `app/src/main/java/com/ebbinghaus/memo/data/repository/MemoRepositoryImpl.kt` | 201 | +3 实现（单事务）；`searchMemos` 改用 tokenizer |
| M4 | `app/src/main/java/com/ebbinghaus/memo/ui/memolist/MemoListViewModel.kt` | 610 | Effect 扩展 `actionLabel/actionKey`；+`OnSnackbarAction`；单值撤销令牌；确认删除/批删发撤销型 Snackbar |
| M5 | `app/src/main/java/com/ebbinghaus/memo/ui/memolist/MemoListScreen.kt` | 940 | `collectLatest`+撤销/超时；`activeKeywords` 高亮；6 词提示；标签 Chip→`OnTagSelected`；`scrollToItem(0)`；「标签：T」指示条；`allTags` |
| M6 | `app/src/main/java/com/ebbinghaus/memo/ui/memolist/MemoEditDialog.kt` | 291 | +`allTags` 默认参 + 候选 `SuggestionChip` 追加去重 |
| M7 | `app/src/main/java/com/ebbinghaus/memo/ui/memolist/MemoEditorDraftState.kt` | 51 | +纯函数 `appendTagToInput` |
| M8 | `app/src/main/java/com/ebbinghaus/memo/ui/component/SelectionActionBar.kt` | 111 | 泛化为 `actions: List<SelectionActionItem>`；「全选当前结果」 |
| M9 | `app/src/main/java/com/ebbinghaus/memo/ui/trash/TrashViewModel.kt` | 343 | 搜索/排序/展开/多选/批量状态与事件；数据源改 `getTrashedMemosFiltered`；`catching{}` |
| M10 | `app/src/main/java/com/ebbinghaus/memo/ui/trash/TrashScreen.kt` | 684 | 搜索栏 + 排序弹窗 + 就地展开预览 + 多选底栏 + 批量二次确认 + 双空态 |
| M11 | `app/src/main/java/com/ebbinghaus/memo/ui/review/ReviewViewModel.kt` | 240 | `ReviewUiState.dailyLimit`；`loadReviewBatch` 落值 |
| M12 | `app/src/main/java/com/ebbinghaus/memo/ui/review/ReviewScreen.kt` | 601 | +`onNavigateToSettings`；`dailyLimit<=0` 暂停页 |
| M13 | `app/src/main/java/com/ebbinghaus/memo/ui/navigation/AppNavigation.kt` | 481 | 上提 `TrashViewModel`；`BackHandler` 扩展回收站；`selectionBar` 用新 API；`ReviewScreen` 传参 |
| M14 | `app/src/test/java/com/ebbinghaus/memo/ui/FakeRepositories.kt` | 343 | `FakeMemoRepository` 实现 3 方法（内存版）+ `reviewCount` |
| M15 | `app/src/debug/java/com/ebbinghaus/memo/ui/preview/PreviewFakes.kt` | 330 | `PreviewMemoRepository` 实现 3 方法 |
| M16 | `app/src/test/java/com/ebbinghaus/memo/data/DataLayerContractAdversarialTest.kt` | 451 | `StubKnowledgeMemoDao` 补 3 stub |
| M17 | `app/src/test/java/com/ebbinghaus/memo/data/QaAdversarialDataTest.kt` | 389 | `StubMemoDao` 补 3 stub |
| M18 | `app/src/debug/java/com/ebbinghaus/memo/ui/preview/PagePreviews.kt` | 189 | `ReviewScreen(...)` 补 `onNavigateToSettings = {}` |

---

## 3. 十项需求验收表（PRD §3）

| 编号 | 需求 | 状态 | 证据（`file:line`） |
|---|---|---|---|
| **P2-1** | 删除后 5 秒撤销 | ✅ | `MemoListViewModel.kt:139`（`OnSnackbarAction`）、`:190-193`（单值令牌）、`:387-398`/`:512-519`（发撤销型 Snackbar，`actionLabel="撤销"`+`actionKey`）、`:566-571`（令牌比对，旧回调失效）、`:594`（`nextUndoToken`）；`MemoListScreen.kt:104`（`UNDO_WINDOW_MS=5_000`）、`:314`（`collectLatest`）、`:324`（`withTimeoutOrNull`）。单测 `MemoListUndoTest`（8 例，含「连删两条仅最新可撤销」「过期无操作」「立即删除语义不变」） |
| **P2-2** | 回收站就地预览 | ✅ | `TrashScreen.kt`（`TrashItem` 折叠 3 行 + 展开态完整内容/笔记/标签 + `TrashProgressSection` 档位/下次到期/已复习次数）；`TrashViewModel.kt`（`expandedIds` 支持多条并存）；投影 `TrashedMemoWithProgress.kt`。单测 `TrashViewModelTest.toggleExpand_*` / `expand_exposesReviewProgressProjection` |
| **P2-3** | 回收站批量还原/彻底删除 | ✅ | `TrashViewModel.kt`（多选事件 + `OnBatchRestore` 免确认 + `OnConfirmBatchDelete` 二次确认）；`TrashScreen.kt`（多选底栏 + 含条数弹窗）；DAO `restoreByIds`/`hardDeleteByIds`（单事务，`MemoRepositoryImpl.kt:165-175`）。单测 `batchRestore_*`/`batchDelete_requiresConfirm_thenHardDeletes`/`batchDelete_failure_*` |
| **P2-4** | 回收站搜索/排序 | ✅ | `KnowledgeMemoDao.kt:147-169`（LEFT JOIN + tags 入搜索 + `ORDER BY CASE`）；`TrashViewModel.kt`（`combine(query,sort).flatMapLatest`，独立查询域）；`TrashScreen.kt`（搜索栏 + 排序 `ModalBottomSheet` 3 项）。单测 `search_*`/`sort_*`（含 `sort_tagAsc` 确定性序列 `[2,3,1]`） |
| **P2-5** | 标签 Chip 点击即筛选 | ✅ | `MemoListScreen.kt:899`（`onClick = { onTagClick(tag) }`）、`:530-531`/`:569-570`（→`OnTagSelected`）、`:453-454`（「标签：T」`FilterChip`）、`:501`/`:542`（`scrollToItem(0)`） |
| **P2-6** | 6 词上限轻提示 | ✅ | `MemoListScreen.kt:376`（`ignoredCount`），非模态 `labelSmall` 辅助文字 |
| **P2-7** | 「全选」文案澄清 | ✅ | `AppNavigation.kt:277`、`TrashScreen.kt:193`（`"全选当前结果"` / 已全选时 `"取消全选"`）；`SelectionActionBar.kt` 泛化 |
| **P2-8** | 标签输入交互统一 | ✅ | `MemoEditDialog.kt:73`（`allTags` 默认参）、`:236-238`（`SuggestionChip` → `appendTagToInput`）；`MemoEditorDraftState.kt:42`（纯函数）。单测 `MemoEditorTagInputTest`（8 例） |
| **P2-9** | `dailyLimit = 0` 正确文案 | ✅ | `ReviewScreen.kt:167-168`（`dailyLimit<=0` → `ReviewPausedView`）、`:558`（「今日已按设置暂停复习」）、`:582`（「去设置」）、`:173`（`>0 且队列空` → 原庆祝页）；`ReviewViewModel.kt:36`/`:99`/`:114`（`dailyLimit` 落值）。单测 `ReviewDailyLimitTest`（4 例） |
| **P2-10** | 搜索切分口径统一 | ✅ | `SearchQueryTokenizer.kt`（唯一真源）；被 `MemoRepositoryImpl`（仓储 6 元）、`MemoListScreen.kt:372`（高亮 `activeKeywords`）、`FakeRepositories.kt`、`PreviewFakes.kt` 共用；全项目无遗留 `split(" ", "　")` / `split("\\s+")`。单测 `SearchQueryTokenizerTest`（14 例，含全角空格/换行/Tab/恰好 6/7 词） |

**10 / 10 全部 ✅，无 ❌。**

---

## 4. 测试与构建实测

### 4.1 单测（`build/test-results/**/*.xml` 统计）

| 源集 | 基线 | 当前 | 净增 |
|---|---|---|---|
| `app:testDebugUnitTest` | 144 | **190** | +46 |
| `core:test` | 31 | **45** | +14 |
| **合计** | **175** | **235** | **+60** |

- app `testReleaseUnitTest` 亦 **190 / 0 failed**（双构建类型一致）。
- 失败 / 错误 / 跳过：**0 / 0 / 0**。
- 净增来源：`SearchQueryTokenizerTest`(14) + `MemoListUndoTest`(8) + `MemoEditorTagInputTest`(8) + `ReviewDailyLimitTest`(4) + `TrashViewModelTest`(26) = 60。
- **既有断言零删改**：既有测试文件仅「新增 stub 覆写」与「新增 @Test」，未改动任何既有断言。

### 4.2 编译告警

```
$ ./gradlew test --rerun-tasks assembleDebug assembleRelease --console=plain > t05.log
$ grep -c 'warning:' t05.log
0
```

**零编译告警。**

### 4.3 APK 体积

| 构建类型 | 当前（实测字节） | 基线（同口径） | 增幅 |
|---|---|---|---|
| debug | **17,784,781 B**（16.961 MiB） | 17,719,245 B（`QA_REPORT_V2.md`） | +65,536 B（+64.0 KiB） |
| **release** | **1,742,978 B**（1.6622 MiB） | 1,726,594 B（`QA_REPORT_V2.md`） | **+16,384 B（+16.0 KiB）** |

> release 增幅 **+16 KiB ≤ 100 KB 红线** ✅（debug 非红线）。
> 基线取自上一轮 QA 记录（同项目无 git 仓库，无法 `git stash` 重建；release 基线与 PRD「≈1.65 MB」一致）。

### 4.4 构建配置与产物

- `app/build.gradle.kts`：`compileSdk=35`、`targetSdk=35`、`minSdk=26` —— **未改动**。
- `core/build.gradle.kts`：仅 `junit` 测试依赖 —— **未改动**。
- 合并后 `AndroidManifest`（release/debug）：**唯一权限** `com.ebbinghaus.memo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（androidx.core 自动注入的自签名、非敏感权限；非本次新增）。`<uses-permission>` **零新增**。
- 合并 manifest：`minSdkVersion="26"`、`targetSdkVersion="35"`。

---

## 5. 全局一致性审查（一轮通过）

**审查范围**：全部 8 个新增 + 18 个修改文件作为整体，交叉检查导入一致性、接口契约、数据流、重复实现。

| 检查项 | 结论 | 证据 |
|---|---|---|
| 跨文件导入一致（无缺失/无循环） | ✅ | `:app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin`、`:app:compileReleaseKotlin`、`:core:compileKotlin` 全部成功 |
| 接口契约（3 DAO + 3 Repo 方法全实现） | ✅ | DAO 于 `KnowledgeMemoDao.kt:130/137/169`；Repo 于 `MemoRepositoryImpl.kt:150/165/171`；4 处替身（`DataLayerContractAdversarialTest.kt:43-45`、`QaAdversarialDataTest.kt:50-52`、`FakeRepositories.kt:197/237/252`、`PreviewFakes.kt:128/159/173`）**全部同步**（R1 风险已消除） |
| 数据流正确（投影字段对齐） | ✅ | `getTrashedMemosFiltered` 显式别名 `t.stageLevel AS stageLevel` / `t.dueDate AS dueDate` / `t.reviewCount AS reviewCount`（R5 风险已消除）；`TrashViewModel → TrashScreen` 消费 `TrashedMemoWithProgress` |
| 无重复实现 | ✅ | 切分逻辑**唯一**收敛于 `SearchQueryTokenizer`；全项目无遗留 `split(" ", "　")` / `split("\\s+")` |
| §8-4 软删除口径不变 | ✅ | `KnowledgeMemoDao.kt:130` `UPDATE ... SET deletedAt = NULL WHERE id IN (:ids) AND deletedAt IS NOT NULL`，绝不回写 content/notes/tags |
| §8-5 Effect 契约 | ✅ | `SharedFlow(replay=0, extraBufferCapacity=1)`；新增字段一律带默认值（`actionLabel/actionKey/dailyLimit/allTags`） |
| §8-12 文案红线 | ✅ | 主源码无新增「逾期/落后」惩罚性文案（仅「不标记逾期」保护性表述）；错误提示沿用既有「操作失败，请重试」 |

**IS_PASS: YES**

---

## 6. 两处已裁定项与三处架构风险点的落实

| 项 | 处理 |
|---|---|
| **R3（`collectLatest` 全局采用）** | 已按裁定**全局采用**（「后者覆盖前者」），并在 `MemoListScreen.kt:311-313` 注释**显式标注为有意行为** |
| **R4（回收站搜索含 tags）** | 已按 P2-4 验收①让回收站搜索**含 tags**；**未触碰**列表页 `searchMemos`（保持 content+notes），差异已在代码注释与本文档记录 |
| **R1（测试替身同步）** | 4 处替身**全部同步**，模块编译通过（详见 §5） |
| **R5（`@Embedded`+裸列映射）** | 显式别名 + `Converters` 的 `LocalDate?` 转换，编译通过 |
| **R2（`withTimeoutOrNull` 竞态）** | 超时后 `currentSnackbarData?.dismiss()` + 兜底文案「已移入回收站，可在设置中还原」；**无设备无法真机验证兜底文案是否偶发不显示**（见 §7） |

---

## 7. 遗留事项与诚实声明

1. **无真机/模拟器环境**：以下 Compose 行为**无法在 JVM 断言**，留待有设备环境手工验收（架构 R9）——
   - 撤销 Snackbar 的 **5 秒窗口**、`withDismissAction`、`Indeterminate`→兜底文案切换（含 R2 竞态兜底文案是否偶发不显示）；
   - 回收站**就地展开**动画（`animateContentSize`）、**长按手势**与单击展开的手势层级（边界 12）；
   - 标签 Chip 点击后 `scrollToItem(0)` **实际滚动到顶**。
   - 上述均已在 ViewModel 状态机层以单测守护可测部分，但 UI 层交互未在设备上验证。
2. **回收站排序「按标签」口径**：`ORDER BY (m.tags || char(31)) ASC` 为**序列化串字典序**（首个标签优先，多标签按整串比较），架构 R6 已文档化为可接受语义。
3. **`TrashViewModel` 上提后启动即订阅**：`init` 即在 App 启动订阅 `getTrashedMemosFiltered`（轻量 Flow），架构 R7 记为可接受（未做「首次进入才订阅」优化）。
4. **APK 基线口径**：项目无 git 仓库，release 基线取自 `QA_REPORT_V2.md`（1,726,594 B），非本轮重新构建；与 PRD「≈1.65 MB」一致。
5. **合并后 manifest 的 1 条自签名权限**：`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 由 androidx.core 自动注入，**非本次新增、非敏感**（与 `QA_REPORT_V2.md` 记录一致）。

---

## 8. 复现命令

```bash
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11

# 全量回归 + 构建（零告警校验）
./gradlew test --rerun-tasks assembleDebug assembleRelease --console=plain 2>&1 | tee t05.log
grep -c 'warning:' t05.log          # → 0

# 分模块测试
./gradlew :core:test                # → 45 passed / 0 failed
./gradlew :app:testDebugUnitTest    # → 190 passed / 0 failed

# 体积
stat -c '%s' app/build/outputs/apk/debug/app-debug.apk     # 17,784,781
stat -c '%s' app/build/outputs/apk/release/app-release.apk # 1,742,978
```

---

**最终状态：`./gradlew test` = 235 passed / 0 failed（基线 175，+60） ｜ 零编译告警 ｜ debug 16.96 MiB / release 1.6622 MiB（+16 KiB ≤ 100 KB） ｜ Manifest 零新增权限 ｜ compileSdk/targetSdk = 35 ｜ IS_PASS: YES**

---

## 9. 第 2 轮修复（QA 回归反馈）

> 触发：QA 严过关独立回归（`app/src/test/java/com/ebbinghaus/memo/qa/QaP2IndependentVerificationTest.kt`）证伪了第 1 轮「P2-10 已达成」的自述。
> 结论：**第 1 轮 §5 的「全项目无遗留 `split("\\s+")`」结论有误** —— 列表页仓储实际仍就地切分，属真缺陷（非风格问题）。本轮如实修复，不辩解。

### 9.1 D-01（🔴 高）列表页仓储未统一切分口径 —— 已修复

| 项 | 内容 |
|---|---|
| 缺陷 | `MemoRepositoryImpl.searchMemos`（列表页）用 `query.trim().split("\\s+".toRegex())` 就地切分；`\s` **不匹配全角空格 U+3000**。中文 IME 输入 `算法　信息论` 时：UI 高亮切 2 词，仓储只切 1 词（整串）→ **高亮 ≠ 筛选**，P2-10 验收①未达成。 |
| 修复 | 改为与回收站路径同一真源：`val keywords = SearchQueryTokenizer.activeKeywords(query)`（内置 `take(MAX_KEYWORDS)`，`getOrElse(n){""}` 补空逻辑不变）。 |
| 证据 | `app/src/main/java/com/ebbinghaus/memo/data/repository/MemoRepositoryImpl.kt:48`（调用）、`:47`（注释说明口径共用）；原 `split("\\s+")` 已删除。 |
| 收敛核对 | `grep 'split(' MemoRepositoryImpl.kt` → 仅剩 `Converters.kt:35` 的**标签串**逗号切分（与搜索关键词无关，按最小变更原则不动）。全项目生产代码已无 `split("\\s+")` / `split(" ", "　")`（残留仅存在于 QA 测试文件注释中，未触碰）。 |

### 9.2 D-03（🟡 中）空转契约断言 —— 已修复

| 项 | 内容 |
|---|---|
| 缺陷 | 原 `SearchQueryTokenizerTest.highlightAndRepositoryShareSameKeywordSet_contract` 把 `activeKeywords(raw)` 与**自身**比较（恒真），无守护力，导致 D-01 逃逸。 |
| 修复 | 重写为**实质断言** `activeKeywords_pinsWhitespaceContract_forMixedInput`：对 `"全角\u3000换行\nTab\twords beyond six limit"` 显式断言切出 `["全角","换行","Tab","words","beyond","six"]`；并逐项守护 `tokenize("a\u3000b")==["a","b"]`、`tokenize("a\tb\nc")==["a","b","c"]`。口径被改动（误删 U+3000/\t/\n）即失败。 |
| 证据 | `core/src/test/kotlin/com/ebbinghaus/memo/core/SearchQueryTokenizerTest.kt:95-109`。 |

### 9.3 D-02（🟡 中）P2-6 计数失准 —— 已消除（D-01 连带）

| 项 | 内容 |
|---|---|
| 缺陷 | 6 词上限提示 `ignoredCount` 按 tokenizer 计数，而列表页仓储按 `split("\\s+")` 计数，全角空格场景下**口径分歧**（提示「忽略 1」实际「忽略 6」）。 |
| 修复 | D-01 修好后仓储与提示同源，分歧自动消除。 |
| 新增测试 | `ignoredCount_agreesWithActiveKeywords_forSevenHalfWidthWords`、`..._forSevenFullWidthWords`：断言 7 词输入 → `ignoredCount == 1` 且 `activeKeywords.size == 6`，且 `ignoredCount == tokenize().size - activeKeywords().size`。 |
| 证据 | `core/src/test/kotlin/com/ebbinghaus/memo/core/SearchQueryTokenizerTest.kt:111-135`。 |

### 9.4 本轮实测数字（`./gradlew test --rerun-tasks`，真实）

| 源集 | 数量 | 通过 | 失败 |
|---|---|---|---|
| `core:test` | **47** | **47** | 0 |
| `app:testDebugUnitTest` | **216** | **214** | **2** |
| `app:testReleaseUnitTest` | **216** | **214** | **2** |
| **合计（去重按源集计）** | **263** | **261** | **2** |

- **2 条失败 = QA 的 P2-10 / P2-6「缺陷钉扎」用例，属预期**（见 9.5）：
  - `QaP2_10_TokenizerContractTest > listRepository_divergesFromHighlight_forFullWidthSpace_DEFECT_PIN` → `expected:<[算法　信息论]> but was:<[算法, 信息论]>`（仓储已正确切 2 词）。
  - `QaP2_10_TokenizerContractTest > sixWordHint_divergesFromRepositoryEffectiveWordCount_DEFECT_PIN` → `expected:<1> but was:<6>`（仓储已正确生效 6 词）。
- **「我负责的用例」全绿**：`core` 47/47（含 `SearchQueryTokenizerTest` 由 14→**16** 例，+2）；`app` 中非 QA 钉扎的 **214** 条全通过（含 QA 其余 24 条对抗用例）。
- **零编译告警**：`grep -c 'warning:'` → **0**。
- 净增来源（相对第 1 轮 235）：core `+2`（本轮新增 D-02 断言），app `+26`（QA 新增 6 个测试类共 26 例）→ 263。
- 未新增依赖、未改 Manifest、未改 `compileSdk`/`targetSdk`（仍 35）、无 Room schema 变更。

### 9.5 给 QA 回归轮的必要交接（钉扎用例需翻转）

⚠️ 本轮**未触碰** `app/src/test/java/com/ebbinghaus/memo/qa/` 下任何文件（归 QA 维护）。D-01 修复后，QA 的 2 条钉扎用例**当前会失败（预期）**，请在回归轮将其由「断言缺陷存在」改为「断言修复生效」：

| 用例 | 现断言（钉扎） | 回归轮应改为 |
|---|---|---|
| `listRepository_divergesFromHighlight_forFullWidthSpace_DEFECT_PIN` | `assertEquals(listOf("算法　信息论"), repoKeywords)` + `assertNotEquals(highlight, repo)` | `assertEquals(SearchQueryTokenizer.activeKeywords(raw), repoKeywords)`（两集合**相等**） |
| `sixWordHint_divergesFromRepositoryEffectiveWordCount_DEFECT_PIN` | `assertEquals(1, repoKeywords.size)` + `assertTrue(repoKeywords.first().contains("　"))` | `assertEquals(6, repoKeywords.size)`；`assertEquals(SearchQueryTokenizer.activeKeywords(raw), repoKeywords)` |

**本轮最终状态**：源码 D-01/D-02/D-03 全部修复 ｜ `./gradlew test` = **261 passed / 2 failed**（2 条为 QA 钉扎用例、预期失败）｜ 零编译告警 ｜ 零新增依赖 / 权限 / schema 变更 ｜ compileSdk/targetSdk = 35 ｜ IS_PASS: YES（待 QA 回归轮翻转钉扎用例后达成全绿）。
