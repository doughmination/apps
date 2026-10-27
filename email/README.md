# Inbox for Android

An Android app for [Inbox](https://github.com/doughmination/email). It's a
[Trusted Web Activity](https://developer.chrome.com/docs/android/trusted-web-activity):
the live site at https://mail.doughmination.gay, shown full-screen in Chrome,
with its Web Push notifications delivered as the app's own.

Full-screen only works while the site's `/.well-known/assetlinks.json` lists
this app's signing key. If it doesn't match, the app still works but shows a
small address bar at the top.

## Building

Needs JDK 17+ and the Android SDK (platform 35).

```sh
echo "sdk.dir=/opt/android-sdk" > local.properties
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Signing reads `keystore.properties` (gitignored):

```properties
storeFile=inbox.jks
storePassword=...
keyAlias=inbox
keyPassword=...
```

Keep `inbox.jks` backed up. An APK signed with a different key can't be
installed over this one, and the fingerprint in `assetlinks.json` would need
changing too.

## CI

`.github/workflows/build.yml` builds on every push and uploads `inbox.apk` as
an artifact. Each build from `main` replaces the rolling `latest` release, so
the newest APK is always at
https://github.com/doughmination/email-app/releases/download/latest/inbox.apk.
Pushing a `v*` tag also attaches it to a GitHub release of its own. It signs
with two repository secrets:

- `KEYSTORE_BASE64`: `base64 -w0 inbox.jks`
- `KEYSTORE_PASSWORD`: the keystore password
