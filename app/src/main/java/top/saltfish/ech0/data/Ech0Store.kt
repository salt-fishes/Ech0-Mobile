package top.saltfish.ech0.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
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
    val themeMode: String = "system", // system | light | dark
    val serverName: String = "",
    val dynamicColor: Boolean = true,
    val accentSeed: Long = 0xFF0B57D0,
    val backgroundStyle: String = "default", // default | paper | mist | dusk | custom
    val backgroundImage: String = "",
    val githubRepo: String = "salt-fishes/Ech0-Mobile",
    val autoCheckAppUpdate: Boolean = true,
    val commentEmail: String = "",
    val avatarUrl: String = "",
    val lowDataMode: Boolean = false,
    val commentNickname: String = "",
    val commentWebsite: String = "",
    val commentUseDefault: Boolean = true,
    /** 编辑器点取消 / 下拉关闭时，是否询问保存到草稿箱 */
    val promptSaveDraft: Boolean = true
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
        val THEME = stringPreferencesKey("theme_mode")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_SEED = longPreferencesKey("accent_seed")
        val BACKGROUND_STYLE = stringPreferencesKey("background_style")
        val BACKGROUND_IMAGE = stringPreferencesKey("background_image")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_app_update")
        val COMMENT_EMAIL = stringPreferencesKey("comment_email")
        val COMMENT_NICKNAME = stringPreferencesKey("comment_nickname")
        val COMMENT_WEBSITE = stringPreferencesKey("comment_website")
        val COMMENT_USE_DEFAULT = booleanPreferencesKey("comment_use_default")
        val PROMPT_SAVE_DRAFT = booleanPreferencesKey("prompt_save_draft")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val LOW_DATA_MODE = booleanPreferencesKey("low_data_mode")
        val COOKIES = stringPreferencesKey("cookies_json")
        val DRAFTS = stringPreferencesKey("drafts_json")
        val QUEUE = stringPreferencesKey("queue_json")
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
            themeMode = prefs[Keys.THEME] ?: "system",
            serverName = prefs[Keys.SERVER_NAME] ?: "",
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            accentSeed = prefs[Keys.ACCENT_SEED] ?: 0xFF0B57D0,
            backgroundStyle = prefs[Keys.BACKGROUND_STYLE] ?: "default",
            backgroundImage = prefs[Keys.BACKGROUND_IMAGE] ?: "",
            githubRepo = normalizeGitHubRepo(prefs[Keys.GITHUB_REPO]),
            autoCheckAppUpdate = prefs[Keys.AUTO_CHECK_UPDATE] ?: true,
            commentEmail = prefs[Keys.COMMENT_EMAIL] ?: "",
            avatarUrl = prefs[Keys.AVATAR_URL] ?: "",
            lowDataMode = prefs[Keys.LOW_DATA_MODE] ?: false,
            commentNickname = prefs[Keys.COMMENT_NICKNAME] ?: "",
            commentWebsite = prefs[Keys.COMMENT_WEBSITE] ?: "",
            commentUseDefault = prefs[Keys.COMMENT_USE_DEFAULT] ?: true,
            promptSaveDraft = prefs[Keys.PROMPT_SAVE_DRAFT] ?: true
        )
        cookiesJson = prefs[Keys.COOKIES] ?: "[]"
        draftsJson = prefs[Keys.DRAFTS] ?: "[]"
        queueJson = prefs[Keys.QUEUE] ?: "[]"
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
                themeMode = p[Keys.THEME] ?: "system",
                serverName = p[Keys.SERVER_NAME] ?: "",
                dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
                accentSeed = p[Keys.ACCENT_SEED] ?: 0xFF0B57D0,
                backgroundStyle = p[Keys.BACKGROUND_STYLE] ?: "default",
                backgroundImage = p[Keys.BACKGROUND_IMAGE] ?: "",
                githubRepo = normalizeGitHubRepo(p[Keys.GITHUB_REPO]),
                autoCheckAppUpdate = p[Keys.AUTO_CHECK_UPDATE] ?: true,
                commentEmail = p[Keys.COMMENT_EMAIL] ?: "",
                avatarUrl = p[Keys.AVATAR_URL] ?: "",
                lowDataMode = p[Keys.LOW_DATA_MODE] ?: false,
                commentNickname = p[Keys.COMMENT_NICKNAME] ?: "",
                commentWebsite = p[Keys.COMMENT_WEBSITE] ?: "",
                commentUseDefault = p[Keys.COMMENT_USE_DEFAULT] ?: true,
                promptSaveDraft = p[Keys.PROMPT_SAVE_DRAFT] ?: true
            )
        }
    }

    suspend fun setBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        appContext.dataStore.edit { it[Keys.BASE_URL] = normalized }
        current = current.copy(baseUrl = normalized)
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
        appContext.dataStore.edit {
            it[Keys.USERNAME] = username
            it[Keys.IS_ADMIN] = isAdmin
            it[Keys.AVATAR_URL] = avatarUrl
        }
        current = current.copy(username = username, isAdmin = isAdmin, avatarUrl = avatarUrl)
    }

    suspend fun setServerName(name: String) {
        appContext.dataStore.edit { it[Keys.SERVER_NAME] = name }
        current = current.copy(serverName = name)
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
        appContext.dataStore.edit { it[Keys.COMPRESS] = enabled }
        current = current.copy(compressImages = enabled)
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

    suspend fun setGithubRepo(repo: String) {
        val normalized = normalizeGitHubRepo(repo)
        appContext.dataStore.edit { it[Keys.GITHUB_REPO] = normalized }
        current = current.copy(githubRepo = normalized)
    }

    suspend fun setAutoCheckAppUpdate(enabled: Boolean) {
        appContext.dataStore.edit { it[Keys.AUTO_CHECK_UPDATE] = enabled }
        current = current.copy(autoCheckAppUpdate = enabled)
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
