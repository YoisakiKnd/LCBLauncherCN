#!/usr/bin/env bash
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != "true" ]]; then
    printf '%s\n' 'Container emulator checks run only in GitHub Actions. No SDK or emulator was started.' >&2
    exit 2
fi

# GitHub-hosted runner only. The fixture APK stays uninstalled inside the host's private directory.
mkdir -p container-check
sdkmanager 'emulator' 'system-images;android-36;google_apis;x86_64'
sudo chmod a+rw /dev/kvm
printf 'no\n' | avdmanager create avd --force -n lcb-api36 -k 'system-images;android-36;google_apis;x86_64'
"$ANDROID_HOME/emulator/emulator" -avd lcb-api36 -no-window -no-audio -no-boot-anim \
    -no-snapshot -gpu swiftshader_indirect -memory 2048 -cores 2 -camera-back none -camera-front none \
    > container-check/emulator.txt 2>&1 &
emulator_pid=$!
cleanup() {
    adb logcat -d -s LCBContainer:I AndroidRuntime:E > container-check/runtime.txt 2>&1 || true
    adb emu kill >/dev/null 2>&1 || true
    kill "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT
ready=false
for ((i=0; i<180; i++)); do
    if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == '1' ]]; then
        ready=true
        break
    fi
    if ! kill -0 "$emulator_pid" 2>/dev/null; then cat container-check/emulator.txt; exit 1; fi
    sleep 2
done
if [[ "$ready" != 'true' ]]; then cat container-check/emulator.txt; exit 1; fi
adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
timeout 180 adb shell am instrument -w -r \
    io.github.lcblauncher.test/androidx.test.runner.AndroidJUnitRunner | tee container-check/instrumentation.txt
python3 - <<'PY'
from pathlib import Path
result = Path('container-check/instrumentation.txt').read_text()
if 'OK (1 test)' not in result or 'FAILURES' in result or 'INSTRUMENTATION_FAILED' in result:
    raise SystemExit('Container execution test failed; inspect runtime.txt and instrumentation.txt')
print('Android 16: ordinary uninstalled APK executed in a separate process; resource, asset, data persistence and fresh-process checks passed.')
PY
