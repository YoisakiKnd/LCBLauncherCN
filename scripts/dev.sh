#!/usr/bin/env bash
set -euo pipefail
if [[ "${LCB_ALLOW_LOCAL_BUILD:-}" != "1" ]]; then
    printf '%s\n' 'Local builds are disabled. Use GitHub Actions. On your own development computer, explicitly set LCB_ALLOW_LOCAL_BUILD=1.' >&2
    exit 2
fi
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

if [[ -d "$project_dir/.tools/jdk21" ]]; then
    export JAVA_HOME="$project_dir/.tools/jdk21"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ -d "$project_dir/.tools/android-sdk" ]]; then
    export ANDROID_HOME="$project_dir/.tools/android-sdk"
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$project_dir/.tools/gradle-user-home}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$project_dir/.tools/android-user-home}"

if [[ $# -eq 0 ]]; then
    set -- :app:assembleDebug
fi
exec ./gradlew "$@" --console=plain
