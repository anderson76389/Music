#!/usr/bin/env bash
set -euo pipefail
package=${SMOKE_PACKAGE:-fr.makoff.music.debug}
mkdir -p smoke-results
# A disconnected emulator must fail with diagnostics instead of waiting indefinitely.
adb() { command timeout --kill-after=5s 90s adb "$@"; }
# Large foldable display changes can temporarily reset the emulator's ADB
# transport even after all instrumented tests pass. Retry only those commands,
# and keep the final activity/crash checks mandatory; persistent errors still fail.
adb_retry() {
  local attempt
  for attempt in 1 2 3 4 5; do
    if adb "$@"; then return 0; fi
    echo "::warning::ADB temporarily unavailable after display change (attempt $attempt/5): $*" >&2
    command timeout --kill-after=2s 12s adb wait-for-device >/dev/null 2>&1 || true
    sleep 3
  done
  echo "::error::ADB command failed on every attempt: $*" >&2
  return 1
}
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
    # SoundProof real parser and cached offline filtering must work on all API levels.
    timeout 90s adb shell am instrument -w -r -e class fr.makoff.music.playback.SoundProofInstrumentedTest#cachedRealSchemaMatchesYouTubeMusicDisplayNames "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results/soundproof-local-test.txt
    grep -Eq 'OK \(1 test\)' smoke-results/soundproof-local-test.txt
    ! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' smoke-results/soundproof-local-test.txt
    # The Settings refresh must replace a stale in-memory artist vote map
    # without relying on the timestamp of a same-length cache file.
    timeout 90s adb shell am instrument -w -r -e class fr.makoff.music.playback.SoundProofInstrumentedTest#manualRefreshRechecksChangedVotesEvenWithSameFileTimestamp "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results/soundproof-manual-refresh-test.txt
    grep -Eq 'OK \(1 test\)' smoke-results/soundproof-manual-refresh-test.txt
    ! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' smoke-results/soundproof-manual-refresh-test.txt
    # Live network access is best-effort; keep evidence but do not hide a failure.
    if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -eq 30 ]; then
      timeout 90s adb shell am instrument -w -r -e class fr.makoff.music.playback.SoundProofInstrumentedTest#downloadsCommunityDumpAndParsesArtistVotes "$package.test/androidx.test.runner.AndroidJUnitRunner" > smoke-results/soundproof-live-test.txt || true
      if grep -Eq 'OK \(1 test\)' smoke-results/soundproof-live-test.txt && ! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' smoke-results/soundproof-live-test.txt; then
        echo "SOUNDPROOF_REAL_DUMP_OK"
      else
        echo "::warning::SoundProof live download did not pass; see soundproof-live-test.txt"
      fi
    fi
    # Deterministic quality/integrity check against a local ranged HTTP server.
    timeout 90s adb shell am instrument -w -r -e class fr.makoff.music.downloads.DownloadRangeInstrumentedTest "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee smoke-results/audio-range-tests.txt
    grep -Eq 'OK \(2 tests\)' smoke-results/audio-range-tests.txt
    ! grep -Eq 'FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: shortMsg' smoke-results/audio-range-tests.txt
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
    # Restoring a real backup restarts the app and can briefly interrupt ADB
    # on API 35. With set -e, the former logcat command aborted the entire CI
    # job on that transient disconnect, before checking the restore marker.
    # Retry only the observation; NEVER report success without RESTORE_APPLY_OK.
    restore_applied=0
    for attempt in $(seq 1 90); do
      if adb logcat -d > smoke-results/restore-process-logcat.txt 2> smoke-results/restore-adb-error.txt; then
        if grep -Fq RESTORE_APPLY_OK smoke-results/restore-process-logcat.txt; then
          restore_applied=1
          break
        fi
      else
        echo "ADB indisponible après restauration (tentative $attempt/90)" >&2
      fi
      sleep 2
    done
    if [ "$restore_applied" -ne 1 ]; then
      echo "::error::Restauration non confirmée : RESTORE_APPLY_OK introuvable après vérification ADB." >&2
      exit 1
    fi
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
    # Online video diagnostic uses the SAME signed video extractor as the
    # Son/Vidéo toggle. External YouTube networking must not block releases.
    timeout 150s adb shell am instrument -w -r -e class fr.makoff.music.playback.YouTubeVideoLiveTest "$package.test/androidx.test.runner.AndroidJUnitRunner" > smoke-results/youtube-video-live-test.txt || echo "Essai clip YouTube interrompu ou délai dépassé" >> smoke-results/youtube-video-live-test.txt
    if ! grep -Fq 'VIDEO_STREAM_PROBE_OK' smoke-results/youtube-video-live-test.txt; then
      echo "::warning::Clip vidéo YouTube non vérifié sur le réseau de l’émulateur ; voir youtube-video-live-test.txt."
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
    # Exercise configuration changes on a near-square unfolded 120Hz-style
    # display. This checks actual Activity recreation and theme animations
    # without pretending that an emulator is a physical HONOR Magic V6.
    adb logcat -c
    echo "FOLDABLE_STAGE: switching to unfolded display"
    adb_retry shell wm size 1440x1320
    adb_retry shell wm density 320
    adb_retry shell am start -W -n "$package/fr.makoff.music.MainActivity" > smoke-results/foldable-unfolded-launch.txt
    sleep 5
    adb_retry exec-out screencap -p > smoke-results/foldable-unfolded.png
    adb_retry shell dumpsys activity activities > smoke-results/foldable-unfolded-activities.txt
    python3 scripts/check_resumed_activity.py "$package" smoke-results/foldable-unfolded-activities.txt
    echo "FOLDABLE_STAGE: switching to folded display"
    adb_retry shell wm size 1080x2400
    adb_retry shell wm density 420
    sleep 4
    adb_retry exec-out screencap -p > smoke-results/foldable-folded.png
    adb_retry shell dumpsys activity activities > smoke-results/foldable-folded-activities.txt
    python3 scripts/check_resumed_activity.py "$package" smoke-results/foldable-folded-activities.txt
    echo "FOLDABLE_STAGE: restoring emulator display"
    adb_retry shell wm size reset
    adb_retry shell wm density reset
    adb_retry logcat -d > smoke-results/foldable-transitions-logcat.txt
    if grep -Fq "Process: $package," smoke-results/foldable-transitions-logcat.txt; then
      echo "::error::Crash during unfold/fold configuration change"
      exit 1
    fi
  fi
  adb shell am force-stop "$package"
done
