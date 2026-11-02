# Doughmination apps

The Android apps, in one Gradle build. The servers they talk to live in their
own repositories (the mail server is [doughmination/email](https://github.com/doughmination/email)).

| App | Directory | Gradle module | Application ID | Workflow |
|---|---|---|---|---|
| Device Reporter | [`api-helper/`](api-helper/) | `:api-helper:app` | `uk.doughmination.devicereporter` | `device-reporter.yml` |
| Inbox | [`email/`](email/) | `:email:app` | `gay.doughmination.mail` | `inbox.yml` |
| Doughmination Manager | [`doughmination-manager/`](doughmination-manager/) | `:doughmination-manager:app` | `gay.doughmination.manager` | `manager.yml` |
| System (Plural) | [`plural/`](plural/) | `:plural:app` | `gay.doughmination.plural` | `plural.yml` |

Doughmination Manager is a placeholder for now: it builds and runs, and
nothing else. Plural is the System app; see [plural/README.md](plural/README.md).

## Layout

```
settings.gradle.kts       every app module is included here
build.gradle.kts          plugin versions (apply false)
gradle/libs.versions.toml one version catalog: SDK levels, plugins, libraries
<app>/app/                one Android application module per app
docs/releasing.md         tags, version codes, signing, CI secrets
docs/manifest.md          the Doughmination Manager's manifest format
.github/workflows/        one workflow per app, sharing android-app.yml
```

The apps share the build and the catalog, never each other's code: no app
depends on another, and each keeps its own namespace, application ID,
resources and signing key.

## Building

Needs JDK 17–21 (Gradle 8.11 doesn't run on newer ones) and the Android SDK
(platform 35), with `sdk.dir` in `local.properties`.

```sh
./gradlew :email:app:assembleDebug        # one app
./gradlew assembleDebug                   # all of them
```

Releases are built by CI; see [docs/releasing.md](docs/releasing.md).
