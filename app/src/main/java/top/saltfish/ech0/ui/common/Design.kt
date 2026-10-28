@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package top.saltfish.ech0.ui.common

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import kotlin.math.abs

/** 统一水平边距：内容与页头都对齐这一条线（MD3 8dp 体系） */
val ContentPadding = 16.dp

// ---------- M3 强调型缓动曲线（页面/转场用 duration+easing；组件用 MotionScheme 弹簧） ----------

object M3Easing {
    val Emphasized = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

// ---------- Material 3 Expressive 主题 ----------
// 品牌种子蓝（承接旧版 accent），Android 12+ 优先动态取色（Material You 规范），低版本回落品牌色

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF00174A),
    inversePrimary = Color(0xFFAEC6FF),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF715573),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD8F8),
    onTertiaryContainer = Color(0xFF29132D),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceDim = Color(0xFFD8DAE0),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF5),
    surfaceContainerHigh = Color(0xFFE6E8EF),
    surfaceContainerHighest = Color(0xFFE1E2E9),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0F0F7),
    surfaceTint = Color(0xFF0B57D0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF002B6F),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    inversePrimary = Color(0xFF0B57D0),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDFBBDF),
    onTertiary = Color(0xFF3F2842),
    tertiaryContainer = Color(0xFF573E59),
    onTertiaryContainer = Color(0xFFFAD8F8),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF37393E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    surfaceTint = Color(0xFFA6C8FF)
)

private fun hsl(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val light = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * light - 1f)) * sat
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = light - c / 2f
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, 1f)
}

private fun seedHueSat(seed: Color): Pair<Float, Float> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    return hsv[0] to hsv[1].coerceIn(0.30f, 0.85f)
}

/** 自定义色的浅色方案：以种子色的色相生成一套连贯的 tonal 角色 */
private fun seedLightScheme(seed: Color): ColorScheme {
    val (h, s) = seedHueSat(seed)
    val primary = hsl(h, s, 0.40f)
    val tertiaryHue = h + 62f
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = hsl(h, s * 0.45f, 0.90f),
        onPrimaryContainer = hsl(h, s * 0.55f, 0.15f),
        inversePrimary = hsl(h, s * 0.55f, 0.80f),
        secondary = hsl(h, s * 0.28f, 0.40f),
        onSecondary = Color.White,
        secondaryContainer = hsl(h, s * 0.24f, 0.91f),
        onSecondaryContainer = hsl(h, s * 0.40f, 0.16f),
        tertiary = hsl(tertiaryHue, s * 0.38f, 0.42f),
        onTertiary = Color.White,
        tertiaryContainer = hsl(tertiaryHue, s * 0.30f, 0.92f),
        onTertiaryContainer = hsl(tertiaryHue, s * 0.35f, 0.18f),
        background = hsl(h, 0.10f, 0.985f),
        onBackground = hsl(h, 0.14f, 0.10f),
        surface = hsl(h, 0.10f, 0.985f),
        onSurface = hsl(h, 0.14f, 0.10f),
        surfaceVariant = hsl(h, 0.12f, 0.90f),
        onSurfaceVariant = hsl(h, 0.10f, 0.30f),
        surfaceTint = primary,
        surfaceDim = hsl(h, 0.10f, 0.86f),
        surfaceBright = hsl(h, 0.08f, 0.99f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hsl(h, 0.10f, 0.965f),
        surfaceContainer = hsl(h, 0.10f, 0.94f),
        surfaceContainerHigh = hsl(h, 0.09f, 0.915f),
        surfaceContainerHighest = hsl(h, 0.08f, 0.89f),
        outline = hsl(h, 0.08f, 0.50f),
        outlineVariant = hsl(h, 0.08f, 0.80f),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        inverseSurface = Color(0xFF2E3036),
        inverseOnSurface = Color(0xFFF0F0F7),
        scrim = Color.Black
    )
}

