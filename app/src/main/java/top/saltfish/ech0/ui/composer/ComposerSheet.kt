@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package top.saltfish.ech0.ui.composer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import top.saltfish.ech0.SharePayload
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.EchoExtension
import top.saltfish.ech0.data.EchoFileUpsertDto
import top.saltfish.ech0.data.EchoUpsertDto
import top.saltfish.ech0.data.Tag
import top.saltfish.ech0.data.TagUpsertDto
import top.saltfish.ech0.data.isCancellationLike
import top.saltfish.ech0.media.Compressor
import top.saltfish.ech0.publish.DraftPayload
import top.saltfish.ech0.publish.PublishStore
import top.saltfish.ech0.ui.common.formatDateTime
import top.saltfish.ech0.data.userMessage
import top.saltfish.ech0.work.PublishScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.Instant
import java.time.format.DateTimeFormatter

// ---------- 图片项与上传状态 ----------

sealed interface UploadState {
    data object Pending : UploadState
    data class Uploading(val progress: Float) : UploadState
    data class Done(val fileId: String) : UploadState
    data class Failed(val message: String) : UploadState
}

class ImageItem(val uri: Uri, state: UploadState = UploadState.Pending) {
    var state: UploadState by mutableStateOf(state)
}

// 草稿箱「继续编辑」/ 详情页「编辑说说」通道
object ComposerBus {
    var restoreDraft: DraftPayload? = null
    var editEcho: Echo? = null
}

// ---------- 抽屉 → 全屏编辑器 ----------

