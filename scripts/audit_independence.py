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

import xml.etree.ElementTree as ET
for p in (r/'app/src/main/res').rglob('*.xml'):
    root = ET.parse(p).getroot()
    if root.tag == 'resources':
        for item in root:
            if item.tag in ('string', 'plurals', 'string-array'):
                visible = ''.join(item.itertext())
                assert not re.search(r'metrolist|meowery|mostafaalagamy|মেট্রোলিস্ট', visible, re.I), p
for p in (r/'services/listen-together').rglob('*'):
    assert p.name not in ('.git', '.gitmodules'), p
    if p.suffix in ('.go', '.mod', '.proto'):
        assert 'github.com/MetrolistGroup/' not in p.read_text(), p
assert (r/'services/listen-together/metroproto/listentogether.proto').exists()
assert (r/'app/src/main/assets/legal/GPL-3.0.txt').read_text() == (r/'LICENSE').read_text()
app_proto = (r/'app/src/main/proto/listentogether.proto').read_text()
server_proto = (r/'services/listen-together/metroproto/listentogether.proto').read_text()
assert app_proto == server_proto, 'Application and server must use the same protocol'
print('PASS: no upstream branding in UI resources; own server and protocol fully bundled; GPL preserved')

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
