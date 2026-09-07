#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# Keep multi-gigabyte SDK and Gradle data off a potentially RAM-backed /tmp.
export GRADLE_USER_HOME="$project_root/.local-tools/gradle-cache"
export ANDROID_USER_HOME="$project_root/.local-tools/android-user"
export TMPDIR="$project_root/.local-tools/tmp"
mkdir -p "$GRADLE_USER_HOME" "$ANDROID_USER_HOME" "$TMPDIR"
cd "$project_root"
# One build across root/subagents. Timeout includes lock acquisition and execution.
exec timeout 1200s flock "$project_root/.local-tools/build.lock" ./gradlew --no-daemon --max-workers=1 --console plain "$@"
