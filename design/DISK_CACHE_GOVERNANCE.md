# 磁盘缓存治理方案（C 盘构建缓存排查）

> 文档类型：**只读排查结论 + 治理方案**（本次未执行任何删除 / 移动 / 配置修改）
> 排查对象：`D:\Projects\androidapk`（EbbinghausMemo）及其构建环境
> 排查时间：2026-09-18（实测证据均标注时间戳）
> 排查方式：Git Bash + PowerShell 只读命令；未运行任何构建

---

## 0. 一句话结论

**是的，C 盘仍然会被写入。** 尽管 `GRADLE_USER_HOME` 已指向 `D:\Agent\Android\.gradle`，但实测发现 **同一个项目在同一天（2026-09-16）既用过 D 盘 Gradle Home（09:05–16:18），又用过 C 盘 Gradle Home（16:07 / 16:25 / 17:42）**。这说明 **并非所有 Gradle 调用都继承了该环境变量**——凡是"启动时环境里没有这个变量"的进程（IDE、旧终端、服务、Agent 子会话等），Gradle 都会回落到默认的 `C:\Users\AMX\.gradle`。因此 C 盘那 2.3G **不是纯粹的历史残留，而是"仍有可能被继续写入"**。

真正根治需要**双保险**：① 把 `GRADLE_USER_HOME` 提升为**系统级(HKLM)**并重启相关进程；② 清空 C 盘旧 Home 后建立**目录联接(Junction)** `C:\Users\AMX\.gradle → D:\Agent\Android\.gradle`，从物理上杜绝 C 盘写入。

---

## 1. 诊断结论

### 1.1 环境事实（实测）

| 项 | 实测值 | 来源 |
|---|---|---|
| C 盘 | 130G，已用 123G，**剩 7.8G（95%）** | `df -h /c` |
| D 盘 | 790G，已用 516G，剩 275G | `df -h /d` |
| 物理内存 | **16 GB**（16,979,800,064 字节） | `Win32_ComputerSystem` |
| CPU | Intel i7-11800H，**16 逻辑核** | `Win32_Processor` / `NUMBER_OF_PROCESSORS` |
| OS | Windows 10.0.26200（Win11 内核） | `cmd /c ver` |
| JDK | `C:\Users\AMX\.jdks\jdk-17.0.13+11` | `$JAVA_HOME` |

### 1.2 环境变量（关键差异）

| 变量 | 值 | 作用域 | 备注 |
|---|---|---|---|
| `GRADLE_USER_HOME` | `D:\Agent\Android\.gradle` | **仅 HKCU（用户级）** | ⚠️ **HKLM 系统级没有** |
| `ANDROID_HOME` | `D:\Agent\Android\Sdk` | HKCU + **HKLM** | 双级都有 |
| `ANDROID_SDK_ROOT` | `D:\Agent\Android\Sdk` | HKCU + **HKLM** | 双级都有 |
| `JAVA_HOME` | `C:\Users\AMX\.jdks\jdk-17.0.13+11` | HKCU + **HKLM** | 双级都有 |
| `TEMP` / `TMP` | `%USERPROFILE%\AppData\Local\Temp`（即 C 盘） | HKCU | Kotlin daemon 日志落此处 |
| `ANDROID_AVD_HOME` | **未设置** | — | AVD 因此默认落 `C:\Users\AMX\.android` |
| `ANDROID_USER_HOME` | **未设置** | — | 同上 |
| `ANDROID_EMULATOR_*` | 未设置 | — | — |

> 证据：
> `reg query "HKCU\Environment"` → 含 `GRADLE_USER_HOME    REG_SZ    D:\Agent\Android\.gradle`
> `reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment"` → **只有** `ANDROID_SDK_ROOT`、`ANDROID_HOME`、`JAVA_HOME`，**没有 `GRADLE_USER_HOME`**。

### 1.3 「是否真的还在往 C 盘写」—— 明确结论 + 证据

**结论：会。** 证据是 Gradle daemon 日志里的 `daemonRegistryDir` 与 `currentDir`：

