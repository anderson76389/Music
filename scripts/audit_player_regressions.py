#!/usr/bin/env python3
"""Non-regression guardrails for the landscape video overlay and audio controls.

Static checks are intentionally narrow: functional Android tests still need to
exercise real playback, while these checks prevent accidental reintroduction of
blocking disk IO or a permanently visible fullscreen close button.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/kotlin/fr/makoff/music"
fullscreen = (src / "ui/player/FullscreenVideoPlayer.kt").read_text()
player = (src / "ui/player/Player.kt").read_text()
service = (src / "playback/MusicService.kt").read_text()
preview = (src / "ui/player/PlayerVideoPreview.kt").read_text()


def between(text: str, start: str, end: str) -> str:
    assert text.count(start) == 1, f"Missing or ambiguous section: {start}"
    chunk = text.split(start, 1)[1]
    assert end in chunk, f"Missing next section after {start}"
    return chunk.split(end, 1)[0]


# Every overlay component (transport, shuffle/repeat, close button and title)
# must follow the SAME visibility state. No independent, always-on close icon.
assert fullscreen.count("visible = controlsVisible,") == 4, "Fullscreen overlays lost shared visibility"
assert "controlsVisible = !controlsVisible" in fullscreen, "Surface tap cannot toggle overlays"
assert "modifier.background(Color.Black).clickable { toggleControls() }" in fullscreen, "Letterbox black areas must toggle controls"
assert "onSurfaceTap = toggleControls" in fullscreen, "Video and black margins must share a tap handler"
assert "if (controlsVisible) interactionCount++" in fullscreen, "Tap fails to refresh auto-hide"
assert "LaunchedEffect(controlsVisible, isPlaying, interactionCount)" in fullscreen
assert "onClick = onExit" in fullscreen
assert "Alignment.TopEnd" in fullscreen
assert "onShuffleToggle(); showControlsAfterAction()" in fullscreen
assert "onRepeatToggle(); showControlsAfterAction()" in fullscreen
assert "Player.REPEAT_MODE_ONE" in fullscreen and "R.drawable.repeat_one" in fullscreen
assert "R.string.action_shuffle_on" in fullscreen and "R.string.action_shuffle_off" in fullscreen

# Do not create a second playback session or duplicate shuffle/repeat state.
assert "val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()" in player
assert "val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()" in player
assert "onShuffleToggle = {" in player and "playerConnection.player.shuffleModeEnabled" in player
assert "onRepeatToggle = { playerConnection.player.toggleRepeatMode() }" in player
assert "rememberPreparedVideoPlayer(" in player
assert "VideoOnlySurface(" in fullscreen
assert "volume = 0f" in preview, "Video decoder must remain silent"

# A simple mode toggle must NOT serialize every song in the queue, or block
# the main playback thread reading DataStore. The normal periodic, transition
# and shutdown queue saves must remain available.
shuffle = between(service, "override fun onShuffleModeEnabledChanged(", "override fun onRepeatModeChanged(")
repeat = between(service, "override fun onRepeatModeChanged(", "private fun applyShuffleOrder(")
for title, method in (("shuffle", shuffle), ("repeat", repeat)):
    assert "saveQueueToDisk()" not in method, f"{title}: synchronous full queue write was reintroduced"
    assert "safeDataStoreEdit" in method, f"{title}: mode preference no longer saved"
assert "val shufflePlaylistFirst = cachedShufflePlaylistFirst" in shuffle
assert "dataStore.get(ShufflePlaylistFirstKey" not in shuffle
assert "settings[ShuffleModeKey] = shuffleModeEnabled" in shuffle
assert "settings[RepeatModeKey] = repeatMode" in repeat
assert "delay(60.seconds)" in service and "saveQueueToDisk()" in service
assert "override fun onMediaItemTransition(" in service
assert "override fun onDestroy()" in service

print("PASS: landscape controls share visibility, use one player, and mode toggles do not block on queue IO")


# Cover transition invariants. A lazy carousel with items
# [previous, current, next] must never start by rendering index 0
# and only then asynchronously snap to index 1.
thumbnail = (src / "ui/player/Thumbnail.kt").read_text()
assert "key(mediaMetadata?.id) {" in thumbnail
assert "rememberLazyGridState(initialFirstVisibleItemIndex = currentMediaIndex.coerceAtLeast(0))" in thumbnail
assert "thumbnailArtworkKey(mediaMetadata?.id, index, item.mediaId)" in thumbnail
assert "thumbnailLazyGridState.scrollToItem(currentMediaIndex)" in thumbnail, "Swipe recentering lost"
assert "val visibleGradientColors" in player
assert "if (gradientColorsOwner == mediaMetadata?.id)" in player
assert 'label = "gradientTop"' in player
assert 'label = "gradientMiddle"' in player
assert 'label = "gradientBottom"' in player
assert 'label = "gradientBackground"' not in player, "Old song artwork retained by whole-layer crossfade"
assert "artworkThemeColor = visibleGradientColors.firstOrNull()" in player
assert "var tapHighlight by remember" in player
assert "if (tapHighlight) {" in player and "tapHighlight = false" in player
more_button = player.split("private fun PlayerMoreMenuButton(", 1)[1]
assert ".background(if (tapHighlight)" in more_button
assert "indication = null" in more_button, "Permanent ripple halo on 3-dot button"

# The menu should not paint an opaque full-screen sheet over the player.
menu = (src / "ui/component/BottomSheetMenu.kt").read_text()
assert "modifier = modifier.fillMaxHeight()" not in menu
assert ".heightIn(max = screenHeight * 0.78f)" in menu
assert "scrimColor = Color.Black.copy(alpha = 0.18f)" in menu
assert "modifier = modifier.liquidGlass(" not in menu, "Cross-window glass hides the album behind the dialog"
assert "MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)" in menu

assert "if (!state.isVisible) return" in menu, "Menu must get fresh modal state after closing"
# New-player-design three dots live in the collapsed queue bar, not in PlayerMoreMenuButton.
queue_ui = (src / "ui/player/Queue.kt").read_text()
assert "var moreTapHighlight by remember" in queue_ui
assert ".background(if (moreTapHighlight) textButtonColor.copy(alpha = 0.28f) else Color.Transparent)" in queue_ui
assert "moreTapHighlight = true" in queue_ui
assert "tint = TextBackgroundColor" in queue_ui

# SoundProof must actually act on the player, including late Room hydration.
soundproof = (src / "playback/SoundProofRepository.kt").read_text()
assert "SoundProofPolicy.isAnyArtistReportedAi(credits, known)" in soundproof
assert "soundProofRepository.shouldSkipArtist(artists)" in service
assert "player.seekToNextMediaItem()" in service
assert "currentSong.value?.takeIf { it.song.id == candidateId }" in service
assert "checkSoundProofForCurrent(player.currentMediaItem)" in service
assert "SoundProofSkipAiKey" in service

# Quality policy: max-quality is available for playback, internal downloads
# and exports; FLAC is a conversion, not magically lossless YouTube audio.
download_format = (src / "downloads/DownloadAudioFormat.kt").read_text()
download_engine = (src / "downloads/FolderAudioDownload.kt").read_text()
internal_engine = (src / "downloads/PlaybackAudioEngine.kt").read_text()
extractor = (src / "utils/InnerTubeXPlayer.kt").read_text()
assert 'M4A(R.string.download_format_m4a, "bestaudio[ext=m4a]")' in download_format
assert 'AAC(R.string.download_format_aac, "bestaudio[ext=m4a]")' in download_format
assert "val portableChoices: List<DownloadAudioFormat> = listOf(M4A, OPUS)" in download_format
assert "bestaudio[acodec=opus]" in download_format
assert 'else -> M4A' in download_format
assert '"bestaudio[ext=m4a]"' in download_format
assert '.addOption("-f", selectYtDlpAudioFormat(format, lowQuality, url.startsWith("file:")))' in download_engine
assert 'private fun remuxPortableAac(' in download_engine
assert '"-c:a", "copy"' in download_engine
assert '"-f", "adts"' in download_engine
assert 'if (format == DownloadAudioFormat.AAC) remuxPortableAac(context, output, directory)' in download_engine
assert "private fun remuxOriginalOpus(" in download_engine
assert '"-f", "opus"' in download_engine
assert 'audioQuality: AudioQuality = AudioQuality.HIGH' in internal_engine
assert "AudioQuality.HIGH -> InnerTubeXAudioQuality.HIGH" in extractor
assert "AudioQuality.HIGH" in service and "QUALITY CHANGED" in service
print("PASS: current-song artwork, menu backdrop/interaction, late SoundProof metadata and highest-quality paths guarded")


# Timer / seek regression safeguards: a previous track must not own the next
# song's timeline, and zero/unknown duration must never turn wavy UI into NaN.
timeline = (src / "ui/player/PlayerTimelinePolicy.kt").read_text()
mini = (src / "ui/player/MiniPlayer.kt").read_text()
wavy = (src / "ui/component/WavySlider.kt").read_text()
switcher = (src / "ui/player/PlayerArtworkSwitcher.kt").read_text()
assert "currentId == playerId" in timeline
assert "playerDurationMs != C.TIME_UNSET" not in timeline or "it != C.TIME_UNSET" in timeline
assert "playerPositionMs.coerceAtLeast(0L)" in timeline
assert "metadataDurationSeconds" in timeline and ".times(1_000L)" in timeline
assert "remember(playerConnection, activeTimelineId)" in player
assert "LaunchedEffect(activeTimelineId, playbackState, isPlaying, isCasting)" in player
assert "PlayerTimelinePolicy.durationMs(" in player
assert "PlayerTimelinePolicy.positionMs(" in player
assert "PlayerTimelinePolicy.seekPosition(it, seekableDuration)" in player
assert "key(activeTimelineId) {" in player, "Old slider pointerInput must not survive a song change"
assert "val effectivePosition by remember {" not in player, "Cast mode was captured stale"
assert "remember(positionState, durationState)" in mini, "Mini-player ring stuck on initial song"
assert "key(mediaMetadata?.id)" in mini, "Old blurred mini cover could persist"
assert "if (span > 0f && span.isFinite() && value.isFinite())" in wavy
assert "firstFrameRendered = false" in switcher
assert "val matchedVideoSong = currentSong?.takeIf { it.song.id == activeVideoId }" in player
assert "matchedVideoSong?.song?.isVideo" in player
assert "matchedVideoSong?.orderedArtists" in player
assert "if (visible && firstFrameRendered) 1f else 0f" in switcher
assert "visible = visible && !firstFrameRendered" in switcher
print("PASS: current-song timer, valid seek ranges, Cast, mini ring, and fresh video surface guarded")

# A mode selector must not appear 5 seconds after expanding the player.
assert "fun showSelector(" in (src / "ui/player/PlayerVideoModePolicy.kt").read_text()
assert "enabled && expanded" in (src / "ui/player/PlayerVideoModePolicy.kt").read_text()
assert "videoEnabled = videoSelectable" in switcher
# Published notes must be version-scoped, not re-used forever.
release_workflow = (root / ".github/workflows/music-release.yml").read_text()
assert "scripts/release_notes.py" in release_workflow
assert "cat CHANGEMENTS-VERSION.txt >> notes.txt" not in release_workflow
print("PASS: persistent Son/Vidéo tabs, original M4A/Opus exports and release-scoped notes")

# A standalone Home/Search song must not leave the Next button inert.
connection = (src / "playback/PlayerConnection.kt").read_text()
next_policy = (src / "playback/NextTrackPolicy.kt").read_text()
mini = (src / "ui/player/MiniPlayer.kt").read_text()
home = (src / "ui/screens/HomeScreen.kt").read_text()
search = (src / "ui/screens/search/OnlineSearchScreen.kt").read_text()
assert "service.requestNextSongOnDemand()" in connection
assert "NextTrackPolicy.canAdvance(" in connection
assert "fun canRequestNextSong(" in next_policy
assert "fun requestNextSongOnDemand()" in service
assert "database.relatedSongs(currentId)" in service
assert "YouTube.next(WatchEndpoint(videoId = currentId))" in service
assert "playerConnection.seekToNext()" in mini
assert "startIndex = allLocalItems.filterIsInstance<Song>()" in home
assert "startIndex = allYtItems.filterIsInstance<SongItem>()" in home
assert "startIndex = viewState.items.filterIsInstance<SongItem>()" in search
print("PASS: Next follows real list queues or loads one successor explicitly without radio")

# Verify selection is about *actual* native streams, not a renamed file.
selector = (root / "vendor/innertubex/src/commonMain/kotlin/fr/makoff/music/innertubex/extraction/FormatSelectors.kt").read_text()
engine = (src / "downloads/FolderAudioDownload.kt").read_text()
download_util = (src / "playback/DownloadUtil.kt").read_text()
assert "AudioQuality.HIGH -> {" in selector
assert "validFormats.maxWithOrNull(highAudioFormatComparator)" in selector
assert "validFormats.filter { it.mimeType.contains(\"audio/mp4\") }.maxByOrNull(::audioFormatScore)" in selector
assert '"bestaudio[ext=m4a]"' in download_format
assert '"bestaudio[acodec=opus]"' in download_format
assert '"-c:a", "copy"' in engine
assert "audioQuality = AudioQuality.HIGH" in download_util
print("PASS: best available original audio bitrate per chosen codec, no lossy export reencoding")
