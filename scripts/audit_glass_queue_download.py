#!/usr/bin/env python3
"""Prevent regression of Liquid Glass, cover palette, player controls and native audio exports."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/kotlin/fr/makoff/music"
glass = (src / "ui/theme/Glass.kt").read_text()
player = (src / "ui/player/Player.kt").read_text()
queue = (src / "ui/player/Queue.kt").read_text()
colors = (src / "ui/theme/PlayerColorExtractor.kt").read_text()
downloader = (src / "downloads/FolderAudioDownload.kt").read_text()
selector = (src / "downloads/DownloadAudioFormat.kt").read_text()
metadata = (src / "downloads/AudioFileMetadata.kt").read_text()
playback = (src / "downloads/PlaybackAudioEngine.kt").read_text()
popup = (src / "downloads/DownloadChoiceDialog.kt").read_text()
tests = (root / "app/src/androidTest/kotlin/fr/makoff/music/downloads/FolderAudioInstrumentedTest.kt").read_text()
live = (root / "app/src/androidTest/kotlin/fr/makoff/music/downloads/YouTubeAudioLiveTest.kt").read_text()
smoke = (root / "scripts/smoke_apk.sh").read_text()

assert "if (dark) 0.72f else 0.74f" in glass, "Glass opacity must remain a touch stronger"
assert "if (dark) 0.30f else 0.36f" in glass, "Readability tint must be opaque enough"
assert "refractionStrength = if (flat) 0f else if (showBorder) 0.65f else 0.42f" in glass, "Liquid Glass optics must remain"
assert "glassQueueSurface(" in colors and "glassQueueAccent(" in colors
assert "PlayerColorExtractor.glassQueueSurface(" in player
assert "PlayerColorExtractor.glassQueueAccent(" in player
assert "themeColor = glassQueueAccent" in player
assert "if (glassMode) {\n                        glassQueueSurface" in player
assert "background.copy(alpha = 0.97f)" in queue
assert "lazyListState.scrollToItem(currentWindowIndex + headerItems)" in queue
assert "val glassQueueSurface by animateColorAsState(" in player
assert "val glassQueueAccent by animateColorAsState(" in player
assert player.index("val glassQueueAccent by animateColorAsState(") < player.index("CompositionLocalProvider(\n        LocalMenuState provides playerMenuState"), "Queue/menu palette must be in the outer player scope"
print("PASS: Liquid Glass slightly less transparent; cover-aware player and up-next surfaces, accents and correct scroll offset")

# The main visible queue actions must have real callbacks, not inert or test-only placeholders.
for wiring in (
    "playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled",
    "playerConnection.player.toggleRepeatMode()",
    "playerConnection.player.seekToDefaultPosition(",
    "playerConnection.togglePlayPause()",
    "playerConnection.service.playNextAutomix(",
    "playerConnection.service.addToQueueAutomix(",
):
    assert wiring in queue, f"Queue interaction lost: {wiring}"
assert "playerConnection::seekToNext" in player
assert "playerConnection::seekToPrevious" in player
assert "OfflinePlayerInstrumentedTest" in smoke and "PlayerVideoSwitchInstrumentedTest" in smoke
assert "SettingsNavigationTest" in smoke and "FolderAudioInstrumentedTest" in smoke
print("PASS: playable queue/player actions connected; emulator UI tests run main controls and settings")

# yt-dlp is a fallback for native M4A and the primary extractor for native Opus.
# Neither yt-dlp nor FFmpeg may re-encode a lossy stream just to change its container.
assert '"bestaudio[ext=m4a]"' in selector
assert '"bestaudio[acodec=opus]"' in selector
assert '"-c:a", "copy"' in downloader and '"-c:a", "copy"' in metadata
assert '"--ignore-config"' in downloader and '"--fixup", "never"' in downloader
assert '"--enable-file-urls"' in downloader
assert "ENGINE_SHA256" in downloader and "checksum mismatch" in downloader
assert "if (format == DownloadAudioFormat.OPUS)" in playback
assert "return YtDlpAudioEngine.download(" in playback
assert "PlaybackAudioEngine.download(" in (src / "downloads/FolderAudioDownload.kt").read_text()
assert "Never publish a new M4A or Opus export without an embedded cover" in metadata
assert "No usable cover image" in metadata
assert '"-disposition:v:0", "attached_pic"' in metadata
assert "METADATA_BLOCK_PICTURE" in metadata
assert "rememberAudioDownloadAction" in popup
assert "ytDlpDownloadsRealBytesAndExportsThroughSafWithoutOverwriting" in tests
assert "ytDlpOriginalOpusIsRemuxedWithoutChangingEncodedSamples" in tests
assert "exportedAudioContainsPortableTagsAndEmbeddedCover" in tests
assert "assertArrayEquals" in tests and "audioPackets(raw), audioPackets(tagged)" in tests
assert "DownloadAudioFormat.M4A, DownloadAudioFormat.OPUS" in live
print("PASS: bundled yt-dlp fallback, M4A/Opus native selection, packet-copy tags, embedded covers and format/interaction regression tests")
