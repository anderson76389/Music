#!/usr/bin/env bash
set -euo pipefail
package=${SMOKE_PACKAGE:-fr.makoff.music.debug}
mkdir -p smoke-results
# A disconnected emulator must fail with diagnostics instead of waiting indefinitely.
adb() { command timeout --kill-after=5s 90s adb "$@"; }
for variant in foss gms; do
  apk=$(find "apk-$variant" -name '*.apk' -print -quit)
  test -n "$apk"
  adb uninstall "$package" >/dev/null 2>&1 || true
  adb logcat -c
  adb install "$apk"
  if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]; then
    adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
  fi
  adb shell am start -W -n "$package/fr.makoff.music.MainActivity" | tee "smoke-results/$variant-launch.txt"
  sleep 15
  adb logcat -d > "smoke-results/$variant-logcat.txt"
  adb shell dumpsys activity activities > "smoke-results/$variant-activities.txt"
  adb exec-out screencap -p > "smoke-results/$variant.png"
  adb shell pidof "$package"
  python3 scripts/check_resumed_activity.py "$package" "smoke-results/$variant-activities.txt"
  if grep -Fq "Process: $package," "smoke-results/$variant-logcat.txt"; then
    echo "Échec : plantage du processus détecté ($variant)."
    exit 1
  fi
  if [ "$variant" = gms ] && [ -d apk-tests ]; then
    adb install apk-tests/*.apk
    timeout 300s adb shell am instrument -w -r -e class fr.makoff.music.downloads.FolderAudioInstrumentedTest "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results/yt-dlp-saf-tests.txt
    adb pull /data/local/tmp/download-quality-popup.png smoke-results/ || true
    adb logcat -d > smoke-results/folder-audio-logcat.txt
    grep -Eq 'OK \([0-9]+ tests?\)' smoke-results/yt-dlp-saf-tests.txt
    ! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' smoke-results/yt-dlp-saf-tests.txt
    adb shell cmd connectivity airplane-mode enable
    adb logcat -c
    adb shell settings get global airplane_mode_on | tee smoke-results/offline-network-state.txt
    grep -q '^1' smoke-results/offline-network-state.txt
    timeout 240s adb shell am instrument -w -r -e class fr.makoff.music.playback.OfflinePlayerInstrumentedTest "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results/offline-player-tests.txt
    adb pull /data/local/tmp/offline-player-failure.png smoke-results/ || true
    adb logcat -d > smoke-results/offline-player-logcat.txt
    adb exec-out screencap -p > smoke-results/offline-player.png
    adb pull /data/local/tmp/glass-mini-player.png smoke-results/ || true
    adb pull /data/local/tmp/glass-player.png smoke-results/ || true
    adb pull /data/local/tmp/glass-player-menu.png smoke-results/ || true
    adb shell cmd connectivity airplane-mode disable
    grep -Eq 'OK \(1 test\)' smoke-results/offline-player-tests.txt
    ! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' smoke-results/offline-player-tests.txt
    # Prepare data, then trigger restore outside instrumentation through the real file picker.
    timeout 60s adb shell am instrument -w -r -e class fr.makoff.music.settings.BackupRestoreProbe#prepareAndRestore "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results/restore-prepare.txt
    grep -Fq 'OK (1 test)' smoke-results/restore-prepare.txt
    adb logcat -d > smoke-results/restore-process-logcat.txt
    grep -Fq RESTORE_PREPARED smoke-results/restore-process-logcat.txt
    python3 scripts/ui_restore_probe.py "$package" | tee smoke-results/restore-ui.txt
    for attempt in $(seq 1 30); do
      adb logcat -d > smoke-results/restore-process-logcat.txt
      if grep -Fq RESTORE_APPLY_OK smoke-results/restore-process-logcat.txt; then break; fi
      sleep 1
    done
    grep -Fq RESTORE_APPLY_OK smoke-results/restore-process-logcat.txt
    timeout 60s adb shell am instrument -w -r -e class fr.makoff.music.settings.BackupRestoreProbe#verifyAfterRestart "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results/restore-verify.txt
    grep -Eq 'OK \(1 test\)' smoke-results/restore-verify.txt
    ! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' smoke-results/restore-verify.txt
    adb logcat -d > smoke-results/backup-restore-logcat.txt
    timeout 1200s adb shell am instrument -w -r -e class fr.makoff.music.settings.SettingsNavigationTest "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results/settings-navigation-tests.txt
    adb pull /data/local/tmp/settings-failure.png smoke-results/ || true
    adb pull /data/local/tmp/settings-glass.png smoke-results/ || true
    adb pull /data/local/tmp/glass-settings-enabled.png smoke-results/ || true
    adb pull /data/local/tmp/glass-home-enabled.png smoke-results/ || true
    for capture in download-history-covers download-history-cleaned glass-home-light glass-home-dark glass-settings-light glass-settings-dark glass-library-fr glass-account-readable glass-selection-readable; do
      adb pull "/data/local/tmp/$capture.png" smoke-results/ || true
    done
    adb logcat -d > smoke-results/settings-navigation-logcat.txt
    grep -Eq 'OK \([0-9]+ tests?\)' smoke-results/settings-navigation-tests.txt
    ! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' smoke-results/settings-navigation-tests.txt
    timeout 150s adb shell am instrument -w -r -e class fr.makoff.music.downloads.YouTubeAudioLiveTest "$package.test/androidx.test.runner.AndroidJUnitRunner" > smoke-results/youtube-live-test.txt || echo "Essai YouTube interrompu ou délai dépassé" >> smoke-results/youtube-live-test.txt
    if ! grep -Eq 'OK \(1 test\)' smoke-results/youtube-live-test.txt; then
      echo "::warning::Le téléchargement YouTube réel n’a pas réussi depuis le réseau de l’émulateur ; voir youtube-live-test.txt."
    fi
  fi
  if [ "$variant" = gms ] && [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]; then
    # Inspect both appearances with animation enabled after deterministic functional tests.
    adb shell settings put global window_animation_scale 1
    adb shell settings put global transition_animation_scale 1
    adb shell settings put global animator_duration_scale 1
    for appearance in yes no; do
      adb shell am force-stop "$package"
      adb shell cmd uimode night "$appearance"
      adb shell am start -W -n "$package/fr.makoff.music.MainActivity"
      sleep 3
      adb shell dumpsys gfxinfo "$package" reset >/dev/null
      adb shell input swipe 160 500 160 230 350
      sleep 2
      adb exec-out screencap -p > "smoke-results/glass-appearance-$appearance.png"
      adb shell dumpsys gfxinfo "$package" framestats > "smoke-results/glass-frames-$appearance.txt"
      adb shell dumpsys activity activities > "smoke-results/glass-activity-$appearance.txt"
      python3 scripts/check_resumed_activity.py "$package" "smoke-results/glass-activity-$appearance.txt"
      adb logcat -d > "smoke-results/glass-logcat-$appearance.txt"
      if grep -Fq "Process: $package," "smoke-results/glass-logcat-$appearance.txt"; then
        echo "Échec : plantage pendant la vérification du thème."
        exit 1
      fi
    done
  fi
  adb shell am force-stop "$package"
done