| 日志文件 | daemonRegistryDir（= 实际使用的 Gradle Home） | 构建项目 | 时间 |
|---|---|---|---|
| `C:\Users\AMX\.gradle\daemon\8.10.2\daemon-12656.out.log` | **C:\Users\AMX\.gradle\daemon** | D:\Projects\androidapk | 2026-09-16 **16:07** |
| `C:\Users\AMX\.gradle\daemon\8.10.2\daemon-11744.out.log` | **C:\Users\AMX\.gradle\daemon** | D:\Projects\androidapk | 2026-09-16 **16:25** |
| `C:\Users\AMX\.gradle\daemon\8.10.2\daemon-18612.out.log` | **C:\Users\AMX\.gradle\daemon** | D:\Projects\androidapk | 2026-09-16 **17:42** |
| `D:\Agent\Android\.gradle\daemon\8.10.2\daemon-12956.out.log` | **D:\Agent\Android\.gradle\daemon** | D:\Projects\androidapk | 2026-09-16 **09:05** |

- C 盘 Home 最后一次被写入：**2026-09-16 17:47**（`caches/build-cache-1/*`、`kotlin-profile/*.profile`、`daemon/registry.bin`）。
- D 盘 Home 最后一次被写入：**2026-09-16 16:18**。
- **同一项目、同一天、两个 Home 交替使用** → 环境变量对部分进程不生效。
- 此刻（排查时）无任何 `java`/`gradle`/`kotlin` 进程在运行（`tasklist` 为空匹配）→ **当前没有实时写入**，但**下一次由"缺变量"的进程触发的构建仍会写 C**。

**最可能的原因（诚实标注为推断）**：`GRADLE_USER_HOME` 只写入了用户级注册表，进程在**启动时**读取环境变量。凡是①在变量写入之前就已启动的 IDE/终端/Agent 宿主进程，②由服务或非交互上下文拉起的子进程，其环境里可能根本没有这个变量，Gradle 便回落到 `C:\Users\AMX\.gradle`。我**无法从现有日志确定具体是哪一个进程**（日志不记录客户端环境），故只能给出此推断 + 物理级兜底方案（见 B1-①）。

### 1.4 另一处"仍在指 C"的配置：`local.properties`

项目 `D:\Projects\androidapk\local.properties`：
```properties
sdk.dir=C\:\\Users\\AMX\\AppData\\Local\\Android\\Sdk
```
**项目级 `local.properties` 的 `sdk.dir` 优先级高于 `ANDROID_HOME`/`ANDROID_SDK_ROOT`**，所以**构建实际使用的是 C 盘 SDK**（`C:\Users\AMX\AppData\Local\Android\Sdk`，1.5G）。这是 C 盘 SDK 不能直接删除的**根本原因**（详见 B2-⑪）。同时 D 盘 SDK **缺少 `platforms/`、`build-tools/`、`cmdline-tools/`**，目前也无法独立承担构建。

### 1.5 关于「自动放置回收站」

- **Windows 存储感知（Storage Sense）已开启**：`HKCU\...\StorageSense\Parameters\StoragePolicy`
  `01=1`（总开关开）、`02=1`（清理未使用的临时文件）、`04=1`（清理回收站）、`08=1`（清理下载文件夹）、`256=30`（回收站阈值 30 天）、`128=30`（下载阈值 30 天），且有 `StoragePoliciesLastTrigger`。
- 相关计划任务存在：`StorageSense`、`SilentCleanup`（系统内置磁盘清理）、`StartComponentCleanup` 等（`Get-ScheduledTask`）。
- **未发现任何第三方清理工具**（Program Files / Program Files (x86) / 启动项里都没有 CCleaner / Wise / Glary / Dism++ / 360 / 火绒 之类）。
- 回收站当前几乎为空：`C:\$Recycle.Bin` 649K、`D:\$Recycle.Bin` 9.2M。
- 另注意到：当前 Agent 宿主环境存在 `CODEBUDDY_SAFE_DELETE_BULK_STATE_DIR` / `CODEBUDDY_SAFE_DELETE_REPORT_PATH`（指向 Temp），说明**工具链的"安全删除"会把删除动作路由到回收站**，而非永久删除。

> **诚实结论**：我**没有**找到"构建过程中自动把 Gradle 构建缓存搬进回收站"的机制。我能证实的只有：(a) 存储感知会自动清理 %TEMP%、下载目录与 30 天以上的回收站内容；(b) 工具链的"安全删除"会进回收站。如果用户观察到的"自动进回收站"来自某个具体软件/行为，**在注册表、计划任务、启动项中都未能定位到**，如实说明。

---

## 2. A. 仍在写 C 盘的来源清单（逐项 + 证据 + 是否可迁移）

### 2.1 `C:\Users\AMX\.gradle` —— 2.4G ⚠️ 核心问题

