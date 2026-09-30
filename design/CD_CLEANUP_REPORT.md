# C 档 + D 档测试整理执行报告

**日期**：2026-09-18
**依据**：`design/TEST_AUDIT.md` §3.4（12 条疑似冗余）、§5（C/D 档建议）
**前置**：`design/TEST_CLEANUP_REPORT.md`（A 档归并 + B 档删除已完成，317 → 272）

---

## 一、执行摘要

| 项 | 结果 |
|---|---|
| C 档 | 12 项**逐对人工核对**，**11 项处理、1 项保留**；共删除 **15 条** |
| D 档 | 关闭 release 变体单测，任务列表中 `testReleaseUnitTest` 已消失 |
| `@Test` 总数 | **272 → 257**（app 209 + core 47 + androidTest 1） |
| 测试结果 | **0 失败 / 0 错误 / 0 跳过**，`BUILD SUCCESSFUL in 15s` |
| 生产代码改动 | **零**（本轮只改测试文件 + `app/build.gradle.kts`） |
| 恒真断言复扫 | **0** |

---

## 二、🔴 核心发现：QA 标注的「疑似重复」多数**不是干净超集**

逐条实读代码后，**没有一条可以直接删除**。弱版往往带独有断言，因此凡有独有断言的一律
**「先并入强版 → 再删冗余」**，而非直接删除。

| # | 用例（弱版） | 处置 | 弱版独有断言（已并入强版） |
|---|---|---|---|
| 1 | `fourSorts_produceDeterministicOrder` | 删除 | 无（干净超集） |
| 2 | `sortChange_doesNotMutateDataSet` | 删除 | 无（干净超集） |
| 3 | `batchAddTag_dedupesAndSkipsExisting` | 删除 | **`isSelectionMode == false`**（操作后自动退出多选） |
| 4 | `roundTrip_fieldByFieldEqual` | 删除 | 无 |
| 5 | `versionTooNew_isRejected` | 删除 | 无 |
| 6 | `corruptedJson_isRejected` | 删除 | 畸形输入 `"{ this is not json ]"`（已加入强版 variants） |
| 6 | `missingSchemaVersion_isRejected` | 删除 | 无（强版断言更强） |
| 6 | `emptyString_isRejected` | 删除 | 无 |
| 7 | `remainingDays_atDay29_30_31` | 删除 | **第 29 天→1、第 31 天→0**（相邻边界日） |
| **8** | `observeAllTags_happyPath_stillPopulatesTags` | **保留** | 见 §三 |
| 9 | `writeCrash_writesArchiveAndLastCrash_withFullReport` | 删除 | **`archive.exists()`、`last_crash` 存在、`report` 含「线程:」** |
| 10 | `writeCrash_whenDirNotWritable_returnsNull_andNeverThrows` | 删除 | 无（干净超集） |
| 11 | `longPress_repeatedOnSameId_isIdempotentToggleNotReset` | 删除 | 无（两个方向均被 `longPress_whileAlreadyInSelectionMode_...` 覆盖） |
| 12 | `dashboardInit_cancellation_isRethrown_notLogged` | 删除 | 见 §三 |
| 12 | `reviewInit_cancellation_isRethrown_notLogged` | 删除 | 见 §三 |
| 12 | `settingsInit_cancellation_isRethrown_notLogged` | 删除 | 见 §三 |

**删除合计 = 15 条。**

---

## 三、两处需要说明的判断

### #8 保留 `observeAllTags_happyPath_stillPopulatesTags`

QA 自己在审计表中已标注该用例「**有独立价值**」：除标签去重排序外，它还断言
**`Log.wCount + Log.eCount == 0`**（「正常路径不得产生任何警告/错误日志」）——
这是加固工作中「**非静默**」的反向守护。

> **判定：保留。** 为凑一个用例数而删掉审计者本人认可价值的用例，正是本次整理要避免的
> 「为减数量而削弱保护」。

### #12 保留合并版、删除 3 条细粒度版

QA 的顾虑是合并版「把三处边界合并为一次断言，**失败定位粒度变粗**」。实读后**该顾虑不成立**：

```kotlin
assertEquals("Dashboard.init 不得吞取消信号", 0, Log.eCount)
assertEquals("Review.init 不得吞取消信号", 0, Log.eCount)
assertEquals("Settings.init 不得吞取消信号", 0, Log.eCount)
```

断言消息**自带 VM 名标识**，失败时能直接定位到哪一个 ViewModel；且合并版**额外**断言
`escapes.isEmpty()`（取消信号不得逃逸为未捕获异常）。故合并版是严格超集 →
按「同一断言只留一处」原则，**保留合并版、删除 3 条细粒度版**。

---

## 四、D 档：关闭 release 变体单测

`app/build.gradle.kts` 新增：

```kotlin
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        variantBuilder.enableUnitTest = false
    }
}
```

**依据**（`design/TEST_AUDIT.md` 已实测核实）：
- `app/src/release/` 源集为空，release 与 debug 单测**逐条等价**（各 224 条，结果文件一一对应）
- R8 / 资源裁剪**不参与本地单元测试**，release 变体不产生额外信号
- debug 专属的 3 个 Preview 文件无任何测试引用

**验证**：
- 任务列表中 `testReleaseUnitTest` **已消失**，只剩 `:app:testDebugUnitTest`
- `./gradlew test --rerun-tasks`：**92s（冷跑）→ 15s**
- 顺带清理了陈旧的 `app/build/test-results/testReleaseUnitTest` 残留目录

---

## 五、最终测试资产

| 指标 | 值 |
|---|---|
| `@Test` 总数 | **257** |
| 分变体 | `app:testDebugUnitTest` **209** · `core:test` **47** · `androidTest` **1**（需真机） |
| 失败 / 错误 / 跳过 | **0 / 0 / 0** |
| 全量耗时 | **15s**（`--rerun-tasks`） |
| 演进 | 317（原始）→ 272（A+B 档）→ **257**（C+D 档），累计 **−60 条** |

---

## 六、遗留

- `androidTest/.../MigrationTest.kt` 需真机执行，本机仅编译通过（其空转断言已在 B 档改为实质断言）
- C 档 #8 保留 1 条与 `testCreateMemo_persistsAndUpdatesTags` 部分重叠的用例（理由见 §三）
