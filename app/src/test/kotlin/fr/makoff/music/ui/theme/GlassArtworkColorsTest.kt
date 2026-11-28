package fr.makoff.music.ui.theme

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GlassArtworkColorsTest {
    @Test fun queueBackgroundAndAccentsFollowCoverWithoutLosingContrast() {
        val blueArtwork = listOf(Color(0xFF1551BB), Color(0xFF173783), Color(0xFF0A1833))
        val redArtwork = listOf(Color(0xFFB53137), Color(0xFF7D2229), Color(0xFF290B10))
        val blueSurface = PlayerColorExtractor.glassQueueSurface(blueArtwork, Color.Black)
        val redSurface = PlayerColorExtractor.glassQueueSurface(redArtwork, Color.Black)
        val blueAccent = PlayerColorExtractor.glassQueueAccent(blueArtwork, Color.White)
        val redAccent = PlayerColorExtractor.glassQueueAccent(redArtwork, Color.White)
        assertNotEquals("Queue follows blue or red cover instead of a static panel", blueSurface, redSurface)
        assertNotEquals("Queue controls change accent with artwork", blueAccent, redAccent)
        for (gray in listOf(Color.White, Color.Gray, Color.Black)) {
            val surface = PlayerColorExtractor.glassQueueSurface(listOf(gray), Color.Black)
            assertTrue((Color(0xFFE4E4E7).luminance() + 0.05f) / (surface.luminance() + 0.05f) >= 4.5f)
        }
        for (surface in listOf(blueSurface, redSurface)) {
            assertTrue("White queue text meets contrast", 1.05f / (surface.luminance() + 0.05f) >= 4.5f)
        }
        for (accent in listOf(blueAccent, redAccent,
            PlayerColorExtractor.glassQueueAccent(listOf(Color(0xFFFFFF00)), Color.Black))) {
            assertTrue("White icons on cover-accent buttons meet contrast", 1.05f / (accent.luminance() + 0.05f) >= 4.5f)
        }
        assertEquals(
            Color(0xFF242426).let { base ->
                Color(base.red * 0.64f, base.green * 0.64f, base.blue * 0.64f)
            },
            PlayerColorExtractor.glassQueueSurface(emptyList(), Color(0xFF242426)),
        )
    }

    @Test fun artworkBackgroundKeepsWhiteTextReadableAndLegacyModeUnchanged() {
        for (rgb in listOf(0xFFFFFFFF, 0xFFFFFF00, 0xFF00FF00, 0xFFD37B35, 0xFF12657F)) {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(rgb.toInt())
            val palette = Palette.from(bitmap).generate()
            val glass = PlayerColorExtractor.extractGradientColors(palette, rgb.toInt(), glass = true)
            assertEquals(3, glass.size)
            assertTrue("White-text contrast on artwork $rgb", glass.all { 1.05f / (it.luminance() + 0.05f) >= 4.5f })
            assertNotEquals("Glass retains artwork color at the bottom", Color.Black, glass.last())
            assertEquals(Color.Black, PlayerColorExtractor.extractGradientColors(palette, rgb.toInt()).last())
            bitmap.recycle()
        }
    }
}
