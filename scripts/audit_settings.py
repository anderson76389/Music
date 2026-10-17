"""Static settings wiring checks; does not claim runtime or service coverage."""
from pathlib import Path
import re
r = Path(__file__).resolve().parents[1]
k = r / 'app/src/main/kotlin/com/metrolist/music'
nav = (k/'ui/screens/NavigationBuilder.kt').read_text()
routes = set(re.findall(r'composable\((?:route\s*=\s*)?"([^"]+)"', nav))
for removed in ('settings/about', 'settings/integrations', 'settings/integrations/lastfm'):
    assert removed not in routes, removed
for p in (r/'app/src/main/kotlin').rglob('*.kt'):
    s = p.read_text()
    assert not re.search(r'LastFM|lastfm|ScrobbleManager|discord', s, re.I), p
for p in sorted((k/'ui/screens/settings').rglob('*.kt')):
    s = p.read_text()
    for route in re.findall(r'navigate\("([^"$]+)"\)', s):
        assert route in routes, (p, route)
    assert not re.search(r'(?:onClick|onCheckedChange)\s*=\s*\{\s*\}', s), p
    actions = len(re.findall(r'\b(?:onClick|onCheckedChange|onValueChange|onDone)\s*=', s))
    print(f'{p.name}: {actions} action bindings; static destinations valid')
print('PASS: removed routes and clients absent; settings action wiring checked (not an end-to-end guarantee)')
