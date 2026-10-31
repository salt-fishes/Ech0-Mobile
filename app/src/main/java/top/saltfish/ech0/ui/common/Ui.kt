package top.saltfish.ech0.ui.common

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.launch
import okhttp3.Request
import top.saltfish.ech0.data.EchoFile
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.data.net.HttpClients
import top.saltfish.ech0.data.net.await
import top.saltfish.ech0.data.net.bodyBytes
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- 时间 ----------

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(unixSeconds: Long): String {
    if (unixSeconds <= 0) return ""
    if (!Ech0Store.current.showRelativeTime) {
        return dateFormatter.format(
            Instant.ofEpochSecond(unixSeconds).atZone(ZoneId.systemDefault())
        )
    }
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

private fun feedImageSize(): Size = when (Ech0Store.current.feedImageQuality) {
    "high" -> Size(1600, 1600)
    "original" -> Size.ORIGINAL
    else -> Size(1080, 1080)
}

// ---------- 头像 ----------

@Composable
fun Avatar(name: String, size: androidx.compose.ui.unit.Dp = 36.dp, url: String? = null) {
    val lowData = !top.saltfish.ech0.data.net.NetworkPolicy.canLoadAvatars()
    if (!url.isNullOrBlank() && !lowData) {
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
    if (onClick != null) {
        SuggestionChip(
            onClick = onClick,
            label = {
                Text(
                    "#$name",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    } else {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                "#$name",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

// ---------- Markdown（轻量渲染：粗体/斜体/删除线/行内代码/链接/话题/提及 + 围栏代码块） ----------

private val inlinePattern = Regex(
    """(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(\*[^*\n]+\*)|(~~[^~\n]+~~)|(\[[^\]\n]+\]\([^)\s]+\))|(https?://[^\s<>()\[\]{}，。；、""']+)|(#[^\s#，。；]+)|(@[^\s@，。；]+)"""
)

private val markdownImagePattern = Regex("""!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)""")

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
    onTagClick: ((String) -> Unit)? = null,
    baseUrl: String = "",
    collapsible: Boolean = false
) {
    val accent = MaterialTheme.colorScheme.primary
    val textColor = style.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface
    val uriHandler = LocalUriHandler.current
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val codeFontFamily = if (Ech0Store.current.codeFont == "serif") {
        FontFamily.Serif
    } else {
        FontFamily.Monospace
    }
    val lowData = !top.saltfish.ech0.data.net.NetworkPolicy.canLoadBodyImages()
    var expanded by remember(markdown) { mutableStateOf(false) }
    var viewerIndex by remember(markdown) { mutableStateOf<Int?>(null) }

    fun resolveImage(raw: String): String =
        if (baseUrl.isBlank()) Ech0Api.resolveUrl(raw)
        else Ech0Api.resolveUrlWithBase(baseUrl, raw)

    val allImages = remember(markdown, baseUrl) {
        markdownImagePattern.findAll(markdown).map { resolveImage(it.groupValues[2]) }.toList()
    }
    val preview = remember(markdown, collapsible) {
        if (!collapsible) null else {
            val lines = markdown.lines()
            when {
                lines.size > 5 -> lines.take(5).joinToString("\n").trimEnd() + "\n…"
                markdown.length > 180 -> markdown.take(180).trimEnd() + "…"
                else -> null
            }
        }
    }
    val shown = if (preview != null && !expanded) preview else markdown

    Column(modifier) {
        shown.split("\n").forEach { line ->
            val isFence = line.trimStart().startsWith("```")
            val imageMatches = markdownImagePattern.findAll(line).toList()
            if (isFence) {
                Text(
                    line,
                    style = style.copy(
                        fontFamily = codeFontFamily,
                        fontSize = 13.sp,
                        color = textColor.copy(0.75f)
                    )
                )
            } else if (imageMatches.isNotEmpty()) {
                // 图片单独成行时按图片渲染，与文字混排时先渲染去掉图片语法后的文字
                val textLine = markdownImagePattern.replace(line, "").trim()
                if (textLine.isNotEmpty()) {
                    Text(
                        buildLine(textLine, accent, textColor, codeBackground, codeFontFamily, uriHandler, onTagClick),
                        style = style.copy(color = textColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                imageMatches.forEach { match ->
                    val resolved = resolveImage(match.groupValues[2])
                    val imageIndex = allImages.indexOf(resolved).coerceAtLeast(0)
                    if (lowData) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = "低流量模式",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(resolved)
                                .size(feedImageSize())
                                .crossfade(180)
                                .build(),
                            contentDescription = match.groupValues[1].ifBlank { "正文图片" },
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewerIndex = imageIndex }
                        )
                    }
                }
            } else {
                val lineAnnotated = buildLine(line, accent, textColor, codeBackground, codeFontFamily, uriHandler, onTagClick)
                Text(
                    lineAnnotated.ifEmpty { AnnotatedString(" ") },
                    style = style.copy(color = textColor),
                    onTextLayout = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (collapsible && preview != null && !expanded) {
            TextButton(
                onClick = { expanded = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 0.dp, vertical = 2.dp
                )
            ) { Text("展开全文", style = MaterialTheme.typography.labelMedium) }
        }
        if (collapsible && preview != null && expanded) {
            TextButton(
                onClick = { expanded = false },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 0.dp, vertical = 2.dp
                )
            ) { Text("收起", style = MaterialTheme.typography.labelMedium) }
        }
    }

    viewerIndex?.let { index ->
        ImageViewerDialog(
            urls = allImages,
            initialIndex = index.coerceIn(0, (allImages.size - 1).coerceAtLeast(0)),
            onDismiss = { viewerIndex = null }
        )
    }
}

private fun buildLine(
    line: String,
    accent: Color,
    textColor: Color,
    codeBackground: Color,
    codeFont: FontFamily,
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
                    SpanStyle(fontFamily = codeFont, background = codeBackground, fontSize = 14.sp)
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

fun imageUrlsOf(files: List<EchoFile>, baseUrl: String = ""): List<String> = files.mapNotNull { ef ->
    ef.file?.takeIf {
        (it.category ?: "image") == "image" || (it.content_type ?: "").startsWith("image/")
    }?.let {
        if (baseUrl.isBlank()) Ech0Api.resolveUrl(it.url)
        else Ech0Api.resolveUrlWithBase(baseUrl, it.url)
    }
}

/** 全屏图片查看：双指缩放、拖动、双击放大；长按保存 / 分享 */
@Composable
fun ImageViewerDialog(urls: List<String>, initialIndex: Int, onDismiss: () -> Unit) {
    if (urls.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, urls.lastIndex),
        pageCount = { urls.size }
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lowData = !top.saltfish.ech0.data.net.NetworkPolicy.canLoadBodyImages()
    var currentScale by remember { mutableFloatStateOf(1f) }
    var actionUrl by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (lowData) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = Color.White.copy(0.7f),
                        modifier = Modifier.size(46.dp)
                    )
                    Text(
                        "低流量模式已开启 · 图片未加载",
                        color = Color.White.copy(0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            } else {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = currentScale <= 1.02f,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    ZoomableImage(
                        url = urls[page],
                        onScaleChanged = { if (page == pager.currentPage) currentScale = it },
                        onLongPress = { actionUrl = urls[page] }
                    )
                }
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
            if (urls.size > 1 && !lowData) {
                Text(
                    "${pager.currentPage + 1} / ${urls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                )
            }
            toast?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp)
                ) { Text(message) }
                androidx.compose.runtime.LaunchedEffect(message) {
                    kotlinx.coroutines.delay(1800)
                    toast = null
                }
            }
        }
    }

    actionUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { if (!busy) actionUrl = null },
            title = { Text("图片操作") },
            text = { Text("可以保存到系统相册，或分享给其他应用。") },
            confirmButton = {
                Row {
                    TextButton(enabled = !busy, onClick = { actionUrl = null }) { Text("取消") }
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val message = shareImage(context, url)
                                busy = false
                                actionUrl = null
                                if (message != null) toast = message
                            }
                        }
                    ) { Text("分享") }
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val message = saveImageToGallery(context, url)
                                busy = false
                                actionUrl = null
                                toast = message
                            }
                        }
                    ) { Text(if (busy) "保存中…" else "保存到相册") }
                }
            }
        )
    }
}

