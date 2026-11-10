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
