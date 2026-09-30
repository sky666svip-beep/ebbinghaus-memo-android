# C 盘清理与 SDK 迁移 —— 执行报告

- **执行人**：工程师 寇豆码
- **执行时间**：2026-09-18 09:38 – 09:46
- **项目**：`D:\Projects\androidapk`
- **授权**：用户在主理人明确列出每项精确路径与风险后，选择「全部做（含迁 SDK）」
- **结论**：**3 项空间释放全部成功**，**SDK 迁移成功并已通过构建验证**；**仅 1 项（HKLM 系统级变量）因权限不足未完成**，需用户以管理员身份执行。

---

## 1. 执行前 / 执行后 C 盘可用空间对比（真实数字）

| 时点      | 可用空间                          | 已用空间              | 来源                           |
| ------- | ----------------------------- | ----------------- | ---------------------------- |
| **执行前** | **7.568 GB**（`df -h` 显示 7.6G） | 122.382 GB（≈123G） | `Get-PSDrive C` / `df -h /c` |
| **执行后** | **13.845 GB**                 | 116.106 GB        | `Get-PSDrive C`              |
| **净释放** | **+6.277 GB**                 | −6.276 GB         | —                            |

> 说明：`df`/PowerShell 均以 GiB（1024³）计。任务书中标注的 2.6G / 2.4G / 1.5G 为十进制 GB，换算后约 2.5 / 2.4 / 1.4 GiB，与实际释放一致。

### 分步空间变化（逐步实测）

| 步骤 | 操作                        | 释放前可用     | 释放后可用      | 本步释放           |
| -- | ------------------------- | --------- | ---------- | -------------- |
| ①  | 删 AVD 快照 `default_boot`   | 7.568 GB  | 10.065 GB  | **+2.497 GB**  |
| ②  | 清空 `C:\Users\AMX\.gradle` | 10.065 GB | 12.443 GB  | **+2.378 GB**  |
| ④  | 删除 C 盘 SDK 目录             | 12.443 GB | 13.858 GB  | **+1.415 GB**  |
| —  | 合计                        | —         | 13.845 GB* | **≈ +6.29 GB** |

\* 末值 13.845 GB 与分步累计 13.858 GB 的 0.013 GB 差异为系统在清理期间产生的正常写入波动。

---

## 2. 逐项执行结果表

| # | 操作                       | 精确路径                                                                   | 结果             | 实际释放         | 核验证据                                                                                                                                   |
| - | ------------------------ | ---------------------------------------------------------------------- | -------------- | ------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| ① | 删除 AVD Quick Boot 快照     | `C:\Users\AMX\.android\avd\Android16_API36.avd\snapshots\default_boot` | ✅ **成功**       | **2.497 GB** | 目录已消失；`config.ini` 等保留；`df` 7.568→10.065                                                                                               |
| ② | 清空 Gradle Home 缓存（保留目录）  | `C:\Users\AMX\.gradle`（全部子项）                                           | ✅ **成功**       | **2.378 GB** | 9 个子目录全部删除、目录本身保留（`Test-Path`=True）；`df` 10.065→12.443                                                                                 |
| ③ | 建立 Junction              | `C:\Users\AMX\.gradle` → `D:\Agent\Android\.gradle`                    | ✅ **成功**       | —            | `LinkType=Junction`、`Target=D:\Agent\Android\.gradle`；`:app:compileDebugKotlin` **BUILD SUCCESSFUL**                                   |
| ⑤ | 设置系统级 `GRADLE_USER_HOME` | HKLM `...\Session Manager\Environment`                                 | ❌ **失败（权限不足）** | —            | 异常：`不允许所请求的注册表访问权`；HKLM 中该值仍不存在。HKCU 中已存在且正确                                                                                           |
| ④ | 迁移 C 盘 SDK → D 盘         | `C:\Users\AMX\AppData\Local\Android\Sdk`                               | ✅ **成功**       | **1.415 GB** | 先复制缺失组件（文件数逐一相等）→ 改 `local.properties` → **`assembleDebug` BUILD SUCCESSFUL** → 删除 C SDK（`Test-Path`=False）→ **再次构建 BUILD SUCCESSFUL** |
| ⑤ | 最终全量测试                   | `./gradlew test`                                                       | ✅ **成功**       | —            | **271 条测试全绿**（224 + 47，failures=0 / errors=0 / skipped=0），并已用 `--rerun-tasks` **真实重跑**验证                                               |

