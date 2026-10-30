package top.saltfish.ech0

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store
import top.saltfish.ech0.publish.PendingFileDeletions
import top.saltfish.ech0.publish.PublishStore

class Ech0App : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Ech0Store.init(this)
        // 清理上次异常退出遗留的草稿暂存图（只删除没有被草稿 / 队列引用的文件）
        appScope.launch {
            Ech0Store.awaitLoaded()
            runCatching { PublishStore.cleanupOrphanDraftFiles(this@Ech0App) }
            runCatching { PendingFileDeletions.flush(this@Ech0App) }
        }
    }

    /**
     * 统一图片加载器：使用浏览器 UA。
     * 部分友链 / 图床会拦截 okhttp 默认 UA，导致网页能显示、App 显示空白。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { Ech0Api.publicClient }
            .crossfade(true)
            .build()
}
