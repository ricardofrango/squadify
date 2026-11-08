#!/usr/bin/env bash
# Installs a release APK on the running emulator, opens it as a tap on its icon would, and fails if the app
# crashed or isn't running and in front after a while (android-release-check.yml, TECHNICAL_DECISIONS §17).
#
#   start-release-apk.sh <apk> <application id>
set -euo pipefail

apk="$1"
package="$2"
wait_seconds=20

adb install -r "$apk"
adb logcat -c
adb shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 > /dev/null
sleep "$wait_seconds"

failed=""
if adb logcat -d -b crash | grep -q "Process: $package"; then
  failed="it crashed"
elif [ -z "$(adb shell pidof "$package" | tr -d '\r')" ]; then
  failed="it isn't running"
elif ! adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | grep -q "$package/"; then
  failed="it isn't in front"
fi

if [ -n "$failed" ]; then
  echo "::error::$package didn't start: $failed ${wait_seconds}s after opening it"
  echo "--- Crash log"
  adb logcat -d -b crash || true
  echo "--- The app's log"
  adb logcat -d | grep -E "AndroidRuntime|$package" | tail -200 || true
  exit 1
fi
echo "$package started and is running after ${wait_seconds}s"
