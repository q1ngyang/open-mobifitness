#!/usr/bin/env bash
set -euo pipefail
# JSONL requests on stdin; JSONL results on stdout. Only use a dedicated emulator.
if [[ $# -lt 2 ]]; then echo 'Usage: run.sh <reference.apk> <emulator-serial> [international|chinese|openmobi]' >&2; exit 2; fi
: "${DEV_TEMP_BASE:?DEV_TEMP_BASE must point to the development volume}"
[[ -d "$DEV_TEMP_BASE" ]] || { echo 'Development volume unavailable' >&2; exit 1; }
project_tools="$DEV_TEMP_BASE/build/projects/open-mobifitness"
oracle_sdk="${ANDROID_HOME:-$project_tools/android-sdk}"
oracle_java="${JAVA_HOME:-$project_tools/tools/jdk-21.0.12.1+1}"
oracle_build="$DEV_TEMP_BASE/build/projects/openmobi-compat-oracle"
oracle_adb="$oracle_sdk/platform-tools/adb"
oracle_serial="$2"
[[ "$oracle_serial" == emulator-* ]] || { echo 'Use a dedicated emulator, not a personal device' >&2; exit 1; }
[[ "$($oracle_adb -s "$oracle_serial" shell getprop ro.kernel.qemu </dev/null | tr -d '\r')" == 1 ]] || { echo 'Target is not an emulator' >&2; exit 1; }
mkdir -p "$oracle_build/classes" "$oracle_build/dex"
export PATH="$oracle_java/bin:$PATH"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.io.tmpdir=${TMPDIR:-$DEV_TEMP_BASE/tmp}"
oracle_source="$(cd "$(dirname "$0")" && pwd)"
javac --release 8 -Xlint:-options -cp "$oracle_sdk/platforms/android-37.0/android.jar" -d "$oracle_build/classes" "$oracle_source/OfficialOracle.java"
"$oracle_sdk/build-tools/36.0.0/d8" --min-api 29 --lib "$oracle_sdk/platforms/android-37.0/android.jar" --output "$oracle_build/dex" "$oracle_build"/classes/*.class
"$oracle_adb" -s "$oracle_serial" shell mkdir -p /data/local/tmp/openmobi-oracle </dev/null
"$oracle_adb" -s "$oracle_serial" push "$oracle_build/dex/classes.dex" /data/local/tmp/openmobi-oracle/harness.dex >&2
"$oracle_adb" -s "$oracle_serial" push "$1" /data/local/tmp/openmobi-oracle/reference.apk >&2
oracle_utils=com.blankj.utilcode.util.q
if [[ "${3:-international}" == chinese ]]; then oracle_utils=com.blankj.utilcode.util.z; fi
if [[ "${3:-international}" == openmobi ]]; then oracle_utils=openmobi; fi
"$oracle_adb" -s "$oracle_serial" shell "CLASSPATH=/data/local/tmp/openmobi-oracle/harness.dex:/data/local/tmp/openmobi-oracle/reference.apk app_process /data/local/tmp/openmobi-oracle OfficialOracle $oracle_utils"
