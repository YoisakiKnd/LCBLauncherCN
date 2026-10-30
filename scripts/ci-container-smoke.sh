#!/usr/bin/env bash
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != "true" ]]; then
    printf '%s\n' 'Container emulator checks run only in GitHub Actions. No SDK or emulator was started.' >&2
    exit 2
fi

# GitHub-hosted runner only. The fixture APK stays uninstalled inside the host's private directory.
# avdmanager honors ANDROID_USER_HOME; emulator 37 still searches ANDROID_AVD_HOME first.
# Set one explicit AVD location for both tools, independent of the debug-keystore directory.
export ANDROID_AVD_HOME="${RUNNER_TEMP:?GitHub runner temp directory is required}/lcb-avd"
mkdir -p container-check "$ANDROID_AVD_HOME"
timeout 360 sdkmanager 'emulator' 'system-images;android-36;google_apis;x86_64' | tee container-check/sdk.txt
sudo chmod a+rw /dev/kvm
printf 'no\n' | timeout 120 avdmanager create avd --force -n lcb-api36 -k 'system-images;android-36;google_apis;x86_64' -p "$ANDROID_AVD_HOME/lcb-api36.avd"
test -f "$ANDROID_AVD_HOME/lcb-api36.ini"
"$ANDROID_HOME/emulator/emulator" -avd lcb-api36 -no-window -no-audio -no-boot-anim \
    -no-snapshot -gpu swiftshader_indirect -memory 2048 -cores 2 -camera-back none -camera-front none \
    > container-check/emulator.txt 2>&1 &
emulator_pid=$!
cleanup() {
    timeout 10 adb logcat -d -s LCBContainer:I AndroidRuntime:E > container-check/runtime.txt 2>&1 || true
    for image in ui-home.png ui-archive.png ui-tools.png ui-home-landscape.png; do
        timeout 5 adb exec-out run-as io.github.lcblauncher cat "cache/$image" > "container-check/$image" 2>/dev/null || rm -f "container-check/$image"
    done
    timeout 5 adb emu kill >/dev/null 2>&1 || true
    kill "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT
ready=false
boot_deadline=$((SECONDS + 360))
while ((SECONDS < boot_deadline)); do
    if [[ "$(timeout 5 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == '1' ]]; then
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
