@file:OptIn(ExperimentalMaterial3Api::class)

package top.saltfish.ech0.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.saltfish.ech0.MainActivity
import top.saltfish.ech0.SharePayload
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.ui.common.ContentPadding
import top.saltfish.ech0.ui.common.Ech0Theme
import top.saltfish.ech0.ui.common.M3Easing
import top.saltfish.ech0.ui.composer.ComposerSheet
import top.saltfish.ech0.ui.detail.DetailScreen
import top.saltfish.ech0.ui.discover.DiscoverScreen
import top.saltfish.ech0.ui.hub.HubScreen
import top.saltfish.ech0.ui.me.MeScreen
import top.saltfish.ech0.ui.timeline.TimelineScreen

/** 覆盖层页面（导航栏隐藏，独立返回） */
sealed interface Overlay {
    data class Detail(val echoId: String) : Overlay
    data object Login : Overlay
    data object Setup : Overlay
    data object Settings : Overlay
    data object Zen : Overlay
    data object About : Overlay
    data object Drafts : Overlay
    data object Queue : Overlay
}

class AppNav(
    val openDetail: (String) -> Unit,
    val openLogin: () -> Unit,
    val openSetup: () -> Unit,
    val openSettings: () -> Unit,
    val openZen: () -> Unit,
    val openAbout: () -> Unit,
    val openDrafts: () -> Unit,
    val openQueue: () -> Unit,
    /** 带闸门的发布入口：未配置→连接引导；未登录→登录；管理员→编辑器 */
    val openComposer: () -> Unit
)

/** 站点品牌信息（logo），供时间线页头使用 */
object Branding {
    var logoUrl by androidx.compose.runtime.mutableStateOf<String?>(null)
}

