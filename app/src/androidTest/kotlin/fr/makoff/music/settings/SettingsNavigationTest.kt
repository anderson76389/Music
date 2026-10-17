package fr.makoff.music.settings

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import fr.makoff.music.MainActivity
import fr.makoff.music.R
import fr.makoff.music.constants.CrossfadeEnabledKey
import fr.makoff.music.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun label(id: Int) = context.getString(id)

    private fun findText(id: Int): androidx.test.uiautomator.UiObject2 {
        val text = label(id)
        if (!device.wait(Until.hasObject(By.text(text)), 1500)) {
            UiScrollable(UiSelector().scrollable(true)).setMaxSearchSwipes(12)
                .scrollIntoView(UiSelector().text(text))
        }
        return requireNotNull(device.wait(Until.findObject(By.text(text)), 5000)) { "Missing control: $text" }
    }

    private fun open(id: Int) {
        findText(id).click()
        device.waitForIdle()
    }

    private fun assertNoRemovedEntries() {
        assertFalse(device.hasObject(By.text(label(R.string.integrations))))
        assertFalse(device.hasObject(By.text(label(R.string.about))))
        assertFalse(device.hasObject(By.textContains("Last.fm")))
        assertFalse(device.hasObject(By.textContains("Discord")))
    }

    @Test(timeout = 180000) fun settingsRoutesPersistenceAndOwnChangelog() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val account = device.wait(Until.findObject(By.desc(label(R.string.account))), 20000)
        assertNotNull("Account button", account)
        account!!.click()
        device.waitForIdle()
        assertNoRemovedEntries()
        open(R.string.settings)
        assertNoRemovedEntries()

        val screens = listOf(
            R.string.appearance to R.string.theme,
            R.string.player_and_audio to R.string.audio_quality,
            R.string.content to R.string.content_language,
            R.string.ai_lyrics_translation to R.string.ai_provider,
            R.string.privacy to R.string.pause_listen_history,
            R.string.storage to R.string.downloaded_songs,
            R.string.backup_restore to R.string.action_backup,
        )
        for ((screen, marker) in screens) {
            open(screen)
            findText(marker)
            println("SETTINGS_ROUTE_OK: ${label(screen)} -> ${label(marker)}")
            if (screen == R.string.player_and_audio) {
                val before = runBlocking { context.dataStore.data.first()[CrossfadeEnabledKey] ?: false }
                open(R.string.crossfade)
                val deadline = SystemClock.elapsedRealtime() + 5000
                while (runBlocking { context.dataStore.data.first()[CrossfadeEnabledKey] ?: false } == before &&
                    SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
                assertEquals(!before, runBlocking { context.dataStore.data.first()[CrossfadeEnabledKey] ?: false })
                open(R.string.crossfade)
                println("SETTINGS_PREFERENCE_OK: crossfade stored and restored")
                open(R.string.audio_quality)
                findText(R.string.audio_quality_high)
                device.pressBack()
            }
            device.pressBack()
            device.waitForIdle()
        }
        open(R.string.changelog)
        assertTrue("Changelog title", device.wait(Until.hasObject(By.text(label(R.string.changelog))), 5000))
        val deadline = SystemClock.elapsedRealtime() + 45000
        while (!device.hasObject(By.textStartsWith("test-")) &&
            !device.hasObject(By.text(label(R.string.makoff_changelog_error))) &&
            SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(250)
        assertTrue("Own published test releases loaded from GitHub", device.hasObject(By.textStartsWith("test-")))
        println("SETTINGS_CHANGELOG_OK: published test release visible in debug APK")
        assertNoRemovedEntries()
    }
}
