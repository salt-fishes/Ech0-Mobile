# Ech0 Mobile 设置中心 2.0 方案

> 目标版本：`0.6.0`（versionCode 12）  
> 范围：设置页重构、配置项补齐、实例/账号管理、网络与缓存策略、编辑与评论默认值、诊断与隐私  
> 交付：release APK，仅本地产出，不创建 GitHub Release / tag，不上传发布渠道  
> 前置版本：`0.5.3`（当前 versionCode 11）

---

## 1. 版本目标

`0.6.0` 不只是“给设置页加几个开关”，而是把设置从“能用”升级为“可管理”：

1. **不再有危险操作**：测试连接不会偷偷切换实例；切换实例必须明确确认。
2. **已有能力有入口**：自动更新、默认图片质量、Token 到期、实例信息都能在设置里看到。
3. **配置更细**：阅读、外观、流量、编辑、评论都有可调项。
4. **缓存可解释**：页面缓存、图片缓存、草稿、临时文件分开统计、分开清理。
5. **高级能力可诊断**：慢速实例、Wi-Fi 限制、设置导入导出、请求诊断。
6. **长期可维护**：设置分组、搜索、默认值、迁移、测试都有明确规则。

### 非目标

- 不修改服务端 API 协议。
- 不做多账号同时在线。
- 不做云同步设置。
- 不自动发布 APK；只产出本地构建物。

---

## 2. 当前设置评估

当前 `SettingsScreen` 共 9 组：

| 分组 | 现状 | 主要问题 |
|---|---|---|
| 实例 | 地址输入 + 保存 + 版本文字 | 测试连接会直接保存；切换实例无确认；版本可能不刷新 |
| 网络 | Hello 延迟检查 | 只测一个接口，不能保存前测试；无慢速实例选项 |
| 存储 | 缓存/草稿/应用/空间 + 清理 | 页面缓存和图片缓存合并；应用占用与缓存重叠；无自动清理 |
| 阅读 | 4 档字号 | 无滑杆、行距、代码字体、链接打开方式 |
| 外观 | 主题、动态色、色盘、背景 | 无 AMOLED、高对比、减少动画；模糊开关无系统版本限制说明 |
| 流量 | 低流量模式 | 只有一个总开关，无 Wi-Fi/预加载/媒体细分 |
| 编辑 | 自动保存草稿 | 无默认图片质量、布局、可见性、上传并发、EXIF |
| 评论身份 | 昵称/邮箱/网站 | 全局一份，无评论排序、发送确认、评论草稿清理 |
| 高级 Token | 粘贴 Access Token | 无自动更新、导出导入、重置、诊断、退出登录 |

### 必须优先修的逻辑问题

1. **网络检查会切换实例**
   - `MeScreens.kt:481-515` 的 `checkNetwork()` 先调用 `Ech0Store.setBaseUrl(baseUrl)` 再测试。
   - 现在切换 host 会清 token/cookie/缓存，错误地址试一次就可能退出登录。
   - 目标：测试只读，不写入；测试成功后再提供“保存并切换”。

2. **保存地址无风险提示**
   - 当前只提示“实例地址已保存”。
   - 目标：host/协议变化时弹出确认，明确会清登录态、cookie、页面缓存。

3. **已有配置没有入口**
   - `autoCheckAppUpdate`：自动检查更新开关
   - `compressImages`：默认图片质量来源
   - `tokenExpiresAt`：Token 到期时间
   - `serverName`：当前实例名
   - `githubRepo`：当前基本固定，可清理或隐藏

4. **存储统计口径重叠**
   - `cacheBytes` 包含 `cacheDir` + `filesDir/cache`
   - `appBytes` 也包含 `cacheDir`，导致“应用占用”和缓存重复计算
   - 目标：应用本体 / 页面缓存 / 图片缓存 / 草稿 / 日志 分开

5. **文案与行为不一致**
   - 低流量模式文案仍写“头像、图片、扩展卡封面”，但现在已经跳过扩展卡元数据
   - 背景模糊说明 Android 12+，但开关在低版本仍可点
   - 动态取色在 Android 12 以下禁用，但没有“为什么不可用”的说明

---

## 3. 目标信息架构

从 9 组收敛为 6 组，顶部加搜索：

