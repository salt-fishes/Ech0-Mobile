# 发版说明

发布格式（版本号、tag、标题、正文结构、产物命名）以 [`FORMAT.md`](FORMAT.md) 为准；新版本正文从 [`TEMPLATE.md`](TEMPLATE.md) 复制。

## 当前版本

- versionCode：8
- versionName：0.5.0
- tag：`v0.5.0`
- 签名：项目根目录 `ech0-release.keystore`（**不要提交到仓库**）
- 证书 SHA-256：`dca9bde362a1e5549e1f7ee599ed9064e13e71d842c60427baeb14349fad5c22`

## 本地出包

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
# 输出：app/build/outputs/apk/release/app-release.apk
```

出包后必须核对签名与上一版一致：

```powershell
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## GitHub Release

1. 确认 `app/build.gradle.kts` 的 `versionCode` / `versionName`、`CHANGELOG.md`、`docs/release/X.Y.Z.md` 都已更新。
2. 提交并推送 `main`。
3. 打 tag 并推送：`git tag vX.Y.Z && git push origin vX.Y.Z`。
4. Actions 会校验发版格式（tag / versionName / 标题 / 六个章节 / SHA-256），再用 Secrets 还原签名、构建 APK、生成 SHA-256，并创建 GitHub Release。
5. App 启动时静默请求 `releases/latest`；发现新版本后显示更新卡片，APK 由 Release 资产提供。

### Actions secrets

- `ECH0_KEYSTORE_BASE64`：`base64 -w0 ech0-release.keystore`
- `ECH0_STORE_PASSWORD`
- `ECH0_KEY_ALIAS`
- `ECH0_KEY_PASSWORD`

## 检查更新逻辑

- 启动：静默检查，失败 / 无 Release 不打扰。
- 关于页「检查更新」：手动触发，失败显示统一错误文案。
- 发现新版本：展示 Release 更新记录 +「前往下载 APK」/「GitHub」/「稍后」。
- 不自动安装 APK：Android 普通应用需要用户确认系统安装器，本应用只做检查与下载引导。
- 默认仓库：`salt-fishes/Ech0-Mobile`（代码内 `Ech0Store.githubRepo` 默认值）。

## 手动上传（不走 GitHub Actions）

1. 按 [`FORMAT.md`](FORMAT.md) 创建 `vX.Y.Z` tag 和 Release。
2. 上传已签名 APK，命名为 `ech0-mobile-X.Y.Z.apk`，并生成 `ech0-mobile-X.Y.Z.apk.sha256`。
3. Release 标题写 `Ech0 Mobile vX.Y.Z`，正文使用 `docs/release/X.Y.Z.md` 原文。
4. 正式版设为 latest；`-rc.N` 勾选 pre-release。
5. 确保仓库公开，App 不需要 token 即可读取 Release。
