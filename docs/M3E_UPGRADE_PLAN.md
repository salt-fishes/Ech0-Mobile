# Ech0 Mobile M3E 统一升级计划

> 版本：3.0（2026-11-01）
> 状态：生效中，唯一事实来源
> 合并自：旧版 M3E 计划（本文件前身）、`SHARED_ELEMENT_TRANSITION_PLAN.md`
> 规范来源：`material-3-skill-master` 的 `expressive-design.md` / `motion.md` / `component-catalog.md`
> 适用范围：`ech0-mobile` Android 客户端。Web 端不在本计划内；M3E 本身也是 Compose 优先，不要求 Web 做等价迁移。
> 目标：0.7.0 收尾，0.7.1–0.7.4 完成 M3E 表达层与共享元素，0.7.5+ 只做打磨。

---

## 0. 文档约定

- 本文件是 M3E 改造、共享元素转场、相关发布收尾的**唯一计划**。旧文档只保留指针。
- 优先级：
  - **P0**：阻塞发布、正确性、无障碍或崩溃风险。
  - **P1**：核心 M3E 体验，用户高频可见。
  - **P2**：增强项，可随版本顺延。
  - **P3**：可选/实验，不承诺版本。
- 状态：`✅ 完成` / `◐ 部分完成` / `○ 未开始` / `⏸ 暂缓`。
- 新需求先进入第 5 节 Backlog，再排入第 4 节路线图；不要另开平行计划文档。
- 详细技术方案可以单独成文，但必须在本文件登记链接，避免多处待办各说各话。

---

## 1. 现状基线（2026-11-01）

当前代码版本已 bump 为 `0.7.0`（versionCode 13，尚未发布）。M3E 的“底色”已经具备，但“表达层”覆盖不均，且文档与代码存在漂移。

### 1.1 已完成（0.6–0.7.0 基线）

- 主题与 token：
  - `MaterialExpressiveTheme` + `Ech0Shapes` 圆角层级（20 / 28 / 32 / 48dp）。
  - `scaledTypography()` 同时缩放 baseline 与 emphasized 字阶。
  - `LocalReduceMotion` 注入，主要自定义动画读取该开关。
  - 动态取色、自定义种子色、AMOLED、高对比度、壁纸/宣纸背景。
- 基础组件：
  - `Ech0ChoiceGroup`：用 connected `ToggleButton` 形状替代 13 处 `SingleChoiceSegmentedButtonRow`。
  - `Ech0Loading`：屏幕级 `LoadingIndicator` / `ContainedLoadingIndicator`。
  - `EmptyStateCard`：图标容器使用 `MaterialShapes.Arch`。
  - `MonthPanel`：`Square → Cookie4Sided` 的形状 morph。
  - 发布进度条使用 `LinearWavyProgressIndicator`；编辑器单图上传使用 `CircularWavyProgressIndicator`。
- 页面：
  - 时间线使用 edge-to-edge Hero Header + 滚动后的浮动操作条；设置二级页在展开态使用 `MediumFlexibleTopAppBar`（收起态回退 `OverlayTopBar`）。
  - 发现 / 我的：Hero 卡、Section 标题、20dp 卡片层级；友链已接入同款 Hero Header + 滚动后浮动操作条。
  - 设置：首页 → 二级页 shared-axis X 转场，顶栏标题交叉淡入。
  - 编辑器：sheet 32dp 顶角、`MotionScheme` 展开/拖拽、发布时统一压缩上传。
  - 详情：容器缩放 + 淡入；评论标题 emphasized；点赞使用 `fastSpatialSpec()`。
  - 悬浮 dock：选中胶囊、圆角 12→22dp、Tab 语义、200% 字号隐藏标签。
  - 列表返回：时间线、友链、Zen 已用可见项 key 恢复滚动位置；首页/友链图片可直接打开查看器。
- 媒体：6 种图片布局、图片裁剪/旋转、低流量占位、图片查看器缩放/翻页。
- 图片网格与编辑器（0.7.0）：
  - 非编辑态只保留图片与添加位：轻点图片进入全屏预览，长按进入网格管理态；不再常驻左下角编辑按钮和右上角删除按钮。
  - 网格管理态：右上角显示删除 `×`，左上角显示拖动把手，顶部显示“完成”；长按拖动排序、点 `×` 删除，带 lift scale / tonal target / 触觉反馈，全部走 `MotionScheme` 与 `LocalReduceMotion`。
  - 内容编辑态：预览页左下角“编辑”进入；支持裁剪、画笔、马赛克；工具栏使用 connected `Ech0ChoiceGroup`、tonal 按钮和深色编辑主题，预览 ↔ 编辑用 `AnimatedContent` 过渡。

### 1.2 部分完成 / 需要校准

