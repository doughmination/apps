# Doughmination apps

The Android apps, in one Gradle build. The servers they talk to live in their
own repositories (the mail server is [doughmination/email](https://github.com/doughmination/email)).

| App | Directory | Gradle module | Application ID |
|---|---|---|---|
| Device Reporter | [`api-helper/`](api-helper/) | `:api-helper:app` | `uk.doughmination.devicereporter` |
| Inbox | [`email/`](email/) | `:email:app` | `gay.doughmination.mail` |
| Doughmination Manager | [`doughmination-manager/`](doughmination-manager/) | `:doughmination-manager:app` | `gay.doughmination.manager` |
| System (Plural) | [`plural/`](plural/) | `:plural:app` | `gay.doughmination.plural` |

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
.github/workflows/apps.yml one workflow: builds, signs and releases every app
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

Every push to `main` builds all four apps and replaces the single rolling
[`latest` release](https://github.com/doughmination/apps/releases/latest) with them,
all sharing one version. See [docs/releasing.md](docs/releasing.md).
