# Doughmination Manager

Installs and updates the Doughmination Android apps (System, Inbox, Device
Reporter, and itself) from the rolling `latest` release, in the spirit of
ReVanced Manager. Native Kotlin and Jetpack Compose.

## What it does

- Lists every app in the release's [`manifest.json`](../docs/manifest.md),
  with the installed version (as Android reports it) next to the published one.
- Marks each app: not installed, up to date, update available, newer than the
  release, signed with a different key, or needs a newer Android.
- Downloads APKs into its cache. An interrupted download resumes from where it
  stopped, and a cancelled one can be resumed later.
- Checks every download before installing it: size and SHA-256 against the
  manifest, then the APK's package name, version code and signing certificate
  as Android reads them. Anything that doesn't match is deleted, not installed.
- Installs with Android's `PackageInstaller`. Android asks once to allow the
  Manager as an install source, and asks the person to confirm every install;
  the Manager never gets around either.
- For a copy signed with an older key (Inbox and Device Reporter from before
  the move), it explains why it can't update and opens Android's uninstall prompt.
- Each app's page shows the release notes (the commits that touched it since
  the last release), size, build time, SHA-256 and signing certificate.
- Keeps the last list that loaded, so it still opens offline.

## Layout

```
app/src/main/java/gay/doughmination/manager/
  MainActivity.kt          re-reads installed apps on every resume
  data/Manifest.kt         manifest model and validation
  data/Status.kt           installed vs published -> status (unit tested)
  data/ManifestSource.kt   fetch + last-good cache
  data/Packages.kt         installed packages and APK inspection
  data/Downloader.kt       resumable download, size + SHA-256 check
  install/Installer.kt     PackageInstaller session and status receiver
  ui/                      ViewModel, list and detail screens, theme
```

## Building

```sh
./gradlew :doughmination-manager:app:testDebugUnitTest
./gradlew :doughmination-manager:app:assembleDebug
```

`-PmanifestUrl=https://…/manifest.json` points a build at another manifest.

A debug build is signed with the debug key, so it shows itself as "different
signing key"; it can still install the other apps.
