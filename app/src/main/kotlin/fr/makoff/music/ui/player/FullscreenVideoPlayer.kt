package fr.makoff.music.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import fr.makoff.music.R
import fr.makoff.music.playback.PlayerConnection
import kotlinx.coroutines.delay

/**
 * Full landscape cinema canvas, not the regular two-column music player.
 * The original audio session still exclusively owns audio and all commands.
 * The same muted video decoder is reused across portrait/landscape.
 */
@Composable
internal fun FullscreenVideoPlayer(
    audioId: String,
    videoPlayer: ExoPlayer,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    title: String,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember(videoPlayer) { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3_000L)
            controlsVisible = false
        }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        VideoOnlySurface(
            audioId = audioId,
            videoPlayer = videoPlayer,
            playerConnection = playerConnection,
            isPlaying = isPlaying,
            visible = true,
            fullscreen = true,
            onUnavailable = onUnavailable,
            onSurfaceTap = { controlsVisible = !controlsVisible },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onPrevious(); controlsVisible = true },
                    enabled = canSkipPrevious,
                    modifier = Modifier.size(58.dp).background(Color.Black.copy(alpha = 0.50f), CircleShape),
                ) {
                    Icon(painterResource(R.drawable.skip_previous),
                        contentDescription = stringResource(R.string.previous),
                        tint = if (canSkipPrevious) Color.White else Color.LightGray,
                        modifier = Modifier.size(34.dp))
                }
                IconButton(
                    onClick = { playerConnection.togglePlayPause(); controlsVisible = true },
                    modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.58f), CircleShape),
                ) {
                    Icon(painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.play),
                        tint = Color.White,
                        modifier = Modifier.size(40.dp))
                }
                IconButton(
                    onClick = { onNext(); controlsVisible = true },
                    enabled = canSkipNext,
                    modifier = Modifier.size(58.dp).background(Color.Black.copy(alpha = 0.50f), CircleShape),
                ) {
                    Icon(painterResource(R.drawable.skip_next),
                        contentDescription = stringResource(R.string.next),
                        tint = if (canSkipNext) Color.White else Color.LightGray,
                        modifier = Modifier.size(34.dp))
                }
            }
        }

        // Exit is always available, even after the transport controls fade.
        IconButton(
            onClick = onExit,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                .size(48.dp).background(Color.Black.copy(alpha = 0.60f), CircleShape),
        ) {
            Icon(painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.player_collapse_label),
                tint = Color.White, modifier = Modifier.size(26.dp))
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 20.dp, end = 72.dp),
        ) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}
