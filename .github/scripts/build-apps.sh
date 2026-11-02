#!/usr/bin/env bash
# Builds every app's release APK, each signed with its own key, into <out>
# under a fixed name (system.apk, inbox.apk, ...), and writes <out>/versions.json
# describing them. VERSION_NAME and VERSION_CODE come from the environment and
# are the same for every app.
#
#   build-apps.sh <out>
#
# Everything in versions.json is read back from the built APK itself (aapt2
# for the package and versions, apksigner for the certificate), and the build
# fails if the APK doesn't carry the version it was asked to build. So the
# version an APK reports, the one Gradle was given and the one published are
# always the same.
#
# Keys are read from $KEYS/<app>.p12 with their passwords in the environment
# (see apps.yml). Without a key an app builds with the debug key (Inbox:
# unsigned), which is fine for pull requests and never published. With
# REQUIRE_PINNED_CERT=true, an APK whose certificate isn't the one pinned in
# .github/apps.json fails the build.
set -euo pipefail

out=$1
keys=${KEYS:-${RUNNER_TEMP:-/tmp}/keys}
mkdir -p "$out"
manifest="[]"

tools=$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)
aapt2="${tools}aapt2"
apksigner="${tools}apksigner"
pins=.github/apps.json

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

  # What the APK really is.
  local badging pkg code name minsdk cert
  badging=$("$aapt2" dump badging "$out/$file")
  pkg=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$badging")
  code=$(sed -n "s/^package: .* versionCode='\([^']*\)'.*/\1/p" <<< "$badging")
  name=$(sed -n "s/^package: .* versionName='\([^']*\)'.*/\1/p" <<< "$badging")
  minsdk=$(sed -n "s/^\(min\)\?sdkVersion:'\([0-9]*\)'.*/\2/p" <<< "$badging" | head -1)
  cert=$("$apksigner" verify --print-certs "$out/$file" 2>/dev/null \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1 || true)

  local expected_code=${VERSION_CODE:-1} expected_name=${VERSION_NAME:-}
  if [ "$pkg" != "$package" ]; then
    echo "::error::$file is package '$pkg', expected '$package'"; exit 1
  fi
  if [ "$code" != "$expected_code" ] || { [ -n "$expected_name" ] && [ "$name" != "$expected_name" ]; }; then
    echo "::error::$file reports $name ($code), but this build is ${expected_name:-?} ($expected_code)"; exit 1
  fi
  if [ "${REQUIRE_PINNED_CERT:-false}" = true ]; then
    local pinned
    pinned=$(jq -r --arg f "$file" '.[$f].signingCertSha256 // ""' "$pins" | tr -d ':' | tr 'A-F' 'a-f')
    if [ -z "$cert" ] || [ "$cert" != "$pinned" ]; then
      echo "::error::$file is signed by ${cert:-nothing}, not the key pinned in $pins ($pinned)"; exit 1
    fi
  fi
  echo "$file: $pkg $name ($code), minSdk $minsdk, certificate ${cert:-none}"

  manifest=$(jq \
    --arg file "$file" \
    --arg packageName "$pkg" \
    --argjson versionCode "$code" \
    --arg versionName "$name" \
    --argjson minSdk "${minsdk:-1}" \
    --arg signingCertSha256 "$cert" \
    --arg sha256 "$(sha256sum "$out/$file" | cut -d' ' -f1)" \
    --argjson sizeBytes "$(stat -c %s "$out/$file")" \
    '. + [{$file, $packageName, $versionCode, $versionName, $minSdk, $signingCertSha256, $sha256, $sizeBytes}]' <<< "$manifest")
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
