@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package top.saltfish.ech0.ui.me

import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.saltfish.ech0.BuildConfig
import top.saltfish.ech0.data.ApiException
import top.saltfish.ech0.data.AppRelease
import top.saltfish.ech0.data.DefaultGitHubRepo
import top.saltfish.ech0.data.DiskCache
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.data.SavedInstance
import top.saltfish.ech0.data.net.DiagResult
import top.saltfish.ech0.data.net.NetworkDiagnostics
import top.saltfish.ech0.data.net.RequestDiagnostics
import top.saltfish.ech0.data.net.RequestRecord
import top.saltfish.ech0.data.repo.SiteStore
import top.saltfish.ech0.data.UpdateChecker
import top.saltfish.ech0.publish.PendingFileDeletions
import top.saltfish.ech0.publish.PublishBus
import top.saltfish.ech0.publish.PublishStore
import top.saltfish.ech0.ui.AppNav
import top.saltfish.ech0.ui.common.Avatar
import top.saltfish.ech0.ui.common.ConfirmDialog
import top.saltfish.ech0.ui.common.ContentPadding
import top.saltfish.ech0.ui.common.Ech0ChoiceGroup
import top.saltfish.ech0.ui.common.EmptyStateCard
import top.saltfish.ech0.ui.common.LocalReduceMotion
import top.saltfish.ech0.ui.common.M3Easing
import top.saltfish.ech0.ui.common.OverlayTopBar
import top.saltfish.ech0.ui.common.ProfileAvatar
import top.saltfish.ech0.ui.common.SettingsListRow
import top.saltfish.ech0.ui.common.appBackgroundBrush
import top.saltfish.ech0.ui.common.dockContentPadding
import top.saltfish.ech0.ui.common.formatDateTime
import top.saltfish.ech0.data.isCancellationLike
import top.saltfish.ech0.data.rethrowCancellationLike
import top.saltfish.ech0.data.userMessage
import top.saltfish.ech0.work.PublishScheduler
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.Locale
import kotlin.math.roundToInt

private fun hostOf(url: String): String = runCatching { URI(url).host ?: "" }.getOrDefault("")

private fun schemeOf(url: String): String = runCatching { URI(url).scheme ?: "" }.getOrDefault("")

private enum class SettingsGroup(val title: String, val keywords: List<String>) {
    ACCOUNT(
        "账号与实例",
        listOf(
            "账号", "实例", "地址", "服务器", "站点", "登录", "token", "版本", "连接",
            "过期", "退出", "cookie", "多实例", "保存实例", "切换"
        )
    ),
    NETWORK(
        "网络与流量",
        listOf(
            "网络", "流量", "连接", "延迟", "低流量", "wifi", "Wi-Fi", "测试", "省流",
            "慢速", "预加载", "刷新间隔", "诊断", "仅", "上传", "播放"
        )
    ),
    STORAGE(
        "缓存与存储",
        listOf("缓存", "存储", "清理", "空间", "草稿", "图片", "临时", "磁盘", "上限", "自动清理")
    ),
    APPEARANCE(
        "阅读与外观",
        listOf(
            "阅读", "字号", "字体", "外观", "主题", "颜色", "背景", "模糊", "取色", "深色",
            "行距", "代码", "链接", "gif", "视频", "相对时间", "纯黑", "amoled", "对比",
            "动画", "状态栏", "图标", "暗化"
        )
    ),
    CONTENT(
        "编辑与评论",
        listOf(
            "编辑", "评论", "草稿", "图片质量", "发布", "身份", "昵称", "邮箱", "压缩",
            "布局", "私密", "标签", "并发", "重试", "确认", "exif", "排序",
            "格式", "webp", "jpg", "png", "裁剪", "旋转"
        )
    ),
    ADVANCED(
        "高级与关于",
        listOf(
            "高级", "关于", "更新", "版本", "token", "重置", "自动检查", "诊断", "请求",
            "代理", "ua", "user-agent", "导出", "导入", "清除登录"
        )
    );

    fun matches(query: String): Boolean =
        query.isBlank() || title.contains(query, ignoreCase = true) ||
            keywords.any { it.contains(query, ignoreCase = true) }
}

private sealed interface SettingsPage {
    data object Home : SettingsPage
    data object Search : SettingsPage
    data class Group(val group: SettingsGroup) : SettingsPage
}

private val BackgroundPresets = listOf(
    "default" to "默认",
    "paper" to "纸张",
    "mist" to "薄雾",
    "dusk" to "暮色"
)

private val AccentPresets = listOf(
    0xFF0B57D0L to "蓝",
    0xFF00696DL to "青",
    0xFF6750A4L to "紫",
    0xFF8B5000L to "琥珀",
    0xFF9C4146L to "红",
    0xFF3F6212L to "绿"
)

// ---------- 我的 ----------

