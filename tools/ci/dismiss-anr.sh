#!/bin/sh
# Taps the "Wait" button of a system "isn't responding" dialog if one is on screen; otherwise
# does nothing (never taps blindly into the app).
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || exit 0
bounds=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep 'text="Wait"' \
  | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | head -n 1)
[ -n "$bounds" ] || exit 0
set -- $bounds
adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
echo "Dismissed an ANR dialog"
