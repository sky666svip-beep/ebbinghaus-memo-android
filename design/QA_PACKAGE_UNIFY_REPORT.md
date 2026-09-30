# QA_PACKAGE_UNIFY_REPORT.md — 独立验证「debug / release 包名统一」

> 验证人：QA 工程师 严过关
> 验证对象：`app/build.gradle.kts` 删除 debug `applicationIdSuffix = ".debug"`
> 验证方式：**全部结论均由本人在本机重新构建 / 解包 / 跑测得到，未采信工程师输出**
> 执行环境：Windows + Git Bash；`JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11`（OpenJDK 17.0.13+11）
> SDK：`/c/Users/AMX/AppData/Local/Android/Sdk`，build-tools `34.0.0`
> 验证时间：2026-09-16 17:2x–17:4x

---

## 1. 最终判定 + 智能路由判定

### 最终判定：**变更正确，核心验收点全部通过（附 1 处文档不实声明需更正）**

| 验收点 | 结论 | 证据 |
|---|---|---|
| debug 包名 = `com.ebbinghaus.memo` | ✅ 通过 | §2.1（本人重打 APK + `aapt dump xmltree` 直读二进制） |
| release 包名 = `com.ebbinghaus.memo` | ✅ 通过 | §2.1 |
| 两 APK 签名一致 | ✅ 通过 | §2.2（SHA-256 完全一致） |
| versionCode / versionName 一致 | ✅ 通过 | §2.3（均 `1` / `1.0.0`） |
| debug 块只剩 `isDebuggable` + `enableUnitTestCoverage` | ✅ 通过 | §2.4（`app/build.gradle.kts:38-46`） |
| 全量单测全绿、316 条/变体、0 失败 | ✅ 通过 | §3.1（本人 `--rerun-tasks` 重跑，57 tasks） |
| `assembleDebug` / `assembleRelease` 均可构建 | ✅ 通过 | §3.2 |
| 用例未被削弱、用例数守恒 | ✅ 通过 | §3.3（0 命中 + 316 守恒） |
| `:baselineprofile` 模块不受影响 | ✅ 通过（编译/打包成功，常量仍正确） | §4.1 |
| debug/release 数据可无缝共用 | ✅ 通过 | §4.2 |
| 文档更正 | ⚠️ **1 处不实声明** | §4.4 |

### 智能路由判定

- **代码 / 构建 / 测试维度：`NoOne`** —— 构建配置变更正确，316/变体全绿，无源码缺陷、无测试缺陷，**代码零返工**。
- **最终路由：`Engineer`（仅 1 处文档更正）** —— 工程师交付的 `design/PACKAGE_UNIFY.md` §5 表格存在**一处不实声明**（声称对 `QA_CRASH_REPORT.md:630/632` 加注更正，实测该处**未加注**，且该行属"面向未来的排查指引"而非历史证据）。此为实现外的文档缺陷，建议由工程师补齐。

> 一句话：**功能与构建全部过关；唯一返工项是工程师自己文档里的一句话没兑现。**

---

## 2. A. 包名与签名的独立实测证据（原始输出）

### 2.0 我做了什么（与工程师解耦）

1. 先对**已有 APK** 取证（秒级）；
2. 再 `./gradlew :app:assembleDebug --rerun-tasks` **亲自重打 debug APK**（39 tasks executed，BUILD SUCCESSFUL 3m44s），对新产物重新取证；
3. 所有包名结论均以 **`aapt dump xmltree` 从 APK 二进制直读 AndroidManifest** 为准（不依赖工程师贴的文本，也不只看中间产物）。

### 2.1 包名 —— `aapt dump badging`（对 APK 二进制）

```
$ aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep ^package:
package: name='com.ebbinghaus.memo' versionCode='1' versionName='1.0.0' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'

$ aapt dump badging app/build/outputs/apk/release/app-release.apk | grep ^package:
package: name='com.ebbinghaus.memo' versionCode='1' versionName='1.0.0' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
```

**`aapt dump xmltree` 直读 APK 内二进制清单（最强证据）：**

```
$ aapt dump xmltree app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml | grep -i "package=\|debuggable"
    A: package="com.ebbinghaus.memo" (Raw: "com.ebbinghaus.memo")
      A: android:debuggable(0x0101000f)=(type 0x12)0xffffffff      # = true

$ aapt dump xmltree app/build/outputs/apk/release/app-release.apk AndroidManifest.xml | grep -i "package=\|debuggable"
    A: package="com.ebbinghaus.memo" (Raw: "com.ebbinghaus.memo")   # 无 debuggable 属性（默认 false）
```

