#!/bin/sh
# Checks a release APK against what the app promises: exactly the permissions listed in
# tools/ci/permissions.allowed (no network, no foreground service, nothing new from a library
# update), a packaged Baseline Profile, and zip alignment (4-byte entries; uncompressed native
# libraries, if any, on 16 KB pages). Prints the APK's size. Usage: check-apk.sh <apk>
set -eu
apk="$1"
aapt2="$ANDROID_HOME/build-tools/37.0.0/aapt2"
zipalign="$ANDROID_HOME/build-tools/37.0.0/zipalign"
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

# -v lists every entry: print the whole list only when the check fails.
if aligned=$("$zipalign" -c -P 16 -v 4 "$apk" 2>&1); then
  echo "$aligned" | tail -n 1
else
  echo "$aligned"
  echo "::error::The APK is not zip-aligned (4 bytes; 16 KB pages for native libraries)"
  exit 1
fi

bytes=$(wc -c < "$apk" | tr -d ' ')
echo "APK size: $bytes bytes ($(awk -v b="$bytes" 'BEGIN { printf "%.2f MiB", b / 1048576 }'))"
echo "Permissions, Baseline Profile and alignment as promised."
