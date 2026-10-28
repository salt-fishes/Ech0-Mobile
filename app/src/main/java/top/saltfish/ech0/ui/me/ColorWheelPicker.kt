package top.saltfish.ech0.ui.me

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private fun hsvOf(color: Color): Pair<Float, Float> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[0] to hsv[1]
}

private fun hsvColor(h: Float, s: Float, v: Float = 1f): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

/**
 * 主题色色盘：角度 = 色相，半径 = 饱和度。
 * 亮度不固定参与种子——主题会按色相/饱和度生成 tonal 色板。
 */
@Composable
fun ColorWheelPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = remember { hsvOf(color) }
    var hue by remember { mutableFloatStateOf(initial.first) }
    var saturation by remember { mutableFloatStateOf(initial.second) }

    // 外部重置/换色时同步；拖动回传的色相饱和度基本一致，不会打架
    LaunchedEffect(color) {
        val (h, s) = hsvOf(color)
        if (abs(h - hue) > 0.5f || abs(s - saturation) > 0.01f) {
            hue = h
            saturation = s
        }
    }

    Canvas(
        modifier = modifier
            // 先固定尺寸，避免 aspectRatio 在 fillMaxWidth 的父容器里把色盘撑满整行
            .size(160.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // 消费按下/拖动事件，避免外层 verticalScroll 抢走手势
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    fun update(position: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = min(size.width, size.height) / 2f
                        val dx = position.x - center.x
                        val dy = position.y - center.y
                        val distance = hypot(dx, dy)
                        val sat = (distance / radius).coerceIn(0f, 1f)
                        var newHue = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        if (newHue < 0f) newHue += 360f
                        hue = newHue
                        saturation = sat
                        onColorChange(hsvColor(newHue, sat))
                    }
                    update(down.position)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            update(change.position)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val radius = size.minDimension / 2f
        val center = this.center
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                    Color.Blue, Color.Magenta, Color.Red
                )
            ),
            radius = radius,
            center = center
        )
        // 中心白 -> 边缘透明，形成饱和度渐变
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.16f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        val angle = Math.toRadians(hue.toDouble())
        val pointer = Offset(
            x = center.x + cos(angle).toFloat() * radius * saturation,
            y = center.y + sin(angle).toFloat() * radius * saturation
        )
        val selected = hsvColor(hue, saturation)
        drawCircle(color = Color.White, radius = 12.dp.toPx(), center = pointer)
        drawCircle(color = selected, radius = 9.dp.toPx(), center = pointer)
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = 12.dp.toPx(),
            center = pointer,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
