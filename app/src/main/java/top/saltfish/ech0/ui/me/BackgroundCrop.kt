package top.saltfish.ech0.ui.me

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.saltfish.ech0.ui.common.Ech0Loading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private fun decodeBackgroundBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
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
}.getOrNull()

private fun coverScale(frame: IntSize, bitmap: Bitmap): Float =
    max(frame.width / bitmap.width.toFloat(), frame.height / bitmap.height.toFloat())

private fun clampOffset(offset: Offset, frame: IntSize, bitmap: Bitmap, zoom: Float): Offset {
    val scale = coverScale(frame, bitmap) * zoom
    val maxX = max(0f, (bitmap.width * scale - frame.width) / 2f)
    val maxY = max(0f, (bitmap.height * scale - frame.height) / 2f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

private fun DrawScope.drawBackgroundFrame(
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

private fun saveCroppedBackground(
    context: Context,
    bitmap: Bitmap,
    frame: IntSize,
    zoom: Float,
    offset: Offset
): String? = runCatching {
    val outW = 1080
    val outH = (outW * frame.height / frame.width.toFloat()).roundToInt().coerceIn(1200, 2600)
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
    val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
    val file = File(dir, "custom_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { target.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    target.recycle()
    dir.listFiles()?.forEach { old ->
        if (old.isFile && old.name.startsWith("custom_") && old.absolutePath != file.absolutePath) {
            old.delete()
        }
    }
    file.absolutePath
}.getOrNull()

/** 自定义背景剪裁：固定屏幕比例，双指缩放 + 拖动 */
@Composable
fun BackgroundCropDialog(
    uri: Uri,
    aspectRatio: Float,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(uri) {
        failed = false
        bitmap = withContext(Dispatchers.IO) { decodeBackgroundBitmap(context, uri) }
        if (bitmap == null) failed = true
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
                        "调整背景",
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
                                val path = withContext(Dispatchers.IO) {
                                    saveCroppedBackground(context, bmp, frameSize, zoom, offset)
                                }
                                if (path != null) onDone(path)
                            }
                        }
                    ) {
                        Text("使用", color = Color.White)
                    }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        failed -> Text("无法读取这张图片", color = Color.White)
                        bitmap == null -> Ech0Loading(size = 48.dp, color = Color.White)
                        else -> BoxWithConstraints {
                            val ratio = aspectRatio.coerceIn(0.35f, 1f)
                            val maxW = maxWidth
                            val maxH = maxHeight
                            val frameW = if (maxW / maxH > ratio) maxH * ratio else maxW
                            val frameH = frameW / ratio
                            Box(
                                Modifier
                                    .size(frameW, frameH)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF151515))
                                    .onSizeChanged { frameSize = it }
                                    .pointerInput(bitmap, frameSize) {
                                        detectTransformGestures { _, pan, zoomChange, _ ->
                                            val bmp = bitmap ?: return@detectTransformGestures
                                            zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                            offset = clampOffset(offset + pan, frameSize, bmp, zoom)
                                        }
                                    }
                            ) {
                                val bmp = bitmap ?: return@Box
                                val image = remember(bmp) { bmp.asImageBitmap() }
                                ComposeCanvas(Modifier.fillMaxSize()) {
                                    drawBackgroundFrame(image, bmp, zoom, offset)
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        "双指缩放 · 拖动调整位置",
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "背景会按当前屏幕比例保存",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
