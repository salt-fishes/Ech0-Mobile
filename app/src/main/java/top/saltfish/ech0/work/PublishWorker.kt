package top.saltfish.ech0.work

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.EchoExtension
import top.saltfish.ech0.data.EchoFileUpsertDto
import top.saltfish.ech0.data.EchoUpsertDto
import top.saltfish.ech0.data.MediaUploader
import top.saltfish.ech0.data.TagUpsertDto
import top.saltfish.ech0.data.userMessage
import top.saltfish.ech0.media.Compressor
import top.saltfish.ech0.publish.PublishStore
import top.saltfish.ech0.publish.PendingFileDeletions
import top.saltfish.ech0.publish.QueueItem
import java.util.concurrent.TimeUnit

object PublishScheduler {
    fun kick(context: Context) {
        val request = OneTimeWorkRequestBuilder<PublishWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("ech0_publish_queue", ExistingWorkPolicy.KEEP, request)
    }
}

class PublishWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        /** 超过后标记为失败，避免一张坏图无限重试并堵住后面的队列 */
        private const val MAX_ATTEMPTS = 6
        private const val MAX_ITEMS_PER_RUN = 100
    }

    override suspend fun doWork(): Result {
        runCatching { PendingFileDeletions.flush(applicationContext) }
        var processed = 0
        var shouldRetry = false
        while (processed < MAX_ITEMS_PER_RUN) {
            val item = PublishStore.nextPending() ?: break
            PublishStore.updateItem(item.copy(state = "running"))
            try {
                publish(item)
                // 用原始 item 清理本地暂存图；服务端附件已随说说发布，保留
                PublishStore.finishQueueItem(applicationContext, item)
                processed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val attempts = item.attempts + 1
                val permanent = attempts >= MAX_ATTEMPTS
                // 读取 worker 中途写回的图片上传进度，避免重试时重复上传
                val latestPayload = PublishStore.queue()
                    .firstOrNull { it.payload.id == item.payload.id }
                    ?.payload ?: item.payload
                PublishStore.updateItem(
                    item.copy(
                        payload = latestPayload,
                        state = if (permanent) "failed" else "pending",
                        attempts = attempts,
                        lastError = e.userMessage()
                    )
                )
                if (permanent) {
                    // 这条留待用户手动重试 / 删除，继续处理后面的队列
                    processed++
                } else {
                    shouldRetry = true
                    break
                }
            }
        }
        if (PublishStore.hasPending()) {
            // 本轮达到上限或还有剩余：尽快再跑一轮
            shouldRetry = true
        }
        return if (shouldRetry) Result.retry() else Result.success()
    }

    private suspend fun publish(item: QueueItem) {
        var payload = item.payload
        val itemBase = payload.baseUrl.trimEnd('/')
        val currentBase = Ech0Api.base().trimEnd('/')
        if (itemBase.isNotBlank() && itemBase != currentBase) {
            // 不把旧实例的帖子和 uploaded:<fileId> 发到新实例；用户切回原实例后可继续
            error("该队列条目属于 $itemBase，请切回对应实例后再发布")
        }

        // 上传本地图片；每完成一张就把 uploaded:<id> 写回队列，
        // 这样后续重试 / 删除队列时都不会重复上传或漏删服务端文件
        payload.images.forEachIndexed { index, image ->
            if (image.startsWith("uploaded:")) return@forEachIndexed
            val uri = Uri.parse(image)
            val result = if (top.saltfish.ech0.data.Ech0Store.current.compressImages) {
                Compressor.compressImage(
                    applicationContext,
                    uri,
                    webp = top.saltfish.ech0.data.Ech0Store.current.webpUpload
                )
            } else {
                Compressor.copyOriginal(
                    applicationContext,
                    uri,
                    webp = top.saltfish.ech0.data.Ech0Store.current.webpUpload
                )
            }
            val file = MediaUploader.upload(result.file, result.contentType, "image")

            fun List<String>.padded(size: Int): MutableList<String> =
                toMutableList().apply { while (this.size < size) add("") }

            val images = payload.images.toMutableList().apply { this[index] = "uploaded:${file.id}" }
            val ids = payload.imageFileIds.padded(payload.images.size)
            val urls = payload.imageUrls.padded(payload.images.size)
            ids[index] = file.id
            urls[index] = file.url
            payload = payload.copy(images = images, imageFileIds = ids, imageUrls = urls)
            PublishStore.updateItem(item.copy(payload = payload, state = "running"))
        }

        val fileIds = payload.images.map { image ->
            if (image.startsWith("uploaded:")) {
                image.removePrefix("uploaded:")
            } else {
                error("图片尚未上传完成")
            }
        }
        val extension = payload.extensionType?.let { type ->
            val payloadJson = payload.extensionPayloadJson?.let {
                runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
            }
            EchoExtension(type = type, payload = payloadJson)
        }
        Ech0Api.createEcho(
            EchoUpsertDto(
                content = payload.content,
                echo_files = fileIds.mapIndexed { index, id -> EchoFileUpsertDto(id, index) },
                layout = payload.layout,
                private = payload.private,
                extension = extension,
                tags = payload.tags.map { TagUpsertDto(name = it) },
                created_at = payload.createdAtOverride
            )
        )
    }
}
