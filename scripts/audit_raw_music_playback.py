#!/usr/bin/env python3
"""Regression guardrails for unprocessed audio and continuous suggested playback."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/kotlin/fr/makoff/music"
service = (src / "playback/MusicService.kt").read_text()
settings = (src / "ui/screens/settings/PlayerSettings.kt").read_text()
prefs = (src / "constants/PreferenceKeys.kt").read_text()
queue = (src / "playback/queues/YouTubeQueue.kt").read_text()
recommendations = (src / "playback/PlaybackRecommendations.kt").read_text()

# On an upgrade, restore raw, full-volume, high-quality YouTube playback just
# once and do not touch downloaded files, signatures, subscriptions or likes.
assert "RawYouTubeAudioMigrationKey" in prefs
assert "prefs[RawYouTubeAudioMigrationKey] != true" in service
for key, value in (
    ("AudioQualityKey", "fr.makoff.music.constants.AudioQuality.HIGH.name"),
    ("AudioNormalizationKey", "false"),
    ("SkipSilenceKey", "false"),
    ("SkipSilenceInstantKey", "false"),
    ("CrossfadeEnabledKey", "false"),
    ("VarispeedKey", "false"),
    ("PlayerVolumeKey", "1f"),
    ("AutoplayKey", "true"),
    ("AutoLoadMoreKey", "true"),
):
    assert f"settings[{key}] = {value}" in service, key
assert "eqProfileRepository.setActiveProfile(null)" in service
assert "AudioAddons.update(this@MusicService)" in service
assert "sources.map { it.copy(enabled = false) }" in service
assert "playerVolume.value = playerState.volume" not in service, "Legacy player state must not restore stale app volume"
assert "prefs[AudioNormalizationKey] ?: false" in service
assert "it[AudioNormalizationKey] ?: false" in service
assert "AudioNormalizationKey,\n        defaultValue = false" in settings
assert "AudioQuality.HIGH" in (src / "utils/InnerTubeXPlayer.kt").read_text()
print("PASS: raw PCM, max native volume, no previous DSP, highest available stream")

assert "scheduleAutoplayRecommendations()" in service
assert "PlaybackRecommendations.fetch(seedId)" in service
assert "PlaybackRecommendations.fetch(currentId)" in service
assert "player.addMediaItems(fresh)" in service
assert "player.currentMediaItem?.mediaId != seedId" in service
assert "queueGeneration != generation" in service
assert "if (currentQueue !== queue) return@launch" in service
assert "prefetchNextAudioStream()" in service
assert "stream.playbackTracking?.let" in service
assert "songUrlCache.put(" in service
assert "YouTube.related(endpoint)" in recommendations
assert 'playlistId = "RDAMVM$seedId"' in recommendations
assert "private class EmptyRadioQueueException" not in queue
print("PASS: non-blocking autoplay suggestions, queue-race guard, and next song prefetch")
