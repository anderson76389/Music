package fr.makoff.music.settings

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.makoff.music.MainActivity
import fr.makoff.music.constants.LiquidGlassEnabledKey
import fr.makoff.music.ui.screens.settings.PaletteColors
import fr.makoff.music.ui.theme.DefaultThemeColor
import fr.makoff.music.ui.theme.LocalLiquidGlassEnabled
import fr.makoff.music.ui.theme.MusicTheme
import fr.makoff.music.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ThemePaletteInstrumentedTest {
    @Test(timeout = 120000) fun everyPaletteKeepsReadableColorsInBothThemesAndAppearances() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val before = context.dataStore.data.first()
        var activity: MainActivity? = null
        fun contrast(a: Color, b: Color): Float {
            val first = a.luminance(); val second = b.luminance()
            return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
        }
        try {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (activity == null && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                        .filterIsInstance<MainActivity>().firstOrNull()
                }
                SystemClock.sleep(50)
            }
            assertNotNull(activity)
            val seeds = listOf(DefaultThemeColor, Color(0xFFFA2D55)) + PaletteColors.map { it.seedColor }.filter { it != Color.Transparent }
            for (glass in listOf(false, true)) {
                context.dataStore.edit { it[LiquidGlassEnabledKey] = glass }
                for (dark in listOf(false, true)) for (seed in seeds) {
                    val current = AtomicReference<Pair<Boolean, ColorScheme>?>()
                    instrumentation.runOnMainSync {
                        activity!!.setContent {
                            MusicTheme(darkTheme = dark, pureBlack = dark, themeColor = seed) {
                                val active = LocalLiquidGlassEnabled.current
                                val scheme = MaterialTheme.colorScheme
                                SideEffect { current.set(active to scheme) }
                            }
                        }
                    }
                    val ready = SystemClock.elapsedRealtime() + 5000
                    while (current.get()?.first != glass && SystemClock.elapsedRealtime() < ready) SystemClock.sleep(20)
                    val (enabled, scheme) = requireNotNull(current.get())
                    assertEquals(glass, enabled)
                    assertTrue("Text must remain readable for glass=$glass dark=$dark seed=$seed", contrast(scheme.onSurface, scheme.surface) >= 4.5f)
                    if (dark) assertEquals("OLED background remains black", Color.Black, scheme.surface)
                    if (glass && seed in listOf(DefaultThemeColor, Color(0xFFFA2D55))) {
                        assertEquals("Existing Apple pink is retained", Color(0xFFFA2D55), scheme.primary)
                        assertEquals(Color.White, scheme.onPrimary)
                    } else {
                        assertTrue("Custom accent labels must remain readable for glass=$glass dark=$dark seed=$seed",
                            contrast(scheme.primary, scheme.surface) >= 4.5f)
                        assertTrue("Filled button text must remain readable for glass=$glass dark=$dark seed=$seed",
                            contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
                    }
                }
            }
            println("THEME_PALETTES_OK: all palettes, regular/glass, light/dark, OLED and foreground contrasts; default glass pink retained")
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            context.dataStore.updateData { before }
        }
    }
}
