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
