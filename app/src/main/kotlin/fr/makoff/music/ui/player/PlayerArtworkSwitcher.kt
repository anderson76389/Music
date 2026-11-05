package fr.makoff.music.ui.player

import android.view.TextureView
import android.widget.Toast
import fr.makoff.music.ui.theme.LocalLiquidGlassEnabled
import fr.makoff.music.ui.component.CastButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
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
import fr.makoff.music.ui.theme.liquidGlass
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
    isVideoSong: Boolean,
    isCasting: Boolean,
    isExpanded: Boolean,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    modeTextColor: Color,
    darkBackdrop: Boolean,
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

    // Only use YouTube Music's official counterpart, not a guessed search result.
    LaunchedEffect(videoId, isVideoSong, isCasting, isExpanded) {
        if (videoId.isNullOrBlank() || isVideoSong || isCasting || !isExpanded) return@LaunchedEffect
        companionVideoId = try {
            withContext(Dispatchers.IO) { YouTube.videoCounterpart(videoId).getOrNull() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    val clipVideoId = if (isVideoSong) videoId else companionVideoId
    val canShowVideo = !clipVideoId.isNullOrBlank() && !isCasting
    // Keep the top bar stable while the counterpart lookup runs. YouTube Music
    // displays the Video option disabled when no matching clip is available.
    // Local and external files keep the original Now Playing header.
    val isYouTubeTrack = videoId != null && videoId.length == 11 &&
        videoId.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    val showModeBar = isExpanded && !isCasting && isYouTubeTrack
    LaunchedEffect(isExpanded, canShowVideo) {
        if (!isExpanded || !canShowVideo) showVideo = false
    }

    val playerImage: @Composable () -> Unit = {
        if (showVideo && showModeBar && videoId != null && clipVideoId != null) {
            MusicVideoArtwork(
                videoId = clipVideoId,
                audioId = videoId,
                isPlaying = isPlaying,
                playerConnection = playerConnection,
                onUnavailable = {
                    showVideo = false
                    Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            artwork(showModeBar)
        }
    }
    val topBar: @Composable (Modifier) -> Unit = { barModifier ->
        PlayerModeTopBar(
            audioLabel = coverLabel,
            videoLabel = videoLabel,
            showVideo = showVideo,
            videoEnabled = canShowVideo,
            darkBackdrop = darkBackdrop,
            modeTextColor = modeTextColor,
            onAudioClick = { showVideo = false },
            onVideoClick = { showVideo = true },
            onCollapse = onCollapse,
            onOpenMenu = onOpenMenu,
            modifier = barModifier,
        )
    }

    if (isLandscape && showModeBar) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            // The landscape parent already accounts for the status-bar inset.
            topBar(Modifier.fillMaxWidth().padding(top = 4.dp))
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                playerImage()
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            playerImage()
            if (showModeBar) {
                topBar(
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                        .padding(top = 4.dp),
                )
            }
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
    videoEnabled: Boolean,
    darkBackdrop: Boolean,
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

        PlayerModePills(
            audioLabel = audioLabel,
            videoLabel = videoLabel,
            showVideo = showVideo,
            videoEnabled = videoEnabled,
            darkBackdrop = darkBackdrop,
            onAudioClick = onAudioClick,
            onVideoClick = onVideoClick,
            modifier = Modifier.align(Alignment.Center),
        )

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
    videoEnabled: Boolean,
    darkBackdrop: Boolean,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalLiquidGlassEnabled.current
    val shape = RoundedCornerShape(22.dp)
    val baseColor =
        if (darkBackdrop) Color.Black.copy(alpha = 0.23f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
    val selectedBackground =
        if (darkBackdrop) Color.White.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.primary
    val selectedForeground =
        if (darkBackdrop) Color.White
        else MaterialTheme.colorScheme.onPrimary
    val unselectedForeground =
        if (darkBackdrop) Color.White.copy(alpha = 0.82f)
        else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .then(
                if (glass) {
                    // Liquid Glass may be enabled with a LIGHT app theme while
                    // the player uses its dark artwork gradient. Preserve glass
                    // refraction but tint its chrome dark enough for white labels.
                    Modifier.liquidGlass(shape, chrome = true, showBorder = true)
                        .background(
                            if (darkBackdrop) Color.Black.copy(alpha = 0.32f)
                            else Color.Transparent,
                            shape,
                        )
                } else {
                    Modifier.clip(shape).background(baseColor).border(
                        0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape,
                    )
                },
            )
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
                    .alpha(if (video && !videoEnabled) 0.4f else 1f)
                    .selectable(
                        selected = selected,
                        enabled = !video || videoEnabled,
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
private fun MusicVideoArtwork(
    videoId: String,
    audioId: String,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var videoStream by remember(videoId) { mutableStateOf<InnerTubeXPlayer.VideoStream?>(null) }

    LaunchedEffect(videoId) {
        val resolved = try {
            withContext(Dispatchers.IO) { InnerTubeXPlayer.resolveVideoStream(videoId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (resolved == null) {
            onUnavailable()
        } else {
            videoStream = resolved
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val stream = videoStream
        if (stream == null) {
            CircularProgressIndicator()
        } else {
            VideoOnlySurface(
                videoId = videoId,
                audioId = audioId,
                stream = stream,
                playerConnection = playerConnection,
                isPlaying = isPlaying,
                onUnavailable = onUnavailable,
                modifier = Modifier.fillMaxSize(),
            )
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

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val videoWidth = minOf(maxWidth, maxHeight * (16f / 9f))
        AndroidView(
            factory = { context ->
                TextureView(context).also(videoPlayer::setVideoTextureView)
            },
            modifier = Modifier.size(videoWidth, videoWidth * (9f / 16f)),
        )
        if (!videoReady) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
