@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package top.saltfish.ech0.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.EchoQueryDto
import top.saltfish.ech0.data.HeatmapDay
import top.saltfish.ech0.data.Tag
import top.saltfish.ech0.data.repo.ContentBus
import top.saltfish.ech0.data.repo.PostStore
import top.saltfish.ech0.data.repo.SiteStore
import top.saltfish.ech0.data.rethrowCancellationLike
import top.saltfish.ech0.ui.AppNav
import top.saltfish.ech0.ui.common.AppMessages
import top.saltfish.ech0.ui.common.ContentPadding
import top.saltfish.ech0.ui.common.EmptyStateCard
import top.saltfish.ech0.ui.common.LocalReduceMotion
import top.saltfish.ech0.ui.common.ErrorCard
import top.saltfish.ech0.ui.common.LaunchSession
import top.saltfish.ech0.ui.common.MarkdownText
import top.saltfish.ech0.ui.common.NetImage
import top.saltfish.ech0.ui.common.RefreshIconButton
import top.saltfish.ech0.ui.common.SettingsListRow
import top.saltfish.ech0.ui.common.TagChip
import top.saltfish.ech0.ui.common.dockContentPadding
import top.saltfish.ech0.ui.common.formatTime
import top.saltfish.ech0.ui.common.isCacheStale
import top.saltfish.ech0.data.userMessage
import java.time.LocalDate

private data class DiscoverLoadResult(
    val today: Result<List<Echo>>,
    val hot: Result<List<Echo>>,
    val heatmap: Result<List<HeatmapDay>>,
    val tags: Result<List<Tag>>,
    val onThisDay: Result<List<Echo>>
)

