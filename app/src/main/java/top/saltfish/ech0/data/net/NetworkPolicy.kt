package top.saltfish.ech0.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import top.saltfish.ech0.data.Ech0Store

/** 集中判断网络策略，避免每个页面各读一遍设置 */
object NetworkPolicy {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isWifi(): Boolean {
        val ctx = appContext ?: return true
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun canLoadImages(): Boolean =
        !Ech0Store.current.wifiOnlyImages || isWifi()

    fun canPlayMedia(): Boolean =
        !Ech0Store.current.wifiOnlyMedia || isWifi()

    fun canUpload(): Boolean =
        !Ech0Store.current.wifiOnlyUploads || isWifi()

    fun canPreload(): Boolean = Ech0Store.current.preloadNextPage

    fun canLoadAvatars(): Boolean =
        !(Ech0Store.current.lowDataMode && Ech0Store.current.lowDataAvatars)

    fun canLoadBodyImages(): Boolean =
        !(Ech0Store.current.lowDataMode && Ech0Store.current.lowDataImages)

    fun canLoadExtensionCovers(): Boolean =
        !(Ech0Store.current.lowDataMode && Ech0Store.current.lowDataExtensionCovers)

    fun canLoadExtensionMeta(): Boolean =
        !(Ech0Store.current.lowDataMode && Ech0Store.current.lowDataExtensionMeta)
}
