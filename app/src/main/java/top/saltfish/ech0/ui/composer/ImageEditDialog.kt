package top.saltfish.ech0.ui.composer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private fun decodeEditBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val sample = max(1, (max(w, h) / 2048f).toInt())
            decoder.setTargetSampleSize(sample)
        }
    } else {
        @Suppress("DEPRECATION")
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }
}.getOrNull() ?: runCatching {
    // file:// 草稿图在部分设备上 ImageDecoder 会失败，回退到 BitmapFactory
    if (uri.scheme == "file") {
        BitmapFactory.decodeFile(uri.path)
    } else {
        @Suppress("DEPRECATION")
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }
}.getOrNull()

private fun coverScale(frame: IntSize, bitmap: Bitmap): Float =
    max(frame.width / bitmap.width.toFloat(), frame.height / bitmap.height.toFloat())

private fun clampOffset(offset: Offset, frame: IntSize, bitmap: Bitmap, zoom: Float): Offset {
    val scale = coverScale(frame, bitmap) * zoom
    val maxX = max(0f, (bitmap.width * scale - frame.width) / 2f)
    val maxY = max(0f, (bitmap.height * scale - frame.height) / 2f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

private fun DrawScope.drawEditFrame(
    image: ImageBitmap,
    source: Bitmap,
    zoom: Float,
    offset: Offset
) {
    val scale = coverScale(IntSize(size.width.roundToInt(), size.height.roundToInt()), source) * zoom
    val drawW = source.width * scale
    val drawH = source.height * scale
    val left = (size.width - drawW) / 2f + offset.x
    val top = (size.height - drawH) / 2f + offset.y
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(source.width, source.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
        filterQuality = FilterQuality.High
    )
}

private fun saveEditedImage(
    context: Context,
    bitmap: Bitmap,
    frame: IntSize,
    zoom: Float,
    offset: Offset
): Uri? = runCatching {
    val maxEdge = 2048
    val scaleOut = maxEdge / max(frame.width, frame.height).toFloat()
    val outW = (frame.width * scaleOut).roundToInt().coerceAtLeast(1)
    val outH = (frame.height * scaleOut).roundToInt().coerceAtLeast(1)
    val target = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(target)
    val scale = outW / frame.width.toFloat()
    val cover = coverScale(frame, bitmap) * zoom
    val drawW = bitmap.width * cover
    val drawH = bitmap.height * cover
    val left = (frame.width - drawW) / 2f + offset.x
    val top = (frame.height - drawH) / 2f + offset.y
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(
        bitmap,
        null,
        RectF(left * scale, top * scale, (left + drawW) * scale, (top + drawH) * scale),
        paint
    )
    val dir = File(context.cacheDir, "edited_images").apply { mkdirs() }
    val file = File(dir, "edit_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { target.compress(Bitmap.CompressFormat.PNG, 100, it) }
    target.recycle()
    Uri.fromFile(file)
}.getOrNull()

private fun rotateBitmap(source: Bitmap): Bitmap {
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

/**
 * 编辑器内简单图片编辑：比例裁剪 + 90° 旋转。
 * 结果写入 cache/edited_images，交给上传画质/格式再编码。
 */
@Composable
fun ImageEditDialog(
    uri: Uri,
    onDone: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    var ratioMode by remember { mutableIntStateOf(0) }

    val imageRatio = bitmap?.let { it.width / it.height.toFloat() } ?: 1f
    val ratio = when (ratioMode) {
        1 -> 1f
        2 -> 4f / 3f
        3 -> 16f / 9f
        else -> imageRatio
    }

    LaunchedEffect(uri) {
        failed = false
        bitmap = withContext(Dispatchers.IO) { decodeEditBitmap(context, uri) }
        if (bitmap == null) failed = true
    }
    LaunchedEffect(ratioMode) {
        zoom = 1f
        offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = Color.White)
                    }
                    Text(
                        "编辑图片",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(
                        enabled = bitmap != null && frameSize.width > 0,
                        onClick = {
                            val bmp = bitmap ?: return@TextButton
                            scope.launch {
                                val newUri = withContext(Dispatchers.IO) {
                                    saveEditedImage(context, bmp, frameSize, zoom, offset)
                                }
                                if (newUri != null) onDone(newUri)
                            }
                        }
                    ) {
                        Text("完成", color = Color.White)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        0 to "原图",
                        1 to "1:1",
                        2 to "4:3",
                        3 to "16:9"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = ratioMode == mode,
                            onClick = { ratioMode = mode },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    IconButton(
                        onClick = {
                            val current = bitmap ?: return@IconButton
                            bitmap = rotateBitmap(current)
                            zoom = 1f
                            offset = Offset.Zero
                        }
                    ) {
                        Icon(
                            Icons.Filled.RotateRight,
                            contentDescription = "旋转 90 度",
                            tint = Color.White
                        )
                    }
                }
                Text(
                    "拖动调整位置，双指缩放；完成后按当前比例裁剪并上传。",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        failed -> Text("无法读取这张图片", color = Color.White)
                        bitmap == null -> Text("正在读取图片…", color = Color.White)
                        else -> BoxWithConstraints {
                            val maxW = maxWidth
                            val maxH = maxHeight
                            val safeRatio = ratio.coerceIn(0.35f, 2.5f)
                            val frameW = if (maxW / maxH > safeRatio) maxH * safeRatio else maxW
                            val frameH = frameW / safeRatio
                            Box(
                                Modifier
                                    .size(frameW, frameH)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF151515))
                                    .onSizeChanged { frameSize = it }
                                    .pointerInput(ratio, bitmap) {
                                        val bmp = bitmap ?: return@pointerInput
                                        detectTransformGestures { _, pan, gestureZoom, _ ->
                                            zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                            offset = clampOffset(offset + pan, frameSize, bmp, zoom)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                bitmap?.let { bmp ->
                                    val image = remember(bmp) { bmp.asImageBitmap() }
                                    ComposeCanvas(Modifier.fillMaxSize()) {
                                        drawEditFrame(image, bmp, zoom, offset)
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.navigationBarsPadding().padding(bottom = 8.dp))
            }
        }
    }
}
