# PACKAGE_UNIFY.md — 统一 debug / release 包名

> 任务：删除 debug 变体的 `applicationIdSuffix = ".debug"`，使 debug 与 release 同为
> `com.ebbinghaus.memo` → 二者互为覆盖安装（一个图标 + 数据互通）。
> 类型：小型构建配置变更（快速模式）。执行日期：2026-09-16。

---

## 1. 改动内容（唯一一处代码改动）

**文件**：`app/build.gradle.kts`，**行号**：原 `38-43`（`debug {}` 块）

### 改动前

```kotlin
        debug {
            applicationIdSuffix = ".debug"      // ← 删除这一行
            isDebuggable = true
            // 开启单元测试覆盖率采集（AGP 内置 JaCoCo），供 testDebugUnitTestCoverage 使用
            enableUnitTestCoverage = true
        }
```

### 改动后

```kotlin
        debug {
            // 统一包名：不再追加 .debug 后缀，debug 与 release 同为 com.ebbinghaus.memo
            // （取自 defaultConfig.applicationId）→ 二者互为覆盖安装：同一份数据、只留一个图标。
            // 代价：同一台设备同一时刻只能装一个变体；若将来 release 改用正式签名，
            //       则与 debug 签名不一致，覆盖安装会因签名校验失败而失败。
            isDebuggable = true
            // 开启单元测试覆盖率采集（AGP 内置 JaCoCo），供 testDebugUnitTestCoverage 使用
            enableUnitTestCoverage = true
        }
```

- 删除：`applicationIdSuffix = ".debug"`（1 行）
- 保留：`isDebuggable = true`、`enableUnitTestCoverage = true`（未动）
- 新增：仅注释（说明「同包名可覆盖安装共享数据」及其代价）
- 包名来源：`defaultConfig.applicationId = "com.ebbinghaus.memo"`（`app/build.gradle.kts:14`），删除后缀后 debug 继承该值。

---

## 2. debug APK 包名 —— 实测证据（关键验收点）

重打 debug APK：`./gradlew assembleDebug --console=plain` → **BUILD SUCCESSFUL in 56s**。

### 证据 1：`aapt dump badging`（对实际产出的 APK 二进制，最强证据）

```
$ aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "^package:|^application-label:"
package: name='com.ebbinghaus.memo' versionCode='1' versionName='1.0.0' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
application-label:'艾宾浩斯备忘录'
```

> 包名 = **`com.ebbinghaus.memo`** ✅（不再带 `.debug`）

### 证据 2：合并后的 debug AndroidManifest（AGP 中间产物）

`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.ebbinghaus.memo"
    android:versionCode="1"
    android:versionName="1.0.0" >

    <uses-sdk
        android:minSdkVersion="26"
        android:targetSdkVersion="35" />

    <permission
        android:name="com.ebbinghaus.memo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
```

> 注：自动注入的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 也随之从
> `com.ebbinghaus.memo.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 变为
> `com.ebbinghaus.memo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（`androidx.core` 按包名自动生成，非新增权限）。

### 证据 3：AGP 产物元数据

`app/build/outputs/apk/debug/output-metadata.json`：

```json
{ "applicationId": "com.ebbinghaus.memo", "variantName": "debug", ... "outputFile": "app-debug.apk" }
```

`app/build/intermediates/compatible_screen_manifest/debug/createDebugCompatibleScreenManifests/output-metadata.json`：

```json
"applicationId": "com.ebbinghaus.memo"
```

### 附：release APK 包名（应不受影响）

```
$ aapt dump badging app/build/outputs/apk/release/app-release.apk | grep -E "^package:"
package: name='com.ebbinghaus.memo' versionCode='1' versionName='1.0.0' ...
```

> debug 与 release 现同为 **`com.ebbinghaus.memo`**，`versionCode` 均为 `1`（非降级，允许覆盖安装）。

---

## 3. 签名核验（证明「覆盖安装可成功」）

用 `apksigner verify --print-certs` 对比两个 APK 的签名证书：

| 变体 | Signer #1 DN | Signer #1 SHA-256 |
|---|---|---|
| **debug** | `C=US, O=Android, CN=Android Debug` | `92b4360c903fa6214018696fbb2d789e3145f2d1f61f0d1ebace3d8e6450adba` |
| **release** | `C=US, O=Android, CN=Android Debug` | `92b4360c903fa6214018696fbb2d789e3145f2d1f61f0d1ebace3d8e6450adba` |

