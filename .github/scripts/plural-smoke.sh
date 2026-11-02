#!/usr/bin/env bash
# Installs the Plural APK on the running emulator, walks through the app the
# way a person would, and saves a screenshot (plus the UI tree) of each step.
# Fails if the APK won't install, the app won't launch, or it crashes.
#
#   plural-smoke.sh <apk> <output dir>
set -euo pipefail

apk=$1
out=$2
pkg=gay.doughmination.plural
mkdir -p "$out"

ui() { adb exec-out uiautomator dump /dev/tty 2>/dev/null | sed 's/UI hierch*ary dumped to.*//' || true; }

shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "$out/$1.png"
  ui > "$out/$1.xml"
  echo "saved $1"
}

# Taps the middle of the first element whose text (or description) is exactly $1.
tap_text() {
  local bounds
  bounds=$(ui | grep -oE "(text|content-desc)=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1 \
    | grep -oE 'bounds="[^"]+"' | grep -oE '[0-9]+' | tr '\n' ' ' || true)
  if [ -z "$bounds" ]; then
    echo "::warning::Couldn't find \"$1\" on screen"
    return 1
  fi
  set -- $bounds
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

alive() {
  if ! adb shell pidof "$pkg" > /dev/null; then
    echo "::error::$pkg isn't running after: $1"
    adb logcat -d -b crash > "$out/crash.txt" || true
    cat "$out/crash.txt"
    exit 1
  fi
}

adb logcat -c
adb install -r "$apk"
adb shell dumpsys package "$pkg" | grep -E "versionCode|versionName" | head -2

adb shell am start -W -n "$pkg/.MainActivity"
# The first load fetches members, fronters, the system and relationships.
shot 01-members 10
alive "launch"

size=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
width=${size%x*}
height=${size#*x}

adb shell input swipe $(( width / 2 )) $(( height * 3 / 4 )) $(( width / 2 )) $(( height / 4 )) 400
shot 02-members-scrolled 2
alive "scrolling the members"

# Open whichever member card is in the middle of the screen.
adb shell input tap $(( width / 4 )) $(( height / 2 ))
shot 03-member 4
alive "opening a member"
adb shell input keyevent 4

tap_text "Filters" && shot 04-filters 2 && adb shell input keyevent 4 || true
alive "the filters"

tap_text "Relationships" && shot 05-relationships 5
alive "relationships"
adb shell input swipe $(( width / 2 )) $(( height * 3 / 4 )) $(( width / 2 )) $(( height / 4 )) 400
shot 06-relationships-pairs 2

tap_text "Metrics" && shot 07-metrics 8
alive "metrics"
adb shell input swipe $(( width / 2 )) $(( height * 3 / 4 )) $(( width / 2 )) $(( height / 4 )) 400
shot 08-metrics-scrolled 2

tap_text "Account" && shot 09-account 2
alive "account"

# Background and foreground again: the socket reconnects and data refreshes.
adb shell input keyevent 3
sleep 3
adb shell am start -W -n "$pkg/.MainActivity"
shot 10-resumed 5
alive "coming back from the background"

adb logcat -d > "$out/logcat.txt"
if grep -q "FATAL EXCEPTION" "$out/logcat.txt"; then
  echo "::error::The app crashed"
  grep -A 30 "FATAL EXCEPTION" "$out/logcat.txt"
  exit 1
fi
echo "Smoke test passed"
