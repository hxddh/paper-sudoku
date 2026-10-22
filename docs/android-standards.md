# Android 开发规范 / Checklist — v4.0

> 基线：`targetSdk 36`、`compileSdk 37`、`minSdk 26`，Jetpack Compose + Material 3 Expressive
> （`material3 1.5.0-beta01`）、Material 3 Adaptive 1.1、Navigation 3、Room 2.8、DataStore、Glance。
>
> 依赖说明：Expressive 公共 API 目前在 1.5 beta。发布前跟踪 1.5 stable；若仍未发布，需要在
> “固定 beta”与“等待 stable”之间做一次明确决策并记录在 release notes 中。

## 0. 不可妥协

- [x] `targetSdk = 36`（Play 2026-08-31 起要求）、`compileSdk = 37`、`minSdk = 26`
- [x] 单 Activity + Compose；`enableEdgeToEdge()`；不绘制假系统栏
- [x] `enableOnBackInvokedCallback="true"`；预测性返回：Navigation 3、list-detail 窗格、教练批注随手势缩放
- [x] 不锁方向、不禁止 resize；布局按窗口尺寸而非设备类型决定
- [x] SplashScreen API（`core-splashscreen`），启动画面停留到主题设置读出，深色用户无纸色闪烁
- [x] R8 + 资源压缩的 release 构建；kotlinx.serialization 自带 R8 规则
- [x] 无 `INTERNET` 权限；Android 备份包含 Room 与 DataStore
- [ ] Play Data Safety、内容分级、隐私政策托管（文案已更新，提交待 owner）

## 1. 触控与视觉

| 项 | 规则 | 当前 |
|---|---|---|
| 最小触控目标 | 48 × 48dp | 浮动工具栏 64dp 高；数字键 56dp 高、≥ 48dp 宽（含已填满的键）；状态行按钮 48dp；6×6 单元格 ≈ 61dp |
| 9×9 单元格 | 48dp | 手机竖屏 ≈ 42dp；按住滑动瞄准 + 放大镜让落点与手指大小无关；另有拿起数字、键盘、宽屏布局 |
| 颜色角色 | 无基线紫色回退 | Paper / Night 定义全部 M3 角色（含 `surfaceContainer*`） |
| 对比度 | 正文 4.5:1、图形 3:1 | 所有文字角色 ≥ 4.5:1；格线 3.07:1（Paper）/ 3.45:1（Night），宫线 5.9:1 / 6.9:1 |
| 不只用颜色表意 | 冲突、错误、Coach | 冲突/错误加下划线 + 语义；Coach 步骤用实心/空心圆点并朗读“第 n 步” |
| 字体 | sp；200% 不截断 | UI 文本 sp；顶栏、状态行、工作区随字号增高；棋盘数字与印章文字随图形大小缩放 |
| 动效 | 不承载关键信息 | 落笔、印章、面板过渡均为装饰性 |

## 2. 原生组件映射

| UI | 使用 |
|---|---|
| 导航 | Navigation 3 `NavDisplay`（本子 / 设置） |
| 本子与页面 | `NavigableListDetailPaneScaffold` + `AnimatedPane` + 共享元素 |
| 本子 | `LargeTopAppBar`（折叠）、`Surface` 卡片、`ListItem` |
| 页面顶栏 | 透明小 `TopAppBar` |
| 工具栏 | `HorizontalFloatingToolbar`、`FilledIconToggleButton`、`FilledIconButton` |
| 数字键盘 | `ButtonGroup` 连接式按键 |
| 小组件 | Glance `GlanceAppWidget`（响应式尺寸、`appWidgetBackground`、系统圆角、`previewLayout`） |
| 确认 | `AlertDialog`（重开、换一页练习） |
| 加载 | Expressive `LoadingIndicator` |
| 设置 | `ListItem` + `Switch`、`SingleChoiceSegmentedButtonRow`、系统按应用语言设置；触感跟随系统 |
| 打印 / 分享 | 系统 `PrintManager` / Sharesheet |

## 3. 无障碍与输入

