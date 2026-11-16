package top.saltfish.ech0.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 信息流缓存有效期：先读缓存，过期后在后台刷新；手动刷新永远生效 */
const val FeedCacheTtlMs = 3 * 60 * 1000L

/** 发布 / 编辑 / 删除后，通知时间线立即刷新 */
object FeedRefreshBus {
    var tick by mutableIntStateOf(0)
        private set

    fun bump() {
        tick++
    }
}

/**
 * 每次进程启动后，各信息流第一次出现都强制静默刷新一次；
 * 之后同一进程内切换 tab 仍走 TTL，避免过度请求。
 */
object LaunchSession {
    private var timeline = true
    private var discover = true
    private var hub = true

    /** 启动品牌动效每个进程只播一次（旋转/重建不重播） */
    @Volatile
    var splashShown = false

    @Synchronized
    fun consumeTimeline(): Boolean = timeline.also { timeline = false }

    @Synchronized
    fun consumeDiscover(): Boolean = discover.also { discover = false }

    @Synchronized
    fun consumeHub(): Boolean = hub.also { hub = false }
}

fun isCacheStale(savedAt: Long): Boolean =
    savedAt <= 0L || System.currentTimeMillis() - savedAt > FeedCacheTtlMs

/** 悬浮 dock 的高度 + 安全区；各页统一用它做底部 padding */
@Composable
fun dockContentPadding(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp

/** 统一的顶栏刷新按钮：刷新中显示 M3E LoadingIndicator，结束后回到刷新图标 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RefreshIconButton(
    refreshing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (refreshing) {
        Box(
            modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator(modifier = Modifier.size(20.dp))
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
        }
    }
}

/** 全局轻提示：点赞失败、刷新失败等瞬时错误统一从这里出 */
object AppMessages {
    private var host: SnackbarHostState? = null
    private var scope: CoroutineScope? = null

    fun attach(hostState: SnackbarHostState, coroutineScope: CoroutineScope) {
        host = hostState
        scope = coroutineScope
    }

    fun show(message: String) {
        val h = host ?: return
        val s = scope ?: return
        s.launch {
            h.currentSnackbarData?.dismiss()
            h.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }
}
