package top.saltfish.ech0.ui.common

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import top.saltfish.ech0.data.BiliVideo
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.EchoExtension
import top.saltfish.ech0.data.GithubRepo
import top.saltfish.ech0.data.MusicLinks
import top.saltfish.ech0.data.MusicTrack
import top.saltfish.ech0.data.TweetEmbed
import java.net.URI
import java.util.Locale

private val urlPattern = Regex("""https?://[^\s<>()\[\]{}，。；、""']+""")

private fun JsonObject.str(key: String): String =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull.orEmpty() }.getOrDefault("")

private fun hostOf(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")

/** 从链接识别扩展卡；编辑器粘贴链接和正文自动识别共用 */
fun detectExtension(url: String): EchoExtension? {
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
                ?.let { EchoExtension("VIDEO", buildJson { put("videoId", id) }) }
        }
        lower.contains("youtube.com/watch") -> {
            val id = url.substringAfter("v=", "").substringBefore('&')
            id.takeIf { it.isNotBlank() }
                ?.let { EchoExtension("VIDEO", buildJson { put("videoId", id) }) }
        }
        lower.contains("youtube.com/shorts/") -> {
            val id = url.substringAfter("/shorts/").substringBefore('?').substringBefore('/')
            id.takeIf { it.isNotBlank() }
                ?.let { EchoExtension("VIDEO", buildJson { put("videoId", id) }) }
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

// ---------- 扩展卡数据缓存（避免列表滚动重复请求） ----------

private val githubCache = java.util.concurrent.ConcurrentHashMap<String, GithubRepo>()
private val githubFailedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
private val biliCache = java.util.concurrent.ConcurrentHashMap<String, BiliVideo>()
private val tweetCache = java.util.concurrent.ConcurrentHashMap<String, TweetEmbed>()

private fun openUri(uriHandler: androidx.compose.ui.platform.UriHandler, url: String) {
    if (url.isNotBlank()) runCatching { uriHandler.openUri(url) }
}

@Composable
fun EchoExtensionCard(echo: Echo, modifier: Modifier = Modifier) {
    val extension = resolveExtension(echo) ?: return
    val payload = extension.payload ?: return
    when (extension.type.uppercase(Locale.ROOT)) {
        "GITHUBPROJ" -> GithubRepoCard(payload.str("repoUrl"), modifier)
        "MUSIC" -> MusicExtensionCard(payload.str("url"), modifier)
        "VIDEO" -> VideoExtensionCard(payload.str("videoId"), modifier)
        "TWEET" -> TweetExtensionCard(
            url = payload.str("url"),
            username = payload.str("username"),
            statusId = payload.str("statusId"),
            modifier = modifier
        )
        "LOCATION" -> LocationExtensionCard(
            name = payload.str("placeholder"),
            lat = payload.str("latitude").toDoubleOrNull(),
            lng = payload.str("longitude").toDoubleOrNull(),
            modifier = modifier
        )
        "WEBSITE" -> WebsiteExtensionCard(
            site = payload.str("site"),
            title = payload.str("title"),
            modifier = modifier
        )
        else -> Unit
    }
}

// ---------- 通用外壳 ----------

@Composable
private fun CardShell(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun OpenAction(url: String) {
    val uriHandler = LocalUriHandler.current
    if (url.isBlank()) return
    TextButton(
        onClick = { openUri(uriHandler, url) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text("打开", style = MaterialTheme.typography.labelSmall)
        Icon(
            Icons.Filled.OpenInNew, contentDescription = null,
            modifier = Modifier.padding(start = 2.dp).size(13.dp)
        )
    }
}

// ---------- 网站 ----------

@Composable
private fun WebsiteExtensionCard(site: String, title: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val host = hostOf(site)
    CardShell("网站", Icons.Filled.Language, modifier) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { openUri(uriHandler, site) }
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        (host.ifBlank { site }).trim().take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title.ifBlank { host.ifBlank { site } },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    host.ifBlank { site },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------- GitHub 仓库 ----------

@Composable
private fun GithubRepoCard(repoUrl: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val segments = repoUrl.trim().trimEnd('/').split('/').filter { it.isNotBlank() }
    val owner = segments.getOrNull(segments.lastIndex - 1).orEmpty()
    val repo = segments.lastOrNull().orEmpty()
    var data by remember(repoUrl) { mutableStateOf(githubCache[repoUrl]) }
    var failed by remember(repoUrl) { mutableStateOf(false) }
    val lowData = Ech0Store.current.lowDataMode

    LaunchedEffect(repoUrl) {
        val recentFailure = githubFailedAt[repoUrl]
        if (recentFailure != null && System.currentTimeMillis() - recentFailure < 5 * 60_000L) {
            failed = true
            return@LaunchedEffect
        }
        if (data == null && owner.isNotBlank() && repo.isNotBlank()) {
            val fetched = Ech0Api.githubRepo(owner, repo)
            if (fetched != null) {
                githubCache[repoUrl] = fetched
                githubFailedAt.remove(repoUrl)
                data = fetched
            } else {
                githubFailedAt[repoUrl] = System.currentTimeMillis()
                failed = true
            }
        }
    }

    CardShell(
        "GitHub 仓库",
        Icons.Filled.Code,
        modifier,
        action = { OpenAction(repoUrl) }
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { openUri(uriHandler, repoUrl) }
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatar = data?.owner?.avatar_url.orEmpty()
            if (!lowData && avatar.isNotBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Code, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    data?.name?.ifBlank { repo } ?: repo,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    data?.description?.takeIf { it.isNotBlank() } ?: "$owner/$repo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                when {
                    data != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star, contentDescription = "Star",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            " ${data?.stargazers_count ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Filled.CallSplit, contentDescription = "Fork",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            " ${data?.forks_count ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    failed -> Text(
                        "仓库信息加载失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Text(
                        "加载仓库信息…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------- 音乐 ----------

@Composable
private fun MusicExtensionCard(rawUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val lowData = Ech0Store.current.lowDataMode
    val link = remember(rawUrl) { MusicLinks.parse(rawUrl) }
    var track by remember(rawUrl) { mutableStateOf<MusicTrack?>(null) }
    var loading by remember(rawUrl) { mutableStateOf(true) }
    var failed by remember(rawUrl) { mutableStateOf(false) }
    var player by remember(rawUrl) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(rawUrl) { mutableStateOf(false) }
    var position by remember(rawUrl) { mutableLongStateOf(0L) }
    var duration by remember(rawUrl) { mutableLongStateOf(0L) }

    val platform = when (link?.server) {
        "netease" -> "网易云音乐"
        "tencent" -> "QQ 音乐"
        "apple" -> "Apple Music"
        else -> "音乐"
    }

    fun startOrToggle() {
        val existing = player
        if (existing != null) {
            runCatching {
                if (existing.isPlaying) {
                    existing.pause()
                    playing = false
                } else {
                    existing.start()
                    playing = true
                }
            }
            return
        }
        val t = track
        if (t == null) {
            if (link?.server == "apple" || failed) openUri(uriHandler, rawUrl)
            return
        }
        val mp = MediaPlayer()
        val ready = runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(t.url)
            mp.setOnPreparedListener {
                duration = it.duration.toLong().coerceAtLeast(0L)
                it.start()
                playing = true
            }
            mp.setOnCompletionListener {
                playing = false
                position = 0L
                it.seekTo(0)
            }
            mp.setOnErrorListener { _, _, _ ->
                playing = false
                failed = true
                true
            }
            mp.prepareAsync()
        }.isSuccess
        if (!ready) {
            runCatching { mp.release() }
            failed = true
            return
        }
        player = mp
    }

    LaunchedEffect(rawUrl) {
        if (link == null || link.server == "apple") {
            loading = false
            failed = true
            return@LaunchedEffect
        }
        loading = true
        val fetched = Ech0Api.musicTrack(rawUrl)
        loading = false
        if (fetched == null) failed = true else track = fetched
    }

    LaunchedEffect(playing) {
        while (playing) {
            position = player?.currentPosition?.toLong() ?: 0L
            delay(500)
        }
    }

    DisposableEffect(rawUrl) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }

    CardShell("音乐 · $platform", Icons.Filled.MusicNote, modifier, action = { OpenAction(rawUrl) }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val cover = track?.cover.orEmpty()
                if (!lowData && cover.isNotBlank()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track?.name?.takeIf { it.isNotBlank() } ?: if (loading) "正在获取音乐…" else platform,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track?.artist?.takeIf { it.isNotBlank() }
                        ?: if (failed) "无法播放，点右侧打开" else platform,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track != null && duration > 0L) {
                    LinearProgressIndicator(
                        progress = {
                            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp)
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatMillis(position),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatMillis(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = {
                    if (track == null && !loading) {
                        // 重新获取一次，失败则回退到浏览器
                        scope.launch {
                            loading = true
                            val fetched = Ech0Api.musicTrack(rawUrl)
                            loading = false
                            if (fetched != null) {
                                track = fetched
                                failed = false
                                startOrToggle()
                            } else {
                                failed = true
                                openUri(uriHandler, rawUrl)
                            }
                        }
                    } else {
                        startOrToggle()
                    }
                },
                enabled = !loading || track != null
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause
                    else if (failed && track == null) Icons.Filled.PlayCircle
                    else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放"
                )
            }
        }
    }
}

private fun formatMillis(value: Long): String {
    if (value <= 0L) return "00:00"
    val total = value / 1000
    return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}

// ---------- 视频 ----------

@Composable
private fun VideoExtensionCard(videoId: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val lowData = Ech0Store.current.lowDataMode
    val isBili = videoId.startsWith("BV", ignoreCase = true)
    val watchUrl = if (isBili) {
        "https://www.bilibili.com/video/$videoId"
    } else {
        "https://www.youtube.com/watch?v=$videoId"
    }
    val embedUrl = if (isBili) {
        "https://www.bilibili.com/blackboard/html5mobileplayer.html?bvid=$videoId&as_wide=1&high_quality=1&danmaku=0"
    } else {
        "https://www.youtube.com/embed/$videoId?autoplay=1"
    }
    var expanded by remember(videoId) { mutableStateOf(false) }
    var biliInfo by remember(videoId) { mutableStateOf(biliCache[videoId]) }

    LaunchedEffect(videoId) {
        if (isBili && biliInfo == null && videoId.isNotBlank()) {
            val fetched = Ech0Api.bilibiliVideo(videoId)
            if (fetched != null) {
                biliCache[videoId] = fetched
                biliInfo = fetched
            }
        }
    }

    CardShell(
        if (isBili) "Bilibili 视频" else "YouTube 视频",
        Icons.Filled.PlayCircle,
        modifier,
        action = {
            if (expanded) {
                TextButton(
                    onClick = { expanded = false },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("收起", style = MaterialTheme.typography.labelSmall) }
            } else {
                OpenAction(watchUrl)
            }
        }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (expanded) {
                InlineWebView(embedUrl, Modifier.fillMaxSize())
            } else {
                val thumb = if (isBili) {
                    biliInfo?.cover.orEmpty().replaceFirst("http://", "https://")
                } else {
                    "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                }
                if (!lowData && thumb.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumb)
                            .apply {
                                if (isBili) {
                                    addHeader("Referer", "https://www.bilibili.com/")
                                }
                            }
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { expanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "播放",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }
        val infoText = when {
            isBili -> biliInfo?.title.orEmpty().ifBlank { "Bilibili · $videoId" }
            else -> "YouTube · youtu.be/$videoId"
        }
        Text(
            infoText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
        )
    }
}

// ---------- X 推文 ----------

@Composable
private fun TweetExtensionCard(
    url: String,
    username: String,
    statusId: String,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val fallbackUrl = url.ifBlank {
        if (username.isNotBlank() && statusId.isNotBlank()) {
            "https://x.com/$username/status/$statusId"
        } else ""
    }
    var embed by remember(fallbackUrl) { mutableStateOf(tweetCache[fallbackUrl]) }

    LaunchedEffect(fallbackUrl) {
        if (embed == null && fallbackUrl.isNotBlank()) {
            val fetched = Ech0Api.tweetEmbed(fallbackUrl)
            if (fetched != null) {
                tweetCache[fallbackUrl] = fetched
                embed = fetched
            }
        }
    }

    CardShell("X 推文", Icons.Filled.AlternateEmail, modifier, action = { OpenAction(fallbackUrl) }) {
        Column(
            Modifier.fillMaxWidth()
                .clickable { openUri(uriHandler, fallbackUrl) }
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
        ) {
            Text(
                embed?.authorName?.takeIf { it.isNotBlank() }
                    ?.let { "@$it" }
                    ?: username.takeIf { it.isNotBlank() }?.let { "@$it" }
                    ?: "X",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(3.dp))
            Text(
                embed?.text?.takeIf { it.isNotBlank() } ?: "在 X 查看这条推文",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------- 位置 ----------

@Composable
private fun LocationExtensionCard(
    name: String,
    lat: Double?,
    lng: Double?,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val lowData = Ech0Store.current.lowDataMode
    var expanded by remember { mutableStateOf(false) }
    val hasCoords = lat != null && lng != null && lat != 0.0 && lng != 0.0
    val mapUrl = if (hasCoords) {
        "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=16/$lat/$lng"
    } else ""
    val coordsText = if (hasCoords) {
        String.format(Locale.US, "%.2f°, %.2f°", lat, lng)
    } else "未设置坐标"

    CardShell("位置", Icons.Filled.Place, modifier, action = { OpenAction(mapUrl) }) {
        Column(
            Modifier.fillMaxWidth()
                .clickable { if (hasCoords && !lowData) expanded = !expanded else openUri(uriHandler, mapUrl) }
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        name.ifBlank { "位置" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        coordsText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasCoords && !lowData) {
                    Text(
                        if (expanded) "收起" else "地图",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (expanded && hasCoords) {
                Spacer(Modifier.height(8.dp))
                val bbox = String.format(
                    Locale.US,
                    "%f,%f,%f,%f",
                    lng - 0.01, lat - 0.01, lng + 0.01, lat + 0.01
                )
                InlineWebView(
                    "https://www.openstreetmap.org/export/embed.html?bbox=$bbox&layer=mapnik&marker=$lat,$lng",
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

// ---------- WebView（视频 iframe / 地图预览，按需创建） ----------

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InlineWebView(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val webView = remember(url) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            loadUrl(url)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}
