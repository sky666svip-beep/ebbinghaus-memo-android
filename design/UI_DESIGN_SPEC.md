# 艾宾浩斯知识点备忘录 · UI/UX 设计规范

> 版本 v1.0 · 基于 `d:/Projects/androidapk` 现有代码结构（`core/` `data/` `ui/` 三层）推导
> 技术底座：Kotlin + Jetpack Compose + Material 3 + Navigation Compose + Room Flow + MVI/UDF
> 平台基线：Android 16（targetSdk 36，minSdk 26）；全量复用 `ui/theme/Color.kt` `Type.kt` `Shape`（圆角以代码现有取值为准）

---

## 0. 设计推导依据（代码 → 界面映射）

| 代码落点 | 约束 / 能力 | 对界面设计的直接影响 |
|---|---|---|
| `Screen.kt` 4 条路由 | `memo_list` / `review` / `settings` / `memo_detail/{memoId}` | 导航骨架为"1 主 Tab + 1 沉浸页 + 1 设置页 + 1 详情页" |
| `ReviewStage`（1/2/4/7/15/30/60 天，7 档） | 档位是核心可视资产 | 档位必须"徽章化"常驻在复习卡与详情卡 |
| `ReviewRating` 5 枚举 | FORGET / VAGUE / REMEMBER / DEFAULT_REVIEWED / SKIP | 复习页底部固定 5 按钮，颜色语义固化 |
| `RolloverEngine.planReviewBatch` | 输出 `todayBatch / deferredTasks / totalPendingCount` | Banner 需同时表达"今日量 + 顺延量 + 总量" |
| `DashboardViewModel.checkAppLaunchPrompt` | 跨天 + 未弹过 + limit>0 + dueToday>0 才弹窗 | 看板弹窗为"每日一次"的一次性浮层 |
| `UserSettingsEntity.dailyReviewLimit 0~50` | 0 = 停用 | Banner 与复习页必须存在"停用"独立状态 |
| `MathView` / `MathTextPreprocessor` | 支持 LaTeX 高保真渲染 | 正文区禁止用纯 `Text` 截断渲染，列表用 Unicode 降级 |
| `KnowledgeMemoEntity` | content / notes / tags / createdAt / updatedAt | 详情页卡片顺序即字段优先级顺序 |
| SQLite `ForeignKey.CASCADE` | 删除即级联 | 删除必须二次确认 + 可逆（撤销） |

---

## 1. 设计语言（Design Language）

**关键词：专注 · 轻量 · 无压 · 克制的色彩**

复习是有认知负担的行为，界面原则是 **"零噪音、零逾期焦虑、零惩罚感"**：
- 不使用红色"逾期/过期"标签（业务上不存在逾期）；
- 不使用进度分数、连续打卡断签等压力化元素；
- 只在"评级"这一处使用高饱和语义色，其余全用低饱和中性面。

### 1.1 色彩 Token

沿用 `ui/theme/Color.kt`，不新增主色。

| 角色 | Light | Dark | 用途 |
|---|---|---|---|
| Primary | `#006874` | `#4FD8EB` | 主按钮、FAB、进度条、Tab 选中 |
| Primary Container | `#97F0FF` | `#004E58` | Hero Banner 底、档位徽章底 |
| Secondary Container | `#CDE7EC` | `#334B4F` | "已完成"状态卡底 |
| Tertiary | `#525E7D` | `#B9C6EA` | 笔记/记忆线索区（与正文区别开） |
| Surface | `#F8FAF8` | `#0E1415` | 卡片 |
| Surface Variant | `#DBE4E6` | `#3F484A` | 次级容器、骨架屏 |
| Outline | `#6F797A` | `#899294` | 辅助文字、空状态插画 |
| Error | `#E53935`（ForgetRed） | 同 | 删除、危险操作、校验错误 |

**评级语义色（全局唯一高饱和色组，禁止挪作他用）**

