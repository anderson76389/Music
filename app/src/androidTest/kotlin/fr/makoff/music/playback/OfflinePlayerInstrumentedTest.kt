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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class OfflinePlayerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun await(message: String, timeout: Long = 15000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { message }
            SystemClock.sleep(50)
        }
    }

    private fun clickDescription(id: Int) {
        val label = context.getString(id)
        val button = device.wait(Until.findObject(By.desc(label)), 10000)
        assertNotNull("Player control: $label", button)
        button!!.click()
    }

    private fun wave(): ByteArray {
        val rate = 16000
        val samples = rate * 20
        val out = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { out.putShort((sin(2 * Math.PI * 440 * it / rate) * 4000).toInt().toShort()) }
        return out.array()
    }

    @Test(timeout = 210000)
    fun realCachedAudioPlayerControlsQueueTimersAndQuality() = runBlocking {
        val keys = listOf(AutoplayKey, AutoLoadMoreKey, AutoRadioQueueKey, PersistentQueueKey,
            ShowLyricsKey, CrossfadeEnabledKey, AudioOffload, SkipSilenceKey, AutoDownloadOnLikeKey)
        val before = context.dataStore.data.first()
        context.dataStore.edit { prefs -> keys.forEach { prefs[it] = false } }
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
        val tracks = (0..3).map { index ->
            MediaMetadata(UUID.randomUUID().toString().replace("-", "").take(11),
                "Validation lecteur ${'A' + index}", listOf(MediaMetadata.Artist("test-local-artist", "Audio de test")), 20)
        }
        var bound = false
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            bound = context.bindService(Intent(context, MusicService::class.java), binding, Context.BIND_AUTO_CREATE)
            check(bound)
            val binder = future.get(30, TimeUnit.SECONDS)
            val active = binder.service
            service = active
            await("Production player not ready") { active.isPlayerReady.value }
            val audio = wave()
            for (track in tracks) {
                active.database.insert(track)
                val hole = active.downloadCache.startReadWrite(track.id, 0, audio.size.toLong())
                try {
                    val file = active.downloadCache.startFile(track.id, 0, audio.size.toLong())
                    file.writeBytes(audio)
                    active.downloadCache.commitFile(file, audio.size.toLong())
                } finally { active.downloadCache.releaseHoleSpan(hole) }
                val mutations = ContentMetadataMutations()
                ContentMetadataMutations.setContentLength(mutations, audio.size.toLong())
                active.downloadCache.applyContentMetadataMutations(track.id, mutations)
            }
            tracks.forEachIndexed { i, track ->
                active.database.insert(RelatedSongMap(songId = track.id, relatedSongId = tracks[(i + 1) % tracks.size].id))
            }
            val controller = main { PlayerConnection(context, binder, active.database, scope) }
            connection = controller
            main { active.playQueue(ListQueue("Validation locale", tracks.take(2).map { it.toMediaItem() }), playWhenReady = false) }
            await("Cached PCM did not decode: ${main { active.player.playerError }}") {
                main { active.player.playbackState == Player.STATE_READY && active.player.duration >= 19000 }
            }
            assertNull(main { active.player.playerError })
            val title = device.wait(Until.findObject(By.text(tracks[0].title)), 15000)
            assertNotNull("Mini-player title", title)
            title!!.click()
            clickDescription(R.string.play)
            await("UI Play did not render audio") { main { active.player.isPlaying && active.player.currentPosition > 200 } }
            await("No audio buffers rendered") { main { (active.player.audioCounters?.renderedOutputBufferCount ?: 0) > 0 } }
            clickDescription(R.string.player_pause)
            await("UI Pause did not stop playback") { main { !active.player.playWhenReady } }
            println("PLAYER_UI_OK: cached WAV decoded; real UI play/pause and rendered audio buffers")

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

            main { controller.toggleLibrary() }
            await("Library action not persisted") { runBlocking { active.database.song(tracks[0].id).first()?.song?.inLibrary != null } }
            main { controller.toggleLibrary() }
            await("Library removal not persisted") { runBlocking { active.database.song(tracks[0].id).first()?.song?.inLibrary == null } }
            main { controller.toggleLike() }
            await("Like not persisted") { runBlocking { active.database.song(tracks[0].id).first()?.song?.liked == true } }
            main { controller.toggleLike() }
            await("Unlike not persisted") { runBlocking { active.database.song(tracks[0].id).first()?.song?.liked == false } }
            println("PLAYER_LIBRARY_OK: library add/remove and like/unlike persisted locally")

            context.dataStore.edit { it[SkipSilenceKey] = true }
            await("Skip-silence preference did not reach actual player") { main { active.player.skipSilenceEnabled } }
            context.dataStore.edit { it[SkipSilenceKey] = false; it[AudioQualityKey] = AudioQuality.LOW.name }
            SystemClock.sleep(500)
            assertTrue("Changing quality erased cached download", active.downloadCache.isCached(tracks[0].id, 0, audio.size.toLong()))
            main { controller.play() }
            await("Playback after quality change failed") { main { active.player.isPlaying } }
            main { active.sleepTimer!!.start(0) }
            await("Zero-minute sleep timer did not pause") { main { !active.player.playWhenReady && !active.sleepTimer!!.isActive } }
            main { active.player.seekTo(0, 19700); active.sleepTimer!!.start(-1); controller.play() }
            await("End-of-song sleep timer did not pause", 12000) { main { !active.sleepTimer!!.isActive && !active.player.playWhenReady } }
            println("PLAYER_SETTINGS_OK: skip-silence applied, cached file kept on quality change, timer immediate/end-of-song")
            assertNull("Streaming resolution should not be used for complete cached test audio", active.currentStreamClient.value)
            assertNull(main { active.player.playerError })
        } finally {
            main { service?.sleepTimer?.clear(); connection?.pause(); connection?.dispose(); service?.takeIf { it.isPlayerReady.value }?.player?.clearMediaItems() }
            scope.cancel()
            service?.let { active -> tracks.forEach { track ->
                active.downloadCache.removeResource(track.id)
                active.database.song(track.id).first()?.let { active.database.delete(it.song) }
            } }
            if (bound) context.unbindService(binding)
            context.dataStore.edit { prefs ->
                keys.forEach { key -> before[key]?.let { prefs[key] = it } ?: prefs.remove(key) }
                before[AudioQualityKey]?.let { prefs[AudioQualityKey] = it } ?: prefs.remove(AudioQualityKey)
            }
        }
    }
}