```
设置
├─ 搜索设置
├─ 账号与实例
│  ├─ 当前账号
│  ├─ 当前实例
│  ├─ 测试连接 / 保存并切换
│  └─ 已保存实例
├─ 网络与流量
│  ├─ 网络诊断
│  ├─ 慢速实例兼容
│  ├─ 低流量模式
│  ├─ 仅 Wi-Fi 加载
│  ├─ 预加载下一页
│  └─ 缓存刷新间隔
├─ 缓存与存储
│  ├─ 页面缓存
│  ├─ 图片缓存
│  ├─ 草稿暂存
│  ├─ 日志 / 临时文件
│  ├─ 自动清理
│  └─ 上次刷新时间
├─ 阅读与外观
│  ├─ 字号 / 行距 / 代码字体
│  ├─ 链接打开方式
│  ├─ 主题 / 动态取色
│  ├─ 纯黑 / 高对比 / 减少动画
│  ├─ 色盘与预设色
│  └─ 背景 / 模糊 / 暗化
├─ 编辑与评论
│  ├─ 默认图片质量
│  ├─ 默认布局 / 可见性 / 标签
│  ├─ 上传并发 / EXIF / 重试
│  ├─ 自动保存草稿
│  ├─ 评论身份（按实例）
│  ├─ 评论排序 / 发送确认
│  └─ 评论草稿
└─ 高级与关于
   ├─ 自动检查更新
   ├─ 导出 / 导入设置
   ├─ 重置设置 / 清除登录 / 清除 Cookie
   ├─ 请求诊断
   ├─ 自定义 UA / 代理
   └─ 版本 / 开源许可
```

### 交互规则

- **开关 / 分段 / 滑杆**：即时生效，滑杆 180ms 防抖落盘。
- **文本输入**：显式保存，或失焦保存；输入框旁显示保存状态。
- **实例地址**：测试与保存分离；保存前有风险确认。
- **每组标题右侧**：提供“恢复默认”，二次确认。
- **顶部搜索**：按标题、副标题、关键词过滤；无结果时显示空状态。
- **变更提示**：重要设置变更后用 Snackbar 提示“已保存”，不打断当前页面。

---

## 4. 设置项清单

### 4.1 账号与实例

| 设置项 | 控件 | 默认值 | 说明 |
|---|---|---|---|
| 当前账号 | 卡片 | - | 头像、昵称、管理员标记、Token 到期 |
| 退出登录 | 按钮 | - | 清 token、Cookie、liked、页面缓存 |
| 清除 Cookie | 按钮 | - | 只清 Cookie，不影响草稿 |
| 当前实例 | 卡片 | - | 站名、URL、Echo 版本、连接状态 |
| 测试连接 | 按钮 | - | 不修改 baseUrl、不清缓存 |
| 保存并切换 | 按钮 | - | host/协议变化时确认；展示影响 |
| 已保存实例 | 列表 | 当前实例 | 添加、删除、快速切换 |
| Token 到期 | 文本 | - | 显示剩余时间；过期时提示重新登录 |
| 实例版本 | 文本 | - | 保存/测试后刷新 |

**切换实例确认文案：**

> 即将从 `旧站` 切换到 `新站`。  
> 当前登录状态、Cookie、页面缓存会被清除；草稿和发布队列会保留，但只有切回旧实例后才能发布。  
> 是否继续？

### 4.2 网络与流量

| 设置项 | 控件 | 默认值 | 说明 |
|---|---|---|---|
| 网络诊断 | 卡片 | - | DNS / TLS / HTTP / Hello 延迟 / API 版本 |
| 慢速实例兼容 | 开关 | 关 | 开启后 API read 超时 20s→60s，call 30s→90s |
| 低流量模式 | 开关 | 关 | 保持现有总开关 |
| 低流量：头像 | 开关 | 跟随总开关 | 总开关关闭时可单独控制 |
| 低流量：正文图片 | 开关 | 跟随总开关 |  |
| 低流量：扩展卡封面 | 开关 | 跟随总开关 |  |
| 低流量：扩展卡元数据 | 开关 | 跟随总开关 | GitHub / B站 / 推文 / 音乐 |
| 仅 Wi-Fi 加载图片 | 开关 | 关 | 移动网络下显示占位图 |
| 仅 Wi-Fi 播放媒体 | 开关 | 关 | 视频 / 音乐 / WebView |
| 仅 Wi-Fi 上传 | 开关 | 关 | 移动网络下保留队列 |
| 预加载下一页 | 开关 | 开 | 关闭后只在触底时请求 |
| 缓存刷新间隔 | 选择 | 3 分钟 | 3 / 10 / 30 分钟 |
| 数据用量 | 文本 | - | 本次启动 / 本月请求数与字节数（可选） |

**慢速实例实现：**

- 不重建全局 client。
- 在请求创建后按 `Ech0Store.current.slowInstanceMode` 设置 `Call.timeout().timeout(...)`。
- 上传仍使用独立长超时，不受慢速实例开关影响。
- 图片 client 保持 30s，避免把图片超时拉得过长。

### 4.3 缓存与存储