### 各路径删除前的性质确认（安全前置）

- `snapshots/default_boot/` 内**仅含 `ram.img`（2,684,420,096 字节）**，确认为快照数据，非系统镜像。
- `emu-launch-params.txt` 明确含 **`-no-snapshot`** 启动参数 → 该快照当前未被使用，删除安全。
- `C:\Users\AMX\.gradle` 内容全部为可重建缓存（`caches` 2.3G + `wrapper` 146M + 其它），删除前已核实 **D 盘存在等价 wrapper 发行包**。

---

## 3. SDK 迁移详情

### 3.1 两个 SDK 的差异（迁移前实测）

| 子目录              | C 盘 SDK（1.5G）         | D 盘 SDK（7.0G）                   | 差异          |
| ---------------- | --------------------- | ------------------------------- | ----------- |
| `build-tools`    | ✅ 34.0.0（137M）        | ❌ **缺失**                        | **C→D 需复制** |
| `platforms`      | ✅ android-35（127M）    | ❌ **缺失**                        | **C→D 需复制** |
| `cmdline-tools`  | ✅ latest（147M）        | ❌ **缺失**                        | **C→D 需复制** |
| `platform-tools` | ✅ 17M                 | ✅ 17M                           | 两边都有，无需处理   |
| `emulator`       | ✅ 1.1G                | ✅ 1.1G                          | 两边都有，无需处理   |
| `licenses`       | android-sdk / preview | android-sdk / preview / arm-dbt | D 更全        |
| `system-images`  | ❌ 无                   | ✅ 6.0G                          | D 独有，保留     |

> 项目要求：`compileSdk = 35`、`targetSdk = 35`、`minSdk = 26` → 依赖 `platforms/android-35` 与 `build-tools/34.0.0`，二者当时**仅在 C 盘存在**，因此必须复制。

### 3.2 复制内容（复制，非移动）

使用 `robocopy /E /COPY:DAT` 将以下三项从 C 盘复制到 D 盘：

| 组件              | 复制后文件数 C / D      | 体积 C / D    | 结果                      |
| --------------- | ----------------- | ----------- | ----------------------- |
| `build-tools`   | 170 / **170**     | 137M / 137M | ✅ 完全一致                  |
| `platforms`     | 11163 / **11163** | 127M / 123M | ✅ 文件数一致（体积差异为簇分配/压缩显示差） |
| `cmdline-tools` | 104 / **104**     | 147M / 147M | ✅ 完全一致                  |

复制后 D 盘 SDK 由 **7.0G → 7.4G**（+0.4G），缺失组件已补齐，成为**完整超集**。

### 3.3 `local.properties` 改动

```diff
- ## Android SDK 本地路径配置文件
- sdk.dir=C\:\\Users\\AMX\\AppData\\Local\\Android\\Sdk
+ ## Android SDK 本地路径配置文件
+ sdk.dir=D\:\\Agent\\Android\\Sdk
```

- 改动前已备份：`D:\Projects\androidapk\local.properties.bak`（94 字节，保留原 C 盘配置）。
- 仅修改 `sdk.dir` 一行，未触碰其它内容。

### 3.4 构建验证结果（关键验证点）