| 领域 | 现状 | 缺口 |
|---|---|---|
| 顶栏 | 时间线、友链 Hero Header + 滚动后渐变模糊浮动操作条；设置二级页展开态 `MediumFlexible` | 发现/我的仍是标准 `TopAppBar`；覆盖页部分标准、设置二级页 `MediumFlexible`；模糊、渐变、高度未 token 化 |
| 加载 | 主要页面已用 `LoadingIndicator`；发布条用 `LinearWavy`；上传图用 `CircularWavy` | 仍有约 10 处标准 `CircularProgressIndicator`；音乐卡仍是 `LinearProgressIndicator` |
| 形状 | `Ech0Shapes`、空态 Arch、月份 morph | 点赞/刷新/播放/排序仍用 scale/rotation；头像、媒体 tile 没有品牌形状语言 |
| 导航 | 自定义悬浮 dock，保持现状 | 不做 `ShortNavigationBar` / FAB menu；现有选中态与语义不再改 |
| 选择组 | `Ech0ChoiceGroup` 是 ToggleButton 拼接 | 不是完整 `ButtonGroup`；评论排序、队列、草稿仍是散装按钮 |
| 搜索 | 时间线 / 设置搜索用 `OutlinedTextField` | 未用 `SearchBar` / `DockedSearchBar` / expressive search app bar |
| 媒体 | 6 种图片布局 + 查看器 / 编辑器 | 未用 M3E Carousel / FloatingToolbar；移动端布局与 Web 非 1:1 |
| 共享元素 | 0 处；详情是缩放 + 淡入 | 0.7.1+ 按第 6 节实施 |
| 页面表达 | 时间线 / 发现 / 友链 / 我的主体已做 | 启动页、登录、连接引导、关于、草稿箱、队列、Zen、空态仍以标准 M3 为主 |
| 自适应 | 手机单列 + 悬浮 dock | 宽屏 rail 暂缓；当前不做 `WideNavigationRail`，0.7.5+ 只评估 |
| 动效 | 组件内大量 `MotionScheme` | 页面级转场仍手写 `tween` / `M3Easing`；部分动画未读取 `LocalReduceMotion` |

### 1.3 已知文档/代码漂移（本版修正）

- 旧计划写“屏幕级 `CircularProgressIndicator` 已替换为主要页面”，实际仍有约 10 处标准 spinner；本计划把它们放进 0.7.x。
- 时间线评估过 `LargeFlexibleTopAppBar`，但展开态与 edge-to-edge 图片流不协调，已改为 Hero Header + 浮动操作条；本计划不再把 LargeFlexible 作为时间线目标。
- 旧计划把“上传状态改用 `LinearWavyProgressIndicator`”列为待办，实际发布条已是 LinearWavy、单图上传已是 CircularWavy；本计划不再重复。
- 0.7.0 release notes 把“详情共享元素”放进 P2/P3 收尾，与本计划“0.7.0 不做、0.7.1 开始”冲突；已改为只引用本计划。
- 顶部统一标准定为：主页面 Hero Header + 滚动后渐变模糊紧凑栏；覆盖页使用同一紧凑栏（返回/关闭 + 标题）。参数固定为 blur 36dp、高度 `statusTop + 96dp`、渐变 0.98 → 0.82 → 透明。Dock 保持现状，不再做 `ShortNavigationBar` / FAB menu。

### 1.4 当前关键决策

| 决策 | 当前值 |
|---|---|
| 应用版本 | `0.7.0` / versionCode 13（未发布） |
| 版本策略 | 所有实际改动归入 0.7.x 补丁序列，不再使用大版本跳跃 |
| 顶部模式 | 主页面 Hero Header + 渐变模糊紧凑栏；覆盖页同款紧凑栏。时间线、友链已完成；发现/我的 0.7.1；覆盖页 0.7.2 |
| 顶部参数 | blur 36dp；高度 `statusTop + 96dp`；渐变 0.98 → 0.82 → 透明；内容顶部 `statusTop + 4dp` |
| Dock | 保持现有自定义悬浮 dock；不做 `ShortNavigationBar` / FAB menu |
| 宽屏 / adaptive | 暂缓；不做 `WideNavigationRail`，0.7.5+ 只评估 |
| 共享元素 | 0.7.0 不做；0.7.1 基础设施 + 时间线→详情，0.7.2 友链/Zen，0.7.3 图片，0.7.4 精选入口 |
| 图片查看器 | 0.7.3 前仍是 `Dialog`；共享元素阶段 3 前必须改为同 composition overlay / 路由 |
| 图片网格 / 编辑器 | 两态分离：网格长按管理态（删除/排序）；内容编辑态（裁剪/画笔/马赛克）。0.7.0 已完成 |
| 当前发布物 | `app/build/outputs/apk/release/app-release.apk`，versionName 0.7.0，SHA-256 见 release notes |

---

## 2. 目标与原则

1. **M3E 不是“全部加大、加圆、加动效”**。它是在 MD3 上叠加表达层：用形状、动效、字体层级和 containment 强化重点，而不是让每个元素都抢注意力。
2. **优先内置组件**：能用 `ButtonGroup`、`SplitButton`、`FloatingToolbar`、`SearchBar`、`LoadingIndicator` 的地方，不再手写近似实现。Dock 是明确保留的自定义例外。
3. **一屏一个焦点**：Hero 卡、主操作、空态/引导三者不要同时争夺视觉中心。
4. **形状克制**：`MaterialShapes` 只用于有语义的位置（空态、媒体 tile、品牌时刻、状态反馈）；不做“每个卡片一个形状”。
5. **动效服从可用性**：所有新动效必须读取 `LocalReduceMotion`；大图和阅读面不做形状 morph；共享元素只用于有明确视觉对应关系的容器或图片。
6. **无障碍与低端机是发布门槛**：200% 字号、TalkBack、RTL、低流量、低端机帧率必须在每个版本验收。
7. **Web 不做 M3E 对齐**：M3E 的 Compose 组件在 Web 端没有等价实现；Web 继续使用现有 MD3 体系，移动端只保持产品语义一致（6 种图片布局等），不逐像素搬运。
8. **顶部统一**：主页面用 Hero Header + 滚动后渐变模糊紧凑栏；覆盖页用同款紧凑栏（返回/关闭 + 标题）；渐变/模糊参数 token 化。Dock 保持现状，不改导航结构。

