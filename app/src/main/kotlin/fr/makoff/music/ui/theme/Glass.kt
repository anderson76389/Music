package fr.makoff.music.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

// The capture contains screen content; glass chrome is drawn outside it to avoid feedback.
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }
val LocalChromeGlass = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun GlassCanvas(content: @Composable () -> Unit) {
    val enabled = LocalLiquidGlassEnabled.current
    val chrome = remember { HazeState() }
    val colors = MaterialTheme.colorScheme
    val backdrop = remember(colors) {
        Brush.linearGradient(listOf(colors.surface, colors.surfaceContainerLow, colors.surface))
    }
    CompositionLocalProvider(LocalChromeGlass provides chrome) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (enabled) Modifier.background(backdrop) else Modifier))
            content()
        }
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.liquidGlass(
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    chrome: Boolean = false,
    showBorder: Boolean = true,
    readable: Boolean = false,
): Modifier {
    if (!LocalLiquidGlassEnabled.current) return this
    val surface = MaterialTheme.colorScheme.surface
    if (!chrome) {
        // These list surfaces sit over a flat canvas; they need no source capture or refraction pass.
        val highlight = remember(surface) {
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.08f)))
        }
        val card = if (surface.luminance() < 0.5f) Color(0xFF242426) else Color.White
        return clip(shape).background(card.copy(alpha = 0.96f)).border(0.5.dp, highlight, shape)
    }
    val state = LocalChromeGlass.current ?: return background(surface, shape)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val flat = shape == RoundedCornerShape(0.dp)
    val style = remember(shape, surface, dark, flat, showBorder) {
        GlassStyle.regular.then {
            shape(shape)
            backgroundColor(surface)
            tint(surface.copy(alpha = if (dark) 0.48f else 0.42f))
            optics(blurRadius = 22.dp, refractionStrength = if (flat) 0f else 0.65f, refractionDisplacement = if (flat) 0.dp else 12.dp,
                refractionHeightFraction = 0.28f, depth = 0.45f)
            specularIntensity(if (!showBorder) 0f else if (dark) 0.52f else 0.72f)
            edgeSoftness(1.dp)
            whitePoint(if (dark) -0.18f else 0.12f)
            contrast(if (dark) 0.12f else 0.08f)
            chromaticAberrationStrength(0.025f)
        }
    }
    val legibilityTint = surface.copy(alpha = if (dark) 0.08f else 0.10f)
    if (flat) return clip(shape).hazeGlass(input = HazeInput.Sources(state), style = style).background(legibilityTint)
    val rim = remember(dark) {
        Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) 0.45f else 0.85f),
            Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = if (dark) 0.28f else 0.6f)))
    }
    val glass = shadow(8.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.12f),
        spotColor = Color.Black.copy(alpha = 0.16f))
        .clip(shape).hazeGlass(input = HazeInput.Sources(state), style = style)
    val panel = glass.background(if (readable) surface.copy(alpha = 0.96f) else legibilityTint)
    return if (showBorder) panel.border(0.8.dp, rim, shape) else panel
}
