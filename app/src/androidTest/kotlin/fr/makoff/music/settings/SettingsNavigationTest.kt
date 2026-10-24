package fr.makoff.music.settings

import android.app.Activity
import android.content.Intent
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import fr.makoff.music.ui.screens.settings.DarkMode
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
        var title = findText(id)
        // A scrolled row may remain in the accessibility tree underneath the fixed app bar.
        val safeTop = (96 * context.resources.displayMetrics.density).toInt()
        repeat(3) {
            if (title.top < safeTop) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 10,
                    device.displayWidth / 2, device.displayHeight * 8 / 10, 40)
                device.waitForIdle()
                title = findText(id)
            }
        }
        assertTrue("Control is below the app bar: ${label(id)}", title.top >= safeTop)
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
        // Scrolling/recreation can replace accessibility nodes between lookup and action.
        var activated = false
        repeat(3) {
            if (!activated) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                activated = root != null && visit(root, null)
                if (!activated) SystemClock.sleep(300)
            }
        }
        if (!activated) captureFailure()
        assertTrue("Activate $text", activated)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500)
        device.waitForIdle()
    }

    private fun assertNoRemovedEntries() {
        assertFalse(device.hasObject(By.text(label(R.string.integrations))))
        assertFalse(device.hasObject(By.text(label(R.string.about))))
        assertFalse(device.hasObject(By.text(label(R.string.changelog))))
        assertFalse(device.hasObject(By.text(label(R.string.disable_screenshot))))
        for (id in listOf(R.string.privacy, R.string.sleep_timer, R.string.alarm, R.string.lyrics, R.string.system_equalizer)) {
            assertFalse("Removed entry remains: ${label(id)}", device.hasObject(By.text(label(id))))
        }
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

    private fun resumedMainActivity(): Activity? {
        var activity: Activity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .firstOrNull { it is MainActivity }
        }
        return activity
    }

    private fun awaitThemeRestart(previous: Activity?) {
        assertNotNull("Activity before theme change", previous)
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline) {
            val current = resumedMainActivity()
            if (current != null && current !== previous) {
                device.waitForIdle()
                findText(R.string.liquid_glass_theme)
                println("GLASS_RESTART_OK: activity recreated and appearance route restored")
                return
            }
            SystemClock.sleep(100)
        }
        error("Theme change did not recreate the activity")
    }

    private fun toggleAndRestore(id: Int, key: Preferences.Key<Boolean>, default: Boolean, effect: (() -> Boolean)? = null, clicker: (Int) -> Unit = { open(it) }) {
        val original = runBlocking { context.dataStore.data.first()[key] }
        val before = original ?: default
        try {
            val initialActivity = if (key == LiquidGlassEnabledKey) resumedMainActivity() else null
            clicker(id)
            awaitPreference(label(id)) { context.dataStore.data.first()[key] == !before }
            if (key == LiquidGlassEnabledKey) awaitThemeRestart(initialActivity)
            effect?.let { assertEquals("Consumer follows ${key.name}", !before, it()) }
            if (key == LiquidGlassEnabledKey && !before) {
                device.executeShellCommand("screencap -p /data/local/tmp/glass-settings-enabled.png")
                device.pressBack()
                device.pressBack()
                val account = device.wait(Until.findObject(By.desc(label(R.string.account))), 10000)
                assertNotNull("Home remains usable with glass enabled", account)
                device.waitForIdle()
                device.executeShellCommand("screencap -p /data/local/tmp/glass-home-enabled.png")
                account!!.click()
                open(R.string.settings)
                open(R.string.appearance)
            }
            val themedActivity = if (key == LiquidGlassEnabledKey) resumedMainActivity() else null
            clicker(id)
            awaitPreference(label(id) + " restored") { context.dataStore.data.first()[key] == before }
            if (key == LiquidGlassEnabledKey) awaitThemeRestart(themedActivity)
            effect?.let { assertEquals("Consumer restored ${key.name}", before, it()) }
            println("SETTINGS_TOGGLE_OK: ${label(id)} (${key.name}), stored and restored")
        } finally {
            runBlocking { context.dataStore.edit { prefs -> original?.let { prefs[key] = it } ?: prefs.remove(key) } }
        }
    }

    @Test(timeout = 90000) fun glassReferenceInLightAndDark() {
        val original = runBlocking { context.dataStore.data.first() }
        try {
            runBlocking { context.dataStore.edit { it[LiquidGlassEnabledKey] = true; it[DarkModeKey] = DarkMode.OFF.name } }
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            val account = device.wait(Until.findObject(By.desc(label(R.string.account))), 15000)
            assertNotNull("Glass home remains accessible", account)
            device.waitForIdle()
            device.executeShellCommand("screencap -p /data/local/tmp/glass-home-light.png")
            account!!.click()
            open(R.string.settings)
            open(R.string.appearance)
            device.executeShellCommand("screencap -p /data/local/tmp/glass-settings-light.png")
            runBlocking { context.dataStore.edit { it[DarkModeKey] = DarkMode.ON.name } }
            SystemClock.sleep(1200)
            device.waitForIdle()
            findText(R.string.liquid_glass_theme)
            device.executeShellCommand("screencap -p /data/local/tmp/glass-settings-dark.png")
            device.pressBack()
            device.pressBack()
            assertTrue("Dark glass home remains accessible", device.wait(Until.hasObject(By.desc(label(R.string.account))), 10000))
            device.executeShellCommand("screencap -p /data/local/tmp/glass-home-dark.png")
            println("GLASS_APPEARANCES_OK: light and dark glass home/settings captured")
        } finally {
            runBlocking { context.dataStore.edit { prefs ->
                original[LiquidGlassEnabledKey]?.let { prefs[LiquidGlassEnabledKey] = it } ?: prefs.remove(LiquidGlassEnabledKey)
                original[DarkModeKey]?.let { prefs[DarkModeKey] = it } ?: prefs.remove(DarkModeKey)
            } }
            val activity = resumedMainActivity()
            instrumentation.runOnMainSync { activity?.finish() }
        }
    }

    @Test(timeout = 45000) fun updateNotificationOpensIntegratedInstaller() {
        val url = "https://github.com/anderson76389/Music/releases/download/test-notification-no-apk/Metrolist.apk"
        val release = fr.makoff.music.utils.ReleaseInfo("test-notification", "test-notification", "", "",
            listOf(fr.makoff.music.utils.ReleaseAsset("Metrolist.apk", url, 100, "universal", "gms")))
        var opened: Activity? = null
        try {
            fr.makoff.music.utils.UpdateNotifications.show(context, release)
            device.openNotification()
            val notification = device.wait(Until.findObject(By.text(label(R.string.update_available_title))), 10000)
            assertNotNull("Update notification is visible", notification)
            notification!!.click()
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (SystemClock.elapsedRealtime() < deadline && opened == null) {
                instrumentation.runOnMainSync {
                    opened = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .firstOrNull { it is fr.makoff.music.ui.screens.settings.ApkUpdateActivity }
                }
                if (opened == null) SystemClock.sleep(100)
            }
            assertNotNull("Notification opens integrated APK update activity", opened)
            assertEquals(url, opened!!.intent.getStringExtra("apk-url"))
            println("UPDATE_NOTIFICATION_OK: tap opens integrated installer with own APK URL")
        } finally {
            instrumentation.runOnMainSync { opened?.finish() }
            fr.makoff.music.utils.UpdateNotifications.cancel(context)
            device.pressBack()
        }
    }

    @Test(timeout = 30000) fun legacyScreenshotPreferenceNoLongerBlocksCaptures() {
        val key = androidx.datastore.preferences.core.booleanPreferencesKey("disableScreenshot")
        val original = runBlocking { context.dataStore.data.first()[key] }
        try {
            runBlocking { context.dataStore.edit { it[key] = true } }
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (resumedMainActivity() == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            val activity = requireNotNull(resumedMainActivity())
            instrumentation.runOnMainSync {
                assertEquals(0, activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            println("SCREENSHOTS_OK: legacy true preference does not secure the window")
        } finally {
            runBlocking { context.dataStore.edit { prefs -> original?.let { prefs[key] = it } ?: prefs.remove(key) } }
            resumedMainActivity()?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    @Test(timeout = 30000) fun removedFeatureMigrationCancelsLegacyAlarmAndKeepsOtherSettings() = runBlocking {
        val done = androidx.datastore.preferences.core.booleanPreferencesKey("removedPlaybackFeaturesV1")
        val oldAlarm = androidx.datastore.preferences.core.stringPreferencesKey("alarmEntries")
        val oldTimer = androidx.datastore.preferences.core.booleanPreferencesKey("sleepTimerEnabled")
        val before = context.dataStore.data.first()
        val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("alarm_store", android.content.Context.MODE_PRIVATE)
        val oldRaw = prefs.getString("alarm_entries", null)
        val id = "migration-test-legacy-alarm"
        val code = id.hashCode() and Int.MAX_VALUE
        val intent = Intent(context, fr.makoff.music.playback.MusicService::class.java).setAction("fr.makoff.music.action.ALARM_TRIGGER")
        val pending = android.app.PendingIntent.getForegroundService(context, code, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        try {
            val raw = "[{\"id\":\"$id\",\"enabled\":true}]"
            prefs.edit().putString("alarm_entries", raw).commit()
            context.dataStore.edit {
                it[done] = false; it[oldAlarm] = raw; it[oldTimer] = true
            }
            context.getSystemService(android.app.AlarmManager::class.java).set(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 60000, pending)
            fr.makoff.music.utils.RemovedPlaybackFeatures.clean(context)
            assertNull(android.app.PendingIntent.getForegroundService(context, code, intent,
                android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE))
            val after = context.dataStore.data.first()
            assertNull(after[oldAlarm]); assertNull(after[oldTimer])
            assertEquals(before[PauseListenHistoryKey], after[PauseListenHistoryKey])
            assertEquals(before[AudioQualityKey], after[AudioQualityKey])
            assertEquals(before[ExternalDownloadFormatKey], after[ExternalDownloadFormatKey])
            println("REMOVED_FEATURES_OK: pending alarm cancelled; playback/history/download settings preserved")
        } finally {
            pending.cancel()
            prefs.edit().apply { if (oldRaw == null) remove("alarm_entries") else putString("alarm_entries", oldRaw) }.commit()
            context.dataStore.edit { it[done] = true; it.remove(oldAlarm); it.remove(oldTimer) }
        }
    }

    @Test(timeout = 540000) fun settingsRoutesAndPlayerPreferences() {
        assertEquals("Apple Music", context.applicationInfo.loadLabel(context.packageManager).toString())
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val account = device.wait(Until.findObject(By.desc(label(R.string.account))), 20000)
        assertNotNull("Account button", account)
        val searches = device.findObjects(By.desc(label(R.string.search)))
        val headerSearch = searches.firstOrNull { it.visibleBounds.centerY() < device.displayHeight / 3 }
        assertNotNull("Search shortcut next to history", headerSearch)
        headerSearch!!.click()
        assertTrue("Search input opened", device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10000))
        device.waitForIdle()
        SystemClock.sleep(700)
        val searchBack = device.wait(Until.findObject(By.desc(label(R.string.dismiss))), 5000)
        assertNotNull("Search back button", searchBack)
        val backBounds = searchBack!!.visibleBounds
        device.click(backBounds.centerX(), backBounds.centerY())
        device.waitForIdle()
        // Refresh cached virtual nodes after Compose removes and restores the app bar.
        // Reapplying the same service info clears UiAutomation's cache on Android 11 too.
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo
        val homeAccount = device.wait(Until.findObject(By.desc(label(R.string.account))), 10000)
        if (homeAccount == null) captureFailure()
        assertNotNull("Return from search to home", homeAccount)
        homeAccount!!.click()
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
                    toggleAndRestore(R.string.crossfade, CrossfadeEnabledKey, false)
                    val crossfadeBefore = runBlocking { context.dataStore.data.first()[CrossfadeEnabledKey] }
                    try {
                        runBlocking { context.dataStore.edit { it[CrossfadeEnabledKey] = true } }
                        toggleAndRestore(R.string.crossfade_gapless, CrossfadeGaplessKey, true)
                        findText(R.string.crossfade_duration)
                    } finally {
                        runBlocking { context.dataStore.edit { prefs -> crossfadeBefore?.let { prefs[CrossfadeEnabledKey] = it } ?: prefs.remove(CrossfadeEnabledKey) } }
                    }
                    val silenceBefore = runBlocking { context.dataStore.data.first()[SkipSilenceKey] }
                    try {
                        runBlocking { context.dataStore.edit { it[SkipSilenceKey] = true } }
                        toggleAndRestore(R.string.skip_silence_instant, SkipSilenceInstantKey, false)
                    } finally {
                        runBlocking { context.dataStore.edit { prefs -> silenceBefore?.let { prefs[SkipSilenceKey] = it } ?: prefs.remove(SkipSilenceKey) } }
                    }
                    listOf(
                        Triple(R.string.audio_offload, AudioOffload, false),
                        Triple(R.string.varispeed, VarispeedKey, false),
                        Triple(R.string.audio_track_playback_params, AudioTrackPlaybackParamsKey, true),
                        Triple(R.string.google_cast, EnableGoogleCastKey, true),

                        Triple(R.string.auto_download_on_like, AutoDownloadOnLikeKey, false),
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
                    val normalizationBefore = runBlocking { context.dataStore.data.first()[AudioNormalizationKey] }
                    val loudnessBefore = runBlocking { context.dataStore.data.first()[LoudnessLevelKey] }
                    try {
                        runBlocking { context.dataStore.edit { it[AudioNormalizationKey] = true } }
                        for ((text, level) in listOf(
                            R.string.loudness_level_aggressive to LoudnessLevel.AGGRESSIVE,
                            R.string.loudness_level_loud to LoudnessLevel.LOUD,
                            R.string.loudness_level_balanced to LoudnessLevel.BALANCED,
                            R.string.loudness_level_quiet to LoudnessLevel.QUIET)) {
                            open(R.string.loudness_level)
                            open(text)
                            awaitPreference("loudness ${level.name}") { context.dataStore.data.first()[LoudnessLevelKey] == level.name }
                        }
                    } finally {
                        runBlocking { context.dataStore.edit { prefs ->
                            normalizationBefore?.let { prefs[AudioNormalizationKey] = it } ?: prefs.remove(AudioNormalizationKey)
                            loudnessBefore?.let { prefs[LoudnessLevelKey] = it } ?: prefs.remove(LoudnessLevelKey)
                        } }
                    }
                    println("SETTINGS_ENUM_OK: quality and all four normalization levels stored and restored")
                    toggleAndRestore(R.string.pause_listen_history, PauseListenHistoryKey, false)
                    toggleAndRestore(R.string.pause_search_history, PauseSearchHistoryKey, false)
                    open(R.string.clear_listen_history)
                    open(android.R.string.cancel)
                    open(R.string.clear_search_history)
                    open(android.R.string.cancel)
                    assertNoRemovedEntries()
                }
                R.string.content -> {
                    open(R.string.android_auto)
                    findText(R.string.android_auto_visible_sections)
                    device.pressBack()
                    device.waitForIdle()
                    println("SETTINGS_ANDROID_AUTO_OK: content route opens Android Auto")
                    listOf(
                    Triple(R.string.hide_explicit, HideExplicitKey, false),
                    Triple(R.string.hide_video_songs, HideVideoSongsKey, false),
                    Triple(R.string.hide_youtube_shorts, HideYoutubeShortsKey, false),
                    ).forEach { (id, key, default) -> toggleAndRestore(id, key, default) }
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
        assertNoRemovedEntries()
    }
}
