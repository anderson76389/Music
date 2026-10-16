#!/usr/bin/env bash
set -euo pipefail
package=fr.makoff.music.debug
mkdir -p smoke-results
for variant in foss gms; do
  apk=$(find "apk-$variant" -name '*.apk' -print -quit)
  test -n "$apk"
  adb uninstall "$package" >/dev/null 2>&1 || true
  adb logcat -c
  adb install "$apk"
  adb shell am start -W -n "$package/fr.makoff.music.MainActivity" | tee "smoke-results/$variant-launch.txt"
  sleep 15
  adb logcat -d > "smoke-results/$variant-logcat.txt"
  adb shell dumpsys activity activities > "smoke-results/$variant-activities.txt"
  adb exec-out screencap -p > "smoke-results/$variant.png"
  adb shell pidof "$package"
  grep -E '(mResumedActivity|topResumedActivity).*fr.makoff.music.MainActivity' "smoke-results/$variant-activities.txt"
  if grep -Eq 'Process: fr.makoff.music.debug([,: ]|$)' "smoke-results/$variant-logcat.txt"; then
    echo "Échec : plantage du processus détecté ($variant)."
    exit 1
  fi
  adb shell am force-stop "$package"
done
