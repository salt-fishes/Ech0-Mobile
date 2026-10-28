package top.saltfish.ech0.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import top.saltfish.ech0.BuildConfig
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0
)

@Serializable
data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val html_url: String = "",
    val published_at: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

/** 固定检查仓库；不再依赖 DataStore 里的历史值，避免旧版本保存的错误仓库导致 404 */
const val DefaultGitHubRepo = "salt-fishes/Ech0-Mobile"

/** 兼容早期默认值 / 大小写变体 */
fun normalizeGitHubRepo(value: String?): String {
    val repo = value?.trim()?.removePrefix("https://github.com/")
        ?.removePrefix("http://github.com/")?.trimEnd('/').orEmpty()
    return if (repo.isBlank() || repo.equals("saltfish/ech0-mobile", ignoreCase = true)) {
        DefaultGitHubRepo
    } else {
        repo
    }
}

data class AppRelease(
    val versionName: String,
    val tagName: String,
    val notes: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val publishedAt: String?
)

/**
 * 客户端版本检查：只查 GitHub Release，不自动下载/安装 APK。
 * 启动时静默调用；设置页手动调用时由 UI 统一提示。
 */
object UpdateChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun check(repo: String): Result<AppRelease?> = withContext(Dispatchers.IO) {
        try {
            val normalized = repo.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .trimEnd('/')
            if (normalized.isBlank() || !normalized.contains('/')) {
                return@withContext Result.success(null)
            }
            val request = Request.Builder()
                .url("https://api.github.com/repos/$normalized/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Ech0-Mobile/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext Result.success(null)
                if (response.code == 403 || response.code == 429) {
                    return@withContext Result.failure(
                        ApiException(
                            "GITHUB_RATE_LIMIT",
                            "GitHub 请求过于频繁，请稍后再试",
                            429
                        )
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        ApiException(
                            "GITHUB_HTTP_${response.code}",
                            "GitHub 请求失败 (${response.code})",
                            response.code
                        )
                    )
                }
                val release = json.decodeFromString(
                    GitHubRelease.serializer(),
                    response.body?.string().orEmpty()
                )
                val remoteVersion = release.tag_name
                    .removePrefix("v")
                    .substringBefore('-')
                    .trim()
                if (!isVersionNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
                    return@withContext Result.success(null)
                }
                val apk = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true) &&
                        it.browser_download_url.isNotBlank()
                }
                Result.success(
                    AppRelease(
                        versionName = remoteVersion,
                        tagName = release.tag_name,
                        notes = release.body.ifBlank { release.name },
                        htmlUrl = release.html_url,
                        apkUrl = apk?.browser_download_url,
                        publishedAt = release.published_at
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 简单的点分版本比较，忽略预发布后缀 */
    fun isVersionNewer(remote: String, current: String): Boolean {
        val a = parseVersion(remote) ?: return false
        val b = parseVersion(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }

    private fun parseVersion(value: String): List<Int>? {
        val parts = value.trim().removePrefix("v").substringBefore('-').split('.')
        if (parts.isEmpty()) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }
}