/** 自定义色的深色方案 */
private fun seedDarkScheme(seed: Color): ColorScheme {
    val (h, s) = seedHueSat(seed)
    val primary = hsl(h, s * 0.62f, 0.80f)
    val tertiaryHue = h + 62f
    return darkColorScheme(
        primary = primary,
        onPrimary = hsl(h, s * 0.70f, 0.16f),
        primaryContainer = hsl(h, s * 0.42f, 0.30f),
        onPrimaryContainer = hsl(h, s * 0.45f, 0.92f),
        inversePrimary = hsl(h, s, 0.40f),
        secondary = hsl(h, s * 0.20f, 0.80f),
        onSecondary = hsl(h, s * 0.30f, 0.18f),
        secondaryContainer = hsl(h, s * 0.22f, 0.28f),
        onSecondaryContainer = hsl(h, s * 0.35f, 0.90f),
        tertiary = hsl(tertiaryHue, s * 0.35f, 0.80f),
        onTertiary = hsl(tertiaryHue, s * 0.40f, 0.16f),
        tertiaryContainer = hsl(tertiaryHue, s * 0.28f, 0.28f),
        onTertiaryContainer = hsl(tertiaryHue, s * 0.30f, 0.92f),
        background = hsl(h, 0.12f, 0.06f),
        onBackground = hsl(h, 0.08f, 0.92f),
        surface = hsl(h, 0.12f, 0.06f),
        onSurface = hsl(h, 0.08f, 0.92f),
        surfaceVariant = hsl(h, 0.10f, 0.25f),
        onSurfaceVariant = hsl(h, 0.08f, 0.80f),
        surfaceTint = primary,
        surfaceDim = hsl(h, 0.12f, 0.06f),
        surfaceBright = hsl(h, 0.10f, 0.22f),
        surfaceContainerLowest = hsl(h, 0.12f, 0.03f),
        surfaceContainerLow = hsl(h, 0.11f, 0.10f),
        surfaceContainer = hsl(h, 0.11f, 0.13f),
        surfaceContainerHigh = hsl(h, 0.10f, 0.16f),
        surfaceContainerHighest = hsl(h, 0.09f, 0.20f),
        outline = hsl(h, 0.08f, 0.60f),
        outlineVariant = hsl(h, 0.08f, 0.30f),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = Color(0xFFE2E2E9),
        inverseOnSurface = Color(0xFF2E3036),
        scrim = Color.Black
    )
}

@Composable
fun Ech0Theme(
    darkTheme: Boolean,
    dynamicColor: Boolean = true,
    accentSeed: Long = 0xFF0B57D0,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> seedDarkScheme(Color(accentSeed))
        else -> seedLightScheme(Color(accentSeed))
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

/** 主题背景画笔；设置页预览与根背景共用，保证所见即所得 */
@Composable
fun appBackgroundBrush(style: String): Brush {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return when (style) {
        "paper" -> if (dark) {
            Brush.verticalGradient(listOf(Color(0xFF201A13), Color(0xFF121318)))
        } else {
            Brush.verticalGradient(
                listOf(Color(0xFFF8EFDF), Color(0xFFF4E4C9), scheme.surface)
            )
        }
        "mist" -> Brush.verticalGradient(
            listOf(
                scheme.primaryContainer.copy(alpha = if (dark) 0.42f else 0.62f),
                scheme.surface,
                scheme.surface
            )
        )
        "dusk" -> Brush.verticalGradient(
            listOf(
                scheme.tertiaryContainer.copy(alpha = if (dark) 0.36f else 0.55f),
                scheme.surface,
                scheme.surface
            )
        )
        else -> SolidColor(scheme.surface)
    }
}

@Composable
fun AppBackground(style: String, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(appBackgroundBrush(style)),
        content = content
    )
}

// ---------- 统一覆盖页顶栏（返回 + 标题） ----------

@Composable
fun OverlayTopBar(title: String, onClose: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

// ---------- 统一列表行（M3 ListItem）：图标 + 标题 + 中列值 + trailing ----------

@Composable
fun SettingsListRow(
    icon: ImageVector?,
    title: String,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val row: @Composable () -> Unit = {
        ListItem(
            headlineContent = {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            },
            supportingContent = valueText?.let {
                { Text(it, style = MaterialTheme.typography.bodySmall) }
            },
            leadingContent = icon?.let {
                {
                    Icon(
                        it, contentDescription = null,
                        tint = iconTint ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingContent = {
                when {
                    trailing != null -> trailing()
                    chevron -> Icon(
                        Icons.Filled.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> {}
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
    if (onClick != null && enabled) {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            modifier = modifier.fillMaxWidth()
        ) { row() }
    } else {
        Box(modifier.fillMaxWidth()) { row() }
    }
}

// ---------- 统一空态 / 首启引导卡 ----------

@Composable
fun EmptyStateCard(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: ImageVector? = null,
    actionText: String? = null,
    secondaryActionText: String? = null,
    onAction: (() -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onAction) { Text(actionText) }
                if (secondaryActionText != null && onSecondaryAction != null) {
                    TextButton(onClick = onSecondaryAction) { Text(secondaryActionText) }
                }
            }
        }
    }
}

// ---------- 统一错误卡（错误只留一处，可重试可跳转） ----------

@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ErrorOutline, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text("重试", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (onSettings != null) {
                TextButton(onClick = onSettings) {
                    Text("去设置", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

// ---------- 统一确认对话框 ----------

@Composable
fun ConfirmDialog(
    title: String,
    confirmText: String = "删除",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
