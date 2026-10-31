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

    /** 长边 2048、质量 82；webp=false 时 PNG 保持 PNG、其余转 JPEG；失败时回退原图拷贝 */
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        maxEdge: Int = 2048,
        quality: Int = 82,
        webp: Boolean = true
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

        // 关闭 WebP 时保留 PNG 的透明通道，其余格式统一转 JPEG
        val keepPng = !webp && originalMime == "image/png"
        val format = when {
            webp -> Bitmap.CompressFormat.WEBP_LOSSY
            keepPng -> Bitmap.CompressFormat.PNG
            else -> Bitmap.CompressFormat.JPEG
        }
        val outExt = when {
            webp -> "webp"
            keepPng -> "png"
            else -> "jpg"
        }
        val outMime = when {
            webp -> "image/webp"
            keepPng -> "image/png"
            else -> "image/jpeg"
        }

        val outFile = cacheFile(context, outExt)
        FileOutputStream(outFile).use { fos ->
            @Suppress("DEPRECATION")
            outBitmap.compress(format, quality, fos)
        }
        if (outBitmap !== bitmap) bitmap.recycle()
        Result(outFile, outMime, outBitmap.width, outBitmap.height)
    }

    /**
     * 原图直传（不压缩）。
     * 开启移除 EXIF 时以高质量重新编码，按 [webp] 决定输出格式；
     * 关闭时直接复制原始字节，保持原格式（这种情况下 [webp] 不生效）。
     */
    suspend fun copyOriginal(
        context: Context,
        uri: Uri,
        stripExif: Boolean = true,
        webp: Boolean = true
    ): Result = if (stripExif) {
        compressImage(context, uri, maxEdge = 4096, quality = 96, webp = webp)
    } else withContext(Dispatchers.IO) {
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