@Composable
private fun ZoomableImage(
    url: String,
    onScaleChanged: (Float) -> Unit,
    onLongPress: () -> Unit
) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, 5f)
        scale = next
        offset = if (next > 1f) offset + panChange else Offset.Zero
        onScaleChanged(next)
    }
    Box(
        Modifier
            .fillMaxSize()
            .transformable(transformState, lockRotationOnZoomPan = true)
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                        onScaleChanged(scale)
                    },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

private suspend fun downloadImage(url: String): Pair<ByteArray, String>? = runCatching {
    HttpClients.image.newCall(Request.Builder().url(url).get().build()).await().use { resp ->
        if (!resp.isSuccessful) return@use null
        val contentType = resp.body?.contentType()?.toString()
            ?: resp.header("Content-Type")
            ?: "image/jpeg"
        resp.bodyBytes() to contentType.substringBefore(';')
    }
}.getOrNull()

private fun extensionFor(contentType: String): String = when {
    contentType.contains("png", true) -> "png"
    contentType.contains("webp", true) -> "webp"
    contentType.contains("gif", true) -> "gif"
    contentType.contains("heic", true) -> "heic"
    else -> "jpg"
}

private suspend fun saveImageToGallery(
    context: android.content.Context,
    url: String
): String {
    val (bytes, contentType) = downloadImage(url) ?: return "下载失败，请稍后重试"
    return runCatching {
        val ext = extensionFor(contentType)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ech0_${System.currentTimeMillis()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, contentType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ech0")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return "无法写入相册"
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return "无法写入相册"
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "已保存到相册"
    }.getOrElse { "保存失败：${it.message ?: "未知错误"}" }
}

private suspend fun shareImage(
    context: android.content.Context,
    url: String
): String? {
    val (bytes, contentType) = downloadImage(url) ?: return "下载失败，请稍后重试"
    return runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "ech0_${System.currentTimeMillis()}.${extensionFor(contentType)}")
        file.writeBytes(bytes)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = contentType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享图片"))
        null
    }.getOrElse { "分享失败：${it.message ?: "未知错误"}" }
}

@Composable
private fun mediaCorner() = MaterialTheme.shapes.medium

@Composable
fun MediaGrid(
    files: List<EchoFile>,
    layout: String?,
    modifier: Modifier = Modifier,
    baseUrl: String = "",
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

    val urls = images.map {
        if (baseUrl.isBlank()) Ech0Api.resolveUrl(it.url)
        else Ech0Api.resolveUrlWithBase(baseUrl, it.url)
    }
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
    if (!top.saltfish.ech0.data.net.NetworkPolicy.canLoadBodyImages()) {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = "低流量模式",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(url)
            .size(feedImageSize())
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
