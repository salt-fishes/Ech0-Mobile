# Ech0 Mobile 网络请求全面优化方案

> 版本：v1.0  
> 日期：2026-10-31  
> 范围：`ech0-mobile` Android 客户端；`Ech0-main` 服务端配合项单独标注  
> 原则：不重写框架、不破坏旧服务端兼容、分阶段落地、每一步可回滚

---

## 0. 摘要

当前客户端采用 **OkHttp 同步请求 + 页面直接调用 `Ech0Api` + 页面各自维护 `DiskCache`** 的方式。整体能工作，但存在四类结构性问题：

1. **重复请求**：首页、友链本地源、Zen、发现页各自请求同一批帖子；站点设置、`hello`、`connectSelf`、`currentUser`、`tags` 也在多个页面重复请求。
2. **串行等待**：友链聚合和发现页都是串行请求，一个慢来源会拖住整页。
3. **缓存未隔离**：缓存没有按实例/账号分区，切换实例可能看到旧站内容，甚至把旧 token 发给新实例。
4. **请求链路不可控**：60 秒级超时、阻塞式 `execute()` 不可取消、磁盘缓存读写主线程、上传无并发限制。

本方案分六个阶段（0–5）：

- **阶段 0**：建立基线、加调试指标
- **阶段 1**：网络基础设施 + 实例隔离 + 磁盘缓存移出主线程
- **阶段 2**：共享数据层（`PostStore` / `SiteStore` / `ContentBus`）
- **阶段 3**：首页/友链/发现/详情并行化与取消
- **阶段 4**：上传、三方元数据、图片、更新检查
- **阶段 5**：测试、基准、灰度与发布

预计单人投入 5–6 周；可与 UI 工作并行。

---

## 1. 目标与非目标

### 1.1 目标

| 维度 | 目标 |
|---|---|
| 正确性 | 切换实例/账号不串数据、不串 token；退出登录后不展示私有缓存 |
| 性能 | 首页/友链/发现首屏和刷新明显变快；重复请求率显著下降 |
| 稳定性 | 弱网可取消、超时可控、失败可重试、上传不互相拖垮 |
| 可维护性 | 页面不直接调 API；读路径统一走 Store；关键逻辑有测试 |
| 离线体验 | 先展示缓存，再后台刷新；断网时仍可浏览已缓存内容 |
| 可观测性 | Debug 下能看到请求耗时、字节数、重试、缓存命中 |

### 1.2 非目标

- 不把 OkHttp 换成 Retrofit/Ktor。
- 不做 WebSocket/SSE 实时推送（后续可单独评估）。
- 不做完整的离线写队列（现有发布队列保留并增强）。
- 不要求服务端做破坏性 API 变更；新增接口必须可降级。
- 不做大规模 UI 重设计，只补充加载、错误、离线、过期提示。

---

## 2. 现状评估

### 2.1 当前请求链路

```
Compose Screen
  └─ Ech0Api（object 单例）
       ├─ client       带 Cookie、Authorization、401 刷新
       ├─ publicClient 无本站登录态、浏览器 UA
       └─ UpdateChecker.client 独立 client
            ↓
       OkHttp execute() + withContext(Dispatchers.IO)
            ↓
       Envelope<T> 解析
            ↓
       DiskCache（页面各自读写 JSON）
```

已具备的优点：

- 请求信封 `{code,msg,data,error_code}` 统一解析。
- `publicClient` 隔离了友链/图片请求，避免把本站 token 发给第三方。
- 401 `TOKEN_*` 有自动 refresh 重放。
- 首页/发现/友链有“先缓存后刷新”的离线策略。
- `LikeController` 已经是跨页面共享状态的正确方向。

### 2.2 问题清单

| ID | 优先级 | 问题 | 位置 | 影响 |
|---|---|---|---|---|
| NET-01 | P0 | 友链聚合串行请求 | `HubScreen.kt:198-228` | 多友链时首屏/分页耗时 = 各来源之和 |
| NET-02 | P0 | 每次加载更多都重拉友链元数据 | `HubScreen.kt:158/177` | 无谓请求；元数据不变也重复拉 |
| NET-03 | P0 | 发现页 5 个接口串行 | `DiscoverScreen.kt:129-148` | 刷新耗时 = 5 个请求之和 |
| NET-04 | P0 | 缓存/登录态未按实例隔离 | `DiskCache.kt`、`Ech0Store.kt:233`、`Api.kt:95` | 切实例串内容；旧 token 发到新站 |
| NET-05 | P1 | 首页/友链/Zen/发现重复请求同一批帖子 | `TimelineScreen.kt:176`、`HubScreen.kt:204`、`DiscoverScreen.kt:502` | 重复流量；多份缓存不一致 |
| NET-06 | P1 | 全局 20s/60s/120s 超时，无 `callTimeout` | `Api.kt:50-75` | 弱网长时间转圈；坏源拖整页 |
| NET-07 | P1 | 同步 `execute()` 不可取消 | `Api.kt:161`、`Api.kt:181`、`Api.kt:446` | 离开页面后请求继续占线程 |
| NET-08 | P1 | 磁盘缓存读写主线程、全量重写 | `DiskCache.kt:59-121`、各页面 | 切 tab/刷新卡顿；大列表更明显 |
| NET-09 | P1 | 重复站点级请求，无 single-flight | `AppRoot.kt:195/219/228`、`MeScreens.kt` | 启动/登录/设置页多发请求 |
| NET-10 | P1 | 三个 OkHttpClient 各自连接池/线程池 | `Api.kt:50/62`、`AppUpdates.kt:61` | 连接复用差、线程多 |
| NET-11 | P1 | 401 刷新可能重复执行 | `Api.kt:103-145` | 并发 401 时重复 refresh |
| NET-12 | P2 | 9 张图同时压缩/上传 | `ComposerSheet.kt:435-449` | 带宽争抢、低内存设备 OOM |
| NET-13 | P2 | 三方卡片元数据无 TTL/低流量仍请求 | `ExtensionCard.kt:167-170/318/505/658/770` | 重复请求；GitHub 限流 |
| NET-14 | P2 | 每次冷启动都检查 GitHub Release | `AppRoot.kt:195-200` | 无谓请求；可能触发限流 |
| NET-15 | P2 | `createTag` 手拼 JSON、URL 编码不完整 | `Api.kt:336`、`Api.kt:84-93` | 特殊字符导致请求失败 |
| NET-16 | P3 | 服务端 API 未 gzip、无聚合接口 | `Ech0-main/internal/router/middleware.go` | 信息流响应体积偏大；发现页 5 次请求 |

### 2.3 共享数据现状

| 数据 | 当前请求方 | 是否可共享 |
|---|---|---|
| 本站帖子列表 | 首页 `queryEchos(20)`、友链本地源 `queryEchos(20)`、Zen `queryEchos(30)` | 默认第 1 页完全相同；应共享查询缓存 + 实体 |
| 今日/热门/那年今日 | 发现页 | 接口不同，但 Echo 实体可共享 |
| 站点设置 `settings` | AppRoot、设置页、连接引导 | 可 TTL 缓存 |
| 版本信息 `hello` | AppRoot、设置页、关于页 | 可 TTL 缓存 |
| 本站 Connect 信息 `connectSelf` | AppRoot、友链页 | 可 TTL 缓存 |
| 友链列表 `connectsInfo` | 友链页 | 可 TTL 缓存；加载更多不必重拉 |
| 当前用户 `currentUser` | 登录、AppRoot、设置页 | 可会话级缓存 |
| 标签 `tags` | 发现页、编辑器 | 可 TTL 缓存 |
| 图片 | Coil | 已由 Coil 共享，需优化配置 |
| 扩展卡元数据 | GitHub/B站/推文/音乐卡 | GitHub/B站/推文已有进程内缓存，音乐没有；需 TTL/落盘 |

### 2.4 当前缓存失效问题

- `FeedRefreshBus` 只在发布成功后通知，且只有 `TimelineScreen` 监听（`AppRoot.kt:451` → `TimelineScreen.kt:252`）。
- 友链页、发现页不监听，发布/编辑/删除后可能继续显示旧内容。
- 首页、友链、发现各有一份 JSON，TTL 独立，数据可能互相矛盾。
- 切换实例只改 URL，不清缓存、token、user、liked；草稿/队列里的 `uploaded:<fileId>` 也没有实例标识。
- 退出登录不清缓存，管理员看过的私有帖子可能继续留在本地缓存中。

---

## 3. 目标架构

