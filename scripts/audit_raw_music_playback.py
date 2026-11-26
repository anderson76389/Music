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
assert "PlaybackRecommendations.fetch(seedId, exclusions)" in service
assert "PlaybackRecommendations.fetch(currentId, exclusions)" in service
assert "player.addMediaItems(autoplayNext)" in service
assert "automixItems.value = if (cachedSimilarContent) suggested.take(30)" in service
assert "generation == queueGeneration && continuousPlaybackAllowed()" in service
assert "queueGeneration != generation" in service
assert "if (currentQueue !== queue) return@launch" in service
assert "prefetchNextAudioStream()" in service
assert "stream.playbackTracking?.let" in service
assert "songUrlCache.put(" in service
assert "YouTube.related(endpoint)" in recommendations
assert 'playlistId = "RDAMVM$seedId"' in recommendations
assert "private class EmptyRadioQueueException" not in queue
print("PASS: non-blocking autoplay suggestions, queue-race guard, and next song prefetch")

assert "else -> minOf(12, suggestions.size - 1)" in recommendations
assert "distinctCandidates(seedId, fromNext + related, existing).size < 12" in recommendations
assert "recommendationCandidates(seedId, online)" in service
assert "database.mostPlayedSongs(" in service
assert "manualNextSongJob?.cancel()" in service
assert "if (!stillCurrent()" in service
assert "requestNextSongOnDemand(automatic = true)" in service
home = (src / "ui/screens/HomeScreen.kt").read_text()
for section in ("speedDialItems.filterIsInstance<SongItem>()", "quickPicks", "forgottenFavorites", "sectionSongs"):
    assert f"+ {section}.filter" in home
print("PASS: actual Home queues, bounded recommendations, history fallback and safe automatic advance")

assert "upcomingTrackCount() >= 12" in service
assert "timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, player.shuffleModeEnabled)" in service
assert "ensureRefillWatch()" in service and "delay(10_000L)" in service
assert "YouTube.next(currentPage.endpoint, token)" in recommendations
assert "YouTube.home().getOrNull()" in recommendations
assert "playerConnection.service.addToQueueAutomix(automix[0], 0)" not in (src / "ui/player/Player.kt").read_text()
print("PASS: service-owned refill and retry, actual shuffle order, continuation and personalized fallback")

assert "initialListRefill = currentQueue is ListQueue && !initialRecommendationsAdded" in service
assert "initialRecommendationsAdded = true" in service