@Composable
fun ComposerSheet(
    seed: Int,
    shared: SharePayload,
    onClose: () -> Unit,
    onPublished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val isAdmin = Ech0Store.current.isAdmin

    var content by remember { mutableStateOf("") }
    val images = remember { mutableStateListOf<ImageItem>() }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var private by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf("waterfall") }
    var extension by remember { mutableStateOf<EchoExtension?>(null) }
    var createdAtOverride by remember { mutableStateOf<Long?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showTagEditor by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var showExtensionDialog by remember { mutableStateOf(false) }
    var showLayoutPicker by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var draftId by remember { mutableStateOf(DraftPayload().id) }
    var availableTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var quality by remember {
        mutableIntStateOf(if (Ech0Store.current.compressImages) 82 else 0)
    }
    var showQualityDialog by remember { mutableStateOf(false) }
    var editingEcho by remember { mutableStateOf<Echo?>(null) }
    var existingFiles by remember { mutableStateOf<List<EchoFileUpsertDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching { Ech0Api.tags() }.onSuccess { availableTags = it }
    }

    fun launchUpload(item: ImageItem) {
        item.state = UploadState.Pending
        scope.launch {
            item.state = UploadState.Uploading(0f)
            try {
                val result = if (quality <= 0) {
                    Compressor.copyOriginal(context, item.uri)
                } else {
                    Compressor.compressImage(context, item.uri, quality = quality)
                }
                val file = Ech0Api.uploadFile(result.file, result.contentType, "image") { p ->
                    item.state = UploadState.Uploading(p)
                }
                item.state = UploadState.Done(file.id)
            } catch (e: Exception) {
                if (e.isCancellationLike()) return@launch
                item.state = UploadState.Failed(e.userMessage())
            }
        }
    }

    // 编辑说说 / 草稿恢复 / 分享注入
    LaunchedEffect(seed) {
        ComposerBus.editEcho?.let { original ->
            editingEcho = original
            content = original.content
            tags = original.tags.map { it.name }
            private = original.private
            layout = original.layout ?: "waterfall"
            createdAtOverride = original.created_at.takeIf { it > 0 }
            extension = original.extension
            existingFiles = original.echo_files.mapIndexedNotNull { index, ef ->
                (ef.file?.id ?: ef.file_id).takeIf { it.isNotBlank() }
                    ?.let { EchoFileUpsertDto(it, index) }
            }
            ComposerBus.editEcho = null
            return@LaunchedEffect
        }
        ComposerBus.restoreDraft?.let { draft ->
            draftId = draft.id
            content = draft.content
            tags = draft.tags
            private = draft.private
            layout = draft.layout
            createdAtOverride = draft.createdAtOverride
            extension = draft.extensionType?.let { type ->
                val payload = draft.extensionPayloadJson?.let {
                    runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
                }
                EchoExtension(type = type, payload = payload)
            }
            draft.images.take(9).forEach { image ->
                runCatching { Uri.parse(image) }.getOrNull()?.let { uri ->
                    val item = ImageItem(uri)
                    images.add(item)
                    launchUpload(item)
                }
            }
            ComposerBus.restoreDraft = null
            return@LaunchedEffect
        }
        if (seed > 0) {
            if (shared.text.isNotBlank()) content = shared.text
            shared.images.take(9).forEach { uri ->
                val item = ImageItem(uri)
                images.add(item)
                launchUpload(item)
            }
        }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        uris.take(9 - images.size).forEach { uri ->
            val item = ImageItem(uri)
            images.add(item)
            launchUpload(item)
        }
    }

    // 草稿自动保存（600ms 防抖）；编辑已有帖子时不写草稿
    LaunchedEffect(content, images.size, tags, private, layout, extension, createdAtOverride, editingEcho) {
        if (editingEcho != null) return@LaunchedEffect
        if (content.isBlank() && images.isEmpty()) return@LaunchedEffect
        delay(600)
        PublishStore.saveDraft(
            DraftPayload(
                id = draftId,
                content = content,
                images = images.map { it.uri.toString() },
                tags = tags,
                private = private,
                layout = layout,
                extensionType = extension?.type,
                extensionPayloadJson = extension?.payload?.toString(),
                createdAtOverride = createdAtOverride
            )
        )
    }

    fun publish() {
        if (publishing) return
        if (!isAdmin) {
            toast = "仅管理员账号可发布，请先在「我的」登录"
            return
        }
        val editing = editingEcho
        val uploading = images.count {
            it.state is UploadState.Uploading || it.state is UploadState.Pending
        }
        if (content.isBlank() && images.none { it.state is UploadState.Done } && existingFiles.isEmpty()) {
            toast = "写点什么，或添加一张图片"
            return
        }
        if (uploading > 0) {
            toast = "还有 $uploading 张图片在上传中…"
            return
        }
        publishing = true
        scope.launch {
            if (editing != null) {
                // 编辑：不新建、不入队；失败时留在编辑器里，避免重复发帖
                try {
                    Ech0Api.updateEcho(
                        EchoUpsertDto(
                            id = editing.id,
                            content = content,
                            echo_files = existingFiles,
                            layout = layout,
                            private = private,
                            extension = extension,
                            tags = tags.map { TagUpsertDto(name = it) },
                            created_at = createdAtOverride ?: editing.created_at
                        )
                    )
                    ComposerBus.editEcho = null
                    onPublished()
                } catch (e: Exception) {
                    if (e.isCancellationLike()) return@launch
                    toast = "保存失败：${e.userMessage()}"
                } finally {
                    publishing = false
                }
                return@launch
            }

            val files = images.mapIndexedNotNull { index, item ->
                (item.state as? UploadState.Done)?.let { EchoFileUpsertDto(it.fileId, index) }
            }
            val payload = DraftPayload(
                id = draftId,
                content = content,
                images = images.map { it.uri.toString() },
                tags = tags,
                private = private,
                layout = layout,
                extensionType = extension?.type,
                extensionPayloadJson = extension?.payload?.toString(),
                createdAtOverride = createdAtOverride
            )
            try {
                Ech0Api.createEcho(
                    EchoUpsertDto(
                        content = content,
                        echo_files = files,
                        layout = layout,
                        private = private,
                        extension = extension,
                        tags = tags.map { TagUpsertDto(name = it) },
                        created_at = createdAtOverride
                    )
                )
                PublishStore.deleteDraft(draftId)
                onPublished()
            } catch (e: Exception) {
                if (e.isCancellationLike()) return@launch
                PublishStore.enqueue(payload)
                PublishScheduler.kick(context)
                toast = "已加入发布队列，联网后自动发布"
                onPublished()
            } finally {
                publishing = false
            }
        }
    }

    // 展开/半屏：0 = 抽屉，1 = 全屏
    val expandAnim = remember { Animatable(0f) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        expandAnim.animateTo(
            if (expanded) 1f else 0f,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val halfHeight = screenHeight * 0.62f
        val sheetFraction = expandAnim.value.coerceIn(0f, 1f)
        val sheetHeight = lerp(halfHeight, screenHeight, sheetFraction)
        val halfHeightPx = with(density) { halfHeight.toPx() }

        // 遮罩：非全屏时可点关闭
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.30f * (1f - sheetFraction * 0.7f)))
                .pointerInput(Unit) {
                    detectTapGestures { if (expandAnim.value < 0.5f) onClose() }
                }
        )

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = lerp(28.dp, 0.dp, sheetFraction),
                    topEnd = lerp(28.dp, 0.dp, sheetFraction)
                ),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .imePadding()
            ) {
                Column(Modifier.fillMaxSize()) {
                    // 拖拽把手（上拉全屏 / 下拉关闭）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        val current = expandAnim.value
                                        scope.launch {
                                            if (current < -0.12f) {
                                                onClose()
                                            } else {
                                                val target = if (current > 0.5f) 1f else 0f
                                                expandAnim.animateTo(
                                                    target,
                                                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 380f)
                                                )
                                                expanded = target == 1f
                                            }
                                        }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        expandAnim.snapTo(
                                            (expandAnim.value - dragAmount / halfHeightPx)
                                                .coerceIn(-0.25f, 1f)
                                        )
                                    }
                                }
                            }
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .width(40.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        )
                    }

                    // 顶栏（QQ空间三段式：取消 | 写说说 | 发表）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onClose) { Text("取消") }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (editingEcho != null) "编辑说说" else "写说说",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { publish() },
                            enabled = isAdmin && !publishing
                        ) {
                            Text(
                                when {
                                    publishing && editingEcho != null -> "保存中…"
                                    publishing -> "发布中…"
                                    editingEcho != null -> "保存"
                                    else -> "发表"
                                }
                            )
                        }
                    }

                    // 非管理员提示条（发布禁用的原因就近呈现）
                    if (!isAdmin) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Lock, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "仅管理员可发表说说，当前账号无发布权限",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // 正文 + 编辑区
                    Box(Modifier.weight(1f)) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                        ) {
                            BasicTextField(
                                value = content,
                                onValueChange = { content = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    lineHeight = 27.sp
                                ).copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    Box {
                                        if (content.isBlank()) {
                                            Text(
                                                "此刻想说什么…",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        inner()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp)
                            )
                            Spacer(Modifier.height(12.dp))

                            if (editingEcho == null) {
                                ImageGridEditor(
                                    images = images,
                                    onAdd = {
                                        pickImages.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onRetry = { launchUpload(it) },
                                    onRemove = { images.remove(it) },
                                    onLayoutClick = { showLayoutPicker = true }
                                )
                                AssistChip(
                                    onClick = { showQualityDialog = true },
                                    label = {
                                        Text(
                                            "画质：${when (quality) {
                                                0 -> "原图"
                                                92 -> "高清"
                                                else -> "标准"
                                            }}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Tune,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            } else if (existingFiles.isNotEmpty()) {
                                Text(
                                    "图片：保留原有 ${existingFiles.size} 张（编辑模式暂不支持增删图片）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(10.dp))

                            // 标签（增删时高度平滑过渡）
                            FlowRow(
                                Modifier.fillMaxWidth().animateContentSize(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tags.forEach { tag ->
                                    InputChip(
                                        selected = false,
                                        onClick = { tags = tags - tag },
                                        label = { Text("#$tag") },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Filled.Close, contentDescription = "移除",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                                if (tags.size < 5) {
                                    AssistChip(
                                        onClick = { showTagEditor = !showTagEditor },
                                        label = { Text("+ 话题") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Tag, contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                            if (showTagEditor) {
                                val unselected = availableTags.filter { it.name !in tags }
                                if (unselected.isNotEmpty()) {
                                    Text(
                                        "选择已有话题",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
                                        Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        unselected.take(20).forEach { tag ->
                                            AssistChip(
                                                onClick = {
                                                    if (tags.size < 5) tags = tags + tag.name
                                                },
                                                label = { Text("#${tag.name}", style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = tagInput,
                                        onValueChange = { tagInput = it },
                                        singleLine = true,
                                        prefix = { Text("#") },
                                        placeholder = { Text("话题名") },
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    FilledIconButton(
                                        onClick = {
                                            val t = tagInput.trim().removePrefix("#").removeSuffix("#")
                                            if (t.isNotBlank() && t !in tags && tags.size < 5) {
                                                tags = tags + t
                                            }
                                            tagInput = ""
                                            showTagEditor = false
                                        }
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "添加")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            // 扩展卡状态
                            if (extension != null) {
                                StatusRow(
                                    text = "卡片：${extension?.type} · 点按编辑",
                                    onClick = { showExtensionDialog = true },
                                    onClear = { extension = null }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            if (createdAtOverride != null) {
                                StatusRow(
                                    text = "补记时间：${formatDateTime(createdAtOverride!!)}",
                                    onClear = { createdAtOverride = null }
                                )
                            }
                            Spacer(Modifier.height(70.dp))
                        }
                    }

                    // 底部工具条（一行折叠全部辅助功能）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ToolIcon(Icons.Filled.Image, "图片") {
                            pickImages.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        ToolIcon(Icons.Filled.Tag, "话题") { showTagEditor = !showTagEditor }
                        ToolIcon(
                            if (private) Icons.Filled.Lock else Icons.Filled.Public,
                            if (private) "私密" else "公开"
                        ) { private = !private }
                        ToolIcon(Icons.Filled.Widgets, "卡片") { showExtensionDialog = true }
                        ToolIcon(Icons.Filled.Schedule, "补记") { showTimeDialog = true }
                        Spacer(Modifier.weight(1f))
                        if (!expanded) {
                            ToolIcon(Icons.Filled.ExpandLess, "全屏") { expanded = true }
                        }
                    }
                }
            }
        }
    }

    if (showLayoutPicker) {
        LayoutPickerDialog(current = layout) { picked -> layout = picked; showLayoutPicker = false }
    }
    if (showExtensionDialog) {
        ExtensionEditorDialog(
            initial = extension,
            onSave = { extension = it; showExtensionDialog = false },
            onDismiss = { showExtensionDialog = false }
        )
    }
    if (showTimeDialog) {
        TimeOverrideDialog(initial = createdAtOverride) { ts ->
            createdAtOverride = ts
            showTimeDialog = false
        }
    }
    if (showQualityDialog) {
        QualityPickerDialog(current = quality) { picked ->
            quality = picked
            showQualityDialog = false
        }
    }

    // Snackbar：Expressive 弹簧弹入 / 缩小淡出（容器常驻以播放退出动画）
    val lastToast = remember { mutableStateOf("") }
    toast?.let { message ->
        lastToast.value = message
        LaunchedEffect(message) {
            delay(1800)
            toast = null
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = toast != null,
            enter = scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(tween(120)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f)
        ) {
            Snackbar { Text(lastToast.value) }
        }
    }
}

@Composable
private fun StatusRow(text: String, onClick: (() -> Unit)? = null, onClear: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            Modifier
                .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Close, contentDescription = "清除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon, label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- 九宫格编辑器（QQ空间式：末位 + 号，逐张进度，单张重试） ----------

@Composable
fun ImageGridEditor(
    images: List<ImageItem>,
    onAdd: () -> Unit,
    onRetry: (ImageItem) -> Unit,
    onRemove: (ImageItem) -> Unit,
    onLayoutClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        (images + listAddSlot(images.size < 9)).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    if (item == null) {
                        // 加号位
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(onClick = onAdd),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Add, "添加图片",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            when (val st = item.state) {
                                is UploadState.Uploading -> {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Black.copy(0.35f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularWavyProgressIndicator(
                                            progress = { st.progress },
                                            color = Color.White,
                                            trackColor = Color.White.copy(0.3f),
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }

                                is UploadState.Failed -> {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(0.7f))
                                            .clickable { onRetry(item) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Refresh, "重试",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                is UploadState.Done -> {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(5.dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2E7D32))
                                    )
                                }

                                UploadState.Pending -> {}
                            }
                            // 删除
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(0.55f))
                                    .clickable { onRemove(item) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Close, "删除",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f).aspectRatio(1f).padding(4.dp))
                }
            }
        }
    }
}

private fun listAddSlot(show: Boolean): List<ImageItem?> = if (show) listOf(null) else emptyList()

// ---------- 布局选择 ----------

private val layoutOptions = listOf(
    "waterfall" to "瀑布流",
    "grid" to "网格",
    "horizontal" to "横向",
    "carousel" to "轮播",
    "stack" to "堆叠",
    "none" to "隐藏"
)

@Composable
fun LayoutPickerDialog(current: String = "waterfall", onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("图片布局") },
        confirmButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                layoutOptions.forEach { (value, label) ->
                    Surface(
                        onClick = { onPick(value) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (value == current) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    )
}

// ---------- 扩展卡编辑（6 种，payload 字段对照 web/src/typings/echo.d.ts） ----------

@Composable
fun ExtensionEditorDialog(
    initial: EchoExtension?,
    onSave: (EchoExtension) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(initial?.type ?: "WEBSITE") }
    var f1 by remember { mutableStateOf("") }
    var f2 by remember { mutableStateOf("") }
    var f3 by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locationError by remember { mutableStateOf<String?>(null) }

    fun applyLocation(latitude: Double, longitude: Double) {
        f2 = String.format(java.util.Locale.US, "%.6f", latitude)
        f3 = String.format(java.util.Locale.US, "%.6f", longitude)
        if (f1.isBlank()) f1 = "当前位置"
    }

    suspend fun awaitLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                if (cont.isActive) cont.resume(location)
            }
        }

    fun fetchLocation() {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            locationError = "设备不支持定位"
            return
        }
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            locationError = "定位权限未授予"
            return
        }
        // Android 12+ 用户可能只给「大致位置」：此时不能用 GPS provider
        val providers = buildList {
            if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (fine && manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                add(LocationManager.PASSIVE_PROVIDER)
            }
        }
        if (providers.isEmpty()) {
            locationError = if (!fine) {
                "只授予了大致位置：请允许精确位置，或在系统设置里打开网络定位"
            } else {
                "请先在系统设置里打开定位"
            }
            return
        }
        locationError = "正在定位…"
        scope.launch {
            var found: Location? = null
            for (provider in providers) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    found = runCatching {
                        withTimeoutOrNull(8000) { awaitLocation(manager, provider) }
                    }.getOrNull()
                }
                if (found == null) {
                    found = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                }
                if (found != null) break
            }
            if (found != null) {
                applyLocation(found.latitude, found.longitude)
                locationError = null
            } else {
                locationError = "定位超时：请到开阔处或打开 Wi-Fi/移动网络后重试"
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) fetchLocation() else locationError = "定位权限被拒绝"
    }

    fun requestLocation() {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fine || coarse) fetchLocation()
        else locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // 回填
    LaunchedEffect(type) {
        val p = initial?.takeIf { it.type == type }?.payload
        f1 = when (type) {
            "MUSIC" -> p?.get("url")?.toString()?.trim('"').orEmpty()
            "VIDEO" -> p?.get("videoId")?.toString()?.trim('"').orEmpty()
            "GITHUBPROJ" -> p?.get("repoUrl")?.toString()?.trim('"').orEmpty()
            "WEBSITE" -> p?.get("site")?.toString()?.trim('"').orEmpty()
            "LOCATION" -> p?.get("placeholder")?.toString()?.trim('"').orEmpty()
            "TWEET" -> p?.get("url")?.toString()?.trim('"').orEmpty()
            else -> ""
        }
        f2 = when (type) {
            "WEBSITE" -> p?.get("title")?.toString()?.trim('"').orEmpty()
            "LOCATION" -> p?.get("latitude")?.toString().orEmpty()
            "TWEET" -> p?.get("username")?.toString()?.trim('"').orEmpty()
            else -> ""
        }
        f3 = when (type) {
            "LOCATION" -> p?.get("longitude")?.toString().orEmpty()
            "TWEET" -> p?.get("statusId")?.toString()?.trim('"').orEmpty()
            else -> ""
        }
    }

    val labels: Triple<String, String?, String?> = when (type) {
        "MUSIC" -> Triple("音乐链接", null, null)
        "VIDEO" -> Triple("视频 ID（B站 BV 号 / YouTube ID）", null, null)
        "GITHUBPROJ" -> Triple("仓库地址", null, null)
        "WEBSITE" -> Triple("网址", "标题", null)
        "LOCATION" -> Triple("地点名", "纬度", "经度")
        "TWEET" -> Triple("推文链接", "用户名", "Status ID")
        else -> Triple("", null, null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扩展卡片") },
        confirmButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("WEBSITE", "VIDEO", "MUSIC", "GITHUBPROJ", "LOCATION", "TWEET").forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = f1,
                    onValueChange = { f1 = it },
                    label = { Text(labels.first) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                labels.second?.let {
                    OutlinedTextField(
                        value = f2,
                        onValueChange = { f2 = it },
                        label = { Text(it) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                labels.third?.let {
                    OutlinedTextField(
                        value = f3,
                        onValueChange = { f3 = it },
                        label = { Text(it) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (type == "LOCATION") {
                    TextButton(onClick = { requestLocation() }) {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("使用当前位置")
                    }
                    locationError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            val payload = when (type) {
                                "MUSIC" -> buildJsonObject { put("url", f1.trim()) }
                                "VIDEO" -> buildJsonObject { put("videoId", f1.trim()) }
                                "GITHUBPROJ" -> buildJsonObject { put("repoUrl", f1.trim()) }
                                "WEBSITE" -> buildJsonObject {
                                    put("site", f1.trim())
                                    put("title", f2.trim().ifBlank { f1.trim() })
                                }
                                "LOCATION" -> buildJsonObject {
                                    put("placeholder", f1.trim())
                                    put("latitude", f2.trim().toDoubleOrNull() ?: 0.0)
                                    put("longitude", f3.trim().toDoubleOrNull() ?: 0.0)
                                }
                                "TWEET" -> buildJsonObject {
                                    put("url", f1.trim())
                                    put("username", f2.trim())
                                    put("statusId", f3.trim())
                                }
                                else -> buildJsonObject { }
                            }
                            onSave(EchoExtension(type = type, payload = payload))
                        }
                    ) { Text("保存") }
                }
            }
        }
    )
}

// ---------- 上传画质 ----------

@Composable
fun QualityPickerDialog(current: Int, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = { onPick(current) },
        title = { Text("上传画质") },
        confirmButton = {
            TextButton(onClick = { onPick(current) }) { Text("关闭") }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "对之后添加的图片生效；已上传的图片需要移除后重新添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    82 to "标准（推荐，体积小）",
                    92 to "高清（体积较大）",
                    0 to "原图（不压缩）"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = current == value,
                        onClick = { onPick(value) },
                        label = { Text(label) }
                    )
                }
            }
        }
    )
}

// ---------- 补记时间 ----------

@Composable
fun TimeOverrideDialog(initial: Long? = null, onPick: (Long?) -> Unit) {
    val zone = ZoneId.systemDefault()
    var selected by remember(initial) {
        mutableStateOf(
            initial?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), zone) }
                ?: LocalDateTime.now()
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日") }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }

    AlertDialog(
        onDismissRequest = { onPick(initial) },
        title = { Text("补记时间") },
        confirmButton = {
            TextButton(onClick = { onPick(selected.atZone(zone).toEpochSecond()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { onPick(null) }) { Text("清除补记") }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "补记 = 修改这条说说的发布时间，会按你选择的日期和时间发布。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    onClick = { showDatePicker = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(dateFormat.format(selected), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text("选择日期", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Surface(
                    onClick = { showTimePicker = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(timeFormat.format(selected), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text("选择时间", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = { selected = LocalDateTime.now() }) {
                    Text("使用当前时间", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = selected.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        selected = LocalDateTime.of(date, selected.toLocalTime())
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = selected.hour,
            initialMinute = selected.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            confirmButton = {
                TextButton(onClick = {
                    selected = selected.withHour(timeState.hour).withMinute(timeState.minute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            }
        )
    }
}
