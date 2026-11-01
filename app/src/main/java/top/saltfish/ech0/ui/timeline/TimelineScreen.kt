@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package top.saltfish.ech0.ui.timeline

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import top.saltfish.ech0.R
import top.saltfish.ech0.data.ApiException
import top.saltfish.ech0.data.DiskCache
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.data.LikeController
import top.saltfish.ech0.data.Tag
import top.saltfish.ech0.data.isCancellationLike
import top.saltfish.ech0.data.repo.ContentBus
import top.saltfish.ech0.data.repo.PostStore
import top.saltfish.ech0.ui.AppNav
import top.saltfish.ech0.ui.Branding
import top.saltfish.ech0.ui.common.AppMessages
import top.saltfish.ech0.ui.common.ImageViewerDialog
import top.saltfish.ech0.ui.common.UserAvatar
import top.saltfish.ech0.ui.common.LaunchSession
import top.saltfish.ech0.ui.common.imageUrlsOf
import top.saltfish.ech0.ui.common.ContentPadding
import top.saltfish.ech0.ui.common.EmptyStateCard
import top.saltfish.ech0.ui.common.LocalReduceMotion
import top.saltfish.ech0.ui.common.EchoExtensionCard
import top.saltfish.ech0.ui.common.ErrorCard
import top.saltfish.ech0.ui.common.MarkdownText
import top.saltfish.ech0.ui.common.MediaGrid
import top.saltfish.ech0.ui.common.MonthSidePanel
import top.saltfish.ech0.ui.common.monthOf
import top.saltfish.ech0.ui.common.monthsOf
import top.saltfish.ech0.ui.common.RefreshIconButton
import top.saltfish.ech0.ui.common.ScrollMemory
import top.saltfish.ech0.ui.common.TagChip
import top.saltfish.ech0.ui.common.dockContentPadding
import top.saltfish.ech0.ui.common.formatTime
import top.saltfish.ech0.ui.common.isCacheStale
import top.saltfish.ech0.data.userMessage
import java.net.URI

/** 发现页标签 → 时间线筛选的一次性通道 */
object TimelineTagBus {
    var pending: Tag? = null
}

