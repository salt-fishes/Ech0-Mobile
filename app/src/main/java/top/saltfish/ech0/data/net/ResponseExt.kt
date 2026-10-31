package top.saltfish.ech0.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response

/**
 * 读取响应体是阻塞 I/O；即使 OkHttp call 是异步的，协程恢复后仍可能在 Main 上。
 * 统一放到 IO 线程，避免 NetworkOnMainThreadException / 主线程卡顿。
 */
suspend fun Response.bodyText(): String = withContext(Dispatchers.IO) {
    body?.string().orEmpty()
}

suspend fun Response.bodyBytes(): ByteArray = withContext(Dispatchers.IO) {
    body?.bytes() ?: ByteArray(0)
}
