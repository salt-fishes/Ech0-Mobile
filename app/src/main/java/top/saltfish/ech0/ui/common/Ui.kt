package top.saltfish.ech0.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import top.saltfish.ech0.data.EchoFile
import top.saltfish.ech0.data.Ech0Api
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- 时间 ----------

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(unixSeconds: Long): String {
    if (unixSeconds <= 0) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - unixSeconds
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60} 分钟前"
        diff < 86400 -> "${diff / 3600} 小时前"
        diff < 86400 * 2 -> "昨天 " + timeFormatter.format(Instant.ofEpochSecond(unixSeconds).atZone(ZoneId.systemDefault()))
        diff < 86400 * 30 -> "${diff / 86400} 天前"
        else -> dateFormatter.format(Instant.ofEpochSecond(unixSeconds).atZone(ZoneId.systemDefault()))
    }
}

fun formatDateTime(unixSeconds: Long): String {
    if (unixSeconds <= 0) return ""
    return dateFormatter.format(Instant.ofEpochSecond(unixSeconds).atZone(ZoneId.systemDefault()))
}

// ---------- 头像 ----------

@Composable
fun Avatar(name: String, size: androidx.compose.ui.unit.Dp = 36.dp, url: String? = null) {
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(Ech0Api.resolveUrl(url))
                .crossfade(220)
                .build(),
            contentDescription = name,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        return
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(Locale.ROOT).ifBlank { "E" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = (size.value * 0.45f).sp
        )
    }
}

// ---------- 标签 chip（M3 chip 视觉：secondaryContainer 药丸） ----------

@Composable
fun TagChip(name: String, onClick: (() -> Unit)? = null) {
    Text(
        "#$name",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ---------- Markdown（轻量渲染：粗体/斜体/删除线/行内代码/链接/话题/提及 + 围栏代码块） ----------

private val inlinePattern = Regex(
    """(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(\*[^*\n]+\*)|(~~[^~\n]+~~)|(\[[^\]\n]+\]\([^)\s]+\))|(https?://[^\s<>()\[\]{}，。；、""']+)|(#[^\s#，。；]+)|(@[^\s@，。；]+)"""
)

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
    onTagClick: ((String) -> Unit)? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    val textColor = style.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface
    val uriHandler = LocalUriHandler.current
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(modifier) {
        markdown.split("\n").forEachIndexed { index, line ->
            val isFence = line.trimStart().startsWith("```")
            if (isFence) {
                Text(
                    line,
                    style = style.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = textColor.copy(0.75f)
                    )
                )
            } else {
                val lineAnnotated = buildLine(line, accent, textColor, codeBackground, uriHandler, onTagClick)
                Text(
                    lineAnnotated.ifEmpty { AnnotatedString(" ") },
                    style = style.copy(color = textColor),
                    onTextLayout = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun buildLine(
    line: String,
    accent: Color,
    textColor: Color,
    codeBackground: Color,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onTagClick: ((String) -> Unit)?
): AnnotatedString {
    // 标题行：# 开头按层级放大加粗
    val heading = Regex("^(#{1,4})\\s+(.+)$").find(line)
    if (heading != null) {
        val level = heading.groupValues[1].length
        val scale = when (level) {
            1 -> 1.5f; 2 -> 1.3f; else -> 1.15f
        }
        return buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (16f * scale).sp)) {
                append(heading.groupValues[2])
            }
        }
    }
    if (line.trim() == "---" || line.trim() == "***") {
        return buildAnnotatedString { append("─".repeat(20)) }
    }
    // 引用行
    val quote = Regex("^>\\s?(.*)$").find(line)
    if (quote != null) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = textColor.copy(0.7f))) { append("▎" + quote.groupValues[1]) }
        }
    }

    return buildAnnotatedString {
        var cursor = 0
        for (m in inlinePattern.findAll(line)) {
            if (m.range.first > cursor) append(line.substring(cursor, m.range.first))
            val token = m.value
            when {
                token.startsWith("`") -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, fontSize = 14.sp)
                ) { append(token.removePrefix("`").removeSuffix("`")) }

                token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removePrefix("**").removeSuffix("**"))
                }

                token.startsWith("~~") -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(token.removePrefix("~~").removeSuffix("~~"))
                }

                token.startsWith("*") -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(token.removePrefix("*").removeSuffix("*"))
                }

                token.startsWith("[") -> {
                    val linkMatch = Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)").find(token)
                    if (linkMatch != null) {
                        val label = linkMatch.groupValues[1]
                        val url = linkMatch.groupValues[2]
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = url,
                                styles = TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline))
                            ) { runCatching { uriHandler.openUri(url) } }
                        ) { append(label) }
                    } else append(token)
                }

                token.startsWith("http") -> withLink(
                    LinkAnnotation.Clickable(
                        tag = token,
                        styles = TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline))
                    ) { runCatching { uriHandler.openUri(token) } }
                ) { append(token) }

                token.startsWith("#") -> withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
                    append(token)
                }

                token.startsWith("@") -> withStyle(SpanStyle(color = accent)) { append(token) }

                else -> append(token)
            }
            cursor = m.range.last + 1
        }
        if (cursor < line.length) append(line.substring(cursor))
    }
}

