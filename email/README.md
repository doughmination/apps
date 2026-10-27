# Inbox for Android

A native Android app (Kotlin, Jetpack Compose) for [Inbox](../email). It talks
to the same Worker API as the web app.

```
app/src/main/java/gay/doughmination/mail/
  MainActivity.kt      sign-in redirects and links to the site land here
  InboxApp.kt          the app-wide Api, Auth, Live and token store
  data/
    Api.kt             /api and /auth calls, bearer-signed
    Auth.kt            sign-in through the SSO in a browser tab (code + PKCE)
    TokenStore.kt      session token, encrypted with an Android Keystore key
    Live.kt            the /api/live WebSocket while the app is open
    Models.kt          mirrors email/src/shared/types.ts
  ui/
    MailViewModel.kt   state and actions for every screen
    ThreadListScreen   folders, swipe actions, long-press menu
    ThreadScreen       a conversation
    EmailView.kt       the only place email HTML is rendered
    ComposeScreen      new mail, replies, forwards, drafts
```

- **Sign-in** opens `/auth/login?app=<challenge>` in a Custom Tab. The server
  redirects to `gay.doughmination.mail://auth?code=...` and the app swaps the code
  and its PKCE verifier for a session token, sent as `Authorization: Bearer`.
- **Email HTML** renders in a WebView with JavaScript off. Links open in the
  browser, and remote content is blocked until you tap "Load remote images".
- **Admin** stays on the web; the drawer links to it.
- Links to `mail.doughmination.gay/inbox/...` open in the app, which needs the
  site's `/.well-known/assetlinks.json` to list this app's signing key.

## Building

Needs JDK 17–21 (Gradle 8.11 doesn't run on newer JDKs) and the Android SDK
(platform 35).

```sh
echo "sdk.dir=/opt/android-sdk" > local.properties
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

`-PinboxUrl=http://10.0.2.2:5173` points a build at `npm run dev` from the
emulator. Signing in locally needs `/auth/*` to reach that server too.

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

`.github/workflows/android.yml` (at the repo root) builds whenever `app/`
changes and uploads `inbox.apk` as an artifact. Each build from `main` replaces
the rolling `latest` release, and pushing a `v*` tag attaches it to a GitHub
release of its own. It signs with two repository secrets:

- `KEYSTORE_BASE64`: `base64 -w0 inbox.jks`
- `KEYSTORE_PASSWORD`: the keystore password
