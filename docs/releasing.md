# Releasing

Each app releases on its own: a tag for one app builds and publishes only that
app. All of it runs in `.github/workflows/android-app.yml`, called by each
app's workflow.

## When CI runs

An app's workflow runs when a push to `main` (or a pull request) touches its
directory, the shared Gradle files (`gradle/`, `*.gradle.kts`,
`gradle.properties`, `gradlew*`), or its workflow, and whenever its release tag
is pushed. Every run builds the release APK and uploads it as a run artifact.

| App | Release tag | Also |
|---|---|---|
| Device Reporter | `device-reporter-v2.1.0` | "Run workflow" with a version creates the tag |
| Inbox | `inbox-v1.2.0` | every build from `main` replaces the `inbox-latest` release |
| Doughmination Manager | `manager-v0.1.0` | "Run workflow" with a version creates the tag |
| Plural | `plural-v0.1.0` | "Run workflow" with a version creates the tag |

```sh
git tag inbox-v1.2.0 && git push origin inbox-v1.2.0
```

Releases are never marked as the repository's "Latest" (only one release can
be, and there are several apps). Link to a specific tag instead, as the mail
site's `/download` does with `inbox-latest`.

Each release carries the APK (`inbox-1.2.0.apk`) and a metadata file next to
it (`inbox-1.2.0.json`) with the package name, version code and name, SHA-256
and size, for building the Manager manifest ([manifest.md](manifest.md)).

## Version codes

Android only installs an update whose `versionCode` is higher than the
installed one, so CI sets it:

```
versionCode = 100 + the workflow's run number
versionName = the tag's version (1.2.0), or <prefix>.<versionCode> for untagged builds
```

The base of 100 keeps every app above the builds it shipped before moving here
(Inbox reached 7 in doughmination/email, Device Reporter 2 in
doughmination/api-helper). Run numbers only ever go up, but they belong to the
workflow file: **if you rename or recreate a workflow, raise its
`version_code_base` above the last code it published**. Local builds use the
fallbacks in each `build.gradle.kts`, which are lower, so a local build won't
install over a CI one.

## Signing

Every app keeps its own key for good. An APK signed with any other key can't
be installed over an existing install, so losing a key means users have to
uninstall first. Keystores never go in git (`*.jks`, `*.keystore` and
`keystore.properties` are ignored); keep them backed up.

The apps read their key two ways, as they did before the move:

- **Inbox** reads `email/keystore.properties` (`storeFile` is relative to
  `email/`). Its key alias is `inbox`.
- **Device Reporter, Manager, Plural** read `SIGNING_STORE_FILE`,
  `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, and use
  the debug key when they're not set.

Pull requests build unsigned or debug-signed. Anything published (a tag, a
manual release, `inbox-latest`) fails rather than ship without the real key.

### Repository secrets

Set them with `gh secret set NAME -R doughmination/apps < file` (or paste the
value when it asks). Base64 a keystore with `base64 -w0 key.jks`.

| Secret | App | Value |
|---|---|---|
| `INBOX_KEYSTORE_BASE64` | Inbox | base64 of `inbox.jks` |
| `INBOX_KEYSTORE_PASSWORD` | Inbox | its store (and key) password |
| `INBOX_GOOGLE_SERVICES_JSON` | Inbox | optional; base64 of `google-services.json`, overrides the committed one |
| `DEVICE_REPORTER_KEYSTORE_BASE64` | Device Reporter | base64 of its keystore |
| `DEVICE_REPORTER_STORE_PASSWORD` | Device Reporter | store password |
| `DEVICE_REPORTER_KEY_ALIAS` | Device Reporter | key alias |
| `DEVICE_REPORTER_KEY_PASSWORD` | Device Reporter | key password |
| `MANAGER_*`, `PLURAL_*` | Manager, Plural | same four as Device Reporter, once they have keys |
| `CODEBERG_USERNAME`, `CODEBERG_REPO`, `CODEBERG_TOKEN` | all | the Codeberg mirror (`codeberg.yml`) |

Make a new app's key once, and back it up before its first release:

```sh
keytool -genkeypair -v -keystore manager.jks -alias manager \
  -keyalg RSA -keysize 4096 -validity 10000
```
