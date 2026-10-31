package top.saltfish.ech0.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import top.saltfish.ech0.data.repo.PostStore
import top.saltfish.ech0.data.repo.SiteStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore(name = "ech0_settings")

/** 当前登录/配置快照（OkHttp 拦截器同步读取用） */
data class Ech0State(
    val baseUrl: String = "",
    val accessToken: String? = null,
    val tokenExpiresAt: Long = 0,
    val username: String = "",
    val isAdmin: Boolean = false,
    val likedIds: Set<String> = emptySet(),
    val compressImages: Boolean = true,
    /** 编辑器默认图片质量：82 标准 / 92 高清 / 0 原图 */
    val defaultImageQuality: Int = 82,
    val themeMode: String = "system", // system | light | dark
    val serverName: String = "",
    val dynamicColor: Boolean = true,
    val accentSeed: Long = 0xFF0B57D0,
    val backgroundStyle: String = "default", // default | paper | mist | dusk | custom
    val backgroundImage: String = "",
    val backgroundBlur: Boolean = false,
    val githubRepo: String = "salt-fishes/Ech0-Mobile",
    val autoCheckAppUpdate: Boolean = true,
    val commentEmail: String = "",
    val avatarUrl: String = "",
    val lowDataMode: Boolean = false,
    val commentNickname: String = "",
    val commentWebsite: String = "",
    val commentUseDefault: Boolean = true,
    /** 关闭编辑器时是否先询问保存；设置页以「自动保存草稿」的相反开关呈现 */
    val promptSaveDraft: Boolean = true,
    /** 全局字体缩放：0.85–1.30 */
    val textScale: Float = 1f,
    // ---------- 网络与流量 ----------
    val slowInstanceMode: Boolean = false,
    val wifiOnlyImages: Boolean = false,
    val wifiOnlyMedia: Boolean = false,
    val wifiOnlyUploads: Boolean = false,
    val preloadNextPage: Boolean = true,
    val feedCacheTtlMinutes: Int = 3,
    val lowDataAvatars: Boolean = true,
    val lowDataImages: Boolean = true,
    val lowDataExtensionCovers: Boolean = true,
    val lowDataExtensionMeta: Boolean = true,
    // ---------- 阅读与外观 ----------
    val lineHeightScale: Float = 1f,
    val linkOpenMode: String = "inApp", // inApp | system
    val feedImageQuality: String = "standard", // standard | high | original
    val codeFont: String = "monospace", // monospace | serif
    val autoPlayGif: Boolean = true,
    val autoPlayVideo: Boolean = false,
    val showRelativeTime: Boolean = true,
    val amoledBlack: Boolean = false,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val statusBarIconMode: String = "auto", // auto | light | dark
    val backgroundDim: Float = 0f,
    // ---------- 编辑 ----------
    val defaultLayout: String = "waterfall",
    val defaultPrivate: Boolean = false,
    val defaultTags: List<String> = emptyList(),
    val uploadConcurrency: Int = 2,
    val stripExif: Boolean = true,
    val uploadRetryCount: Int = 1,
    val publishConfirm: Boolean = false,
    // ---------- 评论 ----------
    val commentSort: String = "oldest", // oldest | newest
    val commentConfirm: Boolean = false,
    // ---------- 高级与存储 ----------
    val updateCheckIntervalHours: Int = 24,
    val customUserAgent: String = "",
    val proxyUrl: String = "",
    val imageCacheMaxMb: Int = 150,
    val autoCleanCache: Boolean = true
)

/** 已保存实例：只存地址与显示名，不存 token */
data class SavedInstance(
    val baseUrl: String,
    val name: String = "",
    val lastUsedAt: Long = 0
)

/** 按实例隔离的评论身份 */
data class StoredCommentIdentity(
    val nickname: String,
    val email: String,
    val website: String,
    val useDefault: Boolean = true
)

/** Android Keystore 加密盒：仅用于令牌落盘加密 */
internal object CryptoBox {
    private const val KEY_ALIAS = "ech0_token_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray())
        return android.util.Base64.encodeToString(iv + ct, android.util.Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val all = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        val iv = all.copyOfRange(0, 12)
        val ct = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ct))
    }.getOrNull()
}

