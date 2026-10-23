package fr.makoff.music.ui.theme

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

// Separate captures prevent a surface from sampling its own glass recursively.
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }
val LocalChromeGlass = staticCompositionLocalOf<HazeState?> { null }
private val LocalCanvasGlass = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun GlassCanvas(content: @Composable () -> Unit) {
    val enabled = LocalLiquidGlassEnabled.current
    val canvas = remember { HazeState() }
    val chrome = remember { HazeState() }
    val colors = MaterialTheme.colorScheme
    val backdrop = remember(colors) {
        Brush.linearGradient(listOf(colors.surface, colors.surfaceContainerLow, colors.surface))
    }
    CompositionLocalProvider(LocalCanvasGlass provides canvas, LocalChromeGlass provides chrome) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (enabled) Modifier.hazeSource(canvas).background(backdrop) else Modifier))
            content()
        }
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.liquidGlass(
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    chrome: Boolean = false,
): Modifier {
    if (!LocalLiquidGlassEnabled.current) return this
    val state = (if (chrome) LocalChromeGlass.current else LocalCanvasGlass.current)
        ?: return background(MaterialTheme.colorScheme.surface, shape)
    val surface = MaterialTheme.colorScheme.surface
    val style = remember(shape, surface) {
        GlassStyle.regular.then {
            shape(shape)
            backgroundColor(surface)
            tint(surface.copy(alpha = 0.62f))
        }
    }
    return clip(shape).hazeGlass(input = HazeInput.Sources(state), style = style)
}
