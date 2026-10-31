package top.saltfish.ech0.data.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 把 OkHttp 的异步回调桥接成可取消的 suspend 调用。
 *
 * 注意：不要用 withContext(IO) { call.execute() } 代替它。协程取消不会中断
 * 阻塞的 execute()，请求会继续占着线程直到超时；这里会在取消时真正 cancel()。
 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }
        }
    )
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
}