| 评级 | 色值 | 形态 | 语义 |
|---|---|---|---|
| 忘记 FORGET | `#E53935` | Filled 按钮 | 回退 1 档 |
| 模糊 VAGUE | `#F57C00` | Filled 按钮 | 保持档位 |
| 记住 REMEMBER | `#2E7D32` | Filled 按钮 | 前进 1 档 |
| 已复习 DEFAULT_REVIEWED | `#00796B` | Outlined 按钮 | 等同"模糊" |
| 跳过 SKIP | `#1976D2` | Outlined 按钮 | 不改档位，延后 1 天 |

### 1.2 字阶（沿用 `Type.kt`）

| Token | 规格 | 使用场景 |
|---|---|---|
| headlineLarge | 28/34 Bold | 设置页大数字（上限值） |
| headlineMedium | 24/30 SemiBold | Hero Banner 数量、完成页标题 |
| titleLarge | 20/26 SemiBold | TopAppBar 标题 |
| titleMedium | 16/24 Medium | 卡片正文、区块标题 |
| bodyMedium | 14/20 Normal | 笔记、说明文 |
| bodySmall | 12/16 Normal | 时间戳、副文案 |
| labelLarge | 14/20 Medium | 按钮文字 |
| labelSmall | 11/16 Medium | 标签 Chip |

**正文行高**：中文正文 ≥1.5 倍；`MathView` 正文 17~18sp，笔记 15sp。

### 1.3 形状 · 阴影 · 间距

| 元素 | 圆角 | 阴影 |
|---|---|---|
| 内容卡片 | 16dp | 1~2dp |
| 复习大卡 | 20dp | 3dp |
| 按钮 / 输入框 | 12dp | 0 |
| Chip / 徽章 | 8dp | 0 |
| FAB | 16dp | 6dp |
| Dialog | 28dp | 24dp |
| Bottom Sheet | 顶部 28dp | — |

间距栅格 **4dp 基准**：`4 / 8 / 12 / 16 / 20 / 24`；页面左右安全边距 **16dp**；列表项间距 **10dp**；卡片内边距 **14~20dp**。

### 1.4 图标与动效

- 图标：Material Symbols Outlined，默认 24dp，卡内次级 18~20dp；`contentDescription` 必填（无障碍）。
- 动效（与 `AppNavigation` 现有 `tween(300ms)` 对齐）：
  - 页面切换：横向 Slide 300ms（现状已实现）
  - 列表项增删：`animateItemPlacement` 200ms
  - 笔记抽屉展开：`AnimatedVisibility` expandVertically 250ms
  - 卡片推进复习：淡出 + 上移 180ms
  - 禁止使用弹跳动效；全部遵循系统"减少动画"开关。

---

## 2. 信息架构与导航结构

### 2.1 架构总览

```
MainActivity (Single-Activity, edge-to-edge)
└── EbbinghausTheme
    └── AppNavigation (NavHost, startDestination = memo_list)
        ├── ① 知识库  memo_list            ← 常驻 Tab，应用主页
        │     ├─ 内联：搜索栏 / 标签筛选栏 / Hero Banner
        │     ├─ 浮层：MemoEditDialog（新增·编辑）
        │     ├─ 浮层：DashboardDialog（跨天看板提醒，每日至多 1 次）
        │     ├─ 浮层：删除确认 AlertDialog（建议补充，见 §7.3）
        │     └─ 下钻 → ④ 详情 memo_detail/{memoId}
        ├── ② 复习    review               ← 沉浸式，隐藏底部导航
        │     └─ 卡片流 → ReviewCompletedView（完成页）
        └── ③ 设置    settings             ← 常驻 Tab
```

### 2.2 导航组件选型

| 断点 | 导航组件 | 说明 |
|---|---|---|
| Compact（<600dp） | **NavigationBar 底部导航**（3 项）+ TopAppBar | 现状为 TopAppBar + 设置图标；建议升级为底部 3 Tab，让"复习"从 Banner 单点升级为常驻一级入口 |
| Medium（600–839dp） | NavigationRail（竖向 80dp） | 释放横向空间给列表 |
| Expanded（≥840dp） | NavigationRail 或 永久 Drawer（240dp） | 配合 List–Detail 双窗格 |

**底部导航 3 项**

