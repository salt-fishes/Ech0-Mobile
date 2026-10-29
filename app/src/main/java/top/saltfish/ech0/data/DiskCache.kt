package top.saltfish.ech0.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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

    private fun dir(context: Context): File = File(context.filesDir, "cache")

    private fun file(context: Context, name: String): File = File(dir(context), name)

    fun loadTimelineCache(context: Context): TimelineCache? = runCatching {
        val f = file(context, "timeline.json")
        if (!f.exists()) return null
        json.decodeFromString<TimelineCache>(f.readText()).takeIf { it.items.isNotEmpty() }
    }.getOrNull()

    fun loadTimeline(context: Context): List<Echo>? = loadTimelineCache(context)?.items

    fun saveTimeline(context: Context, items: List<Echo>) {
        runCatching {
            val f = file(context, "timeline.json")
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(TimelineCache(System.currentTimeMillis(), items)))
        }
    }

    fun loadDiscover(context: Context): DiscoverCache? = runCatching {
        val f = file(context, "discover.json")
        if (!f.exists()) return null
        json.decodeFromString<DiscoverCache>(f.readText())
    }.getOrNull()

    fun saveDiscover(context: Context, cache: DiscoverCache) {
        runCatching {
            val f = file(context, "discover.json")
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(cache))
        }
    }

    fun loadHub(context: Context): HubCache? = runCatching {
        val f = file(context, "hub.json")
        if (!f.exists()) return null
        json.decodeFromString<HubCache>(f.readText())
    }.getOrNull()

    fun saveHub(context: Context, cache: HubCache) {
        runCatching {
            val f = file(context, "hub.json")
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(cache))
        }
    }

    private fun detailKey(key: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(32)
    }

    fun loadDetail(context: Context, key: String): DetailCache? = runCatching {
        val f = file(context, "detail_${detailKey(key)}.json")
        if (!f.exists()) return null
        json.decodeFromString<DetailCache>(f.readText())
    }.getOrNull()

    fun saveDetail(context: Context, key: String, cache: DetailCache) {
        runCatching {
            val f = file(context, "detail_${detailKey(key)}.json")
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(cache))
        }
    }
}