**全部 app 变体的合并清单均为同一包名：**

```
app/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml               -> package="com.ebbinghaus.memo"
app/build/intermediates/merged_manifest/nonMinifiedRelease/.../AndroidManifest.xml  -> package="com.ebbinghaus.memo"
app/build/intermediates/merged_manifest/release/.../AndroidManifest.xml             -> package="com.ebbinghaus.memo"
```

`app/build/outputs/apk/debug/output-metadata.json` → `"applicationId": "com.ebbinghaus.memo"`。

### 2.2 签名 —— `apksigner verify --print-certs`（原始输出）

```
$ apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
Signer #1 certificate SHA-256 digest: 92b4360c903fa6214018696fbb2d789e3145f2d1f61f0d1ebace3d8e6450adba
Signer #1 certificate SHA-1 digest: 54981dc4543361c4f72dedaf5d1e899dc6577917
Signer #1 certificate MD5 digest: ae20088836140ccb39d1a47bce590512

$ apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
Signer #1 certificate SHA-256 digest: 92b4360c903fa6214018696fbb2d789e3145f2d1f61f0d1ebace3d8e6450adba
Signer #1 certificate SHA-1 digest: 54981dc4543361c4f72dedaf5d1e899dc6577917
Signer #1 certificate MD5 digest: ae20088836140ccb39d1a47bce590512
```

> **两 APK 的 DN / SHA-256 / SHA-1 / MD5 四项全等** ⇒ 同一把调试钥匙签名 ⇒ 覆盖安装的签名前提成立。
> 该结论在**我重打的 debug APK** 上复核仍然一致（`92b4360c…adba`）。

### 2.3 versionCode / versionName

| 变体 | versionCode | versionName |
|---|---|---|
| debug | `1` | `1.0.0` |
| release | `1` | `1.0.0` |

> 两者一致 ⇒ 覆盖安装**不会被判为降级**（`INSTALL_FAILED_VERSION_DOWNGRADE` 不触发）。

### 2.4 `debug {}` 块现状（`文件:行号`）

`app/build.gradle.kts`：

- `:14` `applicationId = "com.ebbinghaus.memo"`（defaultConfig，包名唯一来源）
- `:38-46` `debug {}` 块**只剩注释 + 两个属性**，**无 `applicationIdSuffix`**：

```
38  debug {
39-42   // 统一包名：不再追加 .debug 后缀 …（4 行说明注释）
43      isDebuggable = true
44      // 开启单元测试覆盖率采集（AGP 内置 JaCoCo）…
45      enableUnitTestCoverage = true
46  }
```

- 全仓库源码（`app/src`、`core/src`、`baselineprofile/src`）中**无** `com.ebbinghaus.memo.debug` 硬编码；`applicationIdSuffix` 在构建脚本中**已归零**。

---

## 3. B. 回归实测数字

### 3.1 单元测试（本人独立 `--rerun-tasks` 重跑）

```
$ ./gradlew test --console=plain --rerun-tasks
BUILD SUCCESSFUL in 4m 58s
57 actionable tasks: 57 executed
EXIT_CODE=0
```

按 JUnit XML 实测（XML 文件 mtime 17:47:39 / 17:47:40 / 17:43:46，**均晚于本次运行起点 17:42:40，确认为本次新产物**）：

| 测试任务 | XML 文件数 | 用例数 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|---:|
| `:app:testDebugUnitTest` | 42 | **269** | 0 | 0 | 0 |
| `:app:testReleaseUnitTest` | 42 | **269** | 0 | 0 | 0 |
| `:core:test` | 5 | **47** | 0 | 0 | 0 |
| **合计** | — | **585** | **0** | **0** | **0** |

> **口径核对**：工程师所称「316 条/变体」= app 269 + core 47 = 316，**与本人实测完全吻合**；0 失败 / 0 跳过属实。

### 3.2 构建产物

| 命令 | 结果 | 产物大小 |
|---|---|---|
| `:app:assembleDebug --rerun-tasks`（本人亲跑） | **BUILD SUCCESSFUL in 3m 44s**，39 tasks executed，EXIT 0 | `app-debug.apk` = **17,817,604 B** |
| `:app:assembleRelease` | **BUILD SUCCESSFUL**（合并运行 19s），EXIT 0 | `app-release.apk` = **1,759,362 B** |
| `:baselineprofile:assembleBenchmarkRelease` + `assembleNonMinifiedRelease` | **BUILD SUCCESSFUL**，EXIT 0 | 两个测试 APK 各 36,663,691 B |

