"""Static settings wiring checks; does not claim runtime or service coverage."""
from pathlib import Path
import re
r = Path(__file__).resolve().parents[1]
k = r / 'app/src/main/kotlin/fr/makoff/music'
nav = (k/'ui/screens/NavigationBuilder.kt').read_text()
routes = set(re.findall(r'composable\(\s*(?:route\s*=\s*)?"([^"]+)"', nav))
def valid_route(route):
    return any(re.fullmatch('/'.join('[^/]+' if part.startswith('{') and part.endswith('}')
                                    else re.escape(part) for part in template.split('/')), route)
               for template in routes)
assert 'auto_playlist/{playlist}' in routes
assert valid_route('auto_playlist/downloaded')
assert not valid_route('auto_playlist/downloaded/extra')
assert not valid_route('settings/absent')
for removed in ('settings/about', 'settings/integrations', 'settings/integrations/lastfm', 'settings/ai', 'settings/integrations/listen_together'):
    assert removed not in routes, removed
for p in (r/'app/src/main/kotlin').rglob('*.kt'):
    s = p.read_text()
    assert not re.search(r'LastFM|lastfm|ScrobbleManager|discord|ListenTogether|LyricsTranslationHelper|OpenRouter|DeepLService', s, re.I), p
for p in sorted((k/'ui/screens/settings').rglob('*.kt')):
    s = p.read_text()
    for route in re.findall(r'navigate\("([^"$]+)"\)', s):
        assert valid_route(route), (p, route)
    assert not re.search(r'(?:onClick|onCheckedChange)\s*=\s*\{\s*\}', s), p
    actions = len(re.findall(r'\b(?:onClick|onCheckedChange|onValueChange|onDone)\s*=', s))
    print(f'{p.name}: {actions} action bindings; static destinations valid')
print('PASS: removed routes and clients absent; settings action wiring checked (not an end-to-end guarantee)')

# A switch must not display a different default from its production consumer.
defaults = {}
for p in (k/'ui/screens/settings').rglob('*.kt'):
    for key, value in re.findall(r'rememberPreference\(\s*(?:key\s*=\s*)?(\w+)\s*,\s*(?:defaultValue\s*=\s*)?(true|false)', p.read_text()):
        defaults[key] = value
sources = [(p, p.read_text()) for p in (r/'app/src/main/kotlin').rglob('*.kt') if '/ui/screens/settings/' not in str(p)]
for key, expected in defaults.items():
    for p, source in sources:
        patterns = [r'(?:get|rememberPreference)\(\s*(?:key\s*=\s*)?' + key + r'\s*,\s*(?:defaultValue\s*=\s*)?(true|false)', r'\[' + key + r'\]\s*\?:\s*(true|false)']
        for pattern in patterns:
            for match in re.finditer(pattern, source):
                assert match[1] == expected, (p, key, expected, match[1])
print(f'PASS: {len(defaults)} directly declared boolean defaults agree with detected consumers')

settings = (k/'ui/screens/settings/SettingsScreen.kt').read_text()
content = (k/'ui/screens/settings/ContentSettings.kt').read_text()
history = (k/'ui/screens/settings/HistorySettingsSection.kt').read_text()
assert 'R.string.changelog' not in settings
assert 'settings/android_auto' not in settings
assert 'settings/android_auto' in content
assert 'settings/privacy' not in routes
assert 'HistorySettingsSection()' in (k/'ui/screens/settings/PlayerSettings.kt').read_text()
assert 'clearListenHistory()' in history and 'clearSearchHistory()' in history
assert 'DisableScreenshotKey' not in (k/'MainActivity.kt').read_text()
print('PASS: removed settings absent; Android Auto reachable from Content')

assert not (k/'playback/SleepTimer.kt').exists()
assert not (k/'playback/alarm/MusicAlarmScheduler.kt').exists()
assert not (k/'lyrics/LyricsHelper.kt').exists()
assert not (k/'ui/menu/LyricsMenu.kt').exists()
menu = (k/'ui/menu/PlayerMenu.kt').read_text()
assert 'system_equalizer' not in menu
assert 'navController.navigate("equalizer")' in menu
for path in ('ui/player/Player.kt', 'ui/player/Queue.kt', 'ui/screens/settings/AppearanceSettings.kt', 'ui/screens/settings/ContentSettings.kt'):
    text = (k/path).read_text()
    assert not re.search(r'R\.string\.(?:sleep_timer|lyrics|alarm)|sleepTimer|LyricsMenu|onToggleLyrics', text), path
assert 'R.drawable.search' in (k/'MainActivity.kt').read_text()
print('PASS: removed playback features absent; integrated equalizer and history actions preserved')

player_settings = (k/'ui/screens/settings/PlayerSettings.kt').read_text()
player_keys = set(re.findall(r'remember(?:Enum)?Preference\(\s*(?:key\s*=\s*)?(\w+)', player_settings))
for key in sorted(player_keys):
    consumers = []
    for p, source in sources:
        if p.name == 'PreferenceKeys.kt':
            continue
        body = '\n'.join(line for line in source.splitlines() if not line.startswith('import '))
        if re.search(r'\b' + re.escape(key) + r'\b', body):
            consumers.append(str(p.relative_to(k)))
    assert consumers, ('Player setting without production consumer', key)
    print(f'PLAYER_SETTING: {key} -> {", ".join(consumers)}')
print(f'PASS: all {len(player_keys)} Player/audio preferences have production consumers; runtime/hardware checks remain separate')