@Composable
fun MeScreen(nav: AppNav) {
    val state = Ech0Store.current
    var queueCount by remember { mutableStateOf(0) }
    var draftCount by remember { mutableStateOf(0) }
    LaunchedEffect(PublishBus.tick) {
        runCatching {
            queueCount = PublishStore.queue().count { it.state != "done" }
            draftCount = PublishStore.drafts().size
        }
    }

    val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("我的", style = MaterialTheme.typography.titleLarge) },
            scrollBehavior = topBarScroll,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
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
            val loggedIn = state.username.isNotBlank()

            if (!loggedIn) {
                // 整卡可点的登录入口（游客最该点的东西最显眼）
                Card(
                    onClick = nav.openLogin,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("登录 Ech0", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = {
                            Text(
                                if (state.baseUrl.isBlank()) "先连接实例，再登录你的账号"
                                else "登录后可发布说说、管理草稿与队列"
                            )
                        },
                        leadingContent = {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Person, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Icon(
                                Icons.Filled.ChevronRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                if (state.baseUrl.isBlank()) {
                    EmptyStateCard(
                        icon = Icons.Filled.Public,
                        title = "还没有连接实例",
                        hint = "填入自建站点地址后即可登录",
                        actionText = "填写实例地址",
                        onAction = nav.openSetup
                    )
                }
            } else {
                // 账号与实例卡：点击进入账号 / 实例管理
                Card(
                    onClick = nav.openAccountSettings,
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(
                            name = state.username,
                            avatarUrl = state.avatarUrl,
                            size = 52.dp
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    state.username,
                                    style = MaterialTheme.typography.titleMediumEmphasized
                                )
                                if (state.isAdmin) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            "管理员",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.size(2.dp))
                            val instance = state.serverName
                                .ifBlank { hostOf(state.baseUrl) }
                                .ifBlank { "未配置实例" }
                            Text(
                                "@${state.username} · $instance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "账号与实例",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 菜单（M3 ListItem + 分隔线）
            Card(shape = MaterialTheme.shapes.largeIncreased, modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsListRow(
                        icon = Icons.AutoMirrored.Filled.Notes,
                        title = "草稿箱",
                        valueText = if (draftCount > 0) "$draftCount 篇" else null,
                        chevron = true,
                        onClick = nav.openDrafts
                    )
                    // M3 规范：分组内分隔线从文字起点缩进（与图标对齐）
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    SettingsListRow(
                        icon = Icons.Filled.Schedule,
                        title = "发布队列",
                        valueText = if (queueCount > 0) "待发 $queueCount 条" else "无待发",
                        chevron = true,
                        onClick = nav.openQueue
                    )
                    // M3 规范：分组内分隔线从文字起点缩进（与图标对齐）
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    SettingsListRow(
                        icon = Icons.Filled.Settings,
                        title = "设置",
                        valueText = state.serverName.ifBlank { hostOf(state.baseUrl) }
                            .ifBlank { "未配置实例" },
                        chevron = true,
                        onClick = nav.openSettings
                    )
                    // M3 规范：分组内分隔线从文字起点缩进（与图标对齐）
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    SettingsListRow(
                        icon = Icons.Filled.Info,
                        title = "关于",
                        chevron = true,
                        onClick = nav.openAbout
                    )
                }
            }

            Spacer(Modifier.height(dockContentPadding()))
        }
    }
}

// ---------- 登录 ----------

@Composable
fun LoginScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf(Ech0Store.current.baseUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("登录", onClose)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Field("实例地址（如 https://echo.example.com）", baseUrl) { baseUrl = it }
            Field("用户名", username, ImeAction.Next) { username = it }
            Field("密码", password, ImeAction.Done, secret = true) { password = it }
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
                            Ech0Store.setBaseUrl(baseUrl)
                            Ech0Api.login(username.trim(), password)
                            runCatching {
                                val s = SiteStore.settings(force = true)
                                Ech0Store.setServerName(s.site_title.ifBlank { s.server_name })
                            }
                            onClose()
                        } catch (e: Exception) {
                            if (e.isCancellationLike()) return@launch
                            message = e.userMessage()
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
                Text(if (busy) "登录中…" else "登录")
            }
            Spacer(Modifier.size(200.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    imeAction: ImeAction = ImeAction.Next,
    secret: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = imeAction),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------- 设置 ----------

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(onClose: () -> Unit, initialAccount: Boolean = false) {
    val scope = rememberCoroutineScope()
    val state = Ech0Store.current
    var baseUrl by remember { mutableStateOf(state.baseUrl) }
    var themeMode by remember { mutableStateOf(state.themeMode) }
    var dynamicColor by remember { mutableStateOf(state.dynamicColor) }
    var accentSeed by remember { mutableStateOf(state.accentSeed) }
    var backgroundStyle by remember { mutableStateOf(state.backgroundStyle) }
    var backgroundImage by remember { mutableStateOf(state.backgroundImage) }
    var backgroundBlur by remember { mutableStateOf(state.backgroundBlur) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var lowDataMode by remember { mutableStateOf(state.lowDataMode) }
    var promptSaveDraft by remember { mutableStateOf(state.promptSaveDraft) }
    var commentUseDefault by remember { mutableStateOf(state.commentUseDefault) }
    var commentNickname by remember {
        mutableStateOf(state.commentNickname.ifBlank { state.username })
    }
    var commentEmail by remember { mutableStateOf(state.commentEmail) }
    var commentWebsite by remember { mutableStateOf(state.commentWebsite) }
    var tokenInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf<String?>(null) }
    var textScale by remember { mutableStateOf(state.textScale) }
    var netChecking by remember { mutableStateOf(false) }
    var netOk by remember { mutableStateOf<Boolean?>(null) }
    var netStatus by remember { mutableStateOf<String?>(null) }
    var storage by remember { mutableStateOf<StorageStats?>(null) }
    var storageBusy by remember { mutableStateOf(false) }
    var settingsQuery by remember { mutableStateOf("") }
    var selectedGroup by remember {
        mutableStateOf(if (initialAccount) SettingsGroup.ACCOUNT else null)
    }
    var searchMode by remember { mutableStateOf(false) }
    val homeScroll = rememberScrollState()
    val searchFocus = remember { FocusRequester() }
    var autoCheckUpdate by remember { mutableStateOf(state.autoCheckAppUpdate) }
    var imageQuality by remember { mutableIntStateOf(state.defaultImageQuality) }
    var imageQualityMode by remember { mutableStateOf(state.defaultImageQualityMode) }
    var customImageQuality by remember {
        mutableIntStateOf(
            if (state.defaultImageQualityMode == "custom") state.defaultImageQuality else 60
        )
    }
    var imageFormat by remember { mutableStateOf(state.defaultImageFormat) }
    var pendingBaseUrl by remember { mutableStateOf<String?>(null) }
    var probeSiteName by remember { mutableStateOf<String?>(null) }
    var backgroundDim by remember { mutableFloatStateOf(state.backgroundDim) }
    var savedInstances by remember { mutableStateOf<List<SavedInstance>>(emptyList()) }
    var instancesRevision by remember { mutableIntStateOf(0) }
    var showNetDiagnostics by remember { mutableStateOf(false) }
    var netDiagResults by remember { mutableStateOf<List<DiagResult>>(emptyList()) }
    var netDiagRunning by remember { mutableStateOf(false) }
    var showRequestDiagnostics by remember { mutableStateOf(false) }
    var requestRecords by remember { mutableStateOf<List<RequestRecord>>(emptyList()) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<AppRelease?>(null) }
    var uaInput by remember { mutableStateOf(state.customUserAgent) }
    var proxyInput by remember { mutableStateOf(state.proxyUrl) }
    var defaultTagsInput by remember { mutableStateOf(state.defaultTags.joinToString("，")) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var settingsRevision by remember { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    suspend fun loadStorage(): StorageStats = withContext(Dispatchers.IO) {
        val pageDir = File(context.filesDir, "cache")
        val draftDir = File(context.filesDir, "draft_images")
        val imageDir = File(context.cacheDir, "image_cache")
        val pageBytes = dirSize(pageDir)
        val draftBytes = dirSize(draftDir)
        // 临时文件 = cacheDir 下除图片缓存之外的内容
        val tempBytes = context.cacheDir.listFiles()
            ?.filter { it.name != "image_cache" }
            ?.sumOf { dirSize(it) } ?: 0L
        // 应用数据不重复计算页面缓存和草稿暂存
        val appBytes = (dirSize(context.filesDir) - pageBytes - draftBytes).coerceAtLeast(0L)
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        StorageStats(
            pageBytes = pageBytes,
            imageBytes = dirSize(imageDir),
            tempBytes = tempBytes,
            draftBytes = draftBytes,
            appBytes = appBytes,
            freeBytes = stat?.availableBytes ?: 0L,
            totalBytes = stat?.totalBytes ?: 0L,
            lastRefreshAt = DiskCache.latestSavedAt(context)
        )
    }

    LaunchedEffect(Unit) {
        runCatching { version = SiteStore.hello().version }
        storage = loadStorage()
    }

    LaunchedEffect(state.autoCheckAppUpdate) {
        autoCheckUpdate = state.autoCheckAppUpdate
    }

    // 导入 / 重置 / 外部修改后，把本地编辑态重新同步到当前设置
    LaunchedEffect(settingsRevision) {
        if (settingsRevision == 0) return@LaunchedEffect
        val s = Ech0Store.current
        baseUrl = s.baseUrl
        themeMode = s.themeMode
        dynamicColor = s.dynamicColor
        accentSeed = s.accentSeed
        backgroundStyle = s.backgroundStyle
        backgroundImage = s.backgroundImage
        backgroundBlur = s.backgroundBlur
        backgroundDim = s.backgroundDim
        lowDataMode = s.lowDataMode
        promptSaveDraft = s.promptSaveDraft
        commentUseDefault = s.commentUseDefault
        commentNickname = s.commentNickname.ifBlank { s.username }
        commentEmail = s.commentEmail
        commentWebsite = s.commentWebsite
        textScale = s.textScale
        autoCheckUpdate = s.autoCheckAppUpdate
        imageQuality = s.defaultImageQuality
        imageQualityMode = s.defaultImageQualityMode
        if (s.defaultImageQualityMode == "custom") {
            customImageQuality = s.defaultImageQuality
        }
        imageFormat = s.defaultImageFormat
        defaultTagsInput = s.defaultTags.joinToString("，")
        uaInput = s.customUserAgent
        proxyInput = s.proxyUrl
    }

    LaunchedEffect(state.baseUrl, state.username, state.serverName, instancesRevision) {
        savedInstances = Ech0Store.savedInstances()
    }

    // 评论身份按当前实例 / 账号加载，避免把 A 站邮箱带到 B 站
    LaunchedEffect(state.baseUrl, state.username) {
        val stored = Ech0Store.currentCommentIdentity() ?: return@LaunchedEffect
        commentUseDefault = stored.useDefault
        commentNickname = stored.nickname.ifBlank { state.username }
        commentEmail = stored.email
        commentWebsite = stored.website
    }

    LaunchedEffect(backgroundDim) {
        delay(180)
        if (backgroundDim != Ech0Store.current.backgroundDim) {
            Ech0Store.setBackgroundDim(backgroundDim)
        }
    }

    fun checkNetwork() {
        if (netChecking) return
        val target = baseUrl.trim().trimEnd('/')
        if (target.isBlank()) {
            netOk = false
            netStatus = "请先填写实例地址"
            return
        }
        netChecking = true
        netOk = null
        netStatus = null
        probeSiteName = null
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                // 只探测目标地址，不写入设置、不清登录态
                val hello = Ech0Api.probeHello(target)
                val latency = SystemClock.elapsedRealtime() - started
                val site = runCatching { Ech0Api.probeSettings(target) }.getOrNull()
                version = hello.version
                probeSiteName = site?.site_title?.takeIf { it.isNotBlank() }
                    ?: site?.server_name?.takeIf { it.isNotBlank() }
                netOk = true
                netStatus = buildString {
                    append("连接正常 · ${latency} ms · Ech0 ${hello.version}")
                    probeSiteName?.let { append(" · $it") }
                }
            } catch (e: Exception) {
                if (e.isCancellationLike()) return@launch
                netOk = false
                netStatus = "连接失败：${e.userMessage()}"
            } finally {
                netChecking = false
            }
        }
    }

    fun saveBaseUrl(target: String) {
        scope.launch {
            val previous = Ech0Store.current.baseUrl.trimEnd('/')
            val previousHost = hostOf(previous)
            val nextHost = hostOf(target)
            val hostChanged = previousHost.isBlank() || nextHost.isBlank() ||
                !previousHost.equals(nextHost, ignoreCase = true)
            val schemeChanged = schemeOf(previous).isNotBlank() &&
                schemeOf(target).isNotBlank() &&
                !schemeOf(previous).equals(schemeOf(target), ignoreCase = true)
            Ech0Store.setBaseUrl(target)
            baseUrl = target
            version = null
            netOk = null
            netStatus = null
            probeSiteName = null
            message = if (hostChanged || schemeChanged) {
                "已切换实例，登录状态和页面缓存已清除"
            } else {
                "实例地址已保存"
            }
            runCatching { Ech0Api.probeHello(target) }.onSuccess { version = it.version }
            runCatching { Ech0Api.probeSettings(target) }.onSuccess { site ->
                val name = site.site_title.ifBlank { site.server_name }
                if (name.isNotBlank()) Ech0Store.setServerName(name)
            }
            instancesRevision++
        }
    }

    fun requestSaveBaseUrl() {
        val rawTarget = baseUrl.trim().trimEnd('/')
        if (rawTarget.isBlank()) {
            message = "请先填写实例地址"
            return
        }
        val target = runCatching { Ech0Api.normalizeProbeBase(rawTarget) }
            .getOrElse {
                message = it.userMessage()
                return
            }
        val previous = Ech0Store.current.baseUrl.trimEnd('/')
        if (target == previous) {
            message = "当前已是这个实例"
            return
        }
        // 换 host / 协议会清登录态，先让用户确认；同 host 只改路径直接保存
        val hostChanged = !hostOf(previous).equals(hostOf(target), ignoreCase = true)
        val schemeChanged = schemeOf(previous).isNotBlank() &&
            schemeOf(target).isNotBlank() &&
            !schemeOf(previous).equals(schemeOf(target), ignoreCase = true)
        if (hostChanged || schemeChanged) {
            pendingBaseUrl = target
        } else {
            saveBaseUrl(target)
        }
    }

    fun refreshStorage() {
        if (storageBusy) return
        storageBusy = true
        scope.launch {
            storage = loadStorage()
            storageBusy = false
        }
    }

    fun runNetworkDiagnostics() {
        if (netDiagRunning) return
        netDiagRunning = true
        netDiagResults = emptyList()
        showNetDiagnostics = true
        scope.launch {
            netDiagResults = runCatching {
                NetworkDiagnostics.run(Ech0Store.current.baseUrl)
            }.getOrElse {
                listOf(DiagResult("诊断", false, it.userMessage()))
            }
            netDiagRunning = false
        }
    }

    fun checkUpdateNow() {
        if (updateChecking) return
        updateChecking = true
        scope.launch {
            UpdateChecker.check(DefaultGitHubRepo)
                .onSuccess { release ->
                    if (release != null) {
                        updateRelease = release
                    } else {
                        message = "当前已是最新版本"
                    }
                }
                .onFailure { message = "检查更新失败：${it.userMessage()}" }
            updateChecking = false
            Ech0Store.markAppUpdateChecked()
        }
    }

    fun runStorageTask(success: String, block: suspend () -> Unit) {
        if (storageBusy) return
        storageBusy = true
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { block() } }
            storage = loadStorage()
            storageBusy = false
            message = success
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) cropUri = uri
    }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(Ech0Store.exportSettings().toString(2))
                    } ?: error("无法写入文件")
                }
            }
            message = if (result.isSuccess) "设置已导出" else "导出失败：${result.exceptionOrNull()?.message}"
        }
    }

    val importSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: error("无法读取文件")
                    Ech0Store.importSettings(JSONObject(text))
                }
            }
            message = if (result.isSuccess) "设置已导入" else "导入失败：${result.exceptionOrNull()?.message}"
            if (result.isSuccess) settingsRevision++
        }
    }
    val configuration = LocalConfiguration.current
    val screenAspect = if (configuration.screenHeightDp > 0) {
        configuration.screenWidthDp.toFloat() / configuration.screenHeightDp
    } else 0.46f

    fun backFromSettings() {
        when {
            searchMode -> {
                searchMode = false
                settingsQuery = ""
            }
            // 从「我的」直接进入账号与实例：返回应回到我的，而不是设置首页
            initialAccount && selectedGroup != null -> onClose()
            selectedGroup != null -> selectedGroup = null
            else -> onClose()
        }
    }

    fun groupVisible(group: SettingsGroup, page: SettingsPage): Boolean =
        when (page) {
            SettingsPage.Home -> false
            SettingsPage.Search -> settingsQuery.isNotBlank() && group.matches(settingsQuery)
            is SettingsPage.Group -> page.group == group
        }

    fun groupSummary(group: SettingsGroup): String {
        val live = Ech0Store.current
        return when (group) {
            SettingsGroup.ACCOUNT ->
                live.serverName.ifBlank { hostOf(live.baseUrl) }.ifBlank { "未配置实例" } +
                    if (live.username.isNotBlank()) " · ${live.username}" else " · 未登录"
            SettingsGroup.NETWORK -> buildList {
                if (live.lowDataMode) add("低流量")
                if (live.wifiOnlyImages) add("仅 Wi-Fi 图片")
                if (live.wifiOnlyMedia) add("仅 Wi-Fi 媒体")
                if (live.slowInstanceMode) add("慢速兼容")
                if (isEmpty()) add("网络策略与缓存刷新")
            }.joinToString(" · ")
            SettingsGroup.STORAGE -> {
                val bytes = (storage?.pageBytes ?: 0L) + (storage?.imageBytes ?: 0L)
                "缓存 ${formatBytes(bytes)} · ${if (live.autoCleanCache) "自动清理" else "手动清理"}"
            }
            SettingsGroup.APPEARANCE -> buildList {
                add("${(live.textScale * 100).toInt()}% 字号")
                add(
                    when (live.themeMode) {
                        "dark" -> "深色"
                        "light" -> "浅色"
                        else -> "跟随系统"
                    }
                )
                if (live.amoledBlack) add("纯黑")
                if (live.reduceMotion) add("减少动画")
            }.joinToString(" · ")
            SettingsGroup.CONTENT -> buildList {
                add(
                    when (live.defaultImageQualityMode) {
                        "original" -> "原图"
                        "custom" -> "自定义 ${live.defaultImageQuality}%"
                        else -> "压缩"
                    }
                )
                add(
                    when (live.defaultImageFormat) {
                        "jpeg" -> "JPG"
                        "png" -> "PNG"
                        "auto" -> "原格式"
                        else -> "WebP"
                    }
                )
                add(
                    when (live.defaultLayout) {
                        "grid" -> "网格"
                        "horizontal" -> "横向"
                        "carousel" -> "轮播"
                        "stack" -> "堆叠"
                        "none" -> "无图"
                        else -> "瀑布流"
                    }
                )
                add(if (live.commentSort == "newest") "最新评论" else "最早评论")
            }.joinToString(" · ")
            SettingsGroup.ADVANCED ->
                "v${BuildConfig.VERSION_NAME} · " +
                    if (live.updateCheckIntervalHours == 0) "手动更新" else "自动更新"
        }
    }

    LaunchedEffect(searchMode) {
        if (searchMode) {
            delay(80)
            runCatching { searchFocus.requestFocus() }
        }
    }
    BackHandler(enabled = selectedGroup != null || searchMode) {
        backFromSettings()
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(
            title = when {
                searchMode -> "搜索设置"
                selectedGroup != null -> selectedGroup!!.title
                else -> "设置"
            },
            onClose = { backFromSettings() },
            actions = {
                if (!searchMode && selectedGroup == null) {
                    IconButton(onClick = {
                        settingsQuery = ""
                        searchMode = true
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索设置")
                    }
                }
            }
        )
        val currentPage = when {
            searchMode -> SettingsPage.Search
            selectedGroup != null -> SettingsPage.Group(selectedGroup!!)
            else -> SettingsPage.Home
        }
        val reduceMotion = LocalReduceMotion.current
        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    val forward = targetState !is SettingsPage.Home
                    val enter = slideInHorizontally(
                        tween(300, easing = M3Easing.EmphasizedDecelerate)
                    ) { if (forward) it else -it / 4 } + fadeIn(tween(110))
                    val exit = slideOutHorizontally(
                        tween(220, easing = M3Easing.EmphasizedAccelerate)
                    ) { if (forward) -it / 4 else it } + fadeOut(tween(90))
                    enter togetherWith exit
                }
            },
            label = "settingsPage"
        ) { page ->
            val pageScroll = when (page) {
                SettingsPage.Home -> homeScroll
                SettingsPage.Search -> rememberScrollState()
                is SettingsPage.Group -> rememberScrollState()
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(pageScroll)
                    .padding(ContentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            if (page is SettingsPage.Home) {
                Text(
                    "设置分类",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
                SettingsGroup.entries
                    .filter { it != SettingsGroup.ACCOUNT }
                    .forEach { group ->
                    val icon = when (group) {
                        SettingsGroup.ACCOUNT -> Icons.Filled.Person
                        SettingsGroup.NETWORK -> Icons.Filled.Wifi
                        SettingsGroup.STORAGE -> Icons.Filled.Storage
                        SettingsGroup.APPEARANCE -> Icons.Filled.Palette
                        SettingsGroup.CONTENT -> Icons.Filled.EditNote
                        SettingsGroup.ADVANCED -> Icons.Filled.Settings
                    }
                    Card(
                        onClick = {
                            selectedGroup = group
                            settingsQuery = ""
                            searchMode = false
                        },
                        shape = MaterialTheme.shapes.largeIncreased,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    group.title,
                                    style = MaterialTheme.typography.titleSmallEmphasized
                                )
                                Text(
                                    groupSummary(group),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                if (page is SettingsPage.Search) {
                    OutlinedTextField(
                        value = settingsQuery,
                        onValueChange = { settingsQuery = it },
                        placeholder = { Text("搜索设置：缓存、字体、更新、实例…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (settingsQuery.isNotEmpty()) {
                                IconButton(onClick = { settingsQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "清除搜索")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocus)
                    )
                }

                if (page is SettingsPage.Search && settingsQuery.isBlank()) {
                    EmptyStateCard(
                        title = "搜索设置",
                        hint = "输入缓存、字体、更新、实例等关键词",
                        icon = Icons.Filled.Search
                    )
                } else {

            if (groupVisible(SettingsGroup.ACCOUNT, page)) {
                if (page is SettingsPage.Search) SettingsGroupHeader(SettingsGroup.ACCOUNT.title)
                SettingSection("实例") {
                    val live = Ech0Store.current
                    if (live.username.isNotBlank() || live.avatarUrl.isNotBlank()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(
                                name = live.username.ifBlank { "E" },
                                size = 40.dp,
                                url = live.avatarUrl.takeIf { it.isNotBlank() }
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    live.username.ifBlank { "当前账号" },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    if (live.isAdmin) "管理员" else "普通用户",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Field("实例地址（如 https://echo.example.com）", baseUrl) { baseUrl = it }
                    StorageRow(
                        "当前实例",
                        live.serverName.ifBlank { hostOf(live.baseUrl) }.ifBlank { "未配置" }
                    )
                    StorageRow(
                        "当前账号",
                        when {
                            live.username.isNotBlank() ->
                                live.username + if (live.isAdmin) " · 管理员" else ""
                            live.accessToken != null -> "已登录，账号信息待刷新"
                            else -> "未登录"
                        }
                    )
                    StorageRow(
                        "登录有效期",
                        when {
                            live.accessToken == null -> "未登录"
                            live.tokenExpiresAt <= 0 -> "长期有效"
                            live.tokenExpiresAt * 1000 < System.currentTimeMillis() -> "已过期"
                            else -> {
                                val days = ((live.tokenExpiresAt * 1000 - System.currentTimeMillis()) /
                                    86_400_000L).coerceAtLeast(1)
                                "剩余 $days 天"
                            }
                        }
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            version?.let { "Ech0 $it" } ?: "尚未连接",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(onClick = { requestSaveBaseUrl() }) {
                            Text("保存并切换")
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (live.accessToken != null) {
                            TextButton(onClick = { showLogoutConfirm = true }) {
                                Text("退出登录", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    Ech0Store.clearCookies()
                                    message = "Cookie 已清除"
                                }
                            }
                        ) {
                            Text("清除 Cookie", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                SettingSection("已保存实例") {
                    if (savedInstances.isEmpty()) {
                        Text(
                            "暂无已保存实例",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    savedInstances.forEach { instance ->
                        val isCurrent = instance.baseUrl.trimEnd('/') ==
                            Ech0Store.current.baseUrl.trimEnd('/')
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    instance.name.ifBlank { hostOf(instance.baseUrl) },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    instance.baseUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isCurrent) {
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                TextButton(onClick = {
                                    baseUrl = instance.baseUrl
                                    requestSaveBaseUrl()
                                }) { Text("切换", style = MaterialTheme.typography.labelSmall) }
                                TextButton(onClick = {
                                    scope.launch {
                                        Ech0Store.removeSavedInstance(instance.baseUrl)
                                        instancesRevision++
                                    }
                                }) { Text("删除", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    Text(
                        "只保存地址和显示名，不保存 Token；切换回旧实例需要重新登录。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (groupVisible(SettingsGroup.NETWORK, page)) {
                if (page is SettingsPage.Search) SettingsGroupHeader(SettingsGroup.NETWORK.title)
                SettingSection("网络") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("测试连接", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "检查实例连通性、接口响应和延迟",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { checkNetwork() }, enabled = !netChecking) {
                            if (netChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (netStatus == null) "开始测试" else "重新测试")
                        }
                    }
                    netStatus?.let { text ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = when (netOk) {
                                true -> MaterialTheme.colorScheme.secondaryContainer
                                false -> MaterialTheme.colorScheme.errorContainer
                                null -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text,
                                style = MaterialTheme.typography.labelMedium,
                                color = when (netOk) {
                                    true -> MaterialTheme.colorScheme.onSecondaryContainer
                                    false -> MaterialTheme.colorScheme.onErrorContainer
                                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    Text(
                        "测试只读取目标地址，不会保存设置或清除登录状态。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    SettingSwitchRow(
                        title = "慢速实例兼容",
                        subtitle = "把本站 API 读超时放宽到 90 秒，适合自建小主机；上传不受影响",
                        checked = state.slowInstanceMode
                    ) { enabled ->
                        scope.launch { Ech0Store.setSlowInstanceMode(enabled) }
                    }
                    SettingSwitchRow(
                        title = "仅 Wi-Fi 加载图片",
                        subtitle = "移动网络下已缓存的图片仍可显示",
                        checked = state.wifiOnlyImages
                    ) { enabled ->
                        scope.launch { Ech0Store.setWifiOnlyImages(enabled) }
                    }
                    SettingSwitchRow(
                        title = "仅 Wi-Fi 播放媒体",
                        subtitle = "音乐内嵌播放和视频自动播放",
                        checked = state.wifiOnlyMedia
                    ) { enabled ->
                        scope.launch { Ech0Store.setWifiOnlyMedia(enabled) }
                    }
                    SettingSwitchRow(
                        title = "仅 Wi-Fi 上传",
                        subtitle = "移动网络下选图后不会自动上传",
                        checked = state.wifiOnlyUploads
                    ) { enabled ->
                        scope.launch { Ech0Store.setWifiOnlyUploads(enabled) }
                    }
                    SettingSwitchRow(
                        title = "预加载下一页",
                        subtitle = "关闭后滚动到最后一条才加载",
                        checked = state.preloadNextPage
                    ) { enabled ->
                        scope.launch { Ech0Store.setPreloadNextPage(enabled) }
                    }
                    Text("信息流缓存间隔", style = MaterialTheme.typography.bodyMedium)
                    val ttlOptions = listOf(3 to "3 分钟", 10 to "10 分钟", 30 to "30 分钟")
                    Ech0ChoiceGroup(
                        options = ttlOptions,
                        selected = state.feedCacheTtlMinutes,
                        onSelect = { minutes ->
                            scope.launch { Ech0Store.setFeedCacheTtlMinutes(minutes) }
                        }
                    )
                    FilledTonalButton(
                        onClick = { runNetworkDiagnostics() },
                        enabled = !netDiagRunning
                    ) {
                        if (netDiagRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("网络诊断")
                    }
                }

                SettingSection("流量") {
                    SettingSwitchRow(
                        title = "低流量模式",
                        subtitle = "省流总开关；下面可以单独控制头像、正文图片和扩展卡片",
                        checked = lowDataMode
                    ) { enabled ->
                        lowDataMode = enabled
                        scope.launch { Ech0Store.setLowDataMode(enabled) }
                    }
                    if (lowDataMode) {
                        HorizontalDivider()
                        SettingSwitchRow(
                            title = "不加载头像",
                            checked = state.lowDataAvatars
                        ) { enabled ->
                            scope.launch { Ech0Store.setLowDataAvatars(enabled) }
                        }
                        SettingSwitchRow(
                            title = "不加载正文图片",
                            checked = state.lowDataImages
                        ) { enabled ->
                            scope.launch { Ech0Store.setLowDataImages(enabled) }
                        }
                        SettingSwitchRow(
                            title = "不加载扩展卡封面",
                            checked = state.lowDataExtensionCovers
                        ) { enabled ->
                            scope.launch { Ech0Store.setLowDataExtensionCovers(enabled) }
                        }
                        SettingSwitchRow(
                            title = "不预取扩展卡信息",
                            subtitle = "GitHub / B 站 / 推文 / 音乐，点开或播放时再取",
                            checked = state.lowDataExtensionMeta
                        ) { enabled ->
                            scope.launch { Ech0Store.setLowDataExtensionMeta(enabled) }
                        }
                    }
                }
            }

            if (groupVisible(SettingsGroup.STORAGE, page)) {
                if (page is SettingsPage.Search) SettingsGroupHeader(SettingsGroup.STORAGE.title)

            SettingSection("存储") {
                val stats = storage
                if (stats != null) {
                    StorageRow("页面缓存", formatBytes(stats.pageBytes))
                    StorageRow("图片缓存", formatBytes(stats.imageBytes))
                    StorageRow("临时文件", formatBytes(stats.tempBytes))
                    StorageRow("草稿暂存", formatBytes(stats.draftBytes))
                    StorageRow("应用数据", formatBytes(stats.appBytes))
                    StorageRow(
                        "上次刷新",
                        if (stats.lastRefreshAt > 0) {
                            formatDateTime(stats.lastRefreshAt / 1000)
                        } else {
                            "暂无"
                        }
                    )
                    StorageRow(
                        "可用空间",
                        "${formatBytes(stats.freeBytes)} / ${formatBytes(stats.totalBytes)}"
                    )
                } else {
                    Text(
                        "正在统计…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(
                    maxItemsInEachRow = 3,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { refreshStorage() }, enabled = !storageBusy) {
                        Text("刷新", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(
                        onClick = {
                            runStorageTask("页面缓存已清理") { DiskCache.clearAll(context) }
                        },
                        enabled = !storageBusy
                    ) { Text("清理页面", style = MaterialTheme.typography.labelLarge) }
                    TextButton(
                        onClick = {
                            runStorageTask("图片缓存已清理") {
                                context.imageLoader.memoryCache?.clear()
                                runCatching { context.imageLoader.diskCache?.clear() }
                            }
                        },
                        enabled = !storageBusy
                    ) { Text("清理图片", style = MaterialTheme.typography.labelLarge) }
                    TextButton(
                        onClick = {
                            runStorageTask("临时文件已清理") {
                                context.cacheDir.listFiles()
                                    ?.filter { it.name != "image_cache" }
                                    ?.forEach { runCatching { it.deleteRecursively() } }
                            }
                        },
                        enabled = !storageBusy
                    ) { Text("清理临时", style = MaterialTheme.typography.labelLarge) }
                    TextButton(
                        onClick = {
                            runStorageTask("未被草稿引用的暂存图已清理") {
                                PublishStore.cleanupOrphanDraftFiles(context)
                            }
                        },
                        enabled = !storageBusy
                    ) { Text("清理草稿", style = MaterialTheme.typography.labelLarge) }
                }
                Text(
                    "清理页面缓存不会退出登录；清理草稿只删除未被草稿引用的暂存图。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Text("图片磁盘缓存上限", style = MaterialTheme.typography.bodyMedium)
                val cacheOptions = listOf(150 to "150 MB", 300 to "300 MB", 500 to "500 MB")
                Ech0ChoiceGroup(
                    options = cacheOptions,
                    selected = state.imageCacheMaxMb,
                    onSelect = { mb ->
                        scope.launch { Ech0Store.setImageCacheMaxMb(mb) }
                    }
                )
                Text(
                    "修改后下次启动应用生效。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingSwitchRow(
                    title = "自动清理缓存",
                    subtitle = "可用空间低于 500 MB 时自动清理图片和页面缓存",
                    checked = state.autoCleanCache
                ) { enabled ->
                    scope.launch { Ech0Store.setAutoCleanCache(enabled) }
                }
            }
            }

            if (groupVisible(SettingsGroup.APPEARANCE, page)) {
                if (page is SettingsPage.Search) SettingsGroupHeader(SettingsGroup.APPEARANCE.title)

            SettingSection("阅读") {
                Text(
                    "正文字号 · ${(textScale * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = textScale,
                    onValueChange = { textScale = it },
                    onValueChangeFinished = {
                        scope.launch { Ech0Store.setTextScale(textScale) }
                    },
                    valueRange = 0.85f..1.3f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "预览：今天也在认真记录生活。字号调整会同步影响时间线、详情和评论。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("行距", style = MaterialTheme.typography.bodyMedium)
                val lineOptions = listOf(0.9f to "紧凑", 1f to "标准", 1.2f to "宽松")
                Ech0ChoiceGroup(
                    options = lineOptions,
                    selected = state.lineHeightScale,
                    isSelected = { scale ->
                        kotlin.math.abs(state.lineHeightScale - scale) < 0.01f
                    },
                    onSelect = { scale ->
                        scope.launch { Ech0Store.setLineHeightScale(scale) }
                    }
                )
                Text("链接打开方式", style = MaterialTheme.typography.bodyMedium)
                val linkOptions = listOf("inApp" to "应用内", "system" to "系统浏览器")
                Ech0ChoiceGroup(
                    options = linkOptions,
                    selected = state.linkOpenMode,
                    onSelect = { mode ->
                        scope.launch { Ech0Store.setLinkOpenMode(mode) }
                    }
                )
                Text("信息流图片质量", style = MaterialTheme.typography.bodyMedium)
                val imageOptions = listOf("standard" to "标准", "high" to "高清", "original" to "原图")
                Ech0ChoiceGroup(
                    options = imageOptions,
                    selected = state.feedImageQuality,
                    onSelect = { quality ->
                        scope.launch { Ech0Store.setFeedImageQuality(quality) }
                    }
                )
                Text("代码字体", style = MaterialTheme.typography.bodyMedium)
                val codeOptions = listOf("monospace" to "等宽", "serif" to "衬线")
                Ech0ChoiceGroup(
                    options = codeOptions,
                    selected = state.codeFont,
                    onSelect = { font ->
                        scope.launch { Ech0Store.setCodeFont(font) }
                    }
                )
                SettingSwitchRow(
                    title = "自动播放 GIF",
                    subtitle = "关闭后只显示第一帧；修改后下次启动生效",
                    checked = state.autoPlayGif
                ) { enabled ->
                    scope.launch { Ech0Store.setAutoPlayGif(enabled) }
                }
                SettingSwitchRow(
                    title = "自动播放视频",
                    subtitle = "B 站 / YouTube 卡片自动展开内嵌播放器；受仅 Wi-Fi 播放限制",
                    checked = state.autoPlayVideo
                ) { enabled ->
                    scope.launch { Ech0Store.setAutoPlayVideo(enabled) }
                }
                SettingSwitchRow(
                    title = "显示相对时间",
                    subtitle = "关闭后显示完整日期时间",
                    checked = state.showRelativeTime
                ) { enabled ->
                    scope.launch { Ech0Store.setShowRelativeTime(enabled) }
                }
            }

            SettingSection("外观") {
                val options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
                Ech0ChoiceGroup(
                    options = options,
                    selected = themeMode,
                    onSelect = { mode ->
                        themeMode = mode
                        scope.launch { Ech0Store.setThemeMode(mode) }
                    }
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("动态取色", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "使用系统壁纸的颜色（Android 12+）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = dynamicColor,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        onCheckedChange = {
                            dynamicColor = it
                            scope.launch { Ech0Store.setDynamicColor(it) }
                        }
                    )
                }

                SettingSwitchRow(
                    title = "纯黑模式（AMOLED）",
                    subtitle = "深色模式下使用纯黑背景，更省电",
                    checked = state.amoledBlack
                ) { enabled ->
                    scope.launch { Ech0Store.setAmoledBlack(enabled) }
                }
                SettingSwitchRow(
                    title = "高对比度",
                    subtitle = "增强文字、边框与背景的对比",
                    checked = state.highContrast
                ) { enabled ->
                    scope.launch { Ech0Store.setHighContrast(enabled) }
                }
                SettingSwitchRow(
                    title = "减少动画",
                    subtitle = "关闭页面切换、缩放和弹簧动效",
                    checked = state.reduceMotion
                ) { enabled ->
                    scope.launch { Ech0Store.setReduceMotion(enabled) }
                }
                Text("状态栏图标", style = MaterialTheme.typography.bodyMedium)
                val statusOptions = listOf("auto" to "自动", "light" to "浅色", "dark" to "深色")
                Ech0ChoiceGroup(
                    options = statusOptions,
                    selected = state.statusBarIconMode,
                    onSelect = { mode ->
                        scope.launch { Ech0Store.setStatusBarIconMode(mode) }
                    }
                )

                if (!dynamicColor) {
                    Text("主题色", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentPresets.forEach { (seed, label) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        accentSeed = seed
                                        scope.launch { Ech0Store.setAccentSeed(seed) }
                                    }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(seed))
                                )
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ColorWheelPicker(
                            color = Color(accentSeed),
                            onColorChange = {
                                accentSeed = it.toArgb().toLong() and 0xFFFFFFFFL
                            }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "拖动色盘 · #%06X".format(accentSeed and 0xFFFFFFL),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { accentSeed = 0xFF0B57D0 }) {
                            Text("恢复默认", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // 拖动时即时预览，停止 180ms 后再落盘，避免高频写 DataStore
                    LaunchedEffect(accentSeed) {
                        kotlinx.coroutines.delay(180)
                        if (accentSeed != Ech0Store.current.accentSeed) {
                            Ech0Store.setAccentSeed(accentSeed)
                        }
                    }
                }

                Text("背景", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BackgroundPresets.forEach { (id, label) ->
                        val selected = backgroundStyle == id
                        Column(
                            Modifier
                                .width(74.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    backgroundStyle = id
                                    scope.launch { Ech0Store.setBackgroundStyle(id) }
                                }
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(appBackgroundBrush(id))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        shape = MaterialTheme.shapes.medium
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自定义图片", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "选择照片后可缩放、拖动剪裁",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            backgroundPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Text(if (backgroundImage.isBlank()) "选择图片" else "更换图片")
                    }
                }
                if (backgroundImage.isNotBlank()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = File(backgroundImage),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 56.dp, height = 84.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    backgroundStyle = "custom"
                                    scope.launch { Ech0Store.setBackgroundStyle("custom") }
                                }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (backgroundStyle == "custom") "当前使用中" else "点击缩略图启用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                TextButton(onClick = {
                                    backgroundStyle = "custom"
                                    scope.launch { Ech0Store.setBackgroundStyle("custom") }
                                }) { Text("使用", style = MaterialTheme.typography.labelSmall) }
                                TextButton(onClick = {
                                    cropUri = Uri.fromFile(File(backgroundImage))
                                }) { Text("重新剪裁", style = MaterialTheme.typography.labelSmall) }
                                TextButton(onClick = {
                                    runCatching { File(backgroundImage).delete() }
                                    backgroundImage = ""
                                    if (backgroundStyle == "custom") {
                                        backgroundStyle = "default"
                                        scope.launch { Ech0Store.setBackgroundStyle("default") }
                                    }
                                    scope.launch { Ech0Store.setBackgroundImage("") }
                                }) { Text("删除", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("背景模糊", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    "让自定义背景更柔和"
                                } else {
                                    "Android 12 以下系统不支持背景模糊，开关不可用"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = backgroundBlur,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onCheckedChange = {
                                backgroundBlur = it
                                scope.launch { Ech0Store.setBackgroundBlur(it) }
                            }
                        )
                    }
                }
                Text(
                    "背景暗化 · ${(backgroundDim * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = backgroundDim,
                    onValueChange = { backgroundDim = it },
                    valueRange = 0f..0.6f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "调整背景图或渐变底色的暗度，不影响卡片和正文。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }

            if (groupVisible(SettingsGroup.CONTENT, page)) {
                if (page is SettingsPage.Search) SettingsGroupHeader(SettingsGroup.CONTENT.title)

            SettingSection("编辑") {
                Text("默认图片画质", style = MaterialTheme.typography.bodyMedium)
                Ech0ChoiceGroup(
                    options = listOf(
                        "compressed" to "压缩",
                        "original" to "原图",
                        "custom" to "自定义"
                    ),
                    selected = imageQualityMode,
                    onSelect = { mode ->
                        val value = when (mode) {
                            "compressed" -> 30
                            "original" -> 0
                            else -> customImageQuality.coerceIn(1, 100)
                        }
                        imageQualityMode = mode
                        imageQuality = value
                        scope.launch { Ech0Store.setDefaultImageQuality(mode, value) }
                    }
                )
                if (imageQualityMode == "custom") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "压缩比例",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$imageQuality%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Slider(
                        value = imageQuality.toFloat(),
                        onValueChange = {
                            imageQuality = it.roundToInt().coerceIn(1, 100)
                            customImageQuality = imageQuality
                        },
                        onValueChangeFinished = {
                            scope.launch {
                                Ech0Store.setDefaultImageQuality("custom", imageQuality)
                            }
                        },
                        valueRange = 1f..100f
                    )
                }
                Text(
                    when (imageQualityMode) {
                        "compressed" -> "发布时压缩到 30% 质量。"
                        "original" -> "原图不压缩；开启移除 EXIF 时会重新编码。"
                        else -> "发布时按自定义比例压缩。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("默认图片格式", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "webp" to "WebP",
                        "jpeg" to "JPG",
                        "png" to "PNG",
                        "auto" to "原格式"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = imageFormat == value,
                            onClick = {
                                imageFormat = value
                                scope.launch { Ech0Store.setDefaultImageFormat(value) }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    "WebP 体积小；JPG 兼容性好；PNG 无损但更大；原格式尽量保留源文件格式。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("默认图片布局", style = MaterialTheme.typography.bodyMedium)
                val layoutOptions = listOf(
                    "waterfall" to "瀑布流",
                    "grid" to "网格",
                    "horizontal" to "横向",
                    "carousel" to "轮播",
                    "stack" to "堆叠",
                    "none" to "无"
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    layoutOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = state.defaultLayout == value,
                            onClick = {
                                scope.launch { Ech0Store.setDefaultLayout(value) }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                SettingSwitchRow(
                    title = "默认私密",
                    subtitle = "新说说默认仅自己可见",
                    checked = state.defaultPrivate
                ) { enabled ->
                    scope.launch { Ech0Store.setDefaultPrivate(enabled) }
                }
                OutlinedTextField(
                    value = defaultTagsInput,
                    onValueChange = { if (it.length <= 200) defaultTagsInput = it },
                    label = { Text("默认标签（逗号分隔，最多 5 个）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    scope.launch {
                        Ech0Store.setDefaultTags(
                            defaultTagsInput.split(',', '，', ' ')
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                        )
                        message = "默认标签已保存"
                    }
                }) { Text("保存默认标签", style = MaterialTheme.typography.labelLarge) }
                Text("同时上传数量", style = MaterialTheme.typography.bodyMedium)
                val concurrencyOptions = listOf(1 to "1", 2 to "2", 3 to "3")
                Ech0ChoiceGroup(
                    options = concurrencyOptions,
                    selected = state.uploadConcurrency,
                    onSelect = { value ->
                        scope.launch { Ech0Store.setUploadConcurrency(value) }
                    }
                )
                SettingSwitchRow(
                    title = "上传前移除 EXIF",
                    subtitle = "原图模式会以高质量重新编码，去除拍摄位置等信息",
                    checked = state.stripExif
                ) { enabled ->
                    scope.launch { Ech0Store.setStripExif(enabled) }
                }
                Text("上传失败重试", style = MaterialTheme.typography.bodyMedium)
                val retryOptions = listOf(0 to "不重试", 1 to "1 次", 2 to "2 次")
                Ech0ChoiceGroup(
                    options = retryOptions,
                    selected = state.uploadRetryCount,
                    onSelect = { value ->
                        scope.launch { Ech0Store.setUploadRetryCount(value) }
                    }
                )
                SettingSwitchRow(
                    title = "发布前确认",
                    subtitle = "点击发表后再确认一次，避免误触",
                    checked = state.publishConfirm
                ) { enabled ->
                    scope.launch { Ech0Store.setPublishConfirm(enabled) }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动保存草稿", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (promptSaveDraft) {
                                "关闭编辑器时会先询问是否保存"
                            } else {
                                "关闭编辑器时直接存入草稿箱，不再弹窗"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = !promptSaveDraft,
                        onCheckedChange = { auto ->
                            // 存储字段历史上叫“询问草稿”，这里对用户暴露为“自动保存”，逻辑取反
                            promptSaveDraft = !auto
                            scope.launch { Ech0Store.setPromptSaveDraft(!auto) }
                        }
                    )
                }
            }

            SettingSection("评论身份") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动填写默认身份", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "评论时直接用下面的昵称、邮箱和网站",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = commentUseDefault,
                        onCheckedChange = { checked ->
                            commentUseDefault = checked
                            scope.launch {
                                Ech0Store.setCommentIdentity(
                                    nickname = commentNickname.trim(),
                                    email = commentEmail.trim(),
                                    website = commentWebsite.trim(),
                                    useDefault = checked
                                )
                            }
                        }
                    )
                }
                if (commentUseDefault) {
                    OutlinedTextField(
                        value = commentNickname,
                        onValueChange = { if (it.length <= 60) commentNickname = it },
                        label = { Text("昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = commentEmail,
                        onValueChange = { if (it.length <= 120) commentEmail = it },
                        label = { Text("邮箱（必填，不会公开）") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = commentWebsite,
                        onValueChange = { if (it.length <= 200) commentWebsite = it },
                        label = { Text("网站（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                Ech0Store.setCommentIdentity(
                                    nickname = commentNickname.trim(),
                                    email = commentEmail.trim(),
                                    website = commentWebsite.trim(),
                                    useDefault = true
                                )
                                message = "评论身份已保存"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("保存默认身份") }
                }
                Text(
                    "身份按当前实例和账号保存；切换到其他实例不会复用这里填写的邮箱。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Text("评论排序", style = MaterialTheme.typography.bodyMedium)
                val sortOptions = listOf("oldest" to "最早", "newest" to "最新")
                Ech0ChoiceGroup(
                    options = sortOptions,
                    selected = state.commentSort,
                    onSelect = { value ->
                        scope.launch { Ech0Store.setCommentSort(value) }
                    }
                )
                SettingSwitchRow(
                    title = "发送评论前确认",
                    subtitle = "点击发送后再确认一次",
                    checked = state.commentConfirm
                ) { enabled ->
                    scope.launch { Ech0Store.setCommentConfirm(enabled) }
                }
                TextButton(onClick = {
                    scope.launch {
                        Ech0Store.clearCommentDrafts()
                        message = "评论草稿已清除"
                    }
                }) { Text("清除评论草稿", style = MaterialTheme.typography.labelLarge) }
            }
            }

            if (groupVisible(SettingsGroup.ADVANCED, page)) {
                if (page is SettingsPage.Search) SettingsGroupHeader(SettingsGroup.ADVANCED.title)

            SettingSection("高级 · Access Token 登录") {
                Field("粘贴 Access Token（可选）", tokenInput, ImeAction.Done) { tokenInput = it }
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            if (tokenInput.isBlank()) {
                                message = "请先粘贴 Token"
                                return@launch
                            }
                            Ech0Store.setToken(tokenInput.trim(), System.currentTimeMillis() / 1000 + 365 * 86400 * 10)
                            runCatching {
                                val u = SiteStore.currentUser(force = true)
                                Ech0Store.setUser(u.username, u.is_admin, u.avatar.orEmpty())
                                message = "Token 有效 · ${u.username}${if (u.is_admin) "（管理员）" else ""}"
                            }.rethrowCancellationLike().onFailure { message = it.userMessage() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("验证并保存")
                }
            }

            SettingSection("请求与网络") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("请求诊断", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "最近 20 条请求的方法、状态、耗时；不记录 Token 和请求体",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        requestRecords = RequestDiagnostics.recent()
                        showRequestDiagnostics = true
                    }) { Text("查看") }
                }
                OutlinedTextField(
                    value = uaInput,
                    onValueChange = { if (it.length <= 300) uaInput = it },
                    label = { Text("自定义 User-Agent（留空用默认）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    scope.launch {
                        Ech0Store.setCustomUserAgent(uaInput)
                        message = "User-Agent 已保存"
                    }
                }) { Text("保存 User-Agent", style = MaterialTheme.typography.labelLarge) }
                OutlinedTextField(
                    value = proxyInput,
                    onValueChange = { if (it.length <= 120) proxyInput = it },
                    label = { Text("HTTP 代理（host:port，留空关闭）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    if (!top.saltfish.ech0.data.net.HttpClients.isValidProxy(proxyInput)) {
                        message = "代理格式应为 host:port"
                        return@TextButton
                    }
                    scope.launch {
                        Ech0Store.setProxyUrl(proxyInput)
                        message = if (proxyInput.isBlank()) "代理已关闭" else "代理已保存，新连接立即生效"
                    }
                }) { Text("保存代理", style = MaterialTheme.typography.labelLarge) }
                Text(
                    "代理只影响网络连接，不改变实例地址；错误代理可能导致请求失败。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingSection("数据与维护") {
                FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = {
                        exportSettingsLauncher.launch(
                            "ech0-settings-${System.currentTimeMillis()}.json"
                        )
                    }) { Text("导出设置", style = MaterialTheme.typography.labelLarge) }
                    TextButton(onClick = {
                        importSettingsLauncher.launch(arrayOf("application/json", "text/*"))
                    }) { Text("导入设置", style = MaterialTheme.typography.labelLarge) }
                    TextButton(onClick = { showResetConfirm = true }) {
                        Text("重置设置", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = { showLogoutConfirm = true }) {
                        Text("清除登录", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    "导出不包含 Token、Cookie、草稿、队列和评论邮箱；重置设置会保留登录、草稿和缓存。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingSection("关于") {
                SettingSwitchRow(
                    title = "自动检查更新",
                    subtitle = "按下方间隔检查 GitHub Release；只提示，不会自动安装",
                    checked = autoCheckUpdate
                ) { enabled ->
                    autoCheckUpdate = enabled
                    scope.launch { Ech0Store.setAutoCheckAppUpdate(enabled) }
                }
                Text("检查间隔", style = MaterialTheme.typography.bodyMedium)
                val intervalOptions = listOf(24 to "24 小时", 168 to "7 天", 0 to "手动")
                Ech0ChoiceGroup(
                    options = intervalOptions,
                    selected = state.updateCheckIntervalHours,
                    onSelect = { hours ->
                        scope.launch { Ech0Store.setUpdateCheckIntervalHours(hours) }
                    }
                )
                FilledTonalButton(
                    onClick = { checkUpdateNow() },
                    enabled = !updateChecking
                ) {
                    if (updateChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("立即检查更新")
                }
                StorageRow("当前版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                StorageRow(
                    "当前实例",
                    state.serverName.ifBlank { hostOf(state.baseUrl) }.ifBlank { "未配置" }
                )
                Text(
                    "升级会保留已有设置；切换实例只影响该实例相关的登录状态和缓存。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
            }
            }

            if (settingsQuery.isNotBlank() &&
                SettingsGroup.entries.none { it.matches(settingsQuery) }
            ) {
                EmptyStateCard(
                    title = "没有匹配的设置",
                    hint = "试试“缓存”“字体”“更新”或“实例”",
                    icon = Icons.Filled.Search
                )
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(120.dp))
            }
        }
    }

    cropUri?.let { uri ->
        BackgroundCropDialog(
            uri = uri,
            aspectRatio = screenAspect,
            onDone = { path ->
                backgroundImage = path
                backgroundStyle = "custom"
                scope.launch {
                    Ech0Store.setBackgroundImage(path)
                    Ech0Store.setBackgroundStyle("custom")
                }
                cropUri = null
            },
            onDismiss = { cropUri = null }
        )
    }

    pendingBaseUrl?.let { target ->
        val oldLabel = hostOf(Ech0Store.current.baseUrl)
            .ifBlank { Ech0Store.current.baseUrl.ifBlank { "当前实例" } }
        val newLabel = hostOf(target).ifBlank { target }
        AlertDialog(
            onDismissRequest = { pendingBaseUrl = null },
            title = { Text("切换实例？") },
            text = {
                Text(
                    "即将从 $oldLabel 切换到 $newLabel。\n\n" +
                        "当前登录状态、Cookie 和页面缓存会被清除；" +
                        "草稿和发布队列会保留，但需要切回旧实例才能发布。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingBaseUrl = null
                    baseUrl = target
                    saveBaseUrl(target)
                }) { Text("切换") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBaseUrl = null }) { Text("取消") }
            }
        )
    }

    if (showNetDiagnostics) {
        AlertDialog(
            onDismissRequest = {
                if (!netDiagRunning) showNetDiagnostics = false
            },
            title = { Text("网络诊断") },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (netDiagRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在诊断…")
                        }
                    }
                    netDiagResults.forEach { result ->
                        Column {
                            Text(
                                result.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (result.ok) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                            Text(
                                result.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (netDiagResults.isEmpty() && !netDiagRunning) {
                        Text("暂无结果", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { runNetworkDiagnostics() }, enabled = !netDiagRunning) {
                    Text("重新诊断")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNetDiagnostics = false },
                    enabled = !netDiagRunning
                ) { Text("关闭") }
            }
        )
    }

    if (showRequestDiagnostics) {
        AlertDialog(
            onDismissRequest = { showRequestDiagnostics = false },
            title = { Text("请求诊断") },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "最近 ${requestRecords.size} 条请求 · 约 ${formatBytes(requestRecords.sumOf { it.bytes.toLong() })}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (requestRecords.isEmpty()) {
                        Text("还没有请求记录", style = MaterialTheme.typography.bodySmall)
                    }
                    requestRecords.forEach { record ->
                        Column {
                            Text(
                                "${record.method} ${record.host}${record.path}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                buildString {
                                    append(if (record.status == 0) "失败" else "HTTP ${record.status}")
                                    append(" · ${record.durationMs} ms")
                                    if (record.status == 0 && !record.error.isNullOrBlank()) {
                                        append(" · ${record.error}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (record.status in 200..299)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    RequestDiagnostics.clear()
                    requestRecords = emptyList()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showRequestDiagnostics = false }) { Text("关闭") }
            }
        )
    }

    updateRelease?.let { release ->
        AlertDialog(
            onDismissRequest = { updateRelease = null },
            title = { Text("发现新版本 ${release.versionName}") },
            text = {
                Text(
                    release.notes.take(600).ifBlank {
                        "当前版本 ${BuildConfig.VERSION_NAME}，可前往 GitHub 查看新版本。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (release.htmlUrl.isNotBlank()) uriHandler.openUri(release.htmlUrl)
                    updateRelease = null
                }) { Text("查看") }
            },
            dismissButton = {
                TextButton(onClick = { updateRelease = null }) { Text("稍后") }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置所有设置？") },
            text = {
                Text("外观、阅读、网络、编辑和评论设置会恢复默认；登录状态、草稿、队列和缓存会保留。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    scope.launch {
                        Ech0Store.resetSettings()
                        message = "设置已重置"
                        instancesRevision++
                        settingsRevision++
                    }
                }) { Text("重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(if (state.accessToken != null) "退出登录？" else "清除登录状态？") },
            text = {
                Text("会清除 Token、Cookie、点赞状态和当前实例的页面缓存；草稿与发布队列保留。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    scope.launch {
                        runCatching { Ech0Api.logout() }
                        message = "已退出登录"
                        instancesRevision++
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            }
        )
    }
}

// ---------- 设置辅助 ----------

private data class StorageStats(
    val pageBytes: Long,
    val imageBytes: Long,
    val tempBytes: Long,
    val draftBytes: Long,
    val appBytes: Long,
    val freeBytes: Long,
    val totalBytes: Long,
    val lastRefreshAt: Long
)

private fun dirSize(file: File): Long = runCatching {
    if (!file.exists()) 0L
    else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 ->
        String.format(Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
    bytes >= 1024L * 1024 ->
        String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
    bytes >= 1024L ->
        String.format(Locale.US, "%.0f KB", bytes.toDouble() / 1024)
    else -> "$bytes B"
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange
        )
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.largeIncreased) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

// ---------- 草稿箱 ----------

@Composable
fun DraftsScreen(
    onClose: () -> Unit,
    onEdit: (top.saltfish.ech0.publish.DraftPayload) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drafts = remember { mutableStateListOf<top.saltfish.ech0.publish.DraftPayload>() }
    var pendingDelete by remember { mutableStateOf<top.saltfish.ech0.publish.DraftPayload?>(null) }
    LaunchedEffect(Unit) { runCatching { PendingFileDeletions.flush(context) } }
    LaunchedEffect(PublishBus.tick) {
        val latest = PublishStore.drafts()
        drafts.clear()
        drafts.addAll(latest)
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("草稿箱", onClose)
        if (drafts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无草稿",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(drafts, key = { it.id }) { draft ->
                Box(Modifier.animateItem()) {
                Card(shape = MaterialTheme.shapes.largeIncreased) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                draft.content.ifBlank { "（无文字 · ${draft.images.size} 张图）" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )
                            Text(
                                "${formatDateTime(draft.updatedAt)} · ${draft.images.size} 图${if (draft.private) " · 私密" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { onEdit(draft) }) {
                            Text("编辑", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { pendingDelete = draft }) {
                            Icon(
                                Icons.Filled.Delete, contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                }
            }
        }
    }

    pendingDelete?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除草稿？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    drafts.remove(draft)
                    scope.launch { PublishStore.discardDraft(context, draft) }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

// ---------- 发布队列 ----------

@Composable
fun QueueScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val queue = remember { mutableStateListOf<top.saltfish.ech0.publish.QueueItem>() }

    suspend fun refreshQueue() {
        val latest = PublishStore.queue()
        if (latest != queue.toList()) {
            queue.clear()
            queue.addAll(latest)
        }
    }

    LaunchedEffect(PublishBus.tick) { refreshQueue() }
    // 队列状态由 Worker 在后台更新：页面打开时轻量轮询，避免一直停在「发送中」
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            refreshQueue()
        }
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("发布队列", onClose)
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "队列为空",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(queue, key = { it.payload.id }) { item ->
                Box(Modifier.animateItem()) {
                Card(shape = MaterialTheme.shapes.largeIncreased) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (stateColor, container) = when (item.state) {
                                "running" -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
                                "failed" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = container
                            ) {
                                Text(
                                    when (item.state) {
                                        "running" -> "发送中"
                                        "failed" -> "失败"
                                        else -> "待发送"
                                    } + if (item.attempts > 0) " · 重试${item.attempts}次" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = stateColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatDateTime(item.enqueuedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.size(6.dp))
                        Text(
                            item.payload.content.ifBlank { "（无文字 · ${item.payload.images.size} 图）" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                        item.lastError?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        Row {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        PublishStore.updateItem(
                                            item.copy(state = "pending", attempts = 0, lastError = null)
                                        )
                                        refreshQueue()
                                        PublishScheduler.kick(context)
                                    }
                                },
                                enabled = item.state != "running"
                            ) {
                                Text("立即重试", style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        PublishStore.discardQueueItem(context, item)
                                        refreshQueue()
                                    }
                                },
                                enabled = item.state != "running"
                            ) {
                                Text("删除", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

// ---------- 关于 ----------

@Composable
fun AboutScreen(onClose: () -> Unit) {
    var hello by remember { mutableStateOf<top.saltfish.ech0.data.Hello?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTech by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateText by remember { mutableStateOf<String?>(null) }
    var releaseToShow by remember { mutableStateOf<AppRelease?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { hello = SiteStore.hello() }
            .rethrowCancellationLike()
            .onFailure { error = it.userMessage() }
    }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("关于", onClose)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 应用标识
            Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(top.saltfish.ech0.R.drawable.ic_tree_logo),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp)
                    )
                    Text("Ech0 Mobile", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "v${BuildConfig.VERSION_NAME} · Material 3 Expressive",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "自托管说说平台的全功能 Android 客户端",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 实例信息
            Card(shape = MaterialTheme.shapes.largeIncreased, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "实例信息",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val h = hello
                    if (h != null) {
                        AboutLine("实例", Ech0Api.base())
                        AboutLine("版本", "Ech0 ${h.version} (${h.commit.take(7)})")
                        AboutLine("作者", h.author)
                        AboutLine("许可", h.license)
                    } else {
                        Text(
                            error ?: "连接实例中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 开源与链接
            Card(shape = MaterialTheme.shapes.largeIncreased, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(0.dp)) {
                    val repo = "https://github.com/$DefaultGitHubRepo"
                    SettingsListRow(
                        icon = Icons.Filled.Refresh,
                        title = "检查更新",
                        valueText = if (checkingUpdate) "检查中…" else "当前 v${BuildConfig.VERSION_NAME}",
                        chevron = true,
                        onClick = {
                            if (!checkingUpdate) {
                                checkingUpdate = true
                                updateText = null
                                scope.launch {
                                    UpdateChecker.check(DefaultGitHubRepo).fold(
                                        onSuccess = { release ->
                                            if (release != null) {
                                                releaseToShow = release
                                            } else {
                                                updateText = "已是最新版本 v${BuildConfig.VERSION_NAME}"
                                            }
                                        },
                                        onFailure = { updateText = it.userMessage() }
                                    )
                                    checkingUpdate = false
                                }
                            }
                        }
                    )
                    top.saltfish.ech0.ui.common.SettingsListRow(
                        icon = Icons.Filled.Public,
                        title = "Ech0 Mobile 项目",
                        valueText = "GitHub",
                        chevron = true,
                        onClick = { runCatching { uriHandler.openUri(repo) } }
                    )
                    top.saltfish.ech0.ui.common.SettingsListRow(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = "开源许可",
                        valueText = "AGPL-3.0",
                        chevron = true,
                        onClick = { runCatching { uriHandler.openUri("https://www.gnu.org/licenses/agpl-3.0.html") } }
                    )
                    top.saltfish.ech0.ui.common.SettingsListRow(
                        icon = Icons.Filled.Info,
                        title = "更新记录",
                        valueText = "GitHub Release",
                        chevron = true,
                        onClick = { showChangelog = true }
                    )
                    updateText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // 技术栈：默认收起，避免 About 首屏全是开发者信息
            Card(shape = MaterialTheme.shapes.largeIncreased, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    SettingsListRow(
                        icon = Icons.Filled.Settings,
                        title = "技术栈与构建信息",
                        valueText = if (showTech) "收起" else "Jetpack Compose · OkHttp",
                        chevron = !showTech,
                        onClick = { showTech = !showTech }
                    )
                    if (showTech) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AboutLine("UI", "Jetpack Compose · Material 3 Expressive")
                            AboutLine("网络", "OkHttp · Coil")
                            AboutLine("存储", "DataStore · 磁盘缓存")
                            AboutLine("后台", "WorkManager 发布队列")
                        }
                    }
                }
            }
            Spacer(Modifier.size(40.dp))
        }
    }

    if (showChangelog) {
        AppChangelogDialog { showChangelog = false }
    }
    releaseToShow?.let { release ->
        AppReleaseSheet(
            release = release,
            currentVersion = BuildConfig.VERSION_NAME,
            onDismiss = { releaseToShow = null }
        )
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}
