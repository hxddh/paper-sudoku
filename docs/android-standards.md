# Android 开发规范 / Checklist

> 适用：Paper Sudoku（Android）  
> 基线：`targetSdk 36`、`compileSdk 37`、`minSdk 26`、Jetpack Compose + Material 3 Expressive  
> 目标：先把“平台合规”和“可访问”做对，再加内容/变现。
>
> 依赖说明：Material 3 的公开 Expressive 组件目前只在 `1.5.0-beta01` 中提供；1.4 稳定版仍把
> 这些 API 标为 internal。发布前跟踪 1.5 stable，若仍未发布，需要在“固定 beta”与“等待 stable”
> 之间做一次明确决策，不能把 beta 当成长期稳定依赖。

## 0. 不可妥协

- [x] `targetSdk = 36`（Play 2026-08-31 起要求）、`compileSdk = 37`
- [x] Material 3 Expressive：`MaterialExpressiveTheme` + expressive motion；`material3 1.5.0-beta01`
- [x] `minSdk = 26`（Android 8+，`java.time` 可用）
- [x] 单 Activity + Compose；`enableEdgeToEdge()`
- [x] `android:enableOnBackInvokedCallback="true"`；不依赖 `onBackPressed`
- [x] 不锁 orientation、不设 `resizeableActivity=false`
- [x] 不绘制假状态栏 / 假 Home indicator；使用真实系统栏 + insets
- [x] R8 + resource shrinking 的 release 构建跑通（`assembleRelease` 已验证）
- [ ] Play target/API、Data Safety、内容分级、隐私政策（文案已准备，Play 提交待做）
- [x] `versionCode` / 签名配置 / App Bundle 构建路径；（正式签名与分阶段发布待 owner）

## 1. 触控与视觉

| 项 | 规则 | 当前 |
|---|---|---|
| 最小触控目标 | 48 × 48dp | dock/keypad/menu 已按 48–52dp；9×9 单元格 43.3dp 是例外 |
| 6×6 单元格 | 390 / 6 = 65dp | 合规 |
| 9×9 单元格 | 390 / 9 = 43.3dp；360 / 9 = 40dp | **不合规**；仅平板（600 / 9 = 66.7dp）合规 |
| 间距 | 8dp 系统；屏幕边 16dp | 采用 |
| 颜色对比 | 正文 4.5:1；大字号 3:1；图形 3:1 | Paper accent `#A8442F` 5.19:1；muted `#5F574C` 6.2:1 |
| 不用颜色单独表意 | given/user、冲突、提示都要有第二线索 | 冲突用 error 色 + 语义；given/user 仍需加字重/下划线/图例 |
| 字体缩放 | UI 文案 `sp`；200% 不截断 | 棋盘数字按格子大小缩放，另设“大数字” |
| 动效 | 尊重系统“移除动画” | 使用 Compose 默认动画；不承载关键信息 |

**9×9 的硬结论**：在 390dp 手机上，9×9 无法同时满足“完整显示”和 48dp。  
处理方式（三选一）：

1. 手机主界面用 6×6；9×9 只作为平板/经典模式。
2. 9×9 提供键盘、行列选择器、放大镜等替代输入，并明确不宣称“完全无障碍”。
3. 接受 43.3dp 例外，但在设计文档和测试里记录；不要用 Compose 的自动触控扩展（会与相邻格重叠）。

## 2. 原生组件映射

| UI | 使用 | 不要 |
|---|---|---|
| 页面骨架 | `Scaffold` / `BottomSheetScaffold` | 自绘页面容器 |
| 顶部栏 | `TopAppBar`（small） | 自绘状态栏/假 9:41 |
| 棋盘 | 自定义 `Layout` + `Canvas` 画线 + `Text` 放数字 | `LazyVerticalGrid`、Canvas 画字 |
| 单元格 | `Modifier.clickable` + `semantics` + `collectionItemInfo` | 只画不语义化 |
| 数字键盘 | `ButtonGroup` + `FilledTonalButton` | 自绘按钮 |
| 键盘/Coach 面板 | `BottomSheetScaffold` 的 standard sheet | 自绘浮层 |
| 底部动作 | 圆角 `Surface` + `TextButton` / `Button` | `NavigationBar`（它是目的地导航） |
| 菜单 | `IconButton` + `DropdownMenu` | 自绘 `···` |
| 重置确认 | `AlertDialog` | 自绘弹窗 |
| 分享 | 系统 Sharesheet | 自建分享面板 |
| 评分 | Play `ReviewManager` | 自建五星弹窗 |

