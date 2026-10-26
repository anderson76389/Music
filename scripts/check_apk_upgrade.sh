#!/usr/bin/env bash
set -euo pipefail
package=fr.makoff.music
mkdir -p smoke-results
adb uninstall "$package" >/dev/null 2>&1 || true
adb install previous-apk/Metrolist.apk
adb shell dumpsys package "$package" > smoke-results/upgrade-before.txt
adb install -r dist/Metrolist.apk
adb shell dumpsys package "$package" > smoke-results/upgrade-after.txt
compat_work=$(mktemp -d)
mkdir -p "$compat_work/classes" "$compat_work/dex"
javac -cp "$ANDROID_HOME/platforms/android-37.0/android.jar" -d "$compat_work/classes" scripts/compat/QueueFormatProbe.java
jar cf "$compat_work/probe.jar" -C "$compat_work/classes" .
"$ANDROID_HOME/build-tools/36.0.0/d8" --lib "$ANDROID_HOME/platforms/android-37.0/android.jar" --min-api 26 --output "$compat_work/dex" "$compat_work/probe.jar"
adb push "$compat_work/dex/classes.dex" /data/local/tmp/music-queue-compat.dex
adb push previous-apk/Metrolist.apk /data/local/tmp/music-queue-previous.apk
adb push dist/Metrolist.apk /data/local/tmp/music-queue-current.apk
adb shell CLASSPATH=/data/local/tmp/music-queue-compat.dex app_process /system/bin fr.makoff.music.compat.QueueFormatProbe /data/local/tmp/music-queue-previous.apk /data/local/tmp/music-queue-current.apk | tee smoke-results/queue-upgrade.txt
grep -q QUEUE_UPGRADE_OK smoke-results/queue-upgrade.txt
rm -rf "$compat_work"
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
