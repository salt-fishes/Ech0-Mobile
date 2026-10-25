@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package top.saltfish.ech0.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.HeatmapDay
import top.saltfish.ech0.data.Tag
import top.saltfish.ech0.ui.AppNav
import top.saltfish.ech0.ui.common.ContentPadding
import top.saltfish.ech0.ui.common.EmptyStateCard
import top.saltfish.ech0.ui.common.ErrorCard
import top.saltfish.ech0.ui.common.MarkdownText
import top.saltfish.ech0.ui.common.NetImage
import top.saltfish.ech0.ui.common.SettingsListRow
import top.saltfish.ech0.ui.common.formatTime

@Composable
fun DiscoverScreen(nav: AppNav) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 首帧恢复缓存：remember 初始化在第一次组合时同步执行，页面第一帧就是缓存内容
    val cached = remember { top.saltfish.ech0.data.DiskCache.loadDiscover(context) }
    val tags = remember { mutableStateListOf<Tag>().apply { cached?.tags?.let { addAll(it) } } }
    val heatmap = remember { mutableStateListOf<HeatmapDay>().apply { cached?.heatmap?.let { addAll(it) } } }
    val todayList = remember { mutableStateListOf<Echo>().apply { cached?.today?.let { addAll(it) } } }
    val hotList = remember { mutableStateListOf<Echo>().apply { cached?.hot?.let { addAll(it) } } }
    val otdList = remember { mutableStateListOf<Echo>().apply { cached?.otd?.let { addAll(it) } } }
    var randomEcho by remember { mutableStateOf<Echo?>(null) }
    var randomBusy by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadAll() {
        if (Ech0Api.base().isBlank()) return
        scope.launch {
            loading = true
            error = null
            var firstError: String? = null
            // 先全部取回，再统一 swap（刷新时不闪空）
            val today = runCatching { Ech0Api.todayEchos() }
                .onFailure { if (firstError == null) firstError = it.message ?: "加载失败" }
                .getOrDefault(emptyList())
            val hot = runCatching { Ech0Api.hotEchos(5) }
                .onFailure { if (firstError == null) firstError = it.message ?: "加载失败" }
                .getOrDefault(emptyList())
            val hm = runCatching { Ech0Api.heatmap() }
                .onFailure { if (firstError == null) firstError = it.message }
                .getOrDefault(emptyList())
            val tg = runCatching { Ech0Api.tags() }
                .onFailure { if (firstError == null) firstError = it.message }
                .getOrDefault(emptyList())
            // 那年今日（非致命：多数日子为空）
            val otd = runCatching { Ech0Api.onThisDay() }.getOrDefault(emptyList())

            todayList.clear(); todayList.addAll(today)
            hotList.clear(); hotList.addAll(hot)
            heatmap.clear(); heatmap.addAll(hm)
            tags.clear(); tags.addAll(tg)
            otdList.clear(); otdList.addAll(otd)

            if (firstError == null) {
                top.saltfish.ech0.data.DiskCache.saveDiscover(
                    context,
                    top.saltfish.ech0.data.DiskCache.DiscoverCache(
                        savedAt = System.currentTimeMillis(),
                        today = today, hot = hot, otd = otd, heatmap = hm, tags = tg
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
        if (storeState.baseUrl.isNotBlank()) loadAll()
    }

    val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("发现", style = MaterialTheme.typography.titleLarge) },
            actions = {
                if (configured) {
                    IconButton(
                        onClick = {
                            tags.clear(); heatmap.clear(); todayList.clear()
                            hotList.clear(); otdList.clear(); randomEcho = null
                            loadAll()
                        }
                    ) {
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

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .nestedScroll(topBarScroll.nestedScrollConnection)
                .padding(horizontal = ContentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    ErrorCard(message = it, onRetry = {
                        tags.clear(); heatmap.clear(); todayList.clear()
                        hotList.clear(); otdList.clear(); randomEcho = null
                        loadAll()
                    })
                }

                // 今日（直显 + 计数）
                SectionCard(icon = Icons.Filled.WbSunny, title = "今日", count = todayList.size.takeIf { it > 0 }) {
                    if (loading && todayList.isEmpty()) Loading()
                    else EchoMiniList(todayList) { nav.openDetail(it) }
                }

                // 热门（直显 top5）
                SectionCard(icon = Icons.Filled.LocalFireDepartment, title = "热门", count = hotList.size.takeIf { it > 0 }) {
                    if (loading && hotList.isEmpty()) Loading()
                    else EchoMiniList(hotList) { nav.openDetail(it) }
                }

                // 那年今日
                SectionCard(icon = Icons.Filled.EventRepeat, title = "那年今日", count = otdList.size.takeIf { it > 0 }) {
                    if (loading && otdList.isEmpty()) Loading()
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
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
                AnimatedVisibility(
                    visible = randomEcho != null,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150)),
                    exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(120))
                ) {
                    randomEcho?.let { e ->
                        Card(shape = MaterialTheme.shapes.large) {
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

                // 热力图
                if (heatmap.isNotEmpty()) {
                    SectionCard(icon = null, title = "近 30 天") {
                        HeatmapView(heatmap)
                    }
                }

                // 标签
                if (tags.isNotEmpty()) {
                    SectionCard(icon = Icons.Filled.Tag, title = "标签", count = tags.size) {
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.take(12).forEach { tag ->
                                Text(
                                    "#${tag.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
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

            Spacer(Modifier.height(200.dp))
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
    Card(shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.take(5).forEach { e ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { onClick(e.id) }
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            e.username.ifBlank { "匿名" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatTime(e.created_at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        e.content.ifBlank { "（图片说说）" }.replace(Regex("[#`*~\\[\\]>]+"), "").take(80),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapView(days: List<HeatmapDay>) {
    val maxCount = days.maxOfOrNull { it.count } ?: 1
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        days.chunked(10).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { day ->
                    val intensity = if (maxCount == 0) 0f else day.count.toFloat() / maxCount
                    val color = when {
                        day.count == 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
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
                    )
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
            val r = Ech0Api.queryEchos(top.saltfish.ech0.data.EchoQueryDto(page = 1, pageSize = 30))
            items.addAll(r.items)
            page = 2
            endReached = items.size >= r.total
        }.onFailure { error = it.message }
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
                    Card(shape = MaterialTheme.shapes.large) {
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
