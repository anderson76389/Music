package fr.makoff.music.ui.player

import android.view.TextureView
import android.view.View
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import fr.makoff.music.ui.component.CastButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.runtime.CompositionLocalProvider
import fr.makoff.music.ui.theme.LocalLiquidGlassEnabled
import fr.makoff.music.ui.theme.liquidGlass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import fr.makoff.music.R
import fr.makoff.music.constants.PlayerVideoThemeMode
import fr.makoff.music.playback.PlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    artworkThemeColor: Color,
    customThemeColor: Color,
    videoPreview: PlayerVideoPreview,
    videoPlayer: ExoPlayer?,
    thumbnailUrl: String?,
    songTitle: String?,
    onVideoUnavailable: (String) -> Unit,
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
    // Video stream extraction and buffering now belong to BottomSheetPlayer,
    // which survives collapse. Never show Son/Vidéo for a merely guessed clip.
    val clip = videoPreview.clip
    val hasPlayableClip = PlayerVideoModePolicy.clipSelectable(
        youtubeTrack = videoId != null,
        clipId = clip?.id,
        unavailableClipId = null,
        hasResolvedStream = clip != null && videoPlayer != null,
    )
    val showModeBar = PlayerVideoModePolicy.showSelector(
        enabled = videoModeEnabled && !isCasting,
        expanded = isExpanded,
        hasPlayableClip = hasPlayableClip,
    )
    // Maintain the previous single-header policy: never flash the old title
    // behind the selector, even during the background clip availability probe.
    val headerMode = PlayerVideoModePolicy.headerMode(
        selectorVisible = showModeBar,
        waitingForClip = videoModeEnabled && !videoPreview.lookupFinished,
        modeEnabled = videoModeEnabled && isExpanded,
    )
    LaunchedEffect(showVideo, videoPreview.lookupFinished, showModeBar, videoModeEnabled, isCasting, hasPlayableClip, videoPlayer) {
        // A collapsed sheet or a brief layout teardown during orientation
        // change is NOT an unavailable video. Keep the selected tab for the
        // same song instead of unexpectedly switching back to Son.
        if (!showModeBar) videoPlayer?.playWhenReady = false
        if (showVideo && PlayerVideoModePolicy.shouldResetVideoSelection(
                modeEnabled = videoModeEnabled, casting = isCasting,
                lookupFinished = videoPreview.lookupFinished,
                hasPlayableClip = hasPlayableClip,
            )
        ) {
            onShowVideoChange(false)
        }
    }

    val playerImage: @Composable () -> Unit = {
        if (showModeBar && videoId != null && clip != null && videoPlayer != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Son and Vidéo are TWO exclusive screens, not two layers. An
                // Android TextureView doesn't reliably obey Compose alpha(0f);
                // leaving the artwork underneath made the video look pasted on
                // the cover (especially on the foldable / landscape player).
                // Compose the cover ONLY in Son, but keep the TextureView mounted
                // in both modes to avoid re-creating the video decoder on taps.
                if (PlayerVideoModePolicy.showSongArtwork(showVideo)) {
                    artwork(PlayerVideoModePolicy.suppressLegacyThumbnailHeader(videoModeEnabled, isExpanded))
                }
                VideoOnlySurface(
                    audioId = videoId,
                    videoPlayer = videoPlayer,
                    thumbnailUrl = thumbnailUrl,
                    isPlaying = isPlaying,
                    visible = showVideo,
                    playerConnection = playerConnection,
                    onUnavailable = { onVideoUnavailable(clip.id) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            artwork(PlayerVideoModePolicy.suppressLegacyThumbnailHeader(videoModeEnabled, isExpanded))
        }
    }
    val topBar: @Composable (Modifier) -> Unit = { barModifier ->
        PlayerModeTopBar(
            audioLabel = coverLabel,
            videoLabel = videoLabel,
            songTitle = songTitle,
            showVideo = showVideo,
            headerMode = headerMode,
            selectorTheme = selectorTheme,
            appDarkTheme = appDarkTheme,
            artworkThemeColor = artworkThemeColor,
            customThemeColor = customThemeColor,
            modeTextColor = modeTextColor,
            onAudioClick = {
                // The VideoOnlySurface owns play/pause and clock sync, not the
                // tab's click handler. Keep the decoder warm briefly on fast
                // Son/Vidéo/Son gestures without a double seek or black frame.
                onShowVideoChange(false)
            },
            onVideoClick = {
                if (videoPlayer != null && showModeBar) {
                    onShowVideoChange(true)
                }
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
    songTitle: String?,
    showVideo: Boolean,
    headerMode: PlayerVideoModePolicy.HeaderMode,
    selectorTheme: PlayerVideoThemeMode,
    appDarkTheme: Boolean,
    artworkThemeColor: Color,
    customThemeColor: Color,
    modeTextColor: Color,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Place the selector in the absolute horizontal center. A Row with a
    // weighted center shifts when Cast is hidden or resized by the host.
    Box(
        modifier = modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
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
                artworkThemeColor = artworkThemeColor,
                customThemeColor = customThemeColor,
                modifier = Modifier.width(120.dp),
            )
            PlayerVideoModePolicy.HeaderMode.CLASSIC -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 54.dp),
            ) {
                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.labelMedium,
                    color = modeTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                songTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = modeTextColor.copy(alpha = 0.82f),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 1400,
                            velocity = 32.dp,
                        ),
                    )
                }
            }
            PlayerVideoModePolicy.HeaderMode.LOADING -> Unit
        }
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
        CastButton(
            modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
            tintColor = modeTextColor,
            buttonSize = 48.dp,
        )
    }
}

