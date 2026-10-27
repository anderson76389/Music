package fr.makoff.music.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Player
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import fr.makoff.music.MainActivity
import fr.makoff.music.R
import fr.makoff.music.constants.*
import fr.makoff.music.db.entities.RelatedSongMap
import fr.makoff.music.extensions.toMediaItem
import fr.makoff.music.extensions.toggleRepeatMode
import fr.makoff.music.models.MediaMetadata
import fr.makoff.music.playback.queues.ListQueue
import fr.makoff.music.utils.dataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class OfflinePlayerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable?>()
        instrumentation.runOnMainSync {
            try { result.set(block()) } catch (error: Throwable) { failure.set(error) }
        }
        failure.get()?.let { throw it }
        return result.get()
    }

    private fun await(message: String, timeout: Long = 15000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { message }
            SystemClock.sleep(50)
        }
    }

    private fun freshControlBounds(label: String): android.graphics.Rect? {
        fun find(node: android.view.accessibility.AccessibilityNodeInfo): android.graphics.Rect? {
            try {
                if (!node.refresh()) return null
                if (node.isVisibleToUser && node.contentDescription?.toString()?.contains(label) == true) {
                    return android.graphics.Rect().also(node::getBoundsInScreen)
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let { child -> find(child)?.let { return it } }
                }
                return null
            } finally { node.recycle() }
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
    }

    private fun clickDescription(id: Int) {
        val label = context.getString(id)
        var bounds: android.graphics.Rect? = null
        await("Player control: $label", 10000) {
            bounds = freshControlBounds(label)
            bounds != null
        }
        val target = requireNotNull(bounds)
        assertTrue("UI click: $label", device.click(target.centerX(), target.centerY()))
    }

    private fun audioFixture(): ByteArray = android.util.Base64.decode(
        instrumentation.context.assets.open("audio/player-aac.m4a.base64").use { it.readBytes() },
        android.util.Base64.DEFAULT,
    )

    private fun cacheAudio(cache: androidx.media3.datasource.cache.Cache, id: String, bytes: ByteArray) {
        val hole = cache.startReadWrite(id, 0, bytes.size.toLong())
        try {
            val file = cache.startFile(id, 0, bytes.size.toLong())
            file.writeBytes(bytes)
            cache.commitFile(file, bytes.size.toLong())
        } finally { cache.releaseHoleSpan(hole) }
        val metadata = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(metadata, bytes.size.toLong())
        cache.applyContentMetadataMutations(id, metadata)
    }

    @Test(timeout = 210000)
    fun realCachedAudioPlayerControlsQueueAndQuality() = runBlocking {
        await("Offline test requires no active Android network", 20000) {
            context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork == null
        }
        val keys = listOf(AutoplayKey, AutoLoadMoreKey, AutoRadioQueueKey, PersistentQueueKey,
            CrossfadeEnabledKey, AudioOffload, SkipSilenceKey, SkipSilenceInstantKey, AutoDownloadOnLikeKey, LiquidGlassEnabledKey,
            AudioTrackPlaybackParamsKey, EnableGoogleCastKey, EnableSongCacheKey)
        val before = context.dataStore.data.first()
        context.dataStore.edit { prefs -> keys.forEach { prefs[it] = false }; prefs[LiquidGlassEnabledKey] = true; prefs[AudioTrackPlaybackParamsKey] = true; prefs[EnableSongCacheKey] = true; prefs[AudioQualityKey] = AudioQuality.HIGH.name }
        val future = CompletableFuture<MusicService.MusicBinder>()
        val binding = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder is MusicService.MusicBinder) future.complete(binder)
                else future.completeExceptionally(IllegalStateException("Unexpected service binder: $binder"))
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var connection: PlayerConnection? = null
        var service: MusicService? = null
        val artwork = java.io.File(context.cacheDir, "glass-validation-art.png")
        val bitmap = android.graphics.Bitmap.createBitmap(720, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 720f, 400f,
                intArrayOf(0xFF12657F.toInt(), 0xFFD37B35.toInt(), 0xFF653C25.toInt()), null,
                android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 720f, 400f, paint)
        artwork.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val tracks = (0..3).map { index ->
            MediaMetadata(UUID.randomUUID().toString().replace("-", "").take(11),
                "Validation lecteur ${'A' + index}", listOf(MediaMetadata.Artist("test-local-artist", "Audio de test")), 20,
                thumbnailUrl = android.net.Uri.fromFile(artwork).toString())
        }
        var sharedUri: android.net.Uri? = null
        val extraSharedUris = mutableListOf<android.net.Uri>()
        var bound = false
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            bound = context.bindService(Intent(context, MusicService::class.java), binding, Context.BIND_AUTO_CREATE)
            check(bound)
            val binder = future.get(30, TimeUnit.SECONDS)
            println("PLAYER_STAGE: production service bound")
            val active = binder.service
            assertNotNull("Cast handler must exist even when Cast starts disabled", active.castConnectionHandler)
            service = active
            await("Production player not ready") { active.isPlayerReady.value }
            val audio = audioFixture()
            println("PLAYER_STAGE: caching AAC/M4A fixtures")
            val opus = android.util.Base64.decode(
                instrumentation.context.assets.open("audio/player-opus.webm.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)
            for ((index, track) in tracks.withIndex()) {
                val bytes = if (index == 1) opus else audio
                active.database.insert(track)
                val hole = active.downloadCache.startReadWrite(track.id, 0, bytes.size.toLong())
                try {
                    val file = active.downloadCache.startFile(track.id, 0, bytes.size.toLong())
                    file.writeBytes(bytes)
                    active.downloadCache.commitFile(file, bytes.size.toLong())
                } finally { active.downloadCache.releaseHoleSpan(hole) }
                val mutations = ContentMetadataMutations()
                ContentMetadataMutations.setContentLength(mutations, bytes.size.toLong())
                active.downloadCache.applyContentMetadataMutations(track.id, mutations)
            }
            // Migrate one real private download into a shared SAF file while offline.
            val first = tracks[0]
            val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder(first.id, android.net.Uri.parse(first.id)).setCustomCacheKey(first.id).build()
            val completed = androidx.media3.exoplayer.offline.Download(request, androidx.media3.exoplayer.offline.Download.STATE_COMPLETED, 1, 1, audio.size.toLong(), 0, 0)
            (active.downloadUtil.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).putDownload(completed)
            active.downloadUtil.downloads.value = active.downloadUtil.downloads.value + (first.id to completed)
            val directory = java.io.File(context.cacheDir, "offline-export-${first.id}")
            val staged = active.downloadUtil.stageExistingAudio(first.id, directory)!!
            assertArrayEquals(audio, staged.readBytes())
            val testPackage = instrumentation.context.packageName
            device.executeShellCommand("am start -n $testPackage/fr.makoff.music.downloads.GrantAudioTreeActivity")
            val tree = android.provider.DocumentsContract.buildTreeDocumentUri("$testPackage.foldertest", "root")
            await("Document permission missing") {
                context.checkUriPermission(tree, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            val flac = fr.makoff.music.downloads.YtDlpAudioEngine.reuse(context, staged,
                fr.makoff.music.downloads.DownloadAudioFormat.FLAC, directory, "offline-flac")!!
            val external = fr.makoff.music.downloads.saveAudioToTree(context, tree, flac, "${first.id}.flac")
            sharedUri = external
            active.downloadUtil.registerExternalAudio(first, external, flac.length())
            // Exercise the same completed-cache -> export -> shared offline reference path for Opus and M4A.
            for ((index, format) in listOf(1 to fr.makoff.music.downloads.DownloadAudioFormat.BEST,
                2 to fr.makoff.music.downloads.DownloadAudioFormat.M4A)) {
                val track = tracks[index]
                val length = if (index == 1) opus.size.toLong() else audio.size.toLong()
                val nativeRequest = androidx.media3.exoplayer.offline.DownloadRequest.Builder(track.id, android.net.Uri.parse(track.id))
                    .setCustomCacheKey(track.id).build()
                val nativeCompleted = androidx.media3.exoplayer.offline.Download(nativeRequest,
                    androidx.media3.exoplayer.offline.Download.STATE_COMPLETED, 1, 1, length, 0, 0)
                (active.downloadUtil.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).putDownload(nativeCompleted)
                active.downloadUtil.downloads.value = active.downloadUtil.downloads.value + (track.id to nativeCompleted)
                val stage = active.downloadUtil.stageExistingAudio(track.id, directory)!!
                val exported = fr.makoff.music.downloads.YtDlpAudioEngine.reuse(context, stage, format, directory, "offline-$index")!!
                assertEquals(if (index == 1) "opus" else "m4a", exported.extension)
                // Retain compatibility with the .ogg documents produced by previous versions.
                if (index == 1) {
                    // The stream is WebM while the native download is remuxed Opus: never mix their byte offsets.
                    active.downloadCache.removeResource(track.id)
                    cacheAudio(active.playerCache, track.id, opus)
                    val originalSize = ContentMetadataMutations()
                    ContentMetadataMutations.setContentLength(originalSize, opus.size.toLong())
                    active.downloadCache.applyContentMetadataMutations(track.id, originalSize)
                    val pending = androidx.media3.exoplayer.offline.Download(nativeRequest,
                        androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING, 1, 1, length, 0, 0)
                    (active.downloadUtil.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).putDownload(pending)
                    active.downloadUtil.downloads.value = active.downloadUtil.downloads.value + (track.id to pending)
                    main { active.playQueue(ListQueue("Pendant le téléchargement", listOf(track.toMediaItem())), playWhenReady = true) }
                    await("Original WebM must play before download publication") {
                        main { active.player.isPlaying && active.player.currentPosition > 200 }
                    }
                    cacheAudio(active.downloadCache, track.id, exported.readBytes())
                    main { active.player.seekTo(8000) }
                    await("Pending Opus download must not corrupt the WebM reader") {
                        main { active.player.playbackState == Player.STATE_READY && active.player.currentPosition in 7900..11000 && active.player.playerError == null }
                    }
                    val finished = androidx.media3.exoplayer.offline.Download(nativeRequest,
                        androidx.media3.exoplayer.offline.Download.STATE_COMPLETED, 1, 1, exported.length(), 0, 0)
                    (active.downloadUtil.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).putDownload(finished)
                    active.downloadUtil.downloads.value = active.downloadUtil.downloads.value + (track.id to finished)
                    await("Finished native Opus must preserve playback position") {
                        main { active.player.playbackState == Player.STATE_READY && active.player.currentPosition >= 7800 && active.player.playerError == null }
                    }
                }
                val tagged = fr.makoff.music.downloads.tagAudioFile(context, exported, track, index, directory)
                val uri = fr.makoff.music.downloads.saveAudioToTree(context, tree, tagged,
                    "${track.id}.${if (index == 1) "ogg" else "m4a"}")
                extraSharedUris += uri
                active.downloadUtil.registerExternalAudio(track, uri, tagged.length())
                assertTrue(active.downloadCache.getCachedSpans(track.id).isEmpty())
                assertArrayEquals(tagged.readBytes(), context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                if (index == 1) {
                    main { active.player.seekTo(12000) }
                    await("Publishing a tagged public Opus must preserve offline seeking") {
                        main { active.player.playbackState == Player.STATE_READY && active.player.currentPosition in 11800..15000 && active.player.playerError == null }
                    }
                    println("DOWNLOAD_HANDOFF_OK: WebM stream -> native Opus -> tagged shared Opus; offline seek and position retained")
                }
            }
            directory.deleteRecursively()
            assertTrue("Private copy was not removed", active.downloadCache.getCachedSpans(first.id).isEmpty())
            assertEquals(external, active.downloadUtil.externalAudioUri(first.id))
            println("PLAYER_STAGE: private cache exported and replaced by shared document without network")
            tracks.forEachIndexed { i, track ->
                active.database.insert(RelatedSongMap(songId = track.id, relatedSongId = tracks[(i + 1) % tracks.size].id))
            }
            println("PLAYER_STAGE: fixtures and local metadata ready")
            val controller = main { PlayerConnection(context, binder, active.database, scope) }
            connection = controller
            for (index in listOf(1, 2)) {
                main { active.playQueue(ListQueue("Formats exportés", listOf(tracks[index].toMediaItem())), playWhenReady = true) }
                await("Shared format $index did not decode: ${main { active.player.playerError }}") {
                    main { active.player.playbackState == Player.STATE_READY && active.player.duration >= 19000 }
                }
                await("Shared format $index rendered no audio") {
                    main { active.player.isPlaying && active.player.currentPosition > 200 &&
                        (active.player.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 }
                }
                main { active.player.seekTo(8000) }
                await("Shared format $index cannot seek") {
                    main { active.player.currentPosition in 7900..11000 && active.player.playbackState == Player.STATE_READY }
                }
                assertNull(main { active.player.playerError })
                main { active.player.pause() }
                assertTrue(active.downloadCache.getCachedSpans(tracks[index].id).isEmpty())
                assertTrue(active.playerCache.getCachedSpans(tracks[index].id).isEmpty())
                println("SHARED_FORMAT_PLAYBACK_OK: ${if (index == 1) "Ogg/Opus" else "M4A"}; public file, no network, real rendered buffers and seek")
            }
            main { active.playQueue(ListQueue("Validation locale", tracks.take(2).map { it.toMediaItem() }), playWhenReady = false) }
            await("Shared FLAC did not decode: ${main { active.player.playerError }}") {
                main { active.player.playbackState == Player.STATE_READY && active.player.duration >= 19000 }
            }
            assertNull(main { active.player.playerError })
            println("PLAYER_STAGE: cached audio prepared")
            val miniPlayer = device.wait(Until.findObject(By.desc(context.getString(R.string.player))), 15000)
            assertNotNull("Mini-player control", miniPlayer)
            delay(1200)
            device.executeShellCommand("screencap -p /data/local/tmp/glass-mini-player.png")
            miniPlayer!!.click()
            await("Expanded player is visible", 10000) {
                freshControlBounds(context.getString(R.string.next)) != null
            }
            instrumentation.waitForIdleSync()
            delay(1200)
            device.executeShellCommand("screencap -p /data/local/tmp/glass-player.png")
            device.waitForIdle()
            clickDescription(R.string.play)
            await("UI Play did not render audio") { main { active.player.isPlaying && active.player.currentPosition > 200 } }
            await("No audio buffers rendered") { main { (active.player.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 } }
            withTimeout(5000) { controller.isPlaying.first { it } }
            clickDescription(R.string.player_pause)
            await("UI Pause did not stop playback") { main { !active.player.playWhenReady } }
            println("PLAYER_UI_OK: cached AAC/M4A decoded; real UI play/pause and rendered audio buffers")

            main { controller.seekTo(8000) }
            await("Seek failed") { main { active.player.currentPosition in 7800..8200 } }
            clickDescription(R.string.previous)
            await("Previous should restart after 3 seconds") { main { active.player.currentMediaItemIndex == 0 && active.player.currentPosition < 3000 } }
            main { controller.pause() }
            clickDescription(R.string.next)
            await("UI Next failed") { main { active.player.currentMediaItem?.mediaId == tracks[1].id } }
            main { controller.pause(); controller.seekTo(0) }
            clickDescription(R.string.previous)
            await("UI Previous failed") { main { active.player.currentMediaItem?.mediaId == tracks[0].id } }
            main { controller.pause() }
            println("PLAYER_TRANSPORT_OK: seek, restart, UI next and previous")

            main { controller.playNext(tracks[2].toMediaItem()); controller.addToQueue(tracks[3].toMediaItem()) }
            await("Queue insert/append failed") { main { active.player.mediaItemCount == 4 } }
            assertEquals(tracks[2].id, main { active.player.getMediaItemAt(1).mediaId })
            assertEquals(tracks[3].id, main { active.player.getMediaItemAt(3).mediaId })
            main { active.player.moveMediaItem(3, 2); active.player.removeMediaItem(2) }
            assertEquals(3, main { active.player.mediaItemCount })
            main { active.player.shuffleModeEnabled = true }
            await("Shuffle state not propagated") { controller.shuffleModeEnabled.value }
            main { active.player.shuffleModeEnabled = false; active.player.repeatMode = Player.REPEAT_MODE_OFF }
            for (expected in listOf(Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_OFF)) {
                main { active.player.toggleRepeatMode() }
                await("Repeat state not propagated: $expected") { controller.repeatMode.value == expected }
            }
            main { active.player.setPlaybackSpeed(1.25f) }
            assertEquals(1.25f, main { active.player.playbackParameters.speed }, 0.001f)
            main { active.player.setPlaybackSpeed(1f) }
            println("PLAYER_QUEUE_OK: insert, append, move, remove, shuffle, repeat cycle, speed")

            main {
                active.automixItems.value = listOf(tracks[3].toMediaItem())
                assertTrue(active.playNextAutomix(tracks[3].toMediaItem(), 99))
                assertFalse(active.playNextAutomix(tracks[3].toMediaItem(), 99))
                assertEquals(4, active.player.mediaItemCount)
                assertEquals(tracks[3].id, active.player.getMediaItemAt(1).mediaId)
                active.player.removeMediaItem(1)
                active.automixItems.value = listOf(tracks[3].toMediaItem())
                active.addToQueueAutomix(tracks[3].toMediaItem(), -1)
                active.addToQueueAutomix(tracks[3].toMediaItem(), -1)
                assertEquals(4, active.player.mediaItemCount)
                active.player.removeMediaItem(3)
            }
            println("PLAYER_AUTOMIX_OK: stale positions resolved by ID; consumed suggestions cannot be added twice")

            main { controller.toggleLibrary() }
            await("Library action not persisted") { runBlocking { active.database.song(tracks[0].id).first()?.song?.inLibrary != null } }
            main { controller.toggleLibrary() }
            await("Library removal not persisted") { runBlocking { active.database.song(tracks[0].id).first()?.song?.inLibrary == null } }
            main { controller.toggleLike() }
            await("Like not persisted") { runBlocking { active.database.song(tracks[0].id).first()?.song?.liked == true } }
            main { controller.toggleLike() }
            await("Unlike not persisted") { runBlocking { active.database.song(tracks[0].id).first()?.song?.liked == false } }
            println("PLAYER_LIBRARY_OK: library add/remove and like/unlike persisted locally")

            val originalPlayer = main { active.player }
            context.dataStore.edit { it[AudioTrackPlaybackParamsKey] = false }
            await("AudioTrack change did not recreate actual player") { main { active.player !== originalPlayer } }
            await("Queue lost during audio renderer recreation") { main { active.player.mediaItemCount == 3 && active.player.playbackState == Player.STATE_READY } }
            main { active.player.seekTo(0, 0); controller.pause() }

            main { active.player.setPlaybackSpeed(1.25f); controller.play() }
            val softwarePosition = main { active.player.currentPosition }
            delay(1200)
            assertTrue("Software processing must play audio", main { active.player.currentPosition > softwarePosition + 500 })
            main { controller.pause() }
            val softwarePlayer = main { active.player }
            context.dataStore.edit { it[AudioTrackPlaybackParamsKey] = true }
            await("Re-enabling AudioTrack did not recreate renderer") { main { active.player !== softwarePlayer && active.player.playbackState == Player.STATE_READY } }
            assertEquals(1.25f, main { active.player.playbackParameters.speed }, 0.001f)
            main { controller.play() }
            val hardwarePosition = main { active.player.currentPosition }
            delay(1200)
            assertTrue("AudioTrack processing must play audio", main { active.player.currentPosition > hardwarePosition + 500 })
            main { controller.pause(); active.player.setPlaybackSpeed(1f) }
            println("PLAYER_RENDERER_OK: both modes preserve queue/speed and play actual cached audio")


            context.dataStore.edit { it[AudioOffload] = true }
            await("Audio offload preference did not reach player") { main {
                active.player.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode ==
                    androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
            } }
            val durationBefore = before[CrossfadeDurationKey]
            val gaplessBefore = before[CrossfadeGaplessKey]
            try {
                main { active.player.seekTo(0, 17000) }
                context.dataStore.edit {
                    it[CrossfadeEnabledKey] = true
                    it[CrossfadeDurationKey] = 1f
                    it[CrossfadeGaplessKey] = false
                }
                await("Crossfade did not disable incompatible offload") { main {
                    active.player.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode ==
                        androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                } }
                val beforeCrossfade = main { active.player }
                main { controller.play() }
                await("Crossfade preference did not schedule a real player swap", 12000) { main {
                    active.player !== beforeCrossfade && active.player.currentMediaItem?.mediaId == tracks[2].id
                } }
                await("Crossfade did not restore full player volume", 5000) { main { active.player.volume > 0.99f } }
                main { controller.pause() }
                println("PLAYER_CROSSFADE_OK: changing preference schedules actual overlap/swap; offload excluded")
            } finally {
                context.dataStore.edit { prefs ->
                    prefs[CrossfadeEnabledKey] = false
                    prefs[AudioOffload] = false
                    durationBefore?.let { prefs[CrossfadeDurationKey] = it } ?: prefs.remove(CrossfadeDurationKey)
                    gaplessBefore?.let { prefs[CrossfadeGaplessKey] = it } ?: prefs.remove(CrossfadeGaplessKey)
                }
                main { active.player.seekTo(0, 0); controller.pause() }
            }

            context.dataStore.edit { it[SkipSilenceKey] = true }
            await("Skip-silence preference did not reach actual player") { main { active.player.skipSilenceEnabled } }
            cacheAudio(active.playerCache, tracks[3].id, audio)
            context.dataStore.edit { it[SkipSilenceKey] = false; it[AudioQualityKey] = AudioQuality.LOW.name }
            await("Quality change must clear queued streaming bytes") { active.playerCache.getCachedSpans(tracks[3].id).isEmpty() }
            println("PLAYER_QUALITY_CACHE_OK: queued streaming cache cleared; completed shared/private downloads kept")
            assertEquals("Changing quality lost shared download", sharedUri, active.downloadUtil.externalAudioUri(tracks[0].id))
            assertTrue("Playback recreated private download bytes", active.downloadCache.getCachedSpans(tracks[0].id).isEmpty())
            assertTrue("Playback recreated streaming cache bytes", active.playerCache.getCachedSpans(tracks[0].id).isEmpty())
            assertTrue("Changing quality erased another private download", active.downloadCache.isCached(tracks[3].id, 0, audio.size.toLong()))
            main { controller.play() }
            await("Playback after quality change failed") { main { active.player.isPlaying } }
            val volume = device.wait(Until.findObject(By.desc(context.getString(R.string.player_volume))), 10000)
            assertNotNull("Volume must be in the player", volume)
            val volumeBounds = volume!!.visibleBounds
            val previousVolume = active.playerVolume.value
            assertTrue(device.click(volumeBounds.left + volumeBounds.width() / 4, volumeBounds.centerY()))
            await("Visible volume slider must control service volume") { active.playerVolume.value < 0.8f }
            await("Volume must reach the audio player") { main { active.player.volume < 0.8f } }
            main { active.playerVolume.value = previousVolume }
            println("PLAYER_VOLUME_OK: visible slider changes actual player volume")
            clickDescription(R.string.more_options)
            assertNotNull("Player menu opens", device.wait(Until.findObject(By.text(context.getString(R.string.start_radio))), 5000))
            delay(1200)
            device.executeShellCommand("screencap -p /data/local/tmp/glass-player-menu.png")
            device.pressBack()
            await("Closing the menu must return to the expanded player") {
                freshControlBounds(context.getString(R.string.player_volume)) != null &&
                    freshControlBounds(context.getString(R.string.next)) != null
            }
            assertEquals("Opening menu must keep the current track", tracks[0].id,
                main { active.player.currentMediaItem?.mediaId })
            println("PLAYER_MENU_OK: menu dismisses back to expanded player with the same track")
            clickDescription(R.string.more_options)
            var eq = device.wait(Until.findObject(By.text(context.getString(R.string.equalizer))), 3000)
            for (attempt in 0 until 8) {
                if (eq != null) break
                device.swipe(device.displayWidth / 2, device.displayHeight * 8 / 10,
                    device.displayWidth / 2, device.displayHeight * 4 / 10, 35)
                eq = device.findObject(By.text(context.getString(R.string.equalizer)))
            }
            assertNotNull("Built-in equalizer entry", eq)
            assertFalse("System equalizer must be absent", device.hasObject(By.text(context.getString(R.string.system_equalizer))))
            val share = device.findObject(By.text(context.getString(R.string.share)))
            val details = device.findObject(By.text(context.getString(R.string.details)))
            assertNotNull("Share must be in player menu", share)
            assertNotNull("Details must remain in player menu", details)
            assertTrue("Share must precede Details", share!!.visibleBounds.top < details!!.visibleBounds.top)
            eq!!.click()
            assertTrue("Built-in equalizer screen", device.wait(Until.hasObject(By.text(context.getString(R.string.equalizer_header))), 10000))
            device.pressBack()
            println("PLAYER_EQUALIZER_OK: built-in equalizer remains reachable; system entry absent")
            println("PLAYER_SETTINGS_OK: renderer, skip-silence, cached playback and crossfade verified")
            assertNull("Streaming resolution should not be used for complete cached test audio", active.currentStreamClient.value)
            assertNull(main { active.player.playerError })
        } catch (failure: Throwable) {
            runCatching {
                val hierarchy = java.io.ByteArrayOutputStream()
                device.dumpWindowHierarchy(hierarchy)
                hierarchy.toString("UTF-8").replace("><", ">\n<").lineSequence()
                    .forEach { println("PLAYER_FAILURE_UI: $it") }
                device.executeShellCommand("screencap -p /data/local/tmp/offline-player-failure.png")
            }.onFailure { println("PLAYER_FAILURE_CAPTURE: $it") }
            throw failure
        } finally {
            main { connection?.pause(); connection?.dispose(); service?.takeIf { it.isPlayerReady.value }?.player?.clearMediaItems() }
            scope.cancel()
            service?.let { active -> tracks.forEach { track ->
                (active.downloadUtil.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).removeDownload(track.id)
                active.downloadUtil.downloads.value = active.downloadUtil.downloads.value - track.id
                active.downloadCache.removeResource(track.id)
                active.database.song(track.id).first()?.let { active.database.delete(it.song) }
            } }
            (extraSharedUris + listOfNotNull(sharedUri)).forEach { uri ->
                runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
            }
            artwork.delete()
            if (bound) context.unbindService(binding)
            device.pressBack()
            device.waitForIdle()
            context.dataStore.edit { prefs ->
                keys.forEach { key -> before[key]?.let { prefs[key] = it } ?: prefs.remove(key) }
                before[AudioQualityKey]?.let { prefs[AudioQualityKey] = it } ?: prefs.remove(AudioQualityKey)
            }
        }
    }
}
