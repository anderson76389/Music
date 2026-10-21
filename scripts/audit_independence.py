from pathlib import Path
import re
r = Path(__file__).resolve().parents[1]
k = r / 'app/src/main/kotlin/com/metrolist/music'
assert 'fr.makoff.music' in (r/'app/build.gradle.kts').read_text()
assert 'includeBuild("vendor/innertubex")' in (r/'settings.gradle.kts').read_text()
assert (r/'vendor/innertubex/LICENSE').exists()
assert 'override val enabled: Boolean = false' in (k/'utils/InnerTubeXPlayer.kt').read_text()
for p in (r/'app/src').rglob('*'):
    if p.suffix not in ('.kt', '.xml') or 'test' in str(p).lower(): continue
    text = p.read_text()
    assert not re.search(r'https?://[^"\s<]*(?:MetrolistGroup|metrolist\.cc|meowery\.eu|t\.me/metrolist)', text), p
    assert 'com.metrolist.music' not in text, p
print('PASS: application identity, vendored library, disabled remote cipher config, no upstream application endpoints')

for p in (r/'app').rglob('*'):
    if p.suffix in ('.kt', '.xml', '.kts', '.pro') and not any(part in ('test', 'androidTest') for part in p.parts):
        assert 'discord' not in p.read_text().lower(), p
assert 'audioQuality = AudioQuality.HIGH' in (k/'playback/DownloadUtil.kt').read_text()
assert 'InternalDownloadFormatKey, DownloadAudioFormat.BEST' in (k/'playback/DownloadUtil.kt').read_text()
assert 'defaultValue = AudioQuality.HIGH' in (k/'ui/screens/settings/PlayerSettings.kt').read_text()
print('PASS: Discord absent from application; high-quality defaults for player and downloads')

import xml.etree.ElementTree as ET
for p in (r/'app/src/main/res').rglob('*.xml'):
    root = ET.parse(p).getroot()
    if root.tag == 'resources':
        for item in root:
            if item.tag in ('string', 'plurals', 'string-array'):
                visible = ''.join(item.itertext())
                assert not re.search(r'meowery|mostafaalagamy', visible, re.I), p
assert (r/'app/src/main/assets/legal/GPL-3.0.txt').read_text() == (r/'LICENSE').read_text()
assert not (k/'listentogether').exists()
assert not (r/'services/listen-together').exists()
for p in (r/'app/src/main').rglob('*'):
    if p.suffix in ('.kt', '.java', '.xml'):
        assert not re.search(r'ListenTogether|listen_together|LyricsTranslationHelper|OpenRouter|DeepLService|AiSettings|R\.string\.ai_', p.read_text()), p
assert '?: "Metrolist"' in (r/'app/build.gradle.kts').read_text()
print('PASS: removed shared-listening and AI code/resources absent; requested app name; GPL preserved')

# Keep the requested original icon local and byte-identical to the pinned source.
import hashlib
for entry in (r/'assets/logo-origine.sha256').read_text().splitlines():
    expected, path = entry.split('  ', 1)
    assert hashlib.sha256((r/path).read_bytes()).hexdigest() == expected, path
assert 'fr.makoff.music:innertubex' in (r/'gradle/libs.versions.toml').read_text()
assert 'GROUP=fr.makoff.music' in (r/'vendor/innertubex/gradle.properties').read_text()
for path in ('settings.gradle.kts', 'gradle/libs.versions.toml', 'vendor/innertubex/gradle.properties', 'vendor/innertubex/build.gradle.kts'):
    assert 'com.github.MetrolistGroup' not in (r/path).read_text(), path
print('PASS: original logo preserved locally; own library coordinates and local substitution')

for path in (r/'vendor/innertubex/src').rglob('*.kt'):
    assert 'MetrolistGroup/faraday' not in path.read_text(), path
print('PASS: upstream Faraday URLs removed from engine source and tests')

assert hashlib.sha256((r/'app/src/main/res/raw/ytdlp').read_bytes()).hexdigest() == '1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6'
print('PASS: official yt-dlp 2026.08.19 bundled and checksum verified')

# Inspect every production module, including bundled providers and the engine.
for module in ('app', 'innertube', 'vendor/innertubex'):
    for p in (r/module/'src').rglob('*'):
        if p.suffix not in ('.kt', '.xml', '.java') or any('test' in part.lower() for part in p.parts):
            continue
        assert not re.search(r'(?:https?|wss?)://[^"\s<]*(?:metrolistgroup|metrolist\.cc|meowery\.eu|lyricsplus\.atomix\.one|t\.me/metrolist)', p.read_text(), re.I), p
print('PASS: all production modules checked for known upstream service addresses')
