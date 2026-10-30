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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import top.saltfish.ech0.ui.common.AppMessages
import top.saltfish.ech0.ui.common.ContentPadding
import top.saltfish.ech0.ui.common.EchoExtensionCard
import top.saltfish.ech0.ui.common.EmptyStateCard
import top.saltfish.ech0.ui.common.ErrorCard
import top.saltfish.ech0.ui.common.LaunchSession
import top.saltfish.ech0.ui.common.MarkdownText
import top.saltfish.ech0.ui.common.MediaGrid
import top.saltfish.ech0.ui.common.MonthSidePanel
import top.saltfish.ech0.ui.common.RefreshIconButton
import top.saltfish.ech0.ui.common.ScrollMemory
import top.saltfish.ech0.ui.common.TagChip
import top.saltfish.ech0.ui.common.dockContentPadding
import top.saltfish.ech0.ui.common.formatTime
import top.saltfish.ech0.ui.common.monthOf
import top.saltfish.ech0.ui.common.monthsOf
import top.saltfish.ech0.ui.common.recentMonths
import top.saltfish.ech0.ui.common.isCacheStale
import top.saltfish.ech0.data.userMessage
import java.net.URI

private fun hostOf(url: String): String = runCatching { URI(url).host ?: "" }.getOrDefault("")

private data class HubPost(
    val echo: Echo,
    val source: String,
    val sourceUrl: String
)

/** 分页以「来源」为单位：某个友链失败不会拖住其他来源，也不会跳过失败页 */
private data class HubSource(
    val key: String,
    val name: String
)