@Composable
fun DiscoverScreen(nav: AppNav) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val coldStart = remember { LaunchSession.consumeDiscover() }
    val tags = remember { mutableStateListOf<Tag>() }
    val heatmap = remember { mutableStateListOf<HeatmapDay>() }
    val todayList = remember { mutableStateListOf<Echo>() }
    val hotList = remember { mutableStateListOf<Echo>() }
    val otdList = remember { mutableStateListOf<Echo>() }
    var randomEcho by remember { mutableStateOf<Echo?>(null) }
    var randomBusy by remember { mutableStateOf(false) }
    var lastSavedAt by remember { mutableStateOf(0L) }

    var refreshing by remember { mutableStateOf(false) }
    var silentRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadAll(silent: Boolean = false) {
        val requestBase = Ech0Api.base()
        if (requestBase.isBlank() || refreshing || silentRefreshing) return
        if (silent) silentRefreshing = true else refreshing = true
        if (!silent) error = null
        val previousIds = (todayList + hotList + otdList).mapTo(mutableSetOf()) { it.id }
        scope.launch {
            try {
                // 5 个接口并行；先全部取回，再统一 swap，刷新失败时保留缓存不闪空
                val loaded = coroutineScope {
                    val today = async {
                        runCatching { Ech0Api.todayEchos() }.rethrowCancellationLike()
                    }
                    val hot = async {
                        runCatching { Ech0Api.hotEchos(8) }.rethrowCancellationLike()
                    }
                    val heatmap = async {
                        runCatching { Ech0Api.heatmap() }.rethrowCancellationLike()
                    }
                    val tags = async {
                        runCatching { SiteStore.tags() }.rethrowCancellationLike()
                    }
                    // 那年今日非致命：失败时沿用旧缓存
                    val onThisDay = async {
                        runCatching { Ech0Api.onThisDay() }.rethrowCancellationLike()
                    }
                    DiscoverLoadResult(
                        today = today.await(),
                        hot = hot.await(),
                        heatmap = heatmap.await(),
                        tags = tags.await(),
                        onThisDay = onThisDay.await()
                    )
                }
                val firstError = loaded.today.exceptionOrNull()
                    ?: loaded.hot.exceptionOrNull()
                    ?: loaded.heatmap.exceptionOrNull()
                    ?: loaded.tags.exceptionOrNull()

                // 请求期间切换了实例：丢弃旧实例结果，避免把旧站内容写进新站列表
                if (requestBase != Ech0Api.base()) return@launch

                val today = loaded.today.getOrNull()
                val hot = loaded.hot.getOrNull()
                val hm = loaded.heatmap.getOrNull()
                val tg = loaded.tags.getOrNull()
                val otd = loaded.onThisDay.getOrNull()

                if (today != null && hot != null && hm != null && tg != null) {
                    // 热门里已经有“今日”的帖子就不再重复展示，少即是多
                    val hotUnique = hot
                        .filterNot { h -> today.any { it.id == h.id } }
                        .take(5)
                    val freshCount = (today + hotUnique + (otd ?: emptyList()))
                        .count { it.id !in previousIds }
                    todayList.clear(); todayList.addAll(today)
                    hotList.clear(); hotList.addAll(hotUnique)
                    heatmap.clear(); heatmap.addAll(hm)
                    tags.clear(); tags.addAll(tg)
                    otd?.let { otdList.clear(); otdList.addAll(it) }
                    lastSavedAt = System.currentTimeMillis()
                    if (silent && freshCount > 0) {
                        AppMessages.show("发现页有 $freshCount 条新内容")
                    }
                    top.saltfish.ech0.data.DiskCache.saveDiscover(
                        context,
                        top.saltfish.ech0.data.DiskCache.DiscoverCache(
                            savedAt = lastSavedAt,
                            today = today, hot = hotUnique, otd = otdList.toList(), heatmap = hm, tags = tg
                        )
                    )
                }
                if (!silent) error = firstError?.userMessage()
            } finally {
                refreshing = false
                silentRefreshing = false
            }
        }
    }

    // DataStore 首帧可能尚未加载完 baseUrl：监听 baseUrl 变化，就绪后触发刷新
    val storeState by top.saltfish.ech0.data.Ech0Store.stateFlow.collectAsState(
        initial = top.saltfish.ech0.data.Ech0Store.current
    )
    val configured = storeState.baseUrl.isNotBlank()
    var loadedBase by remember { mutableStateOf(storeState.baseUrl) }
    LaunchedEffect(storeState.baseUrl) {
        val base = storeState.baseUrl
        if (base.isBlank()) return@LaunchedEffect
        val baseChanged = loadedBase != base
        if (baseChanged) {
            loadedBase = base
            tags.clear()
            heatmap.clear()
            todayList.clear()
            hotList.clear()
            otdList.clear()
            lastSavedAt = 0L
            error = null
            refreshing = false
            silentRefreshing = false
        }

        if (todayList.isEmpty() && hotList.isEmpty() && heatmap.isEmpty() && tags.isEmpty()) {
            val cached = withContext(Dispatchers.IO) {
                top.saltfish.ech0.data.DiskCache.loadDiscover(context)
            }
            if (cached != null && storeState.baseUrl == base) {
                tags.addAll(cached.tags)
                heatmap.addAll(cached.heatmap)
                todayList.addAll(cached.today)
                hotList.addAll(cached.hot)
                otdList.addAll(cached.otd)
                lastSavedAt = cached.savedAt
            }
        }

        if (baseChanged) {
            loadAll()
        } else {
            val cacheEmpty = todayList.isEmpty() && hotList.isEmpty()
            when {
                coldStart || cacheEmpty -> loadAll()
                isCacheStale(lastSavedAt) -> loadAll(silent = true)
            }
        }
    }

    val contentRevision = ContentBus.feedRevision
    LaunchedEffect(contentRevision) {
        if (contentRevision > 0 && configured) loadAll()
    }

    val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("发现", style = MaterialTheme.typography.titleLarge) },
            actions = {
                if (configured) {
                    RefreshIconButton(refreshing = refreshing) { loadAll() }
                }
            },
            scrollBehavior = topBarScroll,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { loadAll() },
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .nestedScroll(topBarScroll.nestedScrollConnection)
                .padding(horizontal = ContentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!configured) {
                EmptyStateCard(
                    icon = Icons.Filled.Public,
                    title = "还没有连接实例",
                    hint = "连接后这里会展示今日、热门与那年今日",
                    actionText = "填写实例地址",
                    onAction = nav.openSetup
                )
            } else {
                error?.let {
                    ErrorCard(message = it, onRetry = { loadAll() })
                }

                // 今日（直显 + 计数）
                SectionCard(icon = Icons.Filled.WbSunny, title = "今日", count = todayList.size.takeIf { it > 0 }) {
                    if (refreshing && todayList.isEmpty()) Loading()
                    else EchoMiniList(todayList) { nav.openDetail(it) }
                }

                // 热门（直显 top5）
                SectionCard(icon = Icons.Filled.LocalFireDepartment, title = "热门", count = hotList.size.takeIf { it > 0 }) {
                    if (refreshing && hotList.isEmpty()) Loading()
                    else EchoMiniList(hotList) { nav.openDetail(it) }
                }

                // 那年今日
                SectionCard(icon = Icons.Filled.EventRepeat, title = "那年今日", count = otdList.size.takeIf { it > 0 }) {
                    if (refreshing && otdList.isEmpty()) Loading()
                    else if (otdList.isEmpty()) {
                        Text(
                            "这些年的今天还没有记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        EchoMiniList(otdList) { nav.openDetail(it) }
                    }
                }

                // 随机一条（action 语法：trailing 图标钮 = 掷一次；结果可点进详情）
                SettingsListRow(
                    icon = Icons.Filled.Casino,
                    title = "手气不错 · 随机一条",
                    valueText = randomEcho?.let { formatTime(it.created_at) },
                    onClick = { randomEcho?.let { nav.openDetail(it.id) } },
                    trailing = {
                        if (randomBusy) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            IconButton(
                                onClick = {
                                    randomEcho = null
                                    scope.launch {
                                        randomBusy = true
                                        runCatching { randomEcho = Ech0Api.randomEcho() }
                                        randomBusy = false
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Refresh, contentDescription = "换一条",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                )
                // 随机结果卡：弹出/收起展开动画
                val reduceMotion = LocalReduceMotion.current
                AnimatedVisibility(
                    visible = randomEcho != null,
                    enter = expandVertically(
                        if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()
                    ) + fadeIn(tween(if (reduceMotion) 0 else 150)),
                    exit = shrinkVertically(
                        if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec()
                    ) + fadeOut(tween(if (reduceMotion) 0 else 120))
                ) {
                    randomEcho?.let { e ->
                        Card(shape = MaterialTheme.shapes.largeIncreased) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { nav.openDetail(e.id) }
                                    .padding(14.dp)
                            ) {
                                MarkdownText(
                                    markdown = e.content.ifBlank { "（无文字）" }.take(300),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)
                                )
                            }
                        }
                    }
                }

                // 热力图：即使这 30 天没有发布，也保留隐约的格子
                SectionCard(icon = null, title = "近 30 天") {
                    HeatmapView(heatmap)
                }

                // 标签
                if (tags.isNotEmpty()) {
                    SectionCard(icon = Icons.Filled.Tag, title = "标签", count = tags.size) {
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.take(12).forEach { tag ->
                                TagChip(tag.name) { nav.openTag(tag) }
                            }
                        }
                    }
                }
            }

            // Zen（小行卡，跳转语法）
            SettingsListRow(
                icon = Icons.Filled.AutoAwesome,
                title = "Zen 沉浸浏览",
                chevron = true,
                enabled = configured,
                onClick = { if (configured) nav.openZen() }
            )

            Spacer(Modifier.height(dockContentPadding()))
        }
        }
    }
}

