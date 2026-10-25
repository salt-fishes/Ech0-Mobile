package top.saltfish.ech0.publish

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
    /** 本地图片 uri（content://）或已上传的远端标记 uploaded:<fileId> */
    val images: List<String> = emptyList(),
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

object PublishStore {
    private val mutex = Mutex()

    private val json = Ech0Api.json

    // ---------- 草稿箱 ----------

    suspend fun drafts(): List<DraftPayload> = mutex.withLock {
        runCatching {
            json.decodeFromString(ListSerializer(DraftPayload.serializer()), Ech0Store.draftsJson)
        }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
    }

    suspend fun saveDraft(draft: DraftPayload) = mutex.withLock {
        val list = runCatching {
            json.decodeFromString(ListSerializer(DraftPayload.serializer()), Ech0Store.draftsJson)
        }.getOrDefault(emptyList()).filter { it.id != draft.id } + draft.copy(updatedAt = System.currentTimeMillis() / 1000)
        Ech0Store.persistDrafts(json.encodeToString(ListSerializer(DraftPayload.serializer()), list))
    }

    suspend fun deleteDraft(id: String) = mutex.withLock {
        val list = runCatching {
            json.decodeFromString(ListSerializer(DraftPayload.serializer()), Ech0Store.draftsJson)
        }.getOrDefault(emptyList()).filter { it.id != id }
        Ech0Store.persistDrafts(json.encodeToString(ListSerializer(DraftPayload.serializer()), list))
    }

    // ---------- 发布队列 ----------

    suspend fun queue(): List<QueueItem> = mutex.withLock {
        runCatching {
            json.decodeFromString(ListSerializer(QueueItem.serializer()), Ech0Store.queueJson)
        }.getOrDefault(emptyList()).sortedByDescending { it.enqueuedAt }
    }

    suspend fun enqueue(payload: DraftPayload) = mutex.withLock {
        val list = runCatching {
            json.decodeFromString(ListSerializer(QueueItem.serializer()), Ech0Store.queueJson)
        }.getOrDefault(emptyList())
        val next = (list.filter { it.payload.id != payload.id } + QueueItem(payload = payload))
            .sortedByDescending { it.enqueuedAt }
        Ech0Store.persistQueue(json.encodeToString(ListSerializer(QueueItem.serializer()), next))
    }

    suspend fun updateItem(item: QueueItem) = mutex.withLock {
        val list = runCatching {
            json.decodeFromString(ListSerializer(QueueItem.serializer()), Ech0Store.queueJson)
        }.getOrDefault(emptyList())
        val next = list.map { if (it.payload.id == item.payload.id) item else it }
        Ech0Store.persistQueue(json.encodeToString(ListSerializer(QueueItem.serializer()), next))
    }

    suspend fun remove(id: String) = mutex.withLock {
        val list = runCatching {
            json.decodeFromString(ListSerializer(QueueItem.serializer()), Ech0Store.queueJson)
        }.getOrDefault(emptyList()).filter { it.payload.id != id }
        Ech0Store.persistQueue(json.encodeToString(ListSerializer(QueueItem.serializer()), list))
    }

    /** 取下一个待发条目（供 Worker 消费） */
    suspend fun nextPending(): QueueItem? = mutex.withLock {
        runCatching {
            json.decodeFromString(ListSerializer(QueueItem.serializer()), Ech0Store.queueJson)
        }.getOrDefault(emptyList()).firstOrNull { it.state == "pending" }
    }

    suspend fun hasPending(): Boolean = nextPending() != null
}