**原则**：除了棋盘，全部用 Material 3 组件；棋盘是唯一合理的自定义绘制区域。  
棋盘线用 `Canvas`；数字用 `Text`，这样 TalkBack 能读到。

## 3. 无障碍与输入

- [x] 棋盘容器：`CollectionInfo(size, size)`
- [x] 单元格：`CollectionItemInfo(row, column, 1, 1)`、`Role.Button`、`contentDescription`、`selected`
- [x] 数字用 `Text`，不是 Canvas 文本
- [x] 读屏文案中英双语；`values-zh-rCN`
- [x] 单元格自定义动作：Notes / Erase / Coach
- [x] 键盘：方向键移动、1–9 输入、Backspace 擦除、N 笔记、H 提示
- [ ] 可见焦点环；外接键盘/Chromebook 设备手测
- [ ] TalkBack 手测：6×6、9×9、Coach、Solved
- [ ] Switch Access / Voice Access
- [ ] 200% 字体、显示大小、深色、色盲/高对比
- [ ] 冲突用“图标 + 文案 + 语义”，不只红色
- [ ] Reduce motion / 触感开关

## 4. 返回、Insets、系统栏

- [x] `enableEdgeToEdge`；`targetSdk 36` 不 opt-out
- [x] `TopAppBar` 处理状态栏；sheet 内容 `navigationBarsPadding()`；Mini peek = 256dp + 导航栏高度（键盘常驻），Classic peek = 64dp
- [x] 不画假状态栏/Home indicator
- [x] 主题切换同步系统栏图标明暗（`WindowCompat` side effect）
- [x] 返回键：sheet 展开时先收起；再返回退出
- [ ] `PredictiveBackHandler` 驱动自定义 sheet 动画（当前用 `BackHandler`）
- [ ] 三键导航条对比度 / 导航栏图标明暗
- [ ] 横屏 display cutout insets
- [ ] 多窗口/分屏恢复
- [x] `SavedStateHandle` 恢复棋盘/笔记/选中格/模式/计时起点（设备级验证待做）

## 5. 自适应 / 大屏

- [x] `WindowSizeClass`（Material3 `calculateWindowSizeClass`）
- [x] `Expanded`（≥840dp）：棋盘 + 360dp 侧栏（Dock / Keypad / Coach）
- [ ] `Medium`（600–839dp）：目前仍用底部 sheet；避免 360dp 侧栏把棋盘压到 40dp 以下
- [ ] Android 16 大屏：不锁 orientation、不禁止 resize（≥600dp 限制会被忽略）
- [ ] 平板：棋盘 600–640dp；9×9 单元格 ≥66dp
- [ ] 折叠屏 tabletop / hinge（v1.1）
- [ ] 手机 360dp / 390dp / 430dp 均不裁切
- [ ] 横屏：棋盘与侧栏的 `maxHeight` 约束需在设备上验证

## 6. 架构与数据

