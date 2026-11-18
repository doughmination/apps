# Doughmination Manager manifest (v1)

Doughmination Manager reads one JSON document:

https://github.com/doughmination/apps/releases/latest/download/manifest.json

CI writes it (`.github/scripts/make-manifest.sh`) on every push to `main` and
attaches it to the rolling `latest` release, last, after the APKs. Publishing a
new build of any app never needs a new Manager.

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-11-02T20:00:00Z",
  "commit": "0b6f8154…",
  "apps": [
    {
      "id": "inbox",
      "packageName": "gay.doughmination.mail",
      "name": "Inbox",
      "summary": "Mail for every Doughmination domain.",
      "signingCertSha256": ["79:7D:66:…"],
      "latest": {
        "versionCode": 1012,
        "versionName": "2.1.12",
        "apkUrl": "https://github.com/doughmination/apps/releases/latest/download/inbox.apk",
        "sha256": "…",
        "sizeBytes": 8123456,
        "minSdk": 26,
        "releasedAt": "2026-11-02T20:00:00Z",
        "releaseNotes": "- Commit subjects since the previous release that touched email/",
        "releaseUrl": "https://github.com/doughmination/apps/releases/tag/latest"
      }
    }
  ]
}
```

## Where each value comes from

| Field | Source |
|---|---|
| `packageName`, `versionCode`, `versionName`, `minSdk` | Read from the built APK with `aapt2 dump badging`. The build fails if they differ from the version CI meant to build. |
| `sha256`, `sizeBytes` | The APK file as uploaded. |
| `signingCertSha256` | Pinned in `.github/apps.json`. On `main` the build fails if the APK's certificate (from `apksigner`) isn't that one. |
| `id`, `name`, `summary` | `.github/apps.json`. |
| `releaseNotes` | `git log` of the app's directory since its own previous release. |
| `sourceHash` | Fingerprint of the app's directory plus the shared build files. When it matches the published one, CI keeps the published entry and APK instead (docs/releasing.md). |
| `sourceCommit` | The commit the app was last built from; its next release notes start there. |
| `commit` | The commit this manifest was written from. |

CI also refuses to publish if a changed app's `versionCode` would not be
higher than the one in the manifest already published, so a re-run of an old build can
never roll anything back.

## What the Manager does with it

- Refuses any `schemaVersion` other than 1, and drops entries without an HTTPS
  URL, a 64-hex `sha256`, a size, or a certificate.
- Compares `latest.versionCode` with the installed `longVersionCode`: lower is
  an update, equal is up to date, higher is "newer than the release" (a local
  build); nothing is ever downgraded.
- Shows "different signing key" (with an uninstall button) when the installed
  copy's certificate isn't in `signingCertSha256`: Android won't update it in
  place.
- After downloading (resumable; files are named by version code), checks size
  and SHA-256, then reads the APK with PackageManager and checks its package
  name, version code and certificate against the manifest. Only then does it
  hand it to PackageInstaller, where Android asks the person to confirm.
- Keeps the last good manifest, so the list still shows offline.

The Manager lists itself, which is how it updates itself.

Adding an app: add it to `build-apps.sh`, to `.github/apps.json`, and to the
`<queries>` list in the Manager's `AndroidManifest.xml` (Android 11+ hides
other packages otherwise).

## Not in v1

- A signature over the manifest. The certificate check already stops a
  tampered manifest from installing a foreign APK, and version codes never go
  down, but a tampered manifest could still withhold an update.
- Release channels (beta).
