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
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ExpandLess
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt
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
import top.saltfish.ech0.data.FileDto
import top.saltfish.ech0.data.Tag
import top.saltfish.ech0.data.TagUpsertDto
import top.saltfish.ech0.data.isCancellationLike
import top.saltfish.ech0.data.net.HttpClients
import top.saltfish.ech0.data.net.NetworkPolicy
import top.saltfish.ech0.data.net.await
import top.saltfish.ech0.data.repo.SiteStore
import top.saltfish.ech0.media.Compressor
import top.saltfish.ech0.publish.DraftFiles
import top.saltfish.ech0.publish.DraftPayload
import top.saltfish.ech0.publish.DraftUploadRegistry
import top.saltfish.ech0.publish.PendingFileDeletions
import top.saltfish.ech0.publish.PublishStore
import top.saltfish.ech0.ui.common.AppMessages
import top.saltfish.ech0.ui.common.LocalReduceMotion
import top.saltfish.ech0.ui.common.detectExtension
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

class ImageItem(
    var uri: Uri,
    state: UploadState = UploadState.Pending,
    remoteUrl: String? = null
) {
    var state: UploadState by mutableStateOf(state)

    /** 服务端文件 URL：本地暂存图丢失后仍可用于草稿预览 */
    var remoteUrl: String? = remoteUrl

    /** 用户已从编辑器移除：若上传随后完成，直接删掉服务端文件，避免留下孤儿文件 */
    var removed: Boolean = false

    /** 当前上传协程；移除图片时可以取消，避免继续消耗流量 */
    var uploadJob: Job? = null
}

/**
 * 上传独立于编辑器生命周期：用户保存草稿后立即退出时，上传仍会完成并把 fileId 回填到草稿；
 * 用户选择「不保存」时，上传完成后直接清理服务端文件。
 */