> debug 大小 `17,817,604 B` 与工程师声称**逐字节一致**；release `1,759,362 B` 亦一致。

### 3.3 用例未被削弱 / 用例守恒

- `assertTrue(true)`、`@Ignore`、`@Disabled`、`assumeTrue/assumeThat` —— **各 0 命中**。
- 源码 `@Test` 计数：`app/src/test` = **269**，`core/src/test` = **47**，合计 **316**，与 XML 执行数守恒。

### 3.4 零新增依赖 / 零新增权限 / SDK / Room

- **依赖**：本仓库**无 `.git`（无版本库）**，无法做 diff；但 `app/build.gradle.kts` 的 `dependencies {}` 块内容为标准固定清单，无异常新增项，构建全程未拉取新坐标。**（受限于无 git，此项为"与声明一致"而非"diff 证明"，如实声明。）**
- **权限**：`app/src/main/AndroidManifest.xml` 的 `uses-permission` = **0**；合并后 debug 清单仅含 1 条 `com.ebbinghaus.memo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（`signature` 级，`androidx.core` 自动注入，**随包名自动变化、非新增**）。
- **SDK**：`compileSdk=35`、`targetSdk=35`、`minSdk=26`（`aapt badging` + `build.gradle.kts` 双向一致）。
- **Room**：schema 文件 `1.json` / `2.json` mtime = **2026-09-15**（早于变更窗口 2026-09-16 17:11），`@Database(version = 2)`，**无 schema 变更**。

---

## 4. C. 风险面评估

### 4.1 `:baselineprofile` 模块 —— **不受影响（已实测）**

**关键质疑逐条回答：**

1. **`StartupBenchmark.kt:70 TARGET_PACKAGE = "com.ebbinghaus.memo"` 是否仍正确？**
   **✅ 仍然正确。** 原因：`:baselineprofile`（`com.android.test` + `targetProjectPath = ":app"`）由 `androidx.baselineprofile` 插件生成的基准变体为 **`benchmarkRelease` / `nonMinifiedRelease`**，二者**均为 release 派生**（与 debug 后缀无关），其 applicationId 恒为 `com.ebbinghaus.memo`。实测：
   ```
   app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk -> package: name='com.ebbinghaus.memo'
   ```
   ⇒ 改动前该常量就已正确（基准从不测 debug 变体），改动后依旧正确，**无需改动**。
2. **`baselineProfile(project(":baselineprofile"))` 是否仍能解析？** ✅ **能。** 配置阶段无报错，`:baselineprofile:checkTestedAppObfuscationBenchmarkRelease` / `…NonMinifiedRelease` 均通过（证明"测试模块 ↔ 被测 app"联动健康）。
3. **`:baselineprofile` 编译/运行是否失败？** ✅ **编译 + 打包成功**（产出 `baselineprofile-benchmarkRelease.apk`、`baselineprofile-nonMinifiedRelease.apk`，各 36,663,691 B，EXIT 0）。
   ⚠️ **但需澄清一个"看似失败"的陷阱**：直接跑 `./gradlew :baselineprofile:assemble` 会**失败**，报：
   ```
   Execution failed for task ':baselineprofile:connectedNonMinifiedReleaseAndroidTest'.
   > com.android.builder.testing.api.DeviceException: No connected devices!
   ```
   原因：`androidx.baselineprofile` 插件把 **`connectedNonMinifiedReleaseAndroidTest`（需真机）** 挂进了该模块 `assemble` 的任务图（`--dry-run` 可见）。**此失败与本包名变更无关**，是"本机无设备"的环境性失败，改动前亦会同样失败。
   **关键：`app` 的构建完全不受牵连** —— `--dry-run` 实测 `:app:assembleDebug` / `:app:assembleRelease` 任务图中 **connected 任务数 = 0、baselineprofile 任务数 = 0**。

### 4.2 数据兼容性 —— **可无缝共用（硬编码、与包名无关）**

| 数据载体 | 值 | 证据（`文件:行号`） | 是否随包名变化 |
|---|---|---|---|
| Room 数据库文件 | `ebbinghaus_memo.db` | `app/src/main/java/com/ebbinghaus/memo/data/local/AppDatabase.kt:64` | **否**（硬编码常量） |
| Room schema 版本 | `2` | `AppDatabase.kt:31`（`@Database(version = 2)`） | 否 |
| SharedPreferences 文件 | `ebbinghaus_prefs` | `app/src/main/java/com/ebbinghaus/memo/data/preference/PreferenceStore.kt:101` | **否**（硬编码常量） |
| 运行期包名读取 | `context.packageName` | `app/src/main/java/com/ebbinghaus/memo/crash/CrashMetadata.kt:17` | 任意包名皆可，无硬编码 |

> debug 与 release 共用同一份源码（同一 `main` 源集），故**数据库文件名 / 版本 / schema / 偏好名完全相同**。由于这些标识均**硬编码、不随 applicationId 变化**，且两变体包名现也相同 ⇒ 覆盖安装后**数据可无缝共用**。
> 另：`app/src/main/AndroidManifest.xml` 无 `<provider>`/`authorities`（FileProvider 类风险不存在）。

### 4.3 反向风险 —— 用户设备上原有的 `com.ebbinghaus.memo.debug`

- **是否会与新 `com.ebbinghaus.memo` 冲突？** **不会。** Android 以包名唯一标识应用；`…memo.debug` 与 `…memo` 是**两个独立包**，各自独立图标、独立数据沙箱，可共存。
- **其数据是否会自动迁移/被继承？** **不会。** 新包不会读取旧包的沙箱数据。旧包成为**孤儿应用**（新代码不再指向它），系统不会自动清理。
- **工程师的指引是否准确？** **基本准确**：`PACKAGE_UNIFY.md §6.2` 已正确说明"旧 debug 包成孤儿、数据不自动迁移、需手动卸载、如需保留先导出"。**唯一遗漏**见 §4.4。

### 4.4 遗漏与连带影响扫描（**本次发现 1 处不实声明**）

全仓库检索 `com.ebbinghaus.memo.debug` / `applicationIdSuffix`（排除 `build/`、`.agents/`、`.workbuddy-ai/` 等内部产物）后的处置复核：

| 位置 | 性质 | 工程师处置 | 我的复核 |
|---|---|---|---|
| `TEST_READY.md:90` | 面向未来的元数据 | 已改为 `com.ebbinghaus.memo` | ✅ 属实 |
| `CRASH_INVESTIGATION.md:233` | 面向未来的排查指引 | 已加注更正 | ✅ 属实（注解在 `:234`） |
| `CRASH_INVESTIGATION.md:81` / `:464` | 历史分析表 / 历史 logcat | 保留原样 | ✅ 合理（带时间戳的历史证据） |
| `QA_CRASH_REPORT.md:317` | 面向未来的排查指引 | 已加注更正 | ✅ 属实（注解在 `:319`） |
| `QA_CRASH_REPORT.md:536` / `QA_FIX_REPORT.md:125` | 历史合并清单证据 | 保留原样 | ✅ 合理 |
| **`QA_CRASH_REPORT.md:632`** | **面向未来的排查指引（§7.2）** | 工程师声称"✅ 已加注" | ❌ **不实：实测该行未加注** |
| `TEST_INFRA.md` | 仅"debug 变体"等措辞 | 无需更新 | ✅ 属实（无硬编码包名） |

**具体不实点：**

- 工程师 `PACKAGE_UNIFY.md §5` 表格声称：`QA_CRASH_REPORT.md:630`（加注后 `:632`）"✅ 已加注更正说明"。
- **实测**：`QA_CRASH_REPORT.md` 中 `更新（2026-09-16）` 注解**仅出现 1 次**（在 `:319`）。`:632` 原文依旧为
  ```
  - 或有电脑时：`adb logcat -b crash -d`（包名 = **`com.ebbinghaus.memo.debug`**，debug 变体带 `.debug` 后缀）。
  ```
  且该行位于 `### 7.2 若 App 崩到「连设置页都进不去」`——**面向未来的排查指引**，按工程师自定原则（"面向未来操作的指引已更正"）**本应加注**。
