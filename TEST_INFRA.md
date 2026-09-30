# E2E Test Infra: Android Ebbinghaus Knowledge Memo App

## Test Philosophy
- **Opaque-box, requirement-driven**: Tests derive directly from `ORIGINAL_REQUEST.md` and user-facing specifications without coupling to internal private implementations.
- **Progressive testability**: Earliest milestones (M1, M2) provide pass/fail signals via pure JVM unit test runners without requiring full UI assembly.
- **Methodology**: Category-Partition + Boundary Value Analysis (BVA) + Pairwise Combinatorial Testing + Real-World Workload Testing.

---

## Feature Inventory & Test Coverage Matrix

| # | Feature | Requirement Source | Tier 1 (Feature) | Tier 2 (Boundary) | Tier 3 (Pairwise) | Tier 4 (Real-World) |
|---|---------|-------------------|:----------------:|:-----------------:|:-----------------:|:-------------------:|
| F01 | 知识点录入 | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ | ✓ |
| F02 | 知识点编辑保留进度 | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ | ✓ |
| F03 | 知识点删除级联清理 | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ | ✓ |
| F04 | 多标签分类与筛选 | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ | ✓ |
| F05 | 多关键词即时模糊搜索 | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ | ✓ |
| F06 | 笔记查阅与编辑 | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ | ✓ |
| F07 | 录入日基准 Day 0 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F08 | 1~6 档标准间隔 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F09 | 60天长周期复习 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F10 | “忘记”回退1档 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F11 | “模糊”保持当前档 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F12 | “已复习”缺省按模糊 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F13 | “跳过”延后1天 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F14 | 每日复习上限设置 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F15 | 上限为0停用复习 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F16 | 最早到期优先截断 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F17 | 多天未登录合并无罚 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ | ✓ |
| F18 | 应用内汇总看板 | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ | ✓ |
| F19 | 启动聚合弹窗提醒 | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ | ✓ |
| F20 | 免通知权限应用内调度 | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ | ✓ |
| F21 | 卡片式沉浸复习流 | ORIGINAL_REQUEST §R1,R2 | 5 | 5 | ✓ | ✓ |
| F22 | 复习中即时笔记编辑 | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ | ✓ |
| F23 | 纯 JVM 单元测试覆盖 | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ | ✓ |

---

## Test Architecture & Execution Semantics

### 环境前置（2026-09-14 修复记录）
- **`gradlew` 曾为截断的损坏脚本**（1694 B，末尾仅 `exec "$JAVACMD" "$@"`，且 `APP_HOME` 未定义、
  缺少 MSYS 路径转换），导致 `./gradlew` 必然失败。**现已还原为 Gradle 8.10.2 官方标准脚本**（8695 B），
  已用 `./gradlew --version` 实测输出 `Gradle 8.10.2`。`gradlew.bat` 原本完好，未改动。
- **本仓库的自动化环境会拦截 `cmd.exe`**（Bash 工具报 "Invoking cmd.exe from Bash bypasses all command
  validation"，PowerShell 工具报 "cmd.exe cannot be used from the PowerShell tool"）。因此在自动化环境中
  **不要用 `cmd.exe /c "gradlew.bat ..."`**，改用下方 POSIX 形式。

### 执行命令（POSIX / Git Bash，推荐）
```bash
cd D:/Projects/androidapk
export JAVA_HOME=/c/Users/AMX/.jdks/jdk-17.0.13+11   # 或任意 JDK 17
./gradlew test --console=plain --rerun-tasks
./gradlew assembleDebug --console=plain
```
等价兜底（若 `gradlew` 再次损坏，可直接调用 wrapper 主类）：
```bash
"$JAVA_HOME/bin/java" -classpath "gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain test --console=plain
```

- **Test Runner (JVM Unit Tests)**:
  - Command: `./gradlew test`（或 `./gradlew testDebugUnitTest`）；Windows CMD 下为 `.\gradlew.bat test`
  - Pass/Fail Semantics: Exit code 0, 100% tests pass, zero failed or ignored assertions.
  - 当前基线：**84 passed / 0 failed / 0 skipped**（15 个测试类）。
- **Build & Packaging Runner (APK Assembly)**:
  - Command: `./gradlew assembleDebug`（Windows CMD 下为 `.\gradlew.bat assembleDebug`）
  - Pass/Fail Semantics: Exit code 0, generates `app/build/outputs/apk/debug/app-debug.apk` (> 1MB).
  - 当前产物：约 17.5 MB。

### 测试覆盖边界（如实声明）
- 单测覆盖 `core/` 算法、`data/` 契约与 `ui/` ViewModel 纯 JVM 逻辑；**未接入 Jacoco，无覆盖率数字**。
- **Compose UI 层无自动化测试**（无仪器化测试）。UI 结论来自静态推演 + Preview。
  页面级三档 Preview 位于 `app/src/debug/java/com/ebbinghaus/memo/ui/preview/PagePreviews.kt`
  （12 个 Preview：4 页面 × 3 档 + 设置页停用态），依赖同目录 `PreviewFakes.kt` 的内存仓储；
  两者仅存在于 debug 变体，不进入 release 产物。

---

## Real-World Application Scenarios (Tier 4)
1. **Scenario 1: 新人连续 7 天背单词与复习流转**：
   - Day 0 录入 10 个单词；Day 1 全部复习并记住（进入 Stage 2）；Day 3 到期复习，部分忘记（回退 Stage 1），验证档位分支隔离。
2. **Scenario 2: 每日上限为 20，突发积压 60 条知识点**：
   - 到期任务 60 条，limit 设为 20。验证今日复习队列严格截取最早到期 20 条，剩余 40 条顺延次日。
3. **Scenario 3: 暑假放假 15 天未登录重新打开 APP**：
   - 15 天内累积到期任务全部合并，不打任何“逾期”标签，所有档位保持原样，根据每日上限平滑复习。
4. **Scenario 4: 边复习边精修知识点笔记**：
   - 在复习界面中展开笔记抽屉，修改笔记文本并保存，随后点击“记住”完成复习。验证笔记更新成功且复习进度正确推进至下一档。
5. **Scenario 5: 知识点批量整理与删除联动**：
   - 知识点设置多标签，使用标签快速定位筛选，搜索关键词匹配。删除其中 1 个知识点，级联校验其在今日待复习队列中彻底消失。
