#!/usr/bin/env python3
"""Regression checks for layout, string resources and release workflow branding."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
kotlin = root / "app/src/main/kotlin/fr/makoff/music"

for name in ("AppearanceSettings", "ContentSettings", "StorageSettings"):
    path = kotlin / "ui/screens/settings" / (name + ".kt")
    source = path.read_text()
    assert source.count("Scaffold(") == 1, name
    assert source.count("TopAppBar(") == 1, name
    assert source.count(".padding(innerPadding)") == 1, name
    assert "WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom" in source, name
    assert source.index("TopAppBar(") < source.index("Column(\n        Modifier\n            .padding(innerPadding)"), name

for name in ("AndroidAutoSettings", "UpdaterSettings", "BackupAndRestore", "DownloadsSettings"):
    source = (kotlin / "ui/screens/settings" / (name + ".kt")).read_text()
    assert source.count("Scaffold(") == 1, name
    assert source.count("TopAppBar(") == 1, name
    assert ".padding(innerPadding)" in source, name
    assert "WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom" in source, name

player = (kotlin / "ui/player/Player.kt").read_text()
assert player.count(".weight(sideButtonWeight)") == 2
assert "backButtonWeight" not in player
assert "nextButtonWeight" not in player
assert "isBackPressed || isNextPressed" in player
selector = (kotlin / "ui/player/PlayerArtworkSwitcher.kt").read_text()
assert "contentAlignment = Alignment.Center" in selector
assert "modifier = Modifier.width(120.dp)" in selector
fullscreen = (kotlin / "ui/player/FullscreenVideoPlayer.kt").read_text()
assert "modifier = Modifier.align(Alignment.Center)" in fullscreen
sources = (kotlin / "ui/screens/settings/AudioSourcesSettings.kt").read_text()
assert "Modifier.align(Alignment.CenterHorizontally)" in sources

base_strings = root / "app/src/main/res/values/metrolist_strings.xml"
fr_strings = root / "app/src/main/res/values-fr/metrolist_strings.xml"
base = {s.get("name"): s.text for s in ET.parse(base_strings).getroot() if s.tag == "string"}
fr = {s.get("name"): s.text for s in ET.parse(fr_strings).getroot() if s.tag == "string"}
assert base["advanced_desc"] == "Tempo and pitch"
assert fr["advanced_desc"] == "Tempo et tonalité"
for key in ("audio_sources_title", "audio_sources_url", "audio_sources_manifest_hint"):
    assert key in fr, key
assert "Apple Music" in (root / "README.md").read_text()
assert 'rootProject.name = "AppleMusic"' in (root / "settings.gradle.kts").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
assert 'baseApplicationId = "fr.makoff.music"' in gradle
assert 'appNameOverride ?: "Apple Music"' in gradle
workflow = (root / ".github/workflows/makoff-android.yml").read_text()
release = (root / ".github/workflows/music-release.yml").read_text()
assert "name: Apple Music Android" in workflow
assert 'workflows: ["Apple Music Android"]' in release
print("PASS: reserved settings headers, symmetric transport controls and Apple Music branding")
