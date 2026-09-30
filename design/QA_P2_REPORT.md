# QA 独立回归验证报告 · P2-1 ~ P2-10 增量

> 验证人：QA 工程师 **严过关（Edward）** ｜ 日期：2026-09-16
> 对象：`D:\Projects\androidapk` 的 P2-1 ~ P2-10 共 10 项交互优化实现
> 依据：`design/PRD_INCREMENT_V3.md`（验收标准）、`design/ARCHITECTURE_INCREMENT_V3.md`（架构决策）
> 立场：**不采信工程师 `IMPLEMENTATION_SUMMARY_V3.md` 的自述**；全部数字与结论以本次独立实测为准。

---

## 1. 执行摘要

| 项 | 结论 |
|---|---|
| **总体结论** | **FAIL（有条件 PASS 不成立）** —— 10 项中 **9 项真实生效**，**P2-10（切分口径统一）未达成** |
| **实测测试数字** | **261 passed / 0 failed / 0 skipped**（app 216 = 既有 144 + 工程师新增 46 + 本轮 QA 新增 26；core 45） |
| **构建** | `./gradlew test --rerun-tasks` **BUILD SUCCESSFUL**；`assembleRelease` **BUILD SUCCESSFUL** |
| **release 体积** | 实测 **1,742,978 B**；基线 1,726,594 B → **+16,384 B（+16.0 KiB）≤ 100 KB ✅** |
| **编译告警** | **0**（`grep -c 'warning:'` = 0） |
| **既有 175 条用例** | **未被削弱**（`@Ignore/@Disabled/assumeTrue/assertTrue(true)` 命中 = 0；既有文件 `@Test` 数守恒 144/31） |
| **智能路由判定** | **`Engineer`**（源码 Bug：`MemoRepositoryImpl.searchMemos` 未改用 `SearchQueryTokenizer`） |
| **缺陷数** | **2**（1 高 / 1 中），均为 **P2-10 根因同源** |
| **未验证项** | 5s 撤销窗口、就地展开动画、长按手势、`scrollToItem(0)`、Snackbar 超时竞态（**无设备，如实声明未执行**） |

**与工程师自述的差异（关键）**：
- 自述「**10/10 全部完成**、`IS_PASS: YES`」→ **不成立**：P2-10 核心验收（高亮词集合 == 仓储查询词集合）**实测不相等**。
- 自述「`searchMemos` 改用 `SearchQueryTokenizer`」（§2.2 M3、§3 P2-10、§5「无重复实现」）→ **与源码不符**：`MemoRepositoryImpl.kt:47` 仍为 `query.trim().split("\\s+".toRegex())`。
- 其余自述（235 测试、0 告警、release +16 KiB、零依赖/零权限/无 schema 变更、4 处替身同步、R4 回收站含 tags 且未触碰列表页）→ **实测均属实**。

---

## 2. A. 构建与测试（逐条实测）

### A1. 全量测试
```bash
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11
./gradlew test --console=plain --rerun-tasks
```
原始输出（尾）：
```
> Task :app:testReleaseUnitTest
> Task :app:test
BUILD SUCCESSFUL in 31s
55 actionable tasks: 55 executed
```
从 `build/test-results/**/*.xml` 逐套件解析（`tests/failures/errors/skipped`）：

| 源集 | classes | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `app:testDebugUnitTest` | 36 | **216** | 0 | 0 | 0 |
| `app:testReleaseUnitTest` | 36 | **216** | 0 | 0 | 0 |
| `core:test` | 5 | **45** | 0 | 0 | 0 |
| **合计（debug 口径）** | **41** | **261** | **0** | **0** | **0** |

> 说明：工程师自述 235（app 190 + core 45）**实测复现无误**；本报告总数 261 是在其基础上叠加**本轮 QA 新增 26 条**对抗性用例后的结果。若只看「工程师交付态」，则为 **235 passed / 0 failed**，与其自述一致。

### A2. core 独立测试
```bash
./gradlew :core:test --console=plain --rerun-tasks
```
→ `core:test`：**45 tests / 0 failures / 0 skipped**（5 个测试类：`SearchQueryTokenizerTest`(14)、`EbbinghausSchedulerTest`(10)、`RolloverEngineTest`(8)、`AdversarialM1StressTest`(7)、`MathTextPreprocessorTest`(6)）。**独立通过 ✅**

### A3. release 体积（实测）
```bash
./gradlew assembleRelease --console=plain      # BUILD SUCCESSFUL in 5s
stat -c '%s' app/build/outputs/apk/release/app-release.apk
1742978
```
| 构建类型 | 实测字节 | 基线（QA_REPORT_V2） | 增幅 | 红线 |
|---|---|---|---|---|
| **release** | **1,742,978** | 1,726,594 | **+16,384 B ≈ +16.0 KiB** | ≤ 100 KB ✅ |

> 与工程师自述 `1,742,978 B` **逐字节一致**。

### A4. 编译告警
```bash
grep -c 'warning:' /tmp/qa_test_final.log     # → 0
```
**实测 0 告警 ✅**

---

## 3. B. 10 项 P2 逐条独立复核表

> 证据均为**本次实读源码行号**或**实跑用例名**；「真实生效」= 代码路径满足 PRD 验收标准。

