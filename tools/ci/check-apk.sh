#!/bin/sh
# Checks a release APK against what the app promises: exactly the permissions listed in
# tools/ci/permissions.allowed (no network, no foreground service, nothing new from a library
# update), and a packaged Baseline Profile. Usage: check-apk.sh <apk>
set -eu
apk="$1"
aapt2="$ANDROID_HOME/build-tools/37.0.0/aapt2"
here=$(dirname "$0")

dump=$("$aapt2" dump permissions "$apk")
echo "$dump"
actual=$(echo "$dump" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p" | sort -u)
expected=$(grep -v '^#' "$here/permissions.allowed" | sed '/^$/d' | sort -u)
if [ "$actual" != "$expected" ]; then
  echo "::error::The APK's permissions differ from tools/ci/permissions.allowed"
  echo "Expected:"; echo "$expected"
  echo "Actual:"; echo "$actual"
  exit 1
fi

unzip -l "$apk" | grep -qE "assets/dexopt/baseline\.prof$" || {
  echo "::error::The APK has no Baseline Profile"
  exit 1
}
echo "Permissions and Baseline Profile as promised."
