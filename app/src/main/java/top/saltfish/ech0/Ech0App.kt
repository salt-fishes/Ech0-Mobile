package top.saltfish.ech0

import android.app.Application
import android.os.StatFs
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.saltfish.ech0.data.DiskCache as PageCache
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.data.net.HttpClients
import top.saltfish.ech0.data.net.NetworkPolicy
import top.saltfish.ech0.data.repo.SiteStore
import top.saltfish.ech0.publish.PendingFileDeletions
import top.saltfish.ech0.publish.PublishStore

class Ech0App : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Ech0Store.init(this)
        NetworkPolicy.init(this)
        // 清理上次异常退出遗留的草稿暂存图（只删除没有被草稿 / 队列引用的文件）
        appScope.launch {
            Ech0Store.awaitLoaded()
            PageCache.clearLegacy(this@Ech0App)
            runCatching { autoCleanCacheIfNeeded() }
            // 每个进程同步一次服务端用户名 / 头像；不要放在 token 变化里，避免 refresh 后重复请求
            if (Ech0Store.current.baseUrl.isNotBlank() &&
                !Ech0Store.current.accessToken.isNullOrBlank()
            ) {
                runCatching { SiteStore.currentUser(force = true) }.onSuccess { u ->
                    Ech0Store.setUser(u.username, u.is_admin, u.avatar.orEmpty())
                }
            }
            runCatching { PublishStore.cleanupOrphanDraftFiles(this@Ech0App) }
            runCatching { PendingFileDeletions.flush(this@Ech0App) }
        }
    }

    /** 可用空间低于 500MB 时自动清理图片缓存和页面缓存 */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private suspend fun autoCleanCacheIfNeeded() {
        if (!Ech0Store.current.autoCleanCache) return
        val stat = runCatching { StatFs(filesDir.absolutePath) }.getOrNull() ?: return
        if (stat.availableBytes > 500L * 1024 * 1024) return
        runCatching { coil.Coil.imageLoader(this).diskCache?.clear() }
        PageCache.clearAll(this)
    }

    /**
     * 统一图片加载器：使用浏览器 UA。
     * 部分友链 / 图床会拦截 okhttp 默认 UA，导致网页能显示、App 显示空白。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { HttpClients.image }
            .components {
                if (Ech0Store.current.autoPlayGif) add(GifDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this@Ech0App)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this@Ech0App.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(
                        Ech0Store.current.imageCacheMaxMb
                            .coerceIn(50, 1000) * 1024L * 1024L
                    )
                    .build()
            }
            .crossfade(true)
            .build()
}
