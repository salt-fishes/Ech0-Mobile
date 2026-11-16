package top.saltfish.ech0.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.saltfish.ech0.data.Echo
import top.saltfish.ech0.data.net.NetworkPolicy

/** 共享元素注入；未接入共享元素时为空，调用方自动退回淡入淡出 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** 当前 AnimatedContent 的可见性作用域，必须和 SharedTransitionLayout 同一次转场 */
val LocalSharedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** 时间线说说共享 key；需要列表稳定 key，不用列表 index */
fun echoCardKey(echo: Echo): String = "echo:local:${echo.id}"

/** 封面图 key；没有图片时用 id/url 兜底，完全为空则返回 null */
fun echoCoverKey(echo: Echo): String? {
    val file = echo.echo_files.firstOrNull()?.file ?: return null
    val id = file.id.ifBlank { file.url }
    return id.takeIf { it.isNotBlank() }?.let { "image:local:$it" }
}

/** 友链远端说说的共享 key：带实例地址，避免不同实例 id 冲突 */
fun echoRemoteCardKey(remoteBase: String, echo: Echo): String =
    "echo:remote:${remoteBase.trimEnd('/')}:${echo.id}"

fun echoRemoteCoverKey(remoteBase: String, echo: Echo): String? {
    val file = echo.echo_files.firstOrNull()?.file ?: return null
    val id = file.id.ifBlank { file.url }
    return id.takeIf { it.isNotBlank() }
        ?.let { "image:remote:${remoteBase.trimEnd('/')}:$it" }
}

/**
 * 共享元素在 overlay 里的裁剪：
 * - 保留卡片 / 图片自己的圆角，避免转场时出现直角
 * - 同时按「顶栏下沿 ~ dock 上沿」裁切，帖子被栏位遮挡时不会先浮到栏位上面再闪下去
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private class EchoOverlayClip(
    private val shape: Shape,
    private val topPx: Float,
    private val bottomPx: Float
) : SharedTransitionScope.OverlayClip {
    private val clipPath = Path()
    private val viewportPath = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density
    ): Path {
        clipPath.reset()
        when (val outline = shape.createOutline(bounds.size, layoutDirection, density)) {
            is Outline.Rectangle -> clipPath.addRect(outline.rect)
            is Outline.Rounded -> clipPath.addRoundRect(outline.roundRect)
            is Outline.Generic -> clipPath.addPath(outline.path)
        }
        clipPath.translate(Offset(bounds.left, bounds.top))

        if (bottomPx > topPx && bounds.bottom > topPx && bounds.top < bottomPx) {
            viewportPath.reset()
            viewportPath.addRect(
                Rect(bounds.left - 1f, topPx, bounds.right + 1f, bottomPx)
            )
            clipPath.op(clipPath, viewportPath, PathOperation.Intersect)
        }
        return clipPath
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberEchoOverlayClip(shape: Shape): SharedTransitionScope.OverlayClip {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // 顶栏实底区域：状态栏 + 72dp；详情页卡片从状态栏 + 70dp 开始，留 2dp 余量
    val topPx = with(density) { (statusTop + 70.dp).toPx() }
    // dock：导航栏 + 88dp；再加上底部渐隐的视觉边界
    val bottomPx = with(density) { (screenHeight - navBottom - 88.dp).toPx() }
    return remember(shape, topPx, bottomPx) {
        EchoOverlayClip(shape, topPx, bottomPx)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.echoSharedBounds(
    key: String,
    clipShape: Shape = MaterialTheme.shapes.largeIncreased
): Modifier {
    if (LocalReduceMotion.current) return this
    if (!NetworkPolicy.canLoadBodyImages()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalSharedVisibilityScope.current ?: return this
    val overlayClip = rememberEchoOverlayClip(clipShape)
    return with(sharedScope) {
        this@echoSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope,
            clipInOverlayDuringTransition = overlayClip
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.echoSharedElement(
    key: String,
    clipShape: Shape = MaterialTheme.shapes.medium
): Modifier {
    if (LocalReduceMotion.current) return this
    if (!NetworkPolicy.canLoadBodyImages()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalSharedVisibilityScope.current ?: return this
    val overlayClip = rememberEchoOverlayClip(clipShape)
    return with(sharedScope) {
        this@echoSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope,
            clipInOverlayDuringTransition = overlayClip
        )
    }
}
