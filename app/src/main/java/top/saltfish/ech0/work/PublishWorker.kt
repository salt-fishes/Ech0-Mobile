package top.saltfish.ech0.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.EchoExtension
import top.saltfish.ech0.data.EchoFileUpsertDto
import top.saltfish.ech0.data.EchoUpsertDto
import top.saltfish.ech0.data.TagUpsertDto
import top.saltfish.ech0.media.Compressor
import top.saltfish.ech0.publish.PublishStore
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

    override suspend fun doWork(): Result {
        var processed = 0
        while (processed < 5) {
            val item = PublishStore.nextPending() ?: break
            PublishStore.updateItem(item.copy(state = "running"))
            try {
                publish(item)
                PublishStore.remove(item.payload.id)
                processed++
            } catch (e: Exception) {
                PublishStore.updateItem(
                    item.copy(
                        state = "pending",
                        attempts = item.attempts + 1,
                        lastError = e.message ?: "发送失败"
                    )
                )
                PublishScheduler.kick(applicationContext)
                return Result.retry()
            }
        }
        return Result.success()
    }

    private suspend fun publish(item: QueueItem) {
        val payload = item.payload
        // 上传本地图片（uploaded:<id> 前缀的跳过，复用上次结果）
        val fileIds = payload.images.map { image ->
            if (image.startsWith("uploaded:")) {
                image.removePrefix("uploaded:")
            } else {
                val uri = android.net.Uri.parse(image)
                val result = if (top.saltfish.ech0.data.Ech0Store.current.compressImages) {
                    Compressor.compressImage(applicationContext, uri)
                } else {
                    Compressor.copyOriginal(applicationContext, uri)
                }
                Ech0Api.uploadFile(result.file, result.contentType, "image").id
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
