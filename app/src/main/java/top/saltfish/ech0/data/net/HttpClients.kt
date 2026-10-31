package top.saltfish.ech0.data.net

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import top.saltfish.ech0.data.Ech0Store
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * 全局 HTTP client 工厂。
 *
 * 所有 client 共享同一个 ConnectionPool 和 Dispatcher，按用途拆分超时与
 * Cookie/认证策略，避免每个 client 各自建线程池和连接池。
 */
object HttpClients {

    /** 部分图床 / 友链站点会拦截 OkHttp 默认 UA，用浏览器 UA 才能取到图片和分页数据 */
    const val BrowserUserAgent =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val connectionPool = ConnectionPool(8, 5, TimeUnit.MINUTES)

    private val dispatcher = Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 6
    }

    private fun parseProxy(raw: String): Pair<String, Int>? {
        val text = raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
        if (text.isBlank()) return null
        val host = text.substringBeforeLast(':', "")
        val port = text.substringAfterLast(':', "").toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        return host to port
    }

    fun isValidProxy(raw: String): Boolean = raw.isBlank() || parseProxy(raw) != null

    /** OkHttp 每次建立连接时读取当前代理设置，无需重建 client */
    private object DynamicProxySelector : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            val parsed = parseProxy(Ech0Store.current.proxyUrl) ?: return listOf(Proxy.NO_PROXY)
            return listOf(
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress(parsed.first, parsed.second)
                )
            )
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }

    /** 只承载共享资源，不挂业务拦截器；由上层派生具体 client */
    val base: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(connectionPool)
        .dispatcher(dispatcher)
        .proxySelector(DynamicProxySelector)
        .retryOnConnectionFailure(true)
        .build()

    private val browserUserAgentInterceptor = Interceptor { chain ->
        val ua = Ech0Store.current.customUserAgent
            .takeIf { it.isNotBlank() }
            ?: BrowserUserAgent
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", ua)
                .build()
        )
    }

    /** 仅 Wi-Fi 图片：缓存命中不会经过网络，未缓存时才被拦截 */
    private val wifiImageGuardInterceptor = Interceptor { chain ->
        if (!NetworkPolicy.canLoadImages()) {
            throw IOException("wifi_only_images")
        }
        chain.proceed(chain.request())
    }

    /** 图片下载 / Coil；读超时要比三方元数据长 */
    val image: OkHttpClient = base.newBuilder()
        .addInterceptor(wifiImageGuardInterceptor)
        .addInterceptor(browserUserAgentInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    /** 友链远端 API：不能带本站 token，但也不能用 10 秒以内的激进超时 */
    val remote: OkHttpClient = base.newBuilder()
        .addInterceptor(browserUserAgentInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    /** GitHub / B 站 / X / Meting 等第三方元数据 */
    val thirdParty: OkHttpClient = base.newBuilder()
        .addInterceptor(browserUserAgentInterceptor)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** GitHub Release 检查 */
    val update: OkHttpClient = base.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
}