| 项 | 真实生效 | 独立证据（文件:行号 / 用例名） | 备注 |
|---|---|---|---|
| **P2-1 撤销** | ✅ | Effect 带 `actionLabel/actionKey`：`MemoListViewModel.kt:149-153`；`OnSnackbarAction`：`:139`；单值令牌：`:190-196`；单条发撤销型 Snackbar：`:387-400`；批量：`:512-521`；令牌比对（旧回调失效）：`:568`；5s 窗口 = `Indefinite`+`withTimeoutOrNull(5000)`：`MemoListScreen.kt:104,324-331`；超时换文案去按钮：`:336-340`。用例：`MemoListUndoTest`(8) + 本轮 `QaUndoTokenAdversarialTest`(6) | **连删覆盖**语义实测成立（`secondDelete_overwritesToken_oldKeyBecomesInvalid` 通过）。**`OnDeleteMemo` 语义未被改动** ✅：`MemoListViewModel.kt:348-357` 仍为「立即软删、不发撤销型 Snackbar、不弹确认」，用例 `immediateDelete_neverEmitsUndoSnackbar` 通过 |
| **P2-2 就地预览** | ✅ | 卡片 `combinedClickable`→`onToggleExpand`：`TrashScreen.kt:543-546`；`animateContentSize`：`:554`；**就地展开非跳页非弹窗**；展开态完整内容 `maxLines=Int.MAX_VALUE`：`:572`；笔记：`:583`；标签：`:595`；复习进度 `TrashProgressSection`（档位/下次到期/已复习次数）：`:601-604,656-684`；折叠态 3 行：`:572`。用例：`toggleExpand_supportsMultipleExpandedItems`、`expand_exposesReviewProgressProjection` | 四项进度字段与投影 `TrashedMemoWithProgress`（`stageLevel/dueDate/reviewCount`）逐项对应；`还原/彻底删除` 按钮仅在非多选态显示（`:623-646`），与展开手势不冲突 |
| **P2-3 批量** | ✅ | 长按进多选：`TrashScreen.kt:545` → `TrashViewModel.kt:189-203`；**批量还原免二次确认**：`TrashViewModel.kt:236-250`；**批量彻底删除二次确认且含条数**：`TrashScreen.kt:355-379`（文案「将彻底删除 N 条知识点及其复习任务，此操作不可恢复。」）；两方法**均单事务**：`MemoRepositoryImpl.kt:165-175`（`withTransaction`）；FK CASCADE：`ReviewTaskEntity.kt:24-31` + `AppDatabase.kt:69`（`setForeignKeyConstraintsEnabled(true)`）。用例：`batchRestore_hasNoConfirmGate_andExitsSelection`、`batchDelete_requiresConfirmBeforePhysicalDelete`、`batchDeleteFailure_keepsSelectionAndItems` | `restoreByIds`/`hardDeleteByIds` 均带 `AND deletedAt IS NOT NULL`（`KnowledgeMemoDao.kt:130,136`），语义正确 |
| **P2-4 搜索排序** | ✅ | 回收站 SQL `deletedAt IS NOT NULL`（与复习队列**方向相反**）：`KnowledgeMemoDao.kt:150`；搜索范围含 **tags**：`:151-162`；3 种排序 `CASE WHEN :sortKey`：`:163-166`；**独立查询域**（进出不改列表页态）：`TrashViewModel.kt:115-116,128-151`（自有 `_searchQuery/_sortOption`）。用例：`search_matchesContent/Notes/Tags`、`sort_deletedAsc/tagAsc/deletedDesc` | 独立域实测：`TrashViewModelTest` 与 `MemoListViewModel` 各自持有搜索/排序状态，无共享 |
| **P2-5 标签 Chip** | ✅ | `SuggestionChip.onClick = { onTagClick(tag) }`：`MemoListScreen.kt:899`；→ `OnTagSelected`：`:530-531,569-570`；`scrollToItem(0)`：`:501,542`；「标签：T」可清除指示：`:444-464`（`trailingIcon`=Close，点击 → `OnTagSelected(activeTag)` 清除）。用例：`MemoListViewModelTest.testTagSelection_filtersAndToggles`（再点取消） | `onClick` **不再是空 lambda** ✅；回顶键控 `selectedTag`（`:500,541`），避免打字时滚动 |
| **P2-6 六词提示** | ⚠️ **部分** | 提示：`MemoListScreen.kt:406-415`（`labelSmall` 辅助文字，**非模态**，不弹 Snackbar）；`ignoredCount`：`:376`；≤6 不提示、>6 提示：`SearchQueryTokenizerTest.ignoredCount_*` 通过 | **上限保持 6 词**（未改动态 SQL）✅。**但「已忽略 N 个」的 N 与仓储实际生效词数在全角空格输入下不一致**（见缺陷 D-02，P2-10 同源） |
| **P2-7 文案** | ✅ | `AppNavigation.kt:277`、`TrashScreen.kt:193`：`"全选当前结果"`（已全选时 `"取消全选"`）；行为不变仍选 `current.memos`/`current.items`：`MemoListViewModel.kt:467-471`、`TrashViewModel.kt:216-220` | 文案已改，行为未变 ✅ |
| **P2-8 编辑器标签** | ✅ | `allTags: List<String> = emptyList()` **带默认值**：`MemoEditDialog.kt:73`；候选 Chip 点击 **追加去重**：`:237-238` → `appendTagToInput`：`MemoEditorDraftState.kt:42-51`；解析仍 `split(",", "，", "、", " ")`：`:96-99`。用例：`MemoEditorTagInputTest`(8) + `QaAppendTagBoundaryTest`(3) | 默认值保护 `PagePreviews`（`PagePreviews.kt:73,140`）✅；追加非替换 ✅ |
| **P2-9 暂停态** | ✅ | `dailyLimit<=0` → `ReviewPausedView`：`ReviewScreen.kt:166-171`；文案「今日已按设置暂停复习」：`:557-558`；「去设置」入口：`:576-585`；**`dailyLimit>0 且队列空` 仍走原庆祝页**：`:172-178`；`dailyLimit` 落值：`ReviewViewModel.kt:36,114`。用例：`ReviewDailyLimitTest`(4) + `QaDailyLimitBranchTest`(3，含 **负数** 分支) | 分支互斥，**未误伤正常达成** ✅ |
| **P2-10 切分口径** | ❌ **未生效** | UI 高亮用 `SearchQueryTokenizer.activeKeywords`：`MemoListScreen.kt:372`；**仓储列表页未用**：`MemoRepositoryImpl.kt:47` 仍为 `query.trim().split("\\s+".toRegex())`；回收站侧**已用**：`MemoRepositoryImpl.kt:155`。反证用例：`QaP2_10_TokenizerContractTest.listRepository_divergesFromHighlight_forFullWidthSpace_DEFECT_PIN`（**实测通过 = 证明两集合不相等**） | **全项目已无 `split(" ", "　")`，但 `就地 split("\\s+")` 仍存在于 `MemoRepositoryImpl.kt:47`**，违反架构 §8-1。高亮取前 6 词 ✅（`activeKeywords`），但集合与仓储**不恒等** |

