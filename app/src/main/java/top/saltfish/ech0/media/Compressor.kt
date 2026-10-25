package top.saltfish.ech0.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object Compressor {

    data class Result(val file: File, val contentType: String, val width: Int, val height: Int)

    /** 长边 2048、质量 82、WebP；失败时回退原图拷贝 */
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        maxEdge: Int = 2048,
        quality: Int = 82
    ): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val sourceName = uri.lastPathSegment.orEmpty()
        val ext = sourceName.substringAfterLast('.', "").lowercase()
        val originalMime = runCatching { resolver.getType(uri) }.getOrNull()
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "image/jpeg"

        // 视频/动图等非压缩目标直接拷贝
        if (originalMime == "image/gif") {
            val f = cacheFile(context, "gif")
            resolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
            return@withContext Result(f, originalMime, 0, 0)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var w = bounds.outWidth
        var h = bounds.outHeight
        if (w <= 0 || h <= 0) {
            val f = cacheFile(context, ext.ifBlank { "jpg" })
            resolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
            return@withContext Result(f, originalMime, 0, 0)
        }

        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalStateException("无法读取图片")

        val scale = maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height)
        val outBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        val outFile = cacheFile(context, "webp")
        FileOutputStream(outFile).use { fos ->
            @Suppress("DEPRECATION")
            outBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, fos)
        }
        if (outBitmap !== bitmap) bitmap.recycle()
        Result(outFile, "image/webp", outBitmap.width, outBitmap.height)
    }

    /** 原图直传（不压缩） */
    suspend fun copyOriginal(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = runCatching { resolver.getType(uri) }.getOrNull() ?: "image/jpeg"
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "jpg"
        }
        val f = cacheFile(context, ext)
        resolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("无法读取文件")
        Result(f, mime, 0, 0)
    }

    private fun cacheFile(context: Context, ext: String): File {
        val dir = File(context.cacheDir, "upload").apply { mkdirs() }
        return File(dir, "${System.currentTimeMillis()}_${(0..999).random()}.$ext")
    }
}