> 二者**同一把调试钥匙**（release 复用 `signingConfigs.getByName("debug")`，`app/build.gradle.kts:36`）→
> 包名相同 + 签名相同 + versionCode 相同 ⇒ **互相覆盖安装可成功**。

---

## 4. 测试与构建的真实数字

命令：`./gradlew test --console=plain --rerun-tasks` → **BUILD SUCCESSFUL in 6m 26s（57 tasks executed，全量重跑）**

按 JUnit 结果 XML 实测统计（`app/build/test-results/`、`core/build/test-results/`）：

| 测试任务 | 测试类 | 用例数 | 失败 | 错误 | 跳过 |
|---|---|---|---|---|---|
| `app:testDebugUnitTest` | 42 | 269 | 0 | 0 | 0 |
| `app:testReleaseUnitTest` | 42 | 269 | 0 | 0 | 0 |
| `core:test` | 5 | 47 | 0 | 0 | 0 |
| **合计** | — | **585** | **0** | **0** | **0** |

> **口径对照**：原基线「316 条/变体」= app 269 + core 47 = **316**。本次两变体各 269 app 用例均通过，
> core 47 通过 ⇒ **316 条/变体基线保持不变，全绿，零删改断言**。

构建产物：

| 命令 | 结果 | 产物 |
|---|---|---|
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL in 56s** | `app/build/outputs/apk/debug/app-debug.apk`（17,817,604 字节 ≈ 16.99 MB） |
| `./gradlew assembleRelease` | **BUILD SUCCESSFUL in 1m 22s** | `app/build/outputs/apk/release/app-release.apk`（1,759,362 字节 ≈ 1.68 MB） |

> `assembleRelease` 不受影响（release 包名本就是 `com.ebbinghaus.memo`）。

---

## 5. 文档一致性更新清单

全项目检索 `.debug` / `applicationIdSuffix` / `com.ebbinghaus.memo.debug` 后，逐条处置如下。

> **行号口径**：下表为 **2026-09-16 经 QA 回归、逐处重新核对更正后**的**当前行号**；括号内为更正注解所在行。

**判定标准**：
- **面向未来的操作指引 / 命令 / 排查步骤 / 元数据** → **必须更正 / 加注**（否则用户会照错的做）。
- **历史崩溃证据**（当时的 logcat 原文、当时的合并清单实测记录、当时的审计结论）→ **保留原貌**（改动即篡改历史证据）。

| 文件 | 位置（当前行号） | 类型判定 | 处置 | 理由 |
|---|---|---|---|---|
| `TEST_READY.md` | `:90` | 面向未来的元数据 | ✅ **已更新**为 `com.ebbinghaus.memo`（附指向本文件） | Application ID 元数据须与现状一致 |
| `design/CRASH_INVESTIGATION.md` | `:232`（注解 `:234`） | 面向未来的排查指引 | ✅ **已加注** | `adb logcat` 包名过滤指引，用旧包名会抓不到日志 |
| `design/CRASH_INVESTIGATION.md` | `:246`（注解 `:247`） | 面向未来的排查提示 | ✅ **已加注** | §7「给用户的下一步」引用已删除的 `applicationIdSuffix` |
| `design/CRASH_INVESTIGATION.md` | `:81` | 历史证据（D11 审计结论） | ⏸ **保留原样** | 审计时的静态结论，属历史记录 |
| `design/CRASH_INVESTIGATION.md` | `:465` | 历史崩溃证据（logcat 原文） | ⏸ **保留原样** | 带时间戳的原始 logcat 片段 |
| `design/QA_CRASH_REPORT.md` | `:317`（注解 `:319`） | 面向未来的排查指引 | ✅ **已加注** | `adb logcat` 包名过滤指引 |
| `design/QA_CRASH_REPORT.md` | `:329`（注解 `:330`） | 面向未来的排查提示 | ✅ **已加注** | §8「给用户的建议」引用已删除的 `applicationIdSuffix` |
| `design/QA_CRASH_REPORT.md` | `:633`（注解 `:634`） | 面向未来的排查指引 | ✅ **已加注**（**本轮补正**） | §7.2 `adb logcat` 包名过滤指引；此前**声称已加注、实际未加注** |
| `design/QA_CRASH_REPORT.md` | `:537` | 历史证据（当轮合并清单实测记录） | ⏸ **保留原样** | 带时间戳的历史证据 |
| `design/QA_FIX_REPORT.md` | `:125` | 历史证据（当轮合并清单实测记录） | ⏸ **保留原样** | 同 `QA_CRASH_REPORT.md:537` |
| `TEST_INFRA.md` | — | 无硬编码包名 | ✅ **无需更新** | 仅「debug 变体」「`app/src/debug/…` 源集」等措辞，与包名无关 |