```
┌─────────────────────────────────────────────────────────┐
│ Compose Screens                                         │
│  Timeline / Hub / Discover / Detail / Composer / Me     │
└──────────────────────────┬──────────────────────────────┘
                           │ collect revision / state
┌──────────────────────────▼──────────────────────────────┐
│ Data Stores                                             │
│  PostStore      SiteStore      InteractionStore         │
│  RemotePostStore              PublishStore              │
│  ContentBus（revision / invalidation）                   │
└──────────────────────────┬──────────────────────────────┘
                           │ suspend query / mutation
┌──────────────────────────▼──────────────────────────────┐
│ Ech0Api（transport only）                                │
│  endpoint + DTO + envelope parsing + error mapping      │
└──────────────────────────┬──────────────────────────────┘
                           │ Call.await()
┌──────────────────────────▼──────────────────────────────┐
│ HttpClients                                             │
│  api / upload / image / thirdParty / update             │
│  shared ConnectionPool + Dispatcher + EventListener     │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│ DiskStore v2                                            │
│  cache/v2/<instance-hash>/<account>/...                 │
│  atomic write · TTL · LRU · schema version              │
└─────────────────────────────────────────────────────────┘
```

### 3.1 分层职责

| 层 | 职责 | 不做什么 |
|---|---|---|
| Screen | 展示、交互、滚动、局部 loading | 不直接调 `Ech0Api`；不直接读写磁盘 |
| Store | 共享缓存、single-flight、失效、实体合并 | 不处理 UI 状态和导航 |
| Ech0Api | URL、DTO、信封解析、错误映射 | 不做缓存、不做页面逻辑 |
| HttpClients | 连接池、超时、拦截器、认证、日志 | 不关心业务 |
| DiskStore | 序列化、原子写、TTL、容量控制 | 不关心页面 |

### 3.2 关键设计决策

1. **保留 OkHttp + kotlinx.serialization**：当前依赖轻、包体小，换框架收益低。
2. **先做内存 Store，再做磁盘合并**：避免一次性大重构；第一阶段保留现有 `DiskCache` 作为兜底。
3. **查询缓存 + 实体缓存双轨**：查询缓存解决重复请求；实体缓存解决跨页面一致性和点赞/编辑同步。
4. **实例 + 账号作为命名空间**：所有缓存 key 必须带 `baseUrl` 和 viewer scope。
5. **变更走失效而不是复杂合并**：create/update/delete 后清相关 query、更新实体、递增 revision；UI 重新拉取。简单可靠。
6. **远端友链帖子单独命名空间**：`(sourceUrl, id)`，不与本站帖子混用。
7. **并行有上限**：友链最多 4 个并发；上传最多 2–3 个并发；避免把手机和自建实例打满。

---

## 4. 网络基础设施

### 4.1 Client 矩阵

新建 `data/net/HttpClients.kt`，所有 client 从同一个 base `newBuilder()` 派生，共享 `ConnectionPool` 和 `Dispatcher`。

| Client | 用途 | Cookie | Auth | UA | connect | read | write | call |
|---|---|---|---|---|---|---|---|---|
| `api` | 本站 API | 是 | 是 | `Ech0-Mobile/<version>` | 10s | 20s | 30s | 30s |
| `upload` | 文件上传 | 是 | 是 | 同上 | 10s | 30s | 180s | 300s |
| `image` | 图片/Coil | 否 | 否 | Browser UA | 10s | 30s | 30s | 45s |
| `thirdParty` | GitHub/B站/推文/Meting | 否 | 否 | Browser UA | 8s | 10s | 10s | 15s |
| `update` | GitHub Release | 否 | 否 | `Ech0-Mobile/<version>` | 10s | 15s | 15s | 20s |

实现要点：

```kotlin
object HttpClients {
    private val pool = ConnectionPool(8, 5, TimeUnit.MINUTES)
    private val dispatcher = Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 6
    }

    private val base = OkHttpClient.Builder()
        .connectionPool(pool)
        .dispatcher(dispatcher)
        .retryOnConnectionFailure(true)
        .build()

    val api = base.newBuilder()
        .cookieJar(Ech0Api.cookieJar)
        .addInterceptor(HeadersInterceptor())
        .addInterceptor(AuthRetryInterceptor())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val upload = api.newBuilder()
        .writeTimeout(180, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    val image = base.newBuilder()
        .addInterceptor(BrowserUaInterceptor())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    val thirdParty = base.newBuilder()
        .addInterceptor(BrowserUaInterceptor())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
}
```

注意：

- `publicClient` 目前同时被 Coil 和三方 API 使用。拆分后，Coil 用 `image`（30s），三方元数据用 `thirdParty`（10s），避免把图片超时压得太短。
- `Api.kt` 里的 `client` / `publicClient` 保留为兼容别名，内部指向新 client，降低迁移风险。
- `UpdateChecker` 删除自己的 client，改用 `HttpClients.update`。

### 4.2 超时与重试策略

| 请求类型 | 自动重试 | 说明 |
|---|---|---|
| 读接口 GET | 1 次 | 连接重置、超时、502/503/504；300ms + jitter |
| `queryEchos` POST | 1 次 | 只读查询，可安全重试 |
| 登录/刷新 | 0 次 | 由用户重试或重新登录 |
| 创建/更新/删除 | 0 次 | 避免重复创建；由用户/队列重试 |
| 评论提交 | 0 次 | 避免重复评论；4xx 不重试 |
| 文件上传 | 0 次（单次） | 编辑器逐张重试；Worker 整体重试 |
| 401 refresh | 1 次 | 刷新后重放；仍 401 则清登录态 |
| 429 | 遵守 `Retry-After` | 最多 1 次；否则提示用户 |

实现方式：

- 在 `HttpClients` 里加 `RetryInterceptor`，只对白名单方法/状态码生效。
- 重试前检查 `call.isCanceled()`，避免取消后继续。
- 不在 OkHttp 拦截器里做复杂业务重试，避免和 WorkManager 重复。

### 4.3 可取消请求

把 `withContext(Dispatchers.IO) { call.execute() }` 改为 OkHttp 异步 + 协程桥接：

```kotlin
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
```

然后：

```kotlin
private suspend fun <T> executeWith(
    httpClient: OkHttpClient,
    serializer: KSerializer<T>,
    request: Request
): T {
    httpClient.newCall(request).await().use { resp ->
        val text = resp.body?.string().orEmpty()
        // 统一错误解析 + Envelope 解析
    }
}
```

收益：

- 页面离开时真正取消 socket，不再占 IO 线程。
- OkHttp `Dispatcher` 的 `maxRequests` / `maxRequestsPerHost` 生效。
- 上传也可以复用同一套取消能力（但编辑器上传按产品语义可能故意不随页面取消）。

注意：

- `withTimeout` 不能中断阻塞的 `execute()`；必须配合 `Call.cancel()` 或改用 `enqueue`。
- 解析 JSON 仍在回调线程；数据量小，可接受；如需可切到 `Dispatchers.Default`。

### 4.4 认证刷新

目标：并发 401 只刷新一次；token 快过期时提前刷新；刷新失败统一清登录态。

```kotlin
object TokenManager {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Deferred<String>? = null

    suspend fun refreshIfNeeded(staleToken: String?): String {
        mutex.withLock {
            val current = Ech0Store.current.accessToken
            if (current != null && current != staleToken) return current
            refreshJob?.let { return it.await() }
            val job = scope.async { doRefreshBlocking() }
            refreshJob = job
        }
        return try {
            refreshJob!!.await()
        } finally {
            mutex.withLock { refreshJob = null }
        }
    }
}
```

`AuthRetryInterceptor` 逻辑：

1. 记录请求发出时的 `Authorization`。
2. 收到 401 且 `error_code` 以 `TOKEN_` 开头。
3. 如果请求带 `X-No-Retry`，直接返回。
4. 调 `TokenManager.refreshIfNeeded(staleToken)`。
5. 如果 token 已变化，直接重放；否则刷新后重放。
6. 刷新失败：清 token/user/cookies，返回原 401，由 UI 引导登录。

主动刷新：

- 在 `executeWith` 调用前检查 `tokenExpiresAt`；距过期 < 60s 时先刷新。
- 没有 refresh cookie 时不要主动刷新，直接等 401。

CookieJar 改进：

- 持久化去抖（例如 500ms），不要每个响应都写 DataStore。
- 保存时按 domain + path + name 去重；当前只按 domain + name，可能覆盖不同 path 的 cookie。
- 加载时过滤过期 cookie；定期清理。
- 退出登录/切换实例时清理对应 domain 的 cookie。

安全：