private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
    val uploadSemaphore = remember {
        Semaphore(Ech0Store.current.uploadConcurrency.coerceIn(1, 3))
    }

    var content by remember { mutableStateOf("") }
    val images = remember { mutableStateListOf<ImageItem>() }
    var tags by remember { mutableStateOf(Ech0Store.current.defaultTags.take(5)) }
    var private by remember { mutableStateOf(Ech0Store.current.defaultPrivate) }
    var layout by remember { mutableStateOf(Ech0Store.current.defaultLayout) }
    var extension by remember { mutableStateOf<EchoExtension?>(null) }
    var createdAtOverride by remember { mutableStateOf<Long?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showTagEditor by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var showExtensionDialog by remember { mutableStateOf(false) }
    var extensionInitialType by remember { mutableStateOf<String?>(null) }
    var showLayoutPicker by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var draftId by remember { mutableStateOf(DraftPayload().id) }
    var availableTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var quality by remember {
        mutableIntStateOf(Ech0Store.current.defaultImageQuality)
    }
    var qualityMode by remember { mutableStateOf(Ech0Store.current.defaultImageQualityMode) }
    var customQuality by remember {
        mutableIntStateOf(
            if (Ech0Store.current.defaultImageQualityMode == "custom") {
                Ech0Store.current.defaultImageQuality
            } else {
                60
            }
        )
    }
    var imageFormat by remember { mutableStateOf(Ech0Store.current.defaultImageFormat) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showPublishConfirm by remember { mutableStateOf(false) }
    var editingEcho by remember { mutableStateOf<Echo?>(null) }
    var existingFiles by remember { mutableStateOf<List<EchoFileUpsertDto>>(emptyList()) }
    var showClosePrompt by remember { mutableStateOf(false) }
    var autoSaveOnDraftClose by remember { mutableStateOf(false) }
    var imageRevision by remember { mutableIntStateOf(0) }
    var editingImage by remember { mutableStateOf<ImageItem?>(null) }
    /** 草稿已写入存储（自动保存或恢复），关闭时用于判断是否需要清理记录 */
    var savedDraft by remember { mutableStateOf(false) }
    /** 自动保存后的轻量提示，避免每次停顿都弹全局 Snackbar */
    var draftSavedHint by remember { mutableStateOf(false) }
    /** 已经发布 / 已决定丢弃：阻止迟到的自动保存把草稿又写回来 */
    var draftFinalized by remember { mutableStateOf(false) }
    /** 编辑器已经关闭：上传完成后改为回填草稿，而不是更新已消失的界面 */
    var composerClosed by remember { mutableStateOf(false) }
    /** 关闭时选择不保存：迟到的上传结果直接删除服务端文件 */
    var discardUploads by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { SiteStore.tags() }.onSuccess { availableTags = it }
    }

    suspend fun deleteServerFile(fileId: String): Result<Unit> =
        runCatching { Ech0Api.deleteFile(fileId) }
            .onFailure { PendingFileDeletions.enqueue(context, fileId) }

    /** 真正执行压缩 + 上传；只在发布时批量调用，或用户手动重试单张。 */
    suspend fun uploadImage(item: ImageItem): Boolean {
        if (!NetworkPolicy.canUpload()) {
            item.state = UploadState.Failed("仅 Wi-Fi 上传已开启")
            imageRevision++
            return false
        }
        item.state = UploadState.Uploading(0f)
        imageRevision++
        return uploadSemaphore.withPermit {
            try {
                val result = if (quality <= 0) {
                    Compressor.copyOriginal(
                        context,
                        item.uri,
                        stripExif = Ech0Store.current.stripExif,
                        format = imageFormat
                    )
                } else {
                    Compressor.compressImage(
                        context,
                        item.uri,
                        quality = quality,
                        format = imageFormat
                    )
                }
                var lastError: Exception? = null
                var uploaded: FileDto? = null
                val retries = Ech0Store.current.uploadRetryCount
                for (attempt in 0..retries) {
                    try {
                        uploaded = Ech0Api.uploadFile(
                            result.file,
                            result.contentType,
                            "image"
                        ) { p -> item.state = UploadState.Uploading(p) }
                        break
                    } catch (e: Exception) {
                        if (e.isCancellationLike()) throw e
                        lastError = e
                        if (attempt < retries) delay(500L * (attempt + 1))
                    }
                }
                val file = uploaded ?: throw (lastError ?: IllegalStateException("上传失败"))
                val localKey = item.uri.toString()
                val known = DraftUploadRegistry.get(localKey)
                if (known != null) {
                    deleteServerFile(file.id)
                    item.state = UploadState.Done(known.first)
                    item.remoteUrl = known.second
                } else if (item.removed || discardUploads) {
                    deleteServerFile(file.id)
                    return@withPermit false
                } else if (composerClosed) {
                    var attached = PublishStore.attachDraftImage(
                        draftId,
                        localKey,
                        file.id,
                        file.url
                    )
                    if (!attached) {
                        delay(400)
                        attached = PublishStore.attachDraftImage(
                            draftId,
                            localKey,
                            file.id,
                            file.url
                        )
                    }
                    if (attached) {
                        DraftUploadRegistry.put(localKey, file.id, file.url)
                        item.state = UploadState.Done(file.id)
                        item.remoteUrl = file.url
                    } else {
                        deleteServerFile(file.id)
                        return@withPermit false
                    }
                } else {
                    DraftUploadRegistry.put(localKey, file.id, file.url)
                    item.state = UploadState.Done(file.id)
                    item.remoteUrl = file.url
                }
                imageRevision++
                true
            } catch (e: Exception) {
                if (e.isCancellationLike()) return@withPermit false
                item.state = UploadState.Failed(e.userMessage())
                imageRevision++
                false
            }
        }
    }

    fun replaceImage(item: ImageItem, newUri: Uri) {
        item.uploadJob?.cancel()
        item.uploadJob = null
        val oldKey = item.uri.toString()
        val oldFileId = (item.state as? UploadState.Done)?.fileId
            ?: DraftUploadRegistry.get(oldKey)?.first
        DraftUploadRegistry.remove(oldKey)
        DraftFiles.delete(item.uri)
        item.uri = newUri
        item.state = UploadState.Pending
        item.remoteUrl = null
        imageRevision++
        if (!oldFileId.isNullOrBlank()) {
            uploadScope.launch { deleteServerFile(oldFileId) }
        }
    }

    fun removeImage(item: ImageItem) {
        item.uploadJob?.cancel()
        item.uploadJob = null
        item.removed = true
        images.remove(item)
        imageRevision++
        DraftFiles.delete(item.uri)
        val key = item.uri.toString()
        val fileId = (item.state as? UploadState.Done)?.fileId
            ?: DraftUploadRegistry.get(key)?.first
        DraftUploadRegistry.remove(key)
        if (fileId.isNullOrBlank()) return
        uploadScope.launch {
            deleteServerFile(fileId).onFailure {
                if (!it.isCancellationLike()) {
                    toast = "服务器文件删除失败：${it.userMessage()}"
                }
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
            val currentBase = Ech0Api.base().trimEnd('/')
            if (draft.baseUrl.isNotBlank() && draft.baseUrl.trimEnd('/') != currentBase) {
                toast = "该草稿属于另一个实例，请切回原实例后继续编辑"
                ComposerBus.restoreDraft = null
                return@LaunchedEffect
            }
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
            savedDraft = true
            var missing = 0
            draft.images.take(9).forEachIndexed { index, raw ->
                val storedId = draft.imageFileIds.getOrNull(index).orEmpty()
                var remoteUrl = draft.imageUrls.getOrNull(index).orEmpty()
                var fileId = if (raw.startsWith("uploaded:")) {
                    raw.removePrefix("uploaded:")
                } else {
                    storedId
                }
                var localKey = raw
                var previewUri: Uri? = null
                if (raw.startsWith("uploaded:")) {
                    if (remoteUrl.isBlank()) {
                        remoteUrl = DraftUploadRegistry.get(raw)?.second.orEmpty()
                    }
                    previewUri = remoteUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                } else {
                    val localUri = runCatching { Uri.parse(raw) }.getOrNull()
                    val prepared = if (localUri != null && localUri.scheme == "content") {
                        runCatching { DraftFiles.stage(context, localUri) }.getOrNull()
                            ?: localUri
                    } else {
                        localUri
                    }
                    if (prepared != null) localKey = prepared.toString()
                    val known = DraftUploadRegistry.get(localKey)
                        ?: DraftUploadRegistry.get(raw)
                    if (fileId.isBlank()) fileId = known?.first.orEmpty()
                    if (remoteUrl.isBlank()) remoteUrl = known?.second.orEmpty()
                    previewUri = when {
                        prepared != null && DraftFiles.exists(prepared) -> prepared
                        fileId.isNotBlank() && remoteUrl.isNotBlank() -> Uri.parse(remoteUrl)
                        else -> null
                    }
                }
                if (previewUri == null) {
                    missing++
                    DraftUploadRegistry.remove(localKey)
                    if (fileId.isNotBlank()) {
                        uploadScope.launch { deleteServerFile(fileId) }
                    }
                    return@forEachIndexed
                }
                val item = ImageItem(
                    previewUri,
                    if (fileId.isNotBlank()) UploadState.Done(fileId) else UploadState.Pending,
                    remoteUrl.takeIf { it.isNotBlank() }
                )
                images.add(item)
            }
            imageRevision++
            if (missing > 0) toast = "有 $missing 张草稿图片已失效，请重新添加"
            ComposerBus.restoreDraft = null
            return@LaunchedEffect
        }
        if (seed > 0) {
            if (shared.text.isNotBlank()) content = shared.text
            shared.images.take(9).forEach { uri ->
                runCatching { DraftFiles.stage(context, uri) }.getOrNull()?.let { staged ->
                    val item = ImageItem(staged)
                    images.add(item)
                    imageRevision++
                }
            }
        }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        scope.launch {
            uris.take(9 - images.size).forEach { uri ->
                runCatching { DraftFiles.stage(context, uri) }
                    .onSuccess { staged ->
                        val item = ImageItem(staged)
                        images.add(item)
                        imageRevision++
                    }
                    .onFailure { toast = "读取图片失败：${it.userMessage()}" }
            }
        }
    }

    fun currentDraftPayload() = DraftPayload(
        id = draftId,
        baseUrl = Ech0Api.base().trimEnd('/'),
        content = content,
        images = images.map { it.uri.toString() },
        imageFileIds = images.map { item ->
            (item.state as? UploadState.Done)?.fileId
                ?: DraftUploadRegistry.get(item.uri.toString())?.first.orEmpty()
        },
        imageUrls = images.map { item ->
            item.remoteUrl
                ?: DraftUploadRegistry.get(item.uri.toString())?.second
                ?: item.uri.toString().takeIf { it.startsWith("http") }.orEmpty()
        },
        tags = tags,
        private = private,
        layout = layout,
        extensionType = extension?.type,
        extensionPayloadJson = extension?.payload?.toString(),
        createdAtOverride = createdAtOverride
    )

    // 草稿自动保存（600ms 防抖）；编辑已有帖子时不写草稿
    LaunchedEffect(
        content,
        imageRevision,
        tags,
        private,
        layout,
        extension,
        createdAtOverride,
        editingEcho
    ) {
        if (editingEcho != null || draftFinalized) return@LaunchedEffect
        if (content.isBlank() && images.isEmpty()) {
            // 用户把已保存草稿清空：同步删除记录，避免草稿箱残留旧内容
            if (!savedDraft) return@LaunchedEffect
            delay(600)
            if (draftFinalized || content.isNotBlank() || images.isNotEmpty()) return@LaunchedEffect
            PublishStore.deleteDraft(draftId)
            savedDraft = false
            return@LaunchedEffect
        }
        delay(600)
        if (draftFinalized) return@LaunchedEffect
        runCatching {
            PublishStore.saveDraft(currentDraftPayload()) { !draftFinalized }
        }.onSuccess {
            savedDraft = true
            draftSavedHint = true
        }
    }

    LaunchedEffect(draftSavedHint) {
        if (draftSavedHint) {
            delay(1800)
            draftSavedHint = false
        }
    }

    fun publish() {
        if (publishing || closing) return
        if (!isAdmin) {
            toast = "仅管理员账号可发布，请先在「我的」登录"
            return
        }
        val editing = editingEcho
        if (content.isBlank() && images.isEmpty() && existingFiles.isEmpty()) {
            toast = "写点什么，或添加一张图片"
            return
        }
        publishing = true
        scope.launch {
            // 图片先留在本地；只有点「发表」时才压缩并上传
            val pending = images.filter { it.state !is UploadState.Done }
            val uploaded = coroutineScope {
                pending.map { item ->
                    async {
                        item.uploadJob?.cancel()
                        item.uploadJob = null
                        uploadImage(item)
                    }
                }.awaitAll().all { it }
            }
            if (!uploaded) {
                publishing = false
                toast = "有图片上传失败，请重试或移除"
                return@launch
            }

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
                baseUrl = Ech0Api.base().trimEnd('/'),
                content = content,
                images = images.map { item ->
                    (item.state as? UploadState.Done)?.let { "uploaded:${it.fileId}" }
                        ?: item.uri.toString()
                },
                imageFileIds = images.map { (it.state as? UploadState.Done)?.fileId.orEmpty() },
                imageUrls = images.map { item ->
                    item.remoteUrl ?: item.uri.toString().takeIf { it.startsWith("http") }.orEmpty()
                },
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
                // 发布成功：草稿记录和本地暂存图都清理，服务端附件已挂到新说说上
                draftFinalized = true
                composerClosed = true
                discardUploads = true
                PublishStore.finishDraft(context, currentDraftPayload())
                images.forEach { DraftUploadRegistry.remove(it.uri.toString()) }
                onPublished()
            } catch (e: Exception) {
                if (e.isCancellationLike()) return@launch
                // 转队列：草稿与队列原子切换；已上传的图片标记 uploaded:<id> 复用，不再重复上传
                draftFinalized = true
                composerClosed = true
                PublishStore.moveDraftToQueue(draftId, payload)
                images.filter { it.state is UploadState.Done }
                    .forEach { DraftFiles.delete(it.uri) }
                images.forEach { DraftUploadRegistry.remove(it.uri.toString()) }
                PublishScheduler.kick(context)
                toast = "已加入发布队列，联网后自动发布"
                onPublished()
            } finally {
                publishing = false
            }
        }
    }

    fun closeWithDraftAction(save: Boolean) {
        if (closing) return
        if (publishing) {
            toast = "正在发布，请稍候…"
            return
        }
        closing = true
        draftFinalized = true
        composerClosed = true
        if (!save) discardUploads = true
        val payload = currentDraftPayload()
        // 用独立作用域收尾：即使界面先一步销毁，也能可靠写入 / 清理草稿
        uploadScope.launch {
            if (save) {
                val saved = runCatching { PublishStore.saveDraft(payload) }.isSuccess
                if (!saved) {
                    closing = false
                    AppMessages.show("草稿保存失败，请重试")
                    return@launch
                }
                savedDraft = true
                AppMessages.show("草稿已保存")
            } else {
                runCatching { PublishStore.discardDraft(context, payload) }
                savedDraft = false
            }
            onClose()
        }
    }

    fun requestClose() {
        if (closing) return
        if (publishing) {
            toast = "正在发布，请稍候…"
            return
        }
        if (editingEcho != null) {
            draftFinalized = true
            composerClosed = true
            onClose()
            return
        }
        val hasDraftContent = content.isNotBlank() || images.isNotEmpty()
        if (!hasDraftContent) {
            // 没有文字也没有图片：若之前保存过草稿（用户清空后关闭），一并删除
            if (savedDraft) {
                closeWithDraftAction(false)
            } else {
                draftFinalized = true
                composerClosed = true
                onClose()
            }
            return
        }
        if (Ech0Store.current.promptSaveDraft) {
            showClosePrompt = true
        } else {
            closeWithDraftAction(true)
        }
    }

    BackHandler { requestClose() }

    // 兜底：如果外层导航（系统返回 / 进程回收前）直接移除了编辑器，仍尽量保住草稿
    DisposableEffect(Unit) {
        onDispose {
            if (!draftFinalized && editingEcho == null) {
                val hasContent = content.isNotBlank() || images.isNotEmpty()
                if (hasContent) {
                    composerClosed = true
                    val payload = currentDraftPayload()
                    uploadScope.launch {
                        runCatching { PublishStore.saveDraft(payload) }
                            .onSuccess { AppMessages.show("草稿已保存") }
                    }
                }
            }
        }
    }

    // 展开/半屏：0 = 抽屉，1 = 全屏
    val reduceMotion = LocalReduceMotion.current
    val expandSpec = if (reduceMotion) snap<Float>()
    else MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val settleSpec = if (reduceMotion) snap<Float>()
    else MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val expandAnim = remember { Animatable(0f) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded, expandSpec) {
        expandAnim.animateTo(
            if (expanded) 1f else 0f,
            expandSpec
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
                    detectTapGestures { if (expandAnim.value < 0.5f) requestClose() }
                }
        )

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = lerp(32.dp, 0.dp, sheetFraction),
                    topEnd = lerp(32.dp, 0.dp, sheetFraction)
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
                                                requestClose()
                                            } else {
                                                val target = if (current > 0.5f) 1f else 0f
                                                expandAnim.animateTo(
                                                    target,
                                                    settleSpec
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

                    // 顶栏：取消（长按存草稿） | 写说说 | 发表
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(20.dp))
                                .combinedClickable(
                                    onClick = { requestClose() },
                                    onLongClick = {
                                        if (content.isNotBlank() || images.isNotEmpty()) {
                                            closeWithDraftAction(true)
                                        } else {
                                            requestClose()
                                        }
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "取消，长按存入草稿箱"
                                }
                        ) {
                            Text(
                                "取消",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            if (editingEcho != null) "编辑说说" else "写说说",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        Button(
                            onClick = {
                                if (Ech0Store.current.publishConfirm) {
                                    showPublishConfirm = true
                                } else {
                                    publish()
                                }
                            },
                            enabled = isAdmin && !publishing,
                            modifier = Modifier.align(Alignment.CenterEnd)
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

                    AnimatedVisibility(visible = draftSavedHint) {
                        Text(
                            "草稿已自动保存",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                        )
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
                                    onRetry = { item ->
                                        item.state = UploadState.Pending
                                        imageRevision++
                                        toast = "发布时会重新上传"
                                    },
                                    onRemove = { removeImage(it) },
                                    onEdit = { editingImage = it },
                                    onLayoutClick = { showLayoutPicker = true }
                                )
                                if (images.isNotEmpty()) {
                                    AssistChip(
                                        onClick = { showQualityDialog = true },
                                        label = {
                                            Text(
                                                "画质：${when (qualityMode) {
                                                    "original" -> "原图"
                                                    "compressed" -> "压缩"
                                                    else -> "自定义 $quality%"
                                                }} · ${when (imageFormat) {
                                                    "jpeg" -> "JPG"
                                                    "png" -> "PNG"
                                                    "auto" -> "原格式"
                                                    else -> "WebP"
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
                                }
                            } else if (existingFiles.isNotEmpty()) {
                                ExistingImageEditor(
                                    echo = editingEcho,
                                    remainingIds = existingFiles.map { it.file_id }.toSet(),
                                    onRemove = { fileId ->
                                        // 编辑旧帖只解除附件关系，不删服务端文件（可能被其他帖子引用）
                                        existingFiles = existingFiles
                                            .filterNot { it.file_id == fileId }
                                            .mapIndexed { index, dto ->
                                                dto.copy(sort_order = index)
                                            }
                                    }
                                )
                            }
                            Spacer(Modifier.height(10.dp))

                            // 标签（增删时高度平滑过渡）
                            FlowRow(
                                Modifier.fillMaxWidth().animateContentSize(
                                    animationSpec = if (reduceMotion) snap()
                                    else MaterialTheme.motionScheme.defaultSpatialSpec()
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
                                    onClick = {
                                        extensionInitialType = null
                                        showExtensionDialog = true
                                    },
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
                        ToolIcon(
                            if (private) Icons.Filled.Lock else Icons.Filled.Public,
                            if (private) "私密" else "公开"
                        ) { private = !private }
                        ToolIcon(Icons.Filled.Widgets, "卡片") {
                            extensionInitialType = null
                            showExtensionDialog = true
                        }
                        ToolIcon(Icons.Filled.Schedule, "补记") { showTimeDialog = true }
                        ToolIcon(Icons.Filled.MyLocation, "位置") {
                            extensionInitialType = "LOCATION"
                            showExtensionDialog = true
                        }
                        ToolIcon(Icons.Filled.Tag, "话题") { showTagEditor = !showTagEditor }
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
            initialType = extensionInitialType,
            onSave = {
                extension = it
                showExtensionDialog = false
                extensionInitialType = null
            },
            onDismiss = {
                showExtensionDialog = false
                extensionInitialType = null
            }
        )
    }
    if (showTimeDialog) {
        TimeOverrideDialog(initial = createdAtOverride) { ts ->
            createdAtOverride = ts
            showTimeDialog = false
        }
    }
    if (showQualityDialog) {
        QualityPickerDialog(
            currentQuality = quality,
            currentMode = qualityMode,
            currentFormat = imageFormat
        ) { pickedMode, pickedQuality, pickedFormat ->
            qualityMode = pickedMode
            quality = pickedQuality
            if (pickedMode == "custom") customQuality = pickedQuality
            imageFormat = pickedFormat
            showQualityDialog = false
        }
    }

    editingImage?.let { item ->
        ImageEditDialog(
            uri = item.uri,
            onDone = { newUri ->
                editingImage = null
                replaceImage(item, newUri)
            },
            onDismiss = { editingImage = null }
        )
    }

    if (showPublishConfirm) {
        AlertDialog(
            onDismissRequest = { showPublishConfirm = false },
            title = { Text(if (editingEcho != null) "保存修改？" else "发布这条说说？") },
            text = { Text("发布前确认已开启，本操作会立即提交。") },
            confirmButton = {
                TextButton(onClick = {
                    showPublishConfirm = false
                    publish()
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showPublishConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showClosePrompt) {
        AlertDialog(
            onDismissRequest = { showClosePrompt = false },
            title = { Text("保存草稿？") },
            text = {
                Column {
                    Text("这条说说还没有发布，可以保存到草稿箱，下次继续编辑。")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { autoSaveOnDraftClose = !autoSaveOnDraftClose },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = autoSaveOnDraftClose,
                            onCheckedChange = { autoSaveOnDraftClose = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "以后自动保存，不再询问",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showClosePrompt = false
                        autoSaveOnDraftClose = false
                    }) { Text("继续编辑") }
                    TextButton(
                        onClick = {
                            showClosePrompt = false
                            closeWithDraftAction(false)
                        }
                    ) { Text("不保存") }
                    Button(
                        onClick = {
                            showClosePrompt = false
                            if (autoSaveOnDraftClose) {
                                scope.launch { Ech0Store.setPromptSaveDraft(false) }
                            }
                            closeWithDraftAction(true)
                        }
                    ) { Text("存草稿") }
                }
            }
        )
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
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
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
    onEdit: (ImageItem) -> Unit,
    onLayoutClick: () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (reduceMotion) snap()
                else MaterialTheme.motionScheme.defaultSpatialSpec()
            )
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
                            // 编辑（裁剪 / 旋转）
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(5.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(0.55f))
                                    .clickable { onEdit(item) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Crop, "编辑图片",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
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

// ---------- 编辑旧帖：只解除附件关系，不删除服务端文件 ----------

@Composable
private fun ExistingImageEditor(
    echo: Echo?,
    remainingIds: Set<String>,
    onRemove: (String) -> Unit
) {
    val files = echo?.echo_files.orEmpty().mapNotNull { ef ->
        val id = ef.file?.id ?: ef.file_id
        if (id.isBlank() || id !in remainingIds) null else id to ef.file?.url.orEmpty()
    }
    if (files.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            "图片：${files.size} 张，点 × 移除（只解除与本帖的关联）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        files.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (id, url) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        AsyncImage(
                            model = Ech0Api.resolveUrl(url),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(0.55f))
                                .clickable { onRemove(id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Close, "移除",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
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
    initialType: String? = null,
    onSave: (EchoExtension) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(initialType ?: initial?.type ?: "WEBSITE") }
    var f1 by remember { mutableStateOf("") }
    var f2 by remember { mutableStateOf("") }
    var f3 by remember { mutableStateOf("") }
    var linkInput by remember { mutableStateOf(extensionPrimaryLink(initial)) }
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

    var detectedHint by remember { mutableStateOf<String?>(null) }

    // 回填：切换卡片类型时用已有内容填充；检测链接后直接写字段，不能再触发回填
    fun backfill(t: String) {
        val matched = initial?.takeIf { it.type == t }
        val p = matched?.payload
        linkInput = extensionPrimaryLink(matched)
        f1 = when (t) {
            "MUSIC" -> p?.get("url")?.toString()?.trim('"').orEmpty()
            "VIDEO" -> p?.get("videoId")?.toString()?.trim('"').orEmpty()
            "GITHUBPROJ" -> p?.get("repoUrl")?.toString()?.trim('"').orEmpty()
            "WEBSITE" -> p?.get("site")?.toString()?.trim('"').orEmpty()
            "LOCATION" -> p?.get("placeholder")?.toString()?.trim('"').orEmpty()
            "TWEET" -> p?.get("url")?.toString()?.trim('"').orEmpty()
            else -> ""
        }
        f2 = when (t) {
            "WEBSITE" -> p?.get("title")?.toString()?.trim('"').orEmpty()
            "LOCATION" -> p?.get("latitude")?.toString().orEmpty()
            "TWEET" -> p?.get("username")?.toString()?.trim('"').orEmpty()
            else -> ""
        }
        f3 = when (t) {
            "LOCATION" -> p?.get("longitude")?.toString().orEmpty()
            "TWEET" -> p?.get("statusId")?.toString()?.trim('"').orEmpty()
            else -> ""
        }
    }
    LaunchedEffect(Unit) { backfill(type) }

    fun applyDetected(ext: EchoExtension) {
        val p = ext.payload
        type = ext.type
        f1 = when (ext.type) {
            "MUSIC" -> p?.get("url")?.toString()?.trim('"').orEmpty()
            "VIDEO" -> p?.get("videoId")?.toString()?.trim('"').orEmpty()
            "GITHUBPROJ" -> p?.get("repoUrl")?.toString()?.trim('"').orEmpty()
            "WEBSITE" -> p?.get("site")?.toString()?.trim('"').orEmpty()
            "LOCATION" -> p?.get("placeholder")?.toString()?.trim('"').orEmpty()
            "TWEET" -> p?.get("url")?.toString()?.trim('"').orEmpty()
            else -> f1
        }
        f2 = when (ext.type) {
            "WEBSITE" -> p?.get("title")?.toString()?.trim('"').orEmpty()
            "LOCATION" -> p?.get("latitude")?.toString().orEmpty()
            "TWEET" -> p?.get("username")?.toString()?.trim('"').orEmpty()
            else -> ""
        }
        f3 = when (ext.type) {
            "LOCATION" -> p?.get("longitude")?.toString().orEmpty()
            "TWEET" -> p?.get("statusId")?.toString()?.trim('"').orEmpty()
            else -> ""
        }
        detectedHint = "已识别为" + when (ext.type) {
            "GITHUBPROJ" -> "GitHub 仓库"
            "VIDEO" -> "视频"
            "MUSIC" -> "音乐"
            "WEBSITE" -> "网站"
            "LOCATION" -> "位置"
            "TWEET" -> "X 推文"
            else -> ext.type
        }
    }

    fun detectFromLink(raw: String) {
        val text = raw.trim()
        if (!text.startsWith("http://") && !text.startsWith("https://")) return
        val host = runCatching { java.net.URI(text).host.orEmpty() }.getOrDefault("")
        // b23.tv 短链需要跟随跳转才能拿到 BV 号
        if (host.equals("b23.tv", ignoreCase = true)) {
            scope.launch {
                val finalUrl = runCatching {
                    HttpClients.thirdParty.newCall(
                        okhttp3.Request.Builder().url(text).get().build()
                    ).await().use { it.request.url.toString() }
                }.getOrNull()
                val ext = finalUrl?.let { detectExtension(it) }
                if (ext != null && linkInput.trim() == text) {
                    linkInput = text
                    applyDetected(ext)
                }
            }
            return
        }
        val ext = detectExtension(text) ?: run {
            val coords = parseLatLng(text)
            when {
                coords != null && isMapHost(host) -> EchoExtension(
                    "LOCATION",
                    buildJsonObject {
                        put("placeholder", "地图位置")
                        put("latitude", coords.first)
                        put("longitude", coords.second)
                    }
                )
                host.isNotBlank() -> EchoExtension(
                    "WEBSITE",
                    buildJsonObject {
                        put("site", text)
                        put("title", host)
                    }
                )
                else -> return
            }
        }
        linkInput = text
        applyDetected(ext)
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

    fun currentExtension(): EchoExtension {
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
        return EchoExtension(type = type, payload = payload)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扩展卡片") },
        confirmButton = {
            Button(onClick = { onSave(currentExtension()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { input ->
                        linkInput = input
                        detectedHint = null
                        if (input.isNotBlank()) detectFromLink(input)
                    },
                    label = { Text("粘贴链接，自动识别") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "支持 GitHub / B 站 / YouTube / 网易云 / QQ 音乐 / X 推文 / 地图 / 网站",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("WEBSITE", "VIDEO", "MUSIC", "GITHUBPROJ", "LOCATION", "TWEET").forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = {
                                backfill(t)
                                type = t
                                detectedHint = null
                            },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                detectedHint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
            }
        }
    )
}

// ---------- 链接识别辅助 ----------

private fun extensionPrimaryLink(ext: EchoExtension?): String {
    val payload = ext?.payload ?: return ""
    fun str(key: String): String =
        runCatching { payload[key]?.toString()?.trim('"').orEmpty() }.getOrDefault("")
    return when (ext.type.uppercase()) {
        "GITHUBPROJ" -> str("repoUrl")
        "MUSIC" -> str("url")
        "VIDEO" -> {
            val id = str("videoId")
            when {
                id.startsWith("BV", ignoreCase = true) -> "https://www.bilibili.com/video/$id"
                id.isNotBlank() -> "https://www.youtube.com/watch?v=$id"
                else -> ""
            }
        }
        "TWEET" -> str("url")
        "WEBSITE" -> str("site")
        "LOCATION" -> {
            val lat = str("latitude")
            val lng = str("longitude")
            if (lat.isNotBlank() && lng.isNotBlank() && lat != "0.0" && lng != "0.0") {
                "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=16/$lat/$lng"
            } else ""
        }
        else -> ""
    }
}

private fun parseLatLng(text: String): Pair<Double, Double>? {
    val match = Regex("""(-?\d{1,3}(?:\.\d+)?)\s*[,，]\s*(-?\d{1,3}(?:\.\d+)?)""")
        .find(text) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lng = match.groupValues[2].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return lat to lng
}

private fun isMapHost(host: String): Boolean {
    val h = host.lowercase(java.util.Locale.ROOT)
    return listOf(
        "maps.google.", "google.com", "google.cn",
        "amap.com", "openstreetmap.org", "maps.apple.com",
        "map.baidu.com", "map.qq.com"
    ).any { h == it || h.endsWith(".$it") || h.contains(it) }
}

// ---------- 上传画质 ----------

@Composable
fun QualityPickerDialog(
    currentQuality: Int,
    currentMode: String,
    currentFormat: String,
    onPick: (mode: String, quality: Int, format: String) -> Unit
) {
    var mode by remember { mutableStateOf(currentMode) }
    var quality by remember { mutableStateOf(currentQuality) }
    var format by remember { mutableStateOf(currentFormat) }
    var qualityMenu by remember { mutableStateOf(false) }
    var formatMenu by remember { mutableStateOf(false) }
    var customQuality by remember {
        mutableIntStateOf(
            if (currentMode == "custom" && currentQuality in 1..100) currentQuality else 60
        )
    }
    val qualityLabel = when (mode) {
        "original" -> "原图"
        "compressed" -> "压缩 · 30%"
        else -> "自定义 · $quality%"
    }
    val formatLabel = when (format) {
        "jpeg" -> "JPG"
        "png" -> "PNG"
        "auto" -> "原格式"
        else -> "WebP"
    }

    AlertDialog(
        onDismissRequest = { onPick(mode, quality, format) },
        title = { Text("图片画质与格式") },
        confirmButton = {
            TextButton(onClick = { onPick(mode, quality, format) }) { Text("完成") }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { qualityMenu = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("画质", modifier = Modifier.weight(1f))
                        Text(qualityLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = qualityMenu,
                        onDismissRequest = { qualityMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("压缩") },
                            onClick = {
                                mode = "compressed"
                                quality = 30
                                qualityMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("原图") },
                            onClick = {
                                mode = "original"
                                quality = 0
                                qualityMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("自定义") },
                            onClick = {
                                mode = "custom"
                                quality = customQuality.coerceIn(1, 100)
                                qualityMenu = false
                            }
                        )
                    }
                }

                if (mode == "custom") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "压缩比例",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "$quality%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = {
                            quality = it.roundToInt().coerceIn(1, 100)
                            customQuality = quality
                        },
                        valueRange = 1f..100f
                    )
                }

                HorizontalDivider()

                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { formatMenu = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("格式", modifier = Modifier.weight(1f))
                        Text(formatLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = formatMenu,
                        onDismissRequest = { formatMenu = false }
                    ) {
                        listOf(
                            "webp" to "WebP",
                            "jpeg" to "JPG",
                            "png" to "PNG",
                            "auto" to "原格式"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    format = value
                                    formatMenu = false
                                }
                            )
                        }
                    }
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