@Composable
internal fun PlayerModePills(
    audioLabel: String,
    videoLabel: String,
    showVideo: Boolean,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit,
    selectorTheme: PlayerVideoThemeMode,
    appDarkTheme: Boolean,
    artworkThemeColor: Color,
    customThemeColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val glass = selectorTheme == PlayerVideoThemeMode.LIQUID_GLASS
    val activeThemeColor = if (selectorTheme == PlayerVideoThemeMode.CUSTOM) customThemeColor else artworkThemeColor
    val coverDark = activeThemeColor.luminance() < 0.48f
    val dark = when (selectorTheme) {
        PlayerVideoThemeMode.DARK, PlayerVideoThemeMode.OLED -> true
        PlayerVideoThemeMode.LIGHT -> false
        PlayerVideoThemeMode.COVER, PlayerVideoThemeMode.CUSTOM -> coverDark
        PlayerVideoThemeMode.AUTO, PlayerVideoThemeMode.DISABLED, PlayerVideoThemeMode.LIQUID_GLASS -> appDarkTheme
    }
    val shape = RoundedCornerShape(18.dp)
    // COVER samples this track's artwork even when the player background is
    // not a gradient. OLED is true black; AUTO continues to follow the app.
    val barBackground = when (selectorTheme) {
        PlayerVideoThemeMode.DISABLED, PlayerVideoThemeMode.LIQUID_GLASS -> Color.Transparent
        PlayerVideoThemeMode.AUTO -> colors.surfaceContainerHigh
        PlayerVideoThemeMode.COVER, PlayerVideoThemeMode.CUSTOM -> activeThemeColor.copy(alpha = 0.94f)
        PlayerVideoThemeMode.DARK -> Color(0xFF242426)
        PlayerVideoThemeMode.OLED -> Color.Black
        PlayerVideoThemeMode.LIGHT -> Color(0xFFEAEAEC)
    }
    val selectedBackground = when (selectorTheme) {
        PlayerVideoThemeMode.DISABLED -> if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)
        PlayerVideoThemeMode.AUTO -> colors.primaryContainer
        PlayerVideoThemeMode.COVER, PlayerVideoThemeMode.CUSTOM -> if (dark) Color.White.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.18f)
        PlayerVideoThemeMode.DARK -> Color(0xFFF5F5F5)
        PlayerVideoThemeMode.OLED -> Color.White.copy(alpha = 0.20f)
        PlayerVideoThemeMode.LIGHT -> Color(0xFF242426)
        PlayerVideoThemeMode.LIQUID_GLASS -> Color.Transparent
    }
    val selectedForeground = when (selectorTheme) {
        PlayerVideoThemeMode.DISABLED -> if (dark) Color.White else Color.Black
        PlayerVideoThemeMode.AUTO -> colors.onPrimaryContainer
        PlayerVideoThemeMode.COVER, PlayerVideoThemeMode.CUSTOM, PlayerVideoThemeMode.OLED ->
            if (dark) Color.White else Color(0xFF131313)
        PlayerVideoThemeMode.DARK -> Color(0xFF1B1B1D)
        PlayerVideoThemeMode.LIGHT -> Color.White
        PlayerVideoThemeMode.LIQUID_GLASS -> if (dark) Color.White else Color(0xFF17191D)
    }
    val unselectedForeground = when (selectorTheme) {
        PlayerVideoThemeMode.AUTO -> colors.onSurfaceVariant
        else -> if (dark) Color.White.copy(alpha = 0.86f) else Color.Black.copy(alpha = 0.78f)
    }
    val outlineColor = when (selectorTheme) {
        PlayerVideoThemeMode.DISABLED, PlayerVideoThemeMode.LIQUID_GLASS -> Color.Transparent
        PlayerVideoThemeMode.AUTO -> colors.outlineVariant
        PlayerVideoThemeMode.COVER, PlayerVideoThemeMode.CUSTOM, PlayerVideoThemeMode.OLED, PlayerVideoThemeMode.DARK -> Color.White.copy(alpha = 0.18f)
        PlayerVideoThemeMode.LIGHT -> Color.Black.copy(alpha = 0.10f)
    }
    val animatedBarColor by animateColorAsState(
        targetValue = barBackground, animationSpec = tween(170), label = "modeSelectorBackground",
    )
    val animatedSelectedColor by animateColorAsState(
        targetValue = selectedBackground, animationSpec = tween(170), label = "modeSelectorSelected",
    )

    // Local override allows glass for the pill ALONE even if the app's global
    // Liquid Glass theme is disabled. The player supplies the shared HazeState.
    CompositionLocalProvider(LocalLiquidGlassEnabled provides (LocalLiquidGlassEnabled.current || glass)) {
        Row(
            modifier = modifier
                .then(
                    if (glass) Modifier.liquidGlass(shape = shape, chrome = true)
                    else Modifier.clip(shape).background(animatedBarColor).border(0.75.dp, outlineColor, shape)
                )
                .padding(1.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            listOf(false to audioLabel, true to videoLabel).forEach { (video, label) ->
                val selected = showVideo == video
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .then(
                            if (glass && selected) {
                                // Refract the artwork below the active tab, too:
                                // no permanently painted, opaque white highlight.
                                Modifier.liquidGlass(
                                    shape = RoundedCornerShape(16.dp),
                                    chrome = true,
                                    showBorder = true,
                                )
                            } else {
                                Modifier.clip(RoundedCornerShape(19.dp))
                                    .background(if (selected) animatedSelectedColor else Color.Transparent)
                            },
                        )
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = if (video) onVideoClick else onAudioClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (video) R.drawable.player_video_mode else R.drawable.player_audio_mode,
                        ),
                        contentDescription = label,
                        tint = if (selected) selectedForeground else unselectedForeground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoOnlySurface(
    audioId: String,
    videoPlayer: ExoPlayer,
    thumbnailUrl: String?,
    playerConnection: PlayerConnection,
    isPlaying: Boolean,
    visible: Boolean,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The stream is already resolved and the muted ExoPlayer is already
    // prepared above. Switching tabs only attaches its TextureView.
    var videoReady by remember(videoPlayer) { mutableStateOf(videoPlayer.playbackState == Player.STATE_READY) }
    // A READY decoder can still take a frame to attach to a TextureView.
    // Keep the song artwork visible until the FIRST REAL video frame renders.
    var firstFrameRendered by remember(videoPlayer) { mutableStateOf(false) }
    DisposableEffect(videoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                videoReady = playbackState == Player.STATE_READY
            }

            override fun onPlayerError(error: PlaybackException) {
                onUnavailable()
            }

            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
        }
        videoPlayer.addListener(listener)
        onDispose { videoPlayer.removeListener(listener) }
    }
    // The prepared ExoPlayer is shared between portrait and landscape.
    // Releasing a UI surface must never stop the *new* view's player or call
    // clearVideoSurface() globally: that races with orientation reattachment.
    val lastPeriodicSeekMs = remember(videoPlayer) { longArrayOf(0L) }

    // One owner for tab switching, play/pause and position alignment.
    // Repeated rapid tab taps must not issue two competing seekTo() calls.
    LaunchedEffect(videoPlayer, audioId, isPlaying, visible) {
        val audioPlayer = playerConnection.player
        val sameSong = audioPlayer.currentMediaItem?.mediaId == audioId
        if (!visible || !sameSong) {
            if (sameSong && !visible && isPlaying && videoPlayer.playWhenReady) {
                // Keep the muted decoder warm just long enough for a rapid
                // return to video, then stop consuming video network/CPU.
                delay(600L)
            }
            videoPlayer.playWhenReady = false
            return@LaunchedEffect
        }

        val audioPosition = audioPlayer.currentPosition.coerceAtLeast(0L)
        if (PlayerVideoModePolicy.shouldSeekOnVideoSelection(audioPosition, videoPlayer.currentPosition)) {
            videoPlayer.seekTo(audioPosition)
            // A seek within existing buffer can remain READY and fire no state
            // change callback: never leave a permanent loading overlay.
            videoReady = videoPlayer.playbackState == Player.STATE_READY
            lastPeriodicSeekMs[0] = android.os.SystemClock.elapsedRealtime()
        }
        videoPlayer.playWhenReady = isPlaying
        while (isActive) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (PlayerVideoModePolicy.shouldCorrectVideoDrift(
                    audioPositionMs = audioPlayer.currentPosition,
                    videoPositionMs = videoPlayer.currentPosition,
                    videoReady = videoPlayer.playbackState == Player.STATE_READY,
                    timeSinceLastSeekMs = now - lastPeriodicSeekMs[0],
                )
            ) {
                videoPlayer.seekTo(audioPlayer.currentPosition.coerceAtLeast(0L))
                videoReady = videoPlayer.playbackState == Player.STATE_READY
                lastPeriodicSeekMs[0] = now
            }
            delay(1_500L)
        }
    }

    // Propagate explicit seeks immediately rather than waiting for the
    // periodic 350 ms drift correction (e.g. a fast drag of the audio slider).
    DisposableEffect(videoPlayer, audioId, playerConnection, visible) {
        val audio = playerConnection.player
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (visible && reason == Player.DISCONTINUITY_REASON_SEEK &&
                    audio.currentMediaItem?.mediaId == audioId &&
                    PlayerVideoModePolicy.shouldSeekOnVideoSelection(
                        newPosition.positionMs, videoPlayer.currentPosition, toleranceMs = 500L,
                    )
                ) {
                    videoPlayer.seekTo(newPosition.positionMs.coerceAtLeast(0L))
                    videoReady = videoPlayer.playbackState == Player.STATE_READY
                    lastPeriodicSeekMs[0] = android.os.SystemClock.elapsedRealtime()
                }
            }
        }
        audio.addListener(listener)
        onDispose { audio.removeListener(listener) }
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
                .clip(RoundedCornerShape(18.dp))
                .then(if (visible) Modifier.background(Color.Black) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { TextureView(it).apply { visibility = View.INVISIBLE } },
                modifier = Modifier.fillMaxSize(),
                update = { textureView ->
                    // Native visibility, not Compose alpha: when Son is active
                    // the video must not draw a single pixel over the cover.
                    textureView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                    if (textureView.tag !== videoPlayer) {
                        videoPlayer.setVideoTextureView(textureView)
                        textureView.tag = videoPlayer
                    }
                },
                onRelease = { textureView ->
                    // Conditional clear never detaches a replacement surface
                    // attached by the landscape/portrait branch meanwhile.
                    if (textureView.tag === videoPlayer) {
                        videoPlayer.clearVideoTextureView(textureView)
                        textureView.tag = null
                    }
                },
            )
            AnimatedVisibility(
                visible = visible && (!firstFrameRendered || !videoReady),
                enter = fadeIn(animationSpec = tween(100)),
                exit = fadeOut(animationSpec = tween(180)),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (thumbnailUrl != null) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (!videoReady) CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            // A transparent touch target ABOVE the Android view ensures one tap
            // really pauses/resumes the underlying audio service. The video-only
            // session remains muted and follows the service; no duplicate sound.
            Box(
                Modifier.matchParentSize().clickable(enabled = visible, onClickLabel = playbackLabel) {
                    playerConnection.togglePlayPause()
                    controlsVisible = true
                },
            )
            AnimatedVisibility(
                visible = visible && videoReady && (!isPlaying || controlsVisible),
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
        }
    }
}