- [x] 棋盘 `CollectionInfo`；单元格 `CollectionItemInfo(row, 1, col, 1)`、`Role.Button`、`selected`
- [x] 每格一个节点（`clearAndSetSemantics`）：行列、数字或笔记、冲突、教练建议/排除；点击动作 + Erase 自定义动作（滑动瞄准不影响无障碍点击）
- [x] Coach 批注 `liveRegion = Polite`；标题 `heading()`；完成提示 `liveRegion` + `heading()`
- [x] 双窗格本子：选中项 `selected`；窗格 `paneTitle`
- [x] 键盘：方向键（仅棋盘获焦时）、1–9（始终写入）、Backspace、Ctrl+Z、N、H、Esc；键盘模式显示焦点框；系统快捷键帮助（Meta + /）
- [x] 被系统接管的触摸（边缘返回、防误触）不写入；棋盘排除系统返回手势
- [x] 中英双语，`localeConfig` 支持按应用切换语言
- [ ] TalkBack / Switch Access / Voice Access 真机手测
- [ ] 200% 字体、显示大小、色盲模拟真机手测

## 4. 自适应

- [x] Material 3 adaptive list-detail：窄屏单窗格，宽屏本子与页面并排
- [x] 页面内：宽 > 高 且 ≥ 560dp 时棋盘 + 侧边工作区；否则上下排列
- [x] 页面、本子、设置均处理 `safeDrawing` 水平 insets（横屏刘海）
- [x] UI 测试覆盖宽布局
- [ ] 折叠屏桌面模式、多窗口、ChromeOS 窗口缩放真机验证

## 5. 架构与数据

- [x] `:core:engine`（技巧、求解、生成、评级）与 `:core:game`（可序列化 `GameState`）为纯 Kotlin
- [x] 单向数据流：`PageViewModel` → `StateFlow<PageUiState>`，一次性效果走 `Channel`；打开的页面存于 `SavedStateHandle`
- [x] 每步自动保存（Room `saved_games`，JSON）；进程死亡、划掉、重启、升级均可恢复
- [x] Room schema 导出到 `app/schemas`；v1 → v2 → v3 迁移与存储查询有仪器测试
- [x] 本子读取投影列（`givens`/`grid`），不解码整局 JSON；JSON 编解码在 `Dispatchers.Default`
- [x] 完成的保存与记录在同一事务
- [x] 计时使用单调时钟，仅前台且未暂停时计时
- [x] 题目生成在 `Dispatchers.Default`；明日每日题与下一页练习预先生成并缓存（`cacheDir`）
- [x] DataStore `ReplaceFileCorruptionHandler`
- [x] 手写 `AppContainer`（规模不需要 Hilt）

## 6. 测试与 CI

- [x] 引擎：几何、求解唯一性、生成确定性与评级、每一步逻辑推导与答案一致、X-Wing 定式
- [x] `GameState`：输入、笔记整理、撤销、锁定、完成冻结、JSON 往返与篡改拒绝
- [x] ViewModel：自动保存恢复、计时、只记一次、Coach 三级、错误提示、经典续玩、启动落到今天
- [x] Compose UI：棋盘语义、事件、Coach、完成页、暂停、宽布局；`AccessibilityChecks`
- [x] Room 迁移仪器测试
- [x] CI：JVM 测试、debug/release 构建、lint、schema 已提交检查、API 36 模拟器仪器测试
- [x] 截图矩阵（Paper/Night × 页面状态 × 宽屏 × 200% 字号）与已提交的基准图逐张比对
- [x] 每日题与练习页固定样例、3.0 存档格式样例
- [x] Macrobenchmark：冷启动到今日页、书写帧时间（CI 模拟器上跑，数据仅看趋势）
- [x] Baseline Profile（手写规则 + profileinstaller），CI 检查已打进 release APK；`ReportDrawnWhen` 标记完全绘制
- [ ] 真机冷启动 < 400ms 验证（模拟器数据不代表真机）

## 7. 隐私

- [x] 无网络、无账号、无广告、无统计
- [x] 分享为纯文本、无链接；打印走系统打印服务
- [x] 备份规则只包含数据库和设置