**小计**：真实生效 **9 / 10**；P2-10 **未生效**；P2-6 因同源缺陷**部分生效**。

---

## 4. C. 关键风险点复核（架构师标红项）

| 风险 | 结论 | 独立证据 |
|---|---|---|
| **R1 测试替身同步** | ✅ **已同步且非空转** | 4 处替身均补齐：① `DataLayerContractAdversarialTest.kt:43-48`（`StubKnowledgeMemoDao`）；② `QaAdversarialDataTest.kt:50-55`（`StubMemoDao`）；③ `FakeRepositories.kt:197-256`（`FakeMemoRepository` 3 方法**内存版真实逻辑**）；④ `PreviewFakes.kt:128,159,173`（`PreviewMemoRepository`）。**空转判定**：2 个 DAO stub 返回 `0/emptyList` 仅作**基类兜底**（被子类覆写或仅服务于非目标维度测试），**不会**使目标断言失效；2 个 Repository 替身均为**真实内存实现**（`restoreMemos` 逐条置 `deletedAt=null` 并返回计数、`hardDeleteMemos` 物理移除、`getTrashedMemosFiltered` 真实过滤+排序），**被测断言有实际观测对象**。 |
| **R5 Room 列映射** | ✅ **安全** | 显式别名：`KnowledgeMemoDao.kt:147`（`t.stageLevel AS stageLevel, t.dueDate AS dueDate, t.reviewCount AS reviewCount`）；可空性与 `Converters` 一致：`TrashedMemoWithProgress`（`Int?/LocalDate?/Int?`）↔ `Converters.fromLocalDate(LocalDate?):Long?` / `toLocalDate(Long?):LocalDate?`（`Converters.kt:39-46`）；`ReviewTaskEntity.dueDate: LocalDate`（非空）在 LEFT JOIN 下可为 NULL，投影声明为可空 —— **Room 允许非空列读入可空字段**，KSP 编译期已完成列名校验并**通过**。**结论：首次查询崩溃风险已被别名 + 可空转换消除**。 |
| **R2 Snackbar 超时竞态** | ⚠️ **已防御，残余风险不可真机验证** | 实现：`MemoListScreen.kt:324-340`——`withTimeoutOrNull(5000)` 包裹 `showSnackbar(Indefinite)`；超时后 `currentSnackbarData?.dismiss()`（`?.` 空安全）+ 再发兜底文案「已移入回收站，可在设置中还原」。**评估**：`withTimeoutOrNull` 取消挂起的 `showSnackbar` 时 Compose 会移除该 Snackbar，故随后 `currentSnackbarData` 可能为 null（`?.` 已防御），兜底 `showSnackbar` 为块内末句、正常显示；**「兜底文案偶发不显示」的真实诱因是 `collectLatest` 在 5s 内收到新 effect 时取消本块**——此属设计预期（新删覆盖旧删），非缺陷。**代码已有防御（空安全 + 末句发送）**；真机表现**无法验证**。 |
| **R3 `collectLatest`** | ✅ **已文档化** | `MemoListScreen.kt:311-313` 注释**显式标注为有意行为**：「采用 collectLatest…实现『新删覆盖旧删』…对本 App 的瞬时消息语义合理，旧撤销回调随之失效」。**影响**：非撤销型 Snackbar 亦会被后续 effect 取消（后者覆盖前者）。**吞掉重要提示的可能性**：存在但低——本 App 的 memo-list 一次性消息均为「瞬时结果提示」（保存失败/删除失败/已还原等），语义上不要求排队；且**看板提示走独立收集器**（`:351-357`，`collect` 非 `collectLatest`），二者互不覆盖内部队列。**结论：可接受，已文档化**。 |
| **R4 搜索范围差异** | ✅ **按裁定执行** | 回收站搜索**含 tags**：`KnowledgeMemoDao.kt:151-162`（`(m.tags || char(31)) LIKE …`）；列表页 `searchMemos` **确实未被改动**：`KnowledgeMemoDao.kt:92-97` 仅 `content + notes`（无 tags）。**裁定结论成立**：差异已文档化（`MemoRepositoryImpl.kt:154` 注释、架构 R4），未触碰列表页 SQL。 |

---

## 5. D. 回归检查（防「修好新问题」）

### D1. 既有 175 条是否被削弱 —— **核查方法 + 结论**

**方法（三重）**：
1. **反模式静态扫描**（全 `app/src/test` + `core/src/test`）：
   `grep -nE 'assertTrue\(true\)|@Ignore|@Disabled|assumeTrue|TODO\(|FIXME'` → **命中 0**（两类目录均无）。
2. **用例数守恒核对**（逐文件 `grep -c @Test`）：

   | 口径 | 既有基线 | 当前 | 新增来源 | 既有部分 |
   |---|---|---|---|---|
   | app | 144 | 216 | `MemoListUndoTest`(8)+`TrashViewModelTest`(26)+`MemoEditorTagInputTest`(8)+`ReviewDailyLimitTest`(4)+**本轮 QA** `QaP2IndependentVerificationTest`(26) = 72 | **216−72 = 144 ✅ 守恒** |
   | core | 31 | 45 | `SearchQueryTokenizerTest`(14) | **45−14 = 31 ✅ 守恒** |

   → **既有文件未删任何 `@Test` 方法**。