| 子目录 | 体积 | 内容 | 最后写入 |
|---|---|---|---|
| `caches/8.10.2/transforms` | 1.4G | AGP 产物变换缓存（可重建） | 09-16 17:47 |
| `caches/8.10.2/generated-gradle-jars` | 172M | Gradle 生成 jar | 09-16 |
| `caches/8.10.2/{javaCompile,kotlin-dsl,...}` | ~45M | 编译中间缓存 | 09-16 |
| `caches/modules-2/files-2.1` | 457M | 依赖 jar/arr 缓存（可重下） | 09-16 17:47 |
| `caches/build-cache-1` | 210M | 本地构建缓存（可重建） | 09-16 17:47 |
| `wrapper/dists/gradle-8.10.2-bin` | 146M | Gradle 8.10.2 发行包 | 09-15 |
| `daemon/8.10.2/*.out.log` | 2.4M | daemon 日志（证据来源） | 09-16 17:47 |
| `.tmp`（70 个 worker classpath） | 1.3M | 临时文件 | 09-16 17:47 |
| `kotlin-profile` | 348K | Kotlin 编译 profile | 09-16 17:47 |
| `native` | 1.5M | native 集成 | 09-15 |

- **是否可迁移**：可整体迁移/清空（见 B2）。D 盘 Home 已有等价缓存（1.8G）。
- **为什么还在写**：见 1.3 —— 部分进程未继承 `GRADLE_USER_HOME`。

### 2.2 `C:\Users\AMX\.android` —— 2.6G ⚠️ 第二大户

```
2.6G  avd/Android16_API36.avd/snapshots/default_boot   ← 快照（Quick Boot 镜像）
 69M  avd/Android16_API36.avd/cache.img
 18M  avd/Android16_API36.avd/encryptionkey.img
6.3M  avd/Android16_API36.avd/userdata-qemu.img
2.1M  avd/Android16_API36.avd/initrd
6.0M  cache/                       ← SDK 仓库 xml 缓存
 1.7K adbkey / adbkey.pub / debug.keystore / *.ini
```

- **由哪个变量控制**：AVD 位置默认由 `ANDROID_AVD_HOME` 决定；若未设，则由 `ANDROID_USER_HOME` 决定；若两者都未设，**回落到 `%USERPROFILE%\.android`**（即 C 盘）。实测**两个变量都没设** → 所以 AVD 落在 C 盘。
- **AVD 里具体是什么**：`avd/Android16_API36.avd` 2.6G，其中 **`snapshots/default_boot` 独占 2.6G**——这是模拟器的 Quick Boot 快照，**不是系统镜像**（系统镜像在 `D:\Agent\Android\Sdk\system-images\android-36\google_apis\x86_64`，4.3G）。
- **关键佐证**：`avd/Android16_API36.avd/emu-launch-params.txt` 显示最近一次启动用的是
  `D:\Agent\Android\Sdk\emulator\emulator.exe -avd Android16_API36 -no-snapshot ...`，
  即 **以 `-no-snapshot` 启动**——也就是说这个 2.6G 快照**当前根本没被使用**。
- **能否整体迁到 D**：可以。做法见 B1-②。风险：迁移期间不能有模拟器在跑；`.ini` 里的 `path` 需同步改；若迁移不当会丢快照（可接受）。

### 2.3 `C:\Users\AMX\AppData\Local\Android\Sdk` —— 1.5G ⚠️ 不是残留，是"正在用"

| 子目录 | 体积 | 说明 |
|---|---|---|
| `emulator` | 1.1G | 模拟器 37.1.11（**与 D 盘 `emulator` 同版本，重复**） |
| `cmdline-tools` | 147M | sdkmanager 等 |
| `build-tools/34.0.0` | 137M | **构建必需** |
| `platforms/android-35` | 127M | **构建必需（compileSdk=35）** |
| `platform-tools` | 17M | adb（D 盘也有一份 17M） |
| `.temp` / `.knownPackages` / `licenses` | 极小 | 元数据 |

- **是不是 Android Studio 的缓存**：**不是**。本机**未安装 Android Studio**（`AppData\Local\Google\AndroidStudio*`、`AppData\Roaming\Google\AndroidStudio*`、`Program Files\Android`、`studio64.exe` 均不存在；`C:\Program Files\Google` 下只有 Chrome）。这是一个**独立的 Android SDK 安装**，由项目内 `setup_sdk.ps1`（默认 `-sdkRoot` 就是该 C 盘路径）下载引导。
- **能否清理/迁移**：**不能直接删**——`local.properties` 指向它，`build-tools/34.0.0` 与 `platforms/android-35` 是构建必需。**正确做法是"迁移到 D + 改 local.properties"**，见 B1-⑤。