**原则**：面向未来操作的「指引 / 命令 / 排查步骤 / 元数据」已更正 / 加注；带时间戳的历史 logcat / 合并清单 / 审计结论保持原貌，不篡改。

---

## §5 补正（QA 回归发现）

> 触发：QA 独立回归（`design/QA_PACKAGE_UNIFY_REPORT.md` §4.4）发现本文件 §5 存在**一处不实声明**。
> 处置：本人于 2026-09-16 依据「诚实优先」原则，**重新逐处实读**四份文档（本文件 / `CRASH_INVESTIGATION.md` / `QA_CRASH_REPORT.md` / `QA_FIX_REPORT.md`）并更正，不再凭印象声称。

### 一、这次漏改了什么

- §5 原表声称：`QA_CRASH_REPORT.md:630`（加注后 `:632`）"✅ **已加注**更正说明"。
- **实测**：`QA_CRASH_REPORT.md` 全文的更正注解**当时只有 1 处**（在 `:319`），`:632`（§7.2 的 `adb logcat` 指引）**并未加注**。
- 即：**声称对 3 处「面向未来的排查指引」加注，实际只完成 2 处**。
- 该行位于 `### 7.2 若 App 崩到「连设置页都进不去」`——**面向未来的排查指引**，按判定标准**本应加注**：否则用户按旧包名 `com.ebbinghaus.memo.debug` 过滤 logcat 会**抓不到任何日志**，而这恰是当初排查闪退最卡的一环。

### 二、本轮补了哪些

| 补正项 | 位置（当前行号） | 说明 |
|---|---|---|
| **补加注（核心）** | `QA_CRASH_REPORT.md:633`（注解 `:634`） | §7.2 的 `adb logcat` 指引 —— 声明与实做不一致的那一处 |
| 补加注 | `CRASH_INVESTIGATION.md:246`（注解 `:247`） | §7「给用户的下一步」把 `applicationIdSuffix` 列为当前 debug-only 差异 |
| 补加注 | `QA_CRASH_REPORT.md:329`（注解 `:330`） | §8「给用户的建议」中同一处表述 |
| 更正声明 | 本文件 §5 | 由「声称已改」改为**与实际逐处一致的表格**，消除不实声明 |

### 三、分类判定标准（本次实际采用的判据）

| 类型 | 判定依据 | 处置 |
|---|---|---|
| **面向未来的操作指引 / 命令 / 排查步骤 / 元数据** | 内容用于**指导用户 / 同事后续动作**（如过滤 logcat、安装、排查步骤） | **必须更正 / 加注** |
| **历史崩溃证据** | 带时间戳的**当时的** logcat 原文、当时的合并清单实测记录、当时的审计结论 | **保留原貌**（改动即篡改历史证据） |

### 四、本轮**判断为「无需更正」**的位置及理由（诚实优先，不凑数）

- `CRASH_INVESTIGATION.md:81`（D11 审计结论）：属**当时**的静态审计记录，非面向未来的操作指引。
- `CRASH_INVESTIGATION.md:465`：**当时的 logcat 原文**（`Process: com.ebbinghaus.memo.debug`），历史崩溃证据，改动即失真。
- `QA_CRASH_REPORT.md:537` 与 `QA_FIX_REPORT.md:125`：**当时的合并清单实测记录**（`…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`），属历史证据；其存在恰好佐证「当时包名确为 `.debug`」，应保留。
- `TEST_INFRA.md`：全文**无硬编码包名**（仅「debug 变体」「`app/src/debug/…` 源集」等措辞，与包名无关），无需更新。
- `design/QA_PACKAGE_UNIFY_REPORT.md`：**QA 自己的报告** —— `:231` 是「引用待修指引」作为问题证据，`:274` 是「卸载旧孤儿包」的正确指引，**均无需改动**（且本文件不应改动 QA 报告）。

> 附：本文件 §6.2 / §7 中出现的 `com.ebbinghaus.memo.debug` 指**旧孤儿包**（用户需手动卸载的对象），用法正确，无需改动。

