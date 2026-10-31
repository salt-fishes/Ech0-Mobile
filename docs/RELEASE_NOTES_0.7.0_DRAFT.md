# Ech0 Mobile v0.7.0 更新草案

> 状态：草案，未发布。  
> 目标版本：`0.7.0`（当前代码仍为 `0.6.0`，尚未 bump）。  
> 本文只用于整理 M3E 改造内容，不代表已构建或已发布。

## 概览

v0.7.0 计划以 **Material 3 Expressive 2.0** 为主题：统一设置选择组、圆角层级、强调字体和弹簧动效，并在编辑器、详情、月面板和悬浮 dock 上加入更明确的 M3E 表达。

这次改造不重做导航和视觉品牌，保留纸张/雾面背景、悬浮 dock 和现有设置结构。

## 亮点

- **设置选择组升级**：13 处已弃用的 `SegmentedButton` 替换为 Connected Button Group（`Ech0ChoiceGroup`），选中态更圆、更紧凑。
- **圆角层级**：feed / 设置卡 20dp，Hero / 账号卡 28–32dp，编辑器 sheet 32dp，减少卡片圆角混乱。
- **强调字体生效**：行距缩放现在同时作用于 15 个 baseline 与 15 个 emphasized 样式。
- **减少动效统一**：新增 `LocalReduceMotion`，点赞、编辑器展开、发现随机卡、刷新按钮等统一读取设置。
- **悬浮 dock 选中态**：新增胶囊背景、圆角 12→22dp 动画、颜色弹簧与 Tab 语义。
- **月份面板 shape morph**：选中月份 24dp、未选中 8dp，切换时有弹簧形变。
- **标签视觉**：可点击标签改为填充式 `SuggestionChip`，只读标签使用同色 Surface。
- **编辑器交互精简**：取消右侧下拉菜单；长按「取消」直接存入草稿箱并提示「草稿已保存」，短按保留原有确认逻辑；自动保存时编辑器内显示「草稿已自动保存」。
- **图片画质 / 格式档位**：编辑器内提供压缩 / 原图 / 自定义（压缩默认 30%）与 WebP / JPG / PNG / 原格式；画质和格式都改为点击后弹出列表选择。画质模式与百分比分开保存，自定义调到 30% 不会变成「压缩」。设置页可改默认画质与格式。
- **图片简单编辑**：支持比例裁剪（原图 / 1:1 / 4:3 / 16:9）、拖动缩放与 90° 旋转；编辑只改本地文件，发布时才上传。
- **详情转场**：进入详情改为缩放 + 淡入的容器变换式转场，不再长距离横滑。
- **评论排序**：详情评论标题使用 emphasized，排序改为单个图标按钮；点击后 Snackbar 提示「最新在前 / 最早在前」，不再依赖按钮点亮状态。
- **设置二级页转场**：设置首页 → 分类页 / 搜索页使用 shared-axis X 进入退出动效，顶栏标题交叉淡入，返回方向自动反向。
- **加载态**：主要页面和背景裁剪统一使用 `LoadingIndicator` / `Ech0Loading`。
- **空态形状**：空态图标容器使用 `MaterialShapes.Arch`，作为一处克制的形状库表达。

## 改进

- 设置分类卡、分组卡、详情评论卡、Hub 友链卡等统一为 `largeIncreased` 20dp。
- “我的”账号卡使用 32dp 与 `titleMediumEmphasized`。
- 设置分类标题、分组标题、Section 标题改用 emphasized 样式。
- 时间线 `EchoCard` 从 28dp 调整为 20dp，降低信息流卡片膨胀感。
- 发现 SectionCard 标题使用 `titleMediumEmphasized`，随机卡 20dp。
- 友链本站 Hero 使用 32dp 与 `titleLargeEmphasized`。
- 编辑器展开、拖拽 settle、内容尺寸动画改用 `MotionScheme`。
- 详情点赞动画改用 `fastSpatialSpec()`。
- 评论排序从两个 FilterChip 改为一个图标切换，减少标题行占用。
- 图片格式支持 WebP / JPG / PNG / 原格式；画质收敛为压缩（30%）/ 原图 / 自定义三档，自定义可拖动压缩比例。
- 图片在编辑器内只保留本地文件；点击「发表」后才批量压缩并上传，保存草稿不会触发上传。
- 图片编辑结果先写入 `cache/edited_images`，发布时再按当前画质 / 格式上传。
- 刷新按钮在减少动效下停止旋转。
- 200% 字号下悬浮 dock 隐藏文字标签，只保留图标与 contentDescription，避免竖排和裁切。

## 修复

- 行距设置不作用于 emphasized 字体。
- 设置页 13 处 SegmentedButton 与 M3E 规范不一致。
- 卡片圆角 16/28dp 混用，缺少统一层级。
- 点赞、编辑器、发现随机卡等自定义弹簧绕过 `MotionScheme`。
- 部分屏幕级加载仍使用旧 CircularProgressIndicator。
- 可点击标签与只读标签视觉不一致。

## 升级方式

> 尚未构建 v0.7.0 APK，以下为计划内容。

- 已安装旧版本：覆盖安装 `ech0-mobile-0.7.0.apk`，数据保留（需使用相同 release 签名）。
- 首次安装：允许安装未知来源应用后安装。
- 系统要求：Android 10（API 29）及以上。

## 校验

> 构建后填写。

```text
文件：ech0-mobile-0.7.0.apk
SHA-256：待构建后计算
```

## 发布状态

- [ ] 版本号 bump 到 `0.7.0`（versionCode +1）
- [x] 设置二级页进入退出转场、详情评论排序图标
- [x] 编辑器长按取消存草稿、图片画质 / 格式档位
- [x] 图片比例裁剪 / 旋转并重新上传
- [x] 200% 字号检查；修复 dock 标签裁切
- [ ] P2/P3 收尾：详情共享元素、剩余动效清理
- [ ] TalkBack、RTL、减少动效、低端机回归
- [ ] Release APK 构建与安装验证
- [ ] 截图与 CHANGELOG 定稿
- [ ] 正式 Release 文案与校验值
- [ ] 打 tag / 发布（本轮不做）

## 已知待办

- 详情页真正的 shared element 转场。
- 详情评论排序快捷组。
- 时间线 `LargeFlexibleTopAppBar` 评估。
- 上传进度是否使用 `LinearWavyProgressIndicator`。
- 剩余硬编码 tween/spring 的统一。
