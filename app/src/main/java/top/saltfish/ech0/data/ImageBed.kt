package top.saltfish.ech0.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import top.saltfish.ech0.data.net.HttpClients
import java.io.File
import java.util.concurrent.TimeUnit

const val SUPERBED_ENDPOINT = "https://api.superbed.cn/upload"

/** 1×1 透明 PNG，测试图床配置用 */
private val ONE_PIXEL_PNG: ByteArray = java.util.Base64.getDecoder().decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)

/**
 * 内置图床预设。每个预设给出一组默认参数，用户在「高级参数」里仍可逐项覆盖。
 *
 * - Superbed：表单 token + X-API-Key 头，返回顶层 url，err=0 表示成功
 * - EasyImage：表单 token，文件字段名 image，返回 code=200 + url
 * - 兰空 Lsky Pro：Bearer 头，文件字段 file，返回 data.links.url，status=true 表示成功
 * - S.EE / SM.MS：Bearer 头，文件字段 smfile，返回 data.url，success=true 表示成功
 */
enum class ImageBedPreset(
    val id: String,
    val label: String,
    val defaultEndpoint: String,
    val endpointEditable: Boolean,
    val fileField: String,
    val headerName: String,
    val headerPrefix: String,
    val formTokenField: String,
    val albumField: String,
    val urlPath: String,
    val successPath: String,
    val successValue: String,
    val errorPath: String,
    val hint: String
) {
    Superbed(
        id = "superbed",
        label = "Superbed",
        defaultEndpoint = SUPERBED_ENDPOINT,
        endpointEditable = false,
        fileField = "file",
        headerName = "X-API-Key",
        headerPrefix = "",
        formTokenField = "token",
        albumField = "categories",
        urlPath = "url",
        successPath = "err",
        successValue = "0",
        errorPath = "message",
        hint = "聚合图床。Token 在 superbed.cn 用户中心复制；相册名可留空。"
    ),
    EasyImage(
        id = "easyimage",
        label = "EasyImage",
        defaultEndpoint = "",
        endpointEditable = true,
        fileField = "image",
        headerName = "",
        headerPrefix = "",
        formTokenField = "token",
        albumField = "",
        urlPath = "url",
        successPath = "code",
        successValue = "200",
        errorPath = "message",
        hint = "简单图床。地址填「你的域名/api/index.php」，Token 来自站点 tokenList 文件，可留空。"
    ),
    Lsky(
        id = "lsky",
        label = "兰空 Lsky",
        defaultEndpoint = "",
        endpointEditable = true,
        fileField = "file",
        headerName = "Authorization",
        headerPrefix = "Bearer ",
        formTokenField = "",
        albumField = "album_id",
        urlPath = "data.links.url",
        successPath = "status",
        successValue = "true",
        errorPath = "message",
        hint = "兰空 Pro 2.x。地址填「你的域名/api/v1/upload」，Token 不含 Bearer 前缀；1.x 请把 URL 路径改成 data.url。"
    ),
    See(
        id = "see",
        label = "S.EE / SM.MS",
        defaultEndpoint = "",
        endpointEditable = true,
        fileField = "smfile",
        headerName = "Authorization",
        headerPrefix = "Bearer ",
        formTokenField = "",
        albumField = "",
        urlPath = "data.url",
        successPath = "success",
        successValue = "true",
        errorPath = "message",
        hint = "S.EE 填 https://s.ee/api/v1/file/upload；SM.MS 填 https://sm.ms/api/v2/upload，并把认证前缀清空。"
    ),
    Custom(
        id = "custom",
        label = "自定义",
        defaultEndpoint = "",
        endpointEditable = true,
        fileField = "file",
        headerName = "Authorization",
        headerPrefix = "Bearer ",
        formTokenField = "",
        albumField = "",
        urlPath = "url",
        successPath = "",
        successValue = "",
        errorPath = "message",
        hint = "按图床文档填写地址、文件字段名、认证方式与返回 JSON 路径。"
    );

    companion object {
        fun fromId(id: String): ImageBedPreset =
            entries.firstOrNull { it.id == id } ?: Superbed
    }
}

/**
 * 图床配置。字段全部可持久化，UI 里按 [ImageBedPreset] 预填、允许覆盖。
 * [token] 明文只在内存；落盘由 [encodeImageBed] 用 Keystore 加密。
 */