### 2.4 `%TEMP%`（`C:\Users\AMX\AppData\Local\Temp`）—— 130M

- 实测含：`Tencent` 22M、`jieba.cache` 8.9M、多个 `*.tmp`（单个 1~7M）、`node-compile-cache` 3M、`OpenSSLLibrary` 1.6M、`MumuImageCache` 1.4M、`codebuddy-AMX` 1.3M，以及 **`kotlin-daemon.*.log`（每个 ~1.1M，多个）**。
- **Gradle/Kotlin daemon 是否在此产生大量临时文件**：Gradle daemon 的**日志不在这里**（在 `GRADLE_USER_HOME/daemon`，现已随变量落到 D）。但 **Kotlin daemon 的日志默认写系统 TEMP**——实测 `Temp\kotlin-daemon.2026-09-16.*.log` 就是它。
- **构建期间会不会暴涨**：会**有增长但量级不大**（每次构建新增若干 MB 的 daemon 日志/临时 dll/tmp），且**会被存储感知（`02=1`）自动清理**。当前 130M 属正常水位。

### 2.5 项目本地 `.gradle/`（35M）与各 `build/` 目录 —— 确认无 C 盘指向

- `D:\Projects\androidapk\.gradle` = 35M；`app/build` = 512M；`baselineprofile/build` = 284M；`core/build` = 1.6M；根 `build` = 8.7M —— **全部在 D 盘**，符合预期。
- 已检查项目内**无任何指向 C 盘的构建产物路径**；`gradle/wrapper/gradle-wrapper.properties` 使用 `distributionBase=GRADLE_USER_HOME`（即跟随变量，无硬编码 C 盘）。
- 结论：**这几项不是问题**（仅体积可优化）。

### 2.6 其他可能的 C 盘写入点（我自行扫描的结果）

| 路径 | 体积 | 结论 |
|---|---|---|
| `C:\Users\AMX\.konan` | **不存在** | 无 Kotlin/Native 编译缓存 |
| `C:\Users\AMX\.kotlin` | **不存在** | 无 |
| `C:\Users\AMX\.m2` | 145M | Maven 本地库。项目 `settings.gradle.kts` **未使用 `mavenLocal()`**，**与本项目无关**（可能属其他 Maven/Java 项目） |
| `C:\Users\AMX\.cache` | 20M | 通用缓存（非 Gradle） |
| `C:\Users\AMX\AppData\Local\Google\Chrome` | 594M | Chrome 浏览器，**与构建无关** |
| Android Studio `system/` | **不存在** | 本机未装 Android Studio |
| Kotlin daemon 日志 | 见 2.4 | 落 C 盘 TEMP（小） |
| `D:\Agent\Android\Sdk\system-images\android-36\aosp_atd\arm64-v8a` | 1.7G | 在 **D 盘**（用户已确认可删，见 B2-⑮） |

**综上：C 盘与构建相关的"活跃来源"是 `C:\Users\AMX\.gradle`（写）与 `local.properties→C 盘 SDK`（用）；`C:\Users\AMX\.android` 的 2.6G 是快照（当前 `-no-snapshot` 未使用）。**

---

## 3. B1. 配置优化方案（让未来不再落 C / 不再无限膨胀）

> ⚠️ 以下均为**待确认后执行**的方案，本次未做任何改动。

### ① 【最高优先级·根治 C 盘写入】把 `GRADLE_USER_HOME` 提升为系统级 + 目录联接兜底

- **改哪里**：
  1. 把 `GRADLE_USER_HOME=D:\Agent\Android\.gradle` 同时写入**系统级** `HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment`（当前只有用户级）。
  2. **更彻底**：清空 C 盘旧 Home 后建立目录联接，从物理层杜绝：
     ```cmd
     :: 需管理员；先确保无 java/gradle 进程
     rmdir /S /Q "C:\Users\AMX\.gradle"        :: 或先改名备份
     mklink /J "C:\Users\AMX\.gradle" "D:\Agent\Android\.gradle"
     ```
- **预期收益**：无论哪个进程（IDE / 终端 / 服务 / Agent 子会话）拉起 Gradle，C 盘都不会再被写入——**彻底解决 1.3 的问题**。
- **风险**：中。联接需要 C 盘路径不存在才能创建；删除前必须确认无进程占用；`mklink /J` 需管理员权限。**这是本次最推荐、也最需要用户确认的一步。**

### ② 【AVD 迁到 D 盘】

