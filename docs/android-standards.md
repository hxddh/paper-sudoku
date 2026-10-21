# Android 开发规范 / Checklist — v3.0

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
- [x] SplashScreen API（`core-splashscreen`），纸张色启动，无白屏闪烁
- [x] R8 + 资源压缩的 release 构建；kotlinx.serialization 自带 R8 规则
- [x] 无 `INTERNET` 权限；Android 备份包含 Room 与 DataStore
- [ ] Play Data Safety、内容分级、隐私政策托管（文案已更新，提交待 owner）

## 1. 触控与视觉

| 项 | 规则 | 当前 |
|---|---|---|
| 最小触控目标 | 48 × 48dp | Dock ≥ 56dp 高；数字键 60dp 高；6×6 单元格 ≈ 61dp |
| 9×9 单元格 | 48dp | 手机竖屏 ≈ 42dp；按住滑动瞄准 + 放大镜让落点与手指大小无关；另有拿起数字、键盘、宽屏布局 |
| 颜色角色 | 无基线紫色回退 | Paper / Night 定义全部 M3 角色（含 `surfaceContainer*`） |
| 对比度 | 正文 4.5:1、图形 3:1 | primary 5.2:1、onSurfaceVariant 6.2:1（Paper） |
| 不只用颜色表意 | 冲突、错误、Coach | 冲突/错误加下划线 + 语义；Coach 有文字面板 |
| 字体 | sp；200% 不截断 | UI 文本 sp；棋盘数字随格子大小缩放 |
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
| 小组件 | Glance `GlanceAppWidget` |
| 确认 | `AlertDialog`（重开、换一页练习） |
| 加载 | Expressive `LoadingIndicator` |
| 设置 | `ListItem` + `Switch`、`SingleChoiceSegmentedButtonRow`、系统按应用语言设置 |
| 打印 / 分享 | 系统 `PrintManager` / Sharesheet |

## 3. 无障碍与输入

- [x] 棋盘 `CollectionInfo`；单元格 `CollectionItemInfo(row, 1, col, 1)`、`Role.Button`、`selected`
- [x] 单元格描述含笔记、冲突；点击动作 + 自定义动作 Notes / Erase / Coach（滑动瞄准不影响无障碍点击）
- [x] Coach 面板 `liveRegion = Polite`；标题 `heading()`
- [x] 键盘：方向键、1–9、Backspace、Ctrl+Z、N、H、Esc；系统快捷键帮助（Meta + /）
- [x] 中英双语，`localeConfig` 支持按应用切换语言
- [ ] TalkBack / Switch Access / Voice Access 真机手测
- [ ] 200% 字体、显示大小、色盲模拟真机手测

## 4. 自适应

- [x] Material 3 adaptive list-detail：窄屏单窗格，宽屏本子与页面并排
- [x] 页面内：宽 > 高 且 ≥ 560dp 时棋盘 + 侧边工作区；否则上下排列
- [x] 横屏 display cutout 水平 insets
- [x] UI 测试覆盖宽布局
- [ ] 折叠屏桌面模式、多窗口、ChromeOS 窗口缩放真机验证

## 5. 架构与数据

- [x] `:core:engine`（技巧、求解、生成、评级）与 `:core:game`（可序列化 `GameState`）为纯 Kotlin
- [x] 单向数据流：`GameViewModel` → `StateFlow<PlayUiState>`，一次性效果走 `Channel`
- [x] 每步自动保存（Room `saved_games`，JSON）；进程死亡、划掉、重启、升级均可恢复
- [x] Room schema 导出到 `app/schemas`；v1 → v2 迁移有仪器测试
- [x] 计时使用单调时钟，仅前台且未暂停时计时
- [x] 题目生成在 `Dispatchers.Default`
- [x] 手写 `AppContainer`（规模不需要 Hilt）

## 6. 测试与 CI

- [x] 引擎：几何、求解唯一性、生成确定性与评级、每一步逻辑推导与答案一致、X-Wing 定式
- [x] `GameState`：输入、笔记整理、撤销、锁定、完成冻结、JSON 往返与篡改拒绝
- [x] ViewModel：自动保存恢复、计时、只记一次、Coach 三级、错误提示、经典续玩、启动落到今天
- [x] Compose UI：棋盘语义、事件、Coach、完成页、暂停、宽布局；`AccessibilityChecks`
- [x] Room 迁移仪器测试
- [x] CI：JVM 测试、debug/release 构建、lint、schema 已提交检查、API 36 模拟器仪器测试
- [x] 截图矩阵（Paper/Night × 页面状态 × 宽屏 × 200% 字号），CI 上传
- [x] Macrobenchmark：冷启动到今日页、书写帧时间（CI 模拟器上跑，数据仅看趋势）
- [x] Baseline Profile（手写规则 + profileinstaller）
- [ ] 真机冷启动 < 400ms 验证（模拟器数据不代表真机）

## 7. 隐私

- [x] 无网络、无账号、无广告、无统计
- [x] 分享为纯文本、无链接；打印走系统打印服务
- [x] 备份规则只包含数据库和设置