| 验证              | 命令                                                          | 结果                                                    |
| --------------- | ----------------------------------------------------------- | ----------------------------------------------------- |
| 迁移后构建（增量）       | `./gradlew :app:assembleDebug`                              | ✅ **BUILD SUCCESSFUL**（APK 生成 `app-debug.apk` 17.8MB） |
| 迁移后构建（clean 全量） | `./gradlew clean :app:assembleDebug`                        | ✅ **BUILD SUCCESSFUL**                                |
| **SDK 路径权威证明**  | `./gradlew :app:tasks --init-script`（打印 AGP `sdkDirectory`） | ✅ **`PROJECT_SDK_DIR[:app] = D:\Agent\Android\Sdk`**  |
| 删除 C SDK 后再次构建  | `./gradlew clean :app:assembleDebug`                        | ✅ **BUILD SUCCESSFUL**                                |
| 最终全量测试          | `./gradlew test --rerun-tasks`                              | ✅ **BUILD SUCCESSFUL**，**271 条全绿**                    |

### 3.5 迁移过程中遇到并已解决的问题

- **现象**：删除 C SDK 后首次 `clean :app:assembleDebug` 失败，报  
  `Failed to transform android.jar ... C:\Users\AMX\AppData\Local\Android\Sdk\platforms\android-35\android.jar`。
- **原因**：Gradle **配置缓存 / 执行历史**中序列化了旧的 C 盘 SDK 绝对路径（`local.properties` 内容变化未使所有缓存条目失效）。
- **处理**：删除项目可再生的缓存目录 `D:\Projects\androidapk\.gradle`（**非源码、非构建脚本**），重新构建即成功。此为移动 SDK 后的标准操作，不影响项目源码。

---

## 4. Junction 与 HKLM 变量的结果

### 4.1 Junction（✅ 成功）

```
New-Item -ItemType Junction -Path "C:\Users\AMX\.gradle" -Target "D:\Agent\Android\.gradle"
```

验证：

```
FullName : C:\Users\AMX\.gradle
LinkType : Junction
Target   : {D:\Agent\Android\.gradle}
```