| 设置项 | 控件 | 默认值 | 说明 |
|---|---|---|---|
| 页面缓存 | 文本 + 清理 | - | Timeline / Hub / Discover / Detail |
| 图片缓存 | 文本 + 清理 | - | Coil 磁盘缓存 |
| 草稿暂存 | 文本 + 清理 | - | 只清未被引用的暂存图 |
| 日志 / 临时文件 | 文本 + 清理 | - | 分享图、上传临时文件 |
| 应用本体 | 文本 | - | 不包含缓存 |
| 上次刷新 | 文本 | - | 各页最后成功刷新时间 |
| 图片缓存上限 | 选择 | 150MB | 150 / 300 / 500MB |
| 自动清理 | 开关 | 开 | 低于可用空间阈值时清详情/图片缓存 |
| 清理全部缓存 | 按钮 | - | 二次确认，保留草稿和登录态 |

**统计口径：**

```
缓存 = 页面缓存 + 图片缓存
应用 = filesDir 中排除 cache 的部分
草稿 = draft_images
临时 = cacheDir 中排除 image_cache
```

### 4.4 阅读

| 设置项 | 控件 | 默认值 | 说明 |
|---|---|---|---|
| 正文字号 | 滑杆 85%–130% | 100% | 替代 4 档按钮 |
| 行距 | 分段 | 标准 | 紧凑 / 标准 / 宽松 |
| 代码字体 | 分段 | 等宽 | 系统等宽 / 衬线等宽（可选） |
| 链接打开方式 | 分段 | 内置 | 内置浏览器 / 系统浏览器 |
| 信息流图片质量 | 分段 | 标准 | 标准 / 高清 / 原图 |
| 自动播放 GIF | 开关 | 开 | 低流量/仅 Wi-Fi 时遵守网络策略 |
| 自动播放视频 | 开关 | 关 | 默认不自动播放 |
| 相对时间 | 开关 | 开 | 关闭后显示绝对时间 |

### 4.5 外观

| 设置项 | 控件 | 默认值 | 说明 |
|---|---|---|---|
| 主题模式 | 分段 | 跟随系统 | 浅色 / 深色 / 跟随 |
| 动态取色 | 开关 | 开 | Android 12+ |
| 纯黑模式 | 开关 | 关 | 深色下使用纯黑背景 |
| 高对比度 | 开关 | 关 | 增强文字/边框对比 |
| 减少动画 | 开关 | 关 | 关闭弹簧、缩放、共享轴动画 |
| 状态栏图标 | 分段 | 自动 | 自动 / 浅色 / 深色 |
| 主题色 | 色盘 | #0B57D0 | 预设色 + 最近使用 |
| 背景 | 预设/自定义 | 默认 | 4 套预设 + 自定义图 |
| 背景模糊 | 开关 | 关 | Android 12+；低版本禁用并说明 |
| 背景暗化 | 滑杆 | 0% | 0–60% |

**减少动画范围：**

- Tab 切换、覆盖页 shared-axis、列表 animateItem、点赞脉冲
- 保留必要的进度指示和无障碍焦点动画

### 4.6 编辑

| 设置项 | 控件 | 默认值 | 说明 |
|---|---|---|---|
| 自动保存草稿 | 开关 | 开 | 保持现有逻辑 |
| 默认图片质量 | 分段 | 标准 82 | 标准 / 高清 92 / 原图 |
| 默认布局 | 分段 | waterfall | 6 种布局 |
| 默认可见性 | 分段 | 公开 | 公开 / 私密 |
| 默认标签 | 标签选择 | 空 | 打开编辑器时预选 |
| 上传并发 | 分段 | 2 | 1 / 2 / 3 |
| 上传前移除 EXIF | 开关 | 开 | 压缩时去除位置等元数据 |
| 上传失败重试 | 分段 | 1 次 | 0 / 1 / 2 |
| 发布前确认 | 开关 | 关 | 点击发表后二次确认 |

**实现注意：**

- `ComposerSheet` 当前 `quality` 从 `compressImages` 推导，改为读取 `defaultImageQuality`。
- `uploadSemaphore` 当前固定 `Semaphore(2)`，改为按设置创建（每个编辑器会话）。
- EXIF 仅在压缩路径处理；选择“原图”时保留原始文件，不强制去 EXIF，但 UI 要说明。
- 发布确认不能阻止队列发布，只影响手动发布。

### 4.7 评论

