package fr.makoff.music.ui.player

import android.view.TextureView
import android.widget.Toast
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
@Composable
internal fun PlayerArtworkSwitcher(
    videoId: String?,
    isVideoSong: Boolean,
    isCasting: Boolean,
    isExpanded: Boolean,
    isPlaying: Boolean,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.player_video_unavailable)
    val coverLabel = stringResource(R.string.player_cover_label)
    val videoLabel = stringResource(R.string.player_video_label)
    val canShowVideo = !videoId.isNullOrBlank() && isVideoSong && !isCasting
    var showVideo by remember(videoId) { mutableStateOf(false) }

    LaunchedEffect(isExpanded, canShowVideo) {
        if (!isExpanded || !canShowVideo) showVideo = false
    }

    // YouTube Music-style mode selector sits ABOVE the artwork, never over the cover
    // or the video. The thumbnail keeps its own gestures, header and Cast control.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (canShowVideo && isExpanded) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 10.dp)
                    .liquidGlass(RoundedCornerShape(28.dp), chrome = true)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { showVideo = false },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (!showVideo) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (!showVideo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(coverLabel, fontWeight = if (!showVideo) FontWeight.SemiBold else FontWeight.Normal)
                }
                TextButton(
                    onClick = { showVideo = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (showVideo) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (showVideo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(videoLabel, fontWeight = if (showVideo) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (showVideo && canShowVideo && isExpanded && videoId != null) {
                MusicVideoArtwork(
                    videoId = videoId,
                    isPlaying = isPlaying,
                    playerConnection = playerConnection,
                    onUnavailable = {
                        showVideo = false
                        Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                artwork()
            }
        }
    }
}

@Composable
private fun MusicVideoArtwork(
    videoId: String,
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

    LaunchedEffect(videoPlayer, videoId, isPlaying) {
        while (isActive) {
            val audioPlayer = runCatching { playerConnection.player }.getOrNull()
            if (audioPlayer?.currentMediaItem?.mediaId == videoId) {
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
            factory = { TextureView(it) },
            modifier = Modifier.size(videoWidth, videoWidth * (9f / 16f)),
            update = { videoPlayer.setVideoTextureView(it) },
        )
        if (!videoReady) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