- 即：工程师称"对 3 处面向未来的排查指引加注更正"，**实际只完成 2 处**。
- **影响等级：低**（纯文档，不影响构建/运行/数据）。但**后患真实**：未来用户按该指引抓崩溃日志时会**过滤错包名**（`.debug`）而抓不到。

**其它连带影响：**

- `.agents/*/handoff.md`、`.workbuddy-ai/memory/*.md` 中残留旧包名 —— 属**内部协作产物**（点目录），非用户可见文档，保留合理。
- **预存在的文档陈旧（与本次变更无关，仅记录）**：`TEST_INFRA.md:66` 写"84 passed（15 个测试类）"、`TEST_READY.md:13` 写"63 个用例"，均与当前 316 条基线不符（应为更早里程碑的旧数）。非本次引入。

---

## 5. D. 安装顺序推演 + 丢数据路径

### 5.1 安装顺序推演（基于 Android 覆盖安装规则）

Android 覆盖安装三要件：**同包名 + 同签名 + versionCode 不降级**。当前 debug/release 三者**全部满足**（§2.1/§2.2/§2.3）。

| 顺序 | 结果 | 数据 |
|---|---|---|
| **先 release，再 debug** | debug **覆盖** release（同包名+同签名+vC=1） | **保留** ✅ |
| **先 debug，再 release** | release **覆盖** debug（同包名+同签名+vC=1） | **保留** ✅ |