| 设置项 | 控件 | 默认值 | 说明 |
|---|---|---|---|
| 自动填写默认身份 | 开关 | 开 | 现有逻辑 |
| 昵称 / 邮箱 / 网站 | 输入框 | - | 现有逻辑 |
| 身份作用域 | 文本 | 当前实例 | 按实例保存，不跨实例复用 |
| 评论排序 | 分段 | 最早 | 最早 / 最新 / 热门（若服务端支持） |
| 发送前确认 | 开关 | 关 | 防止误触 |
| 清除评论草稿 | 按钮 | - | 清理本地输入缓存 |
| 邮箱隐私提示 | 文本 | - | 明确“不会公开” |

**按实例保存：**

- key：`baseUrl` + 账号（或 guest）
- 切换实例后读取对应身份；没有则回退全局默认或空
- 不把 A 站邮箱自动带到 B 站

### 4.8 高级与关于

| 设置项 | 控件 | 默认值 | 说明 |
|---|---|---|---|
| 自动检查更新 | 开关 | 开 | 现有 24h 节流 |
| 检查间隔 | 分段 | 24h | 24h / 7d / 手动 |
| 立即检查更新 | 按钮 | - | 复用 About 检查逻辑 |
| 导出设置 | 按钮 | - | JSON，排除 token / cookie |
| 导入设置 | 按钮 | - | 校验版本与字段，不导入秘密 |
| 重置所有设置 | 按钮 | - | 二次确认；保留草稿/队列 |
| 清除登录 | 按钮 | - | 只清账号相关 |
| 请求诊断 | 页面 | - | 最近错误、耗时、重试次数 |
| 自定义 User-Agent | 输入 | 浏览器 UA | 高级；错误值可能影响图床 |
| 代理 | 输入 | 空 | `host:port`，仅高级用户 |
| 版本 / 构建 | 文本 | - | versionName / versionCode / commit |
| 开源许可 | 链接 | - | 打开 licenses |

---

## 5. 数据模型与迁移

### 5.1 新增 `Ech0State` 字段

```kotlin
// 网络 / 流量
val slowInstanceMode: Boolean = false
val wifiOnlyImages: Boolean = false
val wifiOnlyMedia: Boolean = false
val wifiOnlyUploads: Boolean = false
val preloadNextPage: Boolean = true
val feedCacheTtlMinutes: Int = 3
val lowDataAvatars: Boolean = true
val lowDataImages: Boolean = true
val lowDataExtensionCovers: Boolean = true
val lowDataExtensionMeta: Boolean = true

// 阅读 / 外观
val lineHeightScale: Float = 1f
val codeFont: String = "monospace"
val linkOpenMode: String = "inApp"
val autoPlayGif: Boolean = true
val autoPlayVideo: Boolean = false
val showRelativeTime: Boolean = true
val amoledBlack: Boolean = false
val highContrast: Boolean = false
val reduceMotion: Boolean = false
val statusBarIconMode: String = "auto"
val backgroundDim: Float = 0f

// 编辑
val defaultImageQuality: Int = 82
val defaultLayout: String = "waterfall"
val defaultPrivate: Boolean = false
val defaultTags: List<String> = emptyList()
val uploadConcurrency: Int = 2
val stripExif: Boolean = true
val uploadRetryCount: Int = 1
val publishConfirm: Boolean = false

// 评论
val commentSort: String = "oldest"
val commentConfirm: Boolean = false

// 更新 / 高级
val updateCheckIntervalHours: Int = 24
val customUserAgent: String = ""
val proxyUrl: String = ""
val diagnosticsEnabled: Boolean = false
```

### 5.2 按实例保存的数据

这些数据不要放进全局 `Ech0State`，单独用 JSON 或命名空间保存：

- 评论身份：`baseUrl + viewer -> nickname/email/website`
- 慢速实例：`Set<host>` 或 `Map<baseUrl, Boolean>`
- 已保存实例：`List<SavedInstance(baseUrl, name, lastUsedAt)>`
- 用户自定义标签：可按实例/账号保存，也可全局

### 5.3 迁移规则

- DataStore schema 只增不减。
- 旧 `compressImages`：
  - `true` → `defaultImageQuality = 82`
  - `false` → `defaultImageQuality = 0`（原图）
- 旧 `promptSaveDraft`：保持语义，UI 仍显示“自动保存草稿”。
- 旧 `lowDataMode`：作为总开关；子项默认跟随总开关。
- 旧 `autoCheckAppUpdate`：保留；新增间隔默认 24h。
- 旧背景/主题/字号：保持不变。
- 当前实例自动加入“已保存实例”列表。
- 不因升级清缓存、清 token、清草稿。
- 新增字段都有默认值，旧版本回滚时未知字段被忽略。

---

## 6. 代码结构

建议把 70KB 的 `MeScreens.kt` 拆开：