@Composable
fun TimelineScreen(nav: AppNav) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 响应式读取（DataStore 首帧未就绪时也能自动刷新）
    val state by top.saltfish.ech0.data.Ech0Store.stateFlow.collectAsState(
        initial = top.saltfish.ech0.data.Ech0Store.current
    )

    val coldStart = remember { LaunchSession.consumeTimeline() }
    val items = remember { mutableStateListOf<Echo>() }
    var lastSavedAt by remember { mutableStateOf(0L) }
    var page by remember { mutableIntStateOf(1) }
    var refreshing by remember { mutableStateOf(false) }
    var silentRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf<Tag?>(null) }
    var showMonthPanel by remember { mutableStateOf(false) }
    // 初始化成当前 baseUrl：首次进入不误判为“切换实例”，仍可先展示磁盘缓存
    var loadedBase by remember { mutableStateOf(state.baseUrl) }

    suspend fun load(reset: Boolean, silent: Boolean = false, forceNetwork: Boolean = false) {
        val requestBase = state.baseUrl
        if (reset) {
            if (refreshing || silentRefreshing) return
            if (silent) silentRefreshing = true else refreshing = true
        } else {
            if (loadingMore || endReached || refreshing || silentRefreshing) return
            loadingMore = true
        }
        if (!silent) error = null
        try {
            val query = top.saltfish.ech0.data.EchoQueryDto(
                page = if (reset) 1 else page,
                pageSize = 20,
                search = search.ifBlank { null },
                tagIds = tagFilter?.let { listOf(it.id) }
            )
            // 手动刷新/搜索强制走网络；初次/分页/静默刷新允许复用 PostStore
            val result = PostStore.query(query, force = forceNetwork)
            // 请求期间切换了实例：丢弃旧实例结果
            if (requestBase != state.baseUrl) return
            if (reset) {
                val previousIds = items.mapTo(mutableSetOf()) { it.id }
                if (silent) {
                    // 后台静默刷新：更新已有条目，并把新记录插入顶部，不显示转圈
                    val freshPage = result.items.distinctBy { it.id }
                    val freshById = freshPage.associateBy { it.id }
                    for (index in items.indices) {
                        freshById[items[index].id]?.let { items[index] = it }
                    }
                    val fresh = freshPage.filter { it.id !in previousIds }
                    if (fresh.isNotEmpty()) {
                        items.addAll(0, fresh)
                        AppMessages.show("有 ${fresh.size} 条新内容")
                    }
                    lastSavedAt = System.currentTimeMillis()
                    if (search.isBlank() && tagFilter == null) {
                        DiskCache.saveTimeline(context, items.toList())
                    }
                } else {
                    items.clear()
                    items.addAll(result.items)
                    page = 2
                    lastSavedAt = System.currentTimeMillis()
                    // 默认信息流成功加载后写缓存（搜索/标签结果不覆盖主缓存）
                    if (search.isBlank() && tagFilter == null) {
                        DiskCache.saveTimeline(context, items.toList())
                    }
                }
            } else {
                val existing = items.mapTo(mutableSetOf()) { it.id }
                items.addAll(result.items.filter { existing.add(it.id) })
                page += 1
            }
            // 防止分页边界重叠 / 历史缓存造成同一条内容出现两次
            val deduped = items.distinctBy { it.id }
            if (deduped.size != items.size) {
                items.clear()
                items.addAll(deduped)
            }
            endReached = items.size >= result.total || result.items.isEmpty()
        } catch (e: Exception) {
            if (e.isCancellationLike()) return
            if (!silent) {
                val msg = e.userMessage()
                error = if (items.isEmpty()) msg else "刷新失败：$msg"
            }
        } finally {
            refreshing = false
            silentRefreshing = false
            loadingMore = false
        }
    }

    LaunchedEffect(Unit) {
        TimelineTagBus.pending?.let { tag ->
            TimelineTagBus.pending = null
            tagFilter = tag
            load(true)
        }
    }

    LaunchedEffect(state.baseUrl) {
        val base = state.baseUrl
        if (base.isBlank()) return@LaunchedEffect
        val baseChanged = loadedBase != base
        if (baseChanged) {
            // 切换实例：清空旧列表和筛选条件，重新加载
            loadedBase = base
            items.clear()
            page = 1
            lastSavedAt = 0L
            endReached = false
            error = null
            search = ""
            searchActive = false
            tagFilter = null
            refreshing = false
            silentRefreshing = false
            loadingMore = false
        }

        // 缓存读写都放到 IO 线程；页面在 DataStore 首帧加载完成后也能恢复首屏
        if (items.isEmpty()) {
            val cached = withContext(Dispatchers.IO) { DiskCache.loadTimelineCache(context) }
            if (cached != null && items.isEmpty() && state.baseUrl == base) {
                items.addAll(cached.items.distinctBy { it.id })
                lastSavedAt = cached.savedAt
            }
        }

        if (baseChanged) {
            load(true)
        } else {
            when {
                // 启动后第一次刷新照常显示进度；之后再过期刷新走静默
                coldStart || items.isEmpty() -> load(true)
                isCacheStale(lastSavedAt) -> load(true, silent = true)
            }
        }
    }

    // 发布 / 编辑 / 删除后立即刷新，不再等缓存 TTL
    val feedTick = ContentBus.feedRevision
    LaunchedEffect(feedTick) {
        if (feedTick > 0 && state.baseUrl.isNotBlank()) load(true, forceNetwork = true)
    }

    // 首页自动刷新：按设置的信息流缓存间隔静默拉取，月份面板随 items 同步更新
    LaunchedEffect(state.baseUrl, state.feedCacheTtlMinutes, search, tagFilter) {
        val interval = state.feedCacheTtlMinutes.coerceIn(1, 60) * 60_000L
        while (true) {
            delay(interval)
            if (state.baseUrl.isNotBlank()) {
                load(true, silent = true, forceNetwork = true)
            }
        }
    }

    // 从后台回到前台：已有内容且缓存过期时静默刷新，避免首页长期停留在旧内容
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.baseUrl.isNotBlank() && items.isNotEmpty() && lastSavedAt > 0 &&
            isCacheStale(lastSavedAt)
        ) {
            scope.launch { load(true, silent = true, forceNetwork = true) }
        }
    }

    // edge-to-edge：Hero Header 跟随列表滚动，只有滚出后才出现紧凑浮动顶栏
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listHeaderCount = 1 +
        (if (searchActive) 1 else 0) +
        (if (tagFilter != null) 1 else 0) +
        (if (error != null) 1 else 0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = ScrollMemory.timelineIndex,
        initialFirstVisibleItemScrollOffset = ScrollMemory.timelineOffset
    )
    fun saveTimelinePosition() {
        val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            (info.key as? String)?.let { key -> items.any { it.id == key } } == true
        }
        ScrollMemory.timelineKey = anchor?.key as? String
        ScrollMemory.timelineIndex = anchor?.index ?: listState.firstVisibleItemIndex
        ScrollMemory.timelineOffset = anchor?.let { (-it.offset).coerceAtLeast(0) }
            ?: listState.firstVisibleItemScrollOffset
    }
    var timelineScrollRestored by remember { mutableStateOf(false) }
    LaunchedEffect(items.size) {
        if (!timelineScrollRestored && items.isNotEmpty()) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            val keyIndex = ScrollMemory.timelineKey?.let { key ->
                items.indexOfFirst { it.id == key }
            } ?: -1
            val target = if (keyIndex >= 0) {
                listHeaderCount + keyIndex
            } else {
                ScrollMemory.timelineIndex
            }.coerceIn(0, (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            if (target > 0 || ScrollMemory.timelineOffset > 0) {
                listState.scrollToItem(target, ScrollMemory.timelineOffset)
            }
            timelineScrollRestored = true
        }
    }
    LaunchedEffect(listState, items.isNotEmpty()) {
        if (items.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            ScrollMemory.timelineIndex = index
            ScrollMemory.timelineOffset = offset
        }
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val threshold = if (top.saltfish.ech0.data.net.NetworkPolicy.canPreload()) 4 else 1
            last >= listState.layoutInfo.totalItemsCount - threshold &&
                !endReached && !refreshing && !loadingMore && items.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) {
        // loadingMore 会改变 shouldLoadMore，直接调用会被取消；放到独立 scope 执行
        if (shouldLoadMore) scope.launch { load(false) }
    }

    Box(Modifier.fillMaxSize()) {
        // 全出血列表（内容从状态栏下开始，滚动时钻到透明顶栏之下）
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { load(true, forceNetwork = true) } },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ContentPadding,
                    end = ContentPadding,
                    top = statusTop + 4.dp,
                    bottom = dockContentPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hero") {
                    TimelineHeroHeader(
                        serverName = state.serverName.ifBlank { "Ech0" },
                        countLabel = if (items.isEmpty()) "时间线" else "${items.size} 条说说",
                        logoUrl = Branding.logoUrl,
                        lowData = state.lowDataMode,
                        searchActive = searchActive,
                        refreshing = refreshing,
                        onSearch = { searchActive = true },
                        onMonth = { showMonthPanel = true },
                        onRefresh = { scope.launch { load(true, forceNetwork = true) } }
                    )
                }

                // 搜索行（激活时展开）
                if (searchActive) {
                    item(key = "search_row") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                placeholder = { Text("搜索说说…") },
                                singleLine = true,
                                shape = RoundedCornerShape(28.dp),
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { scope.launch { load(true, forceNetwork = true) } }),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                searchActive = false
                                search = ""
                                scope.launch { load(true, forceNetwork = true) }
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "关闭搜索")
                            }
                        }
                    }
                }

                // 标签筛选回显：点标签后从这里清除
                tagFilter?.let { selected ->
                    item(key = "tag_filter") {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InputChip(
                                selected = true,
                                onClick = {
                                    tagFilter = null
                                    scope.launch { load(true, forceNetwork = true) }
                                },
                                label = { Text("#${selected.name}") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close, contentDescription = "清除标签",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "按标签筛选",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 错误只留一处：可重试、可去设置
                error?.let { err ->
                    item(key = "error") {
                        Box(Modifier.animateItem()) {
                            ErrorCard(
                                message = err,
                                onRetry = { scope.launch { load(true, forceNetwork = true) } },
                                onSettings = nav.openSettings
                            )
                        }
                    }
                }

                if (endReached && items.isEmpty() && error == null && !refreshing) {
                    item(key = "empty") {
                        if (state.baseUrl.isBlank()) {
                            EmptyStateCard(
                                icon = Icons.Filled.Public,
                                title = "还没有连接实例",
                                hint = "连接你的 Ech0 站点后，说说会出现在这里",
                                actionText = "填写实例地址",
                                onAction = nav.openSetup
                            )
                        } else {
                            EmptyStateCard(
                                icon = Icons.Filled.Create,
                                title = "还没有说说",
                                hint = if (state.isAdmin) "写下第一条吧" else "站长还没有发布过内容",
                                actionText = if (state.isAdmin) "发一条" else null,
                                onAction = if (state.isAdmin) nav.openComposer else null
                            )
                        }
                    }
                }

                itemsIndexed(items, key = { _, e -> e.id }) { _, echo ->
                    // item 增删/移动时淡入 + 弹簧位移（M3 列表动画）
                    Box(Modifier.animateItem()) {
                        EchoCard(
                            echo = echo,
                            liked = echo.id in state.likedIds,
                            onClick = {
                                saveTimelinePosition()
                                nav.openDetail(echo.id)
                            },
                            onTagClick = { tag ->
                                tagFilter = tag
                                scope.launch { load(true, forceNetwork = true) }
                            },
                            onReload = { scope.launch { load(true, forceNetwork = true) } }
                        )
                    }
                }

                if (loadingMore || (refreshing && items.isEmpty())) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(36.dp))
                        }
                    }
                }
                if (endReached && items.isNotEmpty()) {
                    item(key = "end") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "— 到底了 · 共 ${items.size} 条 —",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Hero Header 滚出后再出现紧凑浮动顶栏；顶部本身不铺实底，保持 edge-to-edge
        val compactTopBar by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 }
        }
        val reduceMotion = LocalReduceMotion.current
        AnimatedVisibility(
            visible = compactTopBar,
            enter = if (reduceMotion) fadeIn(tween(0)) else fadeIn(tween(180)),
            exit = fadeOut(tween(if (reduceMotion) 0 else 120)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(Modifier.fillMaxWidth()) {
                // 顶部渐变模糊：只模糊 scrim 自身，内容仍然透出
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusTop + 96.dp)
                        .blur(36.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .background(
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                0.55f to MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                                1f to Color.Transparent
                            )
                        )
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = ContentPadding,
                            end = 8.dp,
                            top = statusTop + 2.dp,
                            bottom = 4.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                        tonalElevation = 2.dp,
                        modifier = Modifier.widthIn(max = 240.dp)
                    ) {
                        Row(
                            Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimelineLogo(
                                size = 24.dp,
                                logoUrl = Branding.logoUrl,
                                lowData = state.lowDataMode
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                state.serverName.ifBlank { "Ech0" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TimelineActions(
                        searchActive = searchActive,
                        refreshing = refreshing,
                        onSearch = { searchActive = true },
                        onMonth = { showMonthPanel = true },
                        onRefresh = { scope.launch { load(true, forceNetwork = true) } }
                    )
                }
            }
        }
    }

    val visibleMonth = items.getOrNull(
        (listState.firstVisibleItemIndex - listHeaderCount).coerceAtLeast(0)
    )?.let { monthOf(it.created_at) }

    if (showMonthPanel) {
        MonthSidePanel(
            months = monthsOf(items.map { it.created_at }),
            selected = visibleMonth,
            onPick = { month ->
                showMonthPanel = false
                scope.launch {
                    var index = items.indexOfFirst { monthOf(it.created_at) == month }
                    var attempts = 0
                    // 月份还没加载到：按页向后加载，最多 12 页，避免无限请求
                    while (index < 0 && !endReached && error == null && attempts < 12) {
                        val before = items.size
                        load(false)
                        attempts++
                        index = items.indexOfFirst { monthOf(it.created_at) == month }
                        if (items.size == before && endReached) break
                    }
                    if (index < 0) {
                        AppMessages.show("这个月份没有内容，或还没有加载到")
                        return@launch
                    }
                    val max = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    val target = (listHeaderCount + index).coerceIn(0, max)
                    listState.animateScrollToItem(target)
                }
            },
            onLoadMore = {
                scope.launch { load(false) }
            },
            loadingMore = loadingMore,
            endReached = endReached,
            onDismiss = { showMonthPanel = false }
        )
    }
}

