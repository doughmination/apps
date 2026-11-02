# Releasing

One workflow, `.github/workflows/apps.yml`, builds and releases every app
together. There is one release, **`latest`**, and it always holds the newest
build of all four apps.

## What happens on a push

- **Every push to `main`** builds all four apps, each signed with its own key,
  checks the signatures, and replaces the `latest` release with them. Then it
  installs the System app on an emulator and walks its screens
  (`.github/scripts/plural-smoke.sh`); screenshots are attached to the run.
- **Pull requests** build and run the emulator test, and publish nothing.

The release's files keep the same names, so these links always give the
newest build:

| App | Link |
|---|---|
| System | https://github.com/doughmination/apps/releases/latest/download/system.apk |
| Inbox | https://github.com/doughmination/apps/releases/latest/download/inbox.apk |
| Device Reporter | https://github.com/doughmination/apps/releases/latest/download/device-reporter.apk |
| Doughmination Manager | https://github.com/doughmination/apps/releases/latest/download/doughmination-manager.apk |

Next to them, `versions.json` lists each APK's package name, version, SHA-256
and size, for the Manager's manifest ([manifest.md](manifest.md)). The mail
site's `/download` sends people to `inbox.apk`.

## Versions

Every app carries the same version:

```
versionName = <appVersion>.<build number>    e.g. 2.1.14
versionCode = 1000 + <build number>
```

`appVersion` lives in `gradle.properties`; change it there for a new minor or
major version (`2.2`, `3.0`). The build number is the `apps.yml` run number, so
it only ever goes up, and so do version codes, which is what lets each build
install over the last. (1000 is above every code released before the apps
shared a version; the highest was 107.) **If `apps.yml` is ever renamed or
recreated, its run numbers restart: raise the 1000 in it above the last
release's code first.**

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
