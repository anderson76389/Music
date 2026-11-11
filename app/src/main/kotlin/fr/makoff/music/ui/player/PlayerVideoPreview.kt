package fr.makoff.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import fr.makoff.music.innertube.YouTube
import fr.makoff.music.constants.PlayerVideoQuality
import fr.makoff.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/**
 * Lives above the collapsible player sheet. The selected song's video availability
 * and prepared, muted video session survive closing/reopening the expanded UI.
 * Never persist signed video URLs, and never replace the audio service.
 */
internal data class PlayerVideoClip(
    val id: String,
    val stream: InnerTubeXPlayer.VideoStream,
    val maxHeight: Int,
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
    quality: PlayerVideoQuality,
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
    var preview by remember(videoId, canProbe, quality) {
        mutableStateOf(PlayerVideoPreview(lookupFinished = !canProbe))
    }

    LaunchedEffect(videoId, enabled, quality, isVideoSong, localOrUploaded, casting, title, artists, failedClipIds) {
        if (!canProbe) {
            preview = PlayerVideoPreview(lookupFinished = true)
            return@LaunchedEffect
        }
        // The current stream remains valid across recomposition and sheet
        // collapse. Only a change of song or a confirmed decoder error retries.
        if (preview.clip?.let { it.id !in failedClipIds } == true) return@LaunchedEffect

        preview = PlayerVideoPreview()
        val id = videoId ?: return@LaunchedEffect
        val attempted = mutableSetOf<String>()
        suspend fun resolve(candidate: String): PlayerVideoClip? {
            if (candidate in failedClipIds || !attempted.add(candidate)) return null
            val stream = try {
                withContext(Dispatchers.IO) {
                    InnerTubeXPlayer.resolveVideoStream(candidate, quality.maxHeight)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            return stream?.let { PlayerVideoClip(candidate, it, quality.maxHeight) }
        }

        fun publish(clip: PlayerVideoClip) {
            preview = PlayerVideoPreview(clip, lookupFinished = true)
            onPlayableResolved(clip.id)
        }

        // A previously proven clip is retried before any counterpart lookup.
        // Its signed URL is never persisted.
        if (knownPlayableClipId != null) {
            resolve(knownPlayableClipId)?.let {
                publish(it)
                return@LaunchedEffect
            }
        }

        // A same-song playable stream is looked up concurrently with the
        // potentially slow counterpart search. The first verified source wins;
        // outstanding requests are cancelled on success or song change.
        val clip = if (isVideoSong) {
            resolve(id)
        } else {
            coroutineScope {
                val winner = CompletableDeferred<PlayerVideoClip?>()
                val counterpartJob = launch {
                    val counterpart = knownCompanionVideoId ?: try {
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
                    if (counterpart != null && counterpart != id) {
                        onCompanionResolved(counterpart)
                        resolve(counterpart)?.let { winner.complete(it) }
                    }
                }
                val sameSongJob = launch {
                    resolve(id)?.let { winner.complete(it) }
                }
                val completionJob = launch {
                    joinAll(counterpartJob, sameSongJob)
                    winner.complete(null)
                }
                val selected = winner.await()
                counterpartJob.cancel()
                sameSongJob.cancel()
                completionJob.cancel()
                selected
            }
        }
        if (clip != null) {
            publish(clip)
            return@LaunchedEffect
        }
        // A guessed video ID must never display the tabs.
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
    audioPlayer: Player?,
    onUnavailable: (String) -> Unit,
    videoActive: Boolean,
): ExoPlayer? {
    val context = LocalContext.current
    val onUnavailableLatest by rememberUpdatedState(onUnavailable)
    val player = remember(clip, audioId, audioPlayer) {
        if (clip != null && audioPlayer != null) {
            val selected = clip
            val http = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(selected.stream.headers)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(1_500, 12_000, 300, 650)
                        .build(),
                )
                .build()
                .apply {
                    // HLS variants are adaptive and can exceed the extractor's
                    // chosen direct-format limit unless ExoPlayer also caps height.
                    trackSelectionParameters = trackSelectionParameters.buildUpon()
                        .setMaxVideoSize(Int.MAX_VALUE, selected.maxHeight)
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .build()
                    volume = 0f
                    playWhenReady = false
                    setMediaItem(
                        MediaItem.Builder()
                            .setUri(selected.stream.url)
                            .setMimeType(selected.stream.mimeType)
                            .build(),
                    )
                    seekTo(
                        runCatching { audioPlayer.currentPosition }
                            .getOrDefault(0L).coerceAtLeast(0L),
                    )
                    prepare()
                }
        } else null
    }
    val synchronizer = remember(player, audioId, audioPlayer) {
        if (player != null && audioId != null && audioPlayer != null) {
            PlayerVideoSynchronizer(audioPlayer, player, audioId) {
                android.os.SystemClock.elapsedRealtime()
            }
        } else null
    }
    DisposableEffect(synchronizer) {
        synchronizer?.attach()
        onDispose { synchronizer?.detach() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val videoActiveLatest by rememberUpdatedState(videoActive)
    DisposableEffect(synchronizer, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            synchronizer?.setActive(videoActiveLatest && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    SideEffect {
        synchronizer?.setActive(videoActive && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    LaunchedEffect(synchronizer, videoActive, lifecycle) {
        val session = synchronizer ?: return@LaunchedEffect
        if (!videoActive) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(1_500L)
                session.tick()
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