| 序 | 标签 | 图标 | 路由 | 徽标 |
|---|---|---|---|---|
| 1 | 知识库 | menu_book | `memo_list` | 无 |
| 2 | 复习 | list_alt | `review` | **Badge 显示 `dueTodayCount`**（>0 时显示，=0 时隐藏） |
| 3 | 设置 | settings | `settings` | 无 |

**导航规则**
1. **复习页为沉浸页**：进入 `review` 时隐藏 NavigationBar，仅保留 TopAppBar 返回键；退出复习时不弹"放弃确认"（复习不产生副作用，跳过即可）。
2. `memo_detail` 为下钻页：**不显示底部导航**，返回键 `popBackStack()`。
3. 底部 Tab 切换使用 `saveState = true / restoreState = true / launchSingleTop = true`，保证知识库的搜索词、筛选标签、滚动位置在 Tab 往返后保持。
4. 系统返回键：详情页 → 知识库；复习页 → 知识库；知识库 → 退出应用（可加"再按一次退出"）。
5. 深层链接预留：`memo_detail/{id}` 已支持参数化，未来可接 App Shortcut / Widget。

---

## 3. 通用页面布局框架

```
┌──────────────────────────────────────┐
│ StatusBar (edge-to-edge，透明)        │
├──────────────────────────────────────┤
│ TopAppBar  56dp                       │  ← 返回 / 标题 / 操作图标
├──────────────────────────────────────┤
│ [可选] 进度条 / 二级 Tab              │
├──────────────────────────────────────┤
│                                      │
│  Content   padding 16dp              │  ← 可滚动区（LazyColumn / Column+scroll）
│                                      │
├──────────────────────────────────────┤
│ 底部操作区 / FAB / NavigationBar      │
└──────────────────────────────────────┘
```

- **Edge-to-Edge**：Android 15/16 强制，`Scaffold` 自动处理 `systemBars` insets；FAB 与底部按钮需叠加 `navigationBars` padding，避免被手势条遮挡。
- **滚动行为**：知识库列表滚动时 TopAppBar 转为 `surfaceContainer` 容器色（`TopAppBarDefaults.centerAlignedTopAppBar` + `scrollBehavior`）。
- **FAB**：仅知识库页显示，右下角距边 16dp；滚动时保持可见（不做 auto-hide，避免误触丢失入口）。

---

## 4. 核心页面设计

### 4.1 P1 知识库主页 `MemoListScreen`

垂直分区（自上而下）：

| # | 区块 | 内容 | 状态分支 |
|---|---|---|---|
| 1 | **Hero Banner**（Card 16dp） | 今日待复习量 + 主 CTA | 3 态：`有任务` / `已完成` / `已停用` |
| 2 | **搜索栏** OutlinedTextField 12dp | 放大镜 + 输入 + 清空 ✕（有内容才出现） | 空 → 占位文案 |
| 3 | **标签筛选** LazyRow FilterChip | "全部" + 全部去重标签（字典序） | 无标签时整行隐藏 |
| 4 | **知识点列表** LazyColumn | 卡片（正文 4 行截断 / 笔记 2 行 / 标签 / 编辑·删除） | 正常 / 加载骨架 / 空态 |
| 5 | **FAB** | 新增 | 常驻 |

**Hero Banner 三态详规**

| 态 | 触发 | 容器色 | 图标 | 主文案 | 副文案 | 操作 |
|---|---|---|---|---|---|---|
| A 有任务 | `dueTodayCount > 0 && dailyLimit > 0` | `primaryContainer` | — | `今日待复习` + `N 条知识点`(headlineMedium) | 仅 `deferred>0` 时显示"今日上限 X 条，已自动顺延 Y 条至明日" | "立即开始" FilledButton |
| B 已完成 | `dueTodayCount == 0 && dailyLimit > 0` | `secondaryContainer` | check_circle 36dp | `今日复习已全部搞定！` | `暂无到期待复习知识点，继续保持！` | 整卡可点（可选） |
| C 已停用 | `dailyLimit <= 0` | `surfaceVariant` | schedule 32dp | `今日复习已暂停` | `每日复习上限已设为 0 条，可在设置中调整` | 副文案内嵌"去设置"可点链接 |

