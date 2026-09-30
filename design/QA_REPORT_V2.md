# 增量 V2 · 独立验证报告（QA）

> 验证者：QA 工程师 严过关 ｜ 日期：2026-09-15
> 范围：E06 回收站 / E10 排序 / E11 批量操作 / E01 导出导入 / E02 自动快照 / Batch 3（BP / C5 / D4）
> 原则：**不采信 `IMPLEMENTATION_SUMMARY_V2.md` 的任何结论**；所有数字均为本机实测；与自述不符处以实测为准并显式标注。
> 环境：Windows + Git Bash（`cmd.exe` 被拦截）、JDK 17.0.13、Gradle Wrapper、**无模拟器/真机**。

---

## 1. 执行摘要

| 项 | 结论 |
|---|---|
| **总体结论** | **有条件 PASS**（27 ✅ / 7 ⚠️ / 0 ❌；0 个功能性缺陷；无源码 Bug） |
| **路由判定** | **`NoOne`**（未发现需工程师修复的源码缺陷；缺口为「覆盖不足 / 环境无法验证」，非缺陷） |
| **测试真实数字** | `./gradlew test --rerun-tasks` = **BUILD SUCCESSFUL**。app `testDebugUnitTest` **102 passed / 0 failed / 0 skipped（19 类）**；app `testReleaseUnitTest` **102 / 0 / 0**；`:core:test` **31 / 0 / 0（4 类）**。**剔除本次 QA 新增 19 条后，基线 = app 83 + core 31 = 114 passed / 0 failed** → 与工程师自述「114」**一致**（我已独立复算，非照抄）。含 QA 新增后每变体 **133 passed**。 |
| **`:core:test` 独立** | **BUILD SUCCESSFUL**，31 passed（可独立运行，无 app 依赖） |
| **构建产物（实测字节）** | debug = **17,719,245 B**（16.898 MiB）；release = **1,726,594 B**（1.6466 MiB） |
| **release 增幅** | 基线 1.53 MiB(1,604,044 B) → **+122,550 B ≈ +119.7 KiB ≤ 200 KB 红线 ✅** |
| **编译告警** | 源码告警 `w: file://` = **0**；仅 1 条基础设施 `w: Detected multiple Kotlin daemon sessions`（非源码告警）→ 「零编译告警」**实质成立** |
| **与自述的差异** | ① debug APK 实测 17,719,245 B，**比自述少 60,545 B**；② 「Manifest 零权限」在 **APK 产物层不成立**（合并后含 1 条 androidx.core 自动注入的自签名权限，非敏感、非本次新增）；③ 自述「114」已复核为真 |

**为什么是「有条件」而非「PASS」**：7 条 ⚠️ 全部源于**环境限制**（无设备 → `androidTest`/Baseline Profile/`am start -W` 无法执行）或**层次限制**（纯 JVM 无法跑 Room，导致「DB 级往返 / 迁移 / 事务性」只能以代码+生成 SQL 佐证，不能以可执行测试佐证）。这些不是实现缺陷，但在无设备环境**无法闭环**。

---

## 2. A. 构建与测试（逐条实测）

### A1. `./gradlew test --console=plain --rerun-tasks`（含 `--rerun-tasks` 排除 UP-TO-DATE 掩盖）

```
> Task :core:test
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest
> Task :app:testReleaseUnitTest
BUILD SUCCESSFUL in 3m 47s
```

XML 报告逐类统计（`app/build/test-results/testDebugUnitTest/*.xml`）：

```
=== app testDebugUnitTest === total tests=102 failures=0 errors=0 skipped=0 classes=19
=== app testReleaseUnitTest === total tests=102 failures=0 errors=0 skipped=0 classes=19
=== core test === total tests=31 failures=0 errors=0 skipped=0 classes=4
```

| 测试类 | 用例数 | 归属 |
|---|---|---|
| DataLayerContractAdversarialTest | 11 | 基线 |
| ExportCodecRoundTripTest | 8 | 基线 |
| SchemaV2ContractTest | 4 | 基线 |
| TrashRetentionTest | 7 | 基线 |
| DashboardViewModelTest | 7 | 基线 |
| MemoDetailViewModelTest | 3 | 基线 |
| MemoListDeleteConfirmTest | 6 | 基线 |
| MemoListSelectionTest | 7 | 基线 |
| MemoListSortTest | 4 | 基线 |
| MemoListViewModelTest | 8 | 基线 |
| ReviewViewModelTest | 7 | 基线 |
| SettingsViewModelTest | 5 | 基线 |
| NavigationBadgeTest | 2 | 基线 |
| WindowSizeClassTest | 4 | 基线 |
| **基线小计（app）** | **83** | |
| `ExportCodecAdversarialTest` | 5 | **QA 新增** |
| `SoftDeleteRestoreAdversarialTest` | 7 | **QA 新增** |
| `TrashRetentionBoundaryAdversarialTest` | 2 | **QA 新增** |
| `MemoSortDeterminismAdversarialTest` | 3 | **QA 新增** |
| `BatchTagMergeAdversarialTest` | 2 | **QA 新增** |
| **QA 新增小计** | **19** | |
| **app 每变体合计** | **102** | |
| core（4 类：AdversarialM1Stress 7 / EbbinghausScheduler 10 / MathTextPreprocessor 6 / RolloverEngine 8） | 31 | 基线 |

