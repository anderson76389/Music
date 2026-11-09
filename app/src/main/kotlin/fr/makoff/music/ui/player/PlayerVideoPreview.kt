package fr.makoff.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import fr.makoff.music.innertube.YouTube
import fr.makoff.music.playback.PlayerConnection
import fr.makoff.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlinx.coroutines.withContext

/**
 * Lives above the collapsible player sheet. The selected song's video availability
 * and prepared, muted video session survive closing/reopening the expanded UI.
 * Never persist signed video URLs, and never replace the audio service.
 */
internal data class PlayerVideoClip(
    val id: String,
    val stream: InnerTubeXPlayer.VideoStream,
)

internal data class PlayerVideoPreview(
    val clip: PlayerVideoClip? = null,
    val lookupFinished: Boolean = false,
)

/**
 * Probes proactively while the mini player is shown, instead of starting two
 * network lookups each time the user expands the full player.
 *
 * The selector is ONLY shown for an actually resolved video stream. A tentative
 * counterpart, a stale saved ID, or an unfinished request is not sufficient.
 */
@Composable
internal fun rememberPlayerVideoPreview(
    videoId: String?,
    enabled: Boolean,
    isVideoSong: Boolean,
    localOrUploaded: Boolean,
    casting: Boolean,
    title: String?,
    artists: List<String>,
    knownCompanionVideoId: String?,
    knownPlayableClipId: String?,
    failedClipIds: Set<String>,
    onCompanionResolved: (String) -> Unit,
    onPlayableResolved: (String) -> Unit,
): PlayerVideoPreview {
    // Hide the selector and release the decoder in the SAME recomposition
    // as disabling the setting, without waiting for a coroutine.
    val canProbe = PlayerVideoModePolicy.canProbeVideo(videoId, enabled, localOrUploaded, casting)
    var preview by remember(videoId, canProbe) {
        mutableStateOf(PlayerVideoPreview(lookupFinished = !canProbe))
    }

    LaunchedEffect(videoId, enabled, isVideoSong, localOrUploaded, casting, title, artists, failedClipIds) {
        if (!canProbe) {
            preview = PlayerVideoPreview(lookupFinished = true)
            return@LaunchedEffect
        }
        // The current stream remains valid across recomposition and sheet
        // collapse. Only a change of song or a confirmed decoder error retries.
        if (preview.clip?.let { it.id !in failedClipIds } == true) return@LaunchedEffect

        preview = PlayerVideoPreview()
        val id = videoId ?: return@LaunchedEffect
        val counterpart = if (isVideoSong) {
            id
        } else {
            knownCompanionVideoId ?: try {
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(3_500L) {
                        YouTube.videoCounterpart(videoId = id, title = title, artists = artists).getOrNull()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        if (counterpart != null && counterpart != id) onCompanionResolved(counterpart)

        // Prioritize the YouTube Music counterpart; use only the SAME original
        // item as fallback. Audio-only formats remain rejected by the resolver.
        val candidateIds = buildList {
            if (knownPlayableClipId != null) add(knownPlayableClipId)
            if (counterpart != null) add(counterpart)
            add(id)
        }.distinct().filterNot { it in failedClipIds }

        for (candidate in candidateIds) {
            val stream = try {
                withContext(Dispatchers.IO) { InnerTubeXPlayer.resolveVideoStream(candidate) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (stream != null) {
                preview = PlayerVideoPreview(PlayerVideoClip(candidate, stream), lookupFinished = true)
                onPlayableResolved(candidate)
                return@LaunchedEffect
            }
        }
        // Songs without a proven, playable video must never advertise the tabs.
        preview = PlayerVideoPreview(lookupFinished = true)
    }
    return if (canProbe) preview else PlayerVideoPreview(lookupFinished = true)
}

/**
 * One prebuffered player for the current song, even when the full sheet closes.
 * No audio track is ever audible from this player; ExoPlayer's own audio session
 * continues to own playback, history, likes, volume and the queue.
 */
@Composable
internal fun rememberPreparedVideoPlayer(
    clip: PlayerVideoClip?,
    audioId: String?,
    playerConnection: PlayerConnection,
    onUnavailable: (String) -> Unit,
    prewarmForSwitch: Boolean = false,
): ExoPlayer? {
    val context = LocalContext.current
    val onUnavailableLatest by rememberUpdatedState(onUnavailable)
    val player = remember(clip, audioId) {
        clip?.let { selected ->
            val http = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(selected.stream.headers)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(http))
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(1_500, 12_000, 300, 650)
                        .build(),
                )
                .build()
                .apply {
                    volume = 0f
                    playWhenReady = false
                    setMediaItem(
                        MediaItem.Builder()
                            .setUri(selected.stream.url)
                            .setMimeType(selected.stream.mimeType)
                            .build(),
                    )
                    seekTo(
                        runCatching { playerConnection.player.currentPosition }
                            .getOrDefault(0L).coerceAtLeast(0L),
                    )
                    prepare()
                }
        }
    }
    // Keep the muted prebuffer near the audio clock without extra network
    // lookups when the full player opens.
    LaunchedEffect(player, audioId, playerConnection, prewarmForSwitch) {
        val prepared = player ?: return@LaunchedEffect
        while (isActive) {
            // Only maintain a nearby muted video frame while the expanded
            // player is visible. A sheet left collapsed keeps its inexpensive
            // 8-second cache check, avoiding background video decoding.
            delay(if (prewarmForSwitch) 1_800L else 8_000L)
            val audio = playerConnection.player
            if (audioId != null && audio.currentMediaItem?.mediaId == audioId &&
                !prepared.playWhenReady && prepared.playbackState == Player.STATE_READY &&
                abs(audio.currentPosition - prepared.currentPosition) >
                    (if (prewarmForSwitch) 1_600L else 12_000L)
            ) {
                prepared.seekTo(audio.currentPosition.coerceAtLeast(0L))
            }
        }
    }
    DisposableEffect(player, clip) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                clip?.id?.let(onUnavailableLatest)
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.playWhenReady = false
            player?.clearVideoSurface()
            player?.release()
        }
    }
    return player
}
