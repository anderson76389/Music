from pathlib import Path
import re
r = Path(__file__).resolve().parents[1]
k = r / 'app/src/main/kotlin/com/metrolist/music'
assert 'fr.makoff.music' in (r/'app/build.gradle.kts').read_text()
assert 'includeBuild("vendor/innertubex")' in (r/'settings.gradle.kts').read_text()
assert (r/'vendor/innertubex/LICENSE').exists()
assert 'override val enabled: Boolean = false' in (k/'utils/InnerTubeXPlayer.kt').read_text()
assert 'servers.firstOrNull()?.url.orEmpty()' in (k/'listentogether/ListenTogetherServers.kt').read_text()
for p in (r/'app/src').rglob('*'):
    if p.suffix not in ('.kt', '.xml') or 'test' in str(p).lower(): continue
    text = p.read_text()
    assert not re.search(r'https?://[^"\s<]*(?:MetrolistGroup|metrolist\.cc|meowery\.eu|t\.me/metrolist)', text), p
    assert 'com.metrolist.music' not in text, p
print('PASS: application identity, vendored library, disabled remote cipher config, no upstream application endpoints')

for p in (r/'app').rglob('*'):
    if p.suffix in ('.kt', '.xml', '.kts', '.pro'):
        assert 'discord' not in p.read_text().lower(), p
assert 'AudioQualityKey, AudioQuality.HIGH' in (k/'playback/DownloadUtil.kt').read_text()
assert 'defaultValue = AudioQuality.HIGH' in (k/'ui/screens/settings/PlayerSettings.kt').read_text()
print('PASS: Discord absent from application; high-quality defaults for player and downloads')
