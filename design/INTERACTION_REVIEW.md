# 交互逻辑审计报告

**日期**：2026-09-15
**范围**：`app/src/main/java/com/ebbinghaus/memo/ui/` 全量交互面（列表 / 复习 / 详情 / 设置 / 回收站 / 编辑器 / 导航壳）
**方法**：逐文件实读 + 全项目 grep 交叉验证，所有结论附 `文件:行号`
**结论**：**需要优化**。发现 **4 项 P0 功能性缺陷**（Expanded 断点交互入口整体丢失）、**5 项 P1 交互缺陷**、**10 项 P2 体验摩擦**。

---

## 一、结论摘要

| 严重度 | 数量 | 性质 |
|---|---|---|
| 🔴 **P0** | 4 | **Expanded（≥840dp）断点下新增/排序/多选退出/选择提示全部失效** —— 平板横屏与桌面窗口化场景 |
| 🟠 **P1** | 5 | 无障碍文案与算法不符、多选选择集被重置、重复提示、失败无反馈、误触丢草稿 |
| 🟡 **P2** | 10 | 删除无撤销、回收站不可预览/无批量、标签 Chip 死区、6 词上限无提示等 |

---

## 二、🔴 P0：Expanded 断点交互入口整体丢失（同一个根因）

### 根因

`MemoListScreen`（**含 Scaffold / TopAppBar / FAB / 排序弹窗 / BackHandler**）与 `MemoListContent`（**纯内容，无上述任何一项**）职责切分后，`AppNavigation` 只在**非 Expanded** 分支使用前者：

```kotlin
// AppNavigation.kt:265-299
composable(route = Screen.MemoList.route) {
    if (windowSizeClass.useListDetail) {          // ← Expanded (≥840dp)
        MemoListDetailPane(...)                   // → 内部只用 MemoListContent
    } else {
        MemoListScreen(...)                       // ← 只有这里有 TopAppBar/FAB/排序/BackHandler
    }
}
```

而 `useListDetail` 仅在 `widthSizeClass == Expanded` 时为真（`WindowSizeClass.kt:62-63`）。

### 全项目 grep 证据（各只有 1 处，且都在 `MemoListScreen`）

| 能力 | 唯一位置 | Expanded 下是否可达 |
|---|---|---|
| `FloatingActionButton(` | `MemoListScreen.kt:163` | ❌ |
| 排序入口 `showSortSheet` | `MemoListScreen.kt:112,148,190` | ❌ |
| `BackHandler(` | `MemoListScreen.kt:115` | ❌ |
| 多选标题「已选 N 条」 | `MemoListScreen.kt:126` | ❌ |

### 四项缺陷

| # | 缺陷 | 影响 |
|---|---|---|
| **P0-1** | **Expanded 下无法新增知识点** | 平板横屏 / 桌面窗口 ≥840dp 时，全应用**没有任何新增入口**。这是功能缺失，不是体验瑕疵。 |
| **P0-2** | **Expanded 下无法排序** | E10 排序在平板/桌面场景完全不可用，与 PRD「三档共享同一排序值」的目标相悖。 |
| **P0-3** | **Expanded 下返回键无法退出多选态** | 多选态按返回键会**退出列表页/退出 App**，选择丢失。PRD §4.5 明确要求多选态作用于 Expanded 左 Pane。 |
| **P0-4** | **Expanded 下无「已选 N 条」上下文** | 用户进入多选后，左 Pane 无任何选中数量提示（底部操作栏有，但顶部无锚点）。 |

> 注：P0-1/P0-2 在 **Medium（600–839dp）** 下不受影响 —— Medium 走 `useListDetail = false` 分支，仍使用 `MemoListScreen`。

### 修复建议

1. **P0-3（最省事）**：把 `BackHandler` 从 `MemoListScreen` 上提到 `AppNavigation` —— 那里已持有 `memoListState.isSelectionMode` 与 `memoListViewModel`，一处改动同时覆盖单列与双窗格两条分支。
2. **P0-1/P0-2/P0-4**：抽出共享的 `MemoListTopBar`（含标题 / 多选态切换 / 排序入口）与 FAB，让 `MemoListDetailPane` 的左 Pane 也渲染；或将 `MemoListScreen` 的 `Scaffold` 部分一并下沉到 `MemoListContent` 的宿主层，使两条分支共用同一套页面级入口。

---

## 三、🟠 P1：明确交互缺陷

### P1-1 「忘记」评级无障碍文案与算法不符（误导 TalkBack 用户）