3. **实读断言抽查**（重点文件）：`MemoListViewModelTest`（8 例，含 `testDeleteMemo_removesEntity` 依赖 `OnDeleteMemo` 立即删除）、`MemoListDeleteConfirmTest`（6 例）、`QaFixIndependentVerificationTest`（25 例，含 P1-2/P1-4/P1-1/P1-5 对抗）、`MemoListSelectionTest`、`MemoListSortTest`、`MemoListBatchFailureTest`、`RatingSemanticsConsistencyTest`、`MemoEditorDraftStateTest`、`QaAdversarialDataTest`、`QaAdversarialUiTest` —— **断言均为实质性期望值比对，无 `assertTrue(true)` 式空转**。

**结论：既有 175 条断言未被削弱 ✅**（0 命中 + 计数守恒 + 抽查实质）。

### D2. 依赖 / 权限 / schema / SDK
| 检查项 | 实测 | 证据 |
|---|---|---|
| 零新增依赖 | ✅ | `app/build.gradle.kts` 依赖清单均为既有栈（Compose BOM 2024.10.01 / Room 2.6.1 / navigation 2.8.4 等，无新增）；`core/build.gradle.kts` 仅 `junit:junit:4.13.2` |
| Manifest 零新增权限 | ✅ | 源码 `AndroidManifest.xml` **无任何 `<uses-permission>`**；合并后仅 1 条 androidx.core 自动注入的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（非敏感、非本次新增） |
| compileSdk / targetSdk = 35 | ✅ | `app/build.gradle.kts:11,16`（`compileSdk = 35`、`targetSdk = 35`；`minSdk = 26`） |
| 无 Room schema 变更 | ✅ | `app/schemas/…AppDatabase/` 下**仅 `1.json`、`2.json`（无 `3.json`）**；`AppDatabase.kt:31` `version = 2` 未变 |

### D3. `OnDeleteMemo` 语义
✅ **仍为「立即删除、不弹确认」**：`MemoListViewModel.kt:348-357`（直接 `softDeleteMemo`，**不**打开确认弹窗、**不**发撤销型 Snackbar）；`MemoListViewModel.kt:74-75` 注释保留；依赖它的 `MemoListViewModelTest.testDeleteMemo_removesEntity` 与 `QaFixIndependentVerificationTest.immediateDeleteFailure_*` **实测通过**。

---

## 6. E. 对抗性测试（本轮 QA 新增，已写入项目并实跑）

**新增文件**：`app/src/test/java/com/ebbinghaus/memo/qa/QaP2IndependentVerificationTest.kt`（6 个测试类，共 **26 例**，**全绿**）。

| 维度 | 测试类 | 例数 | 覆盖攻击面 | 结果 |
|---|---|---|---|---|
| 切分边界 | `QaTokenizerBoundaryTest` | 7 | 全角空格/换行/Tab/混合/空串/仅分隔符/恰好6/7/超长词/前缀性 | ✅ 全通过 |
| **P2-10 契约** | `QaP2_10_TokenizerContractTest` | 3 | **记录型 DAO + 生产 `MemoRepositoryImpl`**：回收站侧集合恒等（通过）；**列表页侧集合不恒等（缺陷钉扎，通过=证伪）**；6 词提示口径分歧 | ✅ 全通过（含 2 条缺陷钉扎） |
| 撤销令牌 | `QaUndoTokenAdversarialTest` | 6 | 旧令牌失效/新删覆盖/未知令牌忽略/**令牌重放**/过期路径/批量整批还原/立即删除不发撤销 | ✅ 全通过 |
| 回收站状态机 | `QaTrashStateMachineAdversarialTest` | 4 | 全选/批量还原免确认/批量彻底删除二次确认/失败保留选择集 | ✅ 全通过 |
| 标签追加去重 | `QaAppendTagBoundaryTest` | 3 | 跨分隔符去重/空白标签/空输入/解析往返 | ✅ 全通过 |
| dailyLimit 分支 | `QaDailyLimitBranchTest` | 3 | 0 / **负数** / >0 且队列空 | ✅ 全通过 |

**P2-10 核心断言（证伪实录）**——`listRepository_divergesFromHighlight_forFullWidthSpace_DEFECT_PIN`：
```
输入 raw = "算法　信息论"（全角空格 U+3000）
UI 高亮侧  SearchQueryTokenizer.activeKeywords(raw) → ["算法", "信息论"]           （2 词）
仓储侧     MemoRepositoryImpl 下发给 DAO 的关键词  → ["算法　信息论"]              （1 词，含全角空格）
assertNotEquals(highlightKeywords, repoKeywords) → 通过  ⇒ 两集合【不相等】
```
> 该用例的**通过**本身就是缺陷证据：`\s`（Java/Kotlin 默认）**不匹配 U+3000**，故仓储把整串当单关键词。

**过程修正**：初版 `batchUndo_restoresEntireBatchOnly` 中我把「未删条目」误断言为 `assertNotNull(deletedAt)`（应为 `assertNull`），首次运行 26 中 1 失败 → **属测试代码 Bug，由 QA 自行修复**（非源码问题），修复后 26/26 通过。

---

## 7. 智能路由判定

### **`Engineer`**（源码 Bug）

**依据**：断言期望（PRD §3 P2-10 验收①「UI 高亮切分与仓储筛选切分调用同一函数」、架构 §8-1「禁止就地 `split("\\s+")`」）**正确**，但源码产出**错误**——
- `MemoRepositoryImpl.kt:47` 使用 `query.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }`，**未**改用 `SearchQueryTokenizer.activeKeywords(query)`；
- 后果：含全角空格（U+3000，中文输入法高频）的搜索词，**UI 高亮词集合 ≠ 仓储实际查询词集合**，且 **P2-6 的「已忽略多余 N 个」计数失准**；
- 附带：`core/src/test/.../SearchQueryTokenizerTest.kt:96-103` 的「契约守护」用例 `highlightAndRepositoryShareSameKeywordSet_contract` 把**同一函数与自身比较**（`activeKeywords(raw)` vs `activeKeywords(raw)`），**为空转断言**，无法发现该缺陷 —— 属测试质量问题，须由工程师一并修正（改为断言「生产仓储下发的关键词 == `activeKeywords`」，或直接采用本报告新增的 `QaP2_10_TokenizerContractTest` 模式）。

