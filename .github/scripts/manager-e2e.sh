#!/usr/bin/env bash
# The whole update path, on the running emulator: install Doughmination
# Manager, let it read the published manifest, then have it download, verify
# and install Inbox from the rolling release, confirming Android's own install
# prompt the way a person would. Fails unless Inbox ends up installed at
# exactly the version the manifest lists.
#
#   manager-e2e.sh <manager apk> <output dir>
set -euo pipefail

apk=$1
out=$2
pkg=gay.doughmination.manager
target=gay.doughmination.mail
manifest_url="https://github.com/${GITHUB_REPOSITORY:-doughmination/apps}/releases/latest/download/manifest.json"
mkdir -p "$out"

if ! curl -fsSL "$manifest_url" -o "$out/manifest.json"; then
  echo "::warning::No published manifest yet; skipping the Manager test."
  exit 0
fi
want_code=$(jq -r --arg p "$target" '.apps[] | select(.packageName == $p) | .latest.versionCode' "$out/manifest.json")
echo "The manifest lists $target at version code $want_code"

ui() { adb exec-out uiautomator dump /dev/tty 2>/dev/null | sed 's/UI hierch*ary dumped to.*//' || true; }

shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "$out/$1.png"
  ui > "$out/$1.xml"
  echo "saved $1"
}

# Taps the first element whose text or description matches the regex $1 exactly.
tap() {
  local bounds
  bounds=$(ui | grep -oE "(text|content-desc)=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1 \
    | grep -oE 'bounds="[^"]+"' | grep -oE '[0-9]+' | tr '\n' ' ' || true)
  if [ -z "$bounds" ]; then
    echo "::error::Couldn't find \"$1\" on screen"
    return 1
  fi
  set -- $bounds
  adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

expect() {
  if ! ui | grep -qE "text=\"$1"; then
    echo "::error::Expected \"$1\" on screen at step $2"
    exit 1
  fi
  echo "found \"$1\""
}

adb uninstall "$target" > /dev/null 2>&1 || true
adb install -r "$apk"
# What a person does once in Settings: allow the Manager to install apps.
adb shell appops set "$pkg" REQUEST_INSTALL_PACKAGES allow

adb shell am start -W -n "$pkg/.MainActivity"
shot 01-list 10
expect "Doughmination Manager" "launch"
expect "Inbox" "the app list"
expect "Not installed" "the app list"

tap "Inbox"
shot 02-detail 2
expect "What's new" "Inbox's page"
tap "Install [^\"]*"
echo "Downloading and verifying..."

# Android's confirmation prompt appears once the APK is downloaded and checked.
for i in $(seq 1 60); do
  if ui | grep -qiE 'text="(Install|INSTALL|Update|UPDATE)"[^>]*package="com\.(google\.)?android\.packageinstaller"'; then
    break
  fi
  if ui | grep -qE "doesn't match its SHA-256|isn't signed with|was interrupted|download failed|Couldn't install"; then
    shot 03-failed 0
    echo "::error::The Manager reported a failure"
    exit 1
  fi
  sleep 2
done
shot 03-confirm 0
tap "(Install|INSTALL|Update|UPDATE)"

for i in $(seq 1 30); do
  if adb shell pm list packages "$target" | grep -q "$target"; then break; fi
  sleep 2
done
shot 04-installed 3

got_code=$(adb shell dumpsys package "$target" | grep -oE 'versionCode=[0-9]+' | head -1 | cut -d= -f2)
if [ "$got_code" != "$want_code" ]; then
  echo "::error::Inbox installed at version code ${got_code:-none}, but the manifest says $want_code"
  exit 1
fi
expect "Up to date" "after installing"
echo "Manager installed Inbox $got_code, matching the manifest"

adb logcat -d > "$out/logcat.txt"
if grep -q "FATAL EXCEPTION" "$out/logcat.txt"; then
  echo "::error::An app crashed"
  grep -A 30 "FATAL EXCEPTION" "$out/logcat.txt"
  exit 1
fi