@Serializable
data class ImageBedConfig(
    val enabled: Boolean = false,
    val preset: String = ImageBedPreset.Superbed.id,
    /** 空表示用预设默认地址 */
    val endpoint: String = "",
    val token: String = "",
    /** 相册名 / album_id，按 [albumField] 提交 */
    val album: String = "",
    val fileField: String = "file",
    /** 认证头名称，空表示不发 */
    val headerName: String = "X-API-Key",
    /** 认证头前缀，如 "Bearer "（含尾随空格） */
    val headerPrefix: String = "",
    /** 表单里的 token 字段名，空表示不发 */
    val formTokenField: String = "token",
    /** query 里的 token 参数名，空表示不发 */
    val queryTokenField: String = "",
    /** 相册字段名，空表示不发 */
    val albumField: String = "categories",
    /** 额外固定表单字段，JSON 对象字符串 */
    val extraFieldsJson: String = "",
    /** 返回 JSON 里图片地址的路径 */
    val urlPath: String = "url",
    /** 成功判定字段路径，空表示不判定 */
    val successPath: String = "err",
    /** 成功判定字段的期望值 */
    val successValue: String = "0",
    /** 失败信息的字段路径 */
    val errorPath: String = "message"
) {
    fun presetOrNull(): ImageBedPreset = ImageBedPreset.fromId(preset)

    /** 实际请求地址：显式填写优先，否则用预设默认 */
    fun resolvedEndpoint(): String = endpoint.trim().ifBlank { presetOrNull().defaultEndpoint }

    /** 切换预设：保留开关 / token / 相册，其余字段恢复预设默认 */
    fun applyPreset(target: ImageBedPreset): ImageBedConfig = copy(
        preset = target.id,
        endpoint = "",
        fileField = target.fileField,
        headerName = target.headerName,
        headerPrefix = target.headerPrefix,
        formTokenField = target.formTokenField,
        albumField = target.albumField,
        extraFieldsJson = "",
        urlPath = target.urlPath,
        successPath = target.successPath,
        successValue = target.successValue,
        errorPath = target.errorPath
    )
}

internal fun decodeImageBed(raw: String?): ImageBedConfig {
    if (raw.isNullOrBlank()) return ImageBedConfig()
    val config = runCatching {
        Ech0Api.json.decodeFromString(ImageBedConfig.serializer(), raw)
    }.getOrNull() ?: return ImageBedConfig()
    if (config.token.isBlank()) return config
    // Keystore 密钥被清除（重装 / 锁屏重置）后解不开，按未配置处理
    return config.copy(token = CryptoBox.decrypt(config.token) ?: "")
}

internal fun encodeImageBed(config: ImageBedConfig): String {
    val sealed = if (config.token.isBlank()) "" else CryptoBox.encrypt(config.token)
    return Ech0Api.json.encodeToString(ImageBedConfig.serializer(), config.copy(token = sealed))
}

/** 图床上传：只负责把字节传到图床并取回外链，不碰实例 API */
object ImageBedClient {