> 附带判定：本报告的 `QaP2_10_*` 缺陷钉扎用例在**修复后会自动失败**（提示把 `assertNotEquals` 改为 `assertEquals`），工程师可据此闭环。

---

## 8. 缺陷清单

### 缺陷 D-01（**严重度：高**）—— P2-10 未达成：列表页仓储未统一切分口径
| 项 | 内容 |
|---|---|
| **严重度** | **高**（P1 需求「正确性缺陷」未达成；自述失实；影响中文全角空格搜索） |
| **复现** | 在列表页搜索框输入 `算法　信息论`（两词间为**全角空格 U+3000**）→ 期望按 2 词 AND 检索并高亮两词；实际仓储按**单个含全角空格的串**做子串匹配 |
| **期望 vs 实际** | 期望：`activeKeywords(raw)` == 仓储下发关键词集合（PRD §3 P2-10①）；实际：`["算法","信息论"]` ≠ `["算法　信息论"]` |
| **证据** | 源码 `MemoRepositoryImpl.kt:47`（`split("\\s+")`）；`MemoListScreen.kt:372`（高亮用 `activeKeywords`）；反证用例 `QaP2_10_TokenizerContractTest.listRepository_divergesFromHighlight_forFullWidthSpace_DEFECT_PIN`（实测通过 ⇒ 证伪）；对照：回收站侧 `MemoRepositoryImpl.kt:155` **已**正确使用 tokenizer |
| **修复建议** | 将 `searchMemos` 的切分替换为 `SearchQueryTokenizer.activeKeywords(query)` + 6 元补空（与 `getTrashedMemosFiltered` 同款） |

### 缺陷 D-02（**严重度：中**）—— P2-6 提示计数与实际生效词数口径分歧（D-01 同源）
| 项 | 内容 |
|---|---|
| **严重度** | **中**（提示误导，非崩溃） |
| **复现** | 输入 `a　b　c　d　e　f　g`（全角空格分隔 7 词）→ UI 提示「已忽略多余 **1** 个」；实际仓储仅生效 **1** 个词（忽略 6 个） |
| **期望 vs 实际** | 期望：提示数 = 实际被忽略词数；实际：1 ≠ 6 |
| **证据** | `MemoListScreen.kt:376`（`ignoredCount` 走 tokenizer）；`MemoRepositoryImpl.kt:47`（仓储走 `\s+`）；反证用例 `QaP2_10_TokenizerContractTest.sixWordHint_divergesFromRepositoryEffectiveWordCount_DEFECT_PIN` |
| **修复** | 随 D-01 一并修复（口径统一后自动消除） |

### 缺陷 D-03（**严重度：中**，测试质量）—— P2-10「契约守护」用例为空转断言
| 项 | 内容 |
|---|---|
| **严重度** | **中**（导致 P2-10 缺陷逃逸；非功能缺陷） |
| **复现** | 审阅 `core/src/test/kotlin/com/ebbinghaus/memo/core/SearchQueryTokenizerTest.kt:96-103` |
| **期望 vs 实际** | 期望：断言「仓储实际查询词集合」== 「高亮词集合」；实际：`val repository = SearchQueryTokenizer.activeKeywords(raw)` —— 用**被测函数自身**充当「仓储」，恒等成立，**无鉴别力** |
| **证据** | 同文件 `:99-101`；对照本轮新增 `QaP2_10_TokenizerContractTest`（以记录型 DAO 观测**生产仓储真实下发**的关键词，具鉴别力） |
| **修复** | 工程师修正该用例或采纳 QA 版本 |

---

## 9. 遗留与未验证项（如实声明）

**无 Android 模拟器/真机 → 以下 Compose 行为「未执行」，不伪造结论**：
1. **5s 撤销窗口**：`Indefinite` + `withTimeoutOrNull(5000)` 的真实计时、`withDismissAction` 交互、超时后兜底文案切换（含 **R2 竞态**「兜底文案偶发不显示」）——**未在设备验证**。已由 `MemoListUndoTest` + `QaUndoTokenAdversarialTest` 在 ViewModel/令牌层守护可测部分。
2. **回收站就地展开动画**（`animateContentSize`）与**长按手势 vs 单击展开**的手势层级（PRD 边界 12）——**未在设备验证**。
3. **标签 Chip 点击后 `scrollToItem(0)` 实际滚到顶部**（`firstVisibleItemIndex==0`）——**未在设备验证**。
4. **Snackbar 视觉/交互**（非模态 6 词提示不遮挡列表、撤销按钮可点）——**未在设备验证**。

**其他需说明**：
5. **release 基线口径**：项目**无 git 仓库**（`git log` 报 `Exit Code 128`），无法 `git stash` 重建基线；基线 1,726,594 B 取自上一轮 `QA_REPORT_V2.md`（与 PRD「≈1.65 MB」一致）。**该基线非本轮重测**。
6. **「既有断言未删改」的证明边界**：因无 git 历史，无法做逐行 diff；本报告以「反模式扫描 0 命中 + 用例数守恒（144/31）+ 重点文件实读抽查」三法交叉证明，**强度足以支撑结论，但非逐行比对**。
7. **回收站「按标签」排序口径**：`ORDER BY (m.tags || char(31)) ASC` 为**序列化串字典序**（首个标签优先，多标签按整串比较），架构 R6 已文档化为可接受语义——**语义可接受，非缺陷**。
8. **`TrashViewModel` 上提后启动即订阅** `getTrashedMemosFiltered`（架构 R7）——已确认，轻量 Flow，**可接受**。

---