- [x] `:core:engine` 纯 Kotlin，无 Android 依赖
- [x] `GameViewModel` + `StateFlow<GameUiState>`；UI 只发事件
- [x] 不可变 `CellUi` / `GameUiState`
- [x] 引擎：候选数、naked single、hidden single、冲突、完成判定
- [x] `SavedStateHandle` 保存当前局（棋盘、笔记、选中格、模式、计时起点、已记录标记）
- [x] Room 2.8.5 记录完成局；DataStore 保存主题
- [ ] 进程死亡恢复的设备级验证（当前只有 JVM `SavedStateHandle` 单测）
- [ ] undo history 持久化（当前只在内存中，上限 100）
- [x] 题库校验：唯一解 + 只用 naked/hidden single 可解（`PuzzleBankTest`）
- [x] 每日题：61 个 6×6 题 + `Clock` 注入 + 本地日期轮换
- [x] 设置：主题（触感/大数字/自动清笔记未做）
- [x] 统计：完成数、最佳时间（本地 Room；无 streak）
- [x] 计时：TopAppBar 实时计时，完成页显示实际用时
- [ ] 模块化：`app` / `core:engine` / `core:data` / `core:designsystem` / `feature:game`
- [ ] DI：模块少用手写 `AppContainer`；团队/模块多了再 Hilt
- [ ] 导航：Navigation Compose type-safe routes（Home/Game/Stats/Settings）
- [x] `collectAsStateWithLifecycle`
- [ ] 引擎运算离开主线程（6×6/9×9 当前数据量很小；接入更大题库/分析时再迁移）

## 7. 测试与 CI

- [x] 引擎单测（6 个）：候选、naked single、hidden single、完成、冲突
- [x] ViewModel 单测（9 个）：选格、填数、笔记/撤销、Coach/Place、模式切换、每日轮换、Classic 下一题、SavedStateHandle 恢复、完成只记一次
- [x] 题库单测（2 个）：全部唯一解 + 只用两种技巧可解 + 日期轮换
- [x] Compose UI 测试（4 个）：棋盘语义、选格/键盘/Coach 事件、Stats、Expanded 双栏
- [x] `AccessibilityChecks.enable()` 已接入 UI 测试
- [x] Compose UI/无障碍测试实际执行：GitHub Android 16 / API 36、手势导航 emulator 上 4/4 通过；本地 orb 无 KVM，只做编译
- [ ] 截图测试：light/dark、6×6/9×9、Coach、Solved
- [ ] TalkBack 手测
- [ ] Macrobenchmark：启动、棋盘交互
- [ ] Baseline Profile
- [x] `lintDebug` 无 Error（保留版本提示/`ObsoleteSdkInt` 警告）
- [x] GitHub Actions workflow（build/test/lint + Android 16 / API 36 instrumentation job）
- [x] 题库 validator（JVM `PuzzleBankTest`，CI 阻断）
- [ ] Play 预发布报告、内部测试、staged rollout

## 8. 隐私与变现

| 方案 | 权限 | 隐私 | 发现 |
|---|---|---|---|
| 买断 $4.99 | 无 `INTERNET` / `BILLING` | 最强，完全离线 | 较低 |
| 免费 + 一次性 IAP | `INTERNET` + `BILLING` | 仅 Billing 联网；无广告/分析 | 较高 |

- [x] v1：无 `INTERNET`、无广告、无分析、无 IAP
- [ ] 若未来做 IAP：Billing 9.x、query/acknowledge/restore、pending、license testers
- [ ] 若未来加通知：`POST_NOTIFICATIONS` + WorkManager；不用 exact alarm；不制造 streak 焦虑
- [x] 隐私政策草案 `PRIVACY.md`；Play Data Safety/内容分级待提交
- [ ] 字体/题库/图标许可证；不要复制 GPL 代码（如 Lantern Sudoku）

## 9. Definition of Done

- [x] `targetSdk 36` / `compileSdk 37`，Material 3 Expressive，edge-to-edge，无假系统栏
- [x] 6×6 手机主路径所有触控 ≥48dp
- [ ] 9×9 有替代输入或明确例外（当前明确 43.3dp 例外）
- [ ] TalkBack、200% 字体、键盘、深色、横屏/多窗口设备通过
- [x] 所有 sheet/menu/dialog 使用 Material 3 原生组件
- [x] 引擎 + ViewModel + 题库 + UI 无障碍测试全绿（4/4 instrumentation）
- [ ] 截图测试：light/dark、6×6/9×9、Coach、Solved
- [x] 题库 validator 在 CI 中阻断错误题
- [ ] Play 预发布报告无阻断项
- [ ] 隐私/Data Safety/内容分级在 Play Console 完成
