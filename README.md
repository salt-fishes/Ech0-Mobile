# Ech0 Mobile

自托管说说平台 [Ech0](https://github.com/lin-snow/Ech0) 的**全功能** Android 原生客户端：随手写、随手发、随手看。交互对标 QQ 空间「写说说」——默认玻璃抽屉、可拖拽全屏、九宫格多图、权限切换、一键发布；视觉全面采用 **Material 3 Expressive**（`MaterialExpressiveTheme` + `MotionScheme.expressive()` 弹簧动效、Expressive 形变开关、波浪进度指示器、动态取色）。

> 调研与设计依据：`../Ech0-Mobile-调研报告.md`（含 Ech0 v5.8.2 API 全貌、设计资源细节、QQ 空间范式拆解、废案复盘）。
> 本项目是对废案 `blog/ech0-pocket`（发布器薄客户端）的重做：**全功能、账号登录、真权限、完整浏览体验**，不复用其代码。

## 已实现（对照 Ech0 功能矩阵）

| 模块 | 内容 |
|---|---|
| 时间线 | 分页流、关键词搜索、玻璃卡片、Markdown 渲染（粗斜删/行内代码/链接/话题/提及/标题/引用）、6 种图片布局（瀑布/网格/横向/轮播/堆叠/隐藏）、音频视频占位、标签 chips、点赞（限流幂等+本地去重+动效）、分享、长按删帖（管理员） |
| 写说说 | **抽屉默认形态 → 上拉/按钮全屏**（QQ 空间三段式顶栏：取消/写说说/发表）、Photo Picker 九宫格（≤9）、逐张 WebP 压缩（长边 2048·q82，可关原图）、逐张玻璃进度环+单张重试、话题标签（≤5）、公开/私密切换、图片布局选择、6 种扩展卡（WEBSITE/VIDEO/MUSIC/GITHUBPROJ/LOCATION/TWEET，payload 字段对照上游 typings）、补记时间（created_at）、草稿 600ms 自动保存、失败自动入队（WorkManager 指数退避，联网自动补发） |
| 详情/评论 | 全文+媒体+标签、点赞/复制链接、评论树（昵称/热评/待审核标记/相对时间）、回复（parent_id）、游客评论（form_token + 蜜罐 + **gocap PoW 验证码客户端实现**） |
| 发现 | Zen 沉浸瀑布流（StaggeredGrid）、30 天热力图、标签云、今日/热门/随机/那年今日 |
| Hub | 本站信息卡、Connect 友链实例列表、跨实例聚合时间线（按时间归并，来源徽标） |
| 我的/账号 | 用户名密码登录（JWT + refresh HttpOnly cookie 持久化 + 401 自动续期重放）、Access Token 高级登录、退出（吊销）、个人卡、草稿箱（继续编辑）、发布队列（重试/删除）、关于（实例版本） |
| 设置 | 实例地址 + 测试连接、外观（跟随系统/浅色/深色）、上传压缩开关 |
| 系统集成 | 桌面长按快捷方式「写一条」、相册/浏览器分享接收（文本/多图）直达编辑器、edge-to-edge + predictive back |
| 设计系统 | Material 3 Expressive（androidx.compose.material3 1.5.0-beta02）：MaterialExpressiveTheme + MotionScheme.expressive() 弹簧动效、FilledIconToggleButton 形变开关、LoadingIndicator/CircularWavyProgressIndicator 波浪指示器、NavigationBar/TopAppBar/ListItem/SegmentedButton/AlertDialog 全套 M3 组件、Android 12+ 动态取色 + 品牌蓝回落 |

## 工程结构

```
app/src/main/java/top/saltfish/ech0/
├── Ech0App / MainActivity        # 入口；快捷方式/分享意图 → 编辑器
├── data/
│   ├── Models.kt                 # DTO（字段名=JSON 键，对照 internal/model 逐一核实）
│   ├── Api.kt                    # OkHttp：信封解析/401自动refresh重放/multipart进度上传/gocap PoW/Hub远端分页
│   └── Ech0Store.kt              # DataStore + Keystore 加密令牌 + 持久化 CookieJar
├── media/Compressor.kt           # 长边2048/WebP q82，GIF与原图直传回退
├── publish/PublishStore.kt       # 草稿箱 + 发布队列（JSON 持久化）
├── work/PublishWorker.kt         # WorkManager 队列执行器（网络约束+指数退避）
└── ui/
    ├── AppRoot.kt                # MD3E 主题 + Scaffold/NavigationBar/Extended FAB + 覆盖页导航
    ├── common/Design.kt          # MaterialExpressiveTheme、ListItem/空态/错误卡/对话框等共享组件
    ├── common/Ui.kt              # Markdown 渲染 / 媒体宫格 / 头像 / 标签 / 相对时间
    ├── timeline/ · detail/ · composer/ · discover/ · hub/ · me/
```

## 构建

```bash
# JDK 17+，Android SDK 见 local.properties；Gradle/依赖走腾讯/阿里镜像
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

版本对齐设计库构建环境：AGP 9.3.2（内置 Kotlin）/ Kotlin 2.4.10 / Compose Multiplatform 1.12.0 / androidx material3 1.5.0-beta02（M3 Expressive）/ minSdk 29 / targetSdk 37。

## 使用

1. 首次进入「我的 → 登录」：填实例地址（如 `https://echo.example.com`）与账号密码；或在「设置 → 高级」粘贴 Access Token。
2. 时间线顶部「来，说点什么…」或中央发布钮/桌面快捷方式/系统分享 → 写说说抽屉。
3. 弱网发布失败自动进队列，「我的 → 发布队列」可查看/重试。

## 待办（M2+）

- 帖子编辑（PUT /echo 复用编辑器）、更多媒体类别（音频/视频发布）、S3 预签名直传
- Copilot Chat（SSE）、管理面板轻量版（评论审核/文件管理/访客统计/日志）
- i18n（en）、折叠屏双栏、性能分级（低帧设备减 blur/关色散）、Release 签名与 baseline profile
