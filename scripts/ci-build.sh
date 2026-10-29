#!/usr/bin/env bash
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != "true" ]]; then
    printf '%s\n' 'This build entry point runs only in GitHub Actions. No build was started.' >&2
    exit 2
fi

exec ./gradlew --no-daemon --max-workers=2 \
    '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' \
    -PprobeTestHeap=768m -PprobeTestMetaspace=384m \
    :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
