# Inbox for Android

A native Android app (Kotlin, Jetpack Compose) for [Inbox](https://github.com/doughmination/email). It talks
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
    Models.kt          mirrors src/shared/types.ts in doughmination/email
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
- **Notifications**: when Resend's webhook delivers mail, the Worker sends a
  Firebase Cloud Messaging data message to each signed-in install, and
  `Push.kt` shows it; a tap opens the thread. The app registers its FCM token
  after sign-in and removes it on sign-out.
- **Admin** stays on the web; the drawer links to it.
- Links to `mail.doughmination.gay/inbox/...` open in the app, which needs the
  site's `/.well-known/assetlinks.json` to list this app's signing key.

## Building

From the repository root (see [the root README](../README.md) for JDK and SDK):

```sh
./gradlew :email:app:assembleDebug       # email/app/build/outputs/apk/debug/app-debug.apk
./gradlew :email:app:assembleRelease     # email/app/build/outputs/apk/release/app-release.apk
```

`-PinboxUrl=http://10.0.2.2:5173` points a build at `npm run dev` from the
emulator. Signing in locally needs `/auth/*` to reach that server too.

Notifications need Firebase: put the project's `google-services.json` at
`email/app/google-services.json` (next to its `build.gradle.kts`). Without it
the app still builds, with notifications off and Settings saying so.

Signing reads `email/keystore.properties` (gitignored), with `storeFile`
relative to `email/`:

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

`.github/workflows/inbox.yml` builds whenever `email/` (or the shared Gradle
setup) changes and uploads the APK, named by version (`inbox-1.0.112.apk`), as
a release of its own (`inbox-v1.0.112`). Each build from `main` also replaces the
rolling `inbox-latest` release, which the mail site's `/download` link
redirects to, and pushing an `inbox-v1.2.0` tag attaches `inbox-1.2.0.apk` to a release of its own. Secrets
(`INBOX_KEYSTORE_BASE64`, `INBOX_KEYSTORE_PASSWORD`, optionally
`INBOX_GOOGLE_SERVICES_JSON`) and version codes are in
[docs/releasing.md](../docs/releasing.md).

Builds up to 1.0.7 were released from doughmination/email; they're still on
its releases page, and these install over them.
