@file:OptIn(ExperimentalMaterial3Api::class)

package top.saltfish.ech0.ui.hub

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import top.saltfish.ech0.data.ConnectInfo
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.ui.AppNav
import top.saltfish.ech0.ui.common.ContentPadding
import top.saltfish.ech0.ui.common.EmptyStateCard
import top.saltfish.ech0.ui.common.ErrorCard
import top.saltfish.ech0.ui.common.formatTime
import java.net.URI

private fun hostOf(url: String): String = runCatching { URI(url).host ?: "" }.getOrDefault("")

/** 友链：本站 + Connect 实例互联聚合时间线 */
@Composable
fun HubScreen(nav: AppNav) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 首帧恢复缓存：remember 初始化在第一次组合时同步执行，页面第一帧就是缓存内容
    val cached = remember { top.saltfish.ech0.data.DiskCache.loadHub(context) }
    var self by remember { mutableStateOf<ConnectInfo?>(cached?.self) }
    val friends = remember { mutableStateListOf<ConnectInfo>().apply { cached?.friends?.let { addAll(it) } } }
    val merged = remember { mutableStateListOf<Pair<Echo, String>>().apply {
        cached?.merged?.forEach { add(it.echo to it.source) }
    } }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        if (Ech0Api.base().isBlank()) return
        scope.launch {
            loading = true
            error = null
            var firstError: String? = null
            // 先全部取回，再统一 swap（刷新时不闪空）
            val localSelf = runCatching { Ech0Api.connectSelf() }.getOrNull()
            if (localSelf == null && firstError == null) firstError = "无法获取本站信息"
            val localList = runCatching {
                Ech0Api.queryEchos(top.saltfish.ech0.data.EchoQueryDto(page = 1, pageSize = 10)).items
            }.getOrDefault(emptyList())
            val friendList = runCatching { Ech0Api.connectsInfo() }.getOrDefault(emptyList())
            val remote = mutableListOf<Pair<Echo, String>>()
            friendList.forEach { friend ->
                runCatching {
                    Ech0Api.remoteEchoPage(friend.server_url, 1, 10).items.forEach { echo ->
                        remote.add(echo to friend.server_name)
                    }
                }
            }
            val mergedAll = (localList.map { it to (localSelf?.server_name ?: "本站") } + remote)
                .sortedByDescending { it.first.created_at }

            self = localSelf
            friends.clear(); friends.addAll(friendList)
            merged.clear(); merged.addAll(mergedAll)

            if (firstError == null) {
                top.saltfish.ech0.data.DiskCache.saveHub(
                    context,
                    top.saltfish.ech0.data.DiskCache.HubCache(
                        savedAt = System.currentTimeMillis(),
                        self = localSelf,
                        friends = friendList,
                        merged = mergedAll.map { (echo, source) ->
                            top.saltfish.ech0.data.DiskCache.HubEntry(echo, source)
                        }
                    )
                )
            }
            error = firstError
            loading = false
        }
    }

    // DataStore 首帧可能尚未加载完 baseUrl：监听 baseUrl 变化，就绪后触发刷新
    val storeState by top.saltfish.ech0.data.Ech0Store.stateFlow.collectAsState(
        initial = top.saltfish.ech0.data.Ech0Store.current
    )
    val configured = storeState.baseUrl.isNotBlank()
    LaunchedEffect(storeState.baseUrl) {
        if (storeState.baseUrl.isNotBlank()) load()
    }

    val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("友链", style = MaterialTheme.typography.titleLarge) },
            actions = {
                if (configured) {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            },
            scrollBehavior = topBarScroll,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        if (!configured) {
            Box(Modifier.fillMaxSize().padding(ContentPadding), contentAlignment = Alignment.TopCenter) {
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

        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(topBarScroll.nestedScrollConnection),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ContentPadding, end = ContentPadding, top = 4.dp, bottom = 200.dp
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
                                    ?: Ech0Store.current.serverName.ifBlank { "我的 Ech0" },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val host = hostOf(Ech0Api.base())
                                if (host.isNotBlank()) {
                                    DomainChip(host)
                                    Spacer(Modifier.width(8.dp))
                                }
                                if (s != null && s.total_echos >= 0 && s.total_echos > 0) {
                                    Text(
                                        "${s.total_echos} 条",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // 版本仅已知时显示
                            if (!s?.version.isNullOrBlank() && s?.version != "?") {
                                Text(
                                    "Ech0 v${s?.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
            if (!loading && friends.isEmpty()) {
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
                Box(Modifier.animateItem()) {
                Card(shape = MaterialTheme.shapes.large) {
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
                            HubAvatar(friend.server_name, friend.logo?.let { Ech0Api.resolveUrl(it) }, 40.dp)
                        },
                        trailingContent = if (friend.total_echos > 0) {
                            {
                                Text(
                                    "${friend.total_echos} 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else null,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
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
            if (loading) {
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
            items(merged, key = { it.second + it.first.id }) { (echo, source) ->
                val isLocal = source == (self?.server_name ?: "")
                Box(Modifier.animateItem()) {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DomainChip(source, accent = isLocal)
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatTime(echo.created_at),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            echo.content.ifBlank { "（图片说说）" }.take(160),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                            maxLines = 4
                        )
                    }
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
        else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            host,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
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