- `HeadersInterceptor` 只对当前 `baseUrl` 的 host 附加 `Authorization`，避免未来某个请求误用 `api` client 时把 token 发给第三方。
- 切换到不同 host 时清 token，要求重新登录。
- 对 `http://` 实例给出安全提示；保留自建 LAN 场景，但不默认鼓励。

### 4.5 URL、响应与错误

- 所有 URL 用 `HttpUrl.Builder` 构造，避免 query 拼接和路径注入。
- `resolveUrl` 支持：
  - 绝对 URL `http://` / `https://`
  - 协议相对 `//cdn.example.com/x`
  - 相对路径 `/api/files/x`
- 错误体读取限制在 64KB，避免 Cloudflare 挑战页/大 HTML 占内存。
- `executeUnit` 兼容 204/空 body。
- `json` 增加 `coerceInputValues = true`，减少服务端返回 null 导致的解析失败。
- `ApiException` 增加 `retryable` 字段，供重试拦截器和 UI 判断。

---

## 5. 数据层

### 5.1 PostStore

目标：同一实例下，所有页面共享帖子实体和查询结果；相同查询只发一次请求。

#### 5.1.1 数据结构

```kotlin
data class FeedKey(
    val baseUrl: String,
    val viewer: String,          // 例如 "guest" / "user:<id>" / "admin:<id>"
    val page: Int,
    val pageSize: Int,
    val search: String? = null,
    val tagIds: List<String> = emptyList(), // 排序后存
    val sortBy: String = "created_at",
    val sortOrder: String = "desc",
    val privateFilter: Boolean? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null
)

data class CachedPage(
    val fetchedAt: Long,
    val result: PageResult<Echo>
)
```

#### 5.1.2 缓存策略

| 数据 | TTL | 说明 |
|---|---|---|
| 首页默认信息流 | 3 分钟 | 与现有 `FeedCacheTtlMs` 对齐 |
| 友链本地源 | 3 分钟 | 与首页共享同一 `FeedKey` |
| 搜索/标签结果 | 1 分钟 | 变化快，短 TTL |
| 友链远端页 | 5 分钟 | 按 `sourceUrl` 单独 key |
| 详情实体 | 10 分钟 | 打开详情仍会后台刷新 |
| 单条实体 | 会话内 | 被 query 结果更新时覆盖 |

#### 5.1.3 接口

```kotlin
object PostStore {
    suspend fun query(key: FeedKey): PageResult<Echo>
    suspend fun queryRemote(sourceUrl: String, page: Int, pageSize: Int): PageResult<Echo>
    fun upsert(echo: Echo, sourceUrl: String = Ech0Api.base())
    fun remove(id: String, sourceUrl: String = Ech0Api.base())
    fun invalidateQueries(scope: InvalidationScope = InvalidationScope.All)
    fun invalidateDetail(id: String)
    val revision: Int
}
```

实现要点：

- `query` 先查未过期 `pages`；命中直接返回。
- 未命中时用 `Mutex` + `Deferred` 合并同一 `FeedKey` 的并发请求。
- 请求成功后：
  - 每个 `Echo` 写入 `entities["$baseUrl|$id"]`
  - 写入 `pages[key]`
  - 去重、保持服务端顺序
- `queryRemote` 使用 `(sourceUrl, id)` 命名空间，不写入本站 `entities`。
- `upsert` 用于发布、编辑、详情刷新后的实体更新。
- `remove` 同时从所有 `pages` 中删除该 id，避免删帖后列表仍显示。
- `invalidateQueries` 支持按 `baseUrl`、`viewer`、`tag`、`all` 清理。

#### 5.1.4 查询归一化

- `search` 去空白；空字符串转 `null`。
- `tagIds` 排序后比较。
- `pageSize` 统一：首页/友链本地都用 20；Zen 如需 30，明确只共享实体，不共享分页。
- `viewer` 由 `username + isAdmin` 生成；登录/退出时清空。
- `baseUrl` 使用规范化后的 URL（去尾斜杠、小写 host）。

### 5.2 SiteStore

目标：站点级公共数据只请求一次，多个页面共享。

| Key | TTL | 失效时机 |
|---|---|---|
| `settings` | 10 分钟 | 设置页保存、切换实例 |
| `hello` | 10 分钟 | 切换实例、手动刷新 |
| `connectSelf` | 5 分钟 | 友链下拉刷新、切换实例 |
| `connectsInfo` | 5 分钟 | 友链下拉刷新、切换实例 |
| `currentUser` | 会话 | 登录、token 刷新、退出 |
| `tags` | 5 分钟 | 发布/编辑/删除标签、手动刷新 |
| `metingApi` | 跟随 `settings` | 设置更新、切换实例 |

```kotlin
object SiteStore {
    suspend fun settings(force: Boolean = false): SiteSettings
    suspend fun hello(force: Boolean = false): Hello
    suspend fun self(force: Boolean = false): ConnectInfo
    suspend fun connects(force: Boolean = false): List<ConnectInfo>
    suspend fun currentUser(force: Boolean = false): User
    suspend fun tags(force: Boolean = false): List<Tag>
    fun invalidateAll()
}
```

- 每个方法内部使用 `Mutex` + `Deferred` single-flight。
- `settings()` 负责更新 `Ech0Store.serverName` 和 `Branding.logoUrl`，替换 `AppRoot` 里的分散逻辑。
- `currentUser()` 只在登录、token 变化、手动刷新时请求；避免 `AppRoot` 因 `accessToken` 变化重复请求。
- `tags()` 供发现页和编辑器共用。

### 5.3 ContentBus

目标：发布、编辑、删除、点赞、评论后，所有相关页面同步刷新。

```kotlin
object ContentBus {
    var feedRevision by mutableIntStateOf(0)
        private set
    var detailRevision by mutableIntStateOf(0)
        private set

    fun onEchoCreated(echo: Echo?) { ... }
    fun onEchoUpdated(echo: Echo) { ... }
    fun onEchoDeleted(id: String) { ... }
    fun onLikeChanged(id: String) { ... }
    fun onCommentChanged(echoId: String) { ... }
}
```

接入：

- `AppRoot` 发布成功：`ContentBus.onEchoCreated(...)`，同时保留 `FeedRefreshBus.bump()` 兼容旧逻辑。
- `TimelineScreen`：`LaunchedEffect(ContentBus.feedRevision)` 触发刷新或局部更新。
- `HubScreen`：监听 `feedRevision`，只刷新本地源，远端源不动。
- `DiscoverScreen`：监听 `feedRevision`，刷新 today/hot/onthisday。
- `DetailScreen`：监听 `detailRevision`，刷新评论/点赞。
- `LikeController` 继续负责点赞计数；`ContentBus.onLikeChanged` 负责让列表实体同步。

### 5.4 实例/账号隔离

定义：

```kotlin
data class InstanceScope(
    val baseUrl: String,
    val accountKey: String // "guest" / "user:<id>" / "admin:<id>"
)
```

规则：

1. 所有查询 key、实体 key、详情缓存 key、磁盘目录都必须带 `InstanceScope`。
2. `Ech0Store.setBaseUrl`：
   - 规范化 URL。
   - 如果 host 变化：清 token、user、liked、SiteStore、PostStore、Branding、`metingApi`。
   - 取消所有进行中的请求。
   - 清空当前页面的 UI 列表，触发重新加载。
3. `logout`：
   - 清 token/user/cookies。
   - 清私有实体和 query 缓存（至少清 `viewer != guest` 的）。
   - 清 likedIds（按账号存储或清空）。
4. 磁盘目录：

```
files/cache/v2/<sha256(baseUrl)>/<accountKey>/
  posts.json
  queries.json
  site.json
  details/<hash>.json
```

5. 草稿/队列：
   - `DraftPayload` / `QueueItem` 增加 `baseUrl` 字段，默认当前实例。
   - Worker 只处理 `baseUrl == Ech0Api.base()` 的条目；其他实例的条目保持 pending 并提示。
   - 切换实例时，如果存在旧实例草稿/队列，弹窗让用户选择“切回旧实例发布 / 保留 / 导出 / 删除”。
   - 旧数据迁移：缺省 `baseUrl` 时按当前实例补齐，但标记 `migrated=true`，首次发布前提示确认。
6. 详情缓存：
   - 本地：`local:<baseUrl>:<viewer>:<echoId>`
   - 远端：`remote:<sourceUrl>:<echoId>`
7. 测试：
   - 切实例后不出现旧站内容。
   - 旧 token 不会发到新 host。
   - 退出登录后看不到私有帖子。
   - 草稿不会误发到新实例。