```
ui/settings/
├─ SettingsScreen.kt          # 搜索、分组、导航
├─ SettingsSearch.kt          # 搜索索引
├─ AccountInstanceSection.kt
├─ NetworkTrafficSection.kt
├─ StorageSection.kt
├─ ReadingAppearanceSection.kt
├─ EditorCommentSection.kt
├─ AdvancedSection.kt
├─ SettingsComponents.kt      # SettingRow / SectionCard / ResetButton
└─ SettingsActions.kt         # 测试连接、切换实例、导出导入
```

配套：

```
data/settings/
├─ SettingsKeys.kt            # DataStore key
├─ SettingsMigration.kt       # 旧字段迁移
├─ SavedInstancesStore.kt     # 多实例
├─ CommentIdentityStore.kt    # 按实例评论身份
└─ SettingsExport.kt          # 安全导出/导入
```

### 关键改动文件

- `app/build.gradle.kts`：versionCode 12，versionName `0.6.0`
- `Ech0Store.kt`：新增字段、key、setter、迁移
- `MeScreens.kt`：拆分设置页
- `Design.kt`：AMOLED、高对比、减少动画、背景暗化
- `PostStore.kt`：TTL 可配置
- `HttpClients.kt` / `Api.kt`：慢速实例超时、UA/代理
- `ComposerSheet.kt`：默认质量/布局/可见性/并发/EXIF/确认
- `Compressor.kt`：EXIF 处理
- `CommentComposer.kt` / `CommentThreads.kt`：排序、确认、身份作用域
- `AppRoot.kt`：更新检查间隔、减少动画
- `Ui.kt` / `FeedUi.kt`：时间显示、链接打开方式

---

## 7. 网络策略实现

### 7.1 `NetworkPolicy`

新增一个集中判断，避免每个页面自己读设置：

```kotlin
object NetworkPolicy {
    fun canLoadImages(): Boolean
    fun canPlayMedia(): Boolean
    fun canUpload(): Boolean
    fun canPreload(): Boolean
    fun apiTimeout(): TimeoutConfig
}
```

- 结合 `ConnectivityManager` 判断当前是否计费网络。
- Coil 请求、媒体播放、上传、预加载统一走这里。
- 低流量模式与“仅 Wi-Fi”是“与”的关系：任何一个限制生效就不加载。

### 7.2 慢速实例

```kotlin
val call = client.newCall(request)
if (Ech0Store.current.slowInstanceMode) {
    call.timeout().timeout(90, TimeUnit.SECONDS)
}
call.await()
```

- 只影响 API 读请求。
- 上传保持 180s write / 300s call。
- 图片保持 45s call。
- 避免为设置重建 client 和连接池。

### 7.3 缓存 TTL

- `PostStore.PageTtlMs` 从常量改为读取 `feedCacheTtlMinutes`。
- `DiskCache` 的页面缓存 TTL 与 Store 保持一致。
- 设置变化后清空内存查询页，避免旧 TTL 继续生效。

### 7.4 更新检查

- `AppRoot` 使用 `updateCheckIntervalHours`：
  - 24h / 7d / 手动
- “手动”时自动检查只在用户点“立即检查”时执行。
- 手动检查永远绕过节流。

---

## 8. 多实例与草稿/队列

- `SavedInstance` 只存 URL、显示名、最后使用时间，不存 token。
- 切换实例仍走现有 `Ech0Store.setBaseUrl` 清理逻辑。
- 草稿/队列已经带 `baseUrl`：
  - 当前实例优先显示
  - 其他实例条目显示“属于 xxx”，只读或提示切回
- 评论身份按实例隔离。
- 已保存实例不自动登录；token 仍由各 host 的 cookie/token 决定。
- 删除已保存实例不删除该实例缓存；提供单独“清理该实例缓存”。

---

## 9. 诊断与隐私

### 9.1 请求诊断

Debug / 高级页面展示：

- 最近 20 条请求：method、host、path、status、耗时、bytes、retry
- 最近错误：时间、错误码、用户可读消息
- 当前 client 配置：超时、连接数、缓存命中
- 不记录 Authorization、Cookie、请求体

### 9.2 导出 / 导入

导出 JSON 示例：

```json
{
  "schema": 1,
  "versionName": "0.6.0",
  "settings": {
    "themeMode": "system",
    "textScale": 1.0,
    "lowDataMode": false
  }
}
```

- 排除：token、cookies、likedIds、草稿、队列、缓存。
- 导入时校验 schema/字段类型，未知字段忽略。
- 导入后提示“需要重启页面生效”的项。

### 9.3 重置

