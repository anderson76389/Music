package fr.makoff.music.ui.player

import android.view.TextureView
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import android.widget.Toast
import fr.makoff.music.ui.component.CastButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import fr.makoff.music.R
import fr.makoff.music.constants.PlayerVideoThemeMode
import fr.makoff.music.innertube.YouTube
import fr.makoff.music.playback.PlayerConnection
import fr.makoff.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The regular audio session remains the source of truth. When a video is requested,
 * only its picture track is rendered; the service continues to own audio, queue and seeks.
 */
/**
 * Compact YouTube Music-style Song / Video controls.
 *
 * On portrait, the top bar overlays only the pre-reserved header area; the cover
 * remains full-size and the system status-bar inset is applied exactly once.
 * On landscape, the mode bar gets its own row above the artwork.
 */
@Composable
internal fun PlayerArtworkSwitcher(
    videoId: String?,
    videoModeEnabled: Boolean,
    selectorTheme: PlayerVideoThemeMode,
    appDarkTheme: Boolean,
    isVideoSong: Boolean,
    isLocalOrUploaded: Boolean,
    songTitle: String?,
    artistNames: List<String>,
    knownCompanionVideoId: String?,
    onCompanionVideoIdResolved: (String) -> Unit,
    knownPlayableClipId: String?,
    onPlayableClipResolved: (String) -> Unit,
    onPlayableClipInvalidated: (String) -> Unit,
    isCasting: Boolean,
    isExpanded: Boolean,
    isPlaying: Boolean,
    showVideo: Boolean,
    onShowVideoChange: (Boolean) -> Unit,
    playerConnection: PlayerConnection,
    modeTextColor: Color,
    onCollapse: () -> Unit,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
    artwork: @Composable (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.player_video_unavailable)
    val coverLabel = stringResource(R.string.player_cover_label)
    val videoLabel = stringResource(R.string.player_video_label)
    var companionVideoId by remember(videoId, knownCompanionVideoId) {
        mutableStateOf(knownCompanionVideoId)
    }
    var counterpartLookupDone by remember(videoId, knownCompanionVideoId) {
        mutableStateOf(knownCompanionVideoId != null)
    }
    val isYouTubeTrack = videoId != null && videoId.length == 11 &&
        videoId.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    // Prefer the official Song/Video counterpart, but also check whether
    // the original YouTube item itself exposes a real video track.
    // Some YouTube Music responses omit both the pairing and video flag.
    LaunchedEffect(videoId, videoModeEnabled, isVideoSong, isLocalOrUploaded, isCasting, isExpanded, songTitle, artistNames, knownCompanionVideoId) {
        companionVideoId = knownCompanionVideoId
        counterpartLookupDone = knownCompanionVideoId != null
        if (!videoModeEnabled || !isYouTubeTrack || isLocalOrUploaded || isCasting || !isExpanded ||
            isVideoSong || knownCompanionVideoId != null
        ) {
            counterpartLookupDone = true
            return@LaunchedEffect
        }
        val pairedId = try {
            withContext(Dispatchers.IO) {
                YouTube.videoCounterpart(
                    videoId = videoId!!,
                    title = songTitle,
                    artists = artistNames,
                ).getOrNull()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        companionVideoId = pairedId
        pairedId?.let(onCompanionVideoIdResolved)
        counterpartLookupDone = true
    }

    // A direct video stream is a valid video for the *same* audio queue item;
    // never substitute an unverified search result.
    // The first signed URL can be unavailable even when the actual music
    // video is playable. Try the same YouTube item after an unavailable paired
    // clip, without inventing a search match or retrying one bad ID forever.
    var failedClipIds by remember(videoId) { mutableStateOf(emptySet<String>()) }
    val preferredClipId = knownPlayableClipId ?: PlayerVideoModePolicy.candidateVideoId(
        originalId = videoId,
        isYouTubeTrack = isYouTubeTrack,
        isLocalOrUploaded = isLocalOrUploaded,
        isVideoSong = isVideoSong,
        pairedId = companionVideoId,
        counterpartLookupDone = counterpartLookupDone,
    )
    val clipVideoId = PlayerVideoModePolicy.bestClipId(
        originalId = videoId,
        preferredId = preferredClipId,
        youtubeTrack = isYouTubeTrack,
        localOrUploaded = isLocalOrUploaded,
        counterpartLookupDone = counterpartLookupDone,
        failedIds = failedClipIds,
    )
    // Do not advertise an unplayable clip. Resolve the actual signed video
    // stream in the background ONLY while the full player is expanded, and
    // reuse it on demand. This also avoids a duplicate network extraction
    // each time the user switches between Son and Vidéo.
    var availableVideoStream by remember(clipVideoId) {
        mutableStateOf<InnerTubeXPlayer.VideoStream?>(null)
    }
    var videoStreamLookupFinished by remember(clipVideoId) { mutableStateOf(false) }
    LaunchedEffect(clipVideoId, videoModeEnabled, isExpanded, isCasting, isLocalOrUploaded) {
        val id = clipVideoId
        videoStreamLookupFinished = false
        if (!videoModeEnabled || id.isNullOrBlank() || !isExpanded || isCasting || isLocalOrUploaded) {
            availableVideoStream = null
            return@LaunchedEffect
        }
        // Fetch the actual video stream once and prebuffer it. The mode buttons
        // can already be shown for an official pair while this is still loading.
        availableVideoStream = try {
            withContext(Dispatchers.IO) { InnerTubeXPlayer.resolveVideoStream(id) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        videoStreamLookupFinished = true
        if (availableVideoStream != null) {
            onPlayableClipResolved(id)
        } else {
            failedClipIds = failedClipIds + id
            if (knownPlayableClipId == id) onPlayableClipInvalidated(id)
        }
    }
    val playableStream = availableVideoStream.takeIf {
        videoModeEnabled && isExpanded && !isCasting && !isLocalOrUploaded &&
            clipVideoId != null && clipVideoId !in failedClipIds
    }

    // Prepare once, as soon as the clip stream is known, instead of creating
    // and buffering a second player on each tap of the Vidéo tab.
    // Keep the hidden player PAUSED and MUTED. Only buffer a short window;
    // releasing on collapse, source change or disabling this mode saves RAM,
    // GPU work, bandwidth and battery on foldables.
    val warmedVideoPlayer = remember(playableStream, clipVideoId) {
        playableStream?.let { stream ->
            val http = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(stream.headers)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(http))
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(2_000, 8_000, 250, 500)
                        .build(),
                )
                .build()
                .apply {
                    volume = 0f
                    playWhenReady = false
                    setMediaItem(
                        MediaItem.Builder()
                            .setUri(stream.url)
                            .setMimeType(stream.mimeType)
                            .build(),
                    )
                    val position = runCatching { playerConnection.player.currentPosition }
                        .getOrDefault(0L).coerceAtLeast(0L)
                    seekTo(position)
                    prepare()
                }
        }
    }
    DisposableEffect(warmedVideoPlayer) {
        onDispose {
            warmedVideoPlayer?.clearVideoSurface()
            warmedVideoPlayer?.release()
        }
    }
    // The selector appears as soon as a valid stream was resolved.
    // Waiting until ExoPlayer reaches READY used to hide Son/Vidéo entirely
    // for slow connections; video buffering is now shown only on the video tab.
    DisposableEffect(warmedVideoPlayer, clipVideoId) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                clipVideoId?.let { id ->
                    failedClipIds = failedClipIds + id
                    onPlayableClipInvalidated(id)
                }
            }
        }
        warmedVideoPlayer?.addListener(listener)
        onDispose { warmedVideoPlayer?.removeListener(listener) }
    }

    // A verified YouTube Music pairing is enough to reveal Son/Vidéo:
    // do NOT hide the buttons until the muted video player has finished
    // preparing. A direct, unpaired track still needs a playable stream.
    val hasPairedVideo = isVideoSong || companionVideoId != null || knownPlayableClipId != null
    val canShowVideo = PlayerVideoModePolicy.clipSelectable(
        youtubeTrack = isYouTubeTrack,
        clipId = clipVideoId,
        unavailableClipId = clipVideoId?.takeIf { it in failedClipIds },
        hasResolvedStream = playableStream != null,
        hasOfficialPair = hasPairedVideo,
        streamLookupFinished = videoStreamLookupFinished,
    )
    val showModeBar = PlayerVideoModePolicy.showSelector(
        videoModeEnabled, isExpanded,
        canShowVideo && !isCasting && !isLocalOrUploaded,
    )
    // Do not flash "En cours de lecture" over the selector while a clip is
    // being identified. Fall back to the classic header only after checking.
    val checkingForClip = videoModeEnabled && isExpanded && isYouTubeTrack &&
        !isLocalOrUploaded && !isCasting && !showModeBar &&
        (!counterpartLookupDone || (clipVideoId != null && !videoStreamLookupFinished))
    val headerMode = PlayerVideoModePolicy.headerMode(
        selectorVisible = showModeBar,
        waitingForClip = checkingForClip,
    )
    LaunchedEffect(videoModeEnabled, isExpanded, isCasting, isLocalOrUploaded, videoId) {
        // Do not erase the saved video choice just because an Activity is
        // temporarily collapsed during portrait/landscape reconstruction.
        // The media metadata may temporarily be null while the Activity is
        // rebuilt in landscape. A missing ID at that instant must not erase
        // the user's saved video selection.
        if (!videoModeEnabled || isCasting || isLocalOrUploaded) {
            onShowVideoChange(false)
        }
    }
    LaunchedEffect(showVideo, clipVideoId, videoStreamLookupFinished, showModeBar) {
        // Keep the user's video choice while a rotated layout re-resolves its
        // stream. Reset only after a completed, unsuccessful lookup.
        // If both the paired clip and the direct fallback have failed,
        // restore audio mode instead of trapping the player on a spinner.
        if (showVideo && !showModeBar &&
            ((clipVideoId == null && counterpartLookupDone) ||
                (clipVideoId == videoId && videoStreamLookupFinished))
        ) {
            onShowVideoChange(false)
        }
    }

    val playerImage: @Composable () -> Unit = {
        if (showVideo && showModeBar && videoId != null && clipVideoId != null &&
            warmedVideoPlayer != null
        ) {
            VideoOnlySurface(
                videoId = clipVideoId,
                audioId = videoId,
                videoPlayer = warmedVideoPlayer!!,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                onUnavailable = {
                    clipVideoId?.let { id ->
                        failedClipIds = failedClipIds + id
                        onPlayableClipInvalidated(id)
                    }
                    // Keep the choice while trying the same-track video fallback;
                    // close video only when there is no other playable candidate.
                    if (clipVideoId == videoId) {
                        onShowVideoChange(false)
                        Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showVideo && showModeBar) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // A single top bar owns the entire expanded player header.
            // Thumbnail must not draw a second "En cours de lecture" label or Cast.
            artwork(isExpanded)
        }
    }
    val topBar: @Composable (Modifier) -> Unit = { barModifier ->
        PlayerModeTopBar(
            audioLabel = coverLabel,
            videoLabel = videoLabel,
            showVideo = showVideo,
            headerMode = headerMode,
            selectorTheme = selectorTheme,
            appDarkTheme = appDarkTheme,
            modeTextColor = modeTextColor,
            onAudioClick = {
                warmedVideoPlayer?.playWhenReady = false
                onShowVideoChange(false)
            },
            onVideoClick = {
                warmedVideoPlayer?.let { player ->
                    // Seek and start the already-buffered video before the
                    // AndroidView is attached, rather than waiting for the
                    // first 350 ms synchronisation poll.
                    val audioPosition = runCatching { playerConnection.player.currentPosition }
                        .getOrDefault(0L).coerceAtLeast(0L)
                    if (abs(player.currentPosition - audioPosition) > 700L) {
                        player.seekTo(audioPosition)
                    }
                    player.playWhenReady = isPlaying
                }
                onShowVideoChange(true)
            },
            onCollapse = onCollapse,
            modifier = barModifier,
        )
    }

    // One header at a time: Son/Vidéo when a clip is buffered, otherwise
    // "En cours de lecture" in the exact same reserved position.
    // The status inset applies once, regardless of display mode.
    Column(
        // Expanded player: this unified header owns the system top inset.
        // Collapsed player: Thumbnail owns its legacy inset instead. Applying
        // both caused a doubled gap and misaligned "En cours de lecture".
        modifier = modifier.then(
            if (isLandscape || !isExpanded) Modifier else Modifier.statusBarsPadding(),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isExpanded) {
            topBar(Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            playerImage()
        }
    }
}

/**
 * Single top toolbar. When Son/Vidéo is available, its icons replace the
 * classic header; NO extra title is duplicated over the real track title
 * in the player's information section below the artwork.
 */
@Composable
private fun PlayerModeTopBar(
    audioLabel: String,
    videoLabel: String,
    showVideo: Boolean,
    headerMode: PlayerVideoModePolicy.HeaderMode,
    selectorTheme: PlayerVideoThemeMode,
    appDarkTheme: Boolean,
    modeTextColor: Color,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = stringResource(R.string.player_collapse_label),
                tint = modeTextColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (headerMode) {
                PlayerVideoModePolicy.HeaderMode.SELECTOR -> PlayerModePills(
                    audioLabel = audioLabel,
                    videoLabel = videoLabel,
                    showVideo = showVideo,
                    onAudioClick = onAudioClick,
                    onVideoClick = onVideoClick,
                    selectorTheme = selectorTheme,
                    appDarkTheme = appDarkTheme,
                    modifier = Modifier.width(136.dp),
                )
                PlayerVideoModePolicy.HeaderMode.CLASSIC -> Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.labelMedium,
                    color = modeTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Leave the center clear rather than flashing the old label
                // over Son/Vidéo during a counterpart/stream lookup.
                PlayerVideoModePolicy.HeaderMode.LOADING -> Unit
            }
        }
        // Exactly the same width as Collapse: the switch is centered on
        // the *screen*, not offset by the action buttons on either side.
        CastButton(
            modifier = Modifier.size(48.dp),
            tintColor = modeTextColor,
            buttonSize = 48.dp,
        )
    }
}

