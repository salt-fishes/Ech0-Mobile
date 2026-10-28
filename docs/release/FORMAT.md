# 发版格式规范

本文件规定 Ech0 Mobile 的版本号、Tag、Release 文案和产物命名。所有版本必须遵守，例外需要在 `docs/release/` 里单独说明。

## 1. 版本号与 Tag

- `versionName` 使用 SemVer：`MAJOR.MINOR.PATCH`，不带 `v`。
- `versionCode` 是单调递增整数，每次发版必须比上一版大；不要求与版本号换算，发版时手动 +1。
- Git tag 固定为 `v` + `versionName`，例如 `v0.3.0`；tag 必须打在 `main` 上。
- 预发布用 `vX.Y.Z-rc.N`，Go/Android 版本名仍写 `X.Y.Z-rc.N`；GitHub Release 标记为 pre-release，不覆盖 `latest`。
- 已发布的 tag 不允许移动或重打；需要修复时发新 patch 版本。

## 2. 发版前检查

1. `app/build.gradle.kts` 的 `versionCode` / `versionName` 已更新。
2. `CHANGELOG.md` 增加对应版本段落。
3. `docs/release/X.Y.Z.md` 已按模板写好，且文件名与版本号完全一致。
4. `./gradlew :app:assembleRelease` 通过，并用上一版相同证书签名。
5. 真机覆盖安装通过：启动、时间线、详情、点赞、设置、关于页检查更新。
6. `git status` 干净，发版提交已推送到 `main`。

## 3. Release 文案格式

Release 标题固定：

```
Ech0 Mobile vX.Y.Z
```

正文必须来自 `docs/release/X.Y.Z.md`，并包含以下二级标题，顺序固定：

```markdown
## 概览
## 亮点
## 改进
## 修复
## 升级方式
## 校验
```

- **概览**：2–4 句话，说明这个版本解决了什么、适合谁升级。
- **亮点**：3–6 条，面向使用者描述可感知的变化。
- **改进**：体验、性能、兼容性变化；没有写“无”。
- **修复**：明确修复的问题；没有写“无”。
- **升级方式**：覆盖安装说明、首次安装说明、签名一致性提示。
- **校验**：APK 文件名和 SHA-256 校验方式。
- 不写内部类名、提交号、调试地址；截图链接可选，但不能替代文字说明。

模板见 [`TEMPLATE.md`](TEMPLATE.md)。

## 4. 产物命名

| 产物 | 命名 |
|---|---|
| APK | `ech0-mobile-X.Y.Z.apk` |
| 校验文件 | `ech0-mobile-X.Y.Z.apk.sha256` |

- 只上传已签名的 release APK，不上传 debug APK、AAB、mapping、keystore。
- APK 必须与上一版使用同一张证书；证书 SHA-256 记录在 [`README.md`](README.md)。
- `sha256` 文件内容格式：`<64位hash>  ech0-mobile-X.Y.Z.apk`。

## 5. GitHub Release 元数据

- **Tag**：`vX.Y.Z`，与 `versionName` 一致。
- **Title**：`Ech0 Mobile vX.Y.Z`。
- **Body**：`docs/release/X.Y.Z.md` 原文。
- **Assets**：APK + `.sha256`。
- **Latest**：正式版自动设为 latest；`-rc.N` 必须勾选 pre-release。
- 不自动生成 commit 列表作为正文。

## 6. 自动化

推送 `v*` tag 会触发 `.github/workflows/release.yml`：

1. 校验 `docs/release/<version>.md` 是否存在，缺失直接失败。
2. 用仓库 Secrets 还原 release keystore，构建 signed APK。
3. 生成 `dist/ech0-mobile-<version>.apk` 与 `.sha256`。
4. 创建 GitHub Release，标题、正文、资产均按上面的格式。

## 7. 热修复

- 只修 bug：`PATCH +1`，例如 `0.3.1`。
- 必须从 `main` 新建 tag，不移动旧 tag。
- Release 正文的「修复」必须写清楚触发条件和影响范围。

## 8. 禁止事项

- 禁止跳过 `docs/release/X.Y.Z.md` 直接手填 Release 正文。
- 禁止改 tag 指向已发布提交。
- 禁止提交 `keystore.properties`、`*.keystore`、签名密码。
- 禁止用 debug 签名覆盖安装包发版。
