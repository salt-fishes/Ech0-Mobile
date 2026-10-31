package top.saltfish.ech0.data.repo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.EchoQueryDto
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.data.PageResult

/**
 * 信息流查询 key：实例 + 账号 + 查询参数。
 *
 * 首页和友链本地源在默认排序、pageSize=20 时会得到同一个 key，
 * 因此可以共享同一次 /api/echo/query 请求和结果。
 */
data class FeedKey(
    val baseUrl: String,
    val viewer: String,
    val page: Int,
    val pageSize: Int,
    val search: String? = null,
    val tagIds: List<String> = emptyList(),
    val sortBy: String = "created_at",
    val sortOrder: String = "desc",
    val privateFilter: Boolean? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null
) {
    fun toDto(): EchoQueryDto = EchoQueryDto(
        page = page,
        pageSize = pageSize,
        search = search,
        tagIds = tagIds.ifEmpty { null },
        sortBy = sortBy,
        sortOrder = sortOrder,
        dateFrom = dateFrom,
        dateTo = dateTo,
        private = privateFilter
    )
}

/**
 * 帖子查询/实体共享缓存（内存版）。
 *
 * 只负责：
 * - 相同查询期间的 single-flight
 * - 3 分钟内的查询结果复用
 * - 同一实例下的 Echo 实体共享
 *
 * 磁盘缓存仍由现有 DiskCache 负责，后续阶段再合并。
 */
object PostStore {

    private fun pageTtlMs(): Long =
        (top.saltfish.ech0.data.Ech0Store.current.feedCacheTtlMinutes.coerceIn(1, 60)) * 60_000L

    private data class CachedPage(
        val fetchedAt: Long,
        val result: PageResult<Echo>
    )

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pages = mutableMapOf<FeedKey, CachedPage>()
    private val entities = mutableMapOf<String, Echo>()
    private val inFlight = mutableMapOf<FeedKey, Deferred<PageResult<Echo>>>()
    /** 失效代际：失效前发出的请求完成后不能把旧结果写回缓存 */
    private var generation = 0

    /** 内容变化计数，供页面观察后重新拉取 */
    var revision by mutableIntStateOf(0)
        private set

    fun currentViewerKey(): String {
        val state = Ech0Store.current
        return when {
            state.username.isBlank() && state.accessToken.isNullOrBlank() -> "guest"
            // 有 token 但用户名还没同步：不要落到 guest 缓存
            state.username.isBlank() -> "auth:pending"
            else -> "u:${state.username}:${if (state.isAdmin) "admin" else "user"}"
        }
    }

    fun keyOf(query: EchoQueryDto): FeedKey = FeedKey(
        baseUrl = Ech0Api.base().trimEnd('/'),
        viewer = currentViewerKey(),
        page = query.page,
        pageSize = query.pageSize,
        search = query.search?.trim()?.takeIf { it.isNotBlank() },
        tagIds = query.tagIds.orEmpty().sorted(),
        sortBy = query.sortBy,
        sortOrder = query.sortOrder,
        privateFilter = query.private,
        dateFrom = query.dateFrom,
        dateTo = query.dateTo
    )

    /**
     * @param force true 时跳过 3 分钟页缓存；仍会复用正在进行的相同请求。
     */
    suspend fun query(query: EchoQueryDto, force: Boolean = false): PageResult<Echo> =
        query(keyOf(query), force)

    suspend fun query(key: FeedKey, force: Boolean = false): PageResult<Echo> {
        if (!force) {
            val hit = synchronized(lock) {
                pages[key]?.takeIf { System.currentTimeMillis() - it.fetchedAt < pageTtlMs() }
            }
            if (hit != null) return hit.result
        }

        val deferred = synchronized(lock) {
            val gen = generation
            inFlight[key] ?: scope.async {
                val result = Ech0Api.queryEchos(key.toDto())
                synchronized(lock) {
                    if (generation == gen) {
                        result.items.forEach { putLocked(it, key.baseUrl) }
                        pages[key] = CachedPage(System.currentTimeMillis(), result)
                    }
                }
                result
            }.also { inFlight[key] = it }
        }
        return try {
            deferred.await()
        } finally {
            synchronized(lock) {
                if (inFlight[key] === deferred) inFlight.remove(key)
            }
        }
    }

    /** 详情/发布/编辑后更新实体，并同步所有已经缓存的页 */
    fun upsert(echo: Echo, baseUrl: String = Ech0Api.base().trimEnd('/')) {
        val normalized = baseUrl.trimEnd('/')
        synchronized(lock) {
            putLocked(echo, normalized)
            pages.keys.toList().forEach { key ->
                if (key.baseUrl != normalized) return@forEach
                val cached = pages[key] ?: return@forEach
                if (cached.result.items.none { it.id == echo.id }) return@forEach
                pages[key] = cached.copy(
                    result = cached.result.copy(
                        items = cached.result.items.map { if (it.id == echo.id) echo else it }
                    )
                )
            }
        }
    }

    fun remove(id: String, baseUrl: String = Ech0Api.base().trimEnd('/')) {
        val normalized = baseUrl.trimEnd('/')
        synchronized(lock) {
            entities.remove("$normalized|$id")
            pages.keys.toList().forEach { key ->
                if (key.baseUrl != normalized) return@forEach
                val cached = pages[key] ?: return@forEach
                val remaining = cached.result.items.filterNot { it.id == id }
                if (remaining.size != cached.result.items.size) {
                    pages[key] = cached.copy(
                        result = cached.result.copy(
                            items = remaining,
                            total = (cached.result.total - 1).coerceAtLeast(0)
                        )
                    )
                }
            }
        }
        revision++
    }

    /** 发布/编辑/删除后清查询页；实体保留，详情仍可立即显示 */
    fun invalidateQueries() {
        synchronized(lock) {
            generation++
            pages.clear()
            inFlight.clear()
        }
        revision++
    }

    /** 切换实例/账号时清全部内存缓存 */
    fun invalidateAll() {
        synchronized(lock) {
            generation++
            pages.clear()
            entities.clear()
            inFlight.clear()
        }
        revision++
    }

    private fun putLocked(echo: Echo, baseUrl: String) {
        entities["$baseUrl|${echo.id}"] = echo
    }
}
