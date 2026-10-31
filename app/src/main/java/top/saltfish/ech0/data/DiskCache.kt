package top.saltfish.ech0.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** 页面级磁盘缓存：每次打开先显示缓存，再后台刷新覆盖 */
object DiskCache {

    @Serializable
    data class TimelineCache(val savedAt: Long, val items: List<Echo>)

    @Serializable
    data class DiscoverCache(
        val savedAt: Long,
        val today: List<Echo>,
        val hot: List<Echo>,
        val otd: List<Echo>,
        val heatmap: List<HeatmapDay>,
        val tags: List<Tag>
    )

    @Serializable
    data class HubEntry(
        val echo: Echo,
        val source: String,
        val sourceUrl: String = ""
    )

    @Serializable
    data class HubCache(
        val savedAt: Long,
        val self: ConnectInfo?,
        val friends: List<ConnectInfo>,
        val merged: List<HubEntry>,
        /** 每个来源已加载到的页码 / 是否到底，用于重启后继续加载而不是重复拉第 1 页 */
        val sourcePages: Map<String, Int> = emptyMap(),
        val exhaustedSources: Set<String> = emptySet()
    )

    /** 详情页缓存：先显示上次内容，再后台刷新 */
    @Serializable
    data class DetailCache(
        val savedAt: Long,
        val echo: Echo,
        val comments: List<PublicComment> = emptyList(),
        val serverName: String = "",
        val serverLogo: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val ioMutex = Mutex()
    private const val MaxDetailCacheFiles = 100

    /**
     * 缓存按「实例 + 账号」隔离，避免切换实例/退出登录后看到上一个实例的私有内容。
     * baseUrl 或账号变化时会自然落到新目录；旧目录在切换时由 clearAll 清理。
     */
    private fun scopeKey(): String {
        val base = Ech0Api.base().trimEnd('/').ifBlank { "unconfigured" }
        val viewer = Ech0Store.current.let { state ->
            when {
                state.username.isBlank() && state.accessToken.isNullOrBlank() -> "guest"
                // 有 token 但用户名还没同步：不要落到 guest 目录，避免私有内容被游客缓存读到
                state.username.isBlank() -> "auth:pending"
                else -> "u:${state.username}:${if (state.isAdmin) "admin" else "user"}"
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$base|$viewer".toByteArray())
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(24)
    }

    private fun root(context: Context): File = File(context.filesDir, "cache")

    private fun dir(context: Context): File = File(root(context), "v2/${scopeKey()}")

    private fun file(context: Context, name: String): File = File(dir(context), name)

    /** 切换实例/账号时清空所有页面缓存；图片缓存由 Coil 自己管理，不在这里 */
    suspend fun clearAll(context: Context) {
        ioMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { root(context).deleteRecursively() }
            }
        }
    }

    /** 清理旧版 v1 缓存（cache 目录下的 json），只保留 v2 实例目录 */
    fun clearLegacy(context: Context) {
        runCatching {
            root(context).listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                ?.forEach { it.delete() }
        }
    }

    private fun writeAtomic(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) error("cache rename failed: ${file.name}")
        }
    }

    private fun pruneDetailCache(context: Context) {
        val files = runCatching {
            dir(context).listFiles { f ->
                f.isFile && f.name.startsWith("detail_") && f.name.endsWith(".json")
            }?.sortedBy { it.lastModified() }
        }.getOrNull() ?: return
        files.dropLast(MaxDetailCacheFiles).forEach { it.delete() }
    }

    fun loadTimelineCache(context: Context): TimelineCache? = runCatching {
        val f = file(context, "timeline.json")
        if (!f.exists()) return null
        json.decodeFromString<TimelineCache>(f.readText()).takeIf { it.items.isNotEmpty() }
    }.getOrNull()

    fun loadTimeline(context: Context): List<Echo>? = loadTimelineCache(context)?.items

    suspend fun saveTimeline(context: Context, items: List<Echo>) {
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    writeAtomic(
                        file(context, "timeline.json"),
                        json.encodeToString(TimelineCache(System.currentTimeMillis(), items))
                    )
                }
            }
        }
    }

    fun loadDiscover(context: Context): DiscoverCache? = runCatching {
        val f = file(context, "discover.json")
        if (!f.exists()) return null
        json.decodeFromString<DiscoverCache>(f.readText())
    }.getOrNull()

    suspend fun saveDiscover(context: Context, cache: DiscoverCache) {
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    writeAtomic(file(context, "discover.json"), json.encodeToString(cache))
                }
            }
        }
    }

    fun loadHub(context: Context): HubCache? = runCatching {
        val f = file(context, "hub.json")
        if (!f.exists()) return null
        json.decodeFromString<HubCache>(f.readText())
    }.getOrNull()

    /** 页面缓存最近一次写入时间；用于设置页「上次刷新」 */
    fun latestSavedAt(context: Context): Long = listOfNotNull(
        loadTimelineCache(context)?.savedAt,
        loadDiscover(context)?.savedAt,
        loadHub(context)?.savedAt
    ).maxOrNull() ?: 0L

    suspend fun saveHub(context: Context, cache: HubCache) {
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    writeAtomic(file(context, "hub.json"), json.encodeToString(cache))
                }
            }
        }
    }

    private fun detailKey(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(32)
    }

    fun loadDetail(context: Context, key: String): DetailCache? = runCatching {
        val f = file(context, "detail_${detailKey(key)}.json")
        if (!f.exists()) return null
        json.decodeFromString<DetailCache>(f.readText())
    }.getOrNull()

    suspend fun saveDetail(context: Context, key: String, cache: DetailCache) {
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    writeAtomic(
                        file(context, "detail_${detailKey(key)}.json"),
                        json.encodeToString(cache)
                    )
                    pruneDetailCache(context)
                }
            }
        }
    }
}
