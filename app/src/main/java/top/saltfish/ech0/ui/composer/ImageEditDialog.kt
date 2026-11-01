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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.saltfish.ech0.ui.common.Ech0ChoiceGroup
import top.saltfish.ech0.ui.common.LocalReduceMotion
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private enum class ImageEditTool { Crop, Brush, Mosaic }

private data class EditStroke(
    val tool: ImageEditTool,
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

private val BrushColors = listOf(
    Color.White,
    Color.Black,
    Color(0xFFE53935),
    Color(0xFFFFB300),
    Color(0xFF43A047),
    Color(0xFF1E88E5),
    Color(0xFF8E24AA)
)

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

private fun DrawScope.drawEditStrokes(
    strokes: List<EditStroke>,
    source: Bitmap,
    frame: IntSize,
    zoom: Float,
    offset: Offset,
    mosaicBlock: Float
) {
    if (frame.width <= 0 || frame.height <= 0) return
    val scale = coverScale(frame, source) * zoom
    val drawW = source.width * scale
    val drawH = source.height * scale
    val left = (frame.width - drawW) / 2f + offset.x
    val top = (frame.height - drawH) / 2f + offset.y
    strokes.forEach { stroke ->
        when (stroke.tool) {
            ImageEditTool.Brush -> {
                if (stroke.points.size == 1) {
                    drawCircle(
                        color = stroke.color,
                        radius = stroke.width / 2f,
                        center = stroke.points.first()
                    )
                } else {
                    stroke.points.zipWithNext().forEach { (a, b) ->
                        drawLine(
                            color = stroke.color,
                            start = a,
                            end = b,
                            strokeWidth = stroke.width,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            ImageEditTool.Mosaic -> {
                val block = mosaicBlock.coerceAtLeast(2f)
                stroke.points.forEach { p ->
                    val sx = ((p.x - left) / scale).roundToInt().coerceIn(0, source.width - 1)
                    val sy = ((p.y - top) / scale).roundToInt().coerceIn(0, source.height - 1)
                    val color = Color(source.getPixel(sx, sy))
                    drawRect(
                        color = color,
                        topLeft = Offset(p.x - block / 2f, p.y - block / 2f),
                        size = Size(block, block)
                    )
                }
            }
            ImageEditTool.Crop -> Unit
        }
    }
}

private fun saveEditedImage(
    context: Context,
    bitmap: Bitmap,
    frame: IntSize,
    zoom: Float,
    offset: Offset,
    strokes: List<EditStroke>,
    mosaicBlock: Float
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

    val base = target.copy(Bitmap.Config.ARGB_8888, true)
    strokes.forEach { stroke ->
        when (stroke.tool) {
            ImageEditTool.Brush -> {
                val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color.toArgb()
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = stroke.width * scale
                }
                val points = stroke.points.map { Offset(it.x * scale, it.y * scale) }
                if (points.size == 1) {
                    brush.style = Paint.Style.FILL
                    canvas.drawCircle(points.first().x, points.first().y, brush.strokeWidth / 2f, brush)
                } else {
                    points.zipWithNext().forEach { (a, b) ->
                        canvas.drawLine(a.x, a.y, b.x, b.y, brush)
                    }
                }
            }
            ImageEditTool.Mosaic -> {
                val block = (mosaicBlock * scale).coerceAtLeast(2f)
                val mosaic = Paint()
                stroke.points.forEach { p ->
                    val x = (p.x * scale).roundToInt().coerceIn(0, outW - 1)
                    val y = (p.y * scale).roundToInt().coerceIn(0, outH - 1)
                    mosaic.color = base?.getPixel(x, y) ?: android.graphics.Color.TRANSPARENT
                    canvas.drawRect(
                        x - block / 2f,
                        y - block / 2f,
                        x + block / 2f,
                        y + block / 2f,
                        mosaic
                    )
                }
            }
            ImageEditTool.Crop -> Unit
        }
    }
    base?.recycle()

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
 * 图片编辑的两个状态：
 * 1. 预览：全屏查看，左下角“编辑”进入内容编辑；
 * 2. 内容编辑：裁剪 / 画笔 / 马赛克，完成后写回 cache/edited_images。
 *
 * 网格里的“管理编辑态”（删除 / 排序）在 ComposerSheet 的 ImageGridEditor 中单独维护。
 */
@Composable
fun ImageEditDialog(
    uri: Uri,
    onDone: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(ImageEditTool.Crop) }
    var ratioMode by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    var strokes by remember { mutableStateOf(emptyList<EditStroke>()) }
    var currentStroke by remember { mutableStateOf<EditStroke?>(null) }
    var brushColor by remember { mutableStateOf(Color.White) }
    var brushWidthDp by remember { mutableFloatStateOf(6f) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val imageRatio = bitmap?.let { it.width / it.height.toFloat() } ?: 1f
    val ratio = when (ratioMode) {
        1 -> 1f
        2 -> 4f / 3f
        3 -> 16f / 9f
        else -> imageRatio
    }
    val brushWidthPx = with(density) { brushWidthDp.dp.toPx() }
    val mosaicBlockPx = with(density) { (brushWidthDp * 1.4f).dp.toPx() }

    LaunchedEffect(uri) {
        failed = false
        bitmap = withContext(Dispatchers.IO) { decodeEditBitmap(context, uri) }
        if (bitmap == null) failed = true
    }
    LaunchedEffect(ratioMode) {
        zoom = 1f
        offset = Offset.Zero
        strokes = emptyList()
        currentStroke = null
    }

    Dialog(
        onDismissRequest = { if (editing) editing = false else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            val editorScheme = MaterialTheme.colorScheme.copy(
                surface = Color.Black,
                onSurface = Color.White,
                onSurfaceVariant = Color.White.copy(alpha = 0.72f),
                surfaceContainer = Color(0xFF1B1B1B),
                surfaceContainerHigh = Color(0xFF242424),
                surfaceContainerHighest = Color(0xFF2E2E2E),
                outline = Color.White.copy(alpha = 0.45f),
                outlineVariant = Color.White.copy(alpha = 0.22f)
            )
            MaterialTheme(colorScheme = editorScheme) {
            BackHandler(enabled = editing) { editing = false }
            AnimatedContent(
                targetState = editing,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.98f))
                            .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 1.02f))
                    }
                },
                label = "imageEditStage"
            ) { isEditing ->
                if (!isEditing) {
                    ImagePreviewStage(
                        bitmap = bitmap,
                        failed = failed,
                        onClose = onDismiss,
                        onEdit = { editing = true }
                    )
                } else {
                    ImageEditStage(
                        bitmap = bitmap,
                        failed = failed,
                        tool = tool,
                        onToolChange = { tool = it; currentStroke = null },
                        ratioMode = ratioMode,
                        onRatioChange = { ratioMode = it },
                        zoom = zoom,
                        offset = offset,
                        onTransform = { pan, gestureZoom ->
                            val bmp = bitmap ?: return@ImageEditStage
                            zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                            offset = clampOffset(offset + pan, frameSize, bmp, zoom)
                        },
                        frameSize = frameSize,
                        onFrameSize = { frameSize = it },
                        strokes = strokes,
                        currentStroke = currentStroke,
                        brushColor = brushColor,
                        onBrushColor = { brushColor = it },
                        brushWidthDp = brushWidthDp,
                        onBrushWidth = { brushWidthDp = it },
                        mosaicBlockPx = mosaicBlockPx,
                        onStrokeStart = { p ->
                            currentStroke = EditStroke(
                                tool = tool,
                                points = listOf(p),
                                color = brushColor,
                                width = brushWidthPx
                            )
                        },
                        onStrokeMove = { p ->
                            currentStroke = currentStroke?.let { it.copy(points = it.points + p) }
                        },
                        onStrokeEnd = {
                            currentStroke?.let { strokes = strokes + it }
                            currentStroke = null
                        },
                        onStrokeCancel = { currentStroke = null },
                        onUndo = { strokes = strokes.dropLast(1) },
                        onRotate = {
                            bitmap?.let {
                                bitmap = rotateBitmap(it)
                                zoom = 1f
                                offset = Offset.Zero
                                strokes = emptyList()
                                currentStroke = null
                            }
                        },
                        onBack = { editing = false },
                        saving = saving,
                        saveError = saveError,
                        onSave = {
                            val bmp = bitmap ?: return@ImageEditStage
                            if (saving || frameSize.width <= 0) return@ImageEditStage
                            saving = true
                            saveError = null
                            scope.launch {
                                val newUri = withContext(Dispatchers.IO) {
                                    saveEditedImage(
                                        context = context,
                                        bitmap = bmp,
                                        frame = frameSize,
                                        zoom = zoom,
                                        offset = offset,
                                        strokes = strokes,
                                        mosaicBlock = mosaicBlockPx
                                    )
                                }
                                saving = false
                                if (newUri != null) onDone(newUri) else saveError = "保存失败，请重试"
                            }
                        }
                    )
                }
            }
        }
    }
}
}

