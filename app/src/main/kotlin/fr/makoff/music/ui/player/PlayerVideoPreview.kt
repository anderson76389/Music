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
    var preview by remember(videoId) { mutableStateOf(PlayerVideoPreview()) }

    LaunchedEffect(videoId, enabled, isVideoSong, localOrUploaded, casting, title, artists, failedClipIds) {
        if (!PlayerVideoModePolicy.canProbeVideo(videoId, enabled, localOrUploaded, casting)) {
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
                    YouTube.videoCounterpart(videoId = id, title = title, artists = artists).getOrNull()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        if (counterpart != null && counterpart != id) onCompanionResolved(counterpart)

        // A verified pairing gets priority. The original item is probed as a
        // fallback ONLY when it is itself marked as video or had an already
        // verified video stream (e.g. a previous landscape session).
        val candidateIds = buildList {
            if (knownPlayableClipId != null) add(knownPlayableClipId)
            if (counterpart != null) add(counterpart)
            if (isVideoSong) add(id)
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
    return preview
}

/**
 * One prebuffered player for the current song, even when the full sheet closes.
 * No audio track is ever audible from this player; ExoPlayer's own audio session
 * continues to own playback, history, likes, volume and the queue.
 */
@Composable
internal fun rememberPreparedVideoPlayer(
    clip: PlayerVideoClip?,
    playerConnection: PlayerConnection,
    onUnavailable: (String) -> Unit,
): ExoPlayer? {
    val context = LocalContext.current
    val onUnavailableLatest by rememberUpdatedState(onUnavailable)
    val player = remember(clip) {
        clip?.let { selected ->
            val http = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(selected.stream.headers)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(http))
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(800, 4_000, 250, 500)
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