**报告结论**：P2-1 ~ P2-9 **真实生效（9/10）**；**P2-10 未达成**（D-01 高）并连带 P2-6 计数失准（D-02 中）；测试套件 261/0/0 全绿、0 告警、release +16 KiB、零依赖/零权限/无 schema 变更、既有 175 条未被削弱。**智能路由 → `Engineer`**（修复 D-01/D-02/D-03 后，本报告钉扎用例将自动提示切换为等值断言）。

---
---

# 第 2 轮回归（修复验证 + 最终判定）

> 验证人：QA 工程师 **严过关（Edward）** ｜ 日期：2026-09-16 ｜ 轮次：**P2 最终回归轮（第 2 轮，硬上限 2 轮）**
> 触发：工程师按第 1 轮报告修复 D-01/D-02/D-03，第 1 轮遗留的 2 条「缺陷钉扎」用例如期失败（= 修复生效证据）。
> 立场：**逐条实读源码 + 变异测试证伪，不采信工程师自述**。

## 1. 最终判定

| 项 | 结论 |
|---|---|
| **最终判定** | **PASS** —— 10 项 P2 **全部真实生效（10/10）**，第 1 轮唯一失败项 P2-10 已修复 |
| **智能路由判定** | **`NoOne`**（全量 267 用例全绿，无源码 Bug、无测试 Bug 遗留） |
| **缺陷闭环** | D-01（高）/ D-02（中）/ D-03（中）**全部真实修复**，无新增缺陷 |
| **实测测试数字** | **267 passed / 0 failed / 0 errors / 0 skipped**（app 220 + core 47） |
| **构建体积** | release **1,742,978 B**；基线 1,726,594 B → **+16,384 B（+16.0 KiB）≤ 100 KB ✅** |
| **编译告警** | **0** |
| **未验证项** | 5s 撤销窗口 / 就地展开动画 / 长按手势 / `scrollToItem(0)` / Snackbar 超时竞态（**无设备，如实声明未执行**） |

## 2. A. 修复复核（独立实读 + 变异证伪，逐条）

### D-01（🔴高）—— 列表页仓储未统一切分口径 → **已真实修复 ✅**

**实读证据**（`app/src/main/java/com/ebbinghaus/memo/data/repository/MemoRepositoryImpl.kt`）：
```
47:  // 即 SearchQueryTokenizer.activeKeywords（含全角空格 U+3000，取前 6 个），空位补空串
48:  val keywords = SearchQueryTokenizer.activeKeywords(query)      ← 已改用 tokenizer（与回收站路径同源）
49-54: val k1..k6 = keywords.getOrElse(n) { "" }                     ← 6 元补空逻辑完好，未被改坏
155: val keywords = SearchQueryTokenizer.activeKeywords(query)      ← 回收站路径仍正确（未回归）
```
- 第 48 行确认由 `query.trim().split("\\s+".toRegex())` 改为 `SearchQueryTokenizer.activeKeywords(query)`；
- `getOrElse(0..5) { "" }` 补空逻辑逐行核对**未被改坏**（空串 = 该位不启用）；
- **grep 全项目生产代码**（`app/src/main` + `core/src/main`）：
  - `grep -rn 'split("\\s+")'` → **0 命中**（exit=1）
  - `grep -rn 'split(" ", "　")'` → **0 命中**（exit=1）
  - 仅存的 `split(` 调用均为**无关语义**：`Converters.kt:35`（标签序列化分隔）、`MemoEditDialog.kt:96` / `MemoEditorDraftState.kt:46`（标签输入 `split(",", "，", "、", " ")`）、`MathTextPreprocessor.kt:115`（数学文本分段）、`SearchQueryTokenizer.kt:36`（唯一真源本体）。
- **端到端实证**：翻转后的 `listRepository_matchesHighlight_forFullWidthSpace` **实测通过**，即仓储下发的 6 元参数 == `activeKeywords(raw)` 逐项补空（全角空格 U+3000 被正确切分）。

### D-02（🟡中）—— P2-6 提示计数与仓储生效词数口径分歧 → **已随 D-01 消除 ✅**

**实读证据**（`core/src/test/kotlin/com/ebbinghaus/memo/core/SearchQueryTokenizerTest.kt:111-135`）：新增 2 条实质断言——
`ignoredCount_agreesWithActiveKeywords_forSevenHalfWidthWords`（半角 7 词）与 `..._forSevenFullWidthWords`（全角 7 词），均断言 `ignoredCount == 总词数 − 生效词数`。
**端到端实证**：翻转后的 `sixWordHint_agreesWithRepositoryEffectiveWordCount` 实测通过——全角 7 词输入下 `ignoredCount(raw)==1`、`activeKeywords(raw).size==6`、**仓储实际生效 `repoKeywords.size==6`**（第 1 轮为 1），三者口径一致。

### D-03（🟡中，测试质量）—— 「契约守护」用例恒真空转 → **已真实修复且守护有效 ✅**

**实读证据**（`core/src/test/kotlin/com/ebbinghaus/memo/core/SearchQueryTokenizerTest.kt:95-109`）：第 1 轮的空转断言（`activeKeywords(raw)` 与自身比较）已**重写为实质断言**：
```
99:  val raw = "全角\u3000换行\nTab\twords beyond six limit"
100-104: assertEquals(listOf("全角","换行","Tab","words","beyond","six"), activeKeywords(raw))
106: assertEquals(listOf("a","b"), tokenize("a\u3000b"))     ← 显式守护全角空格
107: assertEquals(listOf("a","b","c"), tokenize("a\tb\nc"))  ← 显式守护 Tab/换行
108: assertTrue(activeKeywords(raw).size <= MAX_KEYWORDS)
```
**守护有效性——变异测试实证（关键，非纸面判断）**：
将 `SearchQueryTokenizer.kt:26` 的 `Regex("[\\s\\u3000]+")` 临时变异为 `Regex("[\\s]+")`（即**去掉 U+3000**），运行 `:core:test`：
```
core tests=16 failures=3
  FAILED: activeKeywords_pinsWhitespaceContract_forMixedInput   ← 即 D-03 新断言
  FAILED: tokenize_fullWidthSpace_isSeparator
  FAILED: ignoredCount_agreesWithActiveKeywords_forSevenFullWidthWords
```
→ **去掉 U+3000 会使 D-03 断言失败 ⇒ 守护真实有效**（第 1 轮的空转断言无法做到这一点）。变异已**完整还原**（见 §7 说明）。