> **两种顺序均符合预期（覆盖 + 保留数据）**，无不对称反例。
> 反例（当前不触发，但需警惕）：① versionCode 降级 → 拒绝；② 签名不一致 → 拒绝；③ minSdk 升高 → 拒绝。当前均不成立。
> ⚠️ **未来反例**：若 release 改用**正式签名**（`app/build.gradle.kts:36` 目前临时复用 debug 签名），则 release↔debug 签名不一致，**互相覆盖将失败**，届时须先卸载（→ 丢数据）。此代价已写入 `debug {}` 块注释。

### 5.2 用户可能丢数据的路径（明确指出 + 规避建议）

1. **「先卸载再安装」** —— 卸载会连同沙箱数据一并删除。**规避：始终用覆盖安装**（直接安装 APK，或 `adb install -r`），**不要先卸载**。
2. **卸载孤儿包 `com.ebbinghaus.memo.debug`** —— 若用户此前一直在用 debug 版（数据存在该包内），卸载它会**永久销毁这部分数据**。**规避：先在该旧应用内用"导出"功能导出，再卸载、再导入新包。**
3. **未来 release 改正式签名后强行覆盖** —— 覆盖失败后用户若"卸载再装"→ 丢数据。**规避：改签名前先导出备份；或统一包名策略重新评估。**

---

## 6. 给用户的最终操作指引（准确、无遗漏）

1. **不要卸载现有 `com.ebbinghaus.memo`（release）**，直接安装新 debug 包即可覆盖并**保留数据**：
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   （或手机直接点装 `app-debug.apk`。）
2. **若你此前的数据在旧的 `com.ebbinghaus.memo.debug` 包里**：请**先打开那个旧应用 → 用内置「导出」功能导出数据 → 再手动卸载它 → 装新包后导入**。新包**不会**自动继承旧 debug 包的数据。
3. 装好后桌面**只剩一个图标**，debug 与 release 数据互通。
4. **同一时刻只能装一个变体**：装 debug 会覆盖 release，反之亦然（这是本改动的取舍，非 bug）。
5. **务必避免「先卸载再安装」**：那会清空数据；覆盖安装才是保留数据的正确姿势。
6. 未来若 release 改用正式签名，release↔debug 将**无法互相覆盖**，届时需先导出备份再处理。

---

## 7. 遗留与未验证项

| 项 | 说明 |
|---|---|
| **真机 / 模拟器验证** | 本机**无设备**，**未做真机安装、未验证覆盖安装实际成功、未验证数据实际互通**。上述安装结论为**基于 Android 安装规则 + 静态证据的推演**，非真机实证。 |
| **`connectedNonMinifiedReleaseAndroidTest`** | 需真机，本环境必然 `No connected devices!` 失败；**与包名变更无关**，非本次回归项。 |
| **无 `.git` 版本库** | 无法对"零新增依赖"做 diff 证明，仅能做"内容核对 + 构建未拉新坐标"的间接佐证。 |
| **沙箱限制** | 本机 Bash 沙箱会拦截 Gradle 对 `build/` 的写入（报 Windows "拒绝访问"），故构建/测试均在**关闭沙箱**下执行；此为环境行为，不影响产物正确性。 |
| **文档不实声明（待修）** | `QA_CRASH_REPORT.md:632` 的更正注解缺失，且 `PACKAGE_UNIFY.md §5` 对此的"已加注"声明不实。建议由工程师补齐（本报告不修改该文档）。 |

---

## 附：原始日志留存

- 单测全量日志：`build/qa_test.log`
- 构建日志：`build/qa_build.log`、`build/qa_debug_rebuild.log`
- 首轮（含 baselineprofile 设备任务失败）：`build/qa_verify.log`