> 整卡 A 态可点击直达复习（`clickable` 已实现），CTA 按钮与整卡点击都指向同一 action。

**列表卡片 `MemoCardItem`**
- 正文：`MathTextPreprocessor.formatToUnicode(content)`，`titleMedium`，maxLines 4，Ellipsis
- 笔记：tertiary 色小图标 + 文本，`bodySmall`，maxLines 2，仅 `notes.isNotBlank()` 时渲染
- 标签：`FlowRow` + `SuggestionChip`（labelSmall）
- 操作：编辑（primary）/ 删除（error）两个 IconButton，触控区 48dp
- 整卡点击 → 详情页；操作按钮 `clickable` 独立，不冒泡到整卡

### 4.2 P2 知识点详情 `MemoDetailScreen`

单列滚动，卡片顺序：

1. **正文卡**：区块头（school 图标 + "知识点正文"，primary）+ `MathView`（17sp，minHeight 40dp，**不截断**，支持 LaTeX）
2. **标签卡**：`tags.isNotEmpty()` 才渲染；`surfaceVariant @50%` 容器 + FlowRow Chip
3. **笔记卡**：区块头（note_alt 图标 + "个人笔记与记忆线索"，tertiary）；有笔记走 `MathView` 15sp，无笔记走空态文案
4. **复习进度卡**：`primaryContainer @35%`，4 行 Key-Value
   - 当前档位 → 徽章（长周期文案：`长周期复习 (每 60 天一次)`；否则 `第 N 档 (间隔 D 天)`）
   - 下次复习日期 → `task.dueDate`
   - 累计复习次数 → `N 次`
   - 录入创建时间 → `yyyy-MM-dd HH:mm`
5. **底部操作组**：`编辑知识点`（Filled, weight 1f）+ `删除`（Outlined, error），高 48dp

**TopAppBar actions**：复制正文（Clipboard + Toast）/ 编辑 / 删除

### 4.3 P3 复习流 `ReviewScreen`

**布局（Compact）**

```
TopAppBar  ← 返回 | 每日复习 | 3 / 12
LinearProgressIndicator  6dp  (currentIndex / totalBatchCount)
────────────────────────────────────
        ReviewCardComponent  (Card 20dp, padding 16dp)
        · 档位徽章 (primaryContainer)      累计复习 N 次
        · MathView 正文 18sp
        · 标签 Chips
        · 展开栏：💡 查看个人笔记与记忆提示   ▾
        · [展开] 笔记区（展示态 / 编辑态）
────────────────────────────────────
底部按钮区（固定，padding 16dp）
Row1: [✕ 忘记] [? 模糊] [✓ 记住]      ← Filled，等分 weight 1f
Row2: [✓✓ 已复习 (默认模糊)] [⏭ 跳过]  ← Outlined，weight 1.2 / 0.8
```

**交互要点**
- 底部按钮区**固定不随卡片滚动**；卡片区 `verticalScroll` 自适应高度。
- 提交后 180ms 淡出上移切入下一张，顶部进度条与 `n / m` 同步 `+1`。
- 笔记抽屉：展开 → 展示态（Surface + 铅笔 IconButton）→ 编辑态（OutlinedTextField + 取消 / 保存笔记）。保存走 `memoRepository.updateNotesOnly()` 并**同步刷新本地队列**，不打断心流。
- 提交评级时若笔记仍处于编辑态：**先静默丢弃草稿**（或提示"笔记未保存"）。建议：点击评级前若 `isEditingNotes=true`，自动保存草稿（避免用户丢失输入）。
- 5 按钮语义必须在长按/无障碍文案中说明后果，如"记住：前进一档，30 天后再复习"。

### 4.4 P4 复习完成页 `ReviewCompletedView`

- celebration 图标 72dp（primary）
- 标题 `🎉 今日复习全部达成！`（headlineMedium）
- 副文案：`totalReviewed > 0` → "今日已成功巩固 N 条知识点。遵循艾宾浩斯曲线，记忆已注入长效固化区。"；否则 → "今日暂无到期待复习的知识点，去知识库添加新的知识吧！"
- CTA：`返回知识库主页`（48dp，圆角 14dp）
- 建议增强：展示"顺延至明日 N 条"提示（复用 `deferredCount`）+ "继续录入新知识点"次要按钮

