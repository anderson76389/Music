package fr.makoff.music.playback

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.makoff.music.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** A landscape/foldable resize must not recreate the whole video session. */
@RunWith(AndroidJUnit4::class)
class PlayerRotationManifestInstrumentedTest {
    @Test
    fun videoRotationAndFoldableResizePreserveMainActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java), 0,
        )
        val expected = ActivityInfo.CONFIG_ORIENTATION or
            ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
            ActivityInfo.CONFIG_SCREEN_LAYOUT
        assertEquals(
            "Rotating a playing clip must not recreate MainActivity",
            expected,
            info.configChanges and expected,
        )
    }
}
