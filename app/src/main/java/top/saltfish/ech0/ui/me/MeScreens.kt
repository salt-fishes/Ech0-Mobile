@file:OptIn(ExperimentalMaterial3Api::class)

package top.saltfish.ech0.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.saltfish.ech0.data.ApiException
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.publish.PublishStore
import top.saltfish.ech0.ui.AppNav
import top.saltfish.ech0.ui.common.Avatar
import top.saltfish.ech0.ui.common.ConfirmDialog
import top.saltfish.ech0.ui.common.ContentPadding
import top.saltfish.ech0.ui.common.EmptyStateCard
import top.saltfish.ech0.ui.common.OverlayTopBar
import top.saltfish.ech0.ui.common.SettingsListRow
import top.saltfish.ech0.ui.common.formatDateTime
import top.saltfish.ech0.ui.composer.ComposerBus
import top.saltfish.ech0.work.PublishScheduler
import java.net.URI

private fun hostOf(url: String): String = runCatching { URI(url).host ?: "" }.getOrDefault("")

// ---------- 我的 ----------

@Composable
fun MeScreen(nav: AppNav) {
    val state = Ech0Store.current
    val scope = rememberCoroutineScope()
    var queueCount by remember { mutableStateOf(0) }
    var draftCount by remember { mutableStateOf(0) }
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
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
                // 账号卡
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(state.username, 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.username, style = MaterialTheme.typography.titleMedium)
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
                            val host = hostOf(state.baseUrl)
                            Text(
                                host.ifBlank { state.baseUrl },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 菜单（M3 ListItem + 分隔线）
            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
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
                        valueText = hostOf(state.baseUrl).ifBlank { "未配置实例" },
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

            if (loggedIn) {
                // 退出登录（带确认）
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    onClick = { confirmLogout = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Logout, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "退出登录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.size(150.dp))
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录？",
            confirmText = "退出",
            onConfirm = {
                confirmLogout = false
                scope.launch { runCatching { Ech0Api.logout() } }
            },
            onDismiss = { confirmLogout = false }
        )
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
                .padding(20.dp),
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
                                val s = Ech0Api.settings()
                                Ech0Store.setServerName(s.site_title.ifBlank { s.server_name })
                            }
                            onClose()
                        } catch (e: Exception) {
                            message = (e as? ApiException)?.message ?: "登录失败：${e.message}"
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

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = Ech0Store.current
    var baseUrl by remember { mutableStateOf(state.baseUrl) }
    var themeMode by remember { mutableStateOf(state.themeMode) }
    var compress by remember { mutableStateOf(state.compressImages) }
    var tokenInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { version = Ech0Api.hello().version }
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("设置", onClose)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Field("实例地址", baseUrl) { baseUrl = it }
            Button(
                onClick = {
                    testing = true
                    scope.launch {
                        Ech0Store.setBaseUrl(baseUrl)
                        runCatching {
                            val h = Ech0Api.hello()
                            version = h.version
                            val s = Ech0Api.settings()
                            Ech0Store.setServerName(s.site_title.ifBlank { s.server_name })
                            message = "连接成功 · Ech0 ${h.version}"
                        }.onFailure { message = "连接失败：${it.message}" }
                        testing = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("测试连接")
            }
            version?.let {
                Text(
                    "实例版本：Ech0 $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingSection("外观") {
                val options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = {
                                themeMode = mode
                                scope.launch { Ech0Store.setThemeMode(mode) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            SettingSection("图片") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "上传前压缩（长边 2048 · WebP）",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = compress,
                        onCheckedChange = {
                            compress = it
                            scope.launch { Ech0Store.setCompress(it) }
                        }
                    )
                }
            }

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
                                val u = Ech0Api.currentUser()
                                Ech0Store.setUser(u.username, u.is_admin)
                                message = "Token 有效 · ${u.username}${if (u.is_admin) "（管理员）" else ""}"
                            }.onFailure { message = "Token 无效：${it.message}" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("验证并保存")
                }
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

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

// ---------- 草稿箱 ----------

@Composable
fun DraftsScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val drafts = remember { mutableStateListOf<top.saltfish.ech0.publish.DraftPayload>() }
    LaunchedEffect(Unit) { drafts.addAll(PublishStore.drafts()) }

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
                Card(shape = MaterialTheme.shapes.large) {
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
                        FilledTonalButton(onClick = {
                            ComposerBus.restoreDraft = draft
                            onClose()
                        }) {
                            Text("编辑", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = {
                            drafts.remove(draft)
                            scope.launch { PublishStore.deleteDraft(draft.id) }
                        }) {
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
}

// ---------- 发布队列 ----------

@Composable
fun QueueScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val queue = remember { mutableStateListOf<top.saltfish.ech0.publish.QueueItem>() }
    LaunchedEffect(Unit) { queue.addAll(PublishStore.queue()) }

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
                Card(shape = MaterialTheme.shapes.large) {
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
                            FilledTonalButton(onClick = {
                                scope.launch {
                                    PublishStore.updateItem(item.copy(state = "pending", lastError = null))
                                    queue.clear()
                                    queue.addAll(PublishStore.queue())
                                    PublishScheduler.kick(context)
                                }
                            }) {
                                Text("立即重试", style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                scope.launch {
                                    PublishStore.remove(item.payload.id)
                                    queue.remove(item)
                                }
                            }) {
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
    LaunchedEffect(Unit) {
        runCatching { hello = Ech0Api.hello() }
            .onFailure { error = it.message }
    }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("关于", onClose)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
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
                        painter = androidx.compose.ui.res.painterResource(top.saltfish.ech0.R.drawable.img_logo),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp)
                    )
                    Text("Ech0 Mobile", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "v0.2.0 · Material 3 Expressive",
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
            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
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
            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(0.dp)) {
                    val repo = hello?.repo_url?.takeIf { it.isNotBlank() }
                        ?: "https://github.com/lin-snow/Ech0"
                    top.saltfish.ech0.ui.common.SettingsListRow(
                        icon = Icons.Filled.Public,
                        title = "Ech0 上游项目",
                        valueText = "GitHub",
                        chevron = true,
                        onClick = { runCatching { uriHandler.openUri(repo) } }
                    )
                    top.saltfish.ech0.ui.common.SettingsListRow(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = "开源许可",
                        valueText = "AGPL-3.0",
                        chevron = false,
                        onClick = { runCatching { uriHandler.openUri("https://www.gnu.org/licenses/agpl-3.0.html") } }
                    )
                }
            }

            // 技术栈
            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "技术栈",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AboutLine("UI", "Jetpack Compose · Material 3 Expressive")
                    AboutLine("网络", "OkHttp · Coil")
                    AboutLine("存储", "DataStore · 磁盘缓存")
                    AboutLine("后台", "WorkManager 发布队列")
                }
            }
            Spacer(Modifier.size(40.dp))
        }
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