---

## 3. 工作流总览

| ID | 工作流 | 目标 | 主要版本 |
|---|---|---|---|
| A | Foundation | 主题、token、字体、颜色、motion、reduce motion | 0.6–0.7（已完成） |
| B | Top bars & navigation | 顶部统一模式（Hero Header + 渐变模糊紧凑栏）、覆盖页顶部；dock 保持现状 | 0.7.1–0.7.2 |
| C | Actions & controls | `ButtonGroup`、`SplitButton`、FloatingToolbar、Slider、按钮尺寸 | 0.7.1–0.7.5+ |
| D | Feedback & loading | 统一 spinner、下拉刷新、wavy progress、刷新按钮 | 0.7.0–0.7.1 |
| E | Search & filtering | `SearchBar`、设置搜索、筛选组 | 0.7.1–0.7.3 |
| F | Media & image layouts | 图片布局对齐、Carousel、查看器/编辑器工具栏 | 0.7.3 |
| G | Motion & shape | 页面转场、形状 morph、共享元素 | 0.7.1–0.7.4 |
| H | Page expression | 启动页、登录/连接、关于、草稿、队列、Zen、空态、复杂弹窗 | 0.7.1–0.7.4 |
| I | Adaptation & accessibility | 200%、TalkBack、RTL、低端机、低流量；宽屏/adaptive 暂缓 | 0.7.x 每版 |
| J | Release & verification | 构建、设备矩阵、release notes、文档治理 | 0.7.x 每版 |

---

## 4. 版本路线图

| 版本 | 主题 | 范围 | 退出条件 |
|---|---|---|---|
| **0.7.0** | M3E 基线 | 当前已完成的 M3E 基线；不引入共享元素；剩余 P0/P1 进入 0.7.x | 现有功能稳定；release notes 与实际一致 |
| **0.7.x** | 一致性补丁 | 剩余标准 spinner、下拉刷新 `LoadingIndicator`、音乐 `LinearWavy`、空态统一、启动页 loading、composer toast reduceMotion | 不再有屏幕级/独立标准 `CircularProgressIndicator`；按钮内 16–18dp 小 spinner 允许保留 |
| **0.7.1** | 顶部统一 + 搜索 / 控件 + 共享元素基础 | 主页面顶部统一（Hero Header + 渐变模糊紧凑栏，时间线/友链已落地，发现/我的迁移）、`SearchBar`、Slider、共享元素阶段 0 + 时间线→详情 | 顶部视觉一致；时间线→详情 20 次往返无闪烁；搜索可用；0.7.1 验收矩阵通过 |
| **0.7.2** | 覆盖页顶部 + 共享元素阶段 2 + 控件补齐 | 覆盖页顶部统一、完整 `ButtonGroup`（选择组）、启动页/登录/连接引导、评论排序/队列/草稿 `ButtonGroup`、刷新按钮、筛选组、友链 / Zen → 详情 | 覆盖页顶部一致；友链 / Zen 返回位置正确；远程图片降级正常 |
| **0.7.3** | 媒体表达 + 共享元素阶段 3 | 图片查看器 `Dialog` → 同 composition overlay；图片共享；图片布局/ Carousel；查看器/编辑器 FloatingToolbar；发布 `SplitButton` | 图片查看器共享、翻页、返回全部通过；媒体操作不再用 AlertDialog 堆叠 |
| **0.7.4** | 全局覆盖 + 动效收口 | 共享元素阶段 4（精选入口）；页面转场迁 `MotionScheme`；形状 morph 扩展；关于/草稿/队列/Zen 表达层、复杂弹窗分层 | 页面级动效统一；全量验收通过；宽屏/adaptive 暂缓 |
| **0.7.5+** | 打磨 / 可选 | 按钮 5 档尺寸、Dropdown/Exposed 菜单、ListItem 选中 morph、更多页面表达、宽屏/adaptive 评估 | 不影响主线，按收益排期 |

> 版本号是“建议目标”，不是硬承诺。每个版本可以内部分批，但不得把共享元素阶段 0 的基础设施省掉直接做单卡片动画。

---

## 5. Backlog

### 5.1 导航与顶栏

| ID | 项目 | 现状 / 问题 | 目标 | 版本 | 优先级 |
|---|---|---|---|---|---|
| NAV-01 | 主页面顶部统一 | 时间线、友链已用 Hero Header + 渐变模糊浮动操作条；发现/我的仍是标准 `TopAppBar` | 发现/我的迁移到同一模式；抽出 blur/高度/渐变 token | 0.7.1 | P1 |
| NAV-02 | 覆盖页顶部统一 | 详情/远程详情/设置/关于/草稿/队列/Zen/登录等仍是标准 `TopAppBar` 或 `MediumFlexible` | 统一为紧凑浮动栏：返回/关闭 + 标题 + 同款渐变模糊；表单页可保留 small 形态但共享 token | 0.7.2 | P1 |
| NAV-03 | 悬浮 dock | 自定义悬浮 dock 保持现状 | 不改；不做 `ShortNavigationBar` / FAB menu；只保留现有选中态、Tab 语义和中央发布入口 | 不做 | — |
| NAV-04 | 宽屏 / adaptive | 无 `WindowSizeClass`；平板仍手机布局 | 暂缓；不引入 `WideNavigationRail`，避免改动 dock/导航结构 | 暂缓 | P3 |

> 顶部统一标准：主页面 Hero Header + 滚动后渐变模糊紧凑栏；覆盖页同款紧凑栏。固定参数 blur 36dp、高度 `statusTop + 96dp`、渐变 0.98 → 0.82 → 透明。Dock 保持现状，不再做 `ShortNavigationBar` / FAB menu；宽屏 rail 暂缓。