private const val HubPageSize = 20

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
            cached?.merged?.distinctBy { it.echo.id }?.forEach {
                add(HubPost(it.echo, it.source, it.sourceUrl))
            }
        }
    }
    var lastSavedAt by remember { mutableLongStateOf(cached?.savedAt ?: 0L) }
    var refreshing by remember { mutableStateOf(false) }
    var silentRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var showMonthPanel by remember { mutableStateOf(false) }
    // 每个来源已加载到第几页、是否已到底、上次是否失败；失败页不推进，重试仍从原页开始
    var sourcePages by remember { mutableStateOf<Map<String, Int>>(cached?.sourcePages ?: emptyMap()) }
    var exhaustedSources by remember { mutableStateOf<Set<String>>(cached?.exhaustedSources ?: emptySet()) }

    suspend fun load(reset: Boolean, silent: Boolean = false) {
        if (Ech0Api.base().isBlank()) return
        if (reset && (refreshing || silentRefreshing)) return
        if (!reset && (loadingMore || endReached || refreshing || silentRefreshing)) return
        if (reset) {
            if (silent) silentRefreshing = true else refreshing = true
        } else {
            loadingMore = true
        }
        if (!silent) {
            error = null
            loadMoreError = null
        }
        val localBase = Ech0Api.base().trimEnd('/')
        try {
            // 本站信息只用于展示，拿不到也不影响聚合时间线
            val localSelf = runCatching { Ech0Api.connectSelf() }
                .rethrowCancellationLike().getOrNull()
            val fallbackSelf = self ?: ConnectInfo()
            val rawSelf = localSelf ?: fallbackSelf
            // 服务端 Connect 信息可能还是安装默认值（Ech0 / example.com）：
            // 用当前站点名和账号头像兜底，避免「友链」里自己的来源显示成 Ech0。
            val displaySelf = rawSelf.copy(
                server_name = rawSelf.server_name
                    .takeIf { it.isNotBlank() && !it.equals("Ech0", ignoreCase = true) }
                    ?: top.saltfish.ech0.data.Ech0Store.current.username.ifBlank { "我的 Ech0" },
                server_url = rawSelf.server_url
                    .takeIf { it.isNotBlank() && !it.contains("example.com", ignoreCase = true) }
                    ?: localBase,
                logo = rawSelf.logo
                    ?.takeIf { it.isNotBlank() && !it.contains("example.com", ignoreCase = true) }
                    ?: top.saltfish.ech0.data.Ech0Store.current.avatarUrl.ifBlank { null }
            )
            self = displaySelf

            val friendList = runCatching { Ech0Api.connectsInfo() }
                .rethrowCancellationLike().getOrNull()
            if (friendList != null) {
                friends.clear(); friends.addAll(friendList)
            }
            val friendSnapshot = friendList ?: friends.toList()
            val sources = buildList {
                add(HubSource(localBase, displaySelf.server_name))
                friendSnapshot.forEach { friend ->
                    val key = friend.server_url.trimEnd('/')
                    if (key.isNotBlank()) add(HubSource(key, friend.server_name))
                }
            }.distinctBy { it.key }

            // 下拉刷新从第 1 页重新开始；静默刷新只合并第 1 页，保留已有分页进度
            val nextPages = if (reset && !silent) mutableMapOf<String, Int>() else sourcePages.toMutableMap()
            val nextExhausted =
                if (reset && !silent) mutableSetOf<String>() else exhaustedSources.toMutableSet()
            val failed = mutableSetOf<String>()
            val incoming = mutableListOf<HubPost>()

            sources.forEach { source ->
                if (!reset && nextExhausted.contains(source.key)) return@forEach
                val targetPage = if (reset) 1 else (nextPages[source.key] ?: 0) + 1
                val isLocal = source.key == localBase
                val result = runCatching {
                    if (isLocal) {
                        Ech0Api.queryEchos(
                            top.saltfish.ech0.data.EchoQueryDto(
                                page = targetPage,
                                pageSize = HubPageSize
                            )
                        ).items
                    } else {
                        Ech0Api.remoteEchoPage(source.key, targetPage, HubPageSize).items
                    }
                }.rethrowCancellationLike()
                result.onSuccess { items ->
                    // 静默刷新只合并第 1 页，不倒退已有分页进度；首次无进度时记为第 1 页
                    if (!reset) {
                        nextPages[source.key] = targetPage
                    } else if (!silent || !nextPages.containsKey(source.key)) {
                        nextPages[source.key] = 1
                    }
                    if (items.size >= HubPageSize) nextExhausted.remove(source.key)
                    else nextExhausted.add(source.key)
                    incoming += items.map { echo -> HubPost(echo, source.name, source.key) }
                }.onFailure {
                    // 失败不推进页码、不标记到底，重试时仍请求这一页
                    failed += source.key
                }
            }

            sourcePages = nextPages
            exhaustedSources = nextExhausted

            val allOk = failed.isEmpty()
            // 全部成功后整体替换；部分失败时保留旧内容并合并成功来源，避免刷新把列表清空
            if (reset && !silent && allOk) merged.clear()
            val seen = merged.mapTo(mutableSetOf()) { it.echo.id }
            val fresh = incoming.filter { seen.add(it.echo.id) }
            fresh.forEach { merged.add(it) }
            val sorted = merged.distinctBy { it.echo.id }.sortedByDescending { it.echo.created_at }
            merged.clear(); merged.addAll(sorted)

            val sourceKeys = sources.mapTo(mutableSetOf()) { it.key }
            endReached = allOk && sourceKeys.isNotEmpty() &&
                sourceKeys.all { nextExhausted.contains(it) }

            val failureText = when {
                allOk -> null
                failed.size >= sources.size -> "友链加载失败，请检查网络后重试"
                else -> "有 ${failed.size} 个来源加载失败，已显示其余内容"
            }
            if (!reset) loadMoreError = failureText
            if (reset && !silent) error = failureText

            if (silent && fresh.isNotEmpty()) {
                AppMessages.show("友链有 ${fresh.size} 条新内容")
            }
            if (allOk || incoming.isNotEmpty()) {
                lastSavedAt = System.currentTimeMillis()
                DiskCache.saveHub(
                    context,
                    DiskCache.HubCache(
                        savedAt = lastSavedAt,
                        self = displaySelf,
                        friends = friends.toList(),
                        merged = merged.map {
                            DiskCache.HubEntry(it.echo, it.source, it.sourceUrl)
                        },
                        sourcePages = sourcePages,
                        exhaustedSources = exhaustedSources
                    )
                )
            }
        } catch (e: Exception) {
            if (e.isCancellationLike()) return
            if (reset && !silent) error = e.userMessage()
            if (!reset) loadMoreError = e.userMessage()
        } finally {
            refreshing = false
            silentRefreshing = false
            loadingMore = false
        }
    }

    // DataStore 首帧可能尚未加载完 baseUrl：监听 baseUrl 变化，就绪后触发刷新
    val storeState by top.saltfish.ech0.data.Ech0Store.stateFlow.collectAsState(
        initial = top.saltfish.ech0.data.Ech0Store.current
    )
    val configured = storeState.baseUrl.isNotBlank()
    LaunchedEffect(storeState.baseUrl) {
        if (storeState.baseUrl.isNotBlank()) {
            when {
                coldStart || merged.isEmpty() -> load(true)
                isCacheStale(lastSavedAt) -> load(true, silent = true)
            }
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = ScrollMemory.hubIndex,
        initialFirstVisibleItemScrollOffset = ScrollMemory.hubOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            ScrollMemory.hubIndex = index
            ScrollMemory.hubOffset = offset
        }
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3 &&
                merged.isNotEmpty() && !endReached && !refreshing && !loadingMore &&
                loadMoreError == null
        }
    }
    LaunchedEffect(shouldLoadMore) {
        // 不能在 LaunchedEffect 里直接 load：loadingMore 变化会让 shouldLoadMore 变 false，
        // 取消当前协程导致分页请求半途而废。放独立 scope 里执行。
        if (shouldLoadMore) scope.launch { load(false) }
    }

    val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("友链", style = MaterialTheme.typography.titleLarge) },
            actions = {
                if (configured) {
                    IconButton(onClick = { showMonthPanel = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "按月查看")
                    }
                    RefreshIconButton(refreshing = refreshing) { scope.launch { load(true) } }
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
            onRefresh = { scope.launch { load(true) } },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
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
                    item { ErrorCard(message = it, onRetry = { scope.launch { load(true) } }) }
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
                    val postLogo = if (isLocal) {
                        self?.logo
                    } else {
                        friends.firstOrNull {
                            it.server_url.trimEnd('/') == post.sourceUrl.trimEnd('/')
                        }?.logo
                    }
                    Card(
                        onClick = {
                            if (isLocal) {
                                nav.openDetail(post.echo.id)
                            } else if (post.sourceUrl.isNotBlank()) {
                                nav.openRemoteDetail(post.sourceUrl, post.echo.id)
                            }
                        },
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HubAvatar(
                                    name = post.echo.username.ifBlank { post.source },
                                    logo = postLogo?.let {
                                        Ech0Api.resolveUrlWithBase(post.sourceUrl.ifBlank { base }, it)
                                    },
                                    size = 40.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    Modifier.weight(1f).height(40.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SourceChip(post.source, isLocal)
                                        if (post.echo.username.isNotBlank()) {
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                "@${post.echo.username}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(Modifier.width(2.dp))
                                            Icon(
                                                Icons.Filled.Verified,
                                                contentDescription = "已认证实例",
                                                tint = Color(0xFF1DA1F2),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        formatTime(post.echo.created_at),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Filled.OpenInNew,
                                    contentDescription = "查看详情",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            if (post.echo.content.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                MarkdownText(
                                    markdown = post.echo.content,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                    baseUrl = post.sourceUrl.ifBlank { base },
                                    collapsible = true
                                )
                            }
                            if (post.echo.tags.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    post.echo.tags.take(4).forEach { tag ->
                                        TagChip(tag.name)
                                    }
                                }
                            }
                            if (post.echo.extension != null || post.echo.content.contains("http")) {
                                Spacer(Modifier.height(8.dp))
                                EchoExtensionCard(post.echo)
                            }
                            HubMediaPreview(
                                post.echo,
                                baseUrl = post.sourceUrl.ifBlank { base },
                                onClick = {
                                    if (isLocal) {
                                        nav.openDetail(post.echo.id)
                                    } else if (post.sourceUrl.isNotBlank()) {
                                        nav.openRemoteDetail(post.sourceUrl, post.echo.id)
                                    }
                                }
                            )
                        }
                    }
                }
                if (loadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                    }
                } else if (loadMoreError != null) {
                    item {
                        ErrorCard(
                            message = loadMoreError!!,
                            onRetry = { scope.launch { load(false) } }
                        )
                    }
                }
                if (endReached && merged.isNotEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "— 到底了 · 共 ${merged.size} 条 —",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    val hubHeaderCount = (if (error != null) 1 else 0) + 1 + 1 +
        (if (!refreshing && friends.isEmpty()) 1 else 0) + friends.size + 1
    val hubVisibleMonth = merged.getOrNull(
        (listState.firstVisibleItemIndex - hubHeaderCount).coerceAtLeast(0)
    )?.let { monthOf(it.echo.created_at) }

    if (showMonthPanel) {
        MonthSidePanel(
            months = (monthsOf(merged.map { it.echo.created_at }) + recentMonths())
                .distinct()
                .sortedDescending(),
            selected = hubVisibleMonth,
            onPick = { month ->
                showMonthPanel = false
                scope.launch {
                    var index = merged.indexOfFirst { monthOf(it.echo.created_at) == month }
                    var attempts = 0
                    while (index < 0 && !endReached && loadMoreError == null && attempts < 12) {
                        val before = merged.size
                        load(false)
                        attempts++
                        index = merged.indexOfFirst { monthOf(it.echo.created_at) == month }
                        if (merged.size == before && endReached) break
                    }
                    if (index < 0) {
                        AppMessages.show("这个月份没有内容，或还没有加载到")
                        return@launch
                    }
                    val max = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    val target = (hubHeaderCount + index).coerceIn(0, max)
                    listState.animateScrollToItem(target)
                }
            },
            onDismiss = { showMonthPanel = false }
        )
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
private fun HubMediaPreview(echo: Echo, baseUrl: String, onClick: () -> Unit = {}) {
    if (echo.echo_files.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    MediaGrid(
        files = echo.echo_files,
        layout = echo.layout,
        baseUrl = baseUrl,
        onImageClick = { onClick() }
    )
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
    if (!logo.isNullOrBlank() && !top.saltfish.ech0.data.Ech0Store.current.lowDataMode) {
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