### 4.5 P5 设置 `SettingsScreen`

**卡片 1 · 每日复习数量上限**
- 标题行：tune 图标 + "每日复习数量上限"
- 数值区：`dailyLimit == 0` → `0 条（已停用复习推送）`（error 色，headlineMedium）；否则 `N`（headlineLarge ExtraBold primary）+ `条 / 天`（titleMedium outline）
- `Slider` 0f–50f，steps 49
- 快捷 Chips：`0 (停用) / 10 / 20 / 30 / 50`，选中态 `FilterChip selected`
- 底部说明：`上限仅影响每日进入复习队列的数量，超量任务会自动顺延，不会丢失。`

**卡片 2 · 智能调度与顺延规则说明**（`surfaceVariant @50%`）
5 条 `RuleItem`：最早到期优先 / 超量自动顺延 / 上限 0 暂停 / 跨天零惩罚合并 / 6 档 + 长周期晋升

**建议新增（v1.1）**
- 卡片 3：外观（跟随系统 / 浅色 / 深色）
- 卡片 4：数据（导出 JSON / 导入 / 清空全部，危险操作二次确认）
- 卡片 5：关于（版本、算法说明、开源许可）

---

## 5. 浮层与弹窗

| 浮层 | 触发 | 组件 | 主按钮 | 次按钮 | 关闭方式 |
|---|---|---|---|---|---|
| **新增/编辑知识点** `MemoEditDialog` | FAB / 卡片编辑 / 详情页编辑 | AlertDialog 28dp | 保存 | 取消 | 返回键、点击外部（`dismissOnClickOutside=true`） |
| **每日看板** `DashboardDialog` | 跨天 + 今日未弹 + limit>0 + due>0 | AlertDialog + 图标 + 标题 | 立即复习 | 稍后 | 返回键（等同"稍后"） |
| **删除确认**（建议补） | 删除图标 | AlertDialog，confirm 用 error 色 | 确认删除 | 取消 | — |
| **Snackbar** | 删除成功 / 笔记保存 | Snackbar + action | 撤销 | — | 4s 自动消失 |

**表单校验（`MemoEditDialog`）**
- `content` 必填：`trim().isBlank()` → `isError=true` + supportingText "知识点内容不能为空"；输入任意非空字符即刻清除错误
- `tagsInput` 解析：`,` `，` `、` 空格 四种分隔 → trim → 去重 → 过滤空串
- 编辑态顶部提示：`修改内容不会重置复习进度`（tertiary 小字，消除用户顾虑）

---

## 6. 用户操作流程

### F1 冷启动与看板提醒
```
启动 → 渲染知识库 → DashboardViewModel.OnCheckAppLaunch
   ├─ limit <= 0  → Banner 态C（停用），不弹窗，落库 lastActiveDate
   └─ limit > 0   → 计算 plan
        ├─ isNewDay && !promptedToday && dueToday>0 → 弹出 DashboardDialog
        │      ├─ 立即复习 → 关闭 + 跳转复习页
        │      └─ 稍后     → 关闭（今日不再弹）
        └─ 否则 → 仅刷新 Banner（不弹窗）
   最后统一 settingsRepository.updateLastActiveDate(today)
```
`absentDays = ChronoUnit.DAYS.between(lastActive, today)`，`>1` 走多天文案："欢迎回来！您已有 N 天未打开应用……"

### F2 新增知识点
```
FAB → Dialog(空) → 填内容* / 标签 / 笔记 → 保存
   ├─ 内容为空 → 内联错误，不关闭
   └─ 通过 → createMemo → 自动派发 Day1 初始任务 → Dialog 关闭
        → 列表 Flow 自动插入新项 → Snackbar "已添加，首次复习在明天"
```

### F3 编辑（保留进度）
```
卡片/详情页 编辑 → Dialog(预填) → 修改 → 保存 → updateMemo
   → 仅改 content/notes/tags，ReviewTaskEntity 完全不动
   → Snackbar "已保存，复习进度保持不变"
```