@Composable
private fun ImagePreviewStage(
    bitmap: Bitmap?,
    failed: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when {
            failed -> Text(
                "无法读取这张图片",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
            bitmap == null -> Text(
                "正在读取图片…",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
            else -> {
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Surface(
            onClick = onClose,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
            }
        }
        FilledTonalButton(
            onClick = onEdit,
            enabled = bitmap != null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("编辑")
        }
    }
}

@Composable
private fun ImageEditStage(
    bitmap: Bitmap?,
    failed: Boolean,
    tool: ImageEditTool,
    onToolChange: (ImageEditTool) -> Unit,
    ratioMode: Int,
    onRatioChange: (Int) -> Unit,
    zoom: Float,
    offset: Offset,
    onTransform: (Offset, Float) -> Unit,
    frameSize: IntSize,
    onFrameSize: (IntSize) -> Unit,
    strokes: List<EditStroke>,
    currentStroke: EditStroke?,
    brushColor: Color,
    onBrushColor: (Color) -> Unit,
    brushWidthDp: Float,
    onBrushWidth: (Float) -> Unit,
    mosaicBlockPx: Float,
    onStrokeStart: (Offset) -> Unit,
    onStrokeMove: (Offset) -> Unit,
    onStrokeEnd: () -> Unit,
    onStrokeCancel: () -> Unit,
    onUndo: () -> Unit,
    onRotate: () -> Unit,
    onBack: () -> Unit,
    saving: Boolean,
    saveError: String?,
    onSave: () -> Unit
) {
    val density = LocalDensity.current
    val imageRatio = bitmap?.let { it.width / it.height.toFloat() } ?: 1f
    val ratio = when (ratioMode) {
        1 -> 1f
        2 -> 4f / 3f
        3 -> 16f / 9f
        else -> imageRatio
    }
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnStrokeStart by rememberUpdatedState(onStrokeStart)
    val currentOnStrokeMove by rememberUpdatedState(onStrokeMove)
    val currentOnStrokeEnd by rememberUpdatedState(onStrokeEnd)
    val currentOnStrokeCancel by rememberUpdatedState(onStrokeCancel)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.35f),
                contentColor = Color.White,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回预览",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                "编辑图片",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            FilledTonalButton(
                onClick = onSave,
                enabled = bitmap != null && frameSize.width > 0 && !saving
            ) {
                Text(if (saving) "保存中…" else "完成")
            }
        }

        Ech0ChoiceGroup(
            options = listOf(
                ImageEditTool.Crop to "裁剪",
                ImageEditTool.Brush to "画笔",
                ImageEditTool.Mosaic to "马赛克"
            ),
            selected = tool,
            onSelect = onToolChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        when (tool) {
            ImageEditTool.Crop -> CropTools(
                ratioMode = ratioMode,
                onRatioChange = onRatioChange,
                onRotate = onRotate
            )
            ImageEditTool.Brush, ImageEditTool.Mosaic -> DrawTools(
                tool = tool,
                brushColor = brushColor,
                onBrushColor = onBrushColor,
                brushWidthDp = brushWidthDp,
                onBrushWidth = onBrushWidth,
                onUndo = onUndo,
                canUndo = strokes.isNotEmpty()
            )
        }

        if (saveError != null) {
            Text(
                saveError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

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
                            .clip(MaterialTheme.shapes.largeIncreased)
                            .background(Color(0xFF151515))
                            .onSizeChanged(onFrameSize)
                            .pointerInput(tool, ratio, bitmap) {
                                if (tool == ImageEditTool.Crop) {
                                    detectTransformGestures { _, pan, gestureZoom, _ ->
                                        currentOnTransform(pan, gestureZoom)
                                    }
                                } else {
                                    detectDragGestures(
                                        onDragStart = currentOnStrokeStart,
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentOnStrokeMove(change.position)
                                        },
                                        onDragEnd = currentOnStrokeEnd,
                                        onDragCancel = currentOnStrokeCancel
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        bitmap.let { bmp ->
                            val image = remember(bmp) { bmp.asImageBitmap() }
                            ComposeCanvas(Modifier.fillMaxSize()) {
                                drawEditFrame(image, bmp, zoom, offset)
                                drawEditStrokes(
                                    strokes = strokes + listOfNotNull(currentStroke),
                                    source = bmp,
                                    frame = IntSize(
                                        size.width.roundToInt(),
                                        size.height.roundToInt()
                                    ),
                                    zoom = zoom,
                                    offset = offset,
                                    mosaicBlock = mosaicBlockPx
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            when (tool) {
                ImageEditTool.Crop -> "拖动调整位置，双指缩放；完成后按当前比例裁剪并上传。"
                ImageEditTool.Brush -> "在图片上拖动绘制；可切换颜色和粗细。"
                ImageEditTool.Mosaic -> "在图片上拖动打码；粗细控制马赛克块大小。"
            },
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        Box(Modifier.navigationBarsPadding().padding(bottom = 8.dp))
    }
}

@Composable
private fun CropTools(
    ratioMode: Int,
    onRatioChange: (Int) -> Unit,
    onRotate: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ech0ChoiceGroup(
            options = listOf(0 to "原图", 1 to "1:1", 2 to "4:3", 3 to "16:9"),
            selected = ratioMode,
            onSelect = onRatioChange,
            modifier = Modifier.weight(1f)
        )
        FilledTonalIconButton(onClick = onRotate) {
            Icon(
                Icons.Filled.RotateRight,
                contentDescription = "旋转 90 度",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DrawTools(
    tool: ImageEditTool,
    brushColor: Color,
    onBrushColor: (Color) -> Unit,
    brushWidthDp: Float,
    onBrushWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean
) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = Color.White.copy(alpha = 0.08f),
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tool == ImageEditTool.Brush) {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrushColors.forEach { color ->
                            Surface(
                                onClick = { onBrushColor(color) },
                                shape = CircleShape,
                                color = color,
                                border = if (color == brushColor) {
                                    BorderStroke(2.dp, Color.White)
                                } else {
                                    BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {}
                        }
                    }
                } else {
                    Text(
                        "马赛克",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.weight(1f)
                    )
                }
                FilledTonalIconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "撤销",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "粗细",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Slider(
                    value = brushWidthDp,
                    onValueChange = onBrushWidth,
                    valueRange = 3f..24f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    "${brushWidthDp.roundToInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
