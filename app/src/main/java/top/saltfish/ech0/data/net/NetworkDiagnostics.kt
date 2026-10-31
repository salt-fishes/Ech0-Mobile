package top.saltfish.ech0.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class DiagResult(
    val label: String,
    val ok: Boolean,
    val detail: String
)

/** 设置页网络诊断：DNS / TCP / TLS / HTTP 四段，全部在 IO 线程执行 */
object NetworkDiagnostics {

    suspend fun run(baseUrl: String): List<DiagResult> = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        val parsed = base.toHttpUrlOrNull()
            ?: return@withContext listOf(
                DiagResult("地址", false, "实例地址格式不正确")
            )
        val host = parsed.host
        val port = if (parsed.port > 0) parsed.port else if (parsed.isHttps) 443 else 80
        val results = mutableListOf<DiagResult>()

        val dnsStart = System.nanoTime()
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }
        val dnsMs = (System.nanoTime() - dnsStart) / 1_000_000
        results += if (addresses.isSuccess) {
            DiagResult(
                "DNS",
                true,
                "${addresses.getOrThrow().firstOrNull()?.hostAddress ?: host} · ${dnsMs} ms"
            )
        } else {
            DiagResult("DNS", false, "解析失败 · ${dnsMs} ms")
        }

        val tcpStart = System.nanoTime()
        val tcp = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 6_000)
            }
        }
        val tcpMs = (System.nanoTime() - tcpStart) / 1_000_000
        results += DiagResult(
            "TCP",
            tcp.isSuccess,
            if (tcp.isSuccess) "$host:$port · ${tcpMs} ms"
            else "连接失败 · ${tcpMs} ms"
        )

        if (parsed.isHttps) {
            val tlsStart = System.nanoTime()
            val tls = runCatching {
                val socket = SSLSocketFactory.getDefault()
                    .createSocket() as SSLSocket
                socket.connect(InetSocketAddress(host, port), 6_000)
                socket.soTimeout = 6_000
                socket.startHandshake()
                val cipher = socket.session.cipherSuite
                socket.close()
                cipher
            }
            val tlsMs = (System.nanoTime() - tlsStart) / 1_000_000
            results += DiagResult(
                "TLS",
                tls.isSuccess,
                if (tls.isSuccess) "${tls.getOrThrow()} · ${tlsMs} ms"
                else "握手失败 · ${tlsMs} ms"
            )
        } else {
            results += DiagResult("TLS", true, "HTTP 实例，跳过 TLS")
        }

        val httpStart = System.nanoTime()
        val http = runCatching {
            val request = Request.Builder().url("$base/api/hello").get().build()
            HttpClients.remote.newCall(request).execute().use { response ->
                response.code
            }
        }
        val httpMs = (System.nanoTime() - httpStart) / 1_000_000
        results += DiagResult(
            "HTTP /api/hello",
            http.isSuccess && (http.getOrNull() ?: 0) in 200..299,
            if (http.isSuccess) "HTTP ${http.getOrThrow()} · ${httpMs} ms"
            else "请求失败 · ${httpMs} ms"
        )
        results
    }
}