object Ech0Store {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token_encrypted")
        val TOKEN_EXPIRES = longPreferencesKey("token_expires_at")
        val USERNAME = stringPreferencesKey("username")
        val IS_ADMIN = booleanPreferencesKey("is_admin")
        val LIKED = stringSetPreferencesKey("liked_ids")
        val COMPRESS = booleanPreferencesKey("compress_images")
        val DEFAULT_IMAGE_QUALITY = intPreferencesKey("default_image_quality")
        val THEME = stringPreferencesKey("theme_mode")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_SEED = longPreferencesKey("accent_seed")
        val BACKGROUND_STYLE = stringPreferencesKey("background_style")
        val BACKGROUND_IMAGE = stringPreferencesKey("background_image")
        val BACKGROUND_BLUR = booleanPreferencesKey("background_blur")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_app_update")
        val COMMENT_EMAIL = stringPreferencesKey("comment_email")
        val COMMENT_NICKNAME = stringPreferencesKey("comment_nickname")
        val COMMENT_WEBSITE = stringPreferencesKey("comment_website")
        val COMMENT_USE_DEFAULT = booleanPreferencesKey("comment_use_default")
        val PROMPT_SAVE_DRAFT = booleanPreferencesKey("prompt_save_draft")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val LOW_DATA_MODE = booleanPreferencesKey("low_data_mode")
        val SLOW_INSTANCE = booleanPreferencesKey("slow_instance_mode")
        val WIFI_ONLY_IMAGES = booleanPreferencesKey("wifi_only_images")
        val WIFI_ONLY_MEDIA = booleanPreferencesKey("wifi_only_media")
        val WIFI_ONLY_UPLOADS = booleanPreferencesKey("wifi_only_uploads")
        val PRELOAD_NEXT_PAGE = booleanPreferencesKey("preload_next_page")
        val FEED_CACHE_TTL_MINUTES = intPreferencesKey("feed_cache_ttl_minutes")
        val LOW_DATA_AVATARS = booleanPreferencesKey("low_data_avatars")
        val LOW_DATA_IMAGES = booleanPreferencesKey("low_data_images")
        val LOW_DATA_EXT_COVERS = booleanPreferencesKey("low_data_extension_covers")
        val LOW_DATA_EXT_META = booleanPreferencesKey("low_data_extension_meta")
        val LINE_HEIGHT_SCALE = floatPreferencesKey("line_height_scale")
        val LINK_OPEN_MODE = stringPreferencesKey("link_open_mode")
        val FEED_IMAGE_QUALITY = stringPreferencesKey("feed_image_quality")
        val CODE_FONT = stringPreferencesKey("code_font")
        val AUTO_PLAY_GIF = booleanPreferencesKey("auto_play_gif")
        val AUTO_PLAY_VIDEO = booleanPreferencesKey("auto_play_video")
        val SHOW_RELATIVE_TIME = booleanPreferencesKey("show_relative_time")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val STATUS_BAR_ICON_MODE = stringPreferencesKey("status_bar_icon_mode")
        val BACKGROUND_DIM = floatPreferencesKey("background_dim")
        val DEFAULT_LAYOUT = stringPreferencesKey("default_layout")
        val DEFAULT_PRIVATE = booleanPreferencesKey("default_private")
        val DEFAULT_TAGS = stringPreferencesKey("default_tags_json")
        val UPLOAD_CONCURRENCY = intPreferencesKey("upload_concurrency")
        val STRIP_EXIF = booleanPreferencesKey("strip_exif")
        val UPLOAD_RETRY_COUNT = intPreferencesKey("upload_retry_count")
        val PUBLISH_CONFIRM = booleanPreferencesKey("publish_confirm")
        val COMMENT_SORT = stringPreferencesKey("comment_sort")
        val COMMENT_CONFIRM = booleanPreferencesKey("comment_confirm")
        val COMMENT_IDENTITIES = stringPreferencesKey("comment_identities_json")
        val COMMENT_DRAFTS = stringPreferencesKey("comment_drafts_json")
        val UPDATE_CHECK_INTERVAL_HOURS = intPreferencesKey("update_check_interval_hours")
        val CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")
        val PROXY_URL = stringPreferencesKey("proxy_url")
        val IMAGE_CACHE_MAX_MB = intPreferencesKey("image_cache_max_mb")
        val AUTO_CLEAN_CACHE = booleanPreferencesKey("auto_clean_cache")
        val SAVED_INSTANCES = stringPreferencesKey("saved_instances_json")
        val COOKIES = stringPreferencesKey("cookies_json")
        val DRAFTS = stringPreferencesKey("drafts_json")
        val QUEUE = stringPreferencesKey("queue_json")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }

    /** Compose 可观察：点赞/主题等状态变化后，列表与详情会一起刷新 */
    var current: Ech0State by mutableStateOf(Ech0State())
        private set

    @Volatile
    var cookiesJson: String = "[]"
        private set

    @Volatile
    var draftsJson: String = "[]"

    @Volatile
    var queueJson: String = "[]"

    /** DataStore 首次加载完成：期间不要清理草稿文件，避免把尚未读出的草稿图片当孤儿删掉 */
    @Volatile
    var isLoaded: Boolean = false
        private set

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch { reload() }
    }

    suspend fun reload() {
        val prefs = appContext.dataStore.data.first()
        current = Ech0State(
            baseUrl = prefs[Keys.BASE_URL] ?: "",
            accessToken = prefs[Keys.TOKEN]?.let { CryptoBox.decrypt(it) },
            tokenExpiresAt = prefs[Keys.TOKEN_EXPIRES] ?: 0,
            username = prefs[Keys.USERNAME] ?: "",
            isAdmin = prefs[Keys.IS_ADMIN] ?: false,
            likedIds = prefs[Keys.LIKED] ?: emptySet(),
            compressImages = prefs[Keys.COMPRESS] ?: true,
            defaultImageQuality = prefs[Keys.DEFAULT_IMAGE_QUALITY]
                ?: if (prefs[Keys.COMPRESS] ?: true) 82 else 0,
            themeMode = prefs[Keys.THEME] ?: "system",
            serverName = prefs[Keys.SERVER_NAME] ?: "",
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            accentSeed = prefs[Keys.ACCENT_SEED] ?: 0xFF0B57D0,
            backgroundStyle = prefs[Keys.BACKGROUND_STYLE] ?: "default",
            backgroundImage = prefs[Keys.BACKGROUND_IMAGE] ?: "",
            backgroundBlur = prefs[Keys.BACKGROUND_BLUR] ?: false,
            githubRepo = normalizeGitHubRepo(prefs[Keys.GITHUB_REPO]),
            autoCheckAppUpdate = prefs[Keys.AUTO_CHECK_UPDATE] ?: true,
            commentEmail = prefs[Keys.COMMENT_EMAIL] ?: "",
            avatarUrl = prefs[Keys.AVATAR_URL] ?: "",
            lowDataMode = prefs[Keys.LOW_DATA_MODE] ?: false,
            commentNickname = prefs[Keys.COMMENT_NICKNAME] ?: "",
            commentWebsite = prefs[Keys.COMMENT_WEBSITE] ?: "",
            commentUseDefault = prefs[Keys.COMMENT_USE_DEFAULT] ?: true,
            promptSaveDraft = prefs[Keys.PROMPT_SAVE_DRAFT] ?: true,
            textScale = prefs[Keys.TEXT_SCALE] ?: 1f,
            slowInstanceMode = prefs[Keys.SLOW_INSTANCE] ?: false,
            wifiOnlyImages = prefs[Keys.WIFI_ONLY_IMAGES] ?: false,
            wifiOnlyMedia = prefs[Keys.WIFI_ONLY_MEDIA] ?: false,
            wifiOnlyUploads = prefs[Keys.WIFI_ONLY_UPLOADS] ?: false,
            preloadNextPage = prefs[Keys.PRELOAD_NEXT_PAGE] ?: true,
            feedCacheTtlMinutes = prefs[Keys.FEED_CACHE_TTL_MINUTES] ?: 3,
            lowDataAvatars = prefs[Keys.LOW_DATA_AVATARS] ?: true,
            lowDataImages = prefs[Keys.LOW_DATA_IMAGES] ?: true,
            lowDataExtensionCovers = prefs[Keys.LOW_DATA_EXT_COVERS] ?: true,
            lowDataExtensionMeta = prefs[Keys.LOW_DATA_EXT_META] ?: true,
            lineHeightScale = prefs[Keys.LINE_HEIGHT_SCALE] ?: 1f,
            linkOpenMode = prefs[Keys.LINK_OPEN_MODE] ?: "inApp",
            feedImageQuality = prefs[Keys.FEED_IMAGE_QUALITY] ?: "standard",
            codeFont = prefs[Keys.CODE_FONT] ?: "monospace",
            autoPlayGif = prefs[Keys.AUTO_PLAY_GIF] ?: true,
            autoPlayVideo = prefs[Keys.AUTO_PLAY_VIDEO] ?: false,
            showRelativeTime = prefs[Keys.SHOW_RELATIVE_TIME] ?: true,
            amoledBlack = prefs[Keys.AMOLED_BLACK] ?: false,
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
            statusBarIconMode = prefs[Keys.STATUS_BAR_ICON_MODE] ?: "auto",
            backgroundDim = prefs[Keys.BACKGROUND_DIM] ?: 0f,
            defaultLayout = prefs[Keys.DEFAULT_LAYOUT] ?: "waterfall",
            defaultPrivate = prefs[Keys.DEFAULT_PRIVATE] ?: false,
            defaultTags = parseStringList(prefs[Keys.DEFAULT_TAGS]),
            uploadConcurrency = prefs[Keys.UPLOAD_CONCURRENCY] ?: 2,
            stripExif = prefs[Keys.STRIP_EXIF] ?: true,
            uploadRetryCount = prefs[Keys.UPLOAD_RETRY_COUNT] ?: 1,
            publishConfirm = prefs[Keys.PUBLISH_CONFIRM] ?: false,
            commentSort = prefs[Keys.COMMENT_SORT] ?: "oldest",
            commentConfirm = prefs[Keys.COMMENT_CONFIRM] ?: false,
            updateCheckIntervalHours = prefs[Keys.UPDATE_CHECK_INTERVAL_HOURS] ?: 24,
            customUserAgent = prefs[Keys.CUSTOM_USER_AGENT] ?: "",
            proxyUrl = prefs[Keys.PROXY_URL] ?: "",
            imageCacheMaxMb = prefs[Keys.IMAGE_CACHE_MAX_MB] ?: 150,
            autoCleanCache = prefs[Keys.AUTO_CLEAN_CACHE] ?: true
        )
        cookiesJson = prefs[Keys.COOKIES] ?: "[]"
        draftsJson = prefs[Keys.DRAFTS] ?: "[]"
        queueJson = prefs[Keys.QUEUE] ?: "[]"
        isLoaded = true
    }

    /** 等待首次设置加载完成（带超时，避免 DataStore 异常时永久阻塞） */
    suspend fun awaitLoaded(timeoutMs: Long = 4000) {
        val start = System.currentTimeMillis()
        while (!isLoaded && System.currentTimeMillis() - start < timeoutMs) {
            delay(50)
        }
    }

    // lazy：appContext 由 init() 赋值，不能在 <clinit> 阶段访问
    val stateFlow: Flow<Ech0State> by lazy {
        appContext.dataStore.data.map { p ->
            Ech0State(
                baseUrl = p[Keys.BASE_URL] ?: "",
                accessToken = p[Keys.TOKEN]?.let { CryptoBox.decrypt(it) },
                tokenExpiresAt = p[Keys.TOKEN_EXPIRES] ?: 0,
                username = p[Keys.USERNAME] ?: "",
                isAdmin = p[Keys.IS_ADMIN] ?: false,
                likedIds = p[Keys.LIKED] ?: emptySet(),
                compressImages = p[Keys.COMPRESS] ?: true,
                defaultImageQuality = p[Keys.DEFAULT_IMAGE_QUALITY]
                    ?: if (p[Keys.COMPRESS] ?: true) 82 else 0,
                themeMode = p[Keys.THEME] ?: "system",
                serverName = p[Keys.SERVER_NAME] ?: "",
                dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
                accentSeed = p[Keys.ACCENT_SEED] ?: 0xFF0B57D0,
                backgroundStyle = p[Keys.BACKGROUND_STYLE] ?: "default",
                backgroundImage = p[Keys.BACKGROUND_IMAGE] ?: "",
                backgroundBlur = p[Keys.BACKGROUND_BLUR] ?: false,
                githubRepo = normalizeGitHubRepo(p[Keys.GITHUB_REPO]),
                autoCheckAppUpdate = p[Keys.AUTO_CHECK_UPDATE] ?: true,
                commentEmail = p[Keys.COMMENT_EMAIL] ?: "",
                avatarUrl = p[Keys.AVATAR_URL] ?: "",
                lowDataMode = p[Keys.LOW_DATA_MODE] ?: false,
                commentNickname = p[Keys.COMMENT_NICKNAME] ?: "",
                commentWebsite = p[Keys.COMMENT_WEBSITE] ?: "",
                commentUseDefault = p[Keys.COMMENT_USE_DEFAULT] ?: true,
                promptSaveDraft = p[Keys.PROMPT_SAVE_DRAFT] ?: true,
                textScale = p[Keys.TEXT_SCALE] ?: 1f,
                slowInstanceMode = p[Keys.SLOW_INSTANCE] ?: false,
                wifiOnlyImages = p[Keys.WIFI_ONLY_IMAGES] ?: false,
                wifiOnlyMedia = p[Keys.WIFI_ONLY_MEDIA] ?: false,
                wifiOnlyUploads = p[Keys.WIFI_ONLY_UPLOADS] ?: false,
                preloadNextPage = p[Keys.PRELOAD_NEXT_PAGE] ?: true,
                feedCacheTtlMinutes = p[Keys.FEED_CACHE_TTL_MINUTES] ?: 3,
                lowDataAvatars = p[Keys.LOW_DATA_AVATARS] ?: true,
                lowDataImages = p[Keys.LOW_DATA_IMAGES] ?: true,
                lowDataExtensionCovers = p[Keys.LOW_DATA_EXT_COVERS] ?: true,
                lowDataExtensionMeta = p[Keys.LOW_DATA_EXT_META] ?: true,
                lineHeightScale = p[Keys.LINE_HEIGHT_SCALE] ?: 1f,
                linkOpenMode = p[Keys.LINK_OPEN_MODE] ?: "inApp",
                feedImageQuality = p[Keys.FEED_IMAGE_QUALITY] ?: "standard",
                codeFont = p[Keys.CODE_FONT] ?: "monospace",
                autoPlayGif = p[Keys.AUTO_PLAY_GIF] ?: true,
                autoPlayVideo = p[Keys.AUTO_PLAY_VIDEO] ?: false,
                showRelativeTime = p[Keys.SHOW_RELATIVE_TIME] ?: true,
                amoledBlack = p[Keys.AMOLED_BLACK] ?: false,
                highContrast = p[Keys.HIGH_CONTRAST] ?: false,
                reduceMotion = p[Keys.REDUCE_MOTION] ?: false,
                statusBarIconMode = p[Keys.STATUS_BAR_ICON_MODE] ?: "auto",
                backgroundDim = p[Keys.BACKGROUND_DIM] ?: 0f,
                defaultLayout = p[Keys.DEFAULT_LAYOUT] ?: "waterfall",
                defaultPrivate = p[Keys.DEFAULT_PRIVATE] ?: false,
                defaultTags = parseStringList(p[Keys.DEFAULT_TAGS]),
                uploadConcurrency = p[Keys.UPLOAD_CONCURRENCY] ?: 2,
                stripExif = p[Keys.STRIP_EXIF] ?: true,
                uploadRetryCount = p[Keys.UPLOAD_RETRY_COUNT] ?: 1,
                publishConfirm = p[Keys.PUBLISH_CONFIRM] ?: false,
                commentSort = p[Keys.COMMENT_SORT] ?: "oldest",
                commentConfirm = p[Keys.COMMENT_CONFIRM] ?: false,
                updateCheckIntervalHours = p[Keys.UPDATE_CHECK_INTERVAL_HOURS] ?: 24,
                customUserAgent = p[Keys.CUSTOM_USER_AGENT] ?: "",
                proxyUrl = p[Keys.PROXY_URL] ?: "",
                imageCacheMaxMb = p[Keys.IMAGE_CACHE_MAX_MB] ?: 150,
                autoCleanCache = p[Keys.AUTO_CLEAN_CACHE] ?: true
            )
        }
    }

    suspend fun setBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        val previous = current.baseUrl.trimEnd('/')
        if (normalized == previous) return

        val previousUrl = previous.toHttpUrlOrNull()
        val nextUrl = normalized.toHttpUrlOrNull()
        val previousHost = previousUrl?.host
        val nextHost = nextUrl?.host
        val schemeChanged = previousUrl != null && nextUrl != null &&
            !previousUrl.scheme.equals(nextUrl.scheme, ignoreCase = true)
        // 同一 host 只改路径时保留登录态；首次配置、换 host 或协议变化视为换实例
        val switchHost = (previous.isBlank() && normalized.isNotBlank()) ||
            schemeChanged ||
            (!previousHost.isNullOrBlank() &&
                !nextHost.isNullOrBlank() &&
                !previousHost.equals(nextHost, ignoreCase = true))

        // 先把旧实例加入收藏，切换后仍能在列表里切回来
        if (previous.isNotBlank()) {
            upsertSavedInstance(previous, current.serverName)
        }

        appContext.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = normalized
            // 点赞、站点名和页面缓存都是实例相关的：切地址后不再复用
            prefs.remove(Keys.LIKED)
            prefs.remove(Keys.SERVER_NAME)
            if (switchHost) {
                prefs.remove(Keys.TOKEN)
                prefs.remove(Keys.TOKEN_EXPIRES)
                prefs[Keys.USERNAME] = ""
                prefs[Keys.IS_ADMIN] = false
                prefs[Keys.AVATAR_URL] = ""
                prefs.remove(Keys.COOKIES)
            }
        }

        current = if (switchHost) {
            current.copy(
                baseUrl = normalized,
                accessToken = null,
                tokenExpiresAt = 0,
                username = "",
                isAdmin = false,
                avatarUrl = "",
                likedIds = emptySet(),
                serverName = ""
            )
        } else {
            current.copy(
                baseUrl = normalized,
                likedIds = emptySet(),
                serverName = ""
            )
        }

        if (switchHost) {
            cookiesJson = "[]"
            Ech0Api.cookieJar.clear()
        }
        Ech0Api.invalidateInstanceCaches()
        PostStore.invalidateAll()
        SiteStore.invalidateAll()
        // 文件删除是阻塞 I/O，不能留在调用方（通常是主线程）上
        withContext(Dispatchers.IO) { DiskCache.clearAll(appContext) }
        upsertSavedInstance(
            normalized,
            current.serverName.ifBlank {
                normalized.toHttpUrlOrNull()?.host ?: normalized
            }
        )
    }

    suspend fun setToken(token: String?, expiresAt: Long) {
        appContext.dataStore.edit {
            if (token != null) {
                it[Keys.TOKEN] = CryptoBox.encrypt(token)
                it[Keys.TOKEN_EXPIRES] = expiresAt
            } else {
                it.remove(Keys.TOKEN)
                it.remove(Keys.TOKEN_EXPIRES)
            }
        }
        current = current.copy(accessToken = token, tokenExpiresAt = expiresAt)
    }

    suspend fun setUser(username: String, isAdmin: Boolean, avatarUrl: String = "") {
        val accountChanged = current.username != username || current.isAdmin != isAdmin
        appContext.dataStore.edit {
            it[Keys.USERNAME] = username
            it[Keys.IS_ADMIN] = isAdmin
            it[Keys.AVATAR_URL] = avatarUrl
        }
        current = current.copy(username = username, isAdmin = isAdmin, avatarUrl = avatarUrl)
        // 账号范围变化（登录 / 退出 / 换号）后，旧页面缓存不再属于当前账号
        if (accountChanged) {
            PostStore.invalidateAll()
            SiteStore.invalidateAll()
            withContext(Dispatchers.IO) { DiskCache.clearAll(appContext) }
        }
    }

    suspend fun setServerName(name: String) {
        appContext.dataStore.edit { it[Keys.SERVER_NAME] = name }
        current = current.copy(serverName = name)
        if (current.baseUrl.isNotBlank() && name.isNotBlank()) {
            upsertSavedInstance(current.baseUrl, name)
        }
    }

    /** 退出登录 / 切换账号：点赞和页面缓存都属于账号相关状态 */
    suspend fun clearLiked() {
        appContext.dataStore.edit { it.remove(Keys.LIKED) }
        current = current.copy(likedIds = emptySet())
    }

    suspend fun clearContentCache() {
        withContext(Dispatchers.IO) { DiskCache.clearAll(appContext) }
    }

    /** 本地先记录，再落盘；网络请求由调用方决定，避免重复点赞 */
    suspend fun setLiked(id: String, liked: Boolean) {
        val next = if (liked) current.likedIds + id else current.likedIds - id
        if (next == current.likedIds) return
        current = current.copy(likedIds = next)
        appContext.dataStore.edit { it[Keys.LIKED] = next }
    }

    suspend fun toggleLiked(id: String): Boolean {
        val next = id !in current.likedIds
        setLiked(id, next)
        return next
    }

    suspend fun setCompress(enabled: Boolean) {
        appContext.dataStore.edit {
            it[Keys.COMPRESS] = enabled
            it[Keys.DEFAULT_IMAGE_QUALITY] = if (enabled) 82 else 0
        }
        current = current.copy(
            compressImages = enabled,
            defaultImageQuality = if (enabled) 82 else 0
        )
    }

    /** 默认图片质量：0 表示原图；旧 compressImages 同步为是否压缩 */
    suspend fun setDefaultImageQuality(quality: Int) {
        val normalized = quality.coerceIn(0, 100)
        appContext.dataStore.edit {
            it[Keys.DEFAULT_IMAGE_QUALITY] = normalized
            it[Keys.COMPRESS] = normalized > 0
        }
        current = current.copy(
            defaultImageQuality = normalized,
            compressImages = normalized > 0
        )
    }

    /** 评论邮箱：Ech0 服务端要求昵称和邮箱同时存在，且邮箱不会公开展示 */
    suspend fun setCommentEmail(email: String) {
        appContext.dataStore.edit { it[Keys.COMMENT_EMAIL] = email }
        current = current.copy(commentEmail = email)
    }

    /** 评论默认身份：昵称 / 邮箱 / 网站；关闭后每次手填 */
    suspend fun setCommentIdentity(
        nickname: String,
        email: String,
        website: String,
        useDefault: Boolean
    ) {
        appContext.dataStore.edit {
            it[Keys.COMMENT_NICKNAME] = nickname
            it[Keys.COMMENT_EMAIL] = email
            it[Keys.COMMENT_WEBSITE] = website
            it[Keys.COMMENT_USE_DEFAULT] = useDefault
            it[Keys.COMMENT_IDENTITIES] = updateIdentityMap(
                it[Keys.COMMENT_IDENTITIES],
                nickname,
                email,
                website,
                useDefault
            )
        }
        current = current.copy(
            commentNickname = nickname,
            commentEmail = email,
            commentWebsite = website,
            commentUseDefault = useDefault
        )
    }

    suspend fun setLowDataMode(enabled: Boolean) {
        appContext.dataStore.edit { it[Keys.LOW_DATA_MODE] = enabled }
        current = current.copy(lowDataMode = enabled)
    }

    suspend fun setPromptSaveDraft(enabled: Boolean) {
        appContext.dataStore.edit { it[Keys.PROMPT_SAVE_DRAFT] = enabled }
        current = current.copy(promptSaveDraft = enabled)
    }

    suspend fun setTextScale(scale: Float) {
        val clamped = scale.coerceIn(0.85f, 1.3f)
        appContext.dataStore.edit { it[Keys.TEXT_SCALE] = clamped }
        current = current.copy(textScale = clamped)
    }

    suspend fun setThemeMode(mode: String) {
        appContext.dataStore.edit { it[Keys.THEME] = mode }
        current = current.copy(themeMode = mode)
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        appContext.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
        current = current.copy(dynamicColor = enabled)
    }

    suspend fun setAccentSeed(seed: Long) {
        appContext.dataStore.edit { it[Keys.ACCENT_SEED] = seed }
        current = current.copy(accentSeed = seed)
    }

    suspend fun setBackgroundStyle(style: String) {
        appContext.dataStore.edit { it[Keys.BACKGROUND_STYLE] = style }
        current = current.copy(backgroundStyle = style)
    }

    suspend fun setBackgroundImage(path: String) {
        appContext.dataStore.edit { it[Keys.BACKGROUND_IMAGE] = path }
        current = current.copy(backgroundImage = path)
    }

    suspend fun setBackgroundBlur(enabled: Boolean) {
        appContext.dataStore.edit { it[Keys.BACKGROUND_BLUR] = enabled }
        current = current.copy(backgroundBlur = enabled)
    }

    suspend fun setGithubRepo(repo: String) {
        val normalized = normalizeGitHubRepo(repo)
        appContext.dataStore.edit { it[Keys.GITHUB_REPO] = normalized }
        current = current.copy(githubRepo = normalized)
    }

    suspend fun setAutoCheckAppUpdate(enabled: Boolean) {
        appContext.dataStore.edit { it[Keys.AUTO_CHECK_UPDATE] = enabled }
        current = current.copy(autoCheckAppUpdate = enabled)
    }

    /** GitHub Release 自动检查节流：默认 24 小时内不重复请求 */
    suspend fun shouldCheckAppUpdate(intervalMs: Long = 24 * 60 * 60 * 1000L): Boolean {
        val last = appContext.dataStore.data.first()[Keys.LAST_UPDATE_CHECK] ?: 0L
        return System.currentTimeMillis() - last >= intervalMs
    }

    suspend fun markAppUpdateChecked() {
        appContext.dataStore.edit {
            it[Keys.LAST_UPDATE_CHECK] = System.currentTimeMillis()
        }
    }

    // ---------- 新增设置项 ----------

    fun appContext(): Context = appContext

    private suspend fun flag(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        value: Boolean,
        update: Ech0State.() -> Ech0State
    ) {
        appContext.dataStore.edit { it[key] = value }
        current = current.update()
    }

    private suspend fun number(
        key: androidx.datastore.preferences.core.Preferences.Key<Int>,
        value: Int,
        update: Ech0State.() -> Ech0State
    ) {
        appContext.dataStore.edit { it[key] = value }
        current = current.update()
    }

    private suspend fun decimal(
        key: androidx.datastore.preferences.core.Preferences.Key<Float>,
        value: Float,
        update: Ech0State.() -> Ech0State
    ) {
        appContext.dataStore.edit { it[key] = value }
        current = current.update()
    }

    private suspend fun text(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String,
        update: Ech0State.() -> Ech0State
    ) {
        appContext.dataStore.edit { it[key] = value }
        current = current.update()
    }

    suspend fun setSlowInstanceMode(enabled: Boolean) =
        flag(Keys.SLOW_INSTANCE, enabled) { copy(slowInstanceMode = enabled) }

    suspend fun setWifiOnlyImages(enabled: Boolean) =
        flag(Keys.WIFI_ONLY_IMAGES, enabled) { copy(wifiOnlyImages = enabled) }

    suspend fun setWifiOnlyMedia(enabled: Boolean) =
        flag(Keys.WIFI_ONLY_MEDIA, enabled) { copy(wifiOnlyMedia = enabled) }

    suspend fun setWifiOnlyUploads(enabled: Boolean) =
        flag(Keys.WIFI_ONLY_UPLOADS, enabled) { copy(wifiOnlyUploads = enabled) }

    suspend fun setPreloadNextPage(enabled: Boolean) =
        flag(Keys.PRELOAD_NEXT_PAGE, enabled) { copy(preloadNextPage = enabled) }

    suspend fun setFeedCacheTtlMinutes(minutes: Int) =
        number(Keys.FEED_CACHE_TTL_MINUTES, minutes.coerceIn(1, 60)) {
            copy(feedCacheTtlMinutes = minutes.coerceIn(1, 60))
        }

    suspend fun setLowDataAvatars(enabled: Boolean) =
        flag(Keys.LOW_DATA_AVATARS, enabled) { copy(lowDataAvatars = enabled) }

    suspend fun setLowDataImages(enabled: Boolean) =
        flag(Keys.LOW_DATA_IMAGES, enabled) { copy(lowDataImages = enabled) }

    suspend fun setLowDataExtensionCovers(enabled: Boolean) =
        flag(Keys.LOW_DATA_EXT_COVERS, enabled) { copy(lowDataExtensionCovers = enabled) }

    suspend fun setLowDataExtensionMeta(enabled: Boolean) =
        flag(Keys.LOW_DATA_EXT_META, enabled) { copy(lowDataExtensionMeta = enabled) }

    suspend fun setLineHeightScale(scale: Float) =
        decimal(Keys.LINE_HEIGHT_SCALE, scale.coerceIn(0.8f, 1.6f)) {
            copy(lineHeightScale = scale.coerceIn(0.8f, 1.6f))
        }

    suspend fun setLinkOpenMode(mode: String) =
        text(Keys.LINK_OPEN_MODE, mode) { copy(linkOpenMode = mode) }

    suspend fun setFeedImageQuality(quality: String) =
        text(Keys.FEED_IMAGE_QUALITY, quality) { copy(feedImageQuality = quality) }

    suspend fun setCodeFont(font: String) =
        text(Keys.CODE_FONT, font) { copy(codeFont = font) }

    suspend fun setAutoPlayGif(enabled: Boolean) =
        flag(Keys.AUTO_PLAY_GIF, enabled) { copy(autoPlayGif = enabled) }

    suspend fun setAutoPlayVideo(enabled: Boolean) =
        flag(Keys.AUTO_PLAY_VIDEO, enabled) { copy(autoPlayVideo = enabled) }

    suspend fun setShowRelativeTime(enabled: Boolean) =
        flag(Keys.SHOW_RELATIVE_TIME, enabled) { copy(showRelativeTime = enabled) }

    suspend fun setAmoledBlack(enabled: Boolean) =
        flag(Keys.AMOLED_BLACK, enabled) { copy(amoledBlack = enabled) }

    suspend fun setHighContrast(enabled: Boolean) =
        flag(Keys.HIGH_CONTRAST, enabled) { copy(highContrast = enabled) }

    suspend fun setReduceMotion(enabled: Boolean) =
        flag(Keys.REDUCE_MOTION, enabled) { copy(reduceMotion = enabled) }

    suspend fun setStatusBarIconMode(mode: String) =
        text(Keys.STATUS_BAR_ICON_MODE, mode) { copy(statusBarIconMode = mode) }

    suspend fun setBackgroundDim(value: Float) =
        decimal(Keys.BACKGROUND_DIM, value.coerceIn(0f, 0.6f)) {
            copy(backgroundDim = value.coerceIn(0f, 0.6f))
        }

    suspend fun setDefaultLayout(layout: String) =
        text(Keys.DEFAULT_LAYOUT, layout) { copy(defaultLayout = layout) }

    suspend fun setDefaultPrivate(enabled: Boolean) =
        flag(Keys.DEFAULT_PRIVATE, enabled) { copy(defaultPrivate = enabled) }

    suspend fun setDefaultTags(tags: List<String>) {
        val clean = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(5)
        appContext.dataStore.edit { it[Keys.DEFAULT_TAGS] = toJsonArray(clean).toString() }
        current = current.copy(defaultTags = clean)
    }

    suspend fun setUploadConcurrency(value: Int) =
        number(Keys.UPLOAD_CONCURRENCY, value.coerceIn(1, 3)) {
            copy(uploadConcurrency = value.coerceIn(1, 3))
        }

    suspend fun setStripExif(enabled: Boolean) =
        flag(Keys.STRIP_EXIF, enabled) { copy(stripExif = enabled) }

    suspend fun setUploadRetryCount(value: Int) =
        number(Keys.UPLOAD_RETRY_COUNT, value.coerceIn(0, 2)) {
            copy(uploadRetryCount = value.coerceIn(0, 2))
        }

    suspend fun setPublishConfirm(enabled: Boolean) =
        flag(Keys.PUBLISH_CONFIRM, enabled) { copy(publishConfirm = enabled) }

    suspend fun setCommentSort(value: String) =
        text(Keys.COMMENT_SORT, value) { copy(commentSort = value) }

    suspend fun setCommentConfirm(enabled: Boolean) =
        flag(Keys.COMMENT_CONFIRM, enabled) { copy(commentConfirm = enabled) }

    suspend fun setUpdateCheckIntervalHours(hours: Int) =
        number(Keys.UPDATE_CHECK_INTERVAL_HOURS, hours.coerceIn(0, 168)) {
            copy(updateCheckIntervalHours = hours.coerceIn(0, 168))
        }

    suspend fun setCustomUserAgent(value: String) =
        text(Keys.CUSTOM_USER_AGENT, value.trim()) { copy(customUserAgent = value.trim()) }

    suspend fun setProxyUrl(value: String) =
        text(Keys.PROXY_URL, value.trim()) { copy(proxyUrl = value.trim()) }

    suspend fun setImageCacheMaxMb(value: Int) =
        number(Keys.IMAGE_CACHE_MAX_MB, value.coerceIn(50, 1000)) {
            copy(imageCacheMaxMb = value.coerceIn(50, 1000))
        }

    suspend fun setAutoCleanCache(enabled: Boolean) =
        flag(Keys.AUTO_CLEAN_CACHE, enabled) { copy(autoCleanCache = enabled) }

    // ---------- 多实例 ----------

    private fun savedInstancesRaw(prefs: androidx.datastore.preferences.core.Preferences): List<SavedInstance> {
        val arr = runCatching { JSONArray(prefs[Keys.SAVED_INSTANCES] ?: "[]") }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("baseUrl").trimEnd('/')
                if (url.isBlank()) continue
                add(
                    SavedInstance(
                        baseUrl = url,
                        name = o.optString("name"),
                        lastUsedAt = o.optLong("lastUsedAt")
                    )
                )
            }
        }
    }

    suspend fun savedInstances(): List<SavedInstance> {
        val stored = savedInstancesRaw(appContext.dataStore.data.first())
        val currentUrl = current.baseUrl.trimEnd('/')
        if (currentUrl.isBlank() || stored.any { it.baseUrl == currentUrl }) return stored
        return listOf(
            SavedInstance(
                currentUrl,
                current.serverName.ifBlank {
                    currentUrl.toHttpUrlOrNull()?.host ?: currentUrl
                },
                System.currentTimeMillis()
            )
        ) + stored
    }

    suspend fun upsertSavedInstance(url: String, name: String = "") {
        val normalized = url.trim().trimEnd('/')
        if (normalized.isBlank()) return
        val list = savedInstancesRaw(appContext.dataStore.data.first())
        val merged = (list.filterNot { it.baseUrl == normalized } + SavedInstance(
            baseUrl = normalized,
            name = name.ifBlank {
                list.firstOrNull { it.baseUrl == normalized }?.name.orEmpty()
            },
            lastUsedAt = System.currentTimeMillis()
        )).sortedByDescending { it.lastUsedAt }.take(8)
        appContext.dataStore.edit {
            it[Keys.SAVED_INSTANCES] = savedInstancesJson(merged)
        }
    }

    suspend fun removeSavedInstance(url: String) {
        val normalized = url.trim().trimEnd('/')
        val list = savedInstancesRaw(appContext.dataStore.data.first())
            .filterNot { it.baseUrl == normalized }
        appContext.dataStore.edit {
            it[Keys.SAVED_INSTANCES] = savedInstancesJson(list)
        }
    }

    private fun savedInstancesJson(list: List<SavedInstance>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("baseUrl", it.baseUrl)
                    .put("name", it.name)
                    .put("lastUsedAt", it.lastUsedAt)
            )
        }
        return arr.toString()
    }

    // ---------- 评论身份（按实例） ----------

    private fun identityKey(baseUrl: String, username: String): String {
        val base = baseUrl.trimEnd('/').ifBlank { "unconfigured" }
        val viewer = username.ifBlank { "guest" }
        return "$base|$viewer"
    }

    suspend fun currentCommentIdentity(): StoredCommentIdentity? {
        val prefs = appContext.dataStore.data.first()
        val key = identityKey(current.baseUrl, current.username)
        val map = runCatching {
            JSONObject(prefs[Keys.COMMENT_IDENTITIES] ?: "{}")
        }.getOrDefault(JSONObject())
        map.optJSONObject(key)?.let { o ->
            return StoredCommentIdentity(
                nickname = o.optString("nickname"),
                email = o.optString("email"),
                website = o.optString("website"),
                useDefault = o.optBoolean("useDefault", true)
            )
        }
        // 旧版本全局身份迁移到当前实例；其他实例不会复用这份身份
        // 旧版本全局身份只在没有任何按实例记录时迁移，避免把 A 站身份带到 B 站
        if (map.length() == 0 &&
            (current.commentEmail.isNotBlank() || current.commentNickname.isNotBlank())
        ) {
            val migrated = StoredCommentIdentity(
                nickname = current.commentNickname,
                email = current.commentEmail,
                website = current.commentWebsite,
                useDefault = current.commentUseDefault
            )
            appContext.dataStore.edit {
                it[Keys.COMMENT_IDENTITIES] = putIdentity(map, key, migrated).toString()
            }
            return migrated
        }
        // 已经有按实例保存的身份，但当前实例没有：返回空身份，避免复用其他实例的邮箱
        if (map.length() > 0) return StoredCommentIdentity("", "", "", true)
        return null
    }

    private fun updateIdentityMap(
        existing: String?,
        nickname: String,
        email: String,
        website: String,
        useDefault: Boolean
    ): String {
        val map = runCatching { JSONObject(existing ?: "{}") }.getOrDefault(JSONObject())
        return putIdentity(
            map,
            identityKey(current.baseUrl, current.username),
            StoredCommentIdentity(nickname, email, website, useDefault)
        ).toString()
    }

    private fun putIdentity(
        map: JSONObject,
        key: String,
        identity: StoredCommentIdentity
    ): JSONObject = map.put(
        key,
        JSONObject()
            .put("nickname", identity.nickname)
            .put("email", identity.email)
            .put("website", identity.website)
            .put("useDefault", identity.useDefault)
    )

    suspend fun clearCookies() {
        appContext.dataStore.edit { it.remove(Keys.COOKIES) }
        cookiesJson = "[]"
        Ech0Api.cookieJar.clear()
    }

    // ---------- 评论草稿 ----------

    suspend fun commentDraft(key: String): String {
        val raw = appContext.dataStore.data.first()[Keys.COMMENT_DRAFTS] ?: return ""
        return runCatching { JSONObject(raw).optString(key) }.getOrDefault("")
    }

    suspend fun saveCommentDraft(key: String, text: String) {
        appContext.dataStore.edit { prefs ->
            val map = runCatching {
                JSONObject(prefs[Keys.COMMENT_DRAFTS] ?: "{}")
            }.getOrDefault(JSONObject())
            if (text.isBlank()) {
                map.remove(key)
            } else {
                map.put(key, text.take(500))
            }
            if (map.length() == 0) prefs.remove(Keys.COMMENT_DRAFTS)
            else prefs[Keys.COMMENT_DRAFTS] = map.toString()
        }
    }

    suspend fun clearCommentDrafts() {
        appContext.dataStore.edit { it.remove(Keys.COMMENT_DRAFTS) }
    }

    // ---------- 导出 / 导入 / 重置 ----------

    fun exportSettings(): JSONObject = JSONObject().apply {
        put("schema", 1)
        put("versionName", top.saltfish.ech0.BuildConfig.VERSION_NAME)
        put("themeMode", current.themeMode)
        put("dynamicColor", current.dynamicColor)
        put("accentSeed", current.accentSeed)
        put("backgroundStyle", current.backgroundStyle)
        put("backgroundBlur", current.backgroundBlur)
        put("backgroundDim", current.backgroundDim)
        put("textScale", current.textScale)
        put("lineHeightScale", current.lineHeightScale)
        put("amoledBlack", current.amoledBlack)
        put("highContrast", current.highContrast)
        put("reduceMotion", current.reduceMotion)
        put("statusBarIconMode", current.statusBarIconMode)
        put("lowDataMode", current.lowDataMode)
        put("lowDataAvatars", current.lowDataAvatars)
        put("lowDataImages", current.lowDataImages)
        put("lowDataExtensionCovers", current.lowDataExtensionCovers)
        put("lowDataExtensionMeta", current.lowDataExtensionMeta)
        put("slowInstanceMode", current.slowInstanceMode)
        put("wifiOnlyImages", current.wifiOnlyImages)
        put("wifiOnlyMedia", current.wifiOnlyMedia)
        put("wifiOnlyUploads", current.wifiOnlyUploads)
        put("preloadNextPage", current.preloadNextPage)
        put("feedCacheTtlMinutes", current.feedCacheTtlMinutes)
        put("linkOpenMode", current.linkOpenMode)
        put("feedImageQuality", current.feedImageQuality)
        put("codeFont", current.codeFont)
        put("autoPlayGif", current.autoPlayGif)
        put("autoPlayVideo", current.autoPlayVideo)
        put("showRelativeTime", current.showRelativeTime)
        put("defaultImageQuality", current.defaultImageQuality)
        put("defaultLayout", current.defaultLayout)
        put("defaultPrivate", current.defaultPrivate)
        put("defaultTags", toJsonArray(current.defaultTags))
        put("uploadConcurrency", current.uploadConcurrency)
        put("stripExif", current.stripExif)
        put("uploadRetryCount", current.uploadRetryCount)
        put("publishConfirm", current.publishConfirm)
        put("commentSort", current.commentSort)
        put("commentConfirm", current.commentConfirm)
        put("autoCheckAppUpdate", current.autoCheckAppUpdate)
        put("updateCheckIntervalHours", current.updateCheckIntervalHours)
        put("customUserAgent", current.customUserAgent)
        put("proxyUrl", current.proxyUrl)
        put("imageCacheMaxMb", current.imageCacheMaxMb)
        put("autoCleanCache", current.autoCleanCache)
        put("promptSaveDraft", current.promptSaveDraft)
    }

    /** 导入公开设置；不包含 token / cookie / 评论邮箱 / 草稿 / 队列 */
    suspend fun importSettings(obj: JSONObject) {
        fun has(key: String) = obj.has(key) && !obj.isNull(key)
        if (has("themeMode")) setThemeMode(obj.optString("themeMode", current.themeMode))
        if (has("dynamicColor")) setDynamicColor(obj.optBoolean("dynamicColor", current.dynamicColor))
        if (has("accentSeed")) setAccentSeed(obj.optLong("accentSeed", current.accentSeed))
        if (has("backgroundStyle")) setBackgroundStyle(obj.optString("backgroundStyle", current.backgroundStyle))
        if (has("backgroundBlur")) setBackgroundBlur(obj.optBoolean("backgroundBlur", current.backgroundBlur))
        if (has("backgroundDim")) setBackgroundDim(obj.optDouble("backgroundDim", current.backgroundDim.toDouble()).toFloat())
        if (has("textScale")) setTextScale(obj.optDouble("textScale", current.textScale.toDouble()).toFloat())
        if (has("lineHeightScale")) setLineHeightScale(obj.optDouble("lineHeightScale", current.lineHeightScale.toDouble()).toFloat())
        if (has("amoledBlack")) setAmoledBlack(obj.optBoolean("amoledBlack", current.amoledBlack))
        if (has("highContrast")) setHighContrast(obj.optBoolean("highContrast", current.highContrast))
        if (has("reduceMotion")) setReduceMotion(obj.optBoolean("reduceMotion", current.reduceMotion))
        if (has("statusBarIconMode")) setStatusBarIconMode(obj.optString("statusBarIconMode", current.statusBarIconMode))
        if (has("lowDataMode")) setLowDataMode(obj.optBoolean("lowDataMode", current.lowDataMode))
        if (has("lowDataAvatars")) setLowDataAvatars(obj.optBoolean("lowDataAvatars", current.lowDataAvatars))
        if (has("lowDataImages")) setLowDataImages(obj.optBoolean("lowDataImages", current.lowDataImages))
        if (has("lowDataExtensionCovers")) setLowDataExtensionCovers(obj.optBoolean("lowDataExtensionCovers", current.lowDataExtensionCovers))
        if (has("lowDataExtensionMeta")) setLowDataExtensionMeta(obj.optBoolean("lowDataExtensionMeta", current.lowDataExtensionMeta))
        if (has("slowInstanceMode")) setSlowInstanceMode(obj.optBoolean("slowInstanceMode", current.slowInstanceMode))
        if (has("wifiOnlyImages")) setWifiOnlyImages(obj.optBoolean("wifiOnlyImages", current.wifiOnlyImages))
        if (has("wifiOnlyMedia")) setWifiOnlyMedia(obj.optBoolean("wifiOnlyMedia", current.wifiOnlyMedia))
        if (has("wifiOnlyUploads")) setWifiOnlyUploads(obj.optBoolean("wifiOnlyUploads", current.wifiOnlyUploads))
        if (has("preloadNextPage")) setPreloadNextPage(obj.optBoolean("preloadNextPage", current.preloadNextPage))
        if (has("feedCacheTtlMinutes")) setFeedCacheTtlMinutes(obj.optInt("feedCacheTtlMinutes", current.feedCacheTtlMinutes))
        if (has("linkOpenMode")) setLinkOpenMode(obj.optString("linkOpenMode", current.linkOpenMode))
        if (has("feedImageQuality")) setFeedImageQuality(obj.optString("feedImageQuality", current.feedImageQuality))
        if (has("codeFont")) setCodeFont(obj.optString("codeFont", current.codeFont))
        if (has("autoPlayGif")) setAutoPlayGif(obj.optBoolean("autoPlayGif", current.autoPlayGif))
        if (has("autoPlayVideo")) setAutoPlayVideo(obj.optBoolean("autoPlayVideo", current.autoPlayVideo))
        if (has("showRelativeTime")) setShowRelativeTime(obj.optBoolean("showRelativeTime", current.showRelativeTime))
        if (has("defaultImageQuality")) setDefaultImageQuality(obj.optInt("defaultImageQuality", current.defaultImageQuality))
        if (has("defaultLayout")) setDefaultLayout(obj.optString("defaultLayout", current.defaultLayout))
        if (has("defaultPrivate")) setDefaultPrivate(obj.optBoolean("defaultPrivate", current.defaultPrivate))
        if (has("defaultTags")) {
            val arr = obj.optJSONArray("defaultTags")
            val tags = buildList {
                if (arr != null) for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
            setDefaultTags(tags)
        }
        if (has("uploadConcurrency")) setUploadConcurrency(obj.optInt("uploadConcurrency", current.uploadConcurrency))
        if (has("stripExif")) setStripExif(obj.optBoolean("stripExif", current.stripExif))
        if (has("uploadRetryCount")) setUploadRetryCount(obj.optInt("uploadRetryCount", current.uploadRetryCount))
        if (has("publishConfirm")) setPublishConfirm(obj.optBoolean("publishConfirm", current.publishConfirm))
        if (has("commentSort")) setCommentSort(obj.optString("commentSort", current.commentSort))
        if (has("commentConfirm")) setCommentConfirm(obj.optBoolean("commentConfirm", current.commentConfirm))
        if (has("autoCheckAppUpdate")) setAutoCheckAppUpdate(obj.optBoolean("autoCheckAppUpdate", current.autoCheckAppUpdate))
        if (has("updateCheckIntervalHours")) setUpdateCheckIntervalHours(obj.optInt("updateCheckIntervalHours", current.updateCheckIntervalHours))
        if (has("customUserAgent")) setCustomUserAgent(obj.optString("customUserAgent", current.customUserAgent))
        if (has("proxyUrl")) setProxyUrl(obj.optString("proxyUrl", current.proxyUrl))
        if (has("imageCacheMaxMb")) setImageCacheMaxMb(obj.optInt("imageCacheMaxMb", current.imageCacheMaxMb))
        if (has("autoCleanCache")) setAutoCleanCache(obj.optBoolean("autoCleanCache", current.autoCleanCache))
        if (has("promptSaveDraft")) setPromptSaveDraft(obj.optBoolean("promptSaveDraft", current.promptSaveDraft))
    }

    /** 重置公开设置：保留登录、实例地址、草稿、队列和缓存 */
    suspend fun resetSettings() {
        appContext.dataStore.edit { p ->
            listOf(
                Keys.COMPRESS, Keys.DEFAULT_IMAGE_QUALITY, Keys.THEME, Keys.DYNAMIC_COLOR,
                Keys.ACCENT_SEED, Keys.BACKGROUND_STYLE, Keys.BACKGROUND_IMAGE,
                Keys.BACKGROUND_BLUR, Keys.AUTO_CHECK_UPDATE, Keys.COMMENT_EMAIL,
                Keys.COMMENT_NICKNAME, Keys.COMMENT_WEBSITE, Keys.COMMENT_USE_DEFAULT,
                Keys.COMMENT_IDENTITIES, Keys.COMMENT_DRAFTS, Keys.PROMPT_SAVE_DRAFT, Keys.TEXT_SCALE,
                Keys.LOW_DATA_MODE, Keys.SLOW_INSTANCE, Keys.WIFI_ONLY_IMAGES,
                Keys.WIFI_ONLY_MEDIA, Keys.WIFI_ONLY_UPLOADS, Keys.PRELOAD_NEXT_PAGE,
                Keys.FEED_CACHE_TTL_MINUTES, Keys.LOW_DATA_AVATARS, Keys.LOW_DATA_IMAGES,
                Keys.LOW_DATA_EXT_COVERS, Keys.LOW_DATA_EXT_META, Keys.LINE_HEIGHT_SCALE,
                Keys.LINK_OPEN_MODE, Keys.FEED_IMAGE_QUALITY, Keys.CODE_FONT,
                Keys.AUTO_PLAY_GIF,
                Keys.AUTO_PLAY_VIDEO, Keys.SHOW_RELATIVE_TIME, Keys.AMOLED_BLACK,
                Keys.HIGH_CONTRAST, Keys.REDUCE_MOTION, Keys.STATUS_BAR_ICON_MODE,
                Keys.BACKGROUND_DIM, Keys.DEFAULT_LAYOUT, Keys.DEFAULT_PRIVATE,
                Keys.DEFAULT_TAGS, Keys.UPLOAD_CONCURRENCY, Keys.STRIP_EXIF,
                Keys.UPLOAD_RETRY_COUNT, Keys.PUBLISH_CONFIRM, Keys.COMMENT_SORT,
                Keys.COMMENT_CONFIRM, Keys.UPDATE_CHECK_INTERVAL_HOURS,
                Keys.CUSTOM_USER_AGENT, Keys.PROXY_URL, Keys.IMAGE_CACHE_MAX_MB,
                Keys.AUTO_CLEAN_CACHE
            ).forEach { p.remove(it) }
        }
        reload()
    }

    private fun toJsonArray(values: List<String>): JSONArray = JSONArray().apply {
        values.forEach { put(it) }
    }

    fun saveCookies(cookies: List<Cookie>) {
        val arr = JSONArray()
        cookies.forEach { c ->
            arr.put(
                JSONObject()
                    .put("name", c.name).put("value", c.value)
                    .put("domain", c.domain).put("path", c.path)
                    .put("expiresAt", c.expiresAt).put("secure", c.secure)
                    .put("httpOnly", c.httpOnly).put("hostOnly", c.hostOnly)
            )
        }
        val json = arr.toString()
        cookiesJson = json
        scope.launch { appContext.dataStore.edit { it[Keys.COOKIES] = json } }
    }

    fun loadCookies(url: HttpUrl): List<Cookie> {
        runCatching {
            val arr = JSONArray(cookiesJson)
            val result = mutableListOf<Cookie>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val b = Cookie.Builder()
                    .name(o.getString("name"))
                    .value(o.getString("value"))
                    .path(o.optString("path", "/"))
                    .expiresAt(o.optLong("expiresAt", Long.MAX_VALUE))
                if (o.optBoolean("secure")) b.secure()
                if (o.optBoolean("httpOnly")) b.httpOnly()
                if (o.optBoolean("hostOnly")) b.hostOnlyDomain(o.getString("domain"))
                else b.domain(o.getString("domain"))
                result.add(b.build())
            }
            return result.filter { it.matches(url) }
        }
        return emptyList()
    }

    suspend fun persistDrafts(json: String) {
        draftsJson = json
        appContext.dataStore.edit { it[Keys.DRAFTS] = json }
    }

    suspend fun persistQueue(json: String) {
        queueJson = json
        appContext.dataStore.edit { it[Keys.QUEUE] = json }
    }
}

/** 持久化 CookieJar：refresh_token 在 HttpOnly cookie 里，原生端必须存住它才能续期 */
class PersistentCookieJar : okhttp3.CookieJar {
    @Volatile
    private var cache: Map<String, List<Cookie>> = emptyMap()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val next = cache.toMutableMap()
        // 同名 cookie 覆盖
        cookies.forEach { c ->
            val list = (next[c.domain] ?: emptyList()).filter { it.name != c.name } + c
            next[c.domain] = list
        }
        cache = next
        Ech0Store.saveCookies(next.values.flatten())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (cache.isEmpty()) {
            val all = runCatching { Ech0Store.loadCookies(url) }.getOrDefault(emptyList())
            if (all.isNotEmpty()) {
                val next = all.groupBy { it.domain }
                cache = next
            }
        }
        return cache.values.flatten().filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
    }

    fun clear() {
        cache = emptyMap()
        Ech0Store.saveCookies(emptyList())
    }
}

private fun parseStringList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }.getOrDefault(emptyList())
}
