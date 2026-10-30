package top.saltfish.ech0.publish

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import java.util.UUID

/** 编辑器完整状态（草稿与队列条目共用） */
@Serializable
data class DraftPayload(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    /** 本地图片 uri（file:// / content://）或已上传的远端标记 uploaded:<fileId> */
    val images: List<String> = emptyList(),
    /** 与 images 对齐：已上传图片的服务端 fileId，未上传为空字符串 */
    val imageFileIds: List<String> = emptyList(),
    /** 与 images 对齐：服务端文件 URL，本地暂存文件丢失时仍可预览 */
    val imageUrls: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val private: Boolean = false,
    val layout: String = "waterfall",
    val extensionType: String? = null,
    val extensionPayloadJson: String? = null,
    /** 补记/定时：unix 秒 */
    val createdAtOverride: Long? = null,
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

@Serializable
data class QueueItem(
    val payload: DraftPayload,
    val state: String = "pending", // pending | running | failed | done
    val attempts: Int = 0,
    val lastError: String? = null,
    val enqueuedAt: Long = System.currentTimeMillis() / 1000
)

/** 草稿 / 队列变更通知：我的页面、草稿箱和队列页据此刷新计数 */
object PublishBus {
    var tick by mutableIntStateOf(0)
        private set

    fun bump() {
        tick++
    }
}

object PublishStore {
    private val mutex = Mutex()

    private val json = Ech0Api.json

    private data class ReferenceSnapshot(
        val local: Set<String> = emptySet(),
        val server: Set<String> = emptySet()
    )

    // ---------- 序列化 ----------

    private fun parseDrafts(jsonText: String): List<DraftPayload> =
        runCatching {
            json.decodeFromString(ListSerializer(DraftPayload.serializer()), jsonText)
        }.getOrDefault(emptyList())

    private fun parseQueue(jsonText: String): List<QueueItem> =
        runCatching {
            json.decodeFromString(ListSerializer(QueueItem.serializer()), jsonText)
        }.getOrDefault(emptyList())

    private suspend fun persistDrafts(list: List<DraftPayload>) {
        Ech0Store.persistDrafts(json.encodeToString(ListSerializer(DraftPayload.serializer()), list))
    }

    private suspend fun persistQueue(list: List<QueueItem>) {
        Ech0Store.persistQueue(json.encodeToString(ListSerializer(QueueItem.serializer()), list))
    }

    // ---------- 文件引用 ----------

    private fun isLocalImage(value: String): Boolean =
        value.startsWith("file:") || value.startsWith("content:")

    private fun DraftPayload.serverFileIds(): Set<String> {
        val ids = imageFileIds.filter { it.isNotBlank() }.toMutableSet()
        images.forEach { image ->
            if (image.startsWith("uploaded:")) ids += image.removePrefix("uploaded:")
        }
        return ids
    }

    /** 统计仍被草稿或队列引用的文件，删除时避免误删共享附件 */
    private fun referencesOf(
        drafts: List<DraftPayload>,
        queue: List<QueueItem>
    ): ReferenceSnapshot {
        val local = mutableSetOf<String>()
        val server = mutableSetOf<String>()
        fun collect(payload: DraftPayload) {
            payload.images.filter(::isLocalImage).forEach { local += it }
            server += payload.serverFileIds()
        }
        drafts.forEach(::collect)
        queue.forEach { collect(it.payload) }
        return ReferenceSnapshot(local = local, server = server)
    }

    private suspend fun cleanupDiscardedPayload(
        context: Context,
        payload: DraftPayload,
        references: ReferenceSnapshot
    ) {
        payload.images.filter(::isLocalImage).forEach { image ->
            if (image !in references.local) {
                runCatching { DraftFiles.delete(Uri.parse(image)) }
            }
            DraftUploadRegistry.remove(image)
        }
        payload.serverFileIds().forEach { fileId ->
            if (fileId !in references.server) {
                runCatching { Ech0Api.deleteFile(fileId) }
                    .onFailure { PendingFileDeletions.enqueue(context, fileId) }
            }
        }
    }

    private fun cleanupLocalOnly(payload: DraftPayload, references: ReferenceSnapshot) {
        payload.images.filter(::isLocalImage).forEach { image ->
            if (image !in references.local) {
                runCatching { DraftFiles.delete(Uri.parse(image)) }
            }
            DraftUploadRegistry.remove(image)
        }
    }

    // ---------- 草稿箱 ----------

    suspend fun drafts(): List<DraftPayload> = mutex.withLock {
        Ech0Store.awaitLoaded()
        parseDrafts(Ech0Store.draftsJson).sortedByDescending { it.updatedAt }
    }

    suspend fun saveDraft(
        draft: DraftPayload,
        shouldSave: () -> Boolean = { true }
    ): Boolean = mutex.withLock {
        Ech0Store.awaitLoaded()
        if (!shouldSave()) return@withLock false
        val list = parseDrafts(Ech0Store.draftsJson)
            .filter { it.id != draft.id } + draft.copy(updatedAt = System.currentTimeMillis() / 1000)
        persistDrafts(list)
        PublishBus.bump()
        true
    }

    /** 仅移除草稿记录；需要连图片一起清理时使用 [discardDraft] */
    suspend fun deleteDraft(id: String) = mutex.withLock {
        Ech0Store.awaitLoaded()
        persistDrafts(parseDrafts(Ech0Store.draftsJson).filter { it.id != id })
        PublishBus.bump()
    }

    /**
     * 丢弃草稿：删除记录，并清理不再被其他草稿 / 队列引用的本地暂存图和已上传服务端文件。
     * 用于「不保存」、草稿箱删除、清空内容后关闭等场景。
     */
    suspend fun discardDraft(context: Context, draft: DraftPayload) {
        val references = mutex.withLock {
            Ech0Store.awaitLoaded()
            val drafts = parseDrafts(Ech0Store.draftsJson).filter { it.id != draft.id }
            persistDrafts(drafts)
            referencesOf(drafts, parseQueue(Ech0Store.queueJson))
        }
        cleanupDiscardedPayload(context, draft, references)
        PublishBus.bump()
    }

    /**
     * 发布成功：移除草稿记录、清理本地暂存图，但保留服务端附件（它们已挂到新说说上）。
     */
    suspend fun finishDraft(context: Context, draft: DraftPayload) {
        val references = mutex.withLock {
            Ech0Store.awaitLoaded()
            val drafts = parseDrafts(Ech0Store.draftsJson).filter { it.id != draft.id }
            persistDrafts(drafts)
            referencesOf(drafts, parseQueue(Ech0Store.queueJson))
        }
        cleanupLocalOnly(draft, references)
        PublishBus.bump()
    }

    /** 上传完成时把 fileId 回填到已保存草稿（编辑器可能已经关闭） */
    suspend fun attachDraftImage(
        draftId: String,
        localUri: String,
        fileId: String,
        remoteUrl: String
    ): Boolean = mutex.withLock {
        Ech0Store.awaitLoaded()
        val drafts = parseDrafts(Ech0Store.draftsJson).toMutableList()
        val draftIndex = drafts.indexOfFirst { it.id == draftId }
        if (draftIndex < 0) return@withLock false
        val draft = drafts[draftIndex]
        val imageIndex = draft.images.indexOf(localUri)
        if (imageIndex < 0) return@withLock false

        fun List<String>.padded(size: Int): MutableList<String> =
            toMutableList().apply {
                while (this.size < size) add("")
            }

        val ids = draft.imageFileIds.padded(draft.images.size)
        val urls = draft.imageUrls.padded(draft.images.size)
        if (ids[imageIndex].isNotBlank()) return@withLock false
        ids[imageIndex] = fileId
        urls[imageIndex] = remoteUrl
        drafts[draftIndex] = draft.copy(imageFileIds = ids, imageUrls = urls)
        persistDrafts(drafts)
        true
    }

    /** 清理没有被任何草稿 / 队列引用的本地暂存图（应用启动、草稿箱打开时调用） */
    suspend fun cleanupOrphanDraftFiles(context: Context) {
        Ech0Store.awaitLoaded()
        if (!Ech0Store.isLoaded) return
        val references = mutex.withLock {
            Ech0Store.awaitLoaded()
            referencesOf(parseDrafts(Ech0Store.draftsJson), parseQueue(Ech0Store.queueJson))
        }
        DraftFiles.deleteOrphans(context, references.local)
    }

    // ---------- 发布队列 ----------

    suspend fun queue(): List<QueueItem> = mutex.withLock {
        Ech0Store.awaitLoaded()
        parseQueue(Ech0Store.queueJson).sortedByDescending { it.enqueuedAt }
    }

    suspend fun enqueue(payload: DraftPayload) = mutex.withLock {
        Ech0Store.awaitLoaded()
        val list = (parseQueue(Ech0Store.queueJson).filter { it.payload.id != payload.id } +
            QueueItem(payload = payload)).sortedByDescending { it.enqueuedAt }
        persistQueue(list)
        PublishBus.bump()
    }

    /** 草稿转队列：移除草稿与加入队列在同一次持久化中完成，避免中途退出产生重复内容 */
    suspend fun moveDraftToQueue(draftId: String, payload: DraftPayload) = mutex.withLock {
        Ech0Store.awaitLoaded()
        persistDrafts(parseDrafts(Ech0Store.draftsJson).filter { it.id != draftId })
        val list = (parseQueue(Ech0Store.queueJson).filter { it.payload.id != payload.id } +
            QueueItem(payload = payload)).sortedByDescending { it.enqueuedAt }
        persistQueue(list)
        payload.images.filter(::isLocalImage).forEach { DraftUploadRegistry.remove(it) }
        PublishBus.bump()
    }

    suspend fun updateItem(item: QueueItem) = mutex.withLock {
        Ech0Store.awaitLoaded()
        val list = parseQueue(Ech0Store.queueJson)
            .map { if (it.payload.id == item.payload.id) item else it }
        persistQueue(list)
        PublishBus.bump()
    }

    /** 仅移除队列记录；需要连图片一起清理时使用 [discardQueueItem] */
    suspend fun remove(id: String) = mutex.withLock {
        Ech0Store.awaitLoaded()
        persistQueue(parseQueue(Ech0Store.queueJson).filter { it.payload.id != id })
        PublishBus.bump()
    }

    /** 队列发送成功：移除记录并清理本地暂存图，服务端附件保留 */
    suspend fun finishQueueItem(context: Context, item: QueueItem) {
        val references = mutex.withLock {
            Ech0Store.awaitLoaded()
            val queue = parseQueue(Ech0Store.queueJson).filter { it.payload.id != item.payload.id }
            persistQueue(queue)
            referencesOf(parseDrafts(Ech0Store.draftsJson), queue)
        }
        cleanupLocalOnly(item.payload, references)
        PublishBus.bump()
    }

    /** 用户删除队列条目：本地暂存图和服务端文件都清理（不再被其他条目引用时） */
    suspend fun discardQueueItem(context: Context, item: QueueItem) {
        val references = mutex.withLock {
            Ech0Store.awaitLoaded()
            val queue = parseQueue(Ech0Store.queueJson).filter { it.payload.id != item.payload.id }
            persistQueue(queue)
            referencesOf(parseDrafts(Ech0Store.draftsJson), queue)
        }
        cleanupDiscardedPayload(context, item.payload, references)
        PublishBus.bump()
    }

    /** 取下一个待发条目（供 Worker 消费），按入队时间从早到晚 */
    suspend fun nextPending(): QueueItem? = mutex.withLock {
        Ech0Store.awaitLoaded()
        parseQueue(Ech0Store.queueJson)
            .filter { it.state == "pending" }
            .minByOrNull { it.enqueuedAt }
    }

    suspend fun hasPending(): Boolean = nextPending() != null
}
