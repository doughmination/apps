# System (Plural)

A native Android app (Kotlin, Jetpack Compose) for the Doughmination System,
the plural system tracker at [doughmination.co.uk](https://doughmination.co.uk)
([doughmination/system](https://github.com/doughmination/system)). It isn't a
wrapper around the website: it talks to the same Doughmination API
(`https://doughmination.uk/v2/plural/*`) and the same live socket (`/v2/ws`),
and draws everything natively.

## What it does

Public, no sign-in needed (as on the website):

- **Members**: everyone A–Z, searchable by name or tag, filterable by tag
  (including "untagged") and pride identity. Member colours, pronouns, the
  first two tags and statuses. Whoever is fronting is highlighted.
- **Mental state** banner and **currently fronting / co-fronting**, with how
  long the current front has lasted.
- **Member pages**: about, tags and pride (tap one to filter the list by it),
  partners and crushes (mutual ones marked), and a share link to the website.
- **Relationship map**: the website's rotating 3D graph (`graph.ts` ported),
  drag to turn, tap someone to open them, then everyone in pairs.
- **Metrics**: switches and fronting time over 24h–30d, with the donut, bars
  and top fronters.
- **Live**: fronter and mental-state changes, and an admin's "refresh
  everyone", arrive over the socket while the app is open.

Signed in (admins; the website's `/admin` pages):

- Switch manager, mental state, statuses, tags, force refresh.
- Owner only: pride identities, relationships, user management.
- Profile: view and edit display name, avatar and contact email.

## Signing in

Sign-in uses the API's native app flow, which lives on the
[`plural-app-signin`](https://github.com/doughmination/api/pull/1) branch of
doughmination/api and **isn't deployed yet**:

1. The app opens `/v2/plural/auth/sso/login?app=<PKCE challenge>` in a Custom
   Tab (a real browser, so passkeys work).
2. After SSO the API redirects to `gay.doughmination.plural://auth?code=…`, a
   single-use code that expires after 60 seconds. The JWT itself never appears
   in a URL.
3. The app swaps code and PKCE verifier at `POST /v2/plural/auth/app/token`
   for the same token the website uses, kept encrypted with an Android
   Keystore key (`data/TokenStore.kt`).

Until that's deployed, the API ignores `?app` and the browser tab finishes on
the website instead, so the app stays signed out.

## Layout

```
app/src/main/java/gay/doughmination/plural/
  SystemApp.kt        the app-wide Api, Auth, Live and token store
  MainActivity.kt     catches the sign-in redirect
  data/
    Api.kt            /v2/plural/* calls
    Auth.kt           sign-in (Custom Tab + PKCE)
    Live.kt           the /v2/ws socket
    Models.kt         mirrors react-api's src/types/plural.ts
    Graph.kt          the relationship map's 3D layout (system/src/lib/graph.ts)
    Colour.kt         readable member colours (system/src/lib/colour.ts)
    Pride.kt          the flag catalogue (system/src/lib/pride.ts)
    Relations.kt      partners, crushes, mutual crushes
  ui/
    SystemViewModel   state, navigation and actions for every screen
    HomeScreen, MemberScreen, RelationshipsScreen, MetricsScreen,
    AccountScreen, AdminScreens
```

## Building

From the repository root:

```sh
./gradlew :plural:app:assembleDebug
./gradlew :plural:app:assembleRelease   # debug key unless SIGNING_* is set
```

`-PapiUrl=https://…/v2` points a build at another API.

CI: `plural.yml` builds, signs and releases it on every push to `main` that
touches `plural/` (`plural-v0.1.N`), and
`plural-smoke.yml` installs the signed APK on an emulator, walks the tabs and
uploads screenshots.
