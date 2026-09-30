# Project: Android Ebbinghaus Knowledge Memo App

## Architecture
- **Tech Stack**: Kotlin 2.0.21 + Jetpack Compose + Material 3 + Room 2.6.1 + KSP + Coroutines Flow + ViewModel.
- **Platform Compatibility**: Designed for Android 16 (API 36/35, minSdk 26+), 100% lightweight with zero heavy external dependencies.
- **Layering Architecture**:
  1. `core/`: Pure Kotlin domain models and algorithms (`EbbinghausScheduler`, `RolloverEngine`, `ReviewStage`, `ReviewRating`). 100% decoupled from Android SDK, enabling millisecond JVM unit test execution.
  2. `data/`: Room database (`AppDatabase`), Entities with SQLite ForeignKey CASCADE (`KnowledgeMemoEntity`, `ReviewTaskEntity`, `UserSettingsEntity`), TypeConverters, DAOs, and Repositories exposing Kotlin Coroutine `Flow`.
  3. `ui/`: Jetpack Compose Material 3 UI, Single-Activity architecture (`MainActivity`), Navigation Compose, MVI/UDF ViewModels (`MemoListViewModel`, `ReviewViewModel`, `DashboardViewModel`, `SettingsViewModel`).

---

## Feature Inventory

| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F01 | 知识点录入 (Create) | 新增知识点，绑定唯一ID、内容文本、标签列表、个人笔记与初始复习任务 | M1 | R1, 开发计划.md:5 |
| F02 | 知识点编辑 (Edit) | 修改内容文本、标签或笔记，完全保留原有艾宾浩斯复习进度 | M1 | R1, 开发计划.md:6 |
| F03 | 知识点删除 (Delete) | 物理删除知识点，级联清除关联的复习任务与历史日志 | M1 | R1, 开发计划.md:6 |
| F04 | 多标签分类与筛选 | 支持标签归类并在知识点列表页按标签点选快速筛选 | M1 | R1, 开发计划.md:5 |
| F05 | 多关键词即时模糊搜索 | 支持空格分隔的多关键词对内容文本及笔记进行即时包含检索 | M1 | R1, 开发计划.md:5 |
| F06 | 个人笔记查阅与编辑 | 知识点详情页与复习界面均原生支持查阅并随时编辑保存笔记 | M1 | R1, 开发计划.md:21 |
| F07 | 录入时间基准 (Day 0) | 以知识点录入自然日为 Day 0，初始档位为 1 档，首个复习日为 Day 1 (录入当天不排入复习队列) | M2 | R2, 开发计划.md:8 |
| F08 | 1~6 档标准复习间隔 | 标准 1~6 档复习间隔依次为：1天、2天、4天、7天、15天、30天 | M2 | R2 |
| F09 | 满档晋升长周期 (60天) | 第 6 档再次“记住”进入长周期复习（60天/次），长周期再次“记住”保持 60 天 | M2 | R2, 开发计划.md:12 |
| F10 | “忘记”降档状态机 | 选择“忘记”档位回退 1 档（第 1 档保持第 1 档，长周期退回第 6 档） | M2 | R2, 开发计划.md:10 |
| F11 | “模糊”保档状态机 | 选择“模糊”保持当前档位与对应间隔不变 | M2 | R2, 开发计划.md:11 |
| F12 | “已复习”缺省处理 | 用户仅点击“已复习”未选评级时，系统默认等价于“模糊”处理 | M2 | R2, 开发计划.md:13 |
| F13 | “跳过”延期机制 | 点击“跳过”不改变档位，该任务自动延后 1 天复习（移出今日队列） | M2 | R2, 开发计划.md:22 |
| F14 | 每日复习上限设置 | 支持用户配置每日复习数量上限（0~50条） | M1 | R2, 开发计划.md:19 |
| F15 | 上限为 0 停用提醒 | 每日复习上限设为 0 时，当日待复习数量为 0、不提醒不弹窗 | M2 | R2, 开发计划.md:19 |
| F16 | 最早到期优先截断 | 到期任务 > 上限时，按「到期时间最早优先」（dueDate ASC）截断，超量顺延次日 | M2 | R2, 开发计划.md:14 |
| F17 | 跨多天未登录无惩罚合并 | 连续多天未打开 APP，所有到期未复习任务自动合并入候选池，不标记逾期不扣分惩罚 | M2 | R2, 开发计划.md:16,18 |
| F18 | 应用内汇总看板 (Banner) | 首页醒目展示今日待复习知识点总量，提供“立即复习”直达入口 | M3 | R3, 开发计划.md:17 |
| F19 | 次日打开汇总弹窗 (Modal) | 次日或多天未登录打开 APP 时，自动扫描聚合当日待复习量并弹出单条看板弹窗 | M3 | R3, 开发计划.md:16-17 |
| F20 | 免权限应用内提醒机制 | 无需申请 POST_NOTIFICATIONS 权限，由应用前台生命周期感知即时触发 | M1 | R3, 开发计划.md:26 |
| F21 | 卡片式沉浸复习流 | 逐张卡片展示待复习知识点，集成 5 种操作按钮与进度统计 | M3 | R1, R2, 验收标准 |
| F22 | 复习中即时笔记编辑 | 复习界面可展开抽屉直接编辑并持久化笔记，不打断复习心流 | M3 | R1, 开发计划.md:21 |
| F23 | 纯 JVM 自动化单元测试 | 针对艾宾浩斯状态机、超量顺延及多天调度算法提供 100% 通过的单元测试套件 | M2 | R4, 验收标准 |