### 5.5 磁盘缓存 v2

新建 `data/cache/DiskStore.kt`，替换页面直接调 `DiskCache`。

要求：

- 所有读写 `withContext(Dispatchers.IO)`。
- 原子写：`tmp` 文件 + `rename`。
- 单写者 `Mutex`，避免并发写坏文件。
- 文件头包含 `schemaVersion`、`baseUrl`、`accountKey`、`savedAt`。
- 读取失败/版本不匹配：删除文件，返回 null，不崩溃。
- TTL：由 Store 决定，DiskStore 只负责存取。
- 容量控制：
  - 首页/友链：最多 200 条或 4MB
  - 详情：LRU 100 条或 20MB
  - 图片：Coil 磁盘缓存 100–200MB
- 写入去抖：500ms 合并；`ON_STOP` 时 flush。
- 启动时在 `Ech0App` 的 IO scope 预加载到内存，页面读内存。
- 旧 `DiskCache` 文件：
  - 升级后第一次启动直接清理，避免旧缓存没有实例信息导致串数据。
  - 或者读取后补 `baseUrl`，但只在确认当前实例时使用；建议直接清理，简单安全。

### 5.6 离线策略

| 场景 | 行为 |
|---|---|
| 冷启动有缓存 | 立即展示缓存；后台静默刷新 |
| 缓存未过期 | 直接使用；不请求 |
| 缓存过期 | 先展示缓存；静默刷新；失败保留缓存 |
| 手动下拉 | 强制刷新；失败保留内容并提示 |
| 无网络 | 直接展示缓存；显示“离线/上次更新” |
| 401 | 刷新 token；失败则清登录态并提示 |
| 5xx/429 | 保留缓存；指数退避；提示稍后重试 |
| 切换实例 | 不展示旧实例缓存；显示加载/空状态 |

UI 建议：

- 顶部或列表底部显示“上次更新：x 分钟前”。
- 离线时显示轻量 banner，不阻塞浏览。
- 刷新失败时保留旧内容，错误以 Snackbar/错误卡展示。
- 不要因为一个接口失败清空整页。

---

## 6. 页面改造

### 6.1 首页 Timeline

现状：`TimelineScreen.kt` 自己管理 `items`、`page`、`refreshing`、`DiskCache`。

改造：

1. 查询走 `PostStore.query(FeedKey(...))`。
2. 分页仍由页面控制，但结果写入 Store；切回首页可直接复用。
3. 加请求代际 `generation`：
   - 每次 reset/搜索/标签变化 `generation++`。
   - 响应回来时如果 `generation` 不一致，丢弃，避免旧搜索覆盖新搜索。
4. 搜索输入 300ms 防抖；取消上一次搜索请求。
5. 下拉刷新时：
   - 取消正在进行的静默刷新，或等待其结束再强制刷新。
   - 不要因为 `silentRefreshing` 直接 return。
6. 发布/编辑/删除后监听 `ContentBus.feedRevision`，局部 upsert/remove 或刷新第 1 页。
7. `DiskCache.saveTimeline` 改为 `PostStore` 统一持久化，去抖。
8. 预取：距离底部 5 条时加载下一页；请求中不重复触发。

验收：

- 首页 → 友链 → 首页，3 分钟内不重复请求默认第 1 页。
- 搜索快速输入不会出现旧结果覆盖新结果。
- 发布后首页和友链本地源都能看到新帖。
- 离开页面后请求在 100ms 内取消。

### 6.2 友链 Hub

现状：串行拉所有来源；每次 load-more 重拉元数据。

改造：

1. `connectSelf` / `connectsInfo` 走 `SiteStore`；只有 reset 或手动刷新才 force。
2. 来源列表本地 + 远端；本地源走 `PostStore.query`（与首页共享）。
3. 并行拉取：

```kotlin
val semaphore = Semaphore(4)
val results = coroutineScope {
    sources.map { source ->
        async {
            semaphore.withPermit {
                runCatching {
                    withTimeout(8_000) { fetchSource(source) }
                }
            }
        }
    }.awaitAll()
}
```

4. 每个来源单独记录 `page` / `exhausted` / `failed`；失败不推进页码。
5. 加载更多只拉取未 exhausted 的来源；不要重拉元数据。
6. 合并去重后按时间排序；本地/远端用 `(sourceUrl, id)` 区分。
7. 静默刷新只合并第 1 页，不倒退分页进度。
8. 页面离开时 `coroutineScope` 取消所有来源请求。
9. 部分失败：展示成功来源 + 失败重试入口；不要清空列表。
10. 缓存：合并列表可由实体 Store 派生，避免 `hub.json` 存整份重复帖子；过渡期可保留，但写入 IO + 去抖。

验收：

- 6 个友链、4G 网络下首屏 p95 ≤ 2.5s。
- 单个慢来源不超过 8s，不影响其他来源展示。
- 加载更多不再请求 `connectSelf` / `connectsInfo`。
- 发布后切到友链，本地源能看到新帖。

### 6.3 发现页 / Zen

改造：

1. `loadAll` 并行：

```kotlin
coroutineScope {
    val today = async { runCatching { Ech0Api.todayEchos() } }
    val hot = async { runCatching { Ech0Api.hotEchos(8) } }
    val heatmap = async { runCatching { Ech0Api.heatmap() } }
    val tags = async { SiteStore.tags() }
    val otd = async { runCatching { Ech0Api.onThisDay() } }
    // await 后按 section 独立更新
}
```

2. 每个 section 独立更新：一个失败不影响其他 section。
3. `randomEcho` 加 `randomBusy` + 取消上一次请求；快速点击不会叠加。
4. `ZenScreen` 改用 `PostStore.query`；pageSize 与首页统一为 20，加载 2 页达到 40 条；或保留 30 但只共享实体。
5. 标签走 `SiteStore.tags()`；发现页和编辑器共享。
6. 热力图数据 TTL 5 分钟；下拉刷新 force。
7. `ContentBus.feedRevision` 变化时刷新 today/hot/onthisday。

验收：

- 发现页刷新 p95 ≤ 1.5s（4G，5 个接口并行）。
- 某一个接口失败时，其他 section 仍更新。
- 连续点“随机一条”不会并发多个请求。
- 首页/Zen 重复请求减少。

### 6.4 详情页

本地详情：

1. 先读 `DetailCache`（按实例/账号）；立即展示。
2. 并行请求 `echoById` + `comments`；不要串行。
3. `echoById` 结果 `PostStore.upsert`；评论写入详情缓存。
4. 点赞用 `LikeController`；评论提交后 `ContentBus.onCommentChanged`。
5. 页面离开取消请求；评论提交中的请求不随页面取消（或者提示“发送中”）。
6. 评论提交重试：
   - 网络/5xx 可重试 1 次；
   - 4xx 不重试；
   - 失败保留输入内容。
7. PoW 计算定期 `ensureActive()`，页面离开可取消。

远端详情：

1. 并行 `remoteEchoDetail` + `remoteConnect` + `remoteComments`。
2. 使用 `thirdParty`/`remote` client，超时 10–15s。
3. 评论表单/验证码按需请求。
4. 缓存 key 包含 `sourceUrl`。
5. 远端失败保留缓存，不显示空白。

验收：

- 详情页打开有缓存时首帧有内容。
- 本地详情网络耗时从 `echo + comments` 之和降为 `max`。
- 离开页面后请求取消。
- 评论提交失败不丢输入。

### 6.5 编辑器与上传

现状：选 9 张图后每张都 `launchUpload`，压缩和上传全部并发。

改造：

1. `uploadScope` 加 `Semaphore(2)`（或 3）：
   - 压缩和上传都在许可内执行；
   - 避免 9 路解码/压缩/上传同时跑。
2. 上传重试：
   - 单张失败显示重试；
   - 自动重试 1 次（网络/5xx），退避 500ms；
   - 4xx 不重试。
3. 移除图片时：
   - 如果还在上传，取消对应协程；
   - 已上传成功则走现有删除逻辑；
   - 避免继续消耗流量。
4. `tags` 走 `SiteStore`。
5. 发布成功：
   - `ContentBus.onEchoCreated`；
   - 清相关 query 缓存；
   - 保留现有 `DraftUploadRegistry` 去重逻辑。
6. 草稿/队列增加 `baseUrl`，避免跨实例误发。
7. Worker：
   - 顺序处理队列项；
   - 单个项内图片上传也用 `Semaphore(2)`；
   - 只处理当前实例的条目；
   - 失败重试保留已上传 fileId（现有逻辑保留）。