### F4 删除（级联）
```
删除 → 确认弹窗 → 确认 → deleteMemo → SQLite CASCADE 清理 review_tasks
   → 列表 Flow 移除 → Snackbar "已删除"+[撤销]
   → 详情页路径：isDeleted → Toast + popBackStack()
```
> **撤销建议**：Room 事务内软删（新增 `deletedAt` 字段 + 30 天宽限）或 Snackbar 期间延迟提交；若保持物理删除，则 Snackbar 只做告知、不提供撤销，避免误导。

### F5 搜索与筛选
```
输入关键词 → _searchQuery / 点选标签 → _selectedTag
   → combine → flatMapLatest → searchMemos(query, tag) → Flow 重流
   → 命中：列表刷新（可加关键词高亮）
   → 无命中：空态 B（未检索到匹配的知识点）+ "清空筛选"按钮
再次点击同一标签 = 取消筛选
```

### F6 复习闭环
```
进入 → loadReviewBatch → RolloverEngine.planReviewBatch(dueTasks, today, limit)
   → todayBatch 为空 → 完成页（今日无任务）
   → 否则 逐卡：
        忘记 → 回退1档（第1档保持）  ┐
        模糊 → 保持当前档            │ submitReviewRating(taskId, rating, today)
        记住 → 前进1档（6档→长周期60）│ → currentIndex+1
        已复习 → 等同模糊            │
        跳过 → 不改档，dueDate+1天    ┘
   → 末张完成 → isCompleted=true → 完成页
```

### F7 复习中记笔记
```
展开抽屉 → 展示态 → 铅笔 → 编辑态（notesDraft）
   → 保存笔记 → updateNotesOnly(id, notes) → 同步本地队列 → 回到展示态
   → Toast "笔记已保存"
```

---

## 7. 状态反馈机制

### 7.1 四类反馈通道

| 通道 | 适用 | 时长 | 可操作性 |
|---|---|---|---|
| **内联 Inline** | 表单校验、卡片内说明 | 常驻 | 高（就近修正） |
| **Snackbar** | 删除、新增、保存成功 | 4s（带 action 时 10s） | 中（撤销） |
| **Toast** | 复制、笔记保存等轻量结果 | 2s | 无 |
| **整页状态** | 加载 / 空 / 错误 / 停用 | 常驻 | 高（带 CTA） |

### 7.2 加载态

| 场景 | 表现 | 阈值 |
|---|---|---|
| 详情页首帧 (`isLoading`) | 居中 `CircularProgressIndicator` | <300ms 不显示（防闪烁） |
| 列表首帧 | 骨架屏 3~5 张（`surfaceVariant` 圆角块 + shimmer） | 同上 |
| 搜索 | 不显示全屏 loading，沿用旧列表 + 顶部细进度条 | 输入防抖 300ms |
| 复习批次加载 | `isLoading` → 卡片区骨架，按钮区禁用 | — |
| 提交评级 | 按钮即时响应（乐观推进），写库异步 | — |

### 7.3 状态矩阵（含代码落点）

