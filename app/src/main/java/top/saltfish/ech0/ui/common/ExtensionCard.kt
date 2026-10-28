package top.saltfish.ech0.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.EchoExtension
import java.net.URI
import java.util.Locale

private val urlPattern = Regex("""https?://[^\s<>()\[\]{}，。；、""']+""")

private fun JsonObject.str(key: String): String =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull.orEmpty() }.getOrDefault("")

private fun hostOf(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")

private fun detectExtension(url: String): EchoExtension? {
    val lower = url.lowercase(Locale.ROOT)
    return when {
        lower.contains("github.com/") && lower.split("github.com/").getOrNull(1)
            ?.trim('/')?.split('/')?.size?.let { it >= 2 } == true -> {
            EchoExtension("GITHUBPROJ", buildJson { put("repoUrl", url) })
        }
        lower.contains("bilibili.com/video/") -> {
            val id = url.substringAfter("bilibili.com/video/").substringBefore('?').trim('/')
            id.takeIf { it.startsWith("BV", true) }
                ?.let { EchoExtension("VIDEO", buildJson { put("videoId", it) }) }
        }
        lower.contains("youtu.be/") -> {
            val id = url.substringAfter("youtu.be/").substringBefore('?').substringBefore('&')
            id.takeIf { it.isNotBlank() }
                ?.let { EchoExtension("VIDEO", buildJson { put("videoId", it) }) }
        }
        lower.contains("youtube.com/watch") -> {
            val id = url.substringAfter("v=", "").substringBefore('&')
            id.takeIf { it.isNotBlank() }
                ?.let { EchoExtension("VIDEO", buildJson { put("videoId", it) }) }
        }
        listOf("music.163.com", "y.qq.com", "spotify.com", "music.apple.com")
            .any { lower.contains(it) } -> EchoExtension("MUSIC", buildJson { put("url", url) })
        lower.contains("/status/") && (lower.contains("twitter.com/") || lower.contains("x.com/")) -> {
            val user = url.substringAfter("://").substringAfter('/').substringBefore('/')
            val status = url.substringAfter("/status/").substringBefore('?').substringBefore('/')
            EchoExtension(
                "TWEET",
                buildJson {
                    put("url", url)
                    put("username", user)
                    put("statusId", status)
                }
            )
        }
        else -> null
    }
}

/** 帖子里的扩展卡：优先用服务端 extension，其次从正文链接自动识别 */
fun resolveExtension(echo: Echo): EchoExtension? {
    val explicit = echo.extension
    if (explicit != null && explicit.type.isNotBlank() && explicit.payload != null) return explicit
    val urls = urlPattern.findAll(echo.content).map { it.value.trimEnd('.', ',', '，', '。') }.toList()
    urls.forEach { url -> detectExtension(url)?.let { return it } }
    // 正文就是一条链接时，退化成网站卡
    if (urls.size == 1 && echo.content.trim() == urls.first()) {
        val host = hostOf(urls.first())
        if (host.isNotBlank()) {
            return EchoExtension(
                "WEBSITE",
                buildJson {
                    put("site", urls.first())
                    put("title", host)
                }
            )
        }
    }
    return null
}

private fun buildJson(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
    kotlinx.serialization.json.buildJsonObject(block)

private data class CardSpec(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val url: String
)

private fun specOf(extension: EchoExtension): CardSpec? {
    val p = extension.payload ?: return null
    return when (extension.type.uppercase(Locale.ROOT)) {
        "MUSIC" -> {
            val url = p.str("url")
            CardSpec(
                Icons.Filled.MusicNote,
                when {
                    url.contains("music.163.com") -> "网易云音乐"
                    url.contains("y.qq.com") -> "QQ 音乐"
                    url.contains("spotify.com") -> "Spotify"
                    url.contains("music.apple.com") -> "Apple Music"
                    else -> "音乐"
                },
                url.substringAfter("://").substringBefore('/'),
                url
            )
        }
        "VIDEO" -> {
            val id = p.str("videoId")
            val bili = id.startsWith("BV", ignoreCase = true)
            CardSpec(
                Icons.Filled.PlayCircle,
                if (bili) "Bilibili 视频" else "YouTube 视频",
                if (bili) id else if (id.isBlank()) "视频" else "youtu.be/$id",
                if (bili) "https://www.bilibili.com/video/$id" else "https://www.youtube.com/watch?v=$id"
            )
        }
        "GITHUBPROJ" -> {
            val url = p.str("repoUrl")
            val repo = url.substringAfter("github.com/").trim('/')
            CardSpec(
                Icons.Filled.Code,
                repo.ifBlank { "GitHub 仓库" },
                "GitHub · 自动识别",
                url
            )
        }
        "WEBSITE" -> {
            val site = p.str("site")
            val title = p.str("title").ifBlank { hostOf(site).ifBlank { site } }
            CardSpec(Icons.Filled.Language, title, hostOf(site).ifBlank { site }, site)
        }
        "LOCATION" -> {
            val lat = p.str("latitude")
            val lng = p.str("longitude")
            val coords = if (lat.isNotBlank() && lng.isNotBlank()) "$lat, $lng" else "位置"
            CardSpec(
                Icons.Filled.Place,
                p.str("placeholder").ifBlank { "位置" },
                coords,
                if (lat.isNotBlank() && lng.isNotBlank()) {
                    "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=16/$lat/$lng"
                } else ""
            )
        }
        "TWEET" -> {
            val url = p.str("url")
            val user = p.str("username")
            CardSpec(
                Icons.Filled.AlternateEmail,
                if (user.isBlank()) "X 推文" else "@$user · X",
                "推文",
                url
            )
        }
        else -> null
    }
}

@Composable
fun EchoExtensionCard(echo: Echo, modifier: Modifier = Modifier) {
    val extension = resolveExtension(echo) ?: return
    val spec = specOf(extension) ?: return
    val uriHandler = LocalUriHandler.current
    Surface(
        onClick = { if (spec.url.isNotBlank()) runCatching { uriHandler.openUri(spec.url) } },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        spec.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    spec.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    spec.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (spec.url.isNotBlank()) {
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = "打开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
