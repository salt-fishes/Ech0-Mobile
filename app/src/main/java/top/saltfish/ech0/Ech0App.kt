package top.saltfish.ech0

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import top.saltfish.ech0.data.Ech0Api
import top.saltfish.ech0.data.Ech0Store

class Ech0App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Ech0Store.init(this)
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
