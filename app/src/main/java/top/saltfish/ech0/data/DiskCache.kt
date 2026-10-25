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
    data class HubEntry(val echo: Echo, val source: String)

    @Serializable
    data class HubCache(
        val savedAt: Long,
        val self: ConnectInfo?,
        val friends: List<ConnectInfo>,
        val merged: List<HubEntry>
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun dir(context: Context): File = File(context.filesDir, "cache")

    private fun file(context: Context, name: String): File = File(dir(context), name)

    fun loadTimeline(context: Context): List<Echo>? = runCatching {
        val f = file(context, "timeline.json")
        if (!f.exists()) return null
        json.decodeFromString<TimelineCache>(f.readText()).items.takeIf { it.isNotEmpty() }
    }.getOrNull()

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
}
