# 本地 mock 实例

用于截图、UI 回归和离线开发，不依赖线上 Ech0。

```bash
# 默认：图片/接口返回 http://10.0.2.2:8899，适合 Android 模拟器
node mock/server.mjs

# 截图模式：用真实域名展示（需要把域名转发到 10.0.2.2:8899）
ECH0_PUBLIC_HOST=http://echo.saltfish.top node mock/server.mjs
```

- 端口：`8899`
- 测试账号：`saltfish / ech0`（管理员 / Owner）
- 数据：26 条说说、12 个标签、2 个友链实例、6 种扩展卡、9 张配图、5 条今日、1 条那年今日
- 图片：`mock/files/`（Unsplash 演示图，已本地化）
- 音乐卡：`/meting/api` 返回「晚风 / 陈婧霏」+ 本地封面，音频为 1 秒静音 WAV

## 已实现端点

- `/api/hello`、`/api/settings`、`/api/init/status`
- `/api/login`、`/api/auth/refresh`、`/api/auth/logout`、`/api/user`
- `/api/echo/query`、`/api/echo/{id}`、`/api/echo` POST/PUT/DELETE
- `/api/echo/today|hot|random|onthisday`、`/api/echo/like/{id}`
- `/api/tags`、`/api/heatmap`
- `/api/comments/form`、`/api/comments` GET/POST
- `/api/files/upload`、`/api/files/external`、`/api/files/{name}`
- `/api/connect`、`/api/connects/info`
- `/remote1|2/api/echo/page`、`/remote1|2/api/echo/{id}`、`/remote1|2/api/comments`
- `/meting/api`

## 截图

`ECH0_PUBLIC_HOST` 只影响返回给客户端的绝对图片 / 音轨地址；`/remote1`、`/remote2` 仍由同一进程按路径区分。截图流程见 [`../docs/screenshots-v3/README.md`](../docs/screenshots-v3/README.md)。