```kotlin
// ReviewScreen.kt:66
ReviewRating.FORGET to "忘记：档位回到第 1 档，明天重新复习",
```

实际规则（`ORIGINAL_REQUEST.md` §R2）是「**回退 1 档**，若已是第 1 档则保持第 1 档」。从第 5 档点「忘记」应回到第 4 档，而文案说「回到第 1 档」。该文案同时作为 `contentDescription` 下发（`ReviewScreen.kt:318`），会**系统性误导屏幕阅读器用户**。
**修复**：改为「忘记：档位回退一档，间隔缩短后再复习」。

### P1-2 多选态下长按会重置整个选择集

```kotlin
// MemoListScreen.kt:628-631
combinedClickable(
    onClick = { if (isSelectionMode) onToggleCheck() else onClick() },
    onLongClick = onLongClick          // ← 多选态下仍无条件触发
)
// MemoListViewModel.kt:387-394
is MemoListUiEvent.OnEnterSelectionMode -> {
    _uiState.update { it.copy(isSelectionMode = true, selectedIds = setOf(event.id)) }  // ← 重置
}
```
多选态下长按第 3 条，前两条的选择**全部丢失**，且无任何提示。
**修复**：`onLongClick = { if (isSelectionMode) onToggleCheck() else onLongClick() }`。

### P1-3 「已选 N 条」在 Compact 下重复显示两处

`MemoListScreen.kt:126`（TopAppBar 标题）与 `SelectionActionBar.kt:66`（底部操作栏）同时渲染同一信息。信息冗余，且两处样式不一致（`titleLarge` vs `titleMedium`）。
**修复**：多选态下 TopAppBar 只保留「退出」按钮，数量交给操作栏；或反之。

### P1-4 批量操作失败无任何反馈，且状态已不一致

```kotlin
// MemoListViewModel.kt:437-454（OnConfirmBatchDelete）
_uiState.update { it.copy(isBatchDeleteDialogVisible = false,
                          isSelectionMode = false, selectedIds = emptySet()) }   // ← 先改状态
viewModelScope.launch {
    memoRepository.softDeleteMemos(ids)     // ← 无 try/catch
    _effect.emit(MemoListEffect.ShowSnackbar("已将 ${ids.size} 条移入回收站，可随时还原"))
}
```
`MemoRepositoryImpl` 全文件**无 try/catch**（仅 `withTransaction`）。一旦落库失败：
- UI 已退出多选态、已清空选择 → 用户以为删成功了；
- 异常在 `viewModelScope.launch` 中未被捕获 → 无提示，甚至崩溃。

`OnBatchAddTag`（`MemoListViewModel.kt:465-483`）存在完全相同的问题。
**修复**：`runCatching` 包裹 + 失败时回滚 UI 状态并提示。

### P1-5 编辑器关闭无「未保存」确认，长文本误触即丢

```kotlin
// MemoEditDialog.kt:96-100
properties = DialogProperties(usePlatformDefaultWidth = false,
                              dismissOnBackPress = true, dismissOnClickOutside = false)
```
正文框最小高度 240dp、支持 Markdown/LaTeX 长文。误点 ✕ 或按返回键，**内容直接丢弃且无草稿**（对比：复习页的笔记编辑已有 `shouldAutoSaveNotes` 自动保存机制，两处标准不一致）。
**修复**：`content`/`notes` 有改动时，关闭前弹「放弃编辑？」确认。

---

## 四、🟡 P2：体验摩擦与一致性问题