### 文件改动范围核对
工程师本轮**仅改动 2 个文件**（mtime 09:29）：`MemoRepositoryImpl.kt`、`SearchQueryTokenizerTest.kt`。
`app/src/test/java/com/ebbinghaus/memo/qa/` 下文件 mtime = **09:25**（**早于**工程师修复的 09:29）→ **工程师未改动 QA 钉扎文件** ✅（其内容在工程师修复后原样保留 `_DEFECT_PIN` + `assertNotEquals`，直至本轮由 QA 翻转）。

## 3. B. 钉扎用例翻转（本轮 QA 改动，仅测试文件）

翻转对象：`app/src/test/java/com/ebbinghaus/memo/qa/QaP2IndependentVerificationTest.kt`（**唯一改动的测试文件**）。

| 第 1 轮用例名（钉缺陷） | 第 2 轮用例名（守正确行为） | 翻转后断言 |
|---|---|---|
| `listRepository_divergesFromHighlight_forFullWidthSpace_DEFECT_PIN` | `listRepository_matchesHighlight_forFullWidthSpace` | ① `activeKeywords("算法　信息论") == ["算法","信息论"]`；② `repoKeywords == highlightKeywords`（**等值**）；③ `dao.lastSearchKeywords == ["算法","信息论","","","",""]` |
| `sixWordHint_divergesFromRepositoryEffectiveWordCount_DEFECT_PIN` | `sixWordHint_agreesWithRepositoryEffectiveWordCount` | ① `ignoredCount(raw)==1`；② `activeKeywords(raw).size==6`；③ **`repoKeywords.size==6`**；④ `repoKeywords == activeKeywords(raw)` |

> `assertNotEquals`（断言缺陷存在）已全部替换为 `assertEquals`（断言正确行为），后缀 `_DEFECT_PIN` 已去除。

## 4. C. 最终全量回归（实测，贴原始输出）

### C1. `./gradlew test --console=plain --rerun-tasks`
```
> Task :app:testReleaseUnitTest
> Task :app:test
BUILD SUCCESSFUL in 34s
55 actionable tasks: 55 executed
```
从 `build/test-results/**/*.xml` 逐套件解析：

| 源集 | classes | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `app:testDebugUnitTest` | 37 | **220** | **0** | **0** | **0** |
| `app:testReleaseUnitTest` | 37 | 220 | 0 | 0 | 0 |
| `core:test` | 5 | **47** | **0** | **0** | **0** |
| **合计（debug 口径）** | **42** | **267** | **0** | **0** | **0** |

> 数字说明：任务书预期 263（core 47 + app 216），本轮 QA 按 §6 **额外新增 4 条对抗性补测**，故 app 由 216 → **220**，总数 **267**，**全部通过**。工程师自述「core 47、app 216 中 2 条失败（钉扎）」→ **实测吻合**（钉扎翻转后 2 条转绿）。

### C2. `./gradlew :core:test --console=plain --rerun-tasks`
```
BUILD SUCCESSFUL in 1m 5s
core tests=47 failures=0 errors=0 skipped=0
```
5 个测试类：`SearchQueryTokenizerTest`(16)、`EbbinghausSchedulerTest`(10)、`RolloverEngineTest`(8)、`AdversarialM1StressTest`(7)、`MathTextPreprocessorTest`(6)。**core 独立通过 ✅**

### C3. `./gradlew assembleRelease --console=plain`
```
BUILD SUCCESSFUL in 1m 17s
stat -c '%s' app/build/outputs/apk/release/app-release.apk → 1742978
```
| 构建类型 | 实测字节 | 基线 | 增幅 | 红线 |
|---|---|---|---|---|
| **release** | **1,742,978** | 1,726,594 | **+16,384 B ≈ +16.0 KiB** | ≤ 100 KB ✅ |

> 与第 1 轮、工程师自述均**逐字节一致**（修复仅动一行生产代码 + 测试，无新增资源/依赖）。

### C4. 编译告警
`grep -c 'warning:' /tmp/qa_r2_final.log` → **0** ✅

## 5. D. 闭环复核

### D1. 10 项 P2 最终状态表 —— **10 / 10 全部真实生效**

| 项 | 第 1 轮 | **第 2 轮最终** | 依据 |
|---|---|---|---|
| P2-1 撤销 | ✅ | **✅** | `MemoListViewModel.kt:149-153,139,190-196,387-400`；`MemoListScreen.kt:104,324-331`；用例 `MemoListUndoTest`+`QaUndoTokenAdversarialTest`(6) 全绿 |
| P2-2 就地预览 | ✅ | **✅** | `TrashScreen.kt:543-546,554,572-604`；用例通过 |
| P2-3 批量 | ✅ | **✅** | `TrashViewModel.kt:236-250`；`MemoRepositoryImpl.kt:165-175`（单事务）；FK CASCADE |
| P2-4 搜索排序 | ✅ | **✅** | `KnowledgeMemoDao.kt:150-166`；`TrashViewModel.kt:115-151` 独立域 |
| P2-5 标签 Chip | ✅ | **✅** | `MemoListScreen.kt:899,530-531,501` |
| P2-6 六词提示 | ⚠️ 部分 | **✅** | 提示 `MemoListScreen.kt:376,406-415`；**计数口径已与仓储统一**（D-02 闭环） |
| P2-7 文案 | ✅ | **✅** | `AppNavigation.kt:277`、`TrashScreen.kt:193` |
| P2-8 编辑器标签 | ✅ | **✅** | `MemoEditDialog.kt:73,237-238`；`MemoEditorDraftState.kt:42-51` |
| P2-9 暂停态 | ✅ | **✅** | `ReviewScreen.kt:166-178`；用例含负数分支 |
| **P2-10 切分口径** | ❌ | **✅ 已修复** | **列表页 `MemoRepositoryImpl.kt:48` 已改用 `SearchQueryTokenizer.activeKeywords`**；UI 高亮 `MemoListScreen.kt:372` 同源；回收站 `:156` 同源；翻转用例 `listRepository_matchesHighlight_forFullWidthSpace` 实测通过 |

