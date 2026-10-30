package top.saltfish.ech0.publish

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 草稿图片落到应用私有目录，避免 photo picker 的临时 Uri 在重启后失效 */
object DraftFiles {

    fun dir(context: Context): File = File(context.filesDir, "draft_images")

    suspend fun stage(context: Context, uri: Uri): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.orEmpty())
            ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 2..5 }
            ?: "jpg"
        val folder = dir(context).apply { mkdirs() }
        val target = File(folder, "${UUID.randomUUID()}.$ext")
        val input = resolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取图片")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        Uri.fromFile(target)
    }

    fun exists(uri: Uri): Boolean = when (uri.scheme) {
        "file" -> uri.path?.let { File(it).isFile } == true
        "content" -> true
        else -> false
    }

    fun delete(uri: Uri) {
        if (uri.scheme == "file") {
            runCatching { uri.path?.let { File(it).delete() } }
        }
    }

    fun delete(uriString: String) {
        runCatching { delete(Uri.parse(uriString)) }
    }

    fun deleteAll(context: Context) {
        runCatching { dir(context).deleteRecursively() }
    }

    /** 删除目录里没有被任何草稿 / 队列引用的暂存图 */
    fun deleteOrphans(context: Context, referencedUris: Set<String>) {
        val referencedPaths = referencedUris.mapNotNullTo(mutableSetOf()) { raw ->
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@mapNotNullTo null
            if (uri.scheme == "file") uri.path else null
        }
        val folder = dir(context)
        val files = folder.listFiles().orEmpty()
        files.forEach { file ->
            if (file.isFile && file.absolutePath !in referencedPaths) {
                runCatching { file.delete() }
            }
        }
    }
}

/**
 * 本次进程内「同一张本地暂存图 → 已上传文件」的映射。
 * 用于处理关闭草稿后上传才完成、又重新打开同一草稿等并发场景，避免重复上传产生孤儿文件。
 */
object DraftUploadRegistry {
    private val entries = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    fun put(localUri: String, fileId: String, remoteUrl: String) {
        entries[localUri] = fileId to remoteUrl
    }

    fun get(localUri: String): Pair<String, String>? = entries[localUri]

    fun remove(localUri: String) {
        entries.remove(localUri)
    }
}