| # | 问题 | 证据 | 建议 |
|---|---|---|---|
| **P2-1** | **删除后无「撤销」动作** | 全项目 `Snackbar` **无 `actionLabel`** 使用；仅 `EmptyState` 有同名参数 | 删除 Snackbar 加「撤销」按钮（复用软删除能力，成本极低）；当前回收站入口在「设置 → 数据与安全 → 回收站」三层深处，还原路径过长 |
| **P2-2** | **回收站条目不可点开预览** | `TrashScreen.kt` 卡片无 `clickable`，仅 3 行摘要 | 内容相似时无法判断该还原哪条；建议点击展开完整内容 + 笔记 |
| **P2-3** | **回收站无批量还原 / 批量彻底删除** | `TrashScreen.kt` 仅单条 `TextButton` 还原 | 与刚上线的列表页 E11 批量能力不一致；清空只能「全清」 |
| **P2-4** | **回收站无搜索 / 排序** | `TrashScreen.kt` 无搜索栏 | 条目多时难定位 |
| **P2-5** | **卡片标签 Chip 可点但无响应** | `MemoListScreen.kt:722` `SuggestionChip(onClick = {})` | 有涟漪却无动作，属交互死区；建议点击即按该标签筛选（用户强预期） |
| **P2-6** | **搜索 6 词上限无提示** | 仓储 `split("\\s+")` 取前 6 词，超出静默忽略；placeholder 仅说「空格切分多词」 | 超限时给轻提示，或改为无上限（注意 SQL 注入风险，需参数化） |
| **P2-7** | **「全选」语义模糊** | `MemoListViewModel.kt:409` 只选 `current.memos`（当前筛选结果） | 文案改为「全选当前结果」更准确 |
| **P2-8** | **标签输入交互不一致** | 编辑器为纯文本逗号分隔（`MemoEditDialog.kt:80`）；批量打标签为候选选择器（`TagPickerDialog`） | 编辑器复用 `TagPickerDialog` 的候选能力 |
| **P2-9** | **`dailyLimit = 0` 时复习页仍显示「🎉 今日复习全部达成！」** | `ReviewScreen.kt:154` `isCompleted \|\| reviewQueue.isEmpty()` | 上限为 0 的语义是「今日不复习」，显示「达成」略有误导 |
| **P2-10** | **搜索关键词切分口径不一致** | UI `split(" ", "　")`（`MemoListScreen.kt:264`）vs 仓储 `split("\\s+")` | 含换行/Tab 时**高亮结果与筛选结果可能不一致**；建议统一为 `\\s+` |

---

## 五、增强建议（按投入产出比排序）

| 优先级 | 增强 | 理由 |
|---|---|---|
| ⭐⭐⭐ | **删除 Snackbar 加「撤销」** | 复用现有软删除 + Snackbar 通道，**几行代码**；把「还原」从三层深的回收站提到即时可达 |
| ⭐⭐⭐ | **修复 P0-3 返回键** | 一处 `BackHandler` 上提，修复平板/桌面最刺眼的问题 |
| ⭐⭐ | **长按多选改为「长按切换勾选」** | 修 P1-2，同时让多选态手势语义自洽 |
| ⭐⭐ | **回收站支持点击预览 + 多选批量** | 补齐与列表页的能力对称性 |
| ⭐⭐ | **编辑器草稿保护** | 与复习页笔记自动保存标准对齐 |
| ⭐ | **统一「操作失败」反馈通道** | 目前只有成功路径有 Snackbar；建议所有异步写操作统一 `runCatching` + 失败提示 |
| ⭐ | **首次进入时的一次性引导** | 多选（长按）、排序（右上角）、回收站（设置内）三处均无发现性提示 |

---

## 六、建议修复顺序

1. **第一批（当天可完成，全部是「改一行/加一处」）**
   `P0-3` 返回键上提 · `P1-2` 长按语义 · `P1-1` 无障碍文案 · `P2-1` 撤销按钮
2. **第二批（半个迭代）**
   `P0-1/P0-2/P0-4` 抽共享 TopBar + FAB 下沉 · `P1-4` 失败反馈 · `P1-5` 草稿保护 · `P1-3` 去重
3. **第三批（一个迭代）**
   `P2-2/P2-3/P2-4` 回收站能力补齐 · `P2-5` 标签 Chip · `P2-8` 标签输入统一 · `P2-10` 切分口径统一

---

## 七、已核实「无问题」的项（避免误报）

- `DeleteConfirmDialog` 对空预览有兜底（`DeleteConfirmDialog.kt:52-56`），批量删除传空 `memoPreview` 不会渲染空白行
- `MemoEditDialog` 有空内容校验与错误态（`MemoEditDialog.kt:76-78, 170-178`）
- `SelectionActionBar` 按钮有 `enabled` 态与禁用配色（`SelectionActionBar.kt:75,82,115-134`）
- `DataSafetySectionHost` 导入有预览弹窗 + 全量覆盖警示（`DataSafetySection.kt:121-142`），导出/导入/快照目录三个 SAF 启动器接线正确
- `TrashScreen` 有「复习进度将一并还原」提示（`TrashScreen.kt:115-130`），剩余天数用 `outline` 而非 error 红
- `AppScaffold` 的 Rail 断点下多选操作栏置于内容区顶部（`AppScaffold.kt:89-96`），逻辑正确
- 全项目**无 TODO/FIXME**；`LazyColumn`/`LazyVerticalGrid` 均已带 `key`