> **关键口径说明**：`./gradlew test` 会同时跑 `testDebugUnitTest` 与 `testReleaseUnitTest` 两个变体（各 102），外加 `:core:test`（31），**全任务共执行 235 个用例，全部通过**。工程师自述的「114」= **单个 app 变体基线 83 + core 31**，我已独立复算一致。

**结论**：`114 passed / 0 failed` **属实**（且新增 30 用例亦属实：基线 app 53 → 现 83）。

### A2. `:core:test` 独立

```
> Task :core:test
BUILD SUCCESSFUL in 5m 3s
```
core 报告：`total tests=31 failures=0 errors=0 skipped=0`。✅ `:core` 可独立测试。

### A3. `assembleDebug` / `assembleRelease` 与体积

```
> Task :app:assembleDebug
> Task :app:assembleRelease
BUILD SUCCESSFUL
```
```
17719245  app/build/outputs/apk/debug/app-debug.apk
1726594   app/build/outputs/apk/release/app-release.apk
```

| 产物 | 实测字节 | 自述字节 | 差异 |
|---|---|---|---|
| debug | **17,719,245** | 17,779,790 | **−60,545 B（自述偏大）** |
| release | **1,726,594** | 1,726,594 | 0（一致） |

- release 增幅：`1,726,594 − 1,604,044(1.53 MiB)` = **+122,550 B ≈ +119.7 KiB ≤ 200 KB ✅**（debug 非红线）。
- debug 与自述差 ~60 KB，不影响任何红线（红线仅约束 release），但说明**自述数字不可作为测量基准**，以本报告实测为准。

### A4. 编译告警

```
$ grep -c '^w:' /tmp/qa_build.log      → 1
$ grep -c 'w: file://' /tmp/qa_build.log /tmp/qa_test_full3.log → 0 / 0
$ grep -n '^w:' /tmp/qa_build.log      → w: Detected multiple Kotlin daemon sessions at
```
- **源码编译告警 = 0**（无任何 `w: file://` 指向源文件）。唯一 `w:` 是 Kotlin Gradle Plugin 的多守护进程提示，属**环境噪声**。
- **判定**：工程师「零编译告警」**实质成立**（措辞上应表述为「零源码编译告警」）。

### A5. `androidTest` 源集编译（无法执行，仅编译）

```
> Task :app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 28s
```
→ `MigrationTest.kt` **可编译**；**未执行**（无模拟器/真机，如实声明，未伪造）。

---

## 3. B. 关键风险点复核（12 条）

