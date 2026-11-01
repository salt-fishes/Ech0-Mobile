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

    /** 从文件头识别图片类型，避免 file:// / 无扩展名 URI 被误判成 JPEG。 */
    private fun sniffImageMime(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(16)
            val n = input.read(header)
            if (n >= 8 &&
                (header[0].toInt() and 0xFF) == 0x89 &&
                header[1].toInt() == 'P'.code &&
                header[2].toInt() == 'N'.code &&
                header[3].toInt() == 'G'.code &&
                header[4].toInt() == 0x0D &&
                header[5].toInt() == 0x0A &&
                header[6].toInt() == 0x1A &&
                header[7].toInt() == 0x0A
            ) {
                "image/png"
            } else if (n >= 3 &&
                (header[0].toInt() and 0xFF) == 0xFF &&
                (header[1].toInt() and 0xFF) == 0xD8 &&
                (header[2].toInt() and 0xFF) == 0xFF
            ) {
                "image/jpeg"
            } else if (n >= 6) {
                val head = String(header, 0, 6, Charsets.US_ASCII)
                when {
                    head == "GIF87a" || head == "GIF89a" -> "image/gif"
                    n >= 12 &&
                        String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                        String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                    n >= 12 &&
                        String(header, 4, 4, Charsets.US_ASCII) == "ftyp" &&
                        String(header, 8, 4, Charsets.US_ASCII) in listOf("avif", "avis") -> "image/avif"
                    else -> null
                }
            } else {
                null
            }
        }
    }.getOrNull()

    private fun extensionForMime(mime: String): String = when {
        mime.contains("png", true) -> "png"
        mime.contains("webp", true) -> "webp"
        mime.contains("gif", true) -> "gif"
        mime.contains("avif", true) -> "avif"
        else -> "jpg"
    }

    /** 长边 2048、质量 82、WebP；失败时回退原图拷贝 */
    @Suppress("DEPRECATION")
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        maxEdge: Int = 2048,
        quality: Int = 82,
        format: String = "webp"
    ): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val sourceName = uri.lastPathSegment.orEmpty()
        val ext = sourceName.substringAfterLast('.', "").lowercase()
        val originalMime = sniffImageMime(context, uri)
            ?: runCatching { resolver.getType(uri) }.getOrNull()
                ?.takeIf { it.startsWith("image/") }
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

        val formatKey = when (format.lowercase()) {
            "jpeg", "jpg" -> "jpeg"
            "png" -> "png"
            "auto" -> when (originalMime.lowercase()) {
                "image/png" -> "png"
                "image/jpeg" -> "jpeg"
                else -> "webp"
            }
            else -> "webp"
        }
        val extension = if (formatKey == "jpeg") "jpg" else formatKey
        val outFile = cacheFile(context, extension)
        val compressFormat = when (formatKey) {
            "jpeg" -> Bitmap.CompressFormat.JPEG
            "png" -> Bitmap.CompressFormat.PNG
            else -> Bitmap.CompressFormat.WEBP_LOSSY
        }
        FileOutputStream(outFile).use { fos ->
            outBitmap.compress(compressFormat, quality, fos)
        }
        if (outBitmap !== bitmap) bitmap.recycle()
        Result(outFile, "image/$formatKey", outBitmap.width, outBitmap.height)
    }

    /** 原图直传（不压缩）；开启移除 EXIF 时以高质量重新编码，避免携带拍摄位置 */
    suspend fun copyOriginal(
        context: Context,
        uri: Uri,
        stripExif: Boolean = true,
        format: String = "auto"
    ): Result = if (stripExif || format.lowercase() !in listOf("auto", "")) {
        compressImage(context, uri, maxEdge = 4096, quality = 96, format = format)
    } else withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val extFromName = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        val mime = sniffImageMime(context, uri)
            ?: runCatching { resolver.getType(uri) }.getOrNull()
                ?.takeIf { it.startsWith("image/") }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extFromName)
            ?: "image/jpeg"
        val f = cacheFile(context, extensionForMime(mime))
        resolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("无法读取文件")
        Result(f, mime, 0, 0)
    }

    private fun cacheFile(context: Context, ext: String): File {
        val dir = File(context.cacheDir, "upload").apply { mkdirs() }
        return File(dir, "${System.currentTimeMillis()}_${(0..999).random()}.$ext")
    }
}