@Composable
fun Ech0AppRoot(sharedInitial: SharePayload) {
    val state by Ech0Store.stateFlow.collectAsState(initial = Ech0Store.current)
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    Ech0Theme(darkTheme = dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            var overlay by remember { mutableStateOf<Overlay?>(null) }
            var composerSeed by remember { mutableStateOf(0) }
            var composerOpen by remember { mutableStateOf(false) }
            var setupShownThisSession by remember { mutableStateOf(false) }
            var pendingShare by remember { mutableStateOf(SharePayload()) }
            var showLoginPrompt by remember { mutableStateOf(false) }

            // 预取站点 logo（失败静默回落应用图标）
            LaunchedEffect(state.baseUrl) {
                if (state.baseUrl.isNotBlank()) {
                    runCatching {
                        val self = Ech0Api.connectSelf()
                        Branding.logoUrl = self.logo?.takeIf { it.isNotBlank() }
                            ?.let { Ech0Api.resolveUrl(it) }
                    }
                }
            }

            // 首启：未配置实例 → 连接引导（可跳过）
            LaunchedEffect(Unit) {
                if (state.baseUrl.isBlank() && !setupShownThisSession) {
                    overlay = Overlay.Setup
                    setupShownThisSession = true
                }
            }

            LaunchedEffect(sharedInitial) {
                if (sharedInitial.text.isNotBlank() || sharedInitial.images.isNotEmpty()) {
                    pendingShare = sharedInitial
                    composerOpen = true
                    composerSeed++
                    MainActivity.composerRequest.value = SharePayload()
                    kotlinx.coroutines.delay(1200)
                    pendingShare = SharePayload()
                }
            }

            val nav = remember {
                AppNav(
                    openDetail = { overlay = Overlay.Detail(it) },
                    openLogin = { overlay = Overlay.Login },
                    openSetup = { overlay = Overlay.Setup },
                    openSettings = { overlay = Overlay.Settings },
                    openZen = { overlay = Overlay.Zen },
                    openAbout = { overlay = Overlay.About },
                    openDrafts = { overlay = Overlay.Drafts },
                    openQueue = { overlay = Overlay.Queue },
                    openComposer = {
                        when {
                            state.baseUrl.isBlank() -> overlay = Overlay.Setup
                            state.username.isBlank() -> overlay = Overlay.Login
                            else -> {
                                composerSeed++
                                composerOpen = true
                            }
                        }
                    }
                )
            }

            // 系统返回：先关抽屉/覆盖页，避免直接退出应用
            BackHandler(enabled = overlay != null || composerOpen) {
                when {
                    composerOpen -> composerOpen = false
                    else -> overlay = null
                }
            }

            // 覆盖页导航：M3 shared-axis X（前进从右侧滑入 · 返回滑出右侧，强调型缓动）
            AnimatedContent(
                targetState = overlay,
                transitionSpec = {
                    val forward = targetState != null
                    val enter = slideInHorizontally(
                        tween(300, easing = M3Easing.EmphasizedDecelerate)
                    ) { if (forward) it else -it / 4 } + fadeIn(tween(90))
                    val exit = slideOutHorizontally(
                        tween(200, easing = M3Easing.EmphasizedAccelerate)
                    ) { if (forward) -it / 4 else it } + fadeOut(tween(90))
                    enter togetherWith exit
                },
                label = "overlay"
            ) { currentOverlay ->
                when (val o = currentOverlay) {
                null -> {
                    Box(Modifier.fillMaxSize()) {
                        // M3 fade-through：旧内容快速淡出，新内容延迟淡入并轻微放大
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                (fadeIn(tween(210, delayMillis = 90)) +
                                    androidx.compose.animation.scaleIn(
                                        initialScale = 0.94f,
                                        animationSpec = tween(210, delayMillis = 90)
                                    ))
                                    .togetherWith(fadeOut(tween(90)))
                            },
                            label = "tab"
                        ) { currentTab ->
                            when (currentTab) {
                                0 -> TimelineScreen(nav)
                                1 -> DiscoverScreen(nav)
                                2 -> HubScreen(nav)
                                else -> MeScreen(nav)
                            }
                        }
                        // 悬浮 dock（QQ空间式）：四角 tab + 中央发布钮
                        FloatingDock(
                            selectedTab = tab,
                            onSelect = { tab = it },
                            onPublish = {
                                when {
                                    state.baseUrl.isBlank() -> overlay = Overlay.Setup
                                    !state.isAdmin -> showLoginPrompt = true
                                    else -> {
                                        composerSeed++
                                        composerOpen = true
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
                is Overlay.Detail -> DetailScreen(
                    echoId = o.echoId,
                    onClose = { overlay = null }
                )
                Overlay.Login -> top.saltfish.ech0.ui.me.LoginScreen(
                    onClose = { overlay = null }
                )
                Overlay.Setup -> SetupScreen(
                    onClose = { overlay = null },
                    onConnected = { overlay = null }
                )
                Overlay.Settings -> top.saltfish.ech0.ui.me.SettingsScreen(
                    onClose = { overlay = null }
                )
                Overlay.Zen -> top.saltfish.ech0.ui.discover.ZenScreen(
                    onClose = { overlay = null }
                )
                Overlay.About -> top.saltfish.ech0.ui.me.AboutScreen(
                    onClose = { overlay = null }
                )
                Overlay.Drafts -> top.saltfish.ech0.ui.me.DraftsScreen(
                    onClose = { overlay = null }
                )
                Overlay.Queue -> top.saltfish.ech0.ui.me.QueueScreen(
                    onClose = { overlay = null }
                )
                }
            }

            // 写说说抽屉（默认形态，可拖全屏）：入场 = 弹簧上滑 + 淡入
            AnimatedVisibility(
                visible = composerOpen,
                enter = fadeIn(tween(150)) + slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { it / 5 },
                exit = fadeOut(tween(140))
            ) {
                ComposerSheet(
                    seed = composerSeed,
                    shared = pendingShare,
                    onClose = { composerOpen = false },
                    onPublished = { composerOpen = false }
                )
            }

            // 未登录点发布：弹窗引导
            if (showLoginPrompt) {
                AlertDialog(
                    onDismissRequest = { showLoginPrompt = false },
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    title = { Text("登录后发布") },
                    text = { Text("登录管理员账号后即可发表说说") },
                    confirmButton = {
                        TextButton(onClick = {
                            showLoginPrompt = false
                            overlay = Overlay.Login
                        }) { Text("去登录") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLoginPrompt = false }) { Text("取消") }
                    }
                )
            }
        }
    }
}

// ---------- 悬浮 dock（QQ空间式：四角 tab + 中央发布钮跨在上沿） ----------

@Composable
private fun FloatingDock(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 注意：shadow 的 clip 默认值是 elevation>0（=true），会裁掉凸出的 FAB，必须显式 false
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .shadow(8.dp, CircleShape, clip = false)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DockItem(Icons.Filled.Home, "时间线", selectedTab == 0) { onSelect(0) }
        DockItem(Icons.Filled.Explore, "发现", selectedTab == 1) { onSelect(1) }
        // 中央发布钮：完整放在 dock 内，无阴影（与容器贴合）
        FloatingActionButton(
            onClick = onPublish,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            )
        ) {
            Icon(Icons.Filled.Add, contentDescription = "写说说")
        }
        DockItem(Icons.Filled.Link, "友链", selectedTab == 2) { onSelect(2) }
        DockItem(Icons.Filled.Person, "我的", selectedTab == 3) { onSelect(3) }
    }
}

@Composable
private fun DockItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

// ---------- 首启连接引导 ----------

@Composable
fun SetupScreen(
    onClose: () -> Unit,
    onConnected: () -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var url by remember { mutableStateOf(Ech0Store.current.baseUrl.ifBlank { "https://" }) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 首启卡：Expressive 弹簧弹入
        val entrance = remember {
            MutableTransitionState(false).apply { targetState = true }
        }
        AnimatedVisibility(
            visibleState = entrance,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.88f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ContentPadding + 12.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "E",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    "连接你的 Ech0 实例",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "填入你自建站点的地址，比如 https://echo.example.com",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("实例地址") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth().imePadding()
                )
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        message = null
                        scope.launch {
                            try {
                                Ech0Store.setBaseUrl(url)
                                val hello = Ech0Api.hello()
                                val settings = Ech0Api.settings()
                                Ech0Store.setServerName(settings.site_title.ifBlank { settings.server_name })
                                onConnected()
                            } catch (e: Exception) {
                                message = "连接失败，请检查地址：${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (busy) "连接中…" else "连接")
                }
                TextButton(onClick = onClose) {
                    Text("先逛逛 · 稍后在「我的 → 设置」里填写")
                }
            }
        }
        }
    }
}
