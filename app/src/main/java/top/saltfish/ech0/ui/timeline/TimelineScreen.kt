@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package top.saltfish.ech0.ui.timeline

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import kotlinx.coroutines.launch
import top.saltfish.ech0.R
import top.saltfish.ech0.data.ApiException
import top.saltfish.ech0.data.DiskCache
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.data.LikeController
import top.saltfish.ech0.data.Tag
import top.saltfish.ech0.data.isCancellationLike
import top.saltfish.ech0.ui.AppNav
import top.saltfish.ech0.ui.Branding
import top.saltfish.ech0.ui.common.AppMessages
import top.saltfish.ech0.ui.common.FeedRefreshBus
import top.saltfish.ech0.ui.common.ImageViewerDialog
import top.saltfish.ech0.ui.common.UserAvatar
import top.saltfish.ech0.ui.common.LaunchSession
import top.saltfish.ech0.ui.common.imageUrlsOf
import top.saltfish.ech0.ui.common.ContentPadding
import top.saltfish.ech0.ui.common.EmptyStateCard
import top.saltfish.ech0.ui.common.EchoExtensionCard
import top.saltfish.ech0.ui.common.ErrorCard
import top.saltfish.ech0.ui.common.MarkdownText
import top.saltfish.ech0.ui.common.MediaGrid
import top.saltfish.ech0.ui.common.RefreshIconButton
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

    // 首帧恢复缓存：进页面第一帧就是缓存内容，随后按 TTL 后台刷新覆盖
    val cache = remember { DiskCache.loadTimelineCache(context) }
    val coldStart = remember { LaunchSession.consumeTimeline() }
    val items = remember {
        mutableStateListOf<Echo>().apply { cache?.items?.let { addAll(it) } }
    }
    var lastSavedAt by remember { mutableStateOf(cache?.savedAt ?: 0L) }
    var page by remember { mutableIntStateOf(1) }
    var refreshing by remember { mutableStateOf(false) }
    var silentRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf<Tag?>(null) }

    suspend fun load(reset: Boolean, silent: Boolean = false) {
        if (reset) {
            if (refreshing || silentRefreshing) return
            if (silent) silentRefreshing = true else refreshing = true
        } else {
            if (loadingMore || endReached) return
            loadingMore = true
        }
        if (!silent) error = null
        try {
            val result = Ech0Api.queryEchos(
                top.saltfish.ech0.data.EchoQueryDto(
                    page = if (reset) 1 else page,
                    pageSize = 20,
                    search = search.ifBlank { null },
                    tagIds = tagFilter?.let { listOf(it.id) }
                )
            )
            if (reset) {
                val previousIds = items.mapTo(mutableSetOf()) { it.id }
                if (silent) {
                    // 后台静默刷新：只在发现新记录时提示并插入，不显示转圈
                    val fresh = result.items.filter { it.id !in previousIds }
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
                items.addAll(result.items)
                page += 1
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
        if (state.baseUrl.isNotBlank()) {
            when {
                // 启动后第一次刷新照常显示进度；之后再过期刷新走静默
                coldStart || items.isEmpty() -> load(true)
                isCacheStale(lastSavedAt) -> load(true, silent = true)
            }
        }
    }

    // 发布 / 编辑 / 删除后立即刷新，不再等 3 分钟 TTL
    val feedTick = FeedRefreshBus.tick
    LaunchedEffect(feedTick) {
        if (feedTick > 0 && state.baseUrl.isNotBlank()) load(true)
    }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4 &&
                !endReached && !refreshing && !loadingMore && items.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) {
        // loadingMore 会改变 shouldLoadMore，直接调用会被取消；放到独立 scope 执行
        if (shouldLoadMore) scope.launch { load(false) }
    }

    // M3 enterAlways：滚动时顶栏滑走，内容顶到状态栏（edge-to-edge）
    val topBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        // 全出血列表（内容从状态栏下开始，滚动时钻到透明顶栏之下）
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { load(true) } },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topBarScroll.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = ContentPadding,
                    end = ContentPadding,
                    top = statusTop + 72.dp,
                    bottom = dockContentPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 说说输入条（QQ空间式常驻入口，随信息流滚动）
                item(key = "composer_trigger") {
                    Box(Modifier.animateItem()) {
                        ComposerTrigger(
                            configured = state.baseUrl.isNotBlank(),
                            isAdmin = state.isAdmin,
                            onClick = when {
                                !state.baseUrl.isNotBlank() -> nav.openSetup
                                !state.isAdmin -> nav.openLogin
                                else -> nav.openComposer
                            }
                        )
                    }
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
                                keyboardActions = KeyboardActions(onSearch = { scope.launch { load(true) } }),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                searchActive = false
                                search = ""
                                scope.launch { load(true) }
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
                                    scope.launch { load(true) }
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
                                onRetry = { scope.launch { load(true) } },
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
                            onClick = { nav.openDetail(echo.id) },
                            onTagClick = { tag ->
                                tagFilter = tag
                                scope.launch { load(true) }
                            },
                            onReload = { scope.launch { load(true) } }
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

        // 浮层顶栏：logo + 站名单行；logo 与内容卡片共用 16dp 起始线
        TopAppBar(
            title = {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val logoUrl = Branding.logoUrl
                    Box(Modifier.size(30.dp)) {
                        // 站点 logo 加载失败时保留品牌树标，避免真机上顶栏出现空白
                        Image(
                            painter = painterResource(R.drawable.ic_tree_logo),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                        if (logoUrl != null && !state.lowDataMode) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize().clip(CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.serverName.ifBlank { "Ech0" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            actions = {
                if (!searchActive) {
                    IconButton(onClick = { searchActive = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                }
                RefreshIconButton(refreshing = refreshing) {
                    scope.launch { load(true) }
                }
            },
            scrollBehavior = topBarScroll,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
    }
}

private fun hostOf(url: String): String = runCatching {
    URI(url).host ?: ""
}.getOrDefault("")

@Composable
fun ComposerTrigger(
    configured: Boolean,
    isAdmin: Boolean,
    onClick: () -> Unit
) {
    val hint = when {
        !configured -> "先连接实例，再写说说…"
        !isAdmin -> "登录后写下你的说说…"
        else -> "来，说点什么…"
    }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.Create, contentDescription = null,
                tint = if (isAdmin) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

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
    val likeScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (likeAnim) 1.35f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "like"
    )
    var showDelete by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<Int?>(null) }

    Card(shape = MaterialTheme.shapes.extraLarge) {
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
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
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
