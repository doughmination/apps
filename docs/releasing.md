# Releasing

One workflow, `.github/workflows/apps.yml`, builds and releases every app.
There is one release, **`latest`**, and it always holds the newest build of
each app. It's updated in place, never one release per commit.

## What happens on a push

- **Every push to `main`**:
  1. runs the unit tests;
  2. builds all four apps, each signed with its own key, and reads each APK
     back (`aapt2`, `apksigner`): the build fails if an APK's version isn't the
     one this build set, or its certificate isn't the one pinned in
     `.github/apps.json`;
  3. writes `manifest.json` for Doughmination Manager ([manifest.md](manifest.md)).
     Apps whose sources changed get the new build; the rest keep their
     published one (see Versions). It fails if a changed app's version code
     wouldn't be higher than the published one;
  4. uploads the changed apps' APKs and, last, `manifest.json` to the
     `latest` release, and moves the `latest` tag to the commit;
  5. on an emulator, walks the System app's screens
     (`.github/scripts/plural-smoke.sh`), then has the Manager download,
     verify and install Inbox from the published release, checking the
     installed version code matches the manifest (`.github/scripts/manager-e2e.sh`).
     Screenshots of each step are attached to the run.
- **Pull requests** build, test and run the emulator checks, and publish nothing.

The release's files keep the same names, so these links always give the
newest build:

| App | Link |
|---|---|
| System | https://github.com/doughmination/apps/releases/latest/download/system.apk |
| Inbox | https://github.com/doughmination/apps/releases/latest/download/inbox.apk |
| Device Reporter | https://github.com/doughmination/apps/releases/latest/download/device-reporter.apk |
| Doughmination Manager | https://github.com/doughmination/apps/releases/latest/download/doughmination-manager.apk |

Next to them is `manifest.json`, what the Manager reads. The mail site's
`/download` sends people to `inbox.apk`.

## Versions

Every app follows the same scheme, but moves only when it changes:

```
versionName = <appVersion>.<build number>    e.g. 2.1.14
versionCode = 1000 + <build number>
```

The build number is the `apps.yml` run that last changed that app. So after a
push that only touches `plural/`, the System app becomes 2.1.15 while Inbox
stays at, say, 2.1.9, and the Manager offers only the System update.

"Changed" means the app's fingerprint changed: a hash of its directory plus
the shared files (`design/`, `gradle/`, `build.gradle.kts`, `settings.gradle.kts`,
`gradle.properties`, `gradlew`). Changing a shared file, or `appVersion`,
releases every app. The fingerprint is stored in the manifest (`sourceHash`).

There is one source of truth: CI computes the version once (`appVersion` from
`gradle.properties` and the run number) and passes it to Gradle. Every other
place a version appears (the APK, `manifest.json`, the release notes, what
the Manager shows) is read from the built APK, never typed in again.

Change `appVersion` for a new minor or major version (`2.2`, `3.0`). Run
numbers only go up, so version codes do too, which is what lets each build
install over the last. (1000 is above every code released before the apps
shared a scheme; the highest was 107.) **If `apps.yml` is ever renamed or
recreated, its run numbers restart: raise the 1000 in it above the highest
published code first.** The manifest step refuses to publish otherwise.

Local builds use `<appVersion>.0` and code 1, so they never install over a
release.

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

Pull requests build unsigned or debug-signed. A push to `main` fails rather
than publish anything without the real keys.


### The keys

Each app has a PKCS12 keystore (`<app>.p12`, alias `<app>`, the same password
for store and key), made on 2026-11-02. They live outside every repository in
`C:\Users\clove\.signing\doughmination-apps\`, with their passwords in
`passwords.txt` there. **Back that folder up** (a password manager or an
encrypted drive); if a key is lost, that app can never be updated in place
again.

| App | Certificate SHA-256 |
|---|---|
| Inbox | `79:7D:66:1F:F5:EE:00:FD:DB:E3:E0:7F:FF:07:C5:EF:AC:C3:50:8F:18:65:0F:60:F5:A6:A6:4B:95:30:A8:DD` |
| Device Reporter | `D1:D0:FB:74:BA:38:5E:69:55:BF:58:97:FD:E7:18:4F:4F:2F:1B:91:FD:0E:FA:2D:9E:A8:FF:C0:59:B8:CF:D5` |
| Doughmination Manager | `22:8D:A3:BC:CF:8B:ED:60:21:B8:E6:E0:17:C2:FE:62:F1:EC:52:9C:8C:D4:2C:8D:41:A1:35:FA:62:7B:E8:AA` |
| Plural | `F1:0D:90:FE:C9:0C:97:18:C0:06:8B:70:5A:55:92:62:05:DF:11:74:95:62:A2:7D:30:B5:16:70:40:3E:1D:C1` |

These are public: Inbox's goes in the mail server's
`public/.well-known/assetlinks.json`, and they're the `signingCertSha256`
values for the Manager manifest. CI prints each signed APK's certificate with
`apksigner`, so a release signed by the wrong key is easy to spot.

Inbox and Device Reporter were signed with different keys before the move, so
copies installed from the old repositories have to be uninstalled once before
these builds will install.

### Repository secrets

| Secret | App | Value |
|---|---|---|
| `INBOX_KEYSTORE_BASE64` | Inbox | base64 of `inbox.p12` |
| `INBOX_KEYSTORE_PASSWORD` | Inbox | its password (store and key) |
| `INBOX_GOOGLE_SERVICES_JSON` | Inbox | optional; base64 of `google-services.json`, overrides the committed one |
| `DEVICE_REPORTER_KEYSTORE_BASE64` | Device Reporter | base64 of `device-reporter.p12` |
| `DEVICE_REPORTER_STORE_PASSWORD` | Device Reporter | its password |
| `DEVICE_REPORTER_KEY_ALIAS` | Device Reporter | `device-reporter` |
| `DEVICE_REPORTER_KEY_PASSWORD` | Device Reporter | its password |
| `MANAGER_*`, `PLURAL_*` | Manager, Plural | the same four, for `manager.p12` and `plural.p12` |

To set one again from Windows PowerShell, in the keys folder:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\inbox.p12")) | gh secret set INBOX_KEYSTORE_BASE64 -R doughmination/apps
gh secret set INBOX_KEYSTORE_PASSWORD -R doughmination/apps   # paste the password when asked
```

To make a key for a new app, in Git Bash (no JDK needed; `MSYS_NO_PATHCONV`
stops Git Bash rewriting the `/CN=` subject as a path):

```sh
export MSYS_NO_PATHCONV=1
openssl req -x509 -newkey rsa:4096 -sha256 -days 10000 -nodes \
  -subj "/CN=Doughmination myapp/O=Doughmination" -keyout myapp.key.pem -out myapp.cert.pem
openssl pkcs12 -export -name myapp -inkey myapp.key.pem -in myapp.cert.pem -out myapp.p12
rm myapp.key.pem
```
