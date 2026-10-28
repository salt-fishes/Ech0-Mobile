package top.saltfish.ech0.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private val deviceTimezoneId: String get() = TimeZone.getDefault().id

/**
 * Ech0 REST API 客户端（对照上游 internal/router 与 web/src/service/api 逐一核实）。
 * 信封 {code,msg,data}；code=0 抛 ApiException；401 TOKEN_* 自动 refresh 重放一次。
 */
object Ech0Api {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    val cookieJar: PersistentCookieJar = PersistentCookieJar()

    private val refreshLock = Any()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(::headersInterceptor)
            .addInterceptor(::authRetryInterceptor)
            .build()
    }

    /** 友链 / 远端实例请求不能带本站 Token；图片下载也不应带 */
    val publicClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun base(): String = Ech0Store.current.baseUrl.trimEnd('/')

    fun requireBase() {
        if (base().isBlank()) throw ApiException("NO_BASE_URL", "请先在设置中配置实例地址")
    }

    /** 相对 /api/files/<key> 等本地存储 URL 拼 base */
    fun resolveUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return if (url.startsWith("http")) url else base() + url
    }

    /** 指定实例的 URL 解析（友链远端图片用） */
    fun resolveUrlWithBase(baseUrl: String, url: String?): String {
        if (url.isNullOrBlank()) return ""
        return if (url.startsWith("http")) url else baseUrl.trimEnd('/') + url
    }

    private fun headersInterceptor(chain: Interceptor.Chain): Response {
        val b = chain.request().newBuilder()
            .header("X-Timezone", deviceTimezoneId)
            .header("X-Locale", "zh-CN")
        Ech0Store.current.accessToken?.let { b.header("Authorization", "Bearer $it") }
        return chain.proceed(b.build())
    }

    /** 401 且 TOKEN_* → 尝试 refresh 后重放一次 */
    private fun authRetryInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 401) return response
        val bodyText = runCatching { response.peekBody(1 shl 16).string() }.getOrDefault("")
        val errorCode = runCatching { JSONObject(bodyText).optString("error_code") }.getOrDefault("")
        if (!errorCode.startsWith("TOKEN_")) return response
        if (request.header("X-No-Retry") != null) return response

        val refreshed = synchronized(refreshLock) {
            runCatching { doRefresh() }.isSuccess
        }
        if (!refreshed) return response
        response.close()
        val replay = request.newBuilder().header("X-No-Retry", "1").build()
        return chain.proceed(replay)
    }

    @Synchronized
    private fun doRefresh(): String {
        val req = Request.Builder()
            .url(base() + "/api/auth/refresh")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .header("X-No-Retry", "1")
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val env = runCatching { json.decodeFromString(Envelope.serializer(TokenPair.serializer()), text) }.getOrNull()
            val token = env?.data?.access_token
            if (!resp.isSuccessful || env == null || env.code != 1 || token.isNullOrBlank()) {
                // refresh 失效：清登录态，上层引导重登
                kotlinx.coroutines.runBlocking {
                    Ech0Store.setToken(null, 0)
                    Ech0Store.setUser("", false)
                }
                throw ApiException("REFRESH_TOKEN_INVALID", "登录状态已失效，请重新登录", resp.code)
            }
            val expiresAt = System.currentTimeMillis() / 1000 + (env.data.expires_in.takeIf { it > 0 } ?: 900)
            kotlinx.coroutines.runBlocking { Ech0Store.setToken(token, expiresAt) }
            return token
        }
    }

    // ---------- 基础请求 ----------

    private suspend fun <T> execute(serializer: KSerializer<T>, request: Request): T =
        executeWith(client, serializer, request)

    /** 无本站登录态的请求（友链公共 API） */
    private suspend fun <T> executePublic(serializer: KSerializer<T>, request: Request): T =
        executeWith(publicClient, serializer, request)

    private suspend fun <T> executeWith(
        httpClient: OkHttpClient,
        serializer: KSerializer<T>,
        request: Request
    ): T =
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errCode = runCatching { JSONObject(text).optString("error_code") }.getOrDefault("")
                    val errMsg = runCatching { JSONObject(text).optString("msg") }.getOrDefault("")
                    throw ApiException(
                        errCode.ifBlank { "HTTP_${resp.code}" },
                        errMsg.ifBlank { "请求失败 (${resp.code})" },
                        resp.code
                    )
                }
                val env = json.decodeFromString(Envelope.serializer(serializer), text)
                if (env.code != 1) throw ApiException(env.error_code, env.msg.ifBlank { "请求失败" }, resp.code)
                env.data ?: throw ApiException("EMPTY_DATA", env.msg.ifBlank { "响应为空" }, resp.code)
            }
        }

    /** data:null 的 void 端点 */
    private suspend fun executeUnit(request: Request) {
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errCode = runCatching { JSONObject(text).optString("error_code") }.getOrDefault("")
                    val errMsg = runCatching { JSONObject(text).optString("msg") }.getOrDefault("")
                    throw ApiException(
                        errCode.ifBlank { "HTTP_${resp.code}" },
                        errMsg.ifBlank { "请求失败 (${resp.code})" },
                        resp.code
                    )
                }
                val env = json.decodeFromString(Envelope.serializer(JsonObject.serializer()), text)
                if (env.code != 1) throw ApiException(env.error_code, env.msg.ifBlank { "请求失败" }, resp.code)
            }
        }
    }

    private fun jsonBody(body: String) = body.toRequestBody("application/json".toMediaType())

    // ---------- 实例 / 站点 ----------

    suspend fun hello(): Hello {
        requireBase()
        return execute(Hello.serializer(), Request.Builder().url(base() + "/api/hello").get().build())
    }

    suspend fun settings(): SiteSettings {
        requireBase()
        return execute(SiteSettings.serializer(), Request.Builder().url(base() + "/api/settings").get().build())
    }

    suspend fun initStatus(): InitStatus {
        requireBase()
        return execute(InitStatus.serializer(), Request.Builder().url(base() + "/api/init/status").get().build())
    }

    // ---------- 认证 ----------

    suspend fun login(username: String, password: String): TokenPair {
        requireBase()
        val dto = json.encodeToString(LoginDto.serializer(), LoginDto(username, password))
        val pair = execute(
            TokenPair.serializer(),
            Request.Builder().url(base() + "/api/login").post(jsonBody(dto)).build()
        )
        val expiresAt = System.currentTimeMillis() / 1000 + pair.expires_in
        Ech0Store.setToken(pair.access_token, expiresAt)
        runCatching { currentUser() }.getOrNull()?.let { u ->
            Ech0Store.setUser(u.username, u.is_admin, u.avatar.orEmpty())
        }
        return pair
    }

    suspend fun logout() {
        runCatching {
            executeUnit(Request.Builder().url(base() + "/api/auth/logout").post(jsonBody("{}")).build())
        }
        Ech0Store.setToken(null, 0)
        Ech0Store.setUser("", false)
        cookieJar.clear()
    }

    suspend fun currentUser(): User {
        requireBase()
        return execute(User.serializer(), Request.Builder().url(base() + "/api/user").get().build())
    }

    // ---------- 时间线 ----------

    suspend fun queryEchos(query: EchoQueryDto): PageResult<Echo> {
        requireBase()
        val body = json.encodeToString(EchoQueryDto.serializer(), query)
        return execute(
            PageResult.serializer(Echo.serializer()),
            Request.Builder().url(base() + "/api/echo/query").post(jsonBody(body)).build()
        )
    }

    suspend fun echoById(id: String): Echo {
        requireBase()
        return execute(Echo.serializer(), Request.Builder().url(base() + "/api/echo/$id").get().build())
    }

    suspend fun todayEchos(): List<Echo> {
        requireBase()
        return execute(
            ListSerializer(Echo.serializer()),
            Request.Builder().url(base() + "/api/echo/today").get().build()
        )
    }

    suspend fun hotEchos(limit: Int = 5): List<Echo> {
        requireBase()
        return execute(
            ListSerializer(Echo.serializer()),
            Request.Builder().url(base() + "/api/echo/hot?limit=$limit").get().build()
        )
    }

    suspend fun randomEcho(): Echo {
        requireBase()
        return execute(Echo.serializer(), Request.Builder().url(base() + "/api/echo/random").get().build())
    }

    suspend fun onThisDay(): List<Echo> {
        requireBase()
        return execute(
            ListSerializer(Echo.serializer()),
            Request.Builder().url(base() + "/api/echo/onthisday").get().build()
        )
    }

    suspend fun likeEcho(id: String) {
        requireBase()
        executeUnit(Request.Builder().url(base() + "/api/echo/like/$id").put(jsonBody("{}")).build())
    }

    // ---------- 发布 ----------

    suspend fun createEcho(dto: EchoUpsertDto) {
        requireBase()
        executeUnit(
            Request.Builder().url(base() + "/api/echo")
                .post(jsonBody(json.encodeToString(EchoUpsertDto.serializer(), dto))).build()
        )
    }

    suspend fun updateEcho(dto: EchoUpsertDto) {
        requireBase()
        executeUnit(
            Request.Builder().url(base() + "/api/echo")
                .put(jsonBody(json.encodeToString(EchoUpsertDto.serializer(), dto))).build()
        )
    }

    suspend fun deleteEcho(id: String) {
        requireBase()
        executeUnit(Request.Builder().url(base() + "/api/echo/$id").delete().build())
    }

    // ---------- 标签 ----------

    suspend fun tags(): List<Tag> {
        requireBase()
        return execute(
            ListSerializer(Tag.serializer()),
            Request.Builder().url(base() + "/api/tags").get().build()
        )
    }

    suspend fun createTag(name: String): Tag {
        requireBase()
        return execute(
            Tag.serializer(),
            Request.Builder().url(base() + "/api/tag").post(jsonBody("""{"name":"$name"}""")).build()
        )
    }

    suspend fun deleteTag(id: String) {
        requireBase()
        executeUnit(Request.Builder().url(base() + "/api/tag/$id").delete().build())
    }

    // ---------- 发现 ----------

    suspend fun heatmap(): List<HeatmapDay> {
        requireBase()
        return execute(
            ListSerializer(HeatmapDay.serializer()),
            Request.Builder().url(base() + "/api/heatmap").get().build()
        )
    }

    // ---------- 评论 ----------

    suspend fun commentsForm(echoId: String): FormMeta {
        requireBase()
        return execute(
            FormMeta.serializer(),
            Request.Builder().url(base() + "/api/comments/form?echo_id=$echoId").get().build()
        )
    }

    suspend fun comments(echoId: String): List<PublicComment> {
        requireBase()
        return execute(
            ListSerializer(PublicComment.serializer()),
            Request.Builder().url(base() + "/api/comments?echo_id=$echoId").get().build()
        )
    }

    suspend fun postComment(dto: CreateCommentDto): CommentResult {
        requireBase()
        return execute(
            CommentResult.serializer(),
            Request.Builder().url(base() + "/api/comments")
                .post(jsonBody(json.encodeToString(CreateCommentDto.serializer(), dto))).build()
        )
    }

    /** gocap PoW：challenge → 本地算 nonce → redeem 换 captcha_token */
    suspend fun solveCaptcha(endpoint: String): String = solveCaptchaAt(base(), endpoint)

    private suspend fun solveCaptchaAt(baseUrl: String, endpoint: String): String =
        withContext(Dispatchers.IO) {
            val root = if (endpoint.startsWith("http")) endpoint else baseUrl.trimEnd('/') + endpoint
            val challengeUrl = root.trimEnd('/') + "/challenge"
            val redeemUrl = root.trimEnd('/') + "/redeem"

            val challengeResp = publicClient.newCall(Request.Builder().url(challengeUrl).get().build())
                .execute().use { r ->
                    if (!r.isSuccessful) throw ApiException("CAPTCHA_CHALLENGE_FAILED", "获取验证码挑战失败", r.code)
                    r.body?.string().orEmpty()
                }
            val challenges = runCatching { JSONArray(challengeResp) }.getOrElse {
                JSONArray().put(JSONObject(challengeResp))
            }
            val first = challenges.optJSONObject(0)
                ?: throw ApiException("CAPTCHA_EMPTY", "验证码挑战为空")

            val salt = first.getString("salt")
            val challenge = first.optString("challenge", first.optString("token"))
            val difficulty = first.optInt("difficulty", 4)

            val prefix = "0".repeat(difficulty)
            var nonce = 0L
            val md = MessageDigest.getInstance("SHA-256")
            while (true) {
                val digest = md.digest((salt + challenge + nonce).toByteArray())
                val hex = buildString {
                    digest.forEach { b ->
                        append(((b.toInt() shr 4) and 0xF).toString(16))
                        append((b.toInt() and 0xF).toString(16))
                    }
                }
                if (hex.startsWith(prefix)) break
                nonce++
            }

            val redeemBody = JSONObject()
                .put(
                    "solutions", JSONArray().put(
                        JSONObject().put("challenge", challenge).put("salt", salt)
                            .put("nonce", nonce).put("difficulty", difficulty)
                    )
                ).toString()
            val redeemResp = publicClient.newCall(
                Request.Builder().url(redeemUrl).post(jsonBody(redeemBody)).build()
            ).execute().use { r ->
                if (!r.isSuccessful) throw ApiException("CAPTCHA_REDEEM_FAILED", "验证码校验失败", r.code)
                r.body?.string().orEmpty()
            }
            JSONObject(redeemResp).optString("token")
                .ifBlank { throw ApiException("CAPTCHA_NO_TOKEN", "验证码未返回令牌") }
        }

    // ---------- 上传 ----------

    /** multipart 上传；onProgress 0f..1f */
    suspend fun uploadFile(
        file: File,
        contentType: String,
        category: String,
        onProgress: (Float) -> Unit = {}
    ): FileDto = withContext(Dispatchers.IO) {
        requireBase()
        val media = contentType.toMediaType()
        val body = object : RequestBody() {
            override fun contentType() = media
            override fun contentLength() = file.length()
            override fun writeTo(sink: okio.BufferedSink) {
                val total = contentLength().coerceAtLeast(1)
                var sent = 0L
                file.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        sent += n
                        onProgress(sent.toFloat() / total)
                    }
                }
            }
        }
        val multipart = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", file.name, body)
            .addFormDataPart("category", category)
            .addFormDataPart("storage_type", "local")
            .build()
        val req = Request.Builder().url(base() + "/api/files/upload").post(multipart).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errCode = runCatching { JSONObject(text).optString("error_code") }.getOrDefault("")
                val errMsg = runCatching { JSONObject(text).optString("msg") }.getOrDefault("")
                throw ApiException(
                    errCode.ifBlank { "HTTP_${resp.code}" },
                    errMsg.ifBlank { "上传失败 (${resp.code})" },
                    resp.code
                )
            }
            val env = json.decodeFromString(Envelope.serializer(FileDto.serializer()), text)
            if (env.code != 1) throw ApiException(env.error_code, env.msg.ifBlank { "上传失败" }, resp.code)
            env.data ?: throw ApiException("EMPTY_DATA", "上传响应为空", resp.code)
        }
    }

    /** 登记外链图片（不搬运字节），返回可用 file_id */
    suspend fun registerExternalImage(url: String): FileDto {
        requireBase()
        val dto = json.encodeToString(
            ExternalFileDto.serializer(), ExternalFileDto(url = url, category = "image")
        )
        return execute(
            FileDto.serializer(),
            Request.Builder().url(base() + "/api/files/external").post(jsonBody(dto)).build()
        )
    }

    // ---------- Connect / Hub ----------

    suspend fun connectSelf(): ConnectInfo {
        requireBase()
        return execute(
            ConnectInfo.serializer(),
            Request.Builder().url(base() + "/api/connect").get().build()
        )
    }

    suspend fun connectsInfo(): List<ConnectInfo> {
        requireBase()
        return execute(
            ListSerializer(ConnectInfo.serializer()),
            Request.Builder().url(base() + "/api/connects/info").get().build()
        )
    }

    /** 拉取远端实例时间线（Hub 聚合用） */
    suspend fun remoteEchoPage(remoteBase: String, page: Int, pageSize: Int): PageResult<Echo> {
        val body = """{"page":$page,"pageSize":$pageSize}"""
        return executePublic(
            PageResult.serializer(Echo.serializer()),
            Request.Builder()
                .url(remoteBase.trimEnd('/') + "/api/echo/page")
                .post(jsonBody(body))
                .header("X-Timezone", deviceTimezoneId)
                .build()
        )
    }

    /** 远端单条说说（Hub 详情用） */
    suspend fun remoteEchoDetail(remoteBase: String, id: String): Echo {
        return executePublic(
            Echo.serializer(),
            Request.Builder()
                .url(remoteBase.trimEnd('/') + "/api/echo/$id")
                .header("X-Timezone", deviceTimezoneId)
                .get()
                .build()
        )
    }

    /** 远端实例信息（友链详情页头像 / 站名） */
    suspend fun remoteConnect(remoteBase: String): ConnectInfo {
        return executePublic(
            ConnectInfo.serializer(),
            Request.Builder()
                .url(remoteBase.trimEnd('/') + "/api/connect")
                .header("X-Timezone", deviceTimezoneId)
                .get()
                .build()
        )
    }

    /** 远端评论表单元信息 */
    suspend fun remoteCommentsForm(remoteBase: String, echoId: String): FormMeta {
        return executePublic(
            FormMeta.serializer(),
            Request.Builder()
                .url(remoteBase.trimEnd('/') + "/api/comments/form?echo_id=$echoId")
                .header("X-Timezone", deviceTimezoneId)
                .get()
                .build()
        )
    }

    /** 远端发评论 */
    suspend fun remotePostComment(remoteBase: String, dto: CreateCommentDto): CommentResult {
        return executePublic(
            CommentResult.serializer(),
            Request.Builder()
                .url(remoteBase.trimEnd('/') + "/api/comments")
                .header("X-Timezone", deviceTimezoneId)
                .post(jsonBody(json.encodeToString(CreateCommentDto.serializer(), dto)))
                .build()
        )
    }

    /** 远端验证码：与本站相同的 gocap PoW，但 challenge/redeem 走远端地址 */
    suspend fun solveRemoteCaptcha(remoteBase: String, endpoint: String): String =
        solveCaptchaAt(remoteBase, endpoint)

    /** 远端评论列表（只读） */
    suspend fun remoteComments(remoteBase: String, echoId: String): List<PublicComment> {
        return executePublic(
            ListSerializer(PublicComment.serializer()),
            Request.Builder()
                .url(remoteBase.trimEnd('/') + "/api/comments?echo_id=$echoId")
                .get()
                .build()
        )
    }
}
