package fr.makoff.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import fr.makoff.music.constants.*
import fr.makoff.music.utils.rememberPreference

internal fun boundedGlassValue(value: Float, fallback: Float, min: Float, max: Float): Float =
    if (value.isFinite()) value.coerceIn(min, max) else fallback

data class GlassStyle(
    val enabled: Boolean = false,
    val hue: Float = 275f,
    val intensity: Float = 0.6f,
    val opacity: Float = 0.72f,
    val dark: Boolean = false,
) {
    val tint: Color get() = Color.hsv(hue, 0.65f, 0.85f)
    val base: Color get() = lerp(if (dark) Color(0xFF101018) else Color(0xFFF7F5FC), tint, if (dark) 0.14f else 0.08f)
}

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }

@Composable
fun rememberGlassStyle(dark: Boolean): GlassStyle {
    val enabled by rememberPreference(LiquidGlassKey, false)
    val hue by rememberPreference(GlassHueKey, 275f)
    val intensity by rememberPreference(GlassIntensityKey, 0.6f)
    val opacity by rememberPreference(GlassOpacityKey, 0.72f)
    return GlassStyle(enabled, boundedGlassValue(hue, 275f, 0f, 360f),
        boundedGlassValue(intensity, 0.6f, 0f, 1f), boundedGlassValue(opacity, 0.72f, 0.45f, 0.95f), dark)
}

// Soft gradients are drawn behind content; text and controls are never blurred.
fun Modifier.liquidBackdrop(style: GlassStyle): Modifier = if (!style.enabled) this else drawWithCache {
    val first = Brush.radialGradient(listOf(style.tint.copy(alpha = style.intensity * 0.48f), Color.Transparent),
        center = Offset(size.width * 0.1f, size.height * 0.18f), radius = size.maxDimension * 0.8f)
    val second = Brush.radialGradient(listOf(Color.hsv((style.hue + 65f) % 360f, 0.55f, 0.9f).copy(alpha = style.intensity * 0.3f), Color.Transparent),
        center = Offset(size.width, size.height * 0.8f), radius = size.maxDimension * 0.65f)
    onDrawBehind { drawRect(style.base); drawRect(first); drawRect(second) }
}

fun Modifier.liquidPanel(style: GlassStyle): Modifier = if (!style.enabled) this else drawWithCache {
    val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.23f * style.intensity),
        Color.Transparent, style.tint.copy(alpha = 0.14f * style.intensity)))
    onDrawBehind {
        drawRect(style.base.copy(alpha = style.opacity))
        drawRect(sheen)
        drawLine(Color.White.copy(alpha = 0.45f * style.intensity), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
    }
}
