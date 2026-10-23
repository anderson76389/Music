/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package fr.makoff.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette

object PlayerColorExtractor {
    fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int,
        glass: Boolean = false,
    ): List<Color> {
        val primaryColor = Color(palette.rankedColors(1, fallbackColor).first())
        if (glass) {
            fun readable(rgb: Int): Color {
                var color = Color(rgb)
                // Preserve the artwork hue while keeping white player text legible.
                while (color.luminance() > 0.18f) color = color.copy(
                    red = color.red * 0.9f, green = color.green * 0.9f, blue = color.blue * 0.9f)
                return color
            }
            val dominant = palette.dominantSwatch?.rgb ?: fallbackColor
            return listOf(readable(palette.vibrantSwatch?.rgb ?: dominant), readable(dominant),
                readable(palette.darkVibrantSwatch?.rgb ?: dominant))
        }
        return listOf(
            primaryColor,
            primaryColor.copy(
                red = primaryColor.red * 0.6f,
                green = primaryColor.green * 0.6f,
                blue = primaryColor.blue * 0.6f,
            ),
            Color.Black,
        )
    }
}
