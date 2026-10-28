package top.saltfish.ech0.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 页面/协程离开组合时的取消异常，不应作为用户可见错误 */
fun Throwable.isCancellationLike(): Boolean =
    this is CancellationException ||
        (this is IllegalStateException &&
            message?.contains("left the composition", ignoreCase = true) == true)

/** runCatching 会吞掉 CancellationException，这里把它重新抛出去 */
fun <T> Result<T>.rethrowCancellationLike(): Result<T> {
    exceptionOrNull()?.let { if (it.isCancellationLike()) throw it }
    return this
}

/**
 * 统一把网络/接口异常翻译成用户能看懂的一句话。
 * 页面级错误用 ErrorCard，瞬时错误用 AppMessages，文案都从这里出。
 */
fun Throwable.userMessage(): String {
    if (this is ApiException) {
        val code = errorCode.orEmpty()
        val serverMessage = message.orEmpty().trim()
        return when {
            code == "NO_BASE_URL" -> "请先在设置中填写实例地址"
            code.startsWith("TOKEN_") || httpCode == 401 -> "登录状态已失效，请重新登录"
            code.contains("FORBIDDEN", ignoreCase = true) || httpCode == 403 -> "没有权限执行这个操作"
            httpCode == 404 -> "内容不存在或已经被删除"
            httpCode == 429 || code.contains("RATE", ignoreCase = true) -> "操作太频繁，请稍后再试"
            httpCode >= 500 -> "服务器开小差了，请稍后重试"
            serverMessage.isNotBlank() && !serverMessage.startsWith("HTTP_") -> serverMessage
            else -> "请求失败，请稍后重试"
        }
    }
    return when (this) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "网络连接失败，请检查网络或实例地址"
        is SerializationException -> "返回数据格式异常，请确认实例版本"
        is IOException -> "网络连接失败，请检查网络或实例地址"
        else -> message?.takeIf { it.isNotBlank() } ?: "操作失败，请稍后重试"
    }
}