- 重置所有设置：保留登录、草稿、队列、缓存。
- 清除登录：清 token/cookie/user/liked。
- 清理缓存：页面 + 图片 + 临时文件，保留草稿。
- 清除所有本地数据：二次确认，删除草稿/队列/缓存/设置；不删除服务端内容。

---

## 10. 实施阶段

| 阶段 | 内容 | 预计 | 产出 |
|---|---|---|---|
| 0 | 设计定稿、字段清单、迁移方案 | 1–2d | 本方案 + 字段表 |
| 1 | 设置页骨架、搜索、分组、P0 修复 | 3–4d | 新设置页可运行 |
| 2 | 账号/实例、多实例、网络诊断、慢速实例 | 4–5d | 实例管理闭环 |
| 3 | 存储、阅读、外观、减少动画 | 4–6d | 配置项完整 |
| 4 | 编辑、评论、导出导入、诊断 | 5–7d | 功能齐全 |
| 5 | 单元/UI/模拟器测试、release APK | 3–5d | `0.6.0` APK |

单人总计约 **3–4 周**。可与网络优化后续项并行，但不建议压缩测试阶段。

### 内部里程碑

- `0.6.0-alpha1`：设置页骨架 + P0 修复
- `0.6.0-alpha2`：账号/实例/网络/存储
- `0.6.0-beta1`：阅读/外观/编辑/评论
- `0.6.0-rc1`：高级/诊断/导出导入
- `0.6.0`：测试通过，产出 APK

> 只产出 APK，不创建 GitHub Release / tag，不上传发布渠道。

---

## 11. 验收标准

### 功能性

- [ ] 测试连接不修改 baseUrl、不清 token/cache。
- [ ] 切换实例必须确认；确认后只清当前实例相关状态。
- [ ] 已有设置升级后保持不变。
- [ ] 所有新增设置重启 App 后保持。
- [ ] 设置搜索能定位所有分组和设置项。
- [ ] 低流量/仅 Wi-Fi 对 Coil、媒体、扩展卡、上传都生效。
- [ ] 慢速实例只放宽 API 超时，不影响上传/图片。
- [ ] 存储四类数据统计准确，分开清理互不影响。
- [ ] 导出不含 token/cookie/草稿；导入不覆盖秘密。
- [ ] 评论身份按实例隔离。
- [ ] 默认图片质量/布局/可见性/标签在编辑器打开时生效。
- [ ] 减少动画关闭后主要动效降级，功能不受影响。
- [ ] 纯黑/高对比在深色模式下可读性正常。

### 非功能性

- [ ] 设置页滚动 60fps，无明显 jank。
- [ ] 设置持久化不阻塞主线程。
- [ ] 无新增 lint error。
- [ ] 单元测试覆盖设置迁移、网络策略、导出导入、超时选择。
- [ ] 模拟器完成：实例切换、低流量、仅 Wi-Fi、上传、评论、重置。
- [ ] release APK 已签名，versionName `0.6.0`，versionCode 12。
- [ ] 未创建 GitHub Release / tag，未上传发布渠道。

---

## 12. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 设置项过多 | 页面难找、维护难 | 6 组 + 搜索 + 每组默认值 |
| 全局/实例作用域混淆 | 身份或超时串实例 | 明确区分 Global / Instance / Account 三类 key |
| 仅 Wi-Fi 与 Coil 缓存冲突 | 已缓存图片仍显示 | 只限制网络请求，不限制已缓存内容 |
| 慢速实例实现复杂 | 重建 client 导致连接池失效 | 使用 per-call timeout，不重建 client |
| EXIF 处理影响原图 | 用户预期不符 | 原图模式保留原文件；压缩模式说明会去 EXIF |
| 导出导入泄露秘密 | 隐私风险 | 明确排除 token/cookie，导入校验 |
| 多实例切换误发草稿 | 发到错误站点 | 现有 DraftPayload.baseUrl + Worker 校验 |
| 减少动画影响可用性 | 过渡生硬 | 只关闭装饰性动画，保留进度和焦点动画 |
| 代理/UA 配置错误 | 请求失败 | 高级设置 + 校验 + 一键恢复默认 |

---

## 13. 任务拆分

