# Changelog

All notable changes to Brain Storm are recorded here. The section whose heading matches
`versionName` in `version.properties` is copied into the GitHub release body by CI, so a release
without a matching section fails the build instead of shipping unexplained.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [2.1.0] - 2026-11-20

### Added

- Setup discovers social and video apps from `ApplicationInfo.category` instead of a hardcoded
  package list. Detected apps are pre-selected and grouped under **Social apps** and **Video
  apps**; everything else stays visible under **Other installed apps** for manual selection.
- Setup has a search field that filters installed apps by name or package, so apps that declare no
  category (for example Netflix or JioHotstar) are one query away instead of a long scroll.
- The app list refreshes when the setup step is opened and when an app is installed or removed.

### Notes

- The category comes from the app itself (its `android:appCategory`, or an installer hint). Apps
  that declare none are not guessed at. On the test phone YouTube declares **video**; Netflix,
  Instagram and JioHotstar declare nothing and therefore appear under Other. No new permissions
  and no `QUERY_ALL_PACKAGES`.

## [2.0.0] - 2026-11-19

One package, one APK. The `consumer` and `managed` builds are gone.

### Changed

- **Package name is now `com.vegasol.brainstorm`.** The `.consumer` and `.managed` suffixes are
  removed. Nothing in the app, the release assets or the updater refers to an "edition" any more;
  the only variants are the channels, **beta** and **stable**.
- **One APK per release** (`Brain-Storm-2.0.0_beta.apk`) instead of one per edition. It carries the
  Device Owner code path and only suspends packages when the device really is provisioned; on a
  normal phone it enforces through the overlay exactly as before.
- Settings shows the runtime edition: *Device Owner* when provisioned, *Standard* otherwise. It no
  longer prints a build flag.
- `version.json` is now schema 2: one `apk` entry plus the `package` it installs as.

### Fixed

- **"The download is for com.vegasol.brainstorm.consumer, not com.vegasol.brainstorm.managed."**
  Updates failed because the two editions were two different Android packages, so a download for
  one could never install over the other. With a single package that mismatch cannot occur.
- The updater now compares the package in `version.json` with the installed package **before**
  downloading, and shows the reinstall instruction instead of a 20 MB download that ends in an
  install error.

### Migrating from 1.0.0 — read this once

- 1.0.0 was published as `com.vegasol.brainstorm.consumer` and `com.vegasol.brainstorm.managed`.
  2.0.0 is a **different package**, so Android will not install it over an existing copy. Uninstall
  the old build and install the new APK once; in-app updates work normally from then on.
- The frozen policy, usage history and lockouts of the old install **do not carry over** — Android
  cannot move app data between package names. Setup runs again on the new install.
- If a phone was provisioned as **Device Owner** on `.managed`, changing the package means
  re-provisioning, which usually requires a factory reset. Remove the old owner first
  (`adb shell dpm remove-active-admin com.vegasol.brainstorm.managed/com.vegasol.brainstorm.BrainStormAdminReceiver`)
  before starting over.
- This release still lists `apks.consumer` and `apks.managed` in `version.json`, pointing at the new
  APK. That is for 1.0.0 installs only: they will show the update, download it and report the
  package mismatch honestly instead of claiming they are up to date. The keys are removed in the
  next release.