---

## 6. 给用户的安装指引与注意事项 ⚠️

### 6.1 现在发生了什么

- 本次改动后，**debug 与 release 是同一个应用**（包名同为 `com.ebbinghaus.memo`）。
- 后安装的会**覆盖**先安装的，**保留原有数据**（数据库 `ebbinghaus_memo.db`、偏好 `ebbinghaus_prefs` 均硬编码、与包名无关）。

### 6.2 安装步骤

1. **先卸载旧 debug 应用**：包名 `com.ebbinghaus.memo.debug` 的旧包已成为**孤儿应用**（新代码不再指向它），
   系统不会自动清理 —— 请在「设置 → 应用」里找到它（图标可能显示为「艾宾浩斯备忘录」）**手动卸载**。
   - 该孤儿包内的数据（若之前用它记过东西）**不会自动迁移**，如需保留请先用其内置的「导出」功能导出，再导入新包。
2. 安装新包：`adb install -r app/build/outputs/apk/debug/app-debug.apk`
   （或直接安装 `app-debug.apk` 文件；`-r` 表示覆盖安装）。
3. 结果：桌面**只剩一个图标**，debug 与 release 数据互通。

### 6.3 注意事项

- **同一时刻只能装一个**：debug 与 release 现在互斥，装了 debug 就覆盖了 release（反之亦然）。
  需要同时保留两份请勿统一包名（本改动即为此取舍）。
- **覆盖会保留数据**：从 release 覆盖装 debug（或反向）**不会丢数据**；但若「先卸载再安装」则会清空数据。
- **签名前提**：覆盖安装成功的前提是两者**签名一致**。当前 release 复用 debug 签名（`app/build.gradle.kts:36`），故可覆盖。
  **若将来 release 改用正式签名**，则 release 与 debug 签名不一致，二者互相覆盖安装会**因签名校验失败而失败**
  （届时需先卸载旧包）。此代价已写入 `debug {}` 块注释。
- **不影响 release 上架**：本改动只作用于 debug 变体；release 包名、签名、行为均未改变。

---

## 7. 对主理人核实结论的复核（诚实优先）

逐条复核，**结论全部属实，未发现错误**：

| 核实项 | 复核结果 |
|---|---|
| 代码是否依赖包名 | ✅ 唯一 `crash/CrashMetadata.kt:17` 用 `context.packageName` 运行时取值，任何包名可行 |
| FileProvider / authorities | ✅ `app/src/main/AndroidManifest.xml` 无 `<provider>`/`authorities` |
| 数据库名 / 偏好名 | ✅ `AppDatabase.kt` = `"ebbinghaus_memo.db"`、`PreferenceStore.kt` `PREFS_NAME = "ebbinghaus_prefs"`，均硬编码 |
| 签名是否匹配 | ✅ 实测两 APK 同一证书（SHA-256 `92b4360c…adba`） |
| versionCode | ✅ 两边均 `1` |
| `baselineprofile` 模块 | ✅ `targetProjectPath = ":app"`，无硬编码包名；`StartupBenchmark.kt:70 TARGET_PACKAGE = "com.ebbinghaus.memo"` 正是统一后包名，无需改动 |
| 全项目 `.debug` 引用 | ✅ 源码仅 `app/build.gradle.kts:39` 一处（已删）；其余均为文档/构建中间产物 |

**唯一需补充的观察（非错误）**：`TEST_READY.md:90` 之外，`design/` 下另有 3 处面向未来操作的排查指引写死了旧包名，
主理人未逐条点名；已一并加注更正（见 §5），历史 logcat 证据未改动。

---

## 8. 硬约束对照

| 约束 | 状态 |
|---|---|
| `./gradlew test` 全绿（316 条/变体），不删改断言 | ✅ 316/变体，0 失败，仅改 1 行构建配置，未动任何测试 |
| 零新增依赖 | ✅ 未改 `dependencies {}` |
| `AndroidManifest` 零新增权限 | ✅ 源码 manifest 未改；自动注入权限名随包名变化，非新增 |
| `compileSdk`/`targetSdk` = 35 | ✅ 未动 |
| 不做 Room schema 变更 | ✅ 未动 |
| 最小变更 | ✅ 代码仅删 1 行 + 注释；文档按需更正 |
| 不启动模拟器 | ✅ 未尝试（本环境无设备） |
