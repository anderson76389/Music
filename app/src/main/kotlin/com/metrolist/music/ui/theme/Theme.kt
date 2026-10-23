/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package fr.makoff.music.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import fr.makoff.music.constants.LiquidGlassEnabledKey
import fr.makoff.music.utils.rememberPreference
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    glassCanvas: Boolean = false,
    content: @Composable () -> Unit,
) {
    val liquidGlassEnabled by rememberPreference(LiquidGlassEnabledKey, false)
    val context = LocalContext.current
    // Determine if system dynamic colors should be used (Android S+ and default theme color)
    val useSystemDynamicColor = (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    // Select the appropriate color scheme generation method
    val baseColorScheme = if (useSystemDynamicColor) {
        // Use standard Material 3 dynamic color functions for system wallpaper colors
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // Use materialKolor only when a specific seed color is provided
        rememberDynamicColorScheme(
            seedColor = themeColor, // themeColor is guaranteed non-default here
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot // Keep existing style
        )
    }

    // Apply pureBlack modification if needed, similar to original logic
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, liquidGlassEnabled, themeColor) {
        if (!liquidGlassEnabled) {
            baseColorScheme.pureBlack(darkTheme && pureBlack)
        } else {
            val accent = if (themeColor == DefaultThemeColor) Color(0xFFFA2D55) else themeColor
            baseColorScheme.copy(
                primary = accent, onPrimary = Color.White,
                primaryContainer = accent.copy(alpha = 0.12f), onPrimaryContainer = accent,
                secondary = accent, onSecondary = Color.White,
                secondaryContainer = accent.copy(alpha = 0.1f), onSecondaryContainer = accent,
                onSurface = if (darkTheme) Color(0xFFF5F5F7) else Color(0xFF1C1C1E),
                onBackground = if (darkTheme) Color(0xFFF5F5F7) else Color(0xFF1C1C1E),
                onSurfaceVariant = if (darkTheme) Color(0xFFAEAEB2) else Color(0xFF6C6C70),
                outline = if (darkTheme) Color(0xFF636366) else Color(0xFF8E8E93),
                outlineVariant = if (darkTheme) Color(0xFF38383A) else Color(0xFFD1D1D6),
                surfaceVariant = if (darkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                surface = if (darkTheme) Color(0xFF151517) else Color(0xFFF7F7FA),
                background = if (darkTheme) Color(0xFF151517) else Color(0xFFF7F7FA),
                surfaceContainer = if (darkTheme) Color(0xFF242426) else Color(0xFFF0F0F4),
                surfaceContainerLow = if (darkTheme) Color(0xFF1C1C1E) else Color(0xFFEAEAF0),
            ).pureBlack(darkTheme && pureBlack)
        }
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (liquidGlassEnabled) GlassTypography else androidx.compose.material3.Typography(),
        shapes = if (!liquidGlassEnabled) Shapes() else Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = {
            CompositionLocalProvider(LocalLiquidGlassEnabled provides liquidGlassEnabled) {
                if (glassCanvas) GlassCanvas(content) else content()
            }
        },
    )
}

fun Bitmap.extractThemeColor(): Color = Color(
    Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .rankedColors(1, DefaultThemeColor.toArgb())
        .first()
)

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
