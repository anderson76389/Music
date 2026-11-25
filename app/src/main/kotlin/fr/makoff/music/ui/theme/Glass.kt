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
// Only an actual, known backdrop color may override the theme's neutral frost.
val LocalGlassBackdropTint = staticCompositionLocalOf<Color?> { null }

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
        val card = surface
        val panel = clip(shape).background(card.copy(alpha = 0.96f))
        return if (showBorder) panel.border(0.5.dp, highlight, shape) else panel
    }
    val backdropTint = LocalGlassBackdropTint.current ?: surface
    val state = LocalChromeGlass.current ?: return clip(shape).background(backdropTint)
    val dark = backdropTint.luminance() < 0.5f
    val flat = shape == RoundedCornerShape(0.dp)
    val style = remember(shape, backdropTint, dark, flat, showBorder) {
        GlassStyle.regular.then {
            shape(shape)
            backgroundColor(backdropTint)
            // Keep color from the captured background rather than a fixed grey/purple wash.
            tint(backdropTint.copy(alpha = if (dark) 0.42f else 0.44f))
            optics(blurRadius = 48.dp, refractionStrength = if (flat) 0f else if (showBorder) 0.65f else 0.30f,
                refractionDisplacement = if (flat) 0.dp else if (showBorder) 12.dp else 4.dp,
                refractionHeightFraction = if (showBorder) 0.28f else 0.12f,
                depth = if (showBorder) 0.45f else 0.40f)
            specularIntensity(if (!showBorder) 0f else if (dark) 0.52f else 0.72f)
            edgeSoftness(1.dp)
            whitePoint(if (dark) -0.04f else 0.06f)
            contrast(0.04f)
            chromaticAberrationStrength(0.01f)
        }
    }
    // Keep the moving blurred backdrop visible, but frost it enough to read
    // the controls even over bright album covers.
    val legibilityTint = backdropTint.copy(alpha = if (dark) 0.45f else 0.48f)
    if (flat) return clip(shape).hazeGlass(input = HazeInput.Sources(state), style = style).background(legibilityTint)
    val rim = remember(dark) {
        Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) 0.45f else 0.85f),
            Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = if (dark) 0.28f else 0.6f)))
    }
    // A thin white optical highlight replaces the thick grey rim on compact chrome.
    // It is drawn once at the outer edge, never as an inner capsule or dark shadow.
    val softRim = remember(dark) {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) 0.30f else 0.66f),
                Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = if (dark) 0.14f else 0.32f))
        )
    }
    val glass = (if (showBorder) shadow(8.dp, shape, clip = false,
        ambientColor = Color.Black.copy(alpha = 0.12f),
        spotColor = Color.Black.copy(alpha = 0.16f)) else this)
        .clip(shape).hazeGlass(input = HazeInput.Sources(state), style = style)
    val panel = glass.background(if (readable) backdropTint.copy(alpha = 0.96f) else legibilityTint)
    return if (showBorder) panel.border(0.8.dp, rim, shape) else panel.border(0.8.dp, softRim, shape)
}