**顶部统一规范（0.7.1 起）**

| 部件 | 规范 |
|---|---|
| 主页面 Hero Header | 44dp 前导（站点 logo / 页面图标）、`headlineSmall` 标题、`labelMedium` 副标题、右侧 pill actions；内容顶部 padding = `statusTop + 4dp` |
| 紧凑浮动栏 | `firstVisibleItemIndex > 0` 时淡入；高度 `statusTop + 96dp`；blur 36dp；渐变 `surface` 0.98 → 0.82 → 透明；左侧 pill 标题（24dp 前导），右侧 actions pill |
| 覆盖页顶部 | 同款紧凑栏：返回/关闭 + 标题；无 Hero Header；内容顶部预留高度在 0.7.2 定稿 |
| 参数治理 | 参考 `TimelineScreen.kt` / `HubScreen.kt` 的紧凑栏；抽 `FeedTopBarDefaults`，统一 blur、alpha、高度、padding；禁止各页硬编码不同值 |
| Dock | 保持现有自定义悬浮 dock；不做 `ShortNavigationBar` / FAB menu |

### 5.2 动作与控件

| ID | 项目 | 现状 / 问题 | 目标 | 版本 | 优先级 |
|---|---|---|---|---|---|
| ACT-01 | 完整 `ButtonGroup` | `Ech0ChoiceGroup` 是 ToggleButton 拼接，不是完整组件 | 用 `ButtonGroup` + overflow indicator 替换；支持标准/connected 两种形态 | 0.7.2 | P1 |
| ACT-02 | 小型操作组 | 评论排序单图标；队列“重试/删除”；草稿“编辑/删除”是散装按钮 | 2–3 个相关操作用 connected `ButtonGroup`；队列可用 `SplitButton` | 0.7.2 | P1 |
| ACT-03 | 发布 `SplitButton` | 编辑器顶栏是普通 `Button` | 主操作“发表”+ 关联菜单（定时/存草稿/私密等） | 0.7.3 | P1 |
| ACT-04 | FloatingToolbar | 图片查看器长按操作、图片编辑器顶栏 | `VerticalFloatingToolbar`（查看器）/ `HorizontalFloatingToolbar`（编辑器、编辑器格式/附件） | 0.7.3 | P1 |
| ACT-05 | Slider M3E | 设置字号/背景暗化/图片质量、编辑器画质都是默认 Slider | 使用 M3E 5 档尺寸、inset icon、stops；数值标签与控件关系重排 | 0.7.1 | P1 |
| ACT-06 | 按钮尺寸/形状 | 发布、复制链接、队列重试、草稿编辑均为默认 40dp | 按主次使用 XS–XL 尺寸；主操作可用 medium/large；支持 round/square | 0.7.5+ | P2 |
| ACT-07 | 菜单 | 编辑器画质/格式是自定义锚点 `DropdownMenu` | `ExposedDropdownMenuBox` 或与 `SplitButton` 配对；保留 shape-morph focus | 0.7.5+ | P2 |
| ACT-08 | ListItem 选中 morph | `SettingsListRow` 用 `Surface` 包 `ListItem` | 使用 `ListItem` 的 selected/交互状态与形状 morph | 0.7.5+ | P2 |

### 5.3 反馈与加载

| ID | 项目 | 现状 / 问题 | 目标 | 版本 | 优先级 |
|---|---|---|---|---|---|
| FB-01 | 剩余 spinner | 约 10 处标准 `CircularProgressIndicator`：启动页、连接、登录、网络测试、诊断、检查更新、更新记录、发送评论、加载更早 | 换 `LoadingIndicator` / `ContainedLoadingIndicator`；按钮内小 spinner 可保留 | 0.7.x | P0 |
| FB-02 | 下拉刷新 | 时间线/发现/友链用默认 `PullToRefreshBox` 圆形指示器 | `PullToRefreshDefaults.LoadingIndicator` | 0.7.x | P0 |
| FB-03 | 音乐进度 | `ExtensionCard.kt` 用 `LinearProgressIndicator` | `LinearWavyProgressIndicator` | 0.7.x | P1 |
| FB-04 | 刷新按钮 | `RefreshIconButton` 手写无限旋转 | 评估换成 `LoadingIndicator` 或保留旋转但统一 `MotionScheme`/语义 | 0.7.2 | P2 |
| FB-05 | 编辑器 toast | `ComposerSheet.kt` 的 `AnimatedVisibility` 未读 `LocalReduceMotion` | 补 reduceMotion 降级 | 0.7.x | P2 |

### 5.4 搜索与筛选

| ID | 项目 | 现状 / 问题 | 目标 | 版本 | 优先级 |
|---|---|---|---|---|---|
| SR-01 | 时间线搜索 | `OutlinedTextField` 手动胶囊 | `SearchBar` / expressive search app bar；保留搜索展开/收起 | 0.7.1 | P1 |
| SR-02 | 设置搜索 | `OutlinedTextField` | `SearchBar` / `DockedSearchBar`；空查询显示引导 | 0.7.1 | P1 |
| SR-03 | 筛选组 | 图片布局、标签等仍是 chip 堆叠 | 评估 connected `ButtonGroup` + overflow；不要把所有 chip 都改成组 | 0.7.2 | P2 |

### 5.5 媒体与图片

