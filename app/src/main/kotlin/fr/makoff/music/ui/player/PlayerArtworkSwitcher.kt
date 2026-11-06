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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import fr.makoff.music.R
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
    isVideoSong: Boolean,
    isLocalOrUploaded: Boolean,
    songTitle: String?,
    artistNames: List<String>,
    isCasting: Boolean,
    isExpanded: Boolean,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    modeTextColor: Color,
    onCollapse: () -> Unit,
    onOpenMenu: () -> Unit,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
    artwork: @Composable (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.player_video_unavailable)
    val coverLabel = stringResource(R.string.player_cover_label)
    val videoLabel = stringResource(R.string.player_video_label)
    var companionVideoId by remember(videoId) { mutableStateOf<String?>(null) }
    var showVideo by remember(videoId) { mutableStateOf(false) }
    val isYouTubeTrack = videoId != null && videoId.length == 11 &&
        videoId.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    // Only use YouTube Music's official counterpart, not a guessed search result.
    LaunchedEffect(videoId, videoModeEnabled, isVideoSong, isLocalOrUploaded, isCasting, isExpanded, songTitle, artistNames) {
        companionVideoId = null
        if (!videoModeEnabled || !isYouTubeTrack || isLocalOrUploaded || isVideoSong || isCasting || !isExpanded) return@LaunchedEffect
        companionVideoId = try {
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
    }

    val clipVideoId = if (isVideoSong) videoId else companionVideoId
    // Do not advertise an unplayable clip. Resolve the actual signed video
    // stream in the background ONLY while the full player is expanded, and
    // reuse it on demand. This also avoids a duplicate network extraction
    // each time the user switches between Son and Vidéo.
    var availableVideoStream by remember(clipVideoId) {
        mutableStateOf<InnerTubeXPlayer.VideoStream?>(null)
    }
    LaunchedEffect(clipVideoId, videoModeEnabled, isExpanded, isCasting, isLocalOrUploaded) {
        val id = clipVideoId
        if (!videoModeEnabled || id.isNullOrBlank() || !isExpanded || isCasting || isLocalOrUploaded) {
            availableVideoStream = null
            return@LaunchedEffect
        }
        delay(250L) // avoid extraction storms when rapidly skipping tracks
        availableVideoStream = try {
            withContext(Dispatchers.IO) { InnerTubeXPlayer.resolveVideoStream(id) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    var unavailableClipId by remember(videoId) { mutableStateOf<String?>(null) }
    val canShowVideo = availableVideoStream != null && !isCasting &&
        isYouTubeTrack && !isLocalOrUploaded && clipVideoId != unavailableClipId
    // Never reserve a Son/Vidéo bar for offline files or tracks without a playable clip.
    // Restoring the classic header also avoids a second status-bar inset above the cover.
    val showModeBar = PlayerVideoModePolicy.showSelector(videoModeEnabled, isExpanded, canShowVideo)
    LaunchedEffect(showModeBar) {
        if (!showModeBar) showVideo = false
    }

    val playerImage: @Composable () -> Unit = {
        if (showVideo && showModeBar && videoId != null && clipVideoId != null) {
            VideoOnlySurface(
                videoId = clipVideoId,
                audioId = videoId,
                stream = availableVideoStream!!,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                onUnavailable = {
                    unavailableClipId = clipVideoId
                    showVideo = false
                    Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // With Son/Vidéo disabled or no clip, Thumbnail owns the original
            // "En cours de lecture" header and its status-bar inset.
            artwork(showModeBar)
        }
    }
    val topBar: @Composable (Modifier) -> Unit = { barModifier ->
        PlayerModeTopBar(
            audioLabel = coverLabel,
            videoLabel = videoLabel,
            showVideo = showVideo,
            showModeSelector = showModeBar,
            modeTextColor = modeTextColor,
            onAudioClick = { showVideo = false },
            onVideoClick = { showVideo = true },
            onCollapse = onCollapse,
            onOpenMenu = onOpenMenu,
            modifier = barModifier,
        )
    }

    // Only the actual Son/Vidéo bar owns the portrait system inset.
    // Otherwise Thumbnail restores the legacy header and handles its inset
    // itself. Never show both headers over each other or above a video.
    Column(
        modifier = modifier.then(if (isLandscape || !showModeBar) Modifier else Modifier.statusBarsPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showModeBar) {
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
 * Header from the supplied YouTube Music reference: collapse on the left,
 * two compact centered pills, then Cast and the three-dot menu on the right.
 * Use the active background style rather than assuming the app is always dark.
 */
@Composable
private fun PlayerModeTopBar(
    audioLabel: String,
    videoLabel: String,
    showVideo: Boolean,
    showModeSelector: Boolean,
    modeTextColor: Color,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    onCollapse: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = stringResource(R.string.player_collapse_label),
                tint = modeTextColor,
                modifier = Modifier.size(24.dp),
            )
        }

        // The entire Son / Vidéo selector disappears for songs without a clip.
        // Keep the down arrow, Cast and the player menu usable in all cases.
        if (showModeSelector) {
            PlayerModePills(
                audioLabel = audioLabel,
                videoLabel = videoLabel,
                showVideo = showVideo,
                onAudioClick = onAudioClick,
                onVideoClick = onVideoClick,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            CastButton(
                modifier = Modifier.size(40.dp),
                tintColor = modeTextColor,
                buttonSize = 40.dp,
            )
            IconButton(onClick = onOpenMenu, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = stringResource(R.string.more_options),
                    tint = modeTextColor,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerModePills(
    audioLabel: String,
    videoLabel: String,
    showVideo: Boolean,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fixed YouTube Music-inspired palette: this toolbar MUST keep the
    // same charcoal / peach appearance across light, dark, gradient, AMOLED
    // and Liquid Glass app themes. The rest of the player remains themed.
    val shape = RoundedCornerShape(22.dp)
    val barBackground = Color(0xFF30272B)
    val selectedBackground = Color(0xFFF2A17E)
    val selectedForeground = Color(0xFF251B1D)
    val unselectedForeground = Color(0xFFF8F5F6)

    Row(
        modifier = modifier
            .clip(shape)
            .background(barBackground)
            .border(0.75.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        listOf(false to audioLabel, true to videoLabel).forEach { (video, label) ->
            val selected = showVideo == video
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .defaultMinSize(minWidth = 48.dp)
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
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) selectedForeground else unselectedForeground,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun VideoOnlySurface(
    videoId: String,
    audioId: String,
    stream: InnerTubeXPlayer.VideoStream,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val videoPlayer = remember(stream) {
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(stream.headers)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                volume = 0f // Never duplicate the service's audio track.
                setMediaItem(MediaItem.Builder().setUri(stream.url).setMimeType(stream.mimeType).build())
                prepare()
            }
    }

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
    DisposableEffect(videoPlayer) {
        onDispose {
            videoPlayer.clearVideoSurface()
            videoPlayer.release()
        }
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
