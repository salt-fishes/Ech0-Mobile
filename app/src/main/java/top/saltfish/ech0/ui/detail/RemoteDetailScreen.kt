@file:OptIn(ExperimentalMaterial3Api::class)

package top.saltfish.ech0.ui.detail

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.PublicComment
import top.saltfish.ech0.data.isCancellationLike
import top.saltfish.ech0.data.userMessage
import top.saltfish.ech0.ui.common.Avatar
import top.saltfish.ech0.ui.common.ErrorCard
import top.saltfish.ech0.ui.common.ImageViewerDialog
import top.saltfish.ech0.ui.common.MarkdownText
import top.saltfish.ech0.ui.common.MediaGrid
import top.saltfish.ech0.ui.common.TagChip
import top.saltfish.ech0.ui.common.formatDateTime
import top.saltfish.ech0.ui.common.formatTime
import top.saltfish.ech0.ui.common.imageUrlsOf
import java.net.URI

private fun hostOf(url: String): String = runCatching { URI(url).host ?: "" }.getOrDefault("")

/** 友链远端说说详情：只读，支持完整 Markdown、媒体和评论 */
@Composable
fun RemoteDetailScreen(
    sourceUrl: String,
    echoId: String,
    onClose: () -> Unit
) {
    var echo by remember { mutableStateOf<Echo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var previewImage by remember { mutableStateOf<Int?>(null) }
    val comments = remember { mutableStateListOf<PublicComment>() }
    val uriHandler = LocalUriHandler.current
    val base = sourceUrl.trimEnd('/')

    LaunchedEffect(echoId, reloadKey) {
        error = null
        try {
            echo = Ech0Api.remoteEchoDetail(base, echoId)
            comments.clear()
            runCatching { comments.addAll(Ech0Api.remoteComments(base, echoId)) }
        } catch (e: Exception) {
            if (e.isCancellationLike()) return@LaunchedEffect
            error = e.userMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("友链详情", style = MaterialTheme.typography.titleLarge)
                    Text(
                        hostOf(base),
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
            return
        }

        val e = echo ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(e.username, 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
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
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp)
                            )
                        }
                        if (e.echo_files.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            MediaGrid(
                                files = e.echo_files,
                                layout = e.layout,
                                onImageClick = { previewImage = it }
                            )
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
                            "来自 ${hostOf(base)}",
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
                        "暂无评论，或该实例未公开评论列表",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            items(comments, key = { it.id }) { comment ->
                Card(shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Avatar(comment.nickname, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    comment.nickname.ifBlank { "游客" },
                                    style = MaterialTheme.typography.labelLarge
                                )
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
                        }
                    }
                }
            }
        }
    }

    previewImage?.let { index ->
        ImageViewerDialog(
            urls = imageUrlsOf(echo?.echo_files.orEmpty()),
            initialIndex = index,
            onDismiss = { previewImage = null }
        )
    }
}
