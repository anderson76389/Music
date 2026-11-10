package fr.makoff.music.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import fr.makoff.music.R
import fr.makoff.music.playback.PlayerConnection
import kotlinx.coroutines.delay

/**
 * Fullscreen video shares the existing audio session and queue controls.
 * All overlays (including the close button) follow a single visibility state:
 * tapping empty video space hides them together, and the next tap restores them.
 * No UI command re-creates or seeks the audio player.
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
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onExit: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onExit() }
    var controlsVisible by remember(videoPlayer) { mutableStateOf(true) }
    var interactionCount by remember(videoPlayer) { mutableIntStateOf(0) }
    val toggleControls = {
        controlsVisible = !controlsVisible
        if (controlsVisible) interactionCount++
    }
    val showControlsAfterAction = {
        controlsVisible = true
        // Refresh the auto-hide deadline on every button press, even if already visible.
        interactionCount++
    }
    LaunchedEffect(controlsVisible, isPlaying, interactionCount) {
        if (controlsVisible && isPlaying) {
            delay(3_000L)
            controlsVisible = false
        }
    }

    // Parent handles taps on letterbox/black margins (e.g. foldables).
    // The child handles taps on the video itself, and control buttons consume
    // their own taps without hiding the controls.
    Box(
        modifier = modifier.background(Color.Black).clickable { toggleControls() },
        contentAlignment = Alignment.Center,
    ) {
        VideoOnlySurface(
            audioId = audioId,
            videoPlayer = videoPlayer,
            playerConnection = playerConnection,
            isPlaying = isPlaying,
            visible = true,
            fullscreen = true,
            onUnavailable = onUnavailable,
            onSurfaceTap = toggleControls,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onPrevious(); showControlsAfterAction() },
                    enabled = canSkipPrevious,
                    modifier = Modifier.size(58.dp).background(Color.Black.copy(alpha = 0.50f), CircleShape),
                ) {
                    Icon(painterResource(R.drawable.skip_previous),
                        contentDescription = stringResource(R.string.previous),
                        tint = if (canSkipPrevious) Color.White else Color.LightGray,
                        modifier = Modifier.size(34.dp))
                }
                IconButton(
                    onClick = { playerConnection.togglePlayPause(); showControlsAfterAction() },
                    modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.58f), CircleShape),
                ) {
                    Icon(painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.play),
                        tint = Color.White,
                        modifier = Modifier.size(40.dp))
                }
                IconButton(
                    onClick = { onNext(); showControlsAfterAction() },
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

        // Reuse the player service's own shuffle/repeat state. Never maintain a
        // second queue or playback state for the landscape overlay.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onShuffleToggle(); showControlsAfterAction() },
                    modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.52f), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = stringResource(
                            if (shuffleEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on,
                        ),
                        tint = if (shuffleEnabled) Color.White else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(
                    onClick = { onRepeatToggle(); showControlsAfterAction() },
                    modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.52f), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(
                            if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                        ),
                        contentDescription = stringResource(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> R.string.repeat_mode_off
                            },
                        ),
                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.55f)
                               else Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            IconButton(
                onClick = onExit,
                modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.60f), CircleShape),
            ) {
                Icon(painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.player_collapse_label),
                    tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 20.dp, end = 72.dp),
        ) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}
