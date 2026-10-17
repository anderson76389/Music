#!/usr/bin/env bash
set -euo pipefail
package=${SMOKE_PACKAGE:-fr.makoff.music.debug}
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
  if grep -Fq "Process: $package," "smoke-results/$variant-logcat.txt"; then
    echo "Échec : plantage du processus détecté ($variant)."
    exit 1
  fi
  if [ "$variant" = foss ] && [ -d apk-tests ]; then
    adb install apk-tests/*.apk
    timeout 180s adb shell am instrument -w -r -e class fr.makoff.music.downloads.FolderAudioInstrumentedTest "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results/yt-dlp-saf-tests.txt
    grep -Eq 'OK \([0-9]+ tests?\)' smoke-results/yt-dlp-saf-tests.txt
    ! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' smoke-results/yt-dlp-saf-tests.txt
    timeout 150s adb shell am instrument -w -r -e class fr.makoff.music.downloads.YouTubeAudioLiveTest "$package.test/androidx.test.runner.AndroidJUnitRunner" > smoke-results/youtube-live-test.txt || echo "Essai YouTube interrompu ou délai dépassé" >> smoke-results/youtube-live-test.txt
    if ! grep -Eq 'OK \(1 test\)' smoke-results/youtube-live-test.txt; then
      echo "::warning::Le téléchargement YouTube réel n’a pas réussi depuis le réseau de l’émulateur ; voir youtube-live-test.txt."
    fi
  fi
  adb shell am force-stop "$package"
done