| ID | 项目 | 现状 / 问题 | 目标 | 版本 | 优先级 |
|---|---|---|---|---|---|
| MED-01 | 图片布局对齐 | 移动端 `grid` 无独立分支；瀑布流 9 图会丢第 9 张；比例被 clamp；与 Web 非 1:1 | 补 `grid` 分支、修 9 图、统一比例策略；保持 6 种语义与 Web 一致 | 0.7.3 | P1 |
| MED-02 | M3E Carousel | 横向/轮播共用 `LazyRow`，轮播无分页 | `HorizontalUncontainedCarousel` / `HorizontalCenteredHeroCarousel` | 0.7.3 | P2 |
| MED-03 | 图片查看器宿主 | `ImageViewerDialog` 是 `Dialog`，独立 window | **必须先改为同 composition 的全屏 overlay 或独立路由**；否则共享元素阶段 3 无法实现 | 0.7.3 | P1（阶段 3 前置） |
| MED-04 | 查看器操作层级 | 保存/分享/取消堆在 AlertDialog | 改 bottom sheet / floating toolbar；保留长按入口；工具栏样式见 ACT-04 | 0.7.3 | P2 |

### 5.6 动效与形状

| ID | 项目 | 现状 / 问题 | 目标 | 版本 | 优先级 |
|---|---|---|---|---|---|
| MOT-01 | 共享元素阶段 0 | 0 处；无 `SharedTransitionLayout` | 根布局 + `SharedTransitionScope` + 稳定 key + 返回栈 + 几何规则 | 0.7.1 | P0 |
| MOT-02 | 共享元素阶段 1 | 时间线 → 详情是缩放+淡入 | 卡片 `sharedBounds` + 封面 `sharedElement`；标题/正文不共享 | 0.7.1 | P0 |
| MOT-03 | 共享元素阶段 2 | 友链 / Zen → 详情未接 | 同一 key 规则；返回恢复来源位置；远程图片预取/降级 | 0.7.2 | P1 |
| MOT-04 | 共享元素阶段 3 | 图片查看器是 Dialog | 先做 MED-03，再做缩略图 ↔ 全屏共享；翻页更新 key | 0.7.3 | P1 |
| MOT-05 | 共享元素阶段 4 | 发现/草稿/队列等入口 | 只选有明确视觉对应的入口，最多 2 个共享节点；关于/更新弹窗不纳入 | 0.7.4 | P2 |
| MOT-06 | 页面转场统一 | 手写 tween / M3Easing | 统一到 `MotionScheme` spatial specs；保留 reduceMotion | 0.7.4 | P1 |
| MOT-07 | 形状 morph 扩展 | 仅月份面板 + LoadingIndicator | 点赞、播放/暂停、排序、刷新等高价值状态；不做全站 morph | 0.7.4 | P2 |

> 共享元素的 key、返回栈、几何、降级、验收详见第 6 节。

### 5.7 页面表达

| ID | 项目 | 现状 / 问题 | 目标 | 版本 | 优先级 |
|---|---|---|---|---|---|
| PAGE-01 | 启动页 | 静态 logo + 固定 850ms；loading spinner 见 FB-01 | 品牌形状/节奏的克制 morph；减少动效下降级为静态 | 0.7.2 | P1 |
| PAGE-02 | 登录 / 连接引导 | 标准 Card + OutlinedTextField + Button | 首次体验 hero 构图；连接成功/失败状态用 M3E feedback | 0.7.2 | P1 |
| PAGE-03 | 关于 / 草稿 / 队列 / Zen | 标准 Card / TextButton / 纯 Text 空态 | 统一 expressive 卡片、空态、操作组；详见 PAGE-04/05 | 0.7.4 | P2 |
| PAGE-04 | 空态统一 | 草稿箱、队列、评论空态是纯 `Text` | `EmptyStateCard` + `MaterialShapes`；文案/操作与现有空态一致 | 0.7.x | P1 |
| PAGE-05 | 复杂弹窗 | 评论身份、画质/格式、补记时间等仍塞 AlertDialog；图片操作见 MED-04 | 按复杂度分层：basic dialog / full-screen dialog / sheet；不要所有层级同一形态 | 0.7.4 | P2 |

### 5.8 自适应与无障碍

| ID | 项目 | 现状 / 问题 | 目标 | 版本 | 优先级 |
|---|---|---|---|---|---|
| A11Y-01 | 200% 字号 | 设置/dock 已检查，其他页面未系统覆盖 | 每版回归时间线、详情、编辑器、图片查看器、空态 | 0.7.x 每版 | P0 |
| A11Y-02 | TalkBack / RTL | 未系统回归 | 图标按钮有 contentDescription；RTL 下形状/动效方向正确 | 0.7.x 每版 | P0 |
| A11Y-03 | reduceMotion 覆盖 | 主要动画已接，仍有漏网（如 composer toast） | 所有新动效默认读 `LocalReduceMotion`；共享元素可完全降级 | 0.7.1+ | P0 |
| A11Y-04 | 低端机性能 | 共享元素/大图/远程图有风险 | 按内存等级关闭图片共享；帧率不低于当前基线 | 0.7.1+ | P0 |
| A11Y-05 | 低流量 | 图片/扩展卡已有开关 | 共享元素、查看器、远程详情都走同一降级策略 | 0.7.3+ | P1 |

### 5.9 工程与发布

