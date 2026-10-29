@file:OptIn(ExperimentalMaterial3Api::class)

package top.saltfish.ech0.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.saltfish.ech0.data.CreateCommentDto
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.data.PublicComment
import top.saltfish.ech0.data.isCancellationLike
import top.saltfish.ech0.data.userMessage
import top.saltfish.ech0.ui.common.Avatar
import top.saltfish.ech0.ui.common.CommentComposer
import top.saltfish.ech0.ui.common.CommentIdentity
import top.saltfish.ech0.ui.common.EchoExtensionCard
import top.saltfish.ech0.ui.common.ErrorCard
import top.saltfish.ech0.ui.common.ImageViewerDialog
import top.saltfish.ech0.ui.common.MarkdownText
import top.saltfish.ech0.ui.common.MediaGrid
import top.saltfish.ech0.ui.common.TagChip
import top.saltfish.ech0.ui.common.formatDateTime
import top.saltfish.ech0.ui.common.formatTime
import top.saltfish.ech0.ui.common.imageUrlsOf
import top.saltfish.ech0.ui.common.threadComments
import java.net.URI

private fun hostOf(url: String): String = runCatching { URI(url).host ?: "" }.getOrDefault("")

/** 友链远端说说详情：完整内容、媒体、卡片和评论；评论直接发到对方实例 */
@Composable
fun RemoteDetailScreen(
    sourceUrl: String,
    echoId: String,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var echo by remember { mutableStateOf<Echo?>(null) }
    var serverName by remember { mutableStateOf("") }
    var serverLogo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var previewImage by remember { mutableStateOf<Int?>(null) }
    var replyTo by remember { mutableStateOf<PublicComment?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var commentHint by remember { mutableStateOf<String?>(null) }
    var commentResetKey by remember { mutableIntStateOf(0) }
    val comments = remember { mutableStateListOf<PublicComment>() }
    val uriHandler = LocalUriHandler.current
    val base = sourceUrl.trimEnd('/')

    LaunchedEffect(echoId, reloadKey) {
        error = null
        try {
            echo = Ech0Api.remoteEchoDetail(base, echoId)
            runCatching {
                val info = Ech0Api.remoteConnect(base)
                serverName = info.server_name
                serverLogo = info.logo?.takeIf { it.isNotBlank() }
                    ?.let { Ech0Api.resolveUrlWithBase(base, it) }
            }
            comments.clear()
            runCatching { comments.addAll(Ech0Api.remoteComments(base, echoId)) }
        } catch (e: Exception) {
            if (e.isCancellationLike()) return@LaunchedEffect
            error = e.userMessage()
        }
    }

    fun submitComment(identity: CommentIdentity, content: String) {
        if (content.isBlank() || sending) return
        if (identity.email.isBlank()) {
            commentHint = "请先填写邮箱（Ech0 要求，不会公开）"
            return
        }
        sending = true
        commentHint = "发送中…"
        val parentId = replyTo?.id
        scope.launch {
            try {
                var lastError: Exception? = null
                for (attempt in 0..1) {
                    try {
                        val formStarted = System.currentTimeMillis()
                        val form = Ech0Api.remoteCommentsForm(base, echoId)
                        if (!form.enable_comment) {
                            commentHint = "对方站长已关闭评论"
                            return@launch
                        }
                        val captchaToken = if (form.captcha_enabled && !form.captcha_api_endpoint.isNullOrBlank()) {
                            commentHint = "正在完成验证码…"
                            Ech0Api.solveRemoteCaptcha(base, form.captcha_api_endpoint)
                        } else null
                        // 多留 700ms 余量：token 的签发时间在对方服务端，网络延迟会造成「提交过快」
                        val waitMs = form.min_submit_ms - (System.currentTimeMillis() - formStarted)
                        if (waitMs > 0) delay(waitMs + 700)

                        val result = Ech0Api.remotePostComment(
                            base,
                            CreateCommentDto(
                                echo_id = echoId,
                                parent_id = parentId,
                                nickname = identity.nickname.ifBlank { "游客" },
                                email = identity.email,
                                website = identity.website.ifBlank { null },
                                content = content,
                                form_token = form.form_token,
                                captcha_token = captchaToken
                            )
                        )
                        replyTo = null
                        commentResetKey++
                        commentHint = if (result.status == "pending") "已提交，待对方站长审核" else "评论成功"
                        runCatching { comments.clear(); comments.addAll(Ech0Api.remoteComments(base, echoId)) }
                        return@launch
                    } catch (e: Exception) {
                        if (e.isCancellationLike()) return@launch
                        lastError = e
                        val message = e.userMessage()
                        val retryable = message.contains("过快") || message.contains("失效") ||
                            message.contains("过期") || message.contains("token", ignoreCase = true)
                        if (!retryable || attempt == 1) throw e
                        commentHint = "表单已刷新，正在重试…"
                        delay(300)
                    }
                }
                lastError?.let { throw it }
            } catch (e: Exception) {
                if (e.isCancellationLike()) return@launch
                commentHint = e.userMessage()
            } finally {
                sending = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(replyTo?.id, commentDraft.isBlank()) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()
                        if (up != null && !up.isConsumed && replyTo != null && commentDraft.isBlank()) {
                            replyTo = null
                            focusManager.clearFocus()
                        }
                    }
                }
            }
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("友链详情", style = MaterialTheme.typography.titleLarge)
                    Text(
                        serverName.ifBlank { hostOf(base) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = {
                    runCatching { uriHandler.openUri("$base/echo/$echoId") }
                }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "在浏览器打开")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        error?.let {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                ErrorCard(message = it, onRetry = { reloadKey++ })
            }
            return@Column
        }

        val e = echo ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
            return@Column
        }
        val threads = threadComments(comments)

        LazyColumn(
            Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(e.username, 38.dp, url = serverLogo)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.username.ifBlank { serverName.ifBlank { "匿名" } },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    formatDateTime(e.created_at) + if (e.private) " · 私密" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (e.fav_count > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Whatshot, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        " ${e.fav_count}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (e.content.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            MarkdownText(
                                markdown = e.content,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                                baseUrl = base
                            )
                        }
                        if (e.echo_files.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            MediaGrid(
                                files = e.echo_files,
                                layout = e.layout,
                                baseUrl = base,
                                onImageClick = { previewImage = it }
                            )
                        }
                        if (e.extension != null || e.content.contains("http")) {
                            Spacer(Modifier.height(12.dp))
                            EchoExtensionCard(e)
                        }
                        if (e.tags.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                e.tags.forEach { TagChip(it.name) }
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("评论 ${comments.size}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            "来自 ${serverName.ifBlank { hostOf(base) }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            if (comments.isEmpty()) {
                item {
                    Text(
                        "暂无评论，来说两句吧",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            items(threads, key = { it.root.id }) { thread ->
                Column {
                    RemoteCommentCard(
                        comment = thread.root,
                        serverLogo = serverLogo,
                        echoUsername = e.username,
                        onReply = { replyTo = thread.root }
                    )
                    thread.replies.forEach { reply ->
                        Box(Modifier.padding(start = 26.dp, top = 6.dp)) {
                            RemoteCommentCard(
                                comment = reply,
                                serverLogo = serverLogo,
                                echoUsername = e.username,
                                onReply = { replyTo = reply },
                                compact = true
                            )
                        }
                    }
                }
            }
        }

        CommentComposer(
            hint = commentHint,
            sending = sending,
            replyToName = replyTo?.nickname,
            resetKey = commentResetKey,
            onCancelReply = {
                replyTo = null
                focusManager.clearFocus()
            },
            onDraftChange = { commentDraft = it },
            onSubmit = { identity, content -> submitComment(identity, content) }
        )
    }

    previewImage?.let { index ->
        ImageViewerDialog(
            urls = imageUrlsOf(echo?.echo_files.orEmpty(), base),
            initialIndex = index,
            onDismiss = { previewImage = null }
        )
    }
}

@Composable
private fun RemoteCommentCard(
    comment: PublicComment,
    serverLogo: String?,
    echoUsername: String,
    onReply: () -> Unit,
    compact: Boolean = false
) {
    Card(onClick = onReply, shape = MaterialTheme.shapes.large) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(if (compact) 10.dp else 12.dp)
        ) {
            Avatar(
                comment.nickname,
                if (compact) 26.dp else 30.dp,
                url = if (comment.nickname == echoUsername) serverLogo else null
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        comment.nickname.ifBlank { "游客" },
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (comment.hot) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Whatshot, contentDescription = "热评",
                            tint = Color(0xFFFF8D28),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatTime(comment.created_at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    comment.content,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (comment.status != "approved") {
                        Text(
                            "（待审核）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.TextButton(
                        onClick = onReply,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) { Text("回复", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