---

## Milestones

| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | 工程脚手架、Room 数据层与艾宾浩斯核心算法 (含 100% JVM 单测) | Gradle 8.10.2 + AGP 8.7.3 + Kotlin 2.0.21 + Room 2.6.1 + KSP，Entity/DAO/Repository，CASCADE 外键级联，纯 Kotlin 调度与顺延引擎，全量 JVM 单元测试 100% 通过 | none | DONE |
| M2 | Jetpack Compose UI 界面、ViewModel 状态流与应用内看板 | Material 3 主题、Navigation 路由、MemoListScreen (列表/搜索/多标签/增删改弹窗)、ReviewScreen (卡片沉浸复习/5类评级/即时笔记编辑)、DashboardDialog/HeroBanner (启动聚合/一键直达)、SettingsScreen (每日上限 0~50) | M1 | DONE |
| M3 | 全局集成装配、端到端闭环验证与 Debug APK 打包产出 | MainActivity 与 EbbinghausApp 装配、依赖注入桥接、端到端全链路业务验证、自动化编译打包产出 app-debug.apk | M1, M2 | DONE |

---

## Interface Contracts

### 1. 纯领域模型与算法接口 (Core ↔ Data/UI)
- `ReviewStage`: STAGE_1(1d), STAGE_2(2d), STAGE_3(4d), STAGE_4(7d), STAGE_5(15d), STAGE_6(30d), LONG_TERM_60(60d).
- `ReviewRating`: FORGET, VAGUE, REMEMBER, DEFAULT_REVIEWED, SKIP.
- `ReviewScheduleResult`: `(nextStage: ReviewStage, nextReviewDate: LocalDate, intervalDays: Int)`.
- `EbbinghausScheduler.calculateNextReview(currentStage, rating, reviewDate)`: 纯函数计算下次档位与到期日。
- `RolloverEngine.planReviewBatch(pendingTasks, today, dailyLimit)`: 纯函数执行 `dueDate ASC` 排序、每日上限截取、超量顺延与多日合并。

### 2. 数据层接口 (Data ↔ UI/ViewModels)
- `MemoRepository`:
  - `getAllMemos(): Flow<List<KnowledgeMemoEntity>>`
  - `searchMemos(query: String, tag: String?): Flow<List<KnowledgeMemoEntity>>`
  - `createMemo(content: String, notes: String, tags: List<String>): Long`
  - `updateMemo(id: Long, content: String, notes: String, tags: List<String>)`
  - `updateNotesOnly(id: Long, notes: String)`
  - `deleteMemo(id: Long)` (触发 SQLite CASCADE 自动清理关联任务)
- `ReviewRepository`:
  - `getDueReviewTasks(today: LocalDate): Flow<List<MemoWithReviewTask>>`
  - `submitReviewRating(taskId: Long, rating: ReviewRating, reviewDate: LocalDate)`
- `SettingsRepository`:
  - `getSettings(): Flow<UserSettingsEntity>`
  - `updateDailyLimit(newLimit: Int)`
  - `markDashboardPrompted(promptDate: LocalDate)`

---

## Code Layout

```
d:/Projects/androidapk/
├── local.properties
├── gradle.properties
├── settings.gradle.kts
├── build.gradle.kts
├── gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.properties
│   └── gradle-wrapper.jar
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/ebbinghaus/memo/
│       │   │   ├── MainActivity.kt
│       │   │   ├── EbbinghausApp.kt
│       │   │   ├── core/
│       │   │   │   ├── model/ (ReviewStage, ReviewRating, RolloverPlan)
│       │   │   │   └── engine/ (EbbinghausScheduler, RolloverEngine, SchedulableTask)
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── AppDatabase.kt
│       │   │   │   │   ├── entity/ (KnowledgeMemoEntity, ReviewTaskEntity, UserSettingsEntity, MemoWithReviewTask)
│       │   │   │   │   ├── converter/ (Converters.kt)
│       │   │   │   │   └── dao/ (KnowledgeMemoDao, ReviewTaskDao, UserSettingsDao)
│       │   │   │   └── repository/ (MemoRepository, ReviewRepository, SettingsRepository & Impls)
│       │   │   └── ui/
│       │   │       ├── theme/ (Color, Theme, Type)
│       │   │       ├── navigation/ (AppNavigation, Screen)
│       │   │       ├── memolist/ (MemoListScreen, MemoListViewModel, MemoEditDialog)
│       │   │       ├── review/ (ReviewScreen, ReviewViewModel, ReviewCardComponent)
│       │   │       ├── dashboard/ (DashboardDialog, DashboardViewModel, DashboardBanner)
│       │   │       └── settings/ (SettingsScreen, SettingsViewModel)
│       │   └── res/
│       │       └── values/ (strings.xml)
│       └── test/
│           └── java/com/ebbinghaus/memo/core/
│               ├── EbbinghausSchedulerTest.kt
│               └── RolloverEngineTest.kt
```
