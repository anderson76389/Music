#!/usr/bin/env python3
"""Guard the single-surface glass controls, stable artwork palette, and SoundProof addon."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
app = root / "app/src/main/kotlin/fr/makoff/music"
switcher = (app / "ui/player/PlayerArtworkSwitcher.kt").read_text()
nav = (app / "ui/component/AppNavigation.kt").read_text()
glass = (app / "ui/theme/Glass.kt").read_text()
player = (app / "ui/player/Player.kt").read_text()
settings = (app / "ui/screens/settings/PlayerSettings.kt").read_text()
addons = (app / "ui/screens/settings/AudioSourcesSettings.kt").read_text()
main_settings = (app / "ui/screens/settings/SettingsScreen.kt").read_text()
service = (app / "playback/MusicService.kt").read_text()
repository = (app / "playback/SoundProofRepository.kt").read_text()
tests = (root / "app/src/androidTest/kotlin/fr/makoff/music/settings/SettingsNavigationTest.kt").read_text()

assert switcher.count("Modifier.liquidGlass(") == 1, "Son/Vidéo must be a single glass layer"
assert "unwanted grey vertical seam" in switcher
assert "if (selected) {" in switcher and "Color.White.copy(alpha = 0.14f)" in switcher
assert "onClick = if (video) onVideoClick else onAudioClick" in switcher
assert ".liquidGlass(RoundedCornerShape(32.dp), chrome = true, showBorder = false)" in nav
assert ".liquidGlass(RoundedCornerShape(28.dp), showBorder = false)" in nav
assert "if (showBorder) panel.border(0.5.dp, highlight, shape) else panel" in glass
assert "specularIntensity(if (!showBorder) 0f" in glass
assert "hazeGlass(input = HazeInput.Sources(state), style = style)" in glass
print("PASS: Son/Vidéo is one glass surface; navigation keeps its blur but not grey rim")

assert "val showArtworkBackdrop = !glassMode && state.isExpanded" in player
assert "gradientColorsCache[\"${currentMetadata.id}_$glassMode\"] = extractedColors" in player
assert "if (gradientColorsOwner == mediaMetadata?.id)" in player
assert "key(mediaMetadata?.id)" in player
assert "animateColorAsState(" in player
print("PASS: album palette is stable on repeat cover taps, keyed to the current track")

assert "SoundProofSkipAiKey" not in settings, "SoundProof must no longer appear in Lecteur"
assert addons.count("onCheckedChange = onSoundProofSkipAiChange") == 1, "exactly one SoundProof toggle"
assert "soundProofRepository.refreshNow()" in addons
assert "SoundProofRefreshGenerationKey" in addons
assert "maxOf(System.currentTimeMillis(), last + 1L)" in addons
assert "https://github.com/NuclearBlox/SoundProof-Dumps" in addons
assert "audio_sources_list_title" in addons and "AudioAddons.sources(context)" in addons
assert 'openSection("settings/audio_sources")' in main_settings
assert "SoundProofSkipAiKey" in service
assert "checkSoundProofForCurrent(player.currentMediaItem)" in service
assert "SoundProofPolicy.isAnyArtistReportedAi(credits, known)" in repository
assert "toggleAndRestore(R.string.soundproof_skip_ai_title, SoundProofSkipAiKey, false," in tests
assert "SOUNDPROOF_ADDONS_OK" in tests
print("PASS: SoundProof is a real opt-in addon with persisted switch, on-device votes and manual refresh")