@Composable
private fun Loading() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LoadingIndicator(modifier = Modifier.size(20.dp))
        Text(
            "加载中…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    count: Int? = null,
    content: @Composable () -> Unit
) {
    Card(shape = MaterialTheme.shapes.largeIncreased) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Spacer(Modifier.width(8.dp))
                if (count != null) {
                    Text(
                        "$count 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun EchoMiniList(list: List<Echo>, onClick: (String) -> Unit) {
    if (list.isEmpty()) {
        Text(
            "暂无内容",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.take(5).forEach { e ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { onClick(e.id) }
            ) {
                Column(Modifier.fillMaxWidth().padding(9.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatTime(e.created_at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        e.content.ifBlank { "（图片说说）" }.replace(Regex("[#`*~\\[\\]>]+"), "").take(80),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapView(days: List<HeatmapDay>) {
    // 注意：days 是 SnapshotStateList，刷新时原地 clear/addAll，引用不变；
    // 直接 remember(days) 会一直复用旧的 normalized，导致热力图刷新后仍为空。
    val normalized = remember(days.toList()) {
        val byDate = days.associateBy { it.date }
        val today = LocalDate.now()
        (29 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong()).toString()
            byDate[date] ?: HeatmapDay(date = date, count = 0)
        }
    }
    val maxCount = normalized.maxOfOrNull { it.count } ?: 0
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        normalized.chunked(10).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { day ->
                    val intensity = if (maxCount == 0) 0f else day.count.toFloat() / maxCount
                    val color = when {
                        day.count == 0 -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
                        intensity > 0.66f -> primary
                        intensity > 0.33f -> primary.copy(0.7f)
                        else -> primary.copy(0.4f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(30))
                            .background(color)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                RoundedCornerShape(30)
                            )
                    )
                }
                repeat(10 - week.size) {
                    Box(Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

// ---------- Zen 沉浸瀑布流 ----------

@Composable
fun ZenScreen(onClose: () -> Unit) {
    val items = remember { mutableStateListOf<Echo>() }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var endReached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching {
            val r = PostStore.query(EchoQueryDto(page = 1, pageSize = 30))
            items.addAll(r.items)
            page = 2
            endReached = items.size >= r.total
        }.onFailure { error = it.userMessage() }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Zen") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            },
            actions = {
                if (loading) {
                    LoadingIndicator(modifier = Modifier.size(22.dp))
                } else if (items.isNotEmpty()) {
                    Text(
                        "${items.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        error?.let {
            ErrorCard(
                message = it,
                modifier = Modifier.padding(horizontal = ContentPadding, vertical = 8.dp)
            )
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ContentPadding, end = ContentPadding, top = 6.dp, bottom = 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp
        ) {
            items(items, key = { it.id }) { echo ->
                Box(Modifier.animateItem()) {
                    Card(shape = MaterialTheme.shapes.largeIncreased) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        val imageFile = echo.echo_files.firstOrNull()?.file
                        if (imageFile != null) {
                            val iw = imageFile.width ?: 0
                            val ih = imageFile.height ?: 0
                            val ar = if (iw > 0 && ih > 0) iw.toFloat() / ih else 0.8f
                            NetImage(
                                url = Ech0Api.resolveUrl(imageFile.url),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ar.coerceIn(0.6f, 1.6f))
                                    .clip(MaterialTheme.shapes.medium)
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (echo.content.isNotBlank()) {
                            MarkdownText(
                                markdown = echo.content,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            formatTime(echo.created_at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                }
            }
        }
    }
}