| ID | 项目 | 现状 / 问题 | 目标 | 版本 | 优先级 |
|---|---|---|---|---|---|
| REL-01 | 构建与测试 | 已有命令 | `compileDebugKotlin`、`assembleRelease`、`testDebugUnitTest`、`lintVitalRelease` 全绿 | 0.7.x 每版 | P0 |
| REL-02 | 设备矩阵 | 目前主要手机模拟器 | 手机 API 29/35、平板/折叠屏（暂不做 rail）、200%、深浅色、RTL、低端机 | 0.7.x 每版 | P0 |
| REL-03 | 发布说明准确性 | 文档曾把未完成项写成已完成 | release notes 只写实际交付；未完成项链接本计划 | 0.7.x 每版 | P0 |
| REL-04 | 文档治理 | 存在平行计划与重复待办 | 本文件唯一来源；旧文档改为指针 | 0.7.x 每版 | P1 |

---

## 6. 共享元素转场（合并自旧计划）

> 目标：从 0.7.1 开始，用 2–3 个版本把「列表 → 详情 → 图片」的共享元素转场做成全局一致体验。0.7.0 保留现有容器缩放 + 淡入。

### 6.1 当前架构与硬前提

- `AppRoot` 用自定义 `AnimatedContent` 在 tab 与 `Overlay` 之间切换。
- `DetailScreen` / `RemoteDetailScreen` 是独立覆盖页。
- 时间线、友链使用 `LazyColumn`；Zen 使用 `LazyVerticalStaggeredGrid`。
- 图片预览是 `ImageViewerDialog`，**目前是 `Dialog`，独立 window**。
- 返回逻辑目前是 `overlay = null`；Zen 详情额外用 `zenDetailReturn` 回到 Zen。

**硬前提**：`Dialog` 与主 composition 不在同一个窗口/layout 树，`SharedTransitionLayout` 无法跨窗口共享。阶段 3 之前必须把 `ImageViewerDialog` 改成：

- 挂在 `Ech0AppRoot` 层的同 composition 全屏 overlay；或
- 独立导航目的地。

否则图片共享元素无法实现，这不是调参问题。

### 6.2 稳定 key 规则

| 对象 | key | 说明 |
|---|---|---|
| 本地 Echo | `echo:local:<id>` | 不使用列表 index |
| 远程 Echo | `echo:remote:<sourceUrl>:<id>` | source 参与 key，避免不同实例同 id 冲突 |
| 图片 | `image:<sourceUrl\|local>:<fileId\|url>` | 同一图片在列表/详情/查看器复用 |
| 草稿 | `draft:<id>` | 阶段 4 使用 |
| 队列项 | `queue:<payloadId>` | 阶段 4 使用 |

规则：

- 不允许用 index、标题文本、临时 local path 作为 key。
- key 必须在刷新、分页、切 tab、快速返回时保持稳定。
- 远程实例不可用时，key 仍然稳定，但转场降级。
- 同一屏最多 2 个共享节点（通常容器 + 封面图）。

### 6.3 返回栈与几何

**返回栈**（阶段 0 定稿，二选一）：

- 方案 A：把 `overlay` 从单值改为轻量栈。
- 方案 B：给 `Overlay.Detail` 增加 `returnTo`，图片查看器记录 `ImageOrigin`。

无论哪种，必须支持：

- 详情、远程详情、Zen 详情、图片查看器从同一来源恢复。
- `ScrollMemory` 保留列表位置；图片查看器额外记录来源卡片/页码。
- 预测性返回与共享元素返回同时工作，页面不能先被销毁。

**几何**：

- 卡片根容器：`sharedBounds`，20dp 卡片 ↔ 详情大卡片。
- 封面图（单图或第一张）：`sharedElement`，`ContentScale.Crop` ↔ 详情媒体。
- 标题/正文：不做逐字共享，只淡入，避免 200% 字号抖动。
- 头像：可选；只有在两端位置稳定时才共享，默认不做。
- 圆角、裁剪、背景色两端一致，避免 `sharedBounds` 切角/跳色。

### 6.4 阶段 0：基础设施（0.7.1，先于阶段 1）

目标：不改变视觉，只建立可复用骨架。

1. 在 `Ech0AppRoot` 最外层包 `SharedTransitionLayout`，通过 `CompositionLocal` 提供 `SharedTransitionScope`。
2. 覆盖页 `AnimatedContent` 内部使用 `AnimatedVisibilityScope`（`AnimatedContentScope` 已实现该接口），保证列表与详情在同一 shared transition 作用域。
3. 落地 6.2 的 key 规则与 6.3 的返回栈。
4. 为时间线卡片、详情卡、图片节点建立统一的几何节点；先只加 key，不启用动画。
5. 建立降级开关：`reduceMotion`、低端机、低流量、图片未加载。

验收：

- 不加任何视觉变化的情况下，编译/运行/返回正常。
- 连续切 tab、快速打开/关闭覆盖页 20 次，无重复 key、无崩溃。
- 远程实例超时、图片 404 时，不因 key 缺失抛异常。

### 6.5 阶段 1：时间线 → 本地详情（0.7.1）

范围最小、收益最大。

- 卡片根容器：`sharedBounds`，20dp 卡片 → 详情大卡片。
- 封面图：`sharedElement`，列表缩略图 → 详情媒体；图片未加载时只共享容器。
- 标题/正文：淡入，不逐字共享。
- 返回：反向执行；列表滚动位置由 `ScrollMemory` 恢复。
- 降级：
  - `reduceMotion`：直接使用现有淡入。
  - 低端机：按内存等级关闭图片共享。
  - 低流量：只共享容器。

验收：

- 连续打开/返回 20 次无闪烁、无重复 key。
- 滚动到列表中部进入详情，返回仍在同一张卡片。
- 200% 字号、横屏、深色/浅色正常。
- 快速连点卡片/返回不崩溃。

