#!/usr/bin/env bash
# Writes <dist>/manifest.json, the document Doughmination Manager reads
# (docs/manifest.md), and <dist>/publish.txt, the APKs that need uploading.
#
#   make-manifest.sh <dist> [previous manifest.json]
#
# Inputs: <dist>/versions.json (what build-apps.sh read back from each APK),
# .github/apps.json (names, summaries, pinned certificates), git history, and
# the manifest that's published now.
#
# Each app gets a new version only when its sources changed. An app's
# fingerprint covers its own directory and the shared build files; when it
# matches the published one, the app keeps its published entry and APK, so the
# Manager offers no update for it. Otherwise the freshly built APK is
# published, and:
#   - its release notes are the commits that touched its directory since its
#     own last release;
#   - its versionCode must be higher than the published one, or the script
#     fails and nothing is published.
set -euo pipefail

dist=$1
previous=${2:-}
[ -n "$previous" ] && [ -s "$previous" ] || previous=""
repo=${GITHUB_REPOSITORY:-doughmination/apps}
download="https://github.com/$repo/releases/latest/download"
release_url="https://github.com/$repo/releases/tag/latest"
now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
head_commit=$(git rev-parse HEAD)

# Files every app's build depends on. Changing any of them rebuilds every app.
shared="gradle build.gradle.kts settings.gradle.kts gradle.properties gradlew"

fingerprint() {
  local paths="$1 $shared" p
  for p in $paths; do echo "$p $(git rev-parse "HEAD:$p")"; done | sha256sum | cut -d' ' -f1
}

apps="[]"
: > "$dist/publish.txt"
for file in $(jq -r '.apps[].file' "$dist/versions.json"); do
  built=$(jq --arg f "$file" '.apps[] | select(.file == $f)' "$dist/versions.json")
  meta=$(jq --arg f "$file" '.[$f]' .github/apps.json)
  if [ "$meta" = null ]; then
    echo "::error::$file has no entry in .github/apps.json"; exit 1
  fi
  dir=$(jq -r '.dir' <<< "$meta")
  package=$(jq -r '.packageName' <<< "$built")
  code=$(jq -r '.versionCode' <<< "$built")
  source_hash=$(fingerprint "$dir")

  published="null"
  if [ -n "$previous" ]; then
    published=$(jq --arg p "$package" '[.apps[] | select(.packageName == $p)][0] // null' "$previous")
  fi

  if [ "$published" != null ] && [ "$(jq -r '.sourceHash // ""' <<< "$published")" = "$source_hash" ]; then
    echo "$file: unchanged, keeping $(jq -r '.latest.versionName' <<< "$published")"
    apps=$(jq --argjson app "$published" --argjson meta "$meta" \
      '. + [$app + {id: $meta.id, name: $meta.name, summary: $meta.summary}]' <<< "$apps")
    continue
  fi

  if [ "$published" != null ]; then
    old=$(jq -r '.latest.versionCode' <<< "$published")
    if [ "$code" -le "$old" ]; then
      echo "::error::$file would be version code $code, but $old is already published. Version codes must only go up (docs/releasing.md)."
      exit 1
    fi
  fi

  # What changed in this app since its own last release.
  since=$(jq -r '.sourceCommit // ""' <<< "$published")
  if [ -n "$since" ] && git cat-file -e "$since^{commit}" 2>/dev/null; then
    notes=$(git log --no-merges --format='- %s' "$since..$head_commit" -- "$dir" | head -40)
    [ -n "$notes" ] || notes="- Rebuilt for a change to the shared build setup."
  else
    notes=$(git log --no-merges --format='- %s' -10 -- "$dir")
  fi

  echo "$file: changed, publishing $(jq -r '.versionName' <<< "$built")"
  echo "$file" >> "$dist/publish.txt"
  apps=$(jq \
    --argjson built "$built" \
    --argjson meta "$meta" \
    --arg url "$download/$file" \
    --arg notes "$notes" \
    --arg now "$now" \
    --arg releaseUrl "$release_url" \
    --arg sourceHash "$source_hash" \
    --arg sourceCommit "$head_commit" \
    '. + [{
      id: $meta.id,
      packageName: $built.packageName,
      name: $meta.name,
      summary: $meta.summary,
      signingCertSha256: [$meta.signingCertSha256],
      sourceHash: $sourceHash,
      sourceCommit: $sourceCommit,
      latest: {
        versionCode: $built.versionCode,
        versionName: $built.versionName,
        apkUrl: $url,
        sha256: $built.sha256,
        sizeBytes: $built.sizeBytes,
        minSdk: $built.minSdk,
        releasedAt: $now,
        releaseNotes: $notes,
        releaseUrl: $releaseUrl
      }
    }]' <<< "$apps")
done

jq -n --arg now "$now" --arg commit "$head_commit" --argjson apps "$apps" \
  '{schemaVersion: 1, generatedAt: $now, commit: $commit, apps: $apps}' > "$dist/manifest.json"
cat "$dist/manifest.json"
echo "To publish: $(tr '\n' ' ' < "$dist/publish.txt")"
