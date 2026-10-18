#!/usr/bin/env bash
set -euo pipefail
[[ $# == 3 ]] || { echo 'Usage: run.sh APK emulator-serial international|chinese|openmobi < cases.jsonl' >&2; exit 2; }
: "${DEV_TEMP_BASE:?Development volume required}"
[[ -d "$DEV_TEMP_BASE" ]] || exit 1
case "$3" in international|chinese|openmobi) ;; *) exit 2;; esac
root="$DEV_TEMP_BASE/build/projects/open-mobifitness"
sdk="${ANDROID_HOME:-$root/android-sdk}"
jdk="${JAVA_HOME:-$root/tools/jdk-21.0.12.1+1}"
out="$DEV_TEMP_BASE/build/projects/openmobi-differential-20261018/harness"
adb="$sdk/platform-tools/adb"
[[ "$2" == emulator-* && "$($adb -s "$2" shell getprop ro.kernel.qemu </dev/null | tr -d '\r')" == 1 ]] || exit 2
mkdir -p "$out/classes" "$out/dex"
export PATH="$jdk/bin:$PATH"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djava.io.tmpdir=${TMPDIR:-$DEV_TEMP_BASE/tmp}"
source_dir="$(cd "$(dirname "$0")" && pwd)"
javac --release 8 -Xlint:-options -cp "$sdk/platforms/android-37.0/android.jar" -d "$out/classes" "$source_dir/SequenceOracle.java" "$source_dir/../protocol-oracle/OfficialOracle.java"
"$sdk/build-tools/36.0.0/d8" --min-api 29 --lib "$sdk/platforms/android-37.0/android.jar" --output "$out/dex" "$out"/classes/*.class
remote=/data/local/tmp/openmobi-sequence-$3
"$adb" -s "$2" shell mkdir -p "$remote" </dev/null
"$adb" -s "$2" push "$out/dex/classes.dex" "$remote/harness.dex" >&2
"$adb" -s "$2" push "$1" "$remote/reference.apk" >&2
"$adb" -s "$2" shell "CLASSPATH=$remote/harness.dex:$remote/reference.apk app_process $remote SequenceOracle $3"