@Composable
private fun TimelineHeroHeader(
    serverName: String,
    countLabel: String,
    logoUrl: String?,
    lowData: Boolean,
    searchActive: Boolean,
    refreshing: Boolean,
    onSearch: () -> Unit,
    onMonth: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineLogo(size = 44.dp, logoUrl = logoUrl, lowData = lowData)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                serverName,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                countLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        TimelineActions(
            searchActive = searchActive,
            refreshing = refreshing,
            onSearch = onSearch,
            onMonth = onMonth,
            onRefresh = onRefresh
        )
    }
}

@Composable
private fun TimelineLogo(
    size: androidx.compose.ui.unit.Dp,
    logoUrl: String?,
    lowData: Boolean
) {
    Box(Modifier.size(size)) {
        Image(
            painter = painterResource(R.drawable.ic_tree_logo),
            contentDescription = "站点图标",
            modifier = Modifier.matchParentSize()
        )
        if (logoUrl != null && !lowData) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape)
            )
        }
    }
}

@Composable
private fun TimelineActions(
    searchActive: Boolean,
    refreshing: Boolean,
    onSearch: () -> Unit,
    onMonth: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!searchActive) {
                IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "搜索",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(onClick = onMonth, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = "按月查看",
                    modifier = Modifier.size(20.dp)
                )
            }
            RefreshIconButton(
                refreshing = refreshing,
                modifier = Modifier.size(40.dp),
                onClick = onRefresh
            )
        }
    }
}