| # | 复核项 | 结论 | 独立证据（实测） |
|---|---|---|---|
| B1 | **Room schema 基线顺序** | ✅ **正确** | `1.json`：`"version": 1`，`grep -c deletedAt = 0`（**不含 deletedAt**）；`2.json`：`"version": 2`，`deletedAt` 字段 `"affinity":"INTEGER"`、`"notNull": false`。生成顺序未颠倒。 |
| B2 | **迁移兜底** | ✅ **仅降级兜底** | 全局 `grep fallbackToDestructiveMigration` 仅 2 命中：`AppDatabase.kt:75` 为**注释**、`:76` 为 `.fallbackToDestructiveMigrationOnDowngrade()`。**不存在**全量 `fallbackToDestructiveMigration()`。 |
| B3 | **复习口径同源** | ✅ **单一 SQL，三处共用** | 唯一查询 `ReviewTaskDao.getDueMemosWithTasks`（`:49` 含 `AND knowledge_memos.deletedAt IS NULL`）。三消费点均经 `ReviewRepositoryImpl.kt:20` → 同一查询：`ReviewViewModel.kt:92`、`DashboardViewModel.kt:94/190`、Badge 由 `AppNavigation.kt:188-189` 复用 `dashboardState.dueTodayCount`（其源即 `DashboardViewModel` 同查询）。**非各写一份**。 |
| B4 | **零新增权限** | ⚠️ **源码层成立，产物层有 1 条** | 源码 `app/src/main/AndroidManifest.xml` **无任何 `<uses-permission>`**；但**合并后 release manifest** 含 1 条 `com.ebbinghaus.memo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（`protectionLevel=signature`）——由 `androidx.core` 自动注入的**自签名、非敏感**权限，**非本次新增**（基线即存在）。无 INTERNET/CAMERA/RECORD_AUDIO/存储权限。 |
| B5 | **`:core` 零 Android 依赖 + 包名不变** | ✅ | `grep -rn "androidx\.\|android\." core/src` = **0 命中**；12 个文件包名全为 `com.ebbinghaus.memo.core.*`（零 import churn）。 |
| B6 | **无新增强制依赖** | ✅ | 全 gradle 文件 `grep -niE "hilt\|koin\|work-runtime\|workmanager\|glance\|mlkit\|ml-kit\|kotlinx-serialization\|gson\|moshi\|documentfile\|datastore"` = **0 命中**。`app/build.gradle.kts` 依赖清单人工核对：仅 `:core`、AndroidX/Compose、Room、`profileinstaller`、`org.json`(test)、androidTest 运行器。 |
| B7 | **`baseline-prof.txt` 真实入库且打包** | ✅ | 源文件 `app/src/main/baseline-prof.txt`（19 行，手写最小集）；**解包 release APK 实测**：`assets/dexopt/baseline.prof = 4,625 B`、`baseline.profm = 593 B`、`META-INF/androidx.profileinstaller_profileinstaller.version = 6`、`classes.dex = 2,611,984 B`。 |
| B8 | **测试替身同步** | ✅ 编译通过，**但存在「空转」风险点（见 D4）** | `FakeRepositories.kt`（`FakeMemoRepository` 实现全部新方法，真实内存软删/排序/批量）、`PreviewFakes.kt`（`searchMemos/softDelete*/restore/getTrashed/purge/addTag/getAllIncludingDeleted` 齐备）、`DataLayerContractAdversarialTest.kt`（`StubKnowledgeMemoDao`/`StubReviewTaskDao` 抽象基类集中承载 stub）。整模块编译通过 = 契约一致的部分证明。**但**：`FakeReviewRepository.getDueReviewTasks` 仅按 `dueDate` 过滤、**不含 deletedAt 语义** → 无法证明「软删条目从复习队列消失」（该性质仅由真实 SQL 保证，见 C/E06-①）。 |
| B9 | **软删除不触发 CASCADE** | ✅ | `softDeleteMemo` 仅调 `UPDATE`（`KnowledgeMemoDao.kt:48`；生成 SQL `KnowledgeMemoDao_Impl.java:132 = "UPDATE knowledge_memos SET deletedAt = ? WHERE id = ?"`），**无 DELETE** → 不触发 `ON DELETE CASCADE`。我的 `SoftDeleteRestoreAdversarialTest.softDeleteAndRestore_neverTouchReviewTasks` 断言 `reviewTaskDao` 写操作计数 = 0。 |
| B10 | **回收站独立查询域** | ✅ | `getTrashedMemos`（`:63`）`WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC`；`TrashViewModel` 独立订阅，不复用列表搜索/标签态。 |
| B11 | **偏好零二次迁移** | ✅ | 排序/快照开关/目录 URI/保留数/上次快照日全落 `SharedPreferencesPreferenceStore`（`PREFS_NAME="ebbinghaus_prefs"`）；`AppDatabase` 仅新增 `deletedAt` 一列，无第二张表/列。 |
| B12 | **UI 删除路径全走软删** | ✅ | `grep '\.deleteMemo('` 于 `app/src/main`：物理 `deleteMemo` **零 UI 调用点**；UI 删除仅 `softDeleteMemo`（`MemoDetailViewModel.kt:95`、`MemoListViewModel.kt:314/338`），`hardDeleteMemo` 仅 `TrashViewModel.kt:124`。无「误用物理删除」缺陷。 |

---

## 4. C. 业务验收标准复核表（PRD §3 · 34 条）

> 图例：✅ 有可执行测试或**决定性代码/SQL 证据**；⚠️ 本环境无法闭环（无设备 / 纯 JVM 无法跑 Room）；❌ 不成立。

### E06 回收站（P0）— 6 条

| # | 验收标准 | 判定 | 独立证据 |
|---|---|---|---|
| ① | 删除后 `deletedAt` 非空，且不出现在列表/搜索/标签/复习队列 | ✅ | `softDeleteById`（`KnowledgeMemoDao.kt:48`）；`getAllMemos`(`:41`)、`searchMemos`(`:89`)、`getDueMemosWithTasks`(`ReviewTaskDao.kt:49`) 均带 `deletedAt IS NULL`。**生成 SQL 实证**：`ReviewTaskDao_Impl.java:379/470/555` 均含 `AND knowledge_memos.deletedAt IS NULL`。测试：`MemoListSelectionTest:96-99`、我的 `SoftDeleteRestoreAdversarialTest`。 |
| ② | `review_tasks` 行保留不删（不触发 CASCADE） | ✅ | 软删=UPDATE（生成 SQL `KnowledgeMemoDao_Impl.java:132`），无 DELETE。我的 `softDeleteAndRestore_neverTouchReviewTasks`：`taskDao.mutations == 0`。 |
| ③ | 回收站按 `deletedAt DESC` 展示 + 剩余保留天数 | ✅ | `getTrashedMemos`（`:63`；生成 SQL `:586`）；`TrashScreen.kt:286`「删除于 X 天前 · 剩余 Y 天」用 `colorScheme.outline`（`:288`）。测试：`TrashRetentionTest` 7 条 + 我的 `TrashRetentionBoundaryAdversarialTest`。 |
| ④ | 还原后 `deletedAt=null` 且各字段逐字段等于删除前 | ✅ | `restoreById`（`:54`）**仅** `SET deletedAt = NULL`（生成 SQL `KnowledgeMemoDao_Impl.java:140`）。我的 `softDeleteThenRestore_fieldByFieldIdentical`：`before.copy(deletedAt=null) == after`，且 `createdAt/updatedAt/tags` 逐一断言。 |
| ⑤ | 超期条目物理删除，`review_tasks` 因 CASCADE 清除 | ✅ | `purgeTrashedBefore`（`:57`，`deletedAt < cutoff`）；`2.json` `review_tasks` 外键 `"onDelete":"CASCADE"`；`SchemaV2ContractTest.v2Schema_reviewTasksForeignKeyCascadeRetained`；我的 `cutoffBoundary_exact30dKept_olderPurged`（仓储级：整 30 天保留、超 30 天清除）。 |
| ⑥ | `MigrationTestHelper` 断言 v1→v2 条数不变、`deletedAt` 全 NULL | ⚠️ | `MigrationTest.kt` 已编写且**编译通过**，但**无设备 → 未执行**。JVM 兜底 `SchemaV2ContractTest`（4 绿）仅校验 `1.json`/`2.json`/迁移 SQL 常量，**不等于**真实 SQLite 迁移验证。 |

### E01 导出/导入（P0）— 5 条

| # | 验收标准 | 判定 | 独立证据 |
|---|---|---|---|
| ① | 导出为合法 JSON，含 `schemaVersion` 与全部字段 | ✅ | `ExportCodec.encode`（`ExportCodec.kt:31-78`）；`ExportCodecRoundTripTest` + 我的 `roundTrip_adversarialPayload_fieldByFieldEqual`（含引号/换行/U+001F/emoji/超长 20k/空 tags/软删条目/`userSettings=null`）。 |
| ② | 空库导出仍成功（合法空结构，不崩溃） | ✅ | `ExportCodecRoundTripTest.emptyLibrary_producesValidEmptyStructure`（断言 `"memos":[]`、`"reviewTasks":[]`、`"schemaVersion":1`）；我的对抗用例以 `userSettings=null` 往返成功。 |
| ③ | 往返：导出→全新空库导入→逐字段相等 | ⚠️ | **仅 codec/DTO 级已证**（`ExportCodecRoundTripTest` + 我的对抗往返 + 我的 `roundTrip_entityDtoMapping_preservesEveryField`）。**DB 级往返未证**：`DataPortRepository.importSnapshot`（`:79-113`）依赖 Room 事务，纯 JVM 无 Robolectric 无法执行 → 「新库导入后逐字段相等」缺少可执行证据。 |
| ④ | 走 SAF，`AndroidManifest` 零新增权限 | ✅（源码层） | `DataSafetySection.kt:81/88/95` 三 SAF 启动器（`CreateDocument`/`OpenDocument`/`OpenDocumentTree`）；源码 Manifest 无 `<uses-permission>`。产物层见 B4 说明。 |
| ⑤ | `AppDatabase.version` 仍为 **1**（零迁移） | ⚠️ | 实测 `AppDatabase.kt:31 = version = 2`。**判定**：E01 自身零 schema 变更成立；但字面「version=1」与同批 E06 的 v1→v2 迁移**互斥**，属 PRD 内部冲突，工程师按架构仲裁取 `version=2`。**建议 PRD 修订该条**。 |

### E10 列表排序（P1）— 4 条

| # | 验收标准 | 判定 | 独立证据 |
|---|---|---|---|
| ① | 四种排序各产生确定性顺序（断言首元素 ID 序列） | ✅ | DAO `CASE WHEN :sortKey = N ... , m.id DESC`（`KnowledgeMemoDao.kt:97-101`）；`MemoListSortTest:47-65`；**我的 `MemoSortDeterminismAdversarialTest`**：刻意乱序 + 并列键数据集，断言 4 条精确 ID 序列 `[10,50,30,40,20]`/`[20,50,30,40,10]`/`[40,30,10,50,20]`/`[50,20,30,10,40]`。 |
| ② | 排序选择重启后保持 | ✅ | `MemoListSortTest:68-80`（重建 ViewModel 从同一 `PreferenceStore` 恢复 `DUE_ASC`）。 |
| ③ | 切换排序不重置搜索词与标签筛选 | ✅ | `MemoListSortTest:83-100`（切排序后 `searchQuery`/`selectedTag` 不变）。 |
| ④ | 排序仅影响展示，不修改 `dueDate`/调度状态 | ✅ | DAO 仅改 `ORDER BY`，不参与过滤；`MemoListSortTest:103-113`；我的 `sorting_neverChangesMembership_orSoftDeletedLeakIn`（4 种排序下集合恒为 5，软删条目不泄入）。 |

### E11 批量多选（P1）— 4 条

| # | 验收标准 | 判定 | 独立证据 |
|---|---|---|---|
| ① | 长按进多选、显示「已选 N 条」、全选/取消/退出清空 | ✅ | `MemoListSelectionTest:42-80`；`MemoListScreen.kt:371/401`（`onLongClick`）、`SelectionActionBar.kt`；`AppScaffold.kt:59-113`（`selectionBar` 替换底栏/Rail）。 |
| ② | 批量删除 N 条在**单事务**内完成，均可从回收站还原 | ✅（代码+替身级） | `softDeleteMemos` 走 `database.withTransaction`（`MemoRepositoryImpl.kt:125-130`）；DAO 为单条 `UPDATE ... WHERE id IN (:ids)`（`KnowledgeMemoDao.kt:51`）；我的 `batchSoftDelete_isSingleStatementWithAllIds`（断言 `softDeleteByIds` 仅调用 1 次、ids 齐全、全部非空 deletedAt）；`MemoListSelectionTest:101-103` 还原后存活=3。**真实 SQLite 事务原子性未执行**（无设备）。 |
| ③ | 批量打标签去重合并，不重复、已含跳过 | ✅ | `addTagToMemos`（`MemoRepositoryImpl.kt:152-167`：`filter{!tags.contains(tag)}` → `copy(tags+tag)`）；`MemoListSelectionTest:107-124`；**我的 `SoftDeleteRestoreAdversarialTest.addTagToMemos_dedupesSkipsExistingAndPreservesOrder`**（已含跳过/多标签追加保留原序/空标签新增）+ `BatchTagMergeAdversarialTest`（混合选择/空白标签 no-op 且保持多选态）。 |
| ④ | 事务中断/中途取消后无半更新状态 | ⚠️ | 依赖 Room `withTransaction` 原子回滚；**纯 JVM 无法构造真实事务中断**，仅有代码级保证。无对应可执行测试。 |

### E02 自动快照（P1）— 5 条

| # | 验收标准 | 判定 | 独立证据 |
|---|---|---|---|
| ① | 复用 E01 同一序列化层（同一 codec，非二次实现） | ✅ | `SnapshotManager.kt:79` 调 `dataPortRepository.exportJson()` → `DataPortRepository` → **同一 `ExportCodec`**；全项目仅一个 codec 实现（`grep` 确认）。 |
| ② | 快照写入 SAF 授权目录，文件名含日期 | ✅（代码级） | `SnapshotManager.kt:84-87`（`ebbinghaus-snapshot-YYYYMMDD.json` + `DocumentsContract.createDocument`）。无 JVM 测试（需 SAF/设备）。 |
| ③ | 目录不可写/URI 失效时提示失败且不崩溃 | ✅（代码级） | `SnapshotManager.kt:102-106`（`SecurityException`→`PERMISSION_LOST`，`Exception`→`IO_ERROR`）；`DashboardViewModel.kt:161-170` 失败→`disableSnapshot()` + Snackbar；四类文案 `DataSafetyViewModel.kt:246-255`。无 JVM 测试。 |
| ④ | 在 IO 线程执行，不阻塞主线程 | ✅ | `SnapshotManager.kt:74` `withContext(Dispatchers.IO)`（`pruneTo` 同 `:115`）。 |
| ⑤ | 按保留策略仅保留最近 N 份 | ✅（代码级） | `pruneTo`（`SnapshotManager.kt:114-157`，按文件名日期升序删最旧，删除失败仅 `runCatching` 忽略）。无 JVM 测试。 |

### BP Baseline Profile（P1）— 3 条

| # | 验收标准 | 判定 | 独立证据 |
|---|---|---|---|
| ① | `baseline-prof.txt` 入库并在 release 打包 | ✅ | 源文件在库；**解包 release APK 实测** `assets/dexopt/baseline.prof = 4,625 B`。 |
| ② | 同设备同构建类型冷启动改善 ≥15% | ⚠️ | **无设备 → 无法录制/度量**，未执行 `:baselineprofile:pmp`。`baseline-prof.txt` 为手写最小集（非录制产物）。 |
| ③ | release 体积增幅 ≤ 100 KB（BP 自身） | ✅ | BP 专项 = `profileinstaller` R8 后 ~15~25 KB + `baseline.prof` 4.6 KB + `profm` 0.6 KB ≈ 20~30 KB ≤ 100 KB。**注**：v2 **整体** release 增幅 +119.7 KiB（含 E01/E02/E06/E10/E11 全部代码），仍 ≤ 全局 200 KB 红线；「≤100 KB」应理解为 BP 自身足迹。 |

### C5 启动惰性化（P2）— 3 条

| # | 验收标准 | 判定 | 独立证据 |
|---|---|---|---|
| ① | `onCreate` 中 Room/仓储构建改 `by lazy` | ✅ | `EbbinghausApp.kt`：7 个属性全 `by lazy`；`onCreate` 仅 `super.onCreate()`（`:53-56`）；死代码 `companion.instance` 已删。 |
| ② | `am start -W` 的 `TotalTime` 有可量化下降 | ⚠️ | **无设备 → 未执行**。 |
| ③ | 冷启动后首个查询功能正常 | ⚠️ | 惰性语义正确 + 单测覆盖首查询路径（`MemoListViewModelTest`/`DashboardViewModelTest`/`ReviewViewModelTest`），但**惰性 DB 的真实首次访问**未在设备上验证。 |

### D4 `:core` 模块化（P2）— 4 条

| # | 验收标准 | 判定 | 独立证据 |
|---|---|---|---|
| ① | `:core` 独立 module，无 Android 依赖泄漏，`:core:test` 独立通过 | ✅ | `core/build.gradle.kts`（`kotlin.jvm` + `jvmToolchain(17)`）；`grep android/core/src = 0`；`./gradlew :core:test` **独立 31 passed**。 |
| ② | `:app` 依赖 `:core` | ✅ | `app/build.gradle.kts:73` `implementation(project(":core"))`。 |
| ③ | 包名保持 `com.ebbinghaus.memo.core.*`（零 import churn） | ✅ | 12 文件包名未变（实测 grep）。 |
| ④ | `./gradlew test` 仍 84 passed | ✅ | 实测基线 **114**（83+31），0 回归（≥84 且含新增）。 |

**C 段小结**：**✅ 27 / ⚠️ 7 / ❌ 0**（工程师自述为 31 ✅ / 3 ⚠️；差异在于我把 4 条「仅代码级/无 DB 级可执行证据」的项如实降级为 ⚠️，详见 §8 缺陷清单 D4）。

---

## 5. D. 边界场景复核（PRD §5，抽查 9/16 条）

| §5# | 场景 | 代码/测试对应 | 判定 |
|---|---|---|---|
| 1 | 软删条目从复习队列/Badge 消失，三处同源；还原后按免惩罚进队列 | `ReviewTaskDao.kt:49` `deletedAt IS NULL`；生成 SQL `ReviewTaskDao_Impl.java:379/470/555`；三处共用 `ReviewRepositoryImpl.kt:20` | ✅ 代码/SQL 级；**无 JVM 测试**（替身不含该语义） |
| 2 | 软删不删 `review_tasks`，还原即恢复进度 | 软删=UPDATE；`restoreById` 仅置 NULL | ✅（我的 `softDeleteAndRestore_neverTouchReviewTasks`） |
| 3 | 超期条目启动时批量物理删除（IO，失败不阻断） | `DashboardViewModel.kt:155-158` `runCatching { purgeExpiredTrash(...) }`；`purgeTrashedBefore` | ✅ 代码级（我的仓储级 cutoff 边界测试） |
| 4 | 回收站内再次删除 = 立即物理删除 + 二次确认 | `TrashViewModel.OnRequestDeleteForever/OnConfirmDeleteForever` → `hardDeleteMemo`；`TrashScreen` 二次确认 | ✅ 代码级 |
| 5 | 批量操作中途取消/进程被杀 → 单事务回滚 | `withTransaction` 包裹批量删除/打标签 | ⚠️ 代码级；真实中断未测 |
| 6 | 批量打标签遇重复 → 跳过不重复 | `filter{!tags.contains}` + `copy(tags+tag)` | ✅（我的去重合并用例） |
| 7 | 导出数据为空 → 合法 JSON、提示 0 条、不崩溃 | `ExportCodec` 空结构；`DataSafetyViewModel.kt:176`「已导出 N 条」 | ✅（codec 级） |
| 8 | 导入损坏 JSON → 提示且**不写入任何数据** | `ExportCodec.decode` 先全量解析，失败即 `CorruptedFile`，**不进入** `withTransaction`（`DataPortRepository.kt:82-86` 早返回） | ✅ 代码级（我的 `corruptedJson_variants_allRejected` 7 变体全拒） |
| 9 | 导入版本不兼容 → 明确提示、不部分导入 | `ExportCodec.decode` `version > CURRENT` → `VersionTooNewException`；`DataPortRepository.kt:84` | ✅（我的 `versionTooNew_isRejectedWithoutPartialParse`：内容合法仍整体拒绝） |
| 10 | 导入写入失败 → 单事务整体回滚 | `db.withTransaction{ deleteAll×3 → insertAll×3 }`（`:90-107`），异常 → `ImportResult.Failed` | ⚠️ 代码级；真实回滚未测 |
| 11 | 快照目录不可写/URI 失效 → 提示并**自动关闭开关** | `SnapshotManager.kt:102-106`；`DashboardViewModel.kt:163-166` `disableSnapshot()` | ✅ 代码级 |
| 12 | 快照保留份数超限 → 删最旧，删除失败不阻塞 | `pruneTo`（`:114-157`） | ✅ 代码级 |
| 13 | 搜索/筛选下进入多选 → 筛选控件置灰，退出恢复 | `MemoListScreen.kt:285/303/311` `enabled = !selectionEnabled`；`MemoListScreen.kt:115` `BackHandler(enabled=isSelectionMode)` | ✅ 代码级 |
| 14 | 迁移失败兜底仅 `OnDowngrade`，绝不全量 fallback | `AppDatabase.kt:76` | ✅（B2） |
| 15 | Baseline Profile 与代码漂移 → 不为凑指标加依赖 | 无被禁依赖（B6） | ✅ |
| 16 | `:core` 抽取后零 Android 依赖、包名不变 | `grep = 0`；包名未变 | ✅（B5） |

---

## 6. E. 对抗性测试（我新增，已写入项目并跑通）

> 新增 2 个测试文件、5 个测试类、**19 条用例**，全部通过（app 变体 102 = 基线 83 + QA 19，0 失败）。
> 位置：`app/src/test/java/com/ebbinghaus/memo/data/QaAdversarialDataTest.kt`、`app/src/test/java/com/ebbinghaus/memo/ui/QaAdversarialUiTest.kt`。
> 说明：**未修改任何 `app/src/main` 业务源码**；以「生产仓储 `MemoRepositoryImpl` + 可控替身 DAO」在纯 JVM 证伪。

| 类 | 用例 | 证伪目标 | 结果 |
|---|---|---|---|
| `ExportCodecAdversarialTest` | `roundTrip_adversarialPayload_fieldByFieldEqual` | 引号/反斜杠/换行/制表符/U+001F/emoji/CJK/20k 超长/空 tags/软删条目/`userSettings=null` 往返是否逐字段相等 | ✅ 通过 |
| | `roundTrip_entityDtoMapping_preservesEveryField` | `KnowledgeMemoEntity`/`ReviewTaskEntity` ↔ DTO 映射是否无损（含 `deletedAt`、`lastReviewDate=null`） | ✅ |
| | `versionTooNew_isRejectedWithoutPartialParse` | 高版本但内容合法的文件是否被**整体**拒绝 | ✅ |
| | `corruptedJson_variants_allRejected` | 空串/纯空白/非 JSON/截断/缺数组/缺版本/根非对象 7 变体是否全拒 | ✅ |
| | `decode_isPure_doesNotDependOnPriorEncode` | 编解码是否有隐藏状态 | ✅ |
| `TrashRetentionBoundaryAdversarialTest` | `remainingDays_atDay29_30_31` | 30 天 cutoff 的**边界日**第 29/30/31 天 | ✅ |
| | `cutoffBoundary_exact30dKept_olderPurged` | 整 30 天**保留**、超 30 天清除、存活不受影响（仓储级） | ✅ |
| `SoftDeleteRestoreAdversarialTest` | `softDeleteThenRestore_fieldByFieldIdentical` | 软删→还原**逐字段**等于删除前 | ✅ |
| | `softDeleteAndRestore_neverTouchReviewTasks` | 软删/还原是否触碰 `review_tasks`（写计数须为 0） | ✅ |
| | `batchSoftDelete_isSingleStatementWithAllIds` | 批量删除是否单语句、ids 齐全、全部软删 | ✅ |
| | `batchSoftDelete_emptyList_isNoOp` | 空选择批量删除是否 no-op | ✅ |
| | `addTagToMemos_dedupesSkipsExistingAndPreservesOrder` | 已含跳过 / 多标签追加保序 / 空标签新增 | ✅ |
| | `addTagToMemos_blankTag_isNoOp` | 空白/空标签不得写入 | ✅ |
| | `hardDeleteAndClearTrash_delegateToDao` | 清空回收站只移除软删条目 | ✅ |
| `MemoSortDeterminismAdversarialTest` | `fourSorts_onShuffledData_produceExactIdSequences` | 乱序+并列键下 4 排序的**精确 ID 序列**（含 id DESC 兜底） | ✅ |
| | `sorting_isDeterministic_acrossRepeatedApplication` | 同排序反复应用是否稳定 | ✅ |
| | `sorting_neverChangesMembership_orSoftDeletedLeakIn` | 排序不改成员集、软删不泄入 | ✅ |
| `BatchTagMergeAdversarialTest` | `batchAddTag_mixedSelection_dedupesAndSkipsExisting` | 混合选择（已含/多标签/空标签/未选中）去重合并 | ✅ |
| | `batchAddTag_blankOrEmpty_neverMutatesNorExitsSelection` | 空白标签不改数据且保持多选态 | ✅ |

**对抗结论**：**未能证伪**任何实现。所有攻击面（特殊字符/超长/软删往返、30 天边界、单语句批删、去重合并、乱序排序确定性、损坏/高版本拒绝）行为均符合 PRD 预期。

---

## 7. 智能路由判定

### 判定：**`NoOne`**

**依据**：
1. **无源码缺陷**：19 条对抗用例全部通过，未能证伪任何实现；软删除/还原/批量/排序/编解码/保留策略/迁移 SQL 行为均符合 PRD 与架构。
2. **无测试代码缺陷**：我新增的用例仅因 Kotlin 抽象成员未实现/挂起函数调用位置在首轮编译报错（**我自己的测试代码问题**），已按规则**自行修复**，第二轮全绿 —— 属 QA 自修范畴，不涉及源码。
3. **未通过项均为环境/层次限制**：7 条 ⚠️ 全部为「无设备」（androidTest/BP/C5）或「纯 JVM 无法跑 Room」（DB 级往返/迁移/事务），**非实现缺陷**。

> 备查：若后续要求在无设备环境闭环迁移与事务，需**新增 Robolectric 依赖**（架构 R3 已明确标注为需拍板的**新依赖**，与「轻量」红线冲突），故本次不擅自引入。

---

## 8. 缺陷清单

> 严重度：🔴 高 / 🟠 中 / 🟡 低 / 🔵 提示。**本次无 🔴/🟠 功能性缺陷。**

| ID | 严重度 | 标题 | 期望 vs 实际 | 证据 |
|---|---|---|---|---|
| **D1** | 🟡 | debug APK 体积与工程师自述不符 | 期望：自述 17,779,790 B；实际：**17,719,245 B**（少 60,545 B）。release 一致（1,726,594 B）。**不影响红线**（红线仅约束 release） | `stat -c '%s' app-debug.apk` |
| **D2** | 🔵 | 「Manifest 零权限」在产物层不成立 | 期望：APK 无 `<uses-permission>`；实际：合并后 release manifest 含 1 条 `com.ebbinghaus.memo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（`signature` 级，`androidx.core` 自动注入，**非敏感、非本次新增**）。源码 Manifest 确为 0 条 | `app/build/intermediates/merged_manifest/release/.../AndroidManifest.xml` |
| **D3** | 🔵 | 「零编译告警」措辞 | 期望：0；实际：源码告警 `w: file://` = **0** ✅，但存在 1 条基础设施 `w: Detected multiple Kotlin daemon sessions`。建议表述为「零源码编译告警」 | `grep -c '^w:' = 1`；`grep -c 'w: file://' = 0` |
| **D4** | 🟠（覆盖缺口，非缺陷） | 关键新组件无任何 JVM 测试 | 期望：核心增量有单测守护；实际：**`DataPortRepository` / `SnapshotManager` / `TrashViewModel` / `DataSafetyViewModel` 零 JVM 测试**；E01③（DB 级往返）、E11②④（真实事务）、E06-①（复习队列排除）、C5③（惰性首查询）均**仅代码级**证据。建议：无设备环境下至少为 `DataPortRepository` 引入可注入 DB 抽象或 Robolectric | `grep -rln "DataPortRepository\|SnapshotManager\|TrashViewModel\|DataSafetyViewModel" app/src/test = 空` |
| **D5** | 🟡（PRD 冲突） | E01⑤「version 仍为 1」与 E06 迁移互斥 | 期望（PRD 字面）：`version=1`；实际：`version=2`（E06 唯一迁移触发）。属 **PRD 内部冲突**，非实现缺陷。建议修订 PRD E01⑤ 表述为「E01 自身零 schema 变更」 | `AppDatabase.kt:31` |