8. 幂等性（服务端配合）：
   - 创建说说/评论增加 `client_request_id`；
   - 超时重试时服务端去重；
   - 在服务端支持前，客户端不自动重试 create/comment。

验收：

- 9 张图最多 2 张同时上传。
- 低内存设备不 OOM。
- 移除图片后不再继续上传。
- 队列不会把旧实例草稿发到新实例。

### 6.6 启动、设置、关于

启动：

1. `Ech0App` 初始化 Store，IO 预加载缓存。
2. `AppRoot` 启动请求改为：
   - `SiteStore.currentUser()`（仅登录时，且不因 token 刷新重复）
   - `SiteStore.settings()`
   - `SiteStore.self()`
   - 三者并行；失败静默。
3. 更新检查延迟 1s，24h 节流。
4. 首屏展示缓存；不要等网络。

设置页：

1. `hello` / `settings` 走 `SiteStore`，打开设置不重复请求。
2. “网络检查”测 `hello` 延迟；`settings` 可并行获取，但不要影响延迟数字。
3. 保存地址后：
   - 如 host 变化，清 token/cache，提示重新登录。
   - 如仅路径/协议变化，保留登录态但清 SiteStore。
4. 清理缓存分“内容缓存 / 图片缓存 / 全部”。

关于页：

1. `hello` 走 `SiteStore`。
2. 检查更新走节流 + ETag。
3. 更新记录缓存 1 小时；离线展示缓存。

### 6.7 更新检查

改造：

- `DataStore` 增加 `lastUpdateCheckAt`。
- 自动检查条件：距上次 > 24h；手动检查忽略节流。
- 请求带 `If-None-Match`（保存 ETag）；304 视为无更新。
- 缓存最近一次 Release JSON/解析结果；离线可展示。
- 失败静默；403/429 记录冷却时间，避免反复打。
- 使用 `HttpClients.update`，不要再建 client。

验收：

- 一天内多次冷启动最多 1 次自动检查。
- GitHub 限流时不会每次启动都重试。
- 离线打开“更新记录”可看到上次缓存。

---

## 7. 图片与三方元数据

### 7.1 Coil

`Ech0App.newImageLoader()` 调整：

```kotlin
ImageLoader.Builder(this)
    .okHttpClient { HttpClients.image }
    .crossfade(180)
    .memoryCache {
        MemoryCache.Builder(this)
            .maxSizePercent(0.25)
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(150L * 1024 * 1024)
            .build()
    }
    .respectCacheHeaders(true)
    .build()
```

- `ImageRequest` 明确 `size` 或让 Compose 约束生效，避免解码原图。
- 低流量模式：不请求头像/封面/正文图；保留点击后加载。
- 图片 URL 统一规范化，避免同一图片不同 query 导致缓存失效。
- 保存/分享图片改为流式写文件，避免 `ByteArray` 全量进内存。

### 7.2 扩展卡

| 数据 | 缓存 | TTL | 失败冷却 | 低流量 |
|---|---|---|---|---|
| GitHub 仓库 | 内存 + 磁盘 | 24h | 10min | 不请求，点开再取 |
| B 站视频 | 内存 | 6h | 10min | 不请求 |
| X 推文 | 内存 | 6h | 10min | 不请求 |
| Meting 音乐 | 内存 | 5min | 5min | 点播放再取 |
| 网站标题 | 不请求 | - | - | - |

实现：

- 通用 `TtlCache<K, V>`，带 `ConcurrentHashMap` + 过期时间 + single-flight。
- GitHub 使用 ETag / `If-None-Match`；304 更新 TTL。
- 音乐卡片不再在每次进入组合时自动请求；显示平台名，点播放时再取；保留封面时用缓存。
- 卡片离开组合时取消未完成请求。
- GitHub 403/429 显示“稍后重试”，不要每次重组都打。

验收：

- 列表来回滚动不重复请求同一卡片元数据。
- 低流量模式不产生三方元数据请求。
- GitHub 限流错误率下降。

---

## 8. 服务端配合（可选，收益高）

> 以下为 `Ech0-main` 侧建议；客户端必须兼容旧服务端，新增能力通过版本/404 降级。

### 8.1 API gzip

现状：只有静态资源 gzip（`internal/handler/web/web.go`），`/api` JSON 未压缩。

建议：

- 在 Gin 或反向代理层加 gzip。
- 排除 `text/event-stream`（`/api/copilot` SSE）。
- 对 JSON 设置 `Vary: Accept-Encoding`。
- 注意不要压缩已经是 gzip 的静态文件。

收益：信息流 HTML/JSON 体积通常可降 60–80%，移动网络收益明显。

### 8.2 发现页聚合接口

新增：

```
GET /api/discover?hot_limit=8
```

返回：

```json
{
  "today": [...],
  "hot": [...],
  "onthisday": [...],
  "heatmap": [...],
  "tags": [...]
}
```

- 服务端并行/顺序组装，减少 5 次 RTT 为 1 次。
- 客户端先尝试新接口；404/版本不支持时降级为并行 5 请求。
- 可加 `ETag`，支持 304。

### 8.3 启动聚合接口（可选）

```
GET /api/bootstrap
```

返回 `hello`、`settings`、`connect`、可选 `user`，减少启动请求数。注意：
- 未登录/已登录返回不同字段；
- 不要把敏感设置放进公开响应；
- 客户端仍需兼容旧接口。

### 8.4 公共 GET 缓存头

- `settings`、`hello`、`tags`、`heatmap`、`connects/info` 可设置短 `Cache-Control` + `ETag`。
- 客户端已有 TTL，但 304 可以进一步省流量。
- 认证接口继续 `no-store`。

### 8.5 幂等键

- `POST /api/echo`、`POST /api/comments` 支持 `client_request_id`。
- 服务端在短时间内对相同 id 去重。
- 客户端在超时重试/Worker 重试时复用同一个 id。
- 这是解决“请求超时但服务端已成功”导致重复发布的关键。

### 8.6 Echo `updated_at`

- 返回 `updated_at`，便于客户端判断实体新旧。
- 当前客户端用 TTL + 失效，短期够用；长期可做 LWW 合并。

---

## 9. 代码结构建议

```
app/src/main/java/top/saltfish/ech0/
├── data/
│   ├── Api.kt                     # 只保留 endpoint + DTO + 信封解析
│   ├── Models.kt
│   ├── Ech0Store.kt               # 设置/偏好，保留
│   ├── net/
│   │   ├── HttpClients.kt         # 新：client 矩阵
│   │   ├── CallAwait.kt           # 新：Call.await()
│   │   ├── HeadersInterceptor.kt  # 新：UA/X-Timezone/X-Locale/Auth host guard
│   │   ├── AuthRetryInterceptor.kt# 新：401 重放
│   │   ├── TokenManager.kt        # 新：single-flight refresh
│   │   ├── RetryInterceptor.kt    # 新：只读请求重试
│   │   └── NetworkMetrics.kt      # 新：debug EventListener
│   ├── repo/
│   │   ├── PostStore.kt           # 新：帖子实体 + 查询缓存
│   │   ├── RemotePostStore.kt     # 新：友链远端帖子
│   │   ├── SiteStore.kt           # 新：站点级数据
│   │   ├── ContentBus.kt          # 新：内容变更通知
│   │   └── CacheKeys.kt           # 新：FeedKey/InstanceScope
│   ├── cache/
│   │   ├── DiskStore.kt           # 新：原子写/TTL/LRU
│   │   └── CacheMigration.kt      # 新：旧缓存清理
│   └── DiskCache.kt               # 过渡期保留，逐步删除
├── ui/
│   ├── timeline/TimelineScreen.kt
│   ├── hub/HubScreen.kt
│   ├── discover/DiscoverScreen.kt
│   ├── detail/DetailScreen.kt
│   ├── detail/RemoteDetailScreen.kt
│   ├── composer/ComposerSheet.kt
│   └── AppRoot.kt
├── publish/
│   ├── PublishStore.kt            # DraftPayload/QueueItem 增加 baseUrl
│   ├── PendingFileDeletions.kt    # 已按 baseUrl 隔离，保留
│   └── PublishWorker.kt
└── Ech0App.kt                     # 初始化 Store / 预加载 / Coil
```

原则：

- `ui/` 不出现 `DiskCache`、`OkHttpClient`、`EchoQueryDto` 的直接构造（查询 key 除外）。
- `Api.kt` 不出现缓存逻辑。
- `repo/` 不出现 Compose 状态（除 `ContentBus` 的 revision）。
- 任何新缓存 key 必须能回答：“属于哪个实例、哪个账号、哪个查询”。