// ---------- 媒体宫格（对照 Ech0 六种 layout；圆角走 M3 形状 token） ----------

fun imageUrlsOf(files: List<EchoFile>): List<String> = files.mapNotNull { ef ->
    ef.file?.takeIf {
        (it.category ?: "image") == "image" || (it.content_type ?: "").startsWith("image/")
    }?.let { Ech0Api.resolveUrl(it.url) }
}

/** 全屏图片查看：左右滑动切换，点右上角关闭 */
@Composable
fun ImageViewerDialog(urls: List<String>, initialIndex: Int, onDismiss: () -> Unit) {
    if (urls.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, urls.lastIndex),
        pageCount = { urls.size }
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(urls[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }
            if (urls.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                )
            }
        }
    }
}

@Composable
private fun mediaCorner() = MaterialTheme.shapes.medium

@Composable
fun MediaGrid(
    files: List<EchoFile>,
    layout: String?,
    modifier: Modifier = Modifier,
    onImageClick: (Int) -> Unit = {}
) {
    val images = files.mapNotNull { ef ->
        ef.file?.takeIf { (it.category ?: "image") == "image" || (it.content_type ?: "").startsWith("image/") }
    }
    if (images.isEmpty()) {
        // 音频/视频附件展示
        files.firstOrNull()?.file?.let { f ->
            val isVideo = f.category == "video" || (f.content_type ?: "").startsWith("video/")
            MediaPlaceholder(icon = if (isVideo) "▶" else "♪", label = f.name ?: "附件")
        }
        return
    }

    val urls = images.map { Ech0Api.resolveUrl(it.url) }
    val corner = mediaCorner()
    val aspect = { f: top.saltfish.ech0.data.FileDto ->
        val fw = f.width ?: 0
        val fh = f.height ?: 0
        if (fw > 0 && fh > 0) fw.toFloat() / fh else 4f / 3f
    }

    when {
        urls.size == 1 -> {
            val ar = aspect(images[0]).coerceIn(0.7f, 1.9f)
            NetImage(
                url = urls[0],
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(ar)
                    .clip(corner)
                    .clickable { onImageClick(0) }
            )
        }

        layout == "waterfall" && urls.size >= 4 -> {
            Row(modifier.fillMaxWidth()) {
                val cols = listOf(
                    images.indices.filter { it % 2 == 0 },
                    images.indices.filter { it % 2 == 1 }
                )
                cols.forEachIndexed { ci, idxList ->
                    Column(
                        Modifier.weight(1f).padding(horizontal = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        idxList.take(4).forEach { i ->
                            val ar = aspect(images[i]).coerceIn(0.7f, 1.4f)
                            NetImage(
                                url = urls[i],
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ar)
                                    .padding(vertical = 3.dp)
                                    .clip(corner)
                                    .clickable { onImageClick(i) }
                            )
                        }
                    }
                }
            }
        }

        layout == "horizontal" || layout == "carousel" -> {
            androidx.compose.foundation.lazy.LazyRow(modifier.fillMaxWidth()) {
                items(urls.size) { i ->
                    NetImage(
                        url = urls[i],
                        modifier = Modifier
                            .height(200.dp)
                            .aspectRatio(aspect(images[i]).coerceIn(0.75f, 1.4f))
                            .padding(horizontal = 3.dp)
                            .clip(corner)
                            .clickable { onImageClick(i) }
                    )
                }
            }
        }

        layout == "stack" -> {
            Box {
                NetImage(
                    url = urls[0],
                    modifier = modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect(images[0]).coerceIn(0.75f, 1.6f))
                        .clip(corner)
                        .clickable { onImageClick(0) }
                )
                if (urls.size > 1) {
                    Text(
                        "+${urls.size - 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(0.45f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        urls.size == 2 -> {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                NetImage(
                    urls[0],
                    Modifier.weight(1f).aspectRatio(1f).padding(horizontal = 3.dp)
                        .clip(corner).clickable { onImageClick(0) }
                )
                NetImage(
                    urls[1],
                    Modifier.weight(1f).aspectRatio(1f).padding(horizontal = 3.dp)
                        .clip(corner).clickable { onImageClick(1) }
                )
            }
        }

        urls.size == 4 -> {
            Column(modifier.fillMaxWidth()) {
                listOf(0 to 1, 2 to 3).forEach { (a, b) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        NetImage(urls[a], Modifier.weight(1f).aspectRatio(1f).padding(horizontal = 3.dp).clip(corner).clickable { onImageClick(a) })
                        NetImage(urls[b], Modifier.weight(1f).aspectRatio(1f).padding(horizontal = 3.dp).clip(corner).clickable { onImageClick(b) })
                    }
                }
            }
        }

        else -> {
            // 2/3 与 5..9：三列方格
            Column(modifier.fillMaxWidth()) {
                var idx = 0
                urls.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { url ->
                            val index = idx++
                            NetImage(
                                url,
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(3.dp)
                                    .clip(corner)
                                    .clickable { onImageClick(index) }
                            )
                        }
                        // 补齐空位
                        repeat(3 - row.size) {
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetImage(url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(url)
            .crossfade(220)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
fun MediaPlaceholder(icon: String, label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Text(
            "  $label",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
