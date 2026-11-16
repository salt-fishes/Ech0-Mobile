package top.saltfish.ech0.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.net.NetworkPolicy

/** 根布局注入；未接入共享元素时为空，调用方自动降级 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** 当前 AnimatedContent 的可见性作用域，必须和 SharedTransitionLayout 同一次转场 */
val LocalSharedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** 本地说说的共享 key；不要用列表 index */
fun echoCardKey(echo: Echo): String = "echo:local:${echo.id}"

/** 封面图 key；没有图片或 id/url 都为空时返回 null */
fun echoCoverKey(echo: Echo): String? {
    val file = echo.echo_files.firstOrNull()?.file ?: return null
    val id = file.id.ifBlank { file.url }
    return id.takeIf { it.isNotBlank() }?.let { "image:local:$it" }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.echoSharedBounds(key: String): Modifier {
    if (LocalReduceMotion.current) return this
    if (!NetworkPolicy.canLoadBodyImages()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalSharedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@echoSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.echoSharedElement(key: String): Modifier {
    if (LocalReduceMotion.current) return this
    if (!NetworkPolicy.canLoadBodyImages()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalSharedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@echoSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope
        )
    }
}