---

## 10. 实施计划

### 10.1 阶段

| 阶段 | 内容 | 预计 | 退出标准 |
|---|---|---|---|
| 0 | 基线测量、Debug 指标、StrictMode | 2–3d | 有请求数/耗时/重复率/卡顿基线 |
| 1 | HttpClients、Call.await、TokenManager、实例隔离、DiskCache IO | 5–7d | 请求可取消；0 主线程磁盘 I/O；切实例不串数据 |
| 2 | PostStore、SiteStore、ContentBus | 7–10d | 首页/友链本地源共享；站点数据只请求一次 |
| 3 | Hub/Discover/Detail 并行、首页搜索防抖、取消 | 5–7d | 友链/发现 p95 达标；离开页面取消 |
| 4 | 上传 Semaphore、三方缓存、更新节流、Coil | 4–6d | 9 图最多 2 并发；三方不重复请求 |
| 5 | 测试、基准、灰度、发布 | 5–7d | 测试通过；指标达标；可回滚 |

### 10.2 依赖关系

```
阶段 0
  └─ 阶段 1
       ├─ 阶段 2 ── 阶段 3
       └─ 阶段 4
                 └─ 阶段 5
```

- 阶段 1 是所有后续工作的基础。
- 阶段 2 先做内存 Store；磁盘合并可放到阶段 4。
- 阶段 3 依赖阶段 1 的取消能力；否则并行 + timeout 仍会占线程。
- 阶段 4 的三方缓存可与阶段 3 并行。
- 阶段 5 需要前四个阶段的指标。

### 10.3 灰度与回滚

- 用 `BuildConfig` 或 DataStore 内部开关 `use_new_data_layer` 控制 PostStore/SiteStore 路径。
- 先内部版本验证 1–2 周，再发布。
- 新旧路径并存一个版本；出问题可关闭开关回到页面直连。
- 磁盘缓存 schema 升到 v2，旧缓存直接清理；回滚版本会重新拉取，不影响正确性。
- 发布前备份关键配置说明，不备份用户 token。

---

## 11. 验收指标

> 阶段 0 先记录基线；以下为目标值，按实际基线校准。

| 指标 | 目标 | 测量方式 |
|---|---|---|
| 重复查询率（同 key TTL 内） | < 5% | Debug 拦截器统计 |
| 首页重复第 1 页请求 | 0（3 分钟内） | 请求日志 |
| 友链首屏 p95（6 个来源，4G） | ≤ 2.5s | 手动/自动埋点 |
| 友链单来源超时 | ≤ 8s，不影响其他来源 | 请求日志 |
| 发现页刷新 p95（4G） | ≤ 1.5s | 请求日志 |
| 详情页打开（有缓存） | 首帧有内容 | 手动/截图 |
| 主线程磁盘 I/O | 0 | StrictMode |
| 离开页面请求取消 | ≤ 100ms | 请求日志 |
| 9 图上传并发 | ≤ 2 | 请求日志 |
| 上传成功率 | ≥ 98% | 队列统计 |
| 冷启动首内容 | ≤ 200ms（缓存） | Macrobenchmark |
| 切 tab 卡顿 | 无掉帧/明显 jank | Macrobenchmark |
| 401 refresh 次数 | 同一时刻 1 次 | 日志 |
| GitHub 403/429 | 自动检查 < 1 次/天 | 日志 |
| 数据层测试覆盖 | ≥ 70% | Jacoco/手工统计 |
| APK 体积增长 | ≤ 300KB | 构建对比 |
| 崩溃率 | 不高于当前版本 | 发布监控 |

---

## 12. 测试方案

### 12.1 单元测试

使用 `MockWebServer` + `kotlinx-coroutines-test`：

- 信封解析：成功、`code=0`、空 data、204、非 JSON。
- 错误映射：401/403/404/429/500、超时、DNS 失败。
- `Call.await()`：正常响应、失败、取消、取消后回调。
- TokenManager：
  - 单次 refresh
  - 并发 401 只 refresh 一次
  - token 已变化时直接重放
  - refresh 失败清登录态
- PostStore：
  - 相同 key single-flight
  - TTL 命中/过期
  - 分页去重
  - upsert/remove/invalidate
  - 实例/账号隔离
- SiteStore：
  - TTL
  - force 刷新
  - baseUrl 变化清空
- DiskStore：
  - 原子写
  - 损坏文件降级
  - schema 不匹配
  - 容量/LRU
- URL 构造：
  - 相对路径、`//`、query 编码、特殊字符
- Hub 并行：
  - 部分来源失败
  - 超时不拖累其他来源
  - 分页不重复请求
- 上传：
  - 并发上限
  - 进度回调
  - 取消
  - 重试

### 12.2 集成测试

- 用 `mock/server.mjs` 或 MockWebServer 模拟：
  - 慢友链（10s）
  - 401 → refresh → 重放
  - 429 + Retry-After
  - 大响应体
  - 图片 403/UA 拦截
- 场景：
  - 切换实例
  - 退出登录
  - 发布/编辑/删除
  - 9 图上传
  - 断网/恢复
  - 低流量模式

### 12.3 UI/Compose 测试

- 首屏缓存展示
- 刷新失败保留旧内容
- 离线 banner
- 错误重试
- 实例切换后空状态/重新加载
- 发布后列表更新

### 12.4 性能测试

- Macrobenchmark：冷启动、切 tab、滚动、打开详情。
- StrictMode：磁盘/网络主线程违规。
- 内存：9 图压缩/上传、长列表滚动。
- 网络：Wi-Fi、4G、弱网、离线、高延迟（MockWebServer 延迟）。
- 设备：Android 10、13、16；低内存设备。

### 12.5 CI

