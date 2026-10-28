@file:OptIn(ExperimentalMaterial3Api::class)

package top.saltfish.ech0.ui.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import top.saltfish.ech0.data.ConnectInfo
import top.saltfish.ech0.data.DiskCache
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.isCancellationLike
import top.saltfish.ech0.data.rethrowCancellationLike
import top.saltfish.ech0.ui.AppNav
import top.saltfish.ech0.ui.common.ContentPadding
import top.saltfish.ech0.ui.common.EmptyStateCard
import top.saltfish.ech0.ui.common.ErrorCard
import top.saltfish.ech0.ui.common.LaunchSession
import top.saltfish.ech0.ui.common.MarkdownText
import top.saltfish.ech0.ui.common.MediaPlaceholder
import top.saltfish.ech0.ui.common.NetImage
import top.saltfish.ech0.ui.common.RefreshIconButton
import top.saltfish.ech0.ui.common.dockContentPadding
import top.saltfish.ech0.ui.common.formatTime
import top.saltfish.ech0.ui.common.isCacheStale
import top.saltfish.ech0.data.userMessage
import java.net.URI

private fun hostOf(url: String): String = runCatching { URI(url).host ?: "" }.getOrDefault("")

private data class HubPost(
    val echo: Echo,
    val source: String,
    val sourceUrl: String
)