**严重度分布**：🔴 0 ｜ 🟠 1（覆盖缺口）｜ 🟡 2 ｜ 🔵 2。**功能性缺陷 = 0。**

---

## 9. 遗留与未验证项（如实列出）

| # | 项 | 原因 | 后续动作 |
|---|---|---|---|
| L1 | **`androidTest` 未执行** | 无模拟器/真机 | `MigrationTest.kt` 已编译通过；有设备后 `./gradlew connectedAndroidTest` 执行（验证 v1→v2 条数不变、`deletedAt` 全 NULL、tasks 保留） |
| L2 | **Baseline Profile 未录制** | 无设备 | `baseline-prof.txt` 为**手写最小集**（非录制产物）；有设备后 `./gradlew :baselineprofile:pmp` 重录；**BP② 冷启动 ≥15% 无法验收** |
| L3 | **C5② `am start -W` 未度量** | 无设备 | 有设备后对比改造前后 `TotalTime` |
| L4 | **DB 级往返 / 真实事务 / 真实迁移未执行** | 纯 JVM 无 Room/Robolectric | 见 D4；如要求闭环需引入 Robolectric（新依赖，待拍板） |
| L5 | **E06-① 复习队列排除无 JVM 测试** | `FakeReviewRepository` 不建模 `deletedAt` | 已由 KSP 生成 SQL（`ReviewTaskDao_Impl.java:379/470/555` 含 `deletedAt IS NULL`）佐证；建议补 DAO 级测试或替身语义 |
| L6 | **关键词固定 6 元上限（架构 R4）** | 架构 D5 有意设计 | 超 6 词被忽略，已文档化，非缺陷 |
| L7 | **软删标签从筛选栏消失（架构 R7）** | `allTags` 由已排除软删的 `getAllMemos` 派生 | 语义合理，可接受 |
| L8 | **PRD E01⑤ 内部冲突** | 见 D5 | 建议修订 PRD |

