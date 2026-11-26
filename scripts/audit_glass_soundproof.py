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
assert "chrome = true, showBorder = false" in switcher
assert "No nested surface or grey selection capsule" in switcher
assert "PlayerVideoThemeMode.LIQUID_GLASS -> if (dark) Color.White else Color(0xFF17191D)" in switcher
assert "LocalGlassBackdropTint.current?.let" in switcher
assert player.count("LocalGlassBackdropTint provides when (playerBackground)") == 2
assert "val backdropTint = LocalGlassBackdropTint.current ?: surface" in glass
assert "softRim" not in glass
assert "return if (showBorder) panel.border(0.8.dp, rim, shape) else panel" in glass
assert "glassTabSelection" not in nav
mini = (app / "ui/player/MiniPlayer.kt").read_text()
assert ".liquidGlass(RoundedCornerShape(32.dp), chrome = true, showBorder = false)" in mini
assert "refractionDisplacement = if (flat || !showBorder) 0.dp else 12.dp" in glass
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

# Expanded Home retains lazy lists and every real item across the dice slot.
home_model = (app / "viewmodels/HomeViewModel.kt").read_text()
home_screen = (app / "ui/screens/HomeScreen.kt").read_text()
assert ".take(54)" in home_model and ".take(48)" in home_model
assert "limit = 50, offset = 0" in home_model and ".shuffled().take(36)" in home_model
assert "(items.size + itemsPerPage) / itemsPerPage" in home_screen
assert "if (page == 0) 0 else page * itemsPerPage - 1" in home_screen
assert "if (page == 0) itemsPerPage - 1 else itemsPerPage" in home_screen
for capacity in (6, 8, 9, 10, 12):
    for size in range(1, 55):
        seen = []
        for page in range((size + capacity) // capacity):
            start = 0 if page == 0 else page * capacity - 1
            count = capacity - 1 if page == 0 else capacity
            seen.extend(range(start, min(size, start + count)))
        assert seen == list(range(size)), (size, capacity, seen)
print("PASS: expanded Home caps and all speed-dial titles survive pagination")

assert "if (!showBorder) {" in glass and "return clip(shape).hazeBlur(" in glass
assert glass.index("return clip(shape).hazeBlur(") < glass.index("GlassStyle.regular.then")