private fun hostOf(url: String): String = runCatching {
    URI(url).host ?: ""
}.getOrDefault("")

@Composable
fun EchoCard(
    echo: Echo,
    liked: Boolean,
    onClick: () -> Unit,
    onTagClick: (Tag) -> Unit,
    onReload: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var likeAnim by remember { mutableStateOf(false) }
    var likeBusy by remember(echo.id) { mutableStateOf(false) }
    // 计数与详情页共用 LikeController 的会话级状态
    LaunchedEffect(echo.id, echo.fav_count) { LikeController.sync(echo.id, echo.fav_count) }
    val likeCount = LikeController.count(echo.id, echo.fav_count)
    val reduceMotion = LocalReduceMotion.current
    val likeScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (likeAnim && !reduceMotion) 1.35f else 1f,
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "like"
    )
    var showDelete by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<Int?>(null) }

    Card(shape = MaterialTheme.shapes.largeIncreased) {
        Column(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = { showDelete = true })
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(echo.username, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        echo.username.ifBlank { "匿名" },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatTime(echo.created_at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (echo.private) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Lock, contentDescription = "私密",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                ExtensionBadge(echo)
            }
            Spacer(Modifier.height(10.dp))

            if (echo.content.isNotBlank()) {
                MarkdownText(
                    markdown = echo.content,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    collapsible = true
                )
                Spacer(Modifier.height(10.dp))
            }

            if (echo.echo_files.isNotEmpty()) {
                MediaGrid(
                    files = echo.echo_files,
                    layout = echo.layout,
                    onImageClick = { previewImage = it }
                )
                Spacer(Modifier.height(10.dp))
            }

            if (echo.extension != null || echo.content.contains("http")) {
                EchoExtensionCard(echo)
                Spacer(Modifier.height(10.dp))
            }

            if (echo.tags.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    echo.tags.take(4).forEach { t -> TagChip(t.name) { onTagClick(t) } }
                }
                Spacer(Modifier.height(6.dp))
            }

            // 操作行：点赞 = Expressive 形变开关
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconToggleButton(
                    checked = liked,
                    enabled = !likeBusy,
                    onCheckedChange = { next ->
                        if (likeBusy) return@FilledIconToggleButton
                        if (!next) {
                            AppMessages.show("已赞过 · 服务端不支持取消点赞")
                            return@FilledIconToggleButton
                        }
                        likeBusy = true
                        likeAnim = true
                        scope.launch {
                            val result = LikeController.like(echo.id, echo.fav_count)
                            if (result.isFailure) {
                                AppMessages.show(
                                    result.exceptionOrNull()?.userMessage() ?: "点赞失败，请稍后重试"
                                )
                            }
                            likeBusy = false
                        }
                    }
                ) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (liked) "已点赞" else "点赞",
                        modifier = Modifier.graphicsLayer {
                            scaleX = likeScale
                            scaleY = likeScale
                        }
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "$likeCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (liked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClick) {
                    Icon(
                        Icons.Filled.ModeComment, contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("评论")
                }
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            echo.content.take(120) + "\n\n来自 ${Ech0Api.base()}/echo/${echo.id}"
                        )
                    }
                    context.startActivity(Intent.createChooser(intent, "分享说说"))
                }) {
                    Icon(
                        Icons.Filled.Share, contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("分享")
                }
            }
        }
    }

    if (likeAnim) {
        LaunchedEffect(likeAnim) {
            kotlinx.coroutines.delay(220)
            likeAnim = false
        }
    }

    if (showDelete && Ech0Store.current.isAdmin) {
        top.saltfish.ech0.ui.common.ConfirmDialog(
            title = "删除这条说说？",
            onConfirm = {
                showDelete = false
                scope.launch {
                    runCatching { Ech0Api.deleteEcho(echo.id) }
                        .onSuccess { ContentBus.notifyDeleted(echo.id) }
                    onReload()
                }
            },
            onDismiss = { showDelete = false }
        )
    }

    previewImage?.let { index ->
        ImageViewerDialog(
            urls = imageUrlsOf(echo.echo_files),
            initialIndex = index,
            onDismiss = { previewImage = null }
        )
    }
}

@Composable
private fun ExtensionBadge(echo: Echo) {
    val type = echo.extension?.type ?: return
    val label = when (type) {
        "MUSIC" -> "♪ 音乐"
        "VIDEO" -> "▶ 视频"
        "GITHUBPROJ" -> "GitHub"
        "WEBSITE" -> "🔗 网站"
        "LOCATION" -> "📍 位置"
        "TWEET" -> "𝕏 推文"
        else -> type
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
