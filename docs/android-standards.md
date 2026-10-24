# Android 开发规范 / Checklist — v10.0

> 基线：`targetSdk 36`、`compileSdk 37`、`minSdk 26`，Jetpack Compose + Material 3 Expressive
> （`material3 1.5.0-beta01`）、Material 3 Adaptive 1.1、Navigation 3、Room 2.8、DataStore、Glance。
>
> 依赖说明：Expressive 公共 API 目前在 1.5 beta。发布前跟踪 1.5 stable；若仍未发布，需要在
> “固定 beta”与“等待 stable”之间做一次明确决策并记录在 release notes 中。

## 0. 不可妥协

- [x] `targetSdk = 36`（Play 2026-08-31 起要求）、`compileSdk = 37`、`minSdk = 26`
- [x] 单 Activity + Compose；`enableEdgeToEdge()`；不绘制假系统栏
- [x] `enableOnBackInvokedCallback="true"`；预测性返回：Navigation 3（离开的页缩到 90%、圆角、抬起，随手指线性，提交时淡出；每页不透明，无重影）、list-detail 窗格（棋盘与缩略图交叉淡化）、教练批注随手势缩放并从松手处淡出
- [x] 不锁方向、不禁止 resize；布局按窗口尺寸而非设备类型决定
- [x] SplashScreen API（`core-splashscreen`），启动画面停留到主题设置读出，深色用户无纸色闪烁（启动画面自身跟随系统主题：`setApplicationNightMode` 没有“跟随系统”的取值，故不使用）
- [x] R8 + 资源压缩的 release 构建；kotlinx.serialization 自带 R8 规则
- [x] 合并清单只申请 `WAKE_LOCK`、`RECEIVE_BOOT_COMPLETED` 与 AndroidX 自定义的签名级权限（移除网络状态与前台服务声明及其服务）；CI 与发布流程用 `tools/ci/check-apk.sh` 对照 `tools/ci/permissions.allowed` 逐项检查 release APK；Android 备份包含 Room 与 DataStore
- [ ] Play Data Safety、内容分级、隐私政策托管（文案已更新，提交待 owner）

## 1. 触控与视觉

| 项 | 规则 | 当前 |
|---|---|---|
| 最小触控目标 | 48 × 48dp | 浮动工具栏 64dp 高；数字键 56dp 高、≥ 44dp 宽（6×6 七键：360dp 手机上 45dp，378dp 起 48dp）；状态行按钮 48dp；6×6 单元格 ≈ 61dp |
| 9×9 单元格 | 48dp | 手机竖屏 ≈ 42dp；按住滑动瞄准 + 放大镜让落点与手指大小无关；另有拿起数字、键盘、宽屏布局 |
| 颜色角色 | 无基线紫色回退 | Paper / Night 定义全部 M3 角色（含 `surfaceContainer*`） |
| 对比度 | 正文 4.5:1、图形 3:1 | 所有文字角色 ≥ 4.5:1；格线 3.07:1（Paper）/ 3.11:1（Night），宫线 5.9:1 / 4.7:1（对纸面 sheet 计） |
| 不只用颜色表意 | 冲突、错误、Coach | 冲突为点状下划线 + 语义（与红色标记形状不同）；Coach 关注的单元用描边框出；步骤用实心/空心圆点并朗读；本月目录的完成状态用实心点/空心圈/灰点区分 |
| 字体 | sp；200% 不截断 | UI 文本 sp；顶栏、状态行、工作区随字号增高；卡片缩略图在大字号下缩小；分隔点前不换行；教练步骤在大字号下单独一行；棋盘、数字键与印章的数字按图形大小缩放 |
| 动效 | 不承载关键信息 | 落笔、印章、面板过渡均为装饰性；印章每页只落一次（旋转屏幕、返回后不重放） |

## 2. 原生组件映射

| UI | 使用 |
|---|---|
| 导航 | Navigation 3 `NavDisplay`（本子 / 设置 / 开源许可），自定义 transition / pop / predictive pop |
| RTL | 棋盘与数字键固定从左到右（数独格局不随语言镜像），点按命中按 LTR 计算；有仪器测试 |
| 本子与页面 | `NavigableListDetailPaneScaffold` + `AnimatedPane` + `sharedBounds` |
| 本子 | `LargeTopAppBar`（折叠）、`Surface` 卡片、`ListItem` |
| 页面顶栏 | 透明小 `TopAppBar` |
| 工具栏 | `HorizontalFloatingToolbar`，三个同样安静的图标按钮（铅笔为自绘开关：`combinedClickable`，长按填入候选数，TalkBack 有对应操作）；图标为 Material Symbols Rounded 矢量 |
| 数字键盘 | `ButtonGroup` 连接式按键，擦除为最后一键 |
| 小组件 | Glance `GlanceAppWidget`（响应式尺寸、`appWidgetBackground`、系统圆角、`previewLayout`） |
| 确认 | `AlertDialog`（重开、换一页练习） |
| 加载 | Expressive `LoadingIndicator` |
| 设置 | `ListItem` + `Switch`、`SingleChoiceSegmentedButtonRow`、系统按应用语言设置（外链图标）、开源许可页（OFL 与 Apache 全文）；顶栏滚动时上色；触感跟随系统 |
| 打印 / 分享 | 系统 `PrintManager` / Sharesheet |