| ID | 任务 | 优先级 | 预估 | 依赖 |
|---|---|---|---|---|
| SET-001 | 版本号/字段/迁移 | P0 | 2d | - |
| SET-002 | 设置页骨架、分组、搜索 | P0 | 2d | SET-001 |
| SET-003 | 测试连接不保存 + 切换确认 | P0 | 2d | SET-001 |
| SET-004 | 账号/实例卡片、Token 到期 | P1 | 2d | SET-003 |
| SET-005 | 多实例保存与切换 | P1 | 3d | SET-004 |
| SET-006 | 网络诊断 + 慢速实例 | P1 | 3d | SET-001 |
| SET-007 | 低流量细分 + 仅 Wi-Fi | P1 | 3d | SET-006 |
| SET-008 | 缓存 TTL + 预加载开关 | P1 | 2d | SET-001 |
| SET-009 | 存储分项统计/清理/自动清理 | P0 | 3d | SET-001 |
| SET-010 | 阅读：字号滑杆/行距/链接 | P1 | 2d | SET-002 |
| SET-011 | 外观：AMOLED/高对比/减少动画 | P1 | 3d | SET-002 |
| SET-012 | 编辑默认值/并发/EXIF/确认 | P1 | 4d | SET-001 |
| SET-013 | 评论身份/排序/确认/草稿 | P1 | 3d | SET-001 |
| SET-014 | 自动更新开关/间隔 | P0 | 1d | SET-002 |
| SET-015 | 导出/导入/重置 | P2 | 3d | SET-001 |
| SET-016 | 请求诊断/日志 | P2 | 2d | SET-006 |
| SET-017 | 单元测试/UI 测试 | P0 | 4d | 全部 |
| SET-018 | 模拟器 QA + release APK | P0 | 2d | 全部 |

---

## 14. 交付物

- 源码改动：设置页 2.0、数据字段、迁移、网络策略、编辑/评论默认值。
- 文档：本方案 + 更新后的 `docs/GUIDE.md` 设置章节。
- 测试：`testDebugUnitTest` 通过；模拟器冒烟记录。
- APK：
  - `dist/ech0-mobile-0.6.0.apk`
  - `dist/ech0-mobile-0.6.0.apk.sha256`
- 发布状态：**不发布**。不创建 GitHub Release/tag，不上传任何渠道。
- 版本号：
  - `versionName = "0.6.0"`
  - `versionCode = 12`

---

## 15. 建议的首批落地顺序

如果先做一版可用的 `0.6.0-alpha1`，建议只做：

1. 设置页 6 组骨架 + 搜索
2. 测试连接不保存 + 切换确认
3. 自动检查更新开关
4. 存储分项统计 + 分开清理
5. 默认图片质量
6. 低流量文案修正
7. 背景模糊系统版本提示

这 7 项不改服务端协议，能最快把“危险操作”和“明显缺口”解决，再继续账号卡、网络诊断和外观细化。

---

## 16. 实施进度（2026-10-31）

### 0.6.0 全部批次已完成

> 以下 alpha1 七项为基础，剩余 P1/P2 设置项也已在同一版本完成。

- [x] 设置页收敛为 6 组 + 顶部搜索
  - 账号与实例
  - 网络与流量
  - 缓存与存储
  - 阅读与外观
  - 编辑与评论
  - 高级与关于
- [x] 测试连接改为只读探测
  - 新增 `Ech0Api.probeHello` / `probeSettings`
  - 不写入 `baseUrl`
  - 不清 token、Cookie、页面缓存
- [x] 切换实例确认
  - host / 协议变化时弹出确认
  - 明确提示会清登录状态、Cookie、页面缓存
  - 同 host 只改路径直接保存
- [x] 自动检查更新开关
  - 设置页可开关
  - 保留 24 小时节流
- [x] 存储分项统计与分开清理
  - 页面缓存
  - 图片缓存
  - 临时文件
  - 草稿暂存
  - 应用数据
  - 可用空间
- [x] 默认图片质量
  - 标准 82 / 高清 92 / 原图
  - 旧 `compressImages` 自动映射
  - 编辑器打开时读取默认值
- [x] 低流量文案修正
  - 说明不加载头像和正文图片
  - 说明不预取 GitHub / B 站 / 推文 / 音乐扩展元数据
- [x] 背景模糊系统版本提示
  - Android 12 以下禁用开关并说明原因

### 设置页结构

- [x] 首页分类卡：5 个分组入口（账号与实例已移到「我的」顶部）
- [x] 二级设置页：进入分组后只看该组设置，顶部返回
- [x] 顶部搜索按钮：右上角图标进入独立搜索页，首页不再常驻搜索框
- [x] 系统返回键：搜索页 → 分类首页 → 关闭设置；从「我的」进入账号与实例时返回直接回到「我的」
- [x] 分组摘要：网络策略、缓存大小、字号主题、编辑默认值、版本与更新

### 账号、网络与诊断