### 6.6 阶段 2：友链 / Zen → 详情（0.7.2）

- 友链聚合卡：本地与远程详情共用 key 规则。
- Zen 瀑布流：卡片根容器 + 封面图共享；返回恢复 Zen 网格位置。
- 远程详情：
  - 远程图片进入前用 Coil 预取；失败只共享容器。
  - 返回必须回到友链/Zen 的来源位置，而不是主页。
- 友链刷新/来源切换导致卡片消失时，安全降级，不抛异常。

验收：

- 从友链/Zen 打开详情再返回，位置、滚动、图片连续。
- 远程图片未缓存/404/超时：容器仍连续，图片淡入。
- 刷新后来源卡片消失：无崩溃、无空白页。

### 6.7 阶段 3：图片查看器（0.7.3）

前置：完成 MED-03（`Dialog` → 同 composition overlay / 路由）。

- 列表缩略图 → 全屏查看器：缩略图 `sharedElement` 进入全屏。
- 左右翻页：只共享当前页；翻页后更新 key。
- 返回/下拉：回到原缩略图位置。
- 详情图片 → 查看器使用同一图片 key。
- 长按保存/分享不改变共享元素状态。
- 低流量/图片未加载：只做容器淡入。

验收：

- 列表/详情 → 查看器 → 返回，图片位置连续。
- 左右翻页、快速滑动、返回不闪烁、不重复 key。
- 远程图片失败、低流量、reduceMotion 下降级正常。

### 6.8 阶段 4：全局覆盖（0.7.4）

只纳入有明确视觉对应的入口：

- 发现页随机卡 / 热门卡 → 详情。
- 草稿箱卡片 → 编辑器（仅容器；先评估 sheet 动画冲突）。
- 发布队列卡片 → 队列详情/编辑器（仅容器；先评估）。
- 我的页账号卡 → 账号设置（可选，仅容器）。

**明确不做**：

- 设置分类卡 → 设置二级页。
- 关于页 / 更新弹窗。
- 任何 `Dialog` / `ModalBottomSheet` 作为目标的共享元素（窗口不同，收益低）。

原则：

- 每页最多 2 个共享节点。
- 只共享容器/图片，不共享标题、正文、按钮。
- 无法共享时静默降级为淡入。

### 6.9 降级矩阵

| 条件 | 行为 |
|---|---|
| `reduceMotion` 开启 | 完全退回淡入/容器缩放，不执行 shared bounds |
| 图片未加载完成 | 只共享容器；图片淡入 |
| 低流量模式 | 不加载图片时只共享容器 |
| 远程图片未缓存 | 先 Coil 预取；失败只共享容器 |
| 图片 404 / 超时 | 同失败路径，不崩溃 |
| 低端机 / 内存紧张 | 关闭图片共享，保留容器共享或纯淡入 |
| 200% 字号 | 不共享标题/正文；只共享容器和图片 |
| 预测性返回 | 先取消/反向转场，再销毁页面 |
| 快速连点/返回/切 tab | 去重 key；同一 key 不并发注册 |
| 来源卡片被刷新/删除 | 捕获失效，降级淡入 |
| 目标是 `Dialog` / `ModalBottomSheet` | 不共享；先改宿主（见 MED-03） |

### 6.10 技术风险

- **作用域**：`AnimatedContent` 会销毁离开的页面；共享元素必须依赖根 `SharedTransitionLayout`，不能页面局部 `remember`。
- **列表复用**：`LazyColumn` 的 item 可能被回收；源 item 必须在转场期间保持 composed。
- **Coil crossfade**：共享图片节点应关闭 crossfade，或等图片加载完成后再启动转场，避免“双重淡入”。
- **圆角/裁剪**：列表与详情圆角不一致会出现切角；用 token 对齐。
- **Dialog 窗口**：图片查看器、更新弹窗、图片操作弹窗都不能直接作为共享目标。
- **预测性返回**：共享元素返回与系统手势竞争，需要统一返回栈。
- **性能**：远程图片 + 大图 + shared bounds 可能掉帧；低端机默认关闭图片共享。
- **可访问性**：转场中节点语义要稳定；TalkBack 不因共享元素重复播报。

### 6.11 验收清单

- 列表 → 详情 → 返回，位置、滚动、圆角、图片均连续。
- 断网、图片 404、远程实例超时：转场降级为淡入，不崩溃。
- 减少动效、低流量、200% 字号、RTL、TalkBack 均可用。
- 连续快速点击、返回、切 tab 不产生重复 shared key。
- 低端机帧率不低于当前基线；必要时按设备能力关闭图片共享。
- 阶段 3 验收必须包含：查看器翻页、下拉返回、远程图、低流量。

---

## 7. 验收与发布

### 7.1 构建与静态检查

```bash
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintVitalRelease
./gradlew.bat :app:assembleRelease
```

### 7.2 设备矩阵

| 维度 | 覆盖 |
|---|---|
| 系统 | Android 10（API 29）、Android 15（API 35） |
| 形态 | 手机、平板/折叠屏（仅验证手机布局；rail 暂缓） |
| 主题 | 浅色、深色、AMOLED、高对比度 |
| 字号 | 100%、150%、200% |
| 语言/方向 | 中文、英文、RTL |
| 网络 | Wi-Fi、弱网、断网；低流量模式 |
| 图片 | 本地、远程、未缓存、404、超时 |
| 无障碍 | TalkBack 主流程 |
| 性能 | 低端机帧率、启动时间、内存峰值 |

### 7.3 发布门槛

