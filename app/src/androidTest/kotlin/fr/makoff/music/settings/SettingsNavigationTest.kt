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
import fr.makoff.music.constants.*
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
        assertFalse(device.hasObject(By.textContains("AI lyrics translation")))
        assertFalse(device.hasObject(By.textContains("Listen Together")))
    }

    private fun awaitPreference(label: String, condition: suspend () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!runBlocking { condition() }) {
            check(SystemClock.elapsedRealtime() < deadline) { "Preference did not change: $label" }
            SystemClock.sleep(50)
        }
    }

    private fun toggleAndRestore(id: Int, key: Preferences.Key<Boolean>, default: Boolean, effect: (() -> Boolean)? = null, clicker: (Int) -> Unit = { open(it) }) {
        val original = runBlocking { context.dataStore.data.first()[key] }
        val before = original ?: default
        try {
            clicker(id)
            awaitPreference(label(id)) { context.dataStore.data.first()[key] == !before }
            effect?.let { assertEquals("Consumer follows ${key.name}", !before, it()) }
            clicker(id)
            awaitPreference(label(id) + " restored") { context.dataStore.data.first()[key] == before }
            effect?.let { assertEquals("Consumer restored ${key.name}", before, it()) }
            println("SETTINGS_TOGGLE_OK: ${label(id)} (${key.name}), stored and restored")
        } finally {
            runBlocking { context.dataStore.edit { prefs -> original?.let { prefs[key] = it } ?: prefs.remove(key) } }
        }
    }

    @Test(timeout = 360000) fun settingsRoutesPersistenceAndOwnChangelog() {
        assertEquals("Metrolist", context.applicationInfo.loadLabel(context.packageManager).toString())
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
            R.string.privacy to R.string.pause_listen_history,
            R.string.storage to R.string.downloaded_songs,
            R.string.backup_restore to R.string.action_backup,
        )
        for ((screen, marker) in screens) {
            open(screen)
            findText(marker)
            println("SETTINGS_ROUTE_OK: ${label(screen)} -> ${label(marker)}")
            when (screen) {
                R.string.player_and_audio -> {
                    listOf(
                        Triple(R.string.crossfade, CrossfadeEnabledKey, false),
                        Triple(R.string.skip_silence, SkipSilenceKey, false),
                        Triple(R.string.audio_normalization, AudioNormalizationKey, true),
                        Triple(R.string.seek_seconds_addup, SeekExtraSeconds, false),
                        Triple(R.string.persistent_queue, PersistentQueueKey, true),
                        Triple(R.string.auto_load_more, AutoLoadMoreKey, true),
                        Triple(R.string.auto_radio_queue, AutoRadioQueueKey, true),
                        Triple(R.string.autoplay, AutoplayKey, true),
                        Triple(R.string.disable_load_more_when_repeat_all, DisableLoadMoreWhenRepeatAllKey, false),
                        Triple(R.string.enable_similar_content, SimilarContent, true),
                        Triple(R.string.persistent_shuffle_title, PersistentShuffleAcrossQueuesKey, false),
                        Triple(R.string.remember_shuffle_and_repeat, RememberShuffleAndRepeatKey, true),
                        Triple(R.string.shuffle_playlist_first, ShufflePlaylistFirstKey, false),
                        Triple(R.string.prevent_duplicate_tracks_in_queue, PreventDuplicateTracksInQueueKey, false),
                        Triple(R.string.auto_skip_next_on_error, AutoSkipNextOnErrorKey, false),
                        Triple(R.string.stop_music_on_task_clear, StopMusicOnTaskClearKey, false),
                        Triple(R.string.pause_music_when_media_is_muted, PauseOnMute, false),
                        Triple(R.string.resume_on_bluetooth_connect, ResumeOnBluetoothConnectKey, false),
                        Triple(R.string.keep_screen_on_when_player_is_expanded, KeepScreenOn, false),
                    ).forEach { (id, key, default) -> toggleAndRestore(id, key, default) }
                    val originalQuality = runBlocking { context.dataStore.data.first()[AudioQualityKey] }
                    try {
                        for ((label, value) in listOf(R.string.audio_quality_low to AudioQuality.LOW,
                            R.string.audio_quality_auto to AudioQuality.AUTO, R.string.audio_quality_high to AudioQuality.HIGH)) {
                            open(R.string.audio_quality)
                            open(label)
                            awaitPreference("quality ${value.name}") { context.dataStore.data.first()[AudioQualityKey] == value.name }
                        }
                    } finally {
                        runBlocking { context.dataStore.edit { prefs ->
                            originalQuality?.let { prefs[AudioQualityKey] = it } ?: prefs.remove(AudioQualityKey)
                        } }
                    }
                    println("SETTINGS_ENUM_OK: LOW/AUTO/HIGH stored, original restored")
                }
                R.string.content -> {
                    toggleAndRestore(R.string.enable_lyricsplus, EnableLyricsPlus, true,
                        effect = { fr.makoff.music.lyrics.LyricsPlusProvider.isEnabled(context) },
                        clicker = { id ->
                            val title = findText(id)
                            val switch = device.findObjects(By.checkable(true)).minByOrNull {
                                kotlin.math.abs(it.visibleBounds.centerY() - title.visibleBounds.centerY())
                            }
                            assertNotNull("LyricsPlus switch", switch)
                            assertTrue("Switch belongs to provider row", kotlin.math.abs(switch!!.visibleBounds.centerY() - title.visibleBounds.centerY()) < 150)
                            switch.click()
                            device.waitForIdle()
                        })
                    assertEquals(runBlocking { context.dataStore.data.first()[EnableLyricsPlus] ?: true },
                        fr.makoff.music.lyrics.LyricsPlusProvider.isEnabled(context))
                    listOf(
                    Triple(R.string.hide_explicit, HideExplicitKey, false),
                    Triple(R.string.hide_video_songs, HideVideoSongsKey, false),
                    Triple(R.string.hide_youtube_shorts, HideYoutubeShortsKey, false),
                    ).forEach { (id, key, default) -> toggleAndRestore(id, key, default) }
                }
                R.string.privacy -> {
                    toggleAndRestore(R.string.pause_listen_history, PauseListenHistoryKey, false)
                    toggleAndRestore(R.string.pause_search_history, PauseSearchHistoryKey, false)
                }
                R.string.storage -> toggleAndRestore(R.string.enable_song_cache, EnableSongCacheKey, true)
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