- [x] 账号卡：头像、昵称、管理员标记、Token 剩余天数
- [x] 退出登录 / 清除 Cookie
- [x] 多实例收藏：保存地址与显示名，不保存 Token；支持切换和删除
- [x] 慢速实例兼容：API 读超时放宽到 90 秒，上传/图片不受影响
- [x] 仅 Wi-Fi：图片、媒体播放、上传分别控制
- [x] 预加载下一页开关
- [x] 信息流缓存间隔：3 / 10 / 30 分钟
- [x] 低流量细分：头像、正文图片、扩展卡封面、扩展卡元信息
- [x] 网络诊断：DNS / TCP / TLS / HTTP `/api/hello` 四段耗时
- [x] 请求诊断：最近 20 条请求的方法、host、路径、状态、耗时、字节数，不记录 Token/请求体
- [x] 自定义 User-Agent
- [x] HTTP 代理：`host:port`，动态 ProxySelector，无需重启

### 存储与外观

- [x] 图片磁盘缓存上限：150 / 300 / 500 MB（下次启动生效）
- [x] 可用空间低于 500 MB 自动清理图片与页面缓存
- [x] 上次刷新时间
- [x] 字号滑杆 85%–130%
- [x] 行距：紧凑 / 标准 / 宽松
- [x] 代码字体：等宽 / 衬线
- [x] 链接打开方式：应用内 WebView / 系统浏览器；应用内关闭后返回原页面
- [x] 信息流图片质量：标准 / 高清 / 原图
- [x] 自动播放 GIF / 视频
- [x] 相对时间 / 绝对时间
- [x] 纯黑 AMOLED、高对比、减少动画
- [x] 状态栏图标：自动 / 浅色 / 深色
- [x] 背景暗化 0–60%
- [x] 主题色预设

### 编辑与评论

- [x] 默认图片布局：瀑布流 / 网格 / 横向 / 轮播 / 堆叠 / 无
- [x] 默认私密、默认标签
- [x] 同时上传数量：1 / 2 / 3
- [x] 上传前移除 EXIF（原图模式会高质量重编码）
- [x] 上传失败重试：0 / 1 / 2 次
- [x] 发布前确认
- [x] 评论身份按实例 + 账号隔离，旧全局身份只迁移一次
- [x] 评论排序：最早 / 最新
- [x] 发送评论前确认
- [x] 评论草稿自动保存，设置页可清除

### 高级与维护

- [x] 自动检查更新开关 + 间隔：24 小时 / 7 天 / 手动
- [x] 立即检查更新，发现新版本可打开 GitHub
- [x] 导出设置：JSON，排除 Token、Cookie、草稿、队列、评论邮箱
- [x] 导入设置：字段级校验，未知字段忽略
- [x] 重置所有设置：保留登录、实例地址、草稿、队列、缓存
- [x] 清除登录

### 验证结果

- `:app:testDebugUnitTest`：通过
- `:app:assembleRelease`：通过
- 模拟器实测：
  - 错误地址测试连接失败后，地址仍保持原值
  - 切换实例弹出确认并刷新站点名 / 版本
  - 搜索“缓存”“更新”“图片质量”“诊断”“导出”能定位分组
  - 自动检查更新开关重启后保持
  - 默认图片质量设为高清后，编辑器显示“画质：高清”
  - 默认标签应用到新编辑器
  - 网络诊断返回 DNS / TCP / TLS / HTTP 结果
  - 请求诊断显示最近请求和耗时
  - 导出 JSON 不含 Token / Cookie / 评论邮箱；导入成功
  - 重置设置后登录态和实例地址保留，导入可恢复
  - 设置首页分类卡与二级设置页正常
  - 右上角搜索按钮进入独立搜索页
  - 月份面板只显示实际有内容的月份，并提供“加载更早”
  - 评论草稿关闭详情后重新打开仍存在
  - 应用内浏览器能打开 GitHub，返回后回到原页面
  - AMOLED 深色背景生效
- Lint：仍为原有 4 个错误，无新增 error

### 已知限制

- 请求诊断只保留最近 20 条，不做月度流量统计
- 图片缓存上限和 GIF 解码开关需要下次启动生效
- 评论“热门”排序依赖服务端参数，当前只提供最早 / 最新
- 代码字体只影响 Markdown 行内代码和代码块

---

## 17. 发布产物

- APK：`dist/ech0-mobile-0.6.0.apk`
- SHA-256：`190dd76231c2780be3ccbef44383c9133cbc36dc54bdc11ed394e4adc38f8622`
- 包名：`com.saltfish.echo`
- versionName：`0.6.0`
- versionCode：`12`
- 签名：release keystore，APK Signature Scheme v2
- 已创建 GitHub Release：`v0.6.0`
- Release 资产：`ech0-mobile-0.6.0.apk`、`ech0-mobile-0.6.0.apk.sha256`
- 已在 `ech0test` 模拟器覆盖安装，登录态、实例地址、自动更新开关、默认图片质量、评论草稿和已保存实例均保留
