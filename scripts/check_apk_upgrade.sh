#!/usr/bin/env bash
set -euo pipefail
package=fr.makoff.music
mkdir -p smoke-results
adb uninstall "$package" >/dev/null 2>&1 || true
adb install previous-apk/Metrolist.apk
adb shell dumpsys package "$package" > smoke-results/upgrade-before.txt
adb install -r dist/Metrolist.apk
adb shell dumpsys package "$package" > smoke-results/upgrade-after.txt
python3 - <<'PY'
from pathlib import Path
import os
import re
before = Path('smoke-results/upgrade-before.txt').read_text()
after = Path('smoke-results/upgrade-after.txt').read_text()
def value(text, key):
    return re.search(rf'{key}=([^\s]+)', text).group(1)
assert int(value(after, 'versionCode')) == int(os.environ['MUSIC_VERSION_CODE'])
assert int(value(after, 'versionCode')) > int(value(before, 'versionCode'))
assert re.search(r'firstInstallTime=(.*)', before).group(1) == re.search(r'firstInstallTime=(.*)', after).group(1)
print('UPGRADE_OK: même installation, même signature acceptée, versionCode supérieur')
PY
adb logcat -c
adb shell am start -W -n "$package/fr.makoff.music.MainActivity"
sleep 10
adb shell pidof "$package"
adb logcat -d > smoke-results/upgrade-logcat.txt
! grep -Fq "Process: $package," smoke-results/upgrade-logcat.txt
