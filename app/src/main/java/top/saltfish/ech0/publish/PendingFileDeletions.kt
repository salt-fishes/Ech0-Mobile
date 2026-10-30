package top.saltfish.ech0.publish

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import top.saltfish.ech0.data.ApiException
import top.saltfish.ech0.data.Ech0Api
import java.io.File

/**
 * 服务端文件删除失败（断网、超时）时的补偿队列。
 * 记录实例地址，切换实例后不会误删新实例上的文件。
 */
object PendingFileDeletions {

    @Serializable
    private data class Entry(val baseUrl: String, val fileId: String)

    private val mutex = Mutex()

    private fun file(context: Context) = File(context.filesDir, "pending_file_deletions.json")

    private fun read(context: Context): MutableList<Entry> = runCatching {
        val f = file(context)
        if (!f.exists()) return mutableListOf()
        Ech0Api.json.decodeFromString(
            ListSerializer(Entry.serializer()),
            f.readText()
        ).toMutableList()
    }.getOrDefault(mutableListOf())

    private fun write(context: Context, entries: List<Entry>) {
        runCatching {
            val f = file(context)
            f.parentFile?.mkdirs()
            f.writeText(Ech0Api.json.encodeToString(ListSerializer(Entry.serializer()), entries))
        }
    }

    suspend fun enqueue(context: Context, fileId: String) {
        if (fileId.isBlank()) return
        val base = Ech0Api.base()
        if (base.isBlank()) return
        mutex.withLock {
            val entries = read(context)
            if (entries.none { it.baseUrl == base && it.fileId == fileId }) {
                entries += Entry(base, fileId)
                write(context, entries.takeLast(200))
            }
        }
    }

    /** 联网后重试删除；返回本次成功删除的数量 */
    suspend fun flush(context: Context): Int = mutex.withLock {
        val base = Ech0Api.base()
        if (base.isBlank()) return@withLock 0
        val entries = read(context)
        if (entries.isEmpty()) return@withLock 0
        val remaining = mutableListOf<Entry>()
        var deleted = 0
        entries.forEach { entry ->
            if (entry.baseUrl != base) {
                remaining += entry
                return@forEach
            }
            try {
                Ech0Api.deleteFile(entry.fileId)
                deleted++
            } catch (e: ApiException) {
                // 已经不存在也算清理完成
                if (e.httpCode == 404 || e.errorCode?.endsWith("404") == true) {
                    deleted++
                } else {
                    remaining += entry
                }
            } catch (e: Exception) {
                remaining += entry
            }
        }
        write(context, remaining)
        deleted
    }
}
