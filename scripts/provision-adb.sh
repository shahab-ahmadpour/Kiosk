#!/usr/bin/env bash
# Install the release APK and make it the device owner.
# The tablet must have no Google account and no Samsung account.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
apk=${1:-"$root/app/build/outputs/apk/release/app-release.apk"}
component="com.showroom.kiosk/.AdminReceiver"

if [[ ! -f "$apk" ]]; then
  echo "APK not found: $apk" >&2
  echo "Build it with ./gradlew :app:assembleRelease" >&2
  exit 1
fi

adb start-server >/dev/null
adb wait-for-device
echo "Installing the release APK. A debug APK cannot become device owner."
adb install -r "$apk"

echo "Setting device owner."
out=$(adb shell dpm set-device-owner "$component" 2>&1 || true)
printf '%s\n' "$out"
if ! printf '%s\n' "$out" | grep -q "Success"; then
  echo "Device owner was not set. If the message mentions accounts, factory reset and skip every account." >&2
  exit 1
fi

adb shell am start -n com.showroom.kiosk/.HomeActivity
echo "Opened the kiosk. Set the PIN and turn the lock on from staff setup."
