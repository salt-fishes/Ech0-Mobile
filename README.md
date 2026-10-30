<div align="center">

# 🌳 Ech0 Mobile

**自托管说说平台 [Ech0](https://github.com/lin-snow/Ech0) 的全功能 Android 原生客户端**

随手写 · 随手发 · 随手看

📖 **[操作指南](docs/GUIDE.md)** · 📥 **[下载最新版](https://github.com/salt-fishes/Ech0-Mobile/releases)** · 📝 **[更新记录](CHANGELOG.md)**

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSDK-29-blue)
![Material](https://img.shields.io/badge/Material%203-Expressive-00796B?logo=materialdesign&logoColor=white)
![Release](https://img.shields.io/badge/release-v0.5.3-orange)
![License](https://img.shields.io/badge/License-AGPL--3.0-blue)

</div>

---

## ✨ 界面预览

| 时间线 | 发现 | 友链 |
| :---: | :---: | :---: |
| ![时间线](docs/screenshots-v2/01_timeline.png) | ![发现](docs/screenshots-v2/02_discover.png) | ![友链](docs/screenshots-v2/03_hub.png) |

| 说说详情 | 设置 · 主题色盘 | 关于 · 检查更新 |
| :---: | :---: | :---: |
| ![详情](docs/screenshots-v2/08_detail.png) | ![设置](docs/screenshots-v2/06_settings.png) | ![关于](docs/screenshots-v2/09_update.png) |

> 界面采用 **Material 3 Expressive**：动态取色 · 弹簧动效 · 形变组件 · 沉浸式顶栏 · 悬浮 Dock

## 🚀 功能特性

- ⚡ **启动静默同步** —— 先显示缓存，再自动拉取最新时间线 / 发现 / 友链；启动树标动效，每个进程只播一次。这里同步的是**内容**，不会静默安装 APK。
- 📝 **写说说** —— 抽屉式编辑器（可拖拽全屏）、Photo Picker 九宫格多图、逐张压缩上传（WebP）、话题标签、公开/私密切换、6 种图片布局、6 种扩展卡片（网站 / 视频 / 音乐 / GitHub / 位置 / 推文）、补记时间、草稿自动保存、断网自动入队（WorkManager 指数退避补发）。
- 🏠 **时间线** —— 分页信息流、关键词搜索、Markdown 渲染（粗斜删 / 行内代码 / 链接 / 话题 / 提及 / 标题 / 引用）、标签筛选、分享、长按删帖。点赞为单向（Ech0 服务端无 unlike 接口），本地防重复，**列表页与详情页共用同一份计数和已赞状态**。
- 🔎 **发现** —— 今日 / 热门 / 那年今日、30 天热力图、标签云、随机一条、Zen 沉浸瀑布流。
- 🌐 **友链** —— Connect 实例互联，跨实例时间线按时间归并聚合；支持 Markdown、媒体缩略图、来源 chip。
- 💬 **详情与评论** —— 评论树、回复、游客评论（form_token + 蜜罐 + gocap PoW 验证码客户端实现）。
- 🎨 **外观** —— 160dp 色相 / 饱和度色盘自由取色（拖动不带动页面滚动）、Android 12+ 动态取色、4 种背景、浅色 / 深色 / 跟随系统、edge-to-edge。
- 🔄 **应用更新** —— 启动时静默检查 GitHub Release；关于页「检查更新」可手动触发，发现新版本后展示更新记录并可打开 APK / Release 页；**只做检查与下载引导，不自动安装**。
- ⚡ **体验** —— 时间线 / 发现 / 友链**磁盘缓存秒开**（先显缓存再后台刷新，离线可看）、站点 Logo 自动获取与失败回退、统一错误提示、预测性返回。
- 🔐 **账号** —— 用户名密码登录（JWT 自动续期）、Access Token 登录、发布队列管理、草稿箱。

## 📥 下载

从 [Releases](https://github.com/salt-fishes/Ech0-Mobile/releases) 下载最新 APK（Android 10+）。

- 当前版本：`0.5.2`
- 覆盖安装旧版本会保留数据，但必须使用同一张 release 签名
- 版本检查仓库：`salt-fishes/Ech0-Mobile`，入口在「关于 → 检查更新」

## 🔧 构建

```bash
# JDK 17+，Android SDK 见 local.properties；依赖走阿里云镜像
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release 签名：复制签名凭据文件（不入库）
cp keystore.properties.example keystore.properties  # 并填入自己的 keystore 信息
./gradlew :app:assembleRelease
```

签名、发版步骤与 Release 文案格式见 [`docs/release/README.md`](docs/release/README.md) 和 [`docs/release/FORMAT.md`](docs/release/FORMAT.md)。

## 📖 使用

1. 首次进入按引导连接你的 Ech0 实例（如 `https://echo.example.com`）
2. 「我的 → 登录」管理员账号后即可发布（或「设置 → 高级」粘贴 Access Token）
3. 桌面长按图标「写一条」、系统分享文字 / 图片均可直达编辑器
4. 想检查客户端新版本：「我的 → 关于 → 检查更新」

完整步骤、Token 权限配置和常见问题见 **[操作指南](docs/GUIDE.md)**。

## 🧱 技术栈

| 层 | 选型 |
|---|---|
| UI | Jetpack Compose · **Material 3 Expressive**（MaterialExpressiveTheme / MotionScheme / 波浪指示器 / FilledIconToggle） |
| 构建 | AGP 9.3 · Kotlin 2.4 · Compose Multiplatform 1.12（Android 落地为 androidx） |
| 网络 | OkHttp（401 自动续期重放 / multipart 进度上传）· Coil |
| 存储 | DataStore（Keystore 加密令牌）· 磁盘缓存 |
| 后台 | WorkManager 发布队列（网络约束 + 指数退避） |

## 📂 工程结构

```
app/src/main/java/top/saltfish/ech0/
├── Ech0App / MainActivity        # 入口；快捷方式 / 分享意图 → 编辑器
├── data/                         # DTO / API / DataStore / 缓存 / Release 检查
├── media/Compressor.kt           # 长边 2048 · WebP 压缩
├── publish/ + work/              # 草稿箱 · 发布队列 · WorkManager
└── ui/
    ├── AppRoot.kt                # MD3E 主题 + 悬浮 Dock + 转场导航
    ├── common/                   # 设计系统与共享组件
    └── timeline/ · detail/ · composer/ · discover/ · hub/ · me/

mock/server.mjs                   # 本地 mock 实例（node mock/server.mjs）
docs/release/                     # 发版说明、格式规范与模板
```

## 📝 发版

- 版本号、Tag、Release 标题与正文、APK 命名统一遵守 [`docs/release/FORMAT.md`](docs/release/FORMAT.md)。
- 每个版本的 Release 正文来自 `docs/release/X.Y.Z.md`，由 GitHub Actions 在推送 `vX.Y.Z` tag 时自动构建并发布。
- 完整变更见 [`CHANGELOG.md`](CHANGELOG.md)。

## 📄 开源许可

- 本客户端：**AGPL-3.0**
- 上游 [Ech0](https://github.com/lin-snow/Ech0)：AGPL-3.0-or-later，作者 L1nSn0w
- 界面截图数据来自本地 mock（`mock/server.mjs`），图片为 picsum.photos 占位图

<div align="center">

**如果这个客户端对你有帮助，欢迎点一个 ⭐ Star**

</div>
