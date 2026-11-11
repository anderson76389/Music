package fr.makoff.music.playback

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import fr.makoff.music.MainActivity
import fr.makoff.music.R
import fr.makoff.music.constants.PlayerVideoThemeMode
import fr.makoff.music.ui.player.PlayerModePills
import fr.makoff.music.ui.player.PlayerVideoClip
import fr.makoff.music.ui.player.VideoOnlySurface
import fr.makoff.music.ui.player.rememberPreparedVideoPlayer
import fr.makoff.music.utils.InnerTubeXPlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Real decoder and production switch/surface, using synthetic local media. */
@RunWith(AndroidJUnit4::class)
class PlayerVideoSwitchInstrumentedTest {
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

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { message }
            SystemClock.sleep(50)
        }
    }

    private fun click(label: String) {
        requireNotNull(device.wait(Until.findObject(By.desc(label)), 5_000L)) { "Missing video control: $label" }.click()
    }

    private fun fixture(name: String): File = File(context.cacheDir, "switch-$name").apply {
        writeBytes(android.util.Base64.decode(
            instrumentation.context.assets.open("audio/$name.base64").use { it.readBytes() },
            android.util.Base64.DEFAULT,
        ))
    }

    @Test(timeout = 180_000L)
    fun realVideoSwitchPauseSeekSpeedRotationAndBackground() {
        val audioFile = fixture("player-aac.m4a")
        val videoFile = fixture("player-video.mp4")
        val audio = main {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.Builder().setMediaId("fixture-song").setUri(android.net.Uri.fromFile(audioFile)).build())
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                play()
            }
        }
        val selected = mutableStateOf(false)
        val expanded = mutableStateOf(true)
        val videoRef = AtomicReference<ExoPlayer?>()
        val failure = AtomicReference<String?>()
        var activity: MainActivity? = null
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Activity did not resume") {
                activity = main {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().firstOrNull()
                }
                activity != null
            }
            main {
                val clip = PlayerVideoClip("fixture-video", InnerTubeXPlayer.VideoStream(
                    android.net.Uri.fromFile(videoFile).toString(), "video/mp4", emptyMap(),
                ), 720)
                requireNotNull(activity).setContent {
                    MaterialTheme {
                        val prepared = rememberPreparedVideoPlayer(
                            clip = clip, audioId = "fixture-song", audioPlayer = audio,
                            onUnavailable = { failure.set(it) },
                            videoActive = selected.value && expanded.value,
                        )
                        val playing = androidx.compose.runtime.remember { mutableStateOf(audio.isPlaying) }
                        DisposableEffect(audio) {
                            val listener = object : Player.Listener {
                                override fun onIsPlayingChanged(isPlaying: Boolean) { playing.value = isPlaying }
                            }
                            audio.addListener(listener)
                            onDispose { audio.removeListener(listener) }
                        }
                        SideEffect { videoRef.set(prepared) }
                        Column(Modifier.statusBarsPadding()) {
                            PlayerModePills(
                                audioLabel = context.getString(R.string.player_cover_label),
                                videoLabel = context.getString(R.string.player_video_label),
                                showVideo = selected.value,
                                onAudioClick = { selected.value = false },
                                onVideoClick = { selected.value = true },
                                selectorTheme = PlayerVideoThemeMode.AUTO, appDarkTheme = false,
                                artworkThemeColor = Color.Blue, customThemeColor = Color.Blue,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // Recreate only the surface on rotation, as the production
                            // portrait and fullscreen branches do. The session stays put.
                            key(LocalConfiguration.current.orientation) {
                                if (prepared != null && expanded.value) {
                                    VideoOnlySurface(
                                        videoPlayer = prepared,
                                        onTogglePlayPause = { audio.playWhenReady = !audio.playWhenReady },
                                        isPlaying = playing.value, visible = selected.value,
                                        onUnavailable = { failure.set("surface") },
                                        modifier = Modifier.fillMaxWidth().height(220.dp)
                                            .semantics { contentDescription = "fixture-video-surface" },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            await("Audio fixture failed") { main { audio.isPlaying && audio.currentPosition > 100L } }
            await("Muted decoder failed to prepare: ${failure.get()}") {
                main { videoRef.get()?.playbackState == Player.STATE_READY }
            }
            val video = requireNotNull(videoRef.get())
            assertEquals(0f, main { video.volume }, 0f)
            assertTrue(main { video.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO) })
            val audioLabel = context.getString(R.string.player_cover_label)
            val videoLabel = context.getString(R.string.player_video_label)
            repeat(6) {
                click(videoLabel)
                await("Video tab did not start playback") { main { video.playWhenReady } }
                click(audioLabel)
                await("Audio tab left the video running") { main { !video.playWhenReady } }
            }
            assertSame(video, videoRef.get())
            assertTrue(main { audio.playWhenReady })
            click(videoLabel)
            await("No real video frame rendered") {
                main { (video.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 }
            }
            click("fixture-video-surface")
            await("Video tap did not pause both clocks") { main { !audio.playWhenReady && !video.playWhenReady } }
            click(context.getString(R.string.play))
            await("Play did not resume both clocks") { main { audio.isPlaying && video.playWhenReady } }
            main { audio.pause(); audio.seekTo(6_000L) }
            await("Explicit seek did not align the video") {
                main { video.playbackState == Player.STATE_READY && kotlin.math.abs(video.currentPosition - 6_000L) < 600L }
            }
            main { audio.setPlaybackSpeed(1.5f); audio.play() }
            await("Video ignored audio speed") { main { video.playbackParameters.speed == 1.5f } }
            main { expanded.value = false }
            await("Collapsing the sheet left its decoder running") { main { !video.playWhenReady } }
            assertTrue(main { audio.playWhenReady })
            main { expanded.value = true }
            await("Reopening failed to resume the selected video") { main { video.playWhenReady } }
            val framesBeforeRotation = main { video.videoDecoderCounters?.renderedOutputBufferCount ?: 0 }
            device.setOrientationLeft()
            await("Rotation lost the shared video session") {
                main { context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                    videoRef.get() === video && video.playWhenReady &&
                    (video.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > framesBeforeRotation }
            }
            device.setOrientationNatural()
            device.pressHome()
            await("Background app kept decoding video") { main { !video.playWhenReady } }
            assertTrue(main { audio.playWhenReady })
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Foreground did not resume video") { main { video.playWhenReady } }
            assertNull(failure.get())
            assertNull(main { audio.playerError })
            assertNull(main { video.playerError })
            val proof = "VIDEO_SWITCH_LOCAL_OK: 12 tab clicks, real frames, pause/play, seek, speed, collapse/reopen, rotation and background; one muted decoder"
            instrumentation.addResults(android.os.Bundle().apply { putString("video_switch_proof", proof) })
            println(proof)
        } finally {
            device.unfreezeRotation()
            main {
                activity?.setContent { }
                audio.release()
                // Restore the real application UI for the remaining interaction tests.
                activity?.recreate()
            }
            audioFile.delete()
            videoFile.delete()
        }
    }
}