## 3. 无障碍与输入

- [x] 棋盘 `CollectionInfo`；单元格 `CollectionItemInfo(row, 1, col, 1)`、`Role.Button`、`selected`
- [x] 每格一个节点（`clearAndSetSemantics`）：行列、数字或笔记、冲突、教练建议/排除；点击动作 + Erase 自定义动作（滑动瞄准不影响无障碍点击）
- [x] Coach 批注 `liveRegion = Polite`；标题 `heading()`；完成提示 `liveRegion` + `heading()`
- [x] 双窗格本子：选中项 `selected`；窗格 `paneTitle`
- [x] 键盘：方向键（仅棋盘获焦时）、1–9（始终写入）、Backspace、Ctrl+Z、N、H、Esc；键盘模式显示焦点框；系统快捷键帮助（Meta + /）
- [x] 被系统接管的触摸（边缘返回、防误触）不写入；棋盘排除系统返回手势
- [x] 中英双语，`localeConfig` 支持按应用切换语言；朗读标签按语言连接（“, ” / “，”），不在代码里拼标点
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
- [x] 计时使用单调时钟，仅页面在前台时计时
- [x] 题目生成在 `Dispatchers.Default`；明日每日题与下一页练习预先生成并缓存（`cacheDir`）
- [x] DataStore `ReplaceFileCorruptionHandler`
- [x] 手写 `AppContainer`（规模不需要 Hilt）

## 6. 测试与 CI

- [x] 引擎：几何、求解唯一性、生成确定性与评级、每一步逻辑推导与答案一致、X-Wing 定式；2026–2028 年每日题难度都与日历一致、一年每日题与每级 60 页练习无重复；跟随教练（及随机顺序）解题时每一步讲解的排除都按其技巧成立、落子在所述单元内唯一
- [x] `GameState`：输入、笔记整理、撤销、锁定、完成冻结、JSON 往返与篡改拒绝（含尺寸为 0 等不可能的题面）
- [x] ViewModel：自动保存恢复、计时、只记一次、Coach 三级与多步讲解、错误提示、经典续玩、换页时切回或打开别页、启动落到今天
- [x] Compose UI：棋盘语义、事件、Coach、完成页、单页宽布局（双栏未渲染）、RTL 点按、目录与手册入口；Compose 无障碍检查（`enableAccessibilityChecks()`，每次 perform* 时运行）
- [x] Room 迁移仪器测试（含 3 → 4：JSON 列移到最后、两个索引）
- [x] CI：JVM 测试、debug/release 构建、lint（由 tools/ci/lint-summary.py 从 SARIF 汇总，按规则计数并逐条打印在日志里；报告每次都上传）、schema 已提交检查、API 36 模拟器仪器测试；Gradle 构建缓存与配置缓存
- [x] 截图矩阵（Paper/Night × 页面状态 × 手册 × 单页宽屏；200% 字号仅 Paper）与已提交的基准图逐张比对；另有按 Play 尺寸生成的商店截图（不比对）
- [x] 每日题与练习页固定样例、3.0 存档格式样例
- [x] Macrobenchmark：冷启动到今日页（有/无 Baseline Profile 两组）、书写帧时间（每轮清空数据，只往空格写；CI 上开着动画跑，日志打印各项中位数）；混淆构建冒烟测试（页面 → 本子 → 设置 → 开源许可 → 回到今天）
- [x] 速度预算（10.0）：`tools/ci/speed-budget.py` 把 Macrobenchmark 结果与 `tools/ci/speed-budget.json` 比较，任一项超过预算 1.25 倍 CI 即失败；预算取自 CI 模拟器自己的数字（8.0 main：首帧中位 1430 ms、完全绘制 2525 ms、书写帧 CPU P50 102 / P90 123 ms、超时 P90 170 ms），不是真机目标。`check-apk.sh` 另要求 release APK 不超过 9.0 大小的 1.05 倍
- [x] 出题表（10.0）：2026–2029 年每日题、各级练习 0–299 页、各级各技巧路径页 0–99 页在 `CatalogTable` 里记着命中的那一刻，一刀即成；`CatalogTableTest` 每 37 项抽查一次（及首尾）与搜索结果一致，并按刀数（不按时钟）断言每页一刀；生成器改动后用 `CatalogTableWriter` 重写
- [x] Baseline Profile（手写规则覆盖 app、kotlinx.serialization、协程、Kotlin 标准库集合与文本、Room/SQLite/DataStore + profileinstaller），release 用已提交的文件，CI 检查已打进 release APK；CI 每次在模拟器上用 `BaselineProfileGenerator` 录制真实路径、上传，并由 `tools/ci/profile-coverage.py` 列出未被已提交文件覆盖的规则；`ReportDrawnWhen` 标记完全绘制
- [ ] `androidx.baselineprofile` 插件与 Startup Profile（DEX 布局）：未接入，插件 1.3.4 与 AGP 9.4 的兼容性未在构建中验证
- [ ] 真机冷启动 < 400ms 验证（模拟器数据不代表真机）

## 7. 隐私

- [x] 无网络、无账号、无广告、无统计
- [x] 分享为纯文本、无链接；打印走系统打印服务
- [x] 备份规则只包含数据库和设置