| # | 状态 | 触发条件 | 展示 | 恢复路径 | 代码落点 |
|---|---|---|---|---|---|
| S1 | 首次空库 | `memos.isEmpty() && 无搜索 && 无筛选` | 插画 + "知识库空空如也，点击右下角 + 开始添加吧！" + [新增知识点] 按钮 | FAB / 空态按钮 | `MemoListScreen` 空分支 |
| S2 | 搜索/筛选无结果 | `memos.isEmpty() && (query非空 ∨ tag非空)` | 🔍 + "未检索到匹配的知识点" + [清空筛选] | 清空条件 | 同上 |
| S3 | 今日无复习 | `dueTodayCount == 0 && limit > 0` | Banner 态B + 复习页完成页（第二条文案） | 返回知识库 | `DashboardBanner` / `ReviewCompletedView` |
| S4 | 复习已停用 | `limit <= 0` | Banner 态C；复习页进不去（或进入即完成页 + 停用说明） | 设置内调高 | `DashboardBanner` 首分支 |
| S5 | 超量顺延 | `deferredCount > 0` | Banner 副文案"已自动顺延 N 条至明日" | 无需操作 | `DashboardBanner` |
| S6 | 多天未登录 | `absentDays > 1` | DashboardDialog 多天文案 + "未标记逾期，不扣减记忆档位" | 立即复习/稍后 | `DashboardDialog` |
| S7 | 笔记为空 | `notes.isBlank()` | 列表：整块隐藏；详情："暂无笔记内容，点击下方编辑按钮…"；复习："（暂无个人笔记，点击右侧铅笔即可添加）" | 铅笔 → 编辑 | 三处 |
| S8 | 数据不存在 | `memo == null` | "该知识点不存在或已被移除" + [返回列表] | popBackStack | `MemoDetailScreen` |
| S9 | 内容为空校验 | `content.trim().isBlank()` | `isError` + "知识点内容不能为空" | 输入即清除 | `MemoEditDialog` |
| S10 | 删除确认 | 点击删除 | AlertDialog 级联说明 | 确认/取消 | 详情页已有，列表页**建议补** |
| S11 | 删除成功 | `isDeleted` | Toast "知识点已删除" + 自动返回 | — | `MemoDetailScreen.LaunchedEffect` |
| S12 | 无网络 / 权限异常 | —— | **不适用**（全本地、免通知权限） | — | 无 |
| S13 | 数据库异常 | DAO 抛异常 | 错误态页：⚠ + "数据读取失败" + [重试]（重试=重新订阅 Flow） | 重试 | 建议补 `try/catch` + 错误态 |
| S14 | 笔记未保存离开 | 编辑态直接提交评级 | 自动保存草稿 + Toast "笔记已自动保存" | — | 建议增强 |
| S15 | 上限边界 | Slider 拖到 0 或 50 | 0 → 红色停用文案；50 → 提示"每日最多 50 条" | — | `SettingsScreen` |

### 7.4 空状态与异常状态设计原则

1. **每个空状态必须带一个可执行 CTA**（新增 / 清空筛选 / 去设置 / 重试），杜绝"死胡同"。
2. **文案不制造焦虑**：禁用"逾期/失败/落后"，统一用"顺延/待复习/继续保持"。
3. **空状态视觉**：`outline` 色 72dp 线性图标 + `bodyLarge` 主文案 + `bodySmall` 说明 + 次级按钮；整体垂直居中，左右留 32dp。
4. **异常态必须可恢复**：提供 [重试] 或在数据变化后自动恢复（Flow 天然满足）。
5. **空状态不遮挡主功能**：TopAppBar、底部导航、FAB 在空状态下保持可用。

---

## 8. 响应式适配方案

### 8.1 断点定义（Material 3 WindowSizeClass）

| 类别 | 宽度 | 代表设备 | 导航 | 布局 |
|---|---|---|---|---|
| **Compact** | < 600dp | 手机竖屏 | NavigationBar（底部 3 项） | 单列；内容满宽，左右 16dp |
| **Medium** | 600–839dp | 折叠屏展开、小平板竖屏 | NavigationRail（80dp） | 列表 2 列 Grid；卡片最大宽 600dp 居中 |
| **Expanded** | ≥ 840dp | 平板横屏、Android 16 桌面窗口化 | 永久 Drawer 240dp / Rail | **List–Detail 双窗格**；卡片最大宽 720dp 居中；设置页 2 列 |

### 8.2 各页面响应式规则

**知识库**
- Compact：单列 `LazyColumn`，Banner / 搜索 / 标签 / 列表纵向堆叠
- Medium：`LazyVerticalGrid(GridCells.Adaptive(320.dp))` 2 列；搜索栏与标签栏限宽 600dp 居中
- Expanded：`ListDetailPaneScaffold`——左 Pane（360dp）列表，右 Pane 详情；选中项高亮；窄屏回退为下钻页
- 折叠屏（`FoldingFeature`）：`isSeparating` 时按铰链位置分栏，列表在左、详情在右；避免卡片横跨铰链

