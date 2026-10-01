#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Java does not honor TMPDIR by itself; include Gradle workers and test JVMs.
if [[ -n "${TMPDIR:-}" && "${JAVA_TOOL_OPTIONS:-}" != *java.io.tmpdir=* ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.io.tmpdir=\"$TMPDIR\""
fi
if [[ -n "${DEV_TEMP_BASE:-}" ]]; then
  root="$DEV_TEMP_BASE/build/projects/open-mobifitness"
  mkdir -p "$root/project-cache" "$root/kotlin"
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$DEV_TEMP_BASE/cache/gradle}"
  export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$DEV_TEMP_BASE/cache/openmobi-android}"
  if [[ -z "${JAVA_HOME:-}" && -d "$root/tools/jdk-21.0.12.1+1" ]]; then export JAVA_HOME="$root/tools/jdk-21.0.12.1+1"; fi
  if [[ -z "${ANDROID_HOME:-}" && -d "$root/android-sdk" ]]; then export ANDROID_HOME="$root/android-sdk"; fi
  export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
  exec ./gradlew --project-cache-dir "$root/project-cache" "-Pkotlin.project.persistent.dir=$root/kotlin" "$@"
fi
exec ./gradlew "$@"