- `./gradlew testDebugUnitTest lintDebug assembleDebug`
- 可选 nightly：MockWebServer 集成测试 + Macrobenchmark（需设备/模拟器）。
- 新增依赖（仅测试）：
  - `junit:junit`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-test`
  - `com.squareup.okhttp3:mockwebserver`
  - `app.cash.turbine:turbine`
  - `com.google.truth:truth`
  - `androidx.compose.ui:ui-test-junit4`
  - `androidx.benchmark:benchmark-macro-junit4`

---

## 13. 风险与回滚

| 风险 | 影响 | 缓解 | 回滚 |
|---|---|---|---|
| 共享缓存导致内容过期 | 用户看到旧内容 | TTL + ContentBus 失效 + 手动刷新 | 关闭 Store 开关，回页面直连 |
| 并行请求打满自建实例 | 服务端压力 | Semaphore(4)、TTL、按需刷新 | 降并发/改回串行 |
| 超时太短导致慢实例失败 | 自建服务器可用性下降 | 默认 20s read；按实例可配置 | 恢复 60s |
| 私有缓存泄漏 | 隐私问题 | 实例/账号命名空间；退出清缓存；测试 | 清缓存 + 强制重新登录 |
| 磁盘迁移损坏 | 首屏无缓存 | 原子写、schema、损坏降级 | 删除 v2 缓存重新拉 |
| 重试导致重复发布/评论 | 数据重复 | 变更请求不自动重试；服务端幂等键 | 关闭重试 |
| 草稿跨实例误发 | 发到错误站点 | Draft/Queue 带 baseUrl；Worker 校验 | 保留旧数据并提示 |
| 页面并行后状态竞争 | 列表闪烁/旧结果覆盖 | generation/取消/只接受最新响应 | 回退单请求 |
| 三方缓存过期 | 卡片信息旧 | TTL + 手动刷新 | 清缓存 |
| 体积增加 | APK 变大 | 不新增运行时大依赖 | 移除可选依赖 |

---

## 14. 任务清单

| ID | 任务 | 优先级 | 预估 | 依赖 | 验收 |
|---|---|---|---|---|---|
| NET-101 | 建立请求指标/基线 | P0 | 2d | - | 有重复率/耗时基线 |
| NET-102 | `HttpClients` + 共享连接池 | P0 | 2d | 101 | 5 个 client 派生，连接复用 |
| NET-103 | `Call.await()` + 取消 | P0 | 2d | 102 | 离开页面取消 |
| NET-104 | `TokenManager` single-flight | P0 | 2d | 102 | 并发 401 只刷新一次 |
| NET-105 | 实例/账号隔离 + baseUrl 切换清理 | P0 | 3d | 102 | 切实例不串数据/token |
| NET-106 | `DiskStore` v2 + 主线程 I/O 清理 | P0 | 3d | 105 | StrictMode 0 违规 |
| NET-201 | `PostStore` 实体 + 查询缓存 | P0 | 4d | 103/105 | 首页/友链共享 |
| NET-202 | `SiteStore` | P1 | 3d | 103/105 | settings/hello/self/tags 去重 |
| NET-203 | `ContentBus` + 页面订阅 | P0 | 2d | 201 | 发布/编辑/删除同步 |
| NET-301 | 友链并行 + 每源超时 | P0 | 3d | 201/203 | 6 源 p95 ≤ 2.5s |
| NET-302 | 发现页并行 + section 独立更新 | P0 | 2d | 202 | p95 ≤ 1.5s |
| NET-303 | 首页搜索防抖/generation | P1 | 2d | 201 | 无旧结果覆盖 |
| NET-304 | 详情并行 + 取消 | P1 | 2d | 201 | 首帧缓存/取消 |
| NET-305 | Zen 接入 PostStore | P2 | 1d | 201 | 重复请求下降 |
| NET-401 | 上传 Semaphore + 取消 | P1 | 2d | 103 | 9 图最多 2 并发 |
| NET-402 | 三方卡片 TTL/失败冷却/低流量 | P1 | 3d | 102 | 滚动不重复请求 |
| NET-403 | 更新检查节流 + ETag | P2 | 1d | 102 | 24h 最多 1 次 |
| NET-404 | Coil 配置/图片下载流式 | P2 | 1d | 102 | 内存/磁盘可配 |
| NET-501 | 单元/集成/UI 测试 | P0 | 5d | 全部 | 覆盖关键路径 |
| NET-502 | Macrobenchmark/StrictMode | P1 | 2d | 全部 | 指标达标 |
| NET-503 | 灰度/回滚开关/发布文档 | P0 | 2d | 全部 | 可回滚 |
| SRV-601 | API gzip（排除 SSE） | P2 | 1d | - | 体积下降 |
| SRV-602 | `/api/discover` 聚合 | P2 | 2d | - | 5 请求变 1 |
| SRV-603 | 幂等键 | P2 | 2d | - | 重试不重复 |
| SRV-604 | 公共 GET ETag | P3 | 2d | - | 304 生效 |

---

## 15. 附录

### 15.1 API 清单与归属

| API | 方法 | 认证 | 当前调用方 | 目标 Store |
|---|---|---|---|---|
| `/api/hello` | GET | 否 | AppRoot/设置/关于/Setup | SiteStore |
| `/api/settings` | GET | 否 | AppRoot/设置/Setup | SiteStore |
| `/api/init/status` | GET | 否 | 未使用 | 删除或保留 |
| `/api/login` | POST | 否 | 登录 | Auth |
| `/api/auth/refresh` | POST | Cookie | 拦截器 | TokenManager |
| `/api/user` | GET | 是 | 登录/AppRoot/设置 | SiteStore |
| `/api/echo/query` | POST | 可选 | 首页/友链本地/Zen | PostStore |
| `/api/echo/page` | POST | 可选 | 友链远端 | RemotePostStore |
| `/api/echo/{id}` | GET | 可选 | 详情 | PostStore/Detail |
| `/api/echo/today` | GET | 可选 | 发现 | DiscoverStore |
| `/api/echo/hot` | GET | 可选 | 发现 | DiscoverStore |
| `/api/echo/random` | GET | 可选 | 发现 | 直接调用（短缓存可选） |
| `/api/echo/onthisday` | GET | 可选 | 发现 | DiscoverStore |
| `/api/heatmap` | GET | 否 | 发现 | SiteStore/DiscoverStore |
| `/api/tags` | GET | 否 | 发现/编辑器 | SiteStore |
| `/api/comments/form` | GET | 否 | 详情 | 直接调用 |
| `/api/comments` | GET/POST | 可选 | 详情 | 直接调用/Detail |
| `/api/files/upload` | POST | 是 | 编辑器/Worker | Upload |
| `/api/connect` | GET | 否 | AppRoot/友链 | SiteStore |
| `/api/connects/info` | GET | 否 | 友链 | SiteStore |
| GitHub Release | GET | 否 | 启动/关于 | UpdateChecker |
| GitHub Repo | GET | 否 | 卡片 | ExtensionCache |
| B 站 view | GET | 否 | 卡片 | ExtensionCache |
| X oEmbed | GET | 否 | 卡片 | ExtensionCache |
| Meting API | GET | 否 | 音乐卡 | ExtensionCache |

### 15.2 关键代码草图

#### 15.2.1 Call.await

```kotlin
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
```

#### 15.2.2 FeedKey

```kotlin
data class FeedKey(
    val baseUrl: String,
    val viewer: String,
    val page: Int,
    val pageSize: Int,
    val search: String? = null,
    val tagIds: List<String> = emptyList(),
    val sortBy: String = "created_at",
    val sortOrder: String = "desc"
)
```

#### 15.2.3 友链并行

```kotlin
val semaphore = Semaphore(4)
val results = coroutineScope {
    sources.map { source ->
        async {
            semaphore.withPermit {
                runCatching {
                    withTimeout(8_000) { fetchSource(source) }
                }
            }
        }
    }.awaitAll()
}
```

#### 15.2.4 原子写

```kotlin
suspend fun writeAtomically(file: File, bytes: ByteArray) = withContext(Dispatchers.IO) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.outputStream().use { out ->
        out.write(bytes)
        out.flush()
        (out as? FileOutputStream)?.fd?.sync()
    }
    if (!tmp.renameTo(file)) {
        file.delete()
        if (!tmp.renameTo(file)) error("rename failed: $file")
    }
}
```

#### 15.2.5 ContentBus

```kotlin
object ContentBus {
    var feedRevision by mutableIntStateOf(0)
        private set