@Composable
private fun PlayerModePills(
    audioLabel: String,
    videoLabel: String,
    showVideo: Boolean,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    selectorTheme: PlayerVideoThemeMode,
    appDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    // Only monochrome light/dark variants. DISABLED removes the custom
    // pill treatment without removing the functional Son/Vidéo selector.
    val dark = when (selectorTheme) {
        PlayerVideoThemeMode.DARK -> true
        PlayerVideoThemeMode.LIGHT -> false
        PlayerVideoThemeMode.AUTO, PlayerVideoThemeMode.DISABLED -> appDarkTheme
    }
    val shape = RoundedCornerShape(22.dp)
    val barBackground = when {
        selectorTheme == PlayerVideoThemeMode.DISABLED -> Color.Transparent
        dark -> Color(0xFF242426)
        else -> Color(0xFFEAEAEC)
    }
    val selectedBackground = when {
        selectorTheme == PlayerVideoThemeMode.DISABLED && dark -> Color.White.copy(alpha = 0.16f)
        selectorTheme == PlayerVideoThemeMode.DISABLED -> Color.Black.copy(alpha = 0.10f)
        dark -> Color(0xFFF5F5F5)
        else -> Color(0xFF242426)
    }
    val selectedForeground = when {
        selectorTheme == PlayerVideoThemeMode.DISABLED && dark -> Color.White
        selectorTheme == PlayerVideoThemeMode.DISABLED -> Color.Black
        dark -> Color(0xFF1B1B1D)
        else -> Color.White
    }
    val unselectedForeground = if (dark) Color.White.copy(alpha = 0.85f)
        else Color.Black.copy(alpha = 0.78f)
    val outlineColor = when {
        selectorTheme == PlayerVideoThemeMode.DISABLED -> Color.Transparent
        dark -> Color.White.copy(alpha = 0.16f)
        else -> Color.Black.copy(alpha = 0.10f)
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(barBackground)
            .border(0.75.dp, outlineColor, shape)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        listOf(false to audioLabel, true to videoLabel).forEach { (video, label) ->
            val selected = showVideo == video
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (selected) selectedBackground else Color.Transparent)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = if (video) onVideoClick else onAudioClick,
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (video) R.drawable.player_video_mode else R.drawable.player_audio_mode,
                    ),
                    contentDescription = label,
                    tint = if (selected) selectedForeground else unselectedForeground,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoOnlySurface(
    videoId: String,
    audioId: String,
    videoPlayer: ExoPlayer,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The stream is already resolved and the muted ExoPlayer is already
    // prepared above. Switching tabs only attaches its TextureView.
    var videoReady by remember(videoPlayer) { mutableStateOf(videoPlayer.playbackState == Player.STATE_READY) }
    DisposableEffect(videoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                videoReady = playbackState == Player.STATE_READY
            }

            override fun onPlayerError(error: PlaybackException) {
                onUnavailable()
            }
        }
        videoPlayer.addListener(listener)
        onDispose { videoPlayer.removeListener(listener) }
    }
    // Stop decoding as soon as the visible video surface disappears.
    // Keep the preloaded player alive for a quick return to Vidéo.
    DisposableEffect(videoPlayer) {
        onDispose { videoPlayer.playWhenReady = false }
    }

    LaunchedEffect(videoPlayer, audioId, isPlaying) {
        while (isActive) {
            val audioPlayer = runCatching { playerConnection.player }.getOrNull()
            if (audioPlayer?.currentMediaItem?.mediaId == audioId) {
                val audioPosition = audioPlayer.currentPosition.coerceAtLeast(0L)
                if (abs(videoPlayer.currentPosition - audioPosition) > 700L) {
                    videoPlayer.seekTo(audioPosition)
                }
                videoPlayer.playWhenReady = isPlaying
            } else {
                videoPlayer.playWhenReady = false
            }
            delay(350L)
        }
    }

    var controlsVisible by remember(videoPlayer) { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(2_500L)
            controlsVisible = false
        }
    }
    val playbackLabel = stringResource(if (isPlaying) R.string.player_pause else R.string.play)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val videoWidth = minOf(maxWidth, maxHeight * (16f / 9f))
        Box(
            modifier = Modifier
                .size(videoWidth, videoWidth * (9f / 16f))
                .clip(RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { TextureView(it) },
                modifier = Modifier.fillMaxSize(),
                update = { textureView ->
                    // Reconnect a reused TextureView to the NEW ExoPlayer when
                    // changing tracks, otherwise the clip can remain black.
                    if (textureView.tag !== videoPlayer) {
                        videoPlayer.setVideoTextureView(textureView)
                        textureView.tag = videoPlayer
                    }
                },
            )
            // A transparent touch target ABOVE the Android view ensures one tap
            // really pauses/resumes the underlying audio service. The video-only
            // session remains muted and follows the service; no duplicate sound.
            Box(
                Modifier.matchParentSize().clickable(onClickLabel = playbackLabel) {
                    playerConnection.togglePlayPause()
                    controlsVisible = true
                },
            )
            AnimatedVisibility(
                visible = videoReady && (!isPlaying || controlsVisible),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(
                    onClick = {
                        playerConnection.togglePlayPause()
                        controlsVisible = true
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.Black.copy(alpha = 0.46f), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = playbackLabel,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            if (!videoReady) CircularProgressIndicator()
        }
    }
}