- 建立前先确认 `C:\Users\AMX\.gradle` 已空，再删除空目录，随后建立 Junction。
- 透过 Junction 读取 `C:\Users\AMX\.gradle\wrapper\dists\` 能正确列出 D 盘内容。
- 效果：即使某些进程缺失 `GRADLE_USER_HOME` 而回落到 `C:\Users\AMX\.gradle`，也会被重定向到 D 盘，**彻底消除 C 盘 Gradle Home 复发问题**。

### 4.2 HKLM 系统级 `GRADLE_USER_HOME`（❌ 失败 — 需管理员）

| 位置                          | 状态                                                     |
| --------------------------- | ------------------------------------------------------ |
| HKCU（用户级）`HKCU\Environment` | ✅ 已存在且正确：`GRADLE_USER_HOME = D:\Agent\Android\.gradle` |
| HKLM（系统级）                   | ❌ **不存在**（执行前），本次写入被拒绝                                 |

尝试命令与错误：

```powershell
[Environment]::SetEnvironmentVariable("GRADLE_USER_HOME", "D:\Agent\Android\.gradle", "Machine")
# 异常：使用"3"个参数调用"SetEnvironmentVariable"时发生异常:"不允许所请求的注册表访问权。"
```

> 权限不足，**未强行绕过**。此值当前不影响本机构建（因 Junction 已兜底），仅为彻底杜绝其它账户/服务进程回落 C 盘而建议补齐。

---

## 5. 失败项与回滚记录

| 项                             | 状态             | 说明                                                                                           |
| ----------------------------- | -------------- | -------------------------------------------------------------------------------------------- |
| ⑤ HKLM 系统级 `GRADLE_USER_HOME` | ❌ 失败（权限不足）     | **未回滚**（因为未产生任何改动，HKLM 值本就缺失）。需管理员执行，见第 6 节                                                  |
| SDK 迁移构建（首次删除 C SDK 后）        | ⚠️ 曾失败，**已修复** | 因 Gradle 配置缓存残留 C 盘路径；清理项目 `.gradle` 缓存后恢复，**无回滚必要**（`local.properties` 始终指向 D，且最终构建/测试全部通过） |

> **`local.properties` 回滚条件**：任务要求「若第 4 步构建失败则回滚」。实际第 4 步构建**成功**，故**未回滚**，`local.properties` 现指向 D 盘。备份 `local.properties.bak` 仍保留以备需要。

### 执行中的一处操作偏差（如实说明）

- **背景**：本机 Bash 的 `rm` 删除会**把文件移入回收站**而非永久删除（实测：删除快照后 C 盘可用空间未变化，而 `C:\$Recycle.Bin` 增至 2.6G）。因此后续删除改用 PowerShell / .NET 永久删除。
- **偏差**：在清理回收站中该快照项时，因 PowerShell 双引号内 `$R0FUT6D` 被当作变量展开，实际执行了「删除该用户回收站文件夹本身」。
- **影响评估**：该回收站文件夹当时**仅含本次删除的快照项 `$R0FUT6D`、其元数据 `$I0FUT6D` 与系统自动生成的 `desktop.ini`**，**未包含任何其它用户文件**；Windows 会在需要时自动重建回收站文件夹。**未造成用户数据丢失**。特此如实记录。

---

## 6. 给用户的后续操作

### 6.1 需管理员执行（补齐系统级变量，1 条命令）

以**管理员身份**打开 PowerShell，执行：

```powershell
[Environment]::SetEnvironmentVariable("GRADLE_USER_HOME", "D:\Agent\Android\.gradle", "Machine")
```

验证（无需管理员）：

```powershell
reg query "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v GRADLE_USER_HOME
```

或等价命令：

```cmd
setx GRADLE_USER_HOME "D:\Agent\Android\.gradle" /M
```



> 说明：本机因已建立 Junction 兜底，**不执行此步也不影响当前构建**；执行后可进一步防止其它账户/系统服务进程回落 C 盘。

### 6.2 可选清理

- 确认一切正常后，可删除备份文件 `D:\Projects\androidapk\local.properties.bak`。
- 若希望永久删除而非移入回收站，请勿在 Bash 中用 `rm`（会进回收站）；改用 PowerShell 或定期清空回收站。

---

## 7. 未验证项

1. **AVD 模拟器实际启动**：仅验证快照目录删除与启动参数含 `-no-snapshot`，**未实际启动模拟器**跑一遍。鉴于启动参数为 `-no-snapshot`，删除快照不影响启动，但未经运行验证。
2. **HKLM 系统级变量的生效**：因权限不足未写入，**未验证**（见第 6.1 节）。
3. **其它账户 / 系统服务进程的 Gradle Home 回落**：本次仅覆盖当前用户环境，未验证其它身份运行进程的行为（Junction 已从路径层面兜底）。
4. **D 盘剩余空间**：本次迁移使 D 盘增加约 0.4G（SDK）+ 约 2.4G（Gradle Home 由 Junction 承接）等占用，**未核对 D 盘剩余空间是否充足**。
5. **`app-debug.apk` 在真机/模拟器上的安装与运行**：仅验证构建产物生成，未做安装运行验证。

---

## 附：最终状态快照

| 项目                                       | 状态                                                          |
| ---------------------------------------- | ----------------------------------------------------------- |
| C 盘可用空间                                  | **13.845 GB**（执行前 7.568 GB）                                 |
| `...\snapshots\default_boot`             | 已删除（不存在）                                                    |
| `Android16_API36.avd\config.ini`         | 保留（存在）                                                      |
| `C:\Users\AMX\.gradle`                   | **Junction → D:\Agent\Android.gradle**                      |
| `C:\Users\AMX\AppData\Local\Android\Sdk` | 已删除（不存在）                                                    |
| `D:\Agent\Android\Sdk`                   | 存在，7.4G（完整）                                                 |
| `local.properties`                       | `sdk.dir=D\:\\Agent\\Android\\Sdk`                          |
| 构建                                       | `assembleDebug` / `clean assembleDebug` 全部 BUILD SUCCESSFUL |
| 测试                                       | **271 条全绿**（真实重跑验证）                                         |