**复习页**（核心沉浸体验，宽屏不得拉伸变形）
- Compact：卡片满宽（左右 16dp），按钮区贴底
- Medium：卡片限宽 600dp 居中，按钮区同样限宽
- Expanded：卡片限宽 720dp 居中；按钮区限宽 720dp；进度条随内容限宽
- 横屏 Compact（如 800×360）：**双列布局**——左半卡片内容，右半笔记/按钮区，避免竖向空间不足

**设置页**
- Compact：单列滚动
- ≥600dp：滑块卡与规则卡并排 2 列（各占 1f）

**弹窗**
- Compact：AlertDialog 默认宽度（左右 48dp 边距）
- ≥600dp：Dialog 限宽 480dp 居中；`MemoEditDialog` 内容超长时内部滚动（`verticalScroll` 已实现）

### 8.3 其他适配维度

| 维度 | 规则 |
|---|---|
| **字体缩放** | 全量使用 `sp`；支持系统 0.85×–2.0×；在 1.3× 下复核不截断；复习按钮改为 `autoSize` 或最多 2 行换行 |
| **触控目标** | 所有可点元素 ≥48×48dp；Chip 高度 32dp 但父级 padding 补足；评级按钮高 ≥48dp |
| **屏幕旋转** | 允许旋转；`ReviewUiState` 由 ViewModel 持有，旋转不丢进度；`rememberSaveable` 保存草稿笔记 |
| **深色模式** | `EbbinghausTheme(darkTheme = isSystemInDarkTheme())`；评级色在深色下保持原值（已验证对比度 ≥4.5:1） |
| **动态取色** | `dynamicColor = false`（保持品牌色一致性），预留设置开关 |
| **Edge-to-Edge** | Android 15+ 强制执行；`Scaffold` 处理 insets；FAB 加 `navigationBarsPadding()` |
| **横屏/多窗口** | Android 16 桌面窗口化下窗口可自由缩放，`WindowSizeClass` 实时重算布局 |
| **无障碍** | 所有 Icon 有 `contentDescription`；评级按钮补充"后果说明"的语义文案；对比度 ≥4.5:1；支持 TalkBack 焦点顺序（TopAppBar → 内容 → 底部按钮） |
| **最小宽度资源** | 圆角/边距放 `dimens.xml`，提供 `values-sw600dp` / `values-sw840dp` 覆盖 |

---

## 9. 落地实施清单（对应现有代码）

| 优先级 | 事项 | 文件 | 说明 |
|---|---|---|---|
| P0 | 底部 NavigationBar + 复习 Badge | `AppNavigation.kt` / 新增 `AppScaffold.kt` | Tab 切换状态保存 |
| P0 | 列表页删除二次确认 | `MemoListScreen.kt` | 复用详情页 AlertDialog 样式 |
| P0 | 空/异常态组件化 | 新增 `ui/component/EmptyState.kt` `ErrorState.kt` `LoadingSkeleton.kt` | 三处复用 |
| P0 | Edge-to-Edge + insets | `MainActivity.kt` | `enableEdgeToEdge()` |
| P1 | Snackbar 统一通道 | 新增 `SnackbarHost` 于 Scaffold | 替代部分 Toast |
| P1 | 搜索防抖 + 关键词高亮 | `MemoListViewModel.kt` | `debounce(300)` |
| P1 | WindowSizeClass 适配 | 新增 `ui/util/WindowSizeClass.kt` | `calculateWindowSizeClass` |
| P1 | Expanded 双窗格 | 新增 `ListDetailPaneScaffold` | material3-adaptive |
| P2 | 复习按钮无障碍语义 | `ReviewScreen.kt` | 补充后果说明 |
| P2 | 笔记自动保存 | `ReviewViewModel.kt` | 提交评级时保存草稿 |
| P2 | 设置页外观/数据分组 | `SettingsScreen.kt` | v1.1 |
| P2 | 完成页展示顺延量 | `ReviewCompletedView` | 复用 `deferredCount` |

---

**验收口径**：所有新增界面必须同时提供 Compact / Medium / Expanded 三档预览，以及正常 / 加载 / 空 / 错误 / 停用 五态走查截图。
