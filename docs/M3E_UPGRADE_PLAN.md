# Ech0 Mobile M3E 改造计划

> 目标版本：`0.7.0`（未发布）
> 规范来源：`material-3-skill-master` 的 `expressive-design.md` / `motion.md`

## 已完成（P0 + P1）

### 主题与基础

- `Design.kt` 新增 `Ech0Shapes` 圆角层级：
  - feed / 设置卡：`largeIncreased` 20dp
  - Hero / 账号卡：`extraLarge` 28dp、`extraLargeIncreased` 32dp
  - 编辑器 sheet：32dp
- `scaledTypography()` 现在同时缩放 15 个 baseline 与 15 个 emphasized 样式，设置中的行距对强调字体生效。
- 新增 `LocalReduceMotion`，由 `Ech0Theme(reduceMotion = ...)` 注入；`AppRoot` 传入用户设置。
- 新增 `Ech0ChoiceGroup`（Connected Button Group）替代 13 处 `SingleChoiceSegmentedButtonRow`。
- 新增 `Ech0Loading`，统一屏幕级加载态。

### 页面调整

- 时间线：
  - `EchoCard` 圆角从 28dp 调整为 20dp。
  - 点赞动画改用 `MotionScheme.fastSpatialSpec()`，并读取 `LocalReduceMotion`。
- 发现：
  - `SectionCard` / 随机卡改为 20dp。
  - Section 标题使用 `titleMediumEmphasized`。
  - 随机条加载态改为 `LoadingIndicator`。
- 友链：
  - 本站 Hero 使用 32dp 圆角与 `titleLargeEmphasized`。
  - 友链卡 / 聚合卡改为 20dp。
  - 聚合加载态改为 `Ech0Loading`。
- 我的：
  - 账号卡使用 32dp 圆角与 `titleMediumEmphasized`。
  - 菜单卡与设置分类卡改为 20dp。
  - 设置分类标题、分组标题、SettingSection 标题使用 emphasized 样式。
- 设置：
  - 13 处 Segmented 全部替换为 `Ech0ChoiceGroup`。
  - 设置首页 → 二级分类 / 搜索页使用 shared-axis X 进入退出转场，返回方向自动反向。
  - 顶栏标题使用 `AnimatedContent` 交叉淡入。
  - 网络 / 存储 / 阅读 / 外观 / 编辑 / 评论 / 高级选项保持原有设置逻辑与持久化。
  - 200% 字号下已检查设置首页、网络页与选择组，未出现裁切。
- 月面板：
  - 选中月份从 14dp 变为 24dp 圆角，未选中 8dp，使用 `fastSpatialSpec()`。
- 悬浮 dock：
  - 选中项增加 `secondaryContainer` 胶囊、圆角 12→22dp、颜色/形状弹簧。
  - 增加 `Role.Tab` 与 `selected` 语义。
  - 200% 字号下隐藏文字标签，只保留图标 + contentDescription，避免竖排和裁切。
- 标签：
  - 可点击标签改为填充式 `SuggestionChip`，只读标签使用同色 Surface。
- 编辑器：
  - Sheet 顶部圆角 32dp。
  - 展开 / 拖拽 settle / 内容尺寸动画改用 `MotionScheme`，并尊重减少动效。
  - 顶栏简化为「取消（长按存草稿并提示「草稿已保存」） | 写说说 | 发表」，移除右侧下拉菜单；自动保存显示「草稿已自动保存」。
  - 图片画质 / 格式档位：压缩（30%）/ 原图 / 自定义，WebP / JPG / PNG / 原格式；画质和格式都改为点击弹出列表，设置页可改默认值。画质模式与百分比分开存储，自定义 30% 仍保持自定义。
  - 图片编辑：比例裁剪（原图 / 1:1 / 4:3 / 16:9）、拖动缩放、90° 旋转；编辑只改本地文件，发布时才上传。
  - 图片上传时机：添加、恢复草稿、保存草稿都不上传；点击「发表」后统一压缩并上传。
- 详情：
  - 点赞动画改用 `MotionScheme.fastSpatialSpec()`，尊重减少动效。
  - 覆盖页进入详情改为容器变换式缩放 + 淡入，不再长距离横滑。
  - 评论标题改为 emphasized，排序改为单个图标按钮；点击后 Snackbar 提示「最新在前 / 最早在前」。
- 空态：
  - EmptyStateCard 图标容器改用 `MaterialShapes.Arch`，作为一处克制的形状库表达。
- 刷新：
  - `RefreshIconButton` 在减少动效下停止旋转。
- 远程详情 / 背景裁剪：
  - 屏幕级进度改为 `Ech0Loading`。

## 代码扫描结果

- `SingleChoiceSegmentedButtonRow`：0 处业务使用。
- 屏幕级 `CircularProgressIndicator`：已替换为主要页面（按钮内 16–18dp 小 spinner 保留）。
- `spring(`：主要自定义动画已收敛到 `MotionScheme`；仍有个别非 M3E 动画可后续清理。

## 待办（P2/P3）

- 时间线：评估 `LargeFlexibleTopAppBar`；真正的共享元素卡片 → 详情。
- 详情：可选 `SharedTransitionLayout`；远程详情同步排序快捷组。
- 编辑器：上传状态改用 `LinearWavyProgressIndicator`（仅在不影响准确度时）。
- 设置：搜索页可评估 `SearchBar` 展开形态。
- 动效：剩余硬编码 `tween` / spring 统一为 `Ech0Motion` 或明确标注为 legacy transition。
- 可访问性：TalkBack、200% 字体、RTL、减少动效、低端机帧率回归。
- 发布：更新正式 Release 文案、版本号、截图与校验值；当前只做草案，不发布。

## 验收

```bash
./gradlew.bat :app:testDebugUnitTest :app:lintVitalRelease
./gradlew.bat :app:assembleRelease
```

模拟器：`ech0test`，覆盖安装 release APK，保留登录态、实例、草稿与设置。

## 本轮验证（2026-10-31）

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
