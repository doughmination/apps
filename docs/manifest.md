# Doughmination Manager manifest (proposed, v1)

**Status: proposal.** Nothing serves this yet and the Manager doesn't read it
yet. Where it's hosted, and what builds it, is up to the existing backend; this
only fixes the shape so the app and whatever produces it agree.

The Manager fetches one JSON document from a fixed URL. Publishing a new
version of any app means updating that document; the Manager itself never needs
rebuilding for it.

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-11-02T12:00:00Z",
  "apps": [
    {
      "id": "inbox",
      "packageName": "gay.doughmination.mail",
      "name": "Inbox",
      "summary": "Mail for every doughmination domain.",
      "iconUrl": "https://…/inbox.png",
      "signingCertSha256": ["AB:CD:…"],
      "latest": {
        "versionCode": 112,
        "versionName": "1.2.0",
        "apkUrl": "https://…/inbox-1.2.0.apk",
        "sha256": "…",
        "sizeBytes": 8123456,
        "minSdk": 26,
        "releasedAt": "2026-11-02T12:00:00Z",
        "releaseNotes": "Markdown.",
        "releaseUrl": "https://github.com/doughmination/apps/releases/tag/inbox-v1.2.0"
      }
    }
  ]
}
```

## Fields

| Field | Required | Notes |
|---|---|---|
| `schemaVersion` | yes | `1`. The Manager refuses a major version it doesn't know. |
| `apps[].id` | yes | Stable, URL-safe. Never reused. |
| `apps[].packageName` | yes | The applicationId; how the Manager finds the installed copy. |
| `apps[].name`, `summary`, `iconUrl` | name yes | For the list. |
| `apps[].signingCertSha256` | yes | SHA-256 of the signing certificate(s), as `apksigner verify --print-certs` prints them. The Manager won't install an APK signed by anything else. |
| `latest.versionCode` | yes | Compared with the installed `longVersionCode`. |
| `latest.versionName` | yes | Shown to people. |
| `latest.apkUrl` | yes | HTTPS. |
| `latest.sha256`, `sizeBytes` | yes | Checked after download, before installing. |
| `latest.minSdk` | no | Lets the Manager hide apps the phone can't run. |
| `latest.releasedAt`, `releaseNotes`, `releaseUrl` | no | Shown on the app's page. |

The Manager lists itself in `apps` like any other app, which is how it updates
itself.

## Where the values come from

Every release CI publishes carries `<artifact>-<version>.json` beside the APK
(see [releasing.md](releasing.md)):

```json
{
  "packageName": "gay.doughmination.mail",
  "versionCode": 112,
  "versionName": "1.2.0",
  "file": "inbox-1.2.0.apk",
  "sha256": "…",
  "sizeBytes": 8123456,
  "commit": "…"
}
```

That covers `latest` apart from the URLs and notes. The rest (`name`, `summary`,
`iconUrl`, `signingCertSha256`) changes rarely and can sit in whatever
generates the manifest.

## Open questions

- Where the manifest is served from, and whether APKs stay on GitHub Releases.
- Whether the manifest should be signed (e.g. a detached signature checked
  against a key built into the Manager). The per-app certificate check already
  stops a tampered manifest from installing a foreign APK, but not from
  offering an old version.
- Release channels (beta) are left out of v1.
