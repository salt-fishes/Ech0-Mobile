package top.saltfish.ech0.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import top.saltfish.ech0.data.ConnectInfo
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Hello
import top.saltfish.ech0.data.SiteSettings
import top.saltfish.ech0.data.Tag
import top.saltfish.ech0.data.User

/**
 * 站点级公共数据缓存：同一实例/账号下只请求一份。
 *
 * settings / hello / self / connects / currentUser / tags 都有 TTL，
 * 同一时刻的相同请求会复用同一个 Deferred。
 */
object SiteStore {

    private const val SettingsTtl = 10 * 60 * 1000L
    private const val HelloTtl = 10 * 60 * 1000L
    private const val ConnectsTtl = 5 * 60 * 1000L
    private const val UserTtl = 15 * 60 * 1000L
    private const val TagsTtl = 5 * 60 * 1000L

    private val settingsCache = TtlCache<SiteSettings>(SettingsTtl)
    private val helloCache = TtlCache<Hello>(HelloTtl)
    private val selfCache = TtlCache<ConnectInfo>(ConnectsTtl)
    private val connectsCache = TtlCache<List<ConnectInfo>>(ConnectsTtl)
    private val userCache = TtlCache<User>(UserTtl)
    private val tagsCache = TtlCache<List<Tag>>(TagsTtl)

    suspend fun settings(force: Boolean = false): SiteSettings =
        settingsCache.get(force) { Ech0Api.settings() }

    suspend fun hello(force: Boolean = false): Hello =
        helloCache.get(force) { Ech0Api.hello() }

    suspend fun self(force: Boolean = false): ConnectInfo =
        selfCache.get(force) { Ech0Api.connectSelf() }

    suspend fun connects(force: Boolean = false): List<ConnectInfo> =
        connectsCache.get(force) { Ech0Api.connectsInfo() }

    suspend fun currentUser(force: Boolean = false): User =
        userCache.get(force) { Ech0Api.currentUser() }

    suspend fun tags(force: Boolean = false): List<Tag> =
        tagsCache.get(force) { Ech0Api.tags() }

    fun invalidateConnects() {
        selfCache.invalidate()
        connectsCache.invalidate()
    }

    fun invalidateTags() {
        tagsCache.invalidate()
    }

    fun invalidateAll() {
        settingsCache.invalidate()
        helloCache.invalidate()
        selfCache.invalidate()
        connectsCache.invalidate()
        userCache.invalidate()
        tagsCache.invalidate()
    }

    /**
     * 小型 TTL + single-flight 缓存。
     * loader 失败时不写入 value，旧值继续可用（由调用方决定是否展示错误）。
     */
    private class TtlCache<T>(private val ttlMs: Long) {
        private val lock = Any()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var value: T? = null
        private var fetchedAt = 0L
        private var inFlight: Deferred<T>? = null
        private var generation = 0

        suspend fun get(force: Boolean, loader: suspend () -> T): T {
            if (!force) {
                val cached = synchronized(lock) {
                    value?.takeIf { System.currentTimeMillis() - fetchedAt < ttlMs }
                }
                if (cached != null) return cached
            }

            val deferred = synchronized(lock) {
                val currentGen = generation
                inFlight ?: scope.async {
                    val fresh = loader()
                    synchronized(lock) {
                        if (generation == currentGen) {
                            value = fresh
                            fetchedAt = System.currentTimeMillis()
                        }
                    }
                    fresh
                }.also { inFlight = it }
            }
            return try {
                deferred.await()
            } finally {
                synchronized(lock) {
                    if (inFlight === deferred) inFlight = null
                }
            }
        }

        fun invalidate() {
            synchronized(lock) {
                generation++
                value = null
                fetchedAt = 0L
                inFlight = null
            }
        }
    }
}