/** 友链：本站 + Connect 实例互联聚合时间线 */
@Composable
fun HubScreen(nav: AppNav) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = LocalUriHandler.current

    val cached = remember { DiskCache.loadHub(context) }
    val coldStart = remember { LaunchSession.consumeHub() }
    var self by remember { mutableStateOf<ConnectInfo?>(cached?.self) }
    val friends = remember {
        mutableStateListOf<ConnectInfo>().apply { cached?.friends?.let { addAll(it) } }
    }
    val merged = remember {
        mutableStateListOf<HubPost>().apply {
            cached?.merged?.forEach { add(HubPost(it.echo, it.source, it.sourceUrl)) }
        }
    }
    var lastSavedAt by remember { mutableLongStateOf(cached?.savedAt ?: 0L) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        if (Ech0Api.base().isBlank() || refreshing) return
        refreshing = true
        error = null
        scope.launch {
            try {
                val localSelf = runCatching { Ech0Api.connectSelf() }
                    .rethrowCancellationLike().getOrNull()
                if (localSelf == null) {
                    error = "无法获取本站信息，正在显示缓存"
                    return@launch
                }
                val localList = runCatching {
                    Ech0Api.queryEchos(
                        top.saltfish.ech0.data.EchoQueryDto(page = 1, pageSize = 10)
                    ).items
                }.rethrowCancellationLike().getOrNull()
                if (localList == null) {
                    error = "本站时间线加载失败，正在显示缓存"
                    return@launch
                }
                val friendList = runCatching { Ech0Api.connectsInfo() }
                    .rethrowCancellationLike().getOrNull()

                val remote = mutableListOf<HubPost>()
                (friendList ?: friends.toList()).forEach { friend ->
                    runCatching {
                        Ech0Api.remoteEchoPage(friend.server_url, 1, 10).items.forEach { echo ->
                            remote.add(HubPost(echo, friend.server_name, friend.server_url))
                        }
                    }.rethrowCancellationLike()
                }
                val localBase = Ech0Api.base().trimEnd('/')
                val mergedAll = (
                    localList.map {
                        HubPost(it, localSelf.server_name, localBase)
                    } + remote
                    ).sortedByDescending { it.echo.created_at }

                self = localSelf
                if (friendList != null) {
                    friends.clear(); friends.addAll(friendList)
                }
                merged.clear(); merged.addAll(mergedAll)
                lastSavedAt = System.currentTimeMillis()
                DiskCache.saveHub(
                    context,
                    DiskCache.HubCache(
                        savedAt = lastSavedAt,
                        self = localSelf,
                        friends = friends.toList(),
                        merged = mergedAll.map {
                            DiskCache.HubEntry(it.echo, it.source, it.sourceUrl)
                        }
                    )
                )
            } catch (e: Exception) {
                if (e.isCancellationLike()) return@launch
                error = e.userMessage()
            } finally {
                refreshing = false
            }
        }
    }

    // DataStore 首帧可能尚未加载完 baseUrl：监听 baseUrl 变化，就绪后触发刷新
    val storeState by top.saltfish.ech0.data.Ech0Store.stateFlow.collectAsState(
        initial = top.saltfish.ech0.data.Ech0Store.current
    )
    val configured = storeState.baseUrl.isNotBlank()
    LaunchedEffect(storeState.baseUrl) {
        if (storeState.baseUrl.isNotBlank()) {
            if (coldStart || merged.isEmpty() || isCacheStale(lastSavedAt)) load()
        }
    }

    val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("友链", style = MaterialTheme.typography.titleLarge) },
            actions = {
                if (configured) {
                    RefreshIconButton(refreshing = refreshing) { load() }
                }
            },
            scrollBehavior = topBarScroll,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        if (!configured) {
            Box(
                Modifier.fillMaxSize().padding(ContentPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                EmptyStateCard(
                    icon = Icons.Filled.Link,
                    title = "还没有连接实例",
                    hint = "连接后可在这里看到本站与友链实例的聚合动态",
                    actionText = "填写实例地址",
                    onAction = nav.openSetup
                )
            }
            return
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { load() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topBarScroll.nestedScrollConnection),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = ContentPadding,
                    end = ContentPadding,
                    top = 4.dp,
                    bottom = dockContentPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                error?.let {
                    item { ErrorCard(message = it, onRetry = { load() }) }
                }

                // 本站 hero
                item {
                    val s = self
                    Card(shape = MaterialTheme.shapes.extraLarge) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HubAvatar(
                                name = s?.server_name ?: "E",
                                logo = s?.logo?.let { Ech0Api.resolveUrl(it) },
                                size = 48.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s?.server_name?.takeIf { it.isNotBlank() }
                                        ?: "我的 Ech0",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val host = hostOf(Ech0Api.base())
                                    if (host.isNotBlank()) {
                                        DomainChip(host, accent = true)
                                    }
                                    if (!s?.version.isNullOrBlank() && s?.version != "?") {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Ech0 v${s?.version}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (s != null && s.total_echos > 0) {
                                Text(
                                    "${s.total_echos} 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 友链
                item {
                    Text(
                        "友链实例 · ${friends.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                if (!refreshing && friends.isEmpty()) {
                    item {
                        Card(shape = MaterialTheme.shapes.large) {
                            Text(
                                "还没有友链实例。在 Ech0 Web 端「管理面板 → 扩展」里添加其他 Ech0 站点后，这里会聚合它们的时间线。",
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(14.dp)
                            )
                        }
                    }
                }
                items(friends, key = { it.server_url }) { friend ->
                    Card(
                        onClick = {
                            runCatching { uriHandler.openUri(friend.server_url) }
                        },
                        shape = MaterialTheme.shapes.large
                    ) {
                        ListItem(
                            headlineContent = { Text(friend.server_name) },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DomainChip(hostOf(friend.server_url))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "@${friend.sys_username}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            },
                            leadingContent = {
                                HubAvatar(
                                    friend.server_name,
                                    friend.logo?.let { Ech0Api.resolveUrl(it) },
                                    40.dp
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (friend.total_echos > 0) {
                                        Text(
                                            "${friend.total_echos} 条",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Icon(
                                        Icons.Filled.OpenInNew,
                                        contentDescription = "在浏览器打开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                // 聚合时间线
                item {
                    Text(
                        "聚合时间线",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }
                if (refreshing && merged.isEmpty()) {
                    item {
                        Text(
                            "聚合中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else if (merged.isEmpty() && error == null) {
                    item {
                        Card(shape = MaterialTheme.shapes.large) {
                            Text(
                                "暂无内容",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                items(merged, key = { it.sourceUrl + it.echo.id }) { post ->
                    val base = Ech0Api.base().trimEnd('/')
                    val isLocal = post.sourceUrl.trimEnd('/') == base ||
                        (post.sourceUrl.isBlank() && post.source == self?.server_name)
                    Card(
                        onClick = {
                            if (isLocal) {
                                nav.openDetail(post.echo.id)
                            } else if (post.sourceUrl.isNotBlank()) {
                                runCatching {
                                    uriHandler.openUri(
                                        post.sourceUrl.trimEnd('/') + "/echo/" + post.echo.id
                                    )
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SourceChip(post.source, isLocal)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    formatTime(post.echo.created_at),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!isLocal) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Filled.OpenInNew,
                                        contentDescription = "在浏览器打开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            if (post.echo.content.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                MarkdownText(
                                    markdown = post.echo.content,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)
                                )
                            }
                            HubMediaPreview(post.echo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(source: String, local: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (local) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.widthIn(max = 180.dp)
    ) {
        Text(
            source.ifBlank { "未知来源" },
            style = MaterialTheme.typography.labelSmall,
            color = if (local) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun HubMediaPreview(echo: Echo) {
    val images = echo.echo_files.mapNotNull { it.file }.filter { f ->
        (f.category ?: "image") == "image" ||
            (f.content_type ?: "").startsWith("image/")
    }
    if (images.isEmpty()) {
        echo.echo_files.firstOrNull()?.file?.let { f ->
            val isVideo = f.category == "video" ||
                (f.content_type ?: "").startsWith("video/")
            Spacer(Modifier.height(8.dp))
            MediaPlaceholder(
                icon = if (isVideo) "▶" else "♪",
                label = f.name ?: "附件"
            )
        }
        return
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        images.take(3).forEachIndexed { index, file ->
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                NetImage(
                    url = Ech0Api.resolveUrl(file.url),
                    modifier = Modifier.fillMaxSize()
                )
                if (index == 2 && images.size > 3) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+${images.size - 3}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainChip(host: String, accent: Boolean = false) {
    if (host.isBlank()) return
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (accent) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.widthIn(max = 180.dp)
    ) {
        Text(
            host,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun HubAvatar(name: String, logo: String?, size: androidx.compose.ui.unit.Dp) {
    if (!logo.isNullOrBlank()) {
        AsyncImage(
            model = logo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
        return
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = (size.value * 0.45f).sp
        )
    }
}