- P0 项全部关闭；P1 项有明确结论（完成/顺延并记录）。
- 所有新动效读取 `LocalReduceMotion`。
- 所有新图标按钮有 `contentDescription`。
- 共享元素降级矩阵全部验证。
- release notes 只写实际交付；未完成项链接本计划。
- 旧文档指针已更新，无平行待办。

---

## 8. 文档治理

| 文档 | 状态 | 处理 |
|---|---|---|
| `M3E_UPGRADE_PLAN.md` | 本文件 | 唯一事实来源 |
| `SHARED_ELEMENT_TRANSITION_PLAN.md` | 已合并 | 改为指针，只保留“见第 6 节” |
| `RELEASE_NOTES_0.7.0_DRAFT.md` | 0.7.0 发布说明 | 移除 0.7.0 的共享元素待办，改为链接本计划 |
| `RELEASE_NOTES_0.6.0_DRAFT.md` | 历史 | 不改 |
| `SETTINGS_2.0_PLAN.md` | 独立计划 | 与本计划交叉处只引用，不复制待办 |
| `NETWORK_OPTIMIZATION_PLAN.md` | 独立计划 | 同上 |

规则：

1. 新待办先加到第 5 节对应工作流，再排版本。
2. 详细方案可以单独成文，但必须在本文件登记链接。
3. 发布前检查 release notes 与本计划状态是否一致。
4. 不再创建“某某改造计划”平行文档；确需拆分时，必须标明“本文件为总表”。
5. 旧版 M3E 计划的逐条完成记录保留在 git 历史；本文件只保留摘要和验证证据。

---

## 9. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-01 | 合并旧 `M3E_UPGRADE_PLAN.md` 与 `SHARED_ELEMENT_TRANSITION_PLAN.md`；修正文档/代码漂移；补充导航、搜索、控件、加载、媒体、自适应、空态、页面表达等缺口；明确图片查看器 `Dialog` 为共享元素阶段 3 的硬前提；移除低收益的关于/更新弹窗共享元素。 |
| 2026-11-01 | 友链顶部移植时间线 Hero Header + 浮动操作条；顶部渐变模糊加强为 36dp / 96dp / 0.98 → 0.82；所有目标版本归入 0.7.x；Dock 与 `ShortNavigationBar` / FAB menu 明确不做；顶部统一模式写入 NAV-01 / NAV-02。 |
| 2026-11-01 | 文档升到 3.0：应用 bump 0.7.0 / versionCode 13；补充“当前关键决策”和“顶部统一规范”；release notes 补充构建产物、SHA-256 与最新状态。 |
| 2026-11-01 | 图片网格改为“浏览 / 管理”两态：非编辑态轻点预览、长按管理；管理态 × 删除、拖动排序、完成退出。内容编辑新增画笔、马赛克，并保留裁剪 / 旋转；两者均按 M3E 组件、形状与动效实现。 |
| 2026-11-01 | 修复编辑后缩略图不刷新、上传进度回退、上传中透明编辑器拦截触摸；上传增加原图 / JPEG 回退与具体错误提示；原图上传按文件头识别 PNG/JPEG/WebP/GIF/AVIF，避免 PNG 被误命名为 `.jpg` 后服务端拒绝。 |

---

## 10. 附录：0.7 验证记录（2026-10-31）

保留旧计划的验证结果，作为 0.7 基线证据；后续版本验证记录追加到本节，不覆盖历史。

> 以下为 2026-10-31 对 0.6.0 基线的记录；当前已 bump 到 0.7.0，见第 1.4 节。

- `:app:compileDebugKotlin` 通过
- `:app:assembleRelease` 通过
- `:app:testDebugUnitTest` 通过
- `:app:lintVitalRelease` 通过
- Release APK 已覆盖安装到 `ech0test`；版本仍为 `0.6.0`，未发布
- 已人工检查：时间线、我的、设置首页、网络与流量、缓存与存储、编辑器、发布下拉、详情评论排序
- 已检查设置首页 → 分类页 shared-axis X 转场与顶栏标题交叉淡入
- 已检查 200% 字号：设置首页、网络页、选择组可用；悬浮 dock 标签按预期隐藏
- 已检查编辑器：长按取消保存草稿、评论排序图标、画质 / 格式弹出列表、图片裁剪
- 已确认添加图片后不会立即上传（无进度 / 完成标记），上传动作由「发表」触发
- 草稿箱数量从 2 增至 3，确认长按取消写入草稿
- 截图：`build/screen-home.png`、`build/me3.png`、`build/settings.png`、`build/settings_net.png`、`build/composer.png`、`build/composer_menu.png`、`build/detail2.png`、`build/settings_2x.png`、`build/net_2x_choices.png`

### 2026-11-01 追加验证

- 时间线 Hero Header + 浮动操作条正常；操作条贴右、带顶部渐变模糊，滚动后浮动在内容上。
- 设置二级页 `MediumFlexibleTopAppBar` 显示正常。
- 发布后编辑器隐藏，顶部 `LinearWavyProgressIndicator` 显示「正在发布…」；完成后自动消失。
- 友链卡片图片可直接打开全屏查看器；首页图片查看正常。
- 友链滚动到中部打开详情再返回，仍停在离开的卡片位置。
- 月份面板 `MaterialShapes` Square → Cookie4Sided 形变正常，文字未被裁剪。
- `:app:compileDebugKotlin`、`:app:assembleRelease`、`:app:testDebugUnitTest`、`:app:lintVitalRelease` 通过。
- Zen 详情代码已接入；当前测试实例网络不可用，未完成真机点击回归。
