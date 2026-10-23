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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
): Modifier {
    if (!LocalLiquidGlassEnabled.current) return this
    val surface = MaterialTheme.colorScheme.surface
    if (!chrome) {
        // These list surfaces sit over a flat canvas; they need no source capture or refraction pass.
        val highlight = remember(surface) {
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.08f)))
        }
        return clip(shape).background(surface.copy(alpha = 0.78f)).border(0.5.dp, highlight, shape)
    }
    val state = LocalChromeGlass.current ?: return background(surface, shape)
    val style = remember(shape, surface) {
        GlassStyle.regular.then {
            shape(shape)
            backgroundColor(surface)
            tint(surface.copy(alpha = 0.62f))
        }
    }
    return clip(shape).hazeGlass(input = HazeInput.Sources(state), style = style)
}