    fun bumpFeed() {
        feedRevision++
    }
}
```

### 15.3 文件变更清单

新增：

- `data/net/HttpClients.kt`
- `data/net/CallAwait.kt`
- `data/net/HeadersInterceptor.kt`
- `data/net/AuthRetryInterceptor.kt`
- `data/net/TokenManager.kt`
- `data/net/RetryInterceptor.kt`
- `data/net/NetworkMetrics.kt`
- `data/repo/PostStore.kt`
- `data/repo/RemotePostStore.kt`
- `data/repo/SiteStore.kt`
- `data/repo/ContentBus.kt`
- `data/repo/CacheKeys.kt`
- `data/cache/DiskStore.kt`
- `data/cache/CacheMigration.kt`

修改：

- `data/Api.kt`：移除 client 构建/超时/刷新逻辑，保留 endpoint + DTO + 信封解析。
- `data/Ech0Store.kt`：baseUrl 变化清状态；liked 按实例/账号；`setBaseUrl` 触发失效。
- `data/DiskCache.kt`：标记 deprecated；迁移期只读兜底。
- `Ech0App.kt`：初始化 Store、预加载缓存、Coil 配置。
- `ui/AppRoot.kt`：启动请求走 SiteStore；更新检查节流；发布走 ContentBus。
- `ui/timeline/TimelineScreen.kt`：PostStore + generation + 防抖。
- `ui/hub/HubScreen.kt`：并行 + SiteStore + PostStore。
- `ui/discover/DiscoverScreen.kt`：并行 + section 独立更新 + SiteStore。
- `ui/detail/DetailScreen.kt`：并行 + PostStore + ContentBus。
- `ui/detail/RemoteDetailScreen.kt`：并行 + 远端 client。
- `ui/composer/ComposerSheet.kt`：Semaphore + SiteStore + 取消。
- `publish/PublishStore.kt`：DraftPayload/QueueItem 增加 baseUrl。
- `publish/PublishWorker.kt`：实例校验 + 上传并发。
- `data/AppUpdates.kt`：节流/ETag/共享 client。

删除/替换：

- `DiskCache` 的页面调用点。
- `Ech0Api.client` / `publicClient` 的公开暴露（改为内部/兼容别名）。
- `UpdateChecker.client`。

---

## 16. 结论

这套方案的核心不是“加缓存”，而是把网络请求从“每个页面各自为战”改成：

- **一个网络基础层**：统一 client、超时、取消、认证、重试、指标。
- **一个共享数据层**：同一实例/账号的帖子、站点信息、友链信息只存一份。
- **一个失效总线**：发布/编辑/删除后所有页面同步。
- **一个实例边界**：缓存、token、草稿、队列都不跨实例。

优先做 **阶段 1 + 阶段 2**，就能解决大部分重复请求、串数据和卡顿；阶段 3 解决友链/发现页的体感；阶段 4 解决上传、三方和更新检查的长尾问题。

---

## 17. 当前实施进度（2026-10-31）

### 已完成

- [x] `HttpClients`：共享 `ConnectionPool` / `Dispatcher`，派生 api / upload / image / remote / thirdParty / update。
- [x] `Call.await()`：API、上传、三方、更新检查全部改为可取消异步调用；`doRefresh` 保留同步（拦截器线程内）。
- [x] 分层超时：API 10s/20s/30s，上传 180s/300s，图片 30s/45s，三方 10s/15s，更新 15s/20s。
- [x] 401 刷新去重：并发 401 时检查 token 是否已被其他请求刷新，避免重复 refresh。
- [x] 实例隔离：
  - `DiskCache` 目录改为 `cache/v2/<hash(baseUrl|viewer)>/`
  - `Ech0Store.setBaseUrl` 清 token / user / liked / serverName / cookies / 页面缓存
  - 切换 host 或协议时清登录态；首次配置也清旧 token
  - 退出登录清 liked 和页面缓存
  - `DraftPayload` 增加 `baseUrl`，Worker 拒绝跨实例发布
- [x] 磁盘缓存：`save*` 全部移入 `Dispatchers.IO`；Timeline / Discover / Hub 的缓存读取移入 `LaunchedEffect` + IO。
- [x] 上传并发：编辑器最多 2 路压缩/上传。
- [x] 字符串/URL：`createTag` 改用 serializer；`resolveUrl` 支持协议相对 URL。
- [x] 响应体读取：新增 `Response.bodyText()` / `bodyBytes()`，统一放到 IO，修复 `Call.await()` 恢复后在 Main 读 body 导致的 `NetworkOnMainThreadException`。
- [x] 验证：`:app:compileDebugKotlin`、`:app:assembleDebug` 通过；lint 仅剩项目原有 location/API 兼容错误。

### MCP 冒烟测试（headless emulator `ech0test`）

- 安装、启动、首页首屏通过
- 发现页 5 个接口全部加载通过
- 友链页本站 + 2 个远端来源加载通过
- 设置页网络检查通过（mock 17ms）
- 登录 `saltfish / ech0` 通过
- 发布一条纯文本说说通过，时间线自动刷新
- 断网重启：磁盘缓存内容保留，错误提示正确，不闪空
- 切换实例：旧列表/缓存清空，不再显示旧站内容
- 测试截图：
  - `app/build/outputs/smoke-3.png`
  - `app/build/outputs/smoke-discover.png`
  - `app/build/outputs/smoke-hub.png`
  - `app/build/outputs/smoke-settings.png`
  - `app/build/outputs/smoke-offline.png`

### 阶段 2 已完成

- [x] `PostStore`：
  - `FeedKey`（实例 + viewer + 查询参数）
  - 查询页 3 分钟缓存 + single-flight
  - 实体缓存 / upsert / remove / 失效
  - 失效代际，避免失效前发出的请求把旧结果写回
- [x] `SiteStore`：
  - `settings` / `hello` / `self` / `connects` / `currentUser` / `tags`
  - TTL + single-flight + 失效代际
  - 切换实例/账号时统一失效
- [x] `ContentBus`：
  - 发布/编辑/删除后递增 `feedRevision`
  - 首页、发现页、友链页观察并刷新
- [x] 页面接入：
  - 首页 `PostStore.query`
  - 友链本地源 `PostStore.query`，与首页共享默认第 1 页
  - Zen `PostStore.query`
  - AppRoot / Hub / 发现 / 编辑器 / 设置 / 关于接入 `SiteStore`
  - `Ech0Api.metingApi()` 复用 `SiteStore.settings()`

### MCP 验证：阶段 2 去重结果

冷启动到首页：

- `/api/user`、`/api/settings`、`/api/echo/query`、`/api/connect` 各 1 次
- 音乐卡加载时没有再重复请求 `/api/settings`（复用 `SiteStore`）

从首页切到友链：

- 没有重复请求 `/api/connect`
- 没有重复请求本地 `/api/echo/query`（复用首页第 1 页）
- 只请求 `/api/connects/info` + 两个远端实例

打开发现页后再打开编辑器：

- 编辑器没有重复请求 `/api/tags`（复用发现页的 `SiteStore.tags`）

发布测试：

- 发布后首页/发现页立即出现新内容
- 切到友链后，ContentBus 触发刷新，新内容出现在本地来源顶部

### 阶段 3 已完成

- [x] 友链聚合并行：
  - `Semaphore(4)` 限制来源并发
  - 每个来源 `withTimeout(8s)`
  - 结果按来源顺序合并，保留单来源失败隔离
  - 加载更多不再请求元数据；首屏/刷新按需 force
- [x] 发现页并行：
  - today / hot / heatmap / tags / onthisday 同时发起
  - 统一 `DiscoverLoadResult`，失败时保留旧 section
- [x] 详情页并行：
  - 本地：正文 + 评论并行
  - 远端：正文 + 实例信息 + 评论并行
- [x] 手动刷新语义：
  - 首页/友链下拉、刷新按钮、搜索、标签切换、ContentBus 刷新会 force
  - 初次加载、分页、静默 TTL 刷新允许复用缓存

### MCP 验证：并行结果

- 发现页 5 个请求落在 20ms 内：
  - `07:58:28.735` today
  - `07:58:28.743` hot
  - `07:58:28.748` tags
  - `07:58:28.749` onthisday
  - `07:58:28.755` heatmap
- 友链两个远端来源落在 3ms 内：
  - `07:58:38.619` remote2
  - `07:58:38.622` remote1
- 详情正文 + 评论落在 1ms 内：
  - `07:58:48.015` `/api/echo/e01`
  - `07:58:48.016` `/api/comments`

### 阶段 4 已完成（第一批）

- [x] 上传取消：
  - `ImageItem.uploadJob`
  - 重新上传/移除图片时取消旧协程，不再继续消耗流量
- [x] Coil：
  - 内存缓存 25%
  - 磁盘缓存 `cacheDir/image_cache`，上限 150MB
- [x] 更新检查节流：
  - DataStore 记录 `last_update_check`
  - 自动检查默认 24 小时一次
- [x] 音乐扩展卡：
  - 5 分钟内存缓存 + 2 分钟失败冷却
  - 低流量模式不预取元数据，用户点播放时再请求
  - 播放地址仍保留 Meting 的 `r` 时间戳，避免使用过期 URL
- [x] GitHub / B 站 / 推文卡片：
  - 低流量模式不预取元数据
  - GitHub 显示“低流量模式，点开查看”，不再永久停在加载中

### 磁盘缓存加固

- [x] `DiskCache` 单写者 `Mutex`
- [x] 原子写：临时文件 + rename
- [x] 详情缓存最多 100 个文件，超出后删最旧
- [x] 启动时清理旧版 v1 缓存（只删根目录 json，不动 v2 实例目录）

### 阶段 5（第一批测试）

- [x] 单元测试基础设施：JUnit4 + kotlinx-coroutines-test + MockWebServer
- [x] `CallAwaitTest`：正常响应、401 响应
- [x] `ApiUrlTest`：相对路径、协议相对 URL、绝对 URL
- [x] `UpdateCheckerTest`：版本比较、预发布后缀
- [x] `:app:testDebugUnitTest` 通过

### 待办

- [ ] GitHub / B 站 / 推文卡片持久化 TTL
- [ ] 更多 Repository/缓存测试、Macrobenchmark、灰度发布

---

## 18. 构建产物（未发布）

- APK：`dist/ech0-mobile-0.5.3-netopt.apk`
- SHA-256：`ffc88abe2d671652dabae9d435a3daf4c225ddd5e8fa9297b4c3cc894b31cc59`
- 包名：`com.saltfish.echo`
- versionName：`0.5.3`
- versionCode：`11`
- 未创建 GitHub Release / tag，未上传任何发布渠道
- 已在 `ech0test` 模拟器安装并启动验证

---

## 19. 首页动态刷新与月份面板（2026-10-31 补充）

- [x] 首页按“信息流缓存间隔”定时静默刷新（3 / 10 / 30 分钟）
- [x] 从后台回到前台时，缓存过期会静默刷新
- [x] 静默刷新会更新已加载帖子实体，并把新帖插入顶部
- [x] 移除月份面板的 `recentMonths()` 占位月份，首页和友链都只展示实际有内容的月份
- [x] 月份面板新增“加载更早”，继续翻页后动态扩展月份列表
- [x] 月份列表随 `items` 变化自动重组，不再需要关闭重开面板