    private val client: OkHttpClient by lazy {
        HttpClients.thirdParty.newBuilder()
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    suspend fun upload(
        file: File,
        contentType: String,
        config: ImageBedConfig,
        onProgress: (Float) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val endpoint = config.resolvedEndpoint()
        if (endpoint.isBlank()) throw ApiException("IMAGE_BED_NO_ENDPOINT", "请填写图床上传地址")
        val url = endpoint.toHttpUrlOrNull()
            ?: throw ApiException("IMAGE_BED_BAD_ENDPOINT", "图床地址无效，请检查设置")
        if (url.scheme != "http" && url.scheme != "https") {
            throw ApiException("IMAGE_BED_BAD_ENDPOINT", "图床地址必须是 http/https")
        }
        val token = config.token.trim()
        if (token.isBlank()) throw ApiException("IMAGE_BED_NO_TOKEN", "请先在设置里填写图床 Token")

        val media = contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                config.fileField.ifBlank { "file" },
                file.name,
                ProgressBody(file, media, onProgress)
            )
            .apply {
                if (config.formTokenField.isNotBlank()) {
                    addFormDataPart(config.formTokenField.trim(), token)
                }
                if (config.album.isNotBlank() && config.albumField.isNotBlank()) {
                    addFormDataPart(config.albumField.trim(), config.album.trim())
                }
                extraFields(config.extraFieldsJson).forEach { (name, value) ->
                    addFormDataPart(name, value)
                }
            }
            .build()

        val target = if (config.queryTokenField.isNotBlank()) {
            url.newBuilder().addQueryParameter(config.queryTokenField.trim(), token).build()
        } else {
            url
        }

        val request = Request.Builder()
            .url(target)
            .apply {
                if (config.headerName.isNotBlank()) {
                    header(config.headerName.trim(), config.headerPrefix + token)
                }
            }
            .post(multipart)
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val detail = text.trim().take(160).let { if (it.isBlank()) "" else "：$it" }
                throw ApiException(
                    "IMAGE_BED_HTTP_${resp.code}",
                    "图床上传失败 (${resp.code})$detail",
                    resp.code
                )
            }
            parseUploadResponse(text, config)
        }
    }

    /** 上传一张 1×1 PNG 验证配置；会在图床留下一张测试图 */
    suspend fun testUpload(cacheDir: File, config: ImageBedConfig): String {
        val file = File.createTempFile("ech0-bed-test", ".png", cacheDir)
        return try {
            file.writeBytes(ONE_PIXEL_PNG)
            upload(file, "image/png", config)
        } finally {
            file.delete()
        }
    }

    internal fun parseUploadResponse(body: String, config: ImageBedConfig): String {
        val root = runCatching { Ech0Api.json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: throw ApiException("IMAGE_BED_BAD_RESPONSE", "图床返回内容不是 JSON")

        // 只有明确取到状态字段且不等于期望值时才判定失败；
        // 有些图床成功响应会省略状态字段，此时以能否取到 url 为准
        if (config.successPath.isNotBlank()) {
            val actual = readPath(root, config.successPath)
            if (actual != null && actual != config.successValue) {
                val message = readPath(root, config.errorPath.ifBlank { "message" })
                throw ApiException(
                    "IMAGE_BED_REJECTED",
                    message?.takeIf { it.isNotBlank() }
                        ?: "图床拒绝上传（${config.successPath}=$actual）"
                )
            }
        }

        // 配置的路径优先；再按常见图床结构回退，减少换图床时的配置量
        val link = listOf(config.urlPath, "url", "data.url", "data.links.url")
            .filter { it.isNotBlank() }
            .distinct()
            .firstNotNullOfOrNull { readPath(root, it)?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .orEmpty()
        if (link.isBlank()) {
            val message = readPath(root, config.errorPath.ifBlank { "message" })
            throw ApiException(
                "IMAGE_BED_NO_URL",
                message?.takeIf { it.isNotBlank() } ?: "图床没有返回图片地址，请检查 URL 路径设置"
            )
        }
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            throw ApiException("IMAGE_BED_NO_URL", "图床返回的地址不是 http/https：$link")
        }
        return link
    }

    /** 点号路径：支持 a.b.0.c，数组用数字下标 */
    internal fun readPath(root: JsonElement?, path: String): String? {
        var current: JsonElement? = root
        for (segment in path.split('.').filter { it.isNotBlank() }) {
            current = when (val node = current) {
                is JsonObject -> node[segment]
                is JsonArray -> segment.toIntOrNull()?.let { node.getOrNull(it) }
                else -> null
            } ?: return null
        }
        // 布尔 / 数字也转成字符串参与比较与展示
        return (current as? JsonPrimitive)?.content
    }

    private fun extraFields(raw: String): List<Pair<String, String>> {
        if (raw.isBlank()) return emptyList()
        val obj = runCatching {
            Ech0Api.json.parseToJsonElement(raw) as? JsonObject
        }.getOrNull() ?: return emptyList()
        return obj.mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.content?.let { key to it }
        }
    }

    private class ProgressBody(
        private val file: File,
        private val media: MediaType,
        private val onProgress: (Float) -> Unit
    ) : RequestBody() {
        override fun contentType() = media
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            val total = contentLength().coerceAtLeast(1L)
            var sent = 0L
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    sent += read
                    onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
    }
}

/**
 * 上传入口：启用图床时先传图床再向实例登记外链，否则走实例自己的 /api/files/upload。
 * 编辑器和发布队列都从这里走，保证两条链路一致。
 */
object MediaUploader {

    suspend fun upload(
        file: File,
        contentType: String,
        category: String = "image",
        onProgress: (Float) -> Unit = {}
    ): FileDto {
        val config = Ech0Store.current.imageBed
        if (!config.enabled || category != "image") {
            return Ech0Api.uploadFile(file, contentType, category, onProgress)
        }

        // 图床传输占前 90%，剩余留给实例登记
        val link = ImageBedClient.upload(file, contentType, config) { progress ->
            onProgress((progress * 0.9f).coerceIn(0f, 0.9f))
        }
        onProgress(0.95f)

        val registered = try {
            Ech0Api.registerExternalImage(link)
        } catch (e: Exception) {
            if (e.isCancellationLike()) throw e
            val hint = if ((e as? ApiException)?.httpCode == 403) {
                "（登记外链需要管理员账号）"
            } else {
                ""
            }
            throw ApiException(
                "IMAGE_BED_REGISTER_FAILED",
                "图片已传到图床，但登记到实例失败$hint：${e.userMessage()}"
            )
        }
        onProgress(1f)
        return registered
    }
}
