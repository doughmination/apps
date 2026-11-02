#!/usr/bin/env bash
# Builds every app's release APK, each signed with its own key, into <out>
# under a fixed name (system.apk, inbox.apk, ...), and writes <out>/versions.json
# describing them. VERSION_NAME and VERSION_CODE come from the environment and
# are the same for every app.
#
#   build-apps.sh <out>
#
# Keys are read from $KEYS/<app>.p12 with their passwords in the environment
# (see apps.yml). Without a key an app builds with the debug key (Inbox:
# unsigned), which is fine for pull requests and never published.
set -euo pipefail

out=$1
keys=${KEYS:-${RUNNER_TEMP:-/tmp}/keys}
mkdir -p "$out"
manifest="[]"

# build <file name> <gradle module> <module dir> <package> <key name> <password> <alias>
build() {
  local file=$1 module=$2 dir=$3 package=$4 key=$5 password=$6 alias=$7
  echo "::group::$file ($module)"
  if [ -n "$password" ] && [ -f "$keys/$key.p12" ]; then
    SIGNING_STORE_FILE="$keys/$key.p12" \
    SIGNING_STORE_PASSWORD="$password" \
    SIGNING_KEY_ALIAS="$alias" \
    SIGNING_KEY_PASSWORD="$password" \
      ./gradlew "$module:assembleRelease" --console=plain
  else
    ./gradlew "$module:assembleRelease" --console=plain
  fi
  local apk
  apk=$(ls "$dir"/build/outputs/apk/release/*.apk | head -1)
  cp "$apk" "$out/$file"
  manifest=$(jq \
    --arg file "$file" \
    --arg packageName "$package" \
    --argjson versionCode "${VERSION_CODE:-1}" \
    --arg versionName "${VERSION_NAME:-dev}" \
    --arg sha256 "$(sha256sum "$out/$file" | cut -d' ' -f1)" \
    --argjson sizeBytes "$(stat -c %s "$out/$file")" \
    '. + [{$file, $packageName, $versionCode, $versionName, $sha256, $sizeBytes}]' <<< "$manifest")
  echo "::endgroup::"
}

# Inbox reads email/keystore.properties (written by apps.yml), not SIGNING_*.
build system.apk                ":plural:app"                plural/app                gay.doughmination.plural        plural          "${PLURAL_PASSWORD:-}"          plural
build inbox.apk                 ":email:app"                 email/app                 gay.doughmination.mail          inbox           "${INBOX_PASSWORD:-}"           inbox
build device-reporter.apk       ":api-helper:app"            api-helper/app            uk.doughmination.devicereporter device-reporter "${DEVICE_REPORTER_PASSWORD:-}" "${DEVICE_REPORTER_ALIAS:-device-reporter}"
build doughmination-manager.apk ":doughmination-manager:app" doughmination-manager/app gay.doughmination.manager       manager         "${MANAGER_PASSWORD:-}"         manager

jq -n \
  --arg version "${VERSION_NAME:-dev}" \
  --arg commit "${GITHUB_SHA:-}" \
  --argjson apps "$manifest" \
  '{$version, $commit, $apps}' > "$out/versions.json"
cat "$out/versions.json"