---

## 10. 复现命令（本机实测）

```bash
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11
cd /d/Projects/androidapk

# 全量测试（含 QA 对抗用例），含 --rerun-tasks 排除陈旧结果
./gradlew test --console=plain --rerun-tasks            # BUILD SUCCESSFUL；app 102×2 + core 31

# :core 独立测试
./gradlew :core:test --console=plain --rerun-tasks      # BUILD SUCCESSFUL；31 passed

# 构建 + 体积
./gradlew assembleDebug assembleRelease --console=plain --rerun-tasks
stat -c '%s  %n' app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release.apk
unzip -l app/build/outputs/apk/release/app-release.apk | grep -E 'baseline|dexopt'

# 告警
./gradlew test assembleDebug assembleRelease --console=plain 2>&1 | grep -c '^w: file://'   # 0
```

---

## 11. 最终结论

**有条件 PASS** ｜ **路由：NoOne** ｜ **功能缺陷：0** ｜ **测试：基线 114 passed/0 failed（独立复算一致），含 QA 新增 133 passed/0 failed/变体** ｜ **release 体积增幅 +119.7 KiB ≤ 200 KB** ｜ **源码零编译告警**。

6 项增量（E06/E10/E11/E01/E02/Batch3）的**可断言验收标准**在代码、生成 SQL 与可执行测试层面**均成立且未被对抗性测试证伪**；全部未通过项源于「无设备」与「纯 JVM 无法运行 Room」的环境/层次限制，**非实现缺陷**。建议在具备 Android 设备的环境补跑 `connectedAndroidTest` 与 `:baselineprofile:pmp` 以闭环 L1/L2/L3。