**小计：10 / 10 全部真实生效**（P2-10 由 ❌→✅；P2-6 由 ⚠️→✅）。

### D2. 既有 175 条基线未削弱
- **反模式静态扫描**：`grep -rnE 'assertTrue\(true\)|@Ignore|@Disabled|assumeTrue|TODO\(|FIXME'`（`app/src/test` + `core/src/test`）→ **命中 0** ✅
- **用例数守恒**：app `220 − 46(工程师P2) − 26(轮1 QA) − 4(轮2 QA) = 144` ✅；core `47 − 16(tokenizer) = 31` ✅ → 基线 **144 + 31 = 175 守恒** ✅

### D3. 依赖 / 权限 / schema / SDK
| 检查项 | 实测 | 证据 |
|---|---|---|
| 零新增依赖 | ✅ | `app/build.gradle.kts:71-124`、`core/build.gradle.kts:13-16` 均为既有栈，无新增 |
| Manifest 零权限 | ✅ | `grep -n 'uses-permission' app/src/main/AndroidManifest.xml` → **0 命中**（exit=1） |
| compileSdk / targetSdk = 35 | ✅ | `app/build.gradle.kts:11`（`compileSdk=35`）、`:16`（`targetSdk=35`）、`:15`（`minSdk=26`） |
| 无 Room schema 变更 | ✅ | `app/schemas/…AppDatabase/` 下**仅 `1.json`、`2.json`，无 `3.json`**；`AppDatabase.kt:31` `version = 2` 未变 |

### D4. `MemoListUiEvent.OnDeleteMemo` 语义
✅ **仍为「立即删除、不弹确认」未被改动**：`MemoListViewModel.kt:348-357`——直接 `softDeleteMemo`，**不打开确认弹窗**（`OnRequestDeleteMemo` 才置 `isDeleteDialogVisible`，见 `:359-366`）、**不发撤销型 Snackbar**（注释 `:350-351` 保留）。依赖用例 `immediateDelete_neverEmitsUndoSnackbar`（`QaUndoTokenAdversarialTest`）实测通过。

## 6. E. 对抗性补测（本轮新增 4 条，已写入并实跑）

新增测试类 `QaP2_10_RepositoryTokenizerParityAdversarialTest`（同文件内，4 例，**全绿**）：

| 用例 | 输入 | 断言 | 结果 |
|---|---|---|---|
| `mixedWhitespace_fullWidthTabNewline_repositoryEqualsTokenizer` | `"a　b\tc\nd"` | 列表页与回收站 6 元结果均 == `["a","b","c","d","",""]` | ✅ |
| `consecutiveFullWidthSpaces_produceNoEmptyKeywords` | `"a　　　b"` | == `["a","b","","","",""]`（连续全角空格不产生空词） | ✅ |
| `fullWidthSpaceOnlyInput_yieldsSixEmptySlots` | `"　　"` | `tokenize` 返回空列表；仓储下发 6 元全空串 | ✅ |
| `listAndTrashPaths_shareIdenticalTokenization_forArbitraryInput` | 6 组样本 | 列表页与回收站两条路径 6 元结果**逐项恒等** | ✅ |

> 以上补测均以「记录型 DAO + 生产 `MemoRepositoryImpl`」实证下发关键词，覆盖第 1 轮未穷尽的空白组合。

## 7. 遗留与未验证项（如实声明）

1. **无模拟器/真机 → 以下 Compose 行为「未执行」**：5s 撤销窗口真实计时与 `withDismissAction` 交互、就地展开动画（`animateContentSize`）与长按 vs 单击手势层级、标签 Chip 点击后 `scrollToItem(0)` 实际回顶、Snackbar 视觉/交互与超时竞态（R2）。已由 ViewModel/令牌层用例守护可测部分。
2. **无 git 仓库**（`git log` → `Exit Code 128`）→ 无法做逐行 diff；「既有 175 条未削弱」以「反模式扫描 0 命中 + 用例数守恒 + 重点文件实读」三法交叉证明（非逐行比对）。
3. **release 基线口径**：基线 1,726,594 B 取自上一轮 `QA_REPORT_V2.md`（非本轮重测）；本轮 release 实测值与第 1 轮逐字节一致。
4. **【透明披露】变异测试对源码的临时改动**：为验证 D-03 守护有效性，本轮**临时**将 `core/src/main/.../SearchQueryTokenizer.kt:26` 的 `U+3000` 移除并运行测试，随后**从备份完整还原**（复读文件确认 `Regex("[\\s\\u3000]+")` 与初始内容逐字节一致，`u3000` 计数=1）。**净改动为零**，`app/src/main` 业务源码全程未改。
5. **回收站「按标签」排序口径**（`ORDER BY (m.tags || char(31)) ASC` 为序列化串字典序）与 **`TrashViewModel` 启动即订阅**（架构 R6/R7）——语义可接受，非缺陷。

---

**第 2 轮结论**：D-01 / D-02 / D-03 **全部真实修复**（逐条实读 + 变异证伪）；钉扎用例已翻转为等值断言；全量 **267 / 0 / 0 / 0 全绿**（app 220 + core 47）、**0 告警**、release **+16.0 KiB ≤ 100 KB**、零依赖/零权限/无 schema 变更、既有 175 条未削弱、`OnDeleteMemo` 语义未改。**P2 十项 10/10 全部真实生效**。**最终判定 → `PASS`；智能路由 → `NoOne`。**
