package fr.makoff.music.settings

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
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

    private data class VisibleControl(val text: String, val checkable: Boolean, val bounds: android.graphics.Rect)

    private fun visibleControls(): List<VisibleControl> {
        val result = mutableListOf<VisibleControl>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo) {
            try {
                if (!node.refresh()) return
                if (node.isVisibleToUser) {
                    val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
                    if (bounds.width() > 20 && bounds.height() > 8) {
                        result += VisibleControl(node.text?.toString().orEmpty(), node.isCheckable, bounds)
                    }
                }
                for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
            } finally { node.recycle() }
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        return result
    }

    private fun findText(id: Int): android.graphics.Rect {
        val text = label(id)
        fun current() = visibleControls().firstOrNull { it.text == text }?.bounds
        // Accessibility actions are queued on the app main thread. Give a newly
        // opened screen/dialog a chance to compose before injecting scroll gestures.
        val appearDeadline = SystemClock.elapsedRealtime() + 2000
        do {
            current()?.let { return it }
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < appearDeadline)
        val x = device.displayWidth / 2
        val top = device.displayHeight * 3 / 10
        val bottom = device.displayHeight * 8 / 10
        for ((forward, limit) in listOf(true to 30, false to 60)) {
            var previous = ""
            var unchanged = 0
            for (attempt in 0 until limit) {
                device.swipe(x, if (forward) bottom else top, x, if (forward) top else bottom, 55)
                device.waitForIdle()
                current()?.let { return it }
                val signature = visibleControls().joinToString { it.text + it.bounds.toShortString() }
                unchanged = if (signature == previous) unchanged + 1 else 0
                previous = signature
                if (unchanged >= 2) break
            }
        }
        captureFailure()
        error("Missing control: $text")
    }

    private fun captureFailure() {
        runCatching {
            device.executeShellCommand("screencap -p /data/local/tmp/settings-failure.png")
            val xml = java.io.ByteArrayOutputStream()
            device.dumpWindowHierarchy(xml)
            xml.toString("UTF-8").replace("><", ">\n<").lineSequence().forEach { println("SETTINGS_FAILURE_UI: $it") }
        }
    }

    private fun open(id: Int, switchOnly: Boolean = false) {
        val title = findText(id)
        val text = label(id)
        val switchBounds = if (switchOnly) {
            val control = visibleControls().filter { it.checkable }.minByOrNull {
                kotlin.math.abs(it.bounds.centerY() - title.centerY())
            }
            assertNotNull("Switch for $text", control)
            assertTrue("Switch belongs to $text row",
                kotlin.math.abs(control!!.bounds.centerY() - title.centerY()) < 150)
            control.bounds
        } else null
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo,
                  parentAction: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            try {
                if (!node.refresh()) return false
                val action = if (node.isClickable && node.isEnabled) node else parentAction
                val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
                if (switchOnly && node.isCheckable && node.isEnabled && node.isVisibleToUser && bounds == switchBounds) {
                    return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                }
                if (!switchOnly && node.isVisibleToUser && node.text?.toString() == text && action != null) {
                    // Invoke the actual UI callback without a fling consuming a physical tap.
                    return action.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                }
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    if (visit(child, action)) return true
                }
                return false
            } finally { node.recycle() }
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow
        assertTrue("Activate $text", root != null && visit(root, null))
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
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
            if (SystemClock.elapsedRealtime() >= deadline) {
                captureFailure()
                error("Preference did not change: $label")
            }
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
        device.waitForIdle()
        device.executeShellCommand("screencap -p /data/local/tmp/settings-glass.png")

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
            device.executeShellCommand("screencap -p /data/local/tmp/settings-glass-${screen}.png")
            println("SETTINGS_ROUTE_OK: ${label(screen)} -> ${label(marker)}")
            when (screen) {
                R.string.appearance -> {
                    toggleAndRestore(R.string.liquid_glass_theme, LiquidGlassEnabledKey, false)
                    println("GLASS_SWITCH_OK: enabled and disabled from settings, preference persisted")
                    open(R.string.theme_colors)
                    device.pressBack()
                    device.waitForIdle()
                }
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
                    open(R.string.lyrics_provider_selection)
                    toggleAndRestore(R.string.enable_lyricsplus, EnableLyricsPlus, true,
                        effect = { fr.makoff.music.lyrics.LyricsPlusProvider.isEnabled(context) },
                        clicker = { id -> open(id, switchOnly = true) })
                    assertEquals(runBlocking { context.dataStore.data.first()[EnableLyricsPlus] ?: true },
                        fr.makoff.music.lyrics.LyricsPlusProvider.isEnabled(context))
                    open(R.string.close)
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
                R.string.storage -> {
                    toggleAndRestore(R.string.enable_song_cache, EnableSongCacheKey, true)
                    for ((title, key) in listOf(R.string.download_format_internal to InternalDownloadFormatKey,
                        R.string.download_format_external to ExternalDownloadFormatKey)) {
                        val original = runBlocking { context.dataStore.data.first()[key] }
                        try {
                            for (format in fr.makoff.music.downloads.DownloadAudioFormat.entries) {
                                open(title)
                                open(format.label)
                                awaitPreference("download format ${format.name}") { context.dataStore.data.first()[key] == format.name }
                            }
                        } finally {
                            runBlocking { context.dataStore.edit { prefs ->
                                original?.let { prefs[key] = it } ?: prefs.remove(key)
                            } }
                        }
                    }
                    println("DOWNLOAD_FORMAT_SETTINGS_OK: internal and external BEST/M4A/FLAC persisted")
                }
            }
            device.pressBack()
            device.waitForIdle()
        }
        val latestTag = runBlocking {
            kotlinx.coroutines.withTimeout(45000) {
                fr.makoff.music.utils.Updater.getAllReleases().getOrThrow().first().tagName
            }
        }
        open(R.string.changelog)
        assertTrue("Changelog title", device.wait(Until.hasObject(By.text(label(R.string.changelog))), 5000))
        val deadline = SystemClock.elapsedRealtime() + 45000
        while (!visibleControls().any { it.text == latestTag } &&
            !device.hasObject(By.text(label(R.string.makoff_changelog_error))) &&
            SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(250)
        assertTrue("Latest release from our GitHub is visible", visibleControls().any { it.text == latestTag })
        println("SETTINGS_CHANGELOG_OK: latest repository release visible: $latestTag")
        assertNoRemovedEntries()
    }
}