- **改哪里**：
  1. 设置用户级环境变量 `ANDROID_AVD_HOME=D:\Agent\Android\avd`（或统一用 `ANDROID_USER_HOME=D:\Agent\Android`）。
  2. 把 `C:\Users\AMX\.android\avd\Android16_API36.avd` 与 `Android16_API36.ini` **移动**到 `D:\Agent\Android\avd\`。
  3. 编辑 `D:\Agent\Android\avd\Android16_API36.ini` 的 `path=` 指向新位置（`path.rel` 保持不变）。
- **预期收益**：释放 C 盘 **2.6G**，且以后新建 AVD 也落 D。
- **风险**：低~中。迁移时**不能有模拟器在跑**；路径写错会导致 AVD 找不到（可改回）。可选：顺手删掉没在用的 `snapshots/default_boot`（省 2.6G，代价只是下次冷启动变慢）。
- 说明：`.android` 下的 `debug.keystore` / `adbkey` **不要动**（见 B2-⑩）。

### ③ Gradle 本地构建缓存（`build-cache-1`）设置过期策略

- **背景**：`org.gradle.caching=true` 会启用本地构建缓存，**但它本身没有"大小上限"**，默认按 LRU 清理，无天数约束，理论上会持续增长。
- **改哪里**：在 `settings.gradle.kts` 里加（这是 Gradle 官方支持的写法）：
  ```kotlin
  buildCache {
      local {
          removeUnusedEntriesAfterDays = 30   // 30 天未命中即清理
      }
  }
  ```
- **预期收益**：本地构建缓存不再无限膨胀（当前 C 210M / D 90M）。
- **风险**：低。副作用仅是偶尔多一次未命中（重编译），不影响正确性。

### ④ Gradle daemon 数量与存活时间

- **现状**：`daemon-12956` 日志显示 `idleTimeout=10800000`（**3 小时**，即 Gradle 默认值）。
- **评估**：3 小时是默认、**合理**，无需修改。若想更省内存可设 `org.gradle.daemon.idletimeout=3600000`（1 小时），但会牺牲"连续构建更快"的收益。**本机 16G 内存下建议维持默认。**

### ⑤ 【重要】`local.properties` 指向 C 盘 SDK —— 应改指 D

- **改哪里**：先按 2.3 把 C 盘 SDK 的 `build-tools/`、`platforms/`、`cmdline-tools/` 迁到 `D:\Agent\Android\Sdk`，然后把 `local.properties` 改为：
  ```properties
  sdk.dir=D\:\\Agent\\Android\\Sdk
  ```
- **预期收益**：构建真正使用 D 盘 SDK，C 盘 SDK（1.5G）可安全删除；SDK 与 `ANDROID_HOME` 一致，消除"两套 SDK"混乱。
- **风险**：中。必须**先迁移、后改路径、再验证一次构建成功**，最后才删 C 盘 SDK；顺序错会导致构建找不到 SDK。

### ⑥ Kotlin daemon JVM 参数（显式化）

- **现状**：项目 `gradle.properties` **未显式设置** `kotlin.daemon.jvmargs`，Kotlin daemon 使用默认堆。
- **改哪里**：在 `gradle.properties` 增加
  ```properties
  kotlin.daemon.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
  ```
- **预期收益**：Kotlin 编译 daemon 内存可控，减少因默认值偏小导致的反复 GC。
- **风险**：低。若设得过大且与 Gradle daemon 叠加，可能挤占内存（16G 下 2G 安全）。

### ⑦ `org.gradle.jvmargs=-Xmx2048m` 是否偏小

- **本机内存**：**16 GB**（实测）。当前 Gradle daemon 堆上限 2G。
- **评估**：**偏保守**。16G 内存下，AGP + KSP + Compose 编译，2G 容易触发频繁 GC、拖慢构建。
- **建议**：改为 `-Xmx4096m -XX:MaxMetaspaceSize=1g`（Gradle daemon）+ ⑥ 的 Kotlin daemon 2G。两者叠加约 6G，16G 机器可承受（其余给 IDE/浏览器）。
- **风险**：中。若同时开多个 IDE/模拟器，可能内存吃紧。**建议先 3G 试跑，再决定是否上 4G。**

### ⑧ `baselineprofile/build`（284M）纳入清理策略

- **现状**：`baselineprofile` 是 Macrobenchmark 模块，注释明确"**录制强依赖真机/模拟器；本环境无设备**"。
- **建议**：把 `baselineprofile/build` 纳入"定期清理的构建产物"清单（可删，见 B2-⑯）。无需改配置。
- **风险**：无（删除仅需重新构建）。

### ⑨ TEMP 落 C 盘（可选）

- **现状**：用户级 `TEMP/TMP` 指向 C 盘；Kotlin daemon 日志落此处。
- **建议（可选）**：把用户级 `TEMP`/`TMP` 改为 `D:\Temp`，并把 `D:\Temp` 建好。
- **预期收益**：临时文件与 daemon 日志不再落 C。
- **风险**：低~中。极少数安装程序/工具对 TEMP 有路径假设，但常规安全。**可选，优先级低于 ①②⑤。**

---

## 4. B2. 清理清单（**只列，不执行**）

> 体积均为实测；"删除代价"一栏说明删了会付出什么。

| # | 路径 | 体积 | 是否安全删除 | 判定依据（证据） | 建议操作 | 删除代价 |
|---|---|---|---|---|---|---|
| ① | `C:\Users\AMX\.gradle\caches\8.10.2` | 1.6G | ✅ 安全 | AGP 产物变换/编译缓存，可重建；D 盘 Home 已有等价 `caches/8.10.2` 1.1G | 删除 | 下次构建重新生成 transforms，慢几分钟 |
| ② | `C:\Users\AMX\.gradle\caches\modules-2` | 460M | ✅ 安全（需网络） | 依赖 jar 缓存，可重新下载；D 盘已有 398M | 删除 | 下次构建重新下载依赖（依赖网络，慢） |
| ③ | `C:\Users\AMX\.gradle\caches\build-cache-1` | 210M | ✅ 安全 | 本地构建缓存，纯加速用途 | 删除 | 丢失构建复用，重编译 |
| ④ | `C:\Users\AMX\.gradle\wrapper\dists` | 146M | ⚠️ 可删但有代价 | 含 Gradle 8.10.2 发行包（`gradle-wrapper.properties` 的 `distributionUrl` 指向它）；D 盘已有同版本 | 删除 | **下次构建会重新下载 ~146M 的 Gradle 8.10.2**（若走 C Home 才会下到 C） |
| ⑤ | `C:\Users\AMX\.gradle\daemon` + `native` + `.tmp` + `kotlin-profile` | ~5M | ✅ 安全 | 日志/临时/profile，可重建 | 删除 | 无 |
| ⑥ | **`C:\Users\AMX\.gradle` 整体** | **2.4G** | ✅ 安全（清空后可建 Junction） | 全部为可重建缓存；**前提：已确认所有构建走 D Home** | **改名备份 → 删除 → 建 Junction（B1-①）** | 若未建 Junction 且仍有进程走 C，会重新生成 |
| ⑦ | `C:\Users\AMX\.android\avd\...\snapshots\default_boot` | **2.6G** | ✅ 安全 | Quick Boot 快照，**非系统镜像**；`emu-launch-params.txt` 显示以 `-no-snapshot` 启动，**当前未使用** | 删除 | 下次若去掉 `-no-snapshot` 则冷启动变慢（快照可重建） |
| ⑧ | `C:\Users\AMX\.android\avd` 整体 | 2.6G | ✅ 安全（若不需要该 AVD） | AVD 定义+快照；系统镜像在 D 盘 `system-images` | 迁移或删除 | 删则丢失该 AVD（含已保存快照）；系统镜像不丢 |
| ⑨ | `C:\Users\AMX\.android\cache` | 6.0M | ✅ 安全 | SDK 仓库 xml 缓存 | 删除 | 无（会重新拉取元数据） |
| ⑩ | `C:\Users\AMX\.android\debug.keystore` / `adbkey*` | <4K | ❌ **不要删** | debug 签名密钥 + adb 授权密钥 | 保留 | **删了会导致 debug 签名变化**：已装 app 因签名不一致需卸载重装；adb 需重新授权 |
| ⑪ | `C:\Users\AMX\AppData\Local\Android\Sdk` 整体 | 1.5G | ❌ **当前不可删** | `local.properties` 的 `sdk.dir` 指向它，`build-tools/34.0.0` + `platforms/android-35` 是构建必需 | **先迁移到 D（B1-⑤）再删** | 直接删 → 构建找不到 SDK 直接失败 |
| ⑫ | `...\Android\Sdk\emulator` | 1.1G | ✅ 迁移后可删 | 与 `D:\Agent\Android\Sdk\emulator` **同版本 37.1.11**（package.xml 均 revision 37.1.11），且最近一次启动用的是 D 盘 emulator.exe | 删除 C 盘这份 | 无（D 盘已有同版本） |
| ⑬ | `C:\Users\AMX\AppData\Local\Temp\*`（含 kotlin-daemon 日志） | 130M | ✅ 安全 | 临时文件；存储感知（`02=1`）本就会清 | 清空 | 无（正在使用的文件会跳过） |
| ⑭ | `C:\Users\AMX\.m2` | 145M | ⚠️ 与本项目无关 | 项目未用 `mavenLocal()` | **先确认无其他 Maven 项目再用** | 若有其他 Java/Maven 项目，删了需重下 |
| ⑮ | `D:\Agent\Android\Sdk\system-images\android-36\aosp_atd\arm64-v8a` | 1.7G | ✅ 安全删除（**在 D 盘**） | AVD 用的是 `google_apis/x86_64`；本机 x86_64 无硬件加速，arm64 镜像不可用；用户已确认可删 | 删除 | 无（若将来需 arm64 需重下） |
| ⑯ | 项目 `app/build` + `baselineprofile/build` + `core/build` + 根 `build` | 806M | ✅ 安全（**在 D 盘**） | 纯构建产物，可重建 | `./gradlew clean` 或删目录 | 下次重新构建（本环境无设备，baselineprofile 本就无法录制） |
| ⑰ | 项目 `.gradle` | 35M | ✅ 安全（**在 D 盘**） | 项目级 Gradle 状态 | 删除 | 下次构建重建 |
| ⑱ | `C:\$Recycle.Bin` / `D:\$Recycle.Bin` | 649K / 9.2M | ✅ 安全 | 回收站 | 清空 | 无（确认无待恢复文件） |

### 4.1 特别说明：`C:\Users\AMX\.gradle` 整体能否直接删？

**能，但要精确区分两部分：**

- **可删、无代价**：`caches/`（2.3G）、`daemon/`、`native/`、`.tmp/`、`kotlin-profile/`、`notifications/`、`workers/` —— 全是可重建缓存。
- **删了有代价**：`wrapper/dists/gradle-8.10.2-bin`（146M）—— 删了**下次构建会重新下载 Gradle 8.10.2**。不过 D 盘 Home 已有同版本，只要构建走 D Home 就不会重下。
- **结论**：**可以整体清空**（前提：确认构建已走 D Home），最推荐配合 **B1-① 的 Junction** 一起做，一劳永逸。

### 4.2 特别说明：`C:\Users\AMX\.android` 里的 AVD 删了会丢什么？

- **丢**：AVD 的**定义**（`config.ini`、`hardware-qemu.ini`）+ **已保存的 Quick Boot 快照**（2.6G，含设备内已安装的 app/数据状态）。
- **不丢**：**系统镜像**（在 `D:\Agent\Android\Sdk\system-images`，4.3G）—— 可重新创建同名 AVD。
- **建议**：优先**迁移**（保留 AVD），若要省空间再删快照（当前 `-no-snapshot` 未用）。

### 4.3 特别说明：项目 `build/` 目录删了会怎样？

- **只是重新构建**。`app/build`、`baselineprofile/build`、`core/build`、根 `build` 都是产物目录，删除后 `./gradlew assembleDebug` 会重新生成；无源码/配置丢失。**且它们本就在 D 盘，不影响 C 盘。**

---

## 5. 推荐执行顺序

> 分三批，**每批都需用户确认后**再执行（本次一律不执行）。

**第一批（低风险、立即见效，建议先做）**
1. **B2-⑦/⑧ 处理 AVD 快照**（省 **2.6G**）：先确认无模拟器在跑 → 删除或迁移 `snapshots/default_boot`。
2. **B2-①~⑥ 清空 C 盘 `.gradle`**（省 **2.4G**）：先 `gradlew --stop` 停 daemon → 改名备份 → 删除。
3. **B2-⑬ 清空 %TEMP%**（省 **130M**）。

**第二批（需配置改动，收益大）**
4. **B1-① 根治写入**：把 `GRADLE_USER_HOME` 提到系统级(HKLM) + 重启 IDE/终端；随后建立 Junction `C:\Users\AMX\.gradle → D:\Agent\Android\.gradle`。
5. **B1-② AVD 迁 D**：设 `ANDROID_AVD_HOME`，移动 AVD 目录并改 `.ini`。
6. **B1-⑤ 统一 SDK**：把 C 盘 SDK 的 `build-tools/platforms/cmdline-tools` 迁到 D → 改 `local.properties` → **验证一次构建成功** → 再删 C 盘 SDK（省 **1.5G**）。

**第三批（构建参数调优，需评估）**
7. **B1-⑥⑦ 调内存**：`org.gradle.jvmargs` 提到 3~4G + `kotlin.daemon.jvmargs=2G`（先 3G 试跑）。
8. **B1-③ 构建缓存过期策略**：`settings.gradle.kts` 加 `removeUnusedEntriesAfterDays=30`。
9. **B2-⑮ D 盘 arm64 镜像**（省 **1.7G**，在 D 盘）：确认不再需要后删除。
10. **B2-⑯⑰ 项目产物清理**（省 **841M**，在 D 盘）：`gradlew clean`。

**需用户明确确认的高风险项**：B1-①（Junction / 系统级变量）、B1-⑤（改 `local.properties` + 迁 SDK）、B1-⑦（改内存）、B2-⑭（`.m2` 是否属其他项目）。

---

## 6. 风险与不确定项（如实列出）

1. **"哪个进程导致 C 盘写入"未能定位**：daemon 日志不记录客户端环境变量，只能确认"存在不走 D Home 的构建"，无法指名具体进程。**B1-① 的 Junction 是绕过该不确定性的物理兜底。**
2. **"自动放置回收站"机制未能完全证实**：仅确认存储感知开启（清 TEMP/下载/30 天回收站）+ 工具链"安全删除"进回收站；**没有找到"构建时自动把 Gradle 缓存搬进回收站"的直接证据**。若用户所指为某具体软件，注册表/计划任务/启动项中均未发现。
3. **`C:\Users\AMX\.m2`（145M）归属未知**：本项目不使用 `mavenLocal()`，删它可能影响其他 Maven/Java 项目，**需用户确认**。
4. **Junction 方案的前置条件**：需管理员权限；创建前 C 盘 `.gradle` 路径必须不存在；若有进程占用会失败。**操作前务必 `gradlew --stop` 并关闭 IDE。**
5. **内存调整存在权衡**：16G 机器上 Gradle(4G)+Kotlin(2G) 与 IDE/模拟器/浏览器并发可能吃紧，**建议先 3G 试跑观察**。
6. **AVD 迁移必须"先关模拟器"**：否则文件被占用导致迁移不完整。本机 x86_64 无硬件加速，**不启动模拟器**（遵守约束），故迁移只做文件层面。
7. **`local.properties` 改动会直接影响构建**：必须"先迁移 SDK、后改路径、再验证"，顺序错误会导致构建失败。
8. **D 盘 SDK 当前不完整**（缺 `platforms/`、`build-tools/`、`cmdline-tools/`）：在完成 B1-⑤ 前，**构建无法脱离 C 盘 SDK**。
9. **存储感知会持续自动清理** `%TEMP%`、下载目录与 30 天以上回收站内容——属正常行为，但意味着"放进回收站的缓存"会在 30 天后被永久删除（如需保留请勿依赖回收站）。
10. **未排查项**：启动项 `Consoles` 指向 `C:\Windows\Temp\Microsoft.Windows.CapturePick\...\inetpepui.exe`，路径特征可疑，但**与本次构建缓存无关**，建议另行安全审查（此处不下结论）。

---

## 附录：本次使用的关键证据命令（只读）

```bash
df -h /c /d
du -sh /c/Users/AMX/.gradle/* /c/Users/AMX/.android/* /c/Users/AMX/AppData/Local/Android/Sdk/*
find /c/Users/AMX/.gradle -type f -mtime -3 -printf '%TY-%Tm-%Td %TH:%TM  %p\n' | sort -r
head -5 /c/Users/AMX/.gradle/daemon/8.10.2/daemon-18612.out.log   # → daemonRegistryDir=C:\Users\AMX\.gradle\daemon
head -5 /d/Agent/Android/.gradle/daemon/8.10.2/daemon-12956.out.log # → daemonRegistryDir=D:\Agent\Android\.gradle\daemon
reg query "HKCU\Environment" | grep -i gradle
reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" | grep -i gradle
cat /d/Projects/androidapk/local.properties                        # → sdk.dir=C:\...\AppData\Local\Android\Sdk
cat /c/Users/AMX/.android/avd/Android16_API36.avd/emu-launch-params.txt  # → D 盘 emulator.exe ... -no-snapshot
```

```powershell
(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory
Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\StorageSense\Parameters\StoragePolicy'
Get-ScheduledTask | ? { $_.TaskName -match 'clean|storage|disk' } | Select TaskName,State
```

---

*本文件由工程师寇豆码（Alex）基于只读实测产出，未执行任何删除/移动/配置修改。所有"建议操作"均待用户确认后另行执行。*
