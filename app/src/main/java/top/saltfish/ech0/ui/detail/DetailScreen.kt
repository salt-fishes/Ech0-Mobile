@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package top.saltfish.ech0.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.saltfish.ech0.data.CreateCommentDto
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.LikeController
import top.saltfish.ech0.data.PublicComment
import top.saltfish.ech0.data.isCancellationLike
import top.saltfish.ech0.ui.common.AppMessages
import top.saltfish.ech0.ui.common.Avatar
import top.saltfish.ech0.ui.common.ErrorCard
import top.saltfish.ech0.ui.common.MarkdownText
import top.saltfish.ech0.ui.common.MediaGrid
import top.saltfish.ech0.ui.common.TagChip
import top.saltfish.ech0.ui.common.formatDateTime
import top.saltfish.ech0.ui.common.formatTime
import top.saltfish.ech0.data.userMessage

@Composable
fun DetailScreen(
    echoId: String,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var echo by remember { mutableStateOf<Echo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val comments = remember { mutableStateListOf<PublicComment>() }
    var commentsLoading by remember { mutableStateOf(false) }

    var replyTo by remember { mutableStateOf<PublicComment?>(null) }
    var commentText by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf(Ech0Store.current.username.ifBlank { "游客" }) }
    var sending by remember { mutableStateOf(false) }
    var commentHint by remember { mutableStateOf<String?>(null) }
    var liked by remember { mutableStateOf(echoId in Ech0Store.current.likedIds) }
    var likeBusy by remember { mutableStateOf(false) }
    // 计数与列表页共用 LikeController 的会话级状态
    val likeCount = LikeController.count(echoId, echo?.fav_count ?: 0)
    var likePulse by remember { mutableStateOf(false) }
    // 点赞脉冲：Expressive 弹簧放大回弹
    val likeScale by animateFloatAsState(
        targetValue = if (likePulse) 1.35f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "detailLike"
    )
    LaunchedEffect(likePulse) {
        if (likePulse) {
            kotlinx.coroutines.delay(220)
            likePulse = false
        }
    }

    LaunchedEffect(echoId, reloadKey) {
        error = null
        try {
            echo = Ech0Api.echoById(echoId)
            liked = echoId in Ech0Store.current.likedIds
            echo?.let { LikeController.sync(it.id, it.fav_count) }
        } catch (e: Exception) {
            if (e.isCancellationLike()) return@LaunchedEffect
            error = e.userMessage()
        }
        commentsLoading = true
        runCatching { comments.clear(); comments.addAll(Ech0Api.comments(echoId)) }
        commentsLoading = false
    }

    fun submitComment() {
        if (commentText.isBlank() || sending) return
        sending = true
        commentHint = "发送中…"
        scope.launch {
            try {
                val form = Ech0Api.commentsForm(echoId)
                if (!form.enable_comment) {
                    commentHint = "站长已关闭评论"
                    return@launch
                }
                // 表单太快的蜜罐检查
                val captchaToken = if (form.captcha_enabled && !form.captcha_api_endpoint.isNullOrBlank()) {
                    commentHint = "正在完成验证码…"
                    Ech0Api.solveCaptcha(form.captcha_api_endpoint)
                } else null

                val result = Ech0Api.postComment(
                    CreateCommentDto(
                        echo_id = echoId,
                        parent_id = replyTo?.id,
                        nickname = nickname.ifBlank { "游客" },
                        content = commentText.trim(),
                        form_token = form.form_token,
                        captcha_token = captchaToken
                    )
                )
                commentText = ""
                replyTo = null
                commentHint = if (result.status == "pending") "已提交，待站长审核" else "评论成功"
                runCatching { comments.clear(); comments.addAll(Ech0Api.comments(echoId)) }
            } catch (e: Exception) {
                if (e.isCancellationLike()) return@launch
                commentHint = e.userMessage()
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        TopAppBar(
            title = { Text("说说详情") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        error?.let { err ->
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                ErrorCard(
                    message = err,
                    onRetry = {
                        error = null
                        reloadKey++
                    }
                )
            }
            return
        }

        val e = echo ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
            }
            return
        }

        LazyColumn(
            Modifier.fillMaxSize().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(e.username, 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    e.username.ifBlank { "匿名" },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    formatDateTime(e.created_at) + if (e.private) " · 私密" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (likeCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Whatshot, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        " $likeCount",
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
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp)
                            )
                        }
                        if (e.echo_files.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            MediaGrid(files = e.echo_files, layout = e.layout)
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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val clipboard = LocalClipboardManager.current
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
                            liked = true
                            likePulse = true
                            scope.launch {
                                val result = LikeController.like(echoId, echo?.fav_count ?: 0)
                                if (result.isFailure) {
                                    liked = false
                                    commentHint = result.exceptionOrNull()?.userMessage()
                                        ?: "点赞失败，请稍后重试"
                                    AppMessages.show(commentHint!!)
                                }
                                likeBusy = false
                            }
                        }
                    ) {
                        Icon(
                            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "赞",
                            modifier = Modifier.graphicsLayer {
                                scaleX = likeScale
                                scaleY = likeScale
                            }
                        )
                    }
                    Text(
                        if (liked) "已赞 $likeCount" else "赞 $likeCount",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = {
                            clipboard.setText(AnnotatedString("${Ech0Api.base()}/echo/$echoId"))
                            commentHint = "链接已复制"
                        }
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("复制链接")
                    }
                }
            }

            item {
                Text(
                    "评论 ${comments.size}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (commentsLoading && comments.isEmpty()) {
                item {
                    Text(
                        "评论加载中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (comments.isEmpty() && !commentsLoading) {
                item {
                    Text(
                        "还没有评论，来说两句吧",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            items(comments, key = { it.id }) { c ->
                Box(Modifier.animateItem()) {
                    CommentCard(comment = c, onReply = { replyTo = c })
                }
            }
        }

        // 评论输入区
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            commentHint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
            if (replyTo != null) {
                Row(
                    Modifier.padding(start = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "回复 ${replyTo?.nickname}：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { replyTo = null }) {
                        Text("取消", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            OutlinedTextField(
                value = commentText,
                onValueChange = { if (it.length <= 200) commentText = it },
                placeholder = {
                    Text(
                        if (replyTo != null) "回复 ${replyTo?.nickname}…" else "说两句…（$nickname）"
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    IconButton(
                        onClick = { submitComment() },
                        enabled = !sending && commentText.isNotBlank()
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Filled.Send, contentDescription = "发送",
                                tint = if (commentText.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CommentCard(comment: PublicComment, onReply: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Avatar(comment.nickname, 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        comment.nickname.ifBlank { "游客" },
                        style = MaterialTheme.typography.titleSmall
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
                if (comment.status != "approved") {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "（待审核）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                TextButton(onClick = onReply, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("回复", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
