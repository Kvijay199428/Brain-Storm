# Changelog

All notable changes to Brain Storm are recorded here. The section whose heading matches
`versionName` in `version.properties` is copied into the GitHub release body by CI, so a release
without a matching section fails the build instead of shipping unexplained.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [3.2.3] - 2026-11-24

Beta of the centralized enforcement engine and PiP protection tools.

### Added

- PiP compatibility scanner: reports each monitored app's per-app PiP access state in Settings and
  as an "PiP enabled" badge in the Apps list, with a link to Android's app settings for manual
  disabling.
- Opt-in beta auto-dismissal: during an active lockout, a verified System UI PiP Close control can be
  clicked through the lockout accessibility service, with one controlled attempt per PiP appearance
  and verification of the result.
- `EnforcementCoordinator` centralises all lockout enforcement. Foreground, PiP and media detectors
  report events; only the coordinator shows the blocker and requests playback control.
- Per-attempt deduplication (`EnforcementAttempt`): a new attempt starts only on a not-visible →
  visible transition, so duplicate events can no longer reopen the blocker in a loop.
- `PipWindowDetector` combines package identity, visibility, window type and relative bounds.
- Media-session playback callbacks with bounded pause retries (1 s, 2 s) and state verification.
- Capability-level `EnforcementReport` (visual block, PiP, playback pause, process termination)
  stored in new Room v5 columns; the dashboard shows which capability is partial.

### Removed

- Hidden-API `ForceStopper` and the force-stop toggle/preference. Standard Android cannot reliably
  terminate another app's process; this is now reported honestly instead of attempted.

## [3.2.2] - 2026-11-23

Beta of the opt-in playback controls.

### Added

- Opt-in beta "Attempt to pause playback": when Notification access is granted, a locked app's
  media session receives a pause command from the detector. Best effort only — apps may ignore or
  resume, and this is never reported as full enforcement.
- EXPERIMENTAL "Force stop locked app" toggle attempts the hidden `forceStopPackage` API. It is
  blocked on most modern Android versions, is not allowed in Play builds, and never claims success
  unless the call actually returns.

## [3.2.1] - 2026-11-23

Beta of the layered PiP-safe enforcement work.

### Added

- Explicit confirmed enforcement states per lockout (`PENDING`, `ENFORCED`, `PARTIAL`, `EXPIRED`,
  `RELEASED`) with a Room v4 migration; a requested restriction is never reported as enforced.
- Periodic enforcement of **all** active lockouts, independent of which app is in the foreground.
- Overlap-free usage intervals and per-package visible-time reconciliation, so a PiP window and its
  main activity cannot double-count, and PiP-visible time can count toward the same limit.
- Two separate opt-in detectors in Settings: an accessibility service that notices PiP-sized
  windows of locked apps, and a Notification-access based media-session monitor for background
  playback. Neither reads notification content or window text.

### Changed

- Standard (non-Device-Owner) installs now report `PARTIAL` enforcement: an overlay can block the
  screen but cannot terminate third-party audio or PiP playback. Device Owner package suspension
  remains the full enforcement path.

## [3.2.0] - 2026-11-23

Next stable line. Version numbers increase across channels, so this release follows the 3.1.1 beta
instead of reusing a lower stable number.

### Added

- Setup asks whether to use **Recommended** or **Manual** app selection. Recommended matches
  installed apps by package or app name against a catalog of 831 reviewed video/social apps shipped
  in the APK, pre-selects the matches and lets the user change any tick; Manual starts empty.
- The catalog is generated from a committed CSV by `tools/build_recommended_apps.py`, with pure,
  unit-tested case-insensitive matching.

### Changed

- Android's own app category no longer pre-ticks apps in setup; only the shipped catalog does.

## [3.1.1] - 2026-11-23

Beta of the next line: the same usage reports and pinned headers as 3.0.0, with the **Brainy
Mascot** controls back in Settings for beta testers.

### Added

- Setup now asks whether to use **Recommended** or **Manual** app selection. Recommended matches
  installed apps by package or app name against a catalog of 831 reviewed video/social apps shipped
  in the APK, pre-selects the matches and lets the user change any tick; Manual starts empty.
- The catalog is generated from a committed CSV by `tools/build_recommended_apps.py`, with pure,
  unit-tested case-insensitive matching.

## [3.0.0] - 2026-11-23

First stable release.

### Added

- Today's app ranking lives on the Apps tab: monitored apps are ordered by foreground time, each
  row carries a rank and the most-used app is called out.
- All-time ranking: a card on Reports opens a full screen that ranks every recorded app by total
  foreground time across the whole usage log. Rows open the existing per-app report.
- Adding an app is now a full page with its own loading animation and a separate daily-limit step,
  instead of two stacked dialogs.
- Content detection gained a vertical-feed detector and refreshed Shorts signals.

### Changed

- Reports no longer repeats the daily app ranking or the lock history. Lock history remains on each
  app's own report, where it already lived.
- The lock overlay shows the icon of the app that was just blocked. The lock screen leads with the
  "close the phone and read something" nudge and drops the early-unlock explainer.
- Page headers on Reports, app details, All-time ranking and Settings stay put while their content
  scrolls, like Home and Apps.
- One shared back button across detail pages; the unused dashboard notification bell is gone.
- The Brainy Mascot card is hidden in the stable channel; beta builds keep it.

### Fixed

- Garbled Unicode text in the Apps tab (the lock notice, the unlock countdown and the frozen-limit
  hint) now renders as plain text.
- Ranking, lifetime totals and Apps ordering are produced by pure, unit-tested derivations.

## [2.2.0] - 2026-11-21

### Changed

- The dashboard no longer repeats the monitored-app list. App management lives on the Apps tab;
  Home keeps the budget, slot metrics, lockout and mascot cards.
- App details are now a full per-app report: today's limit progress, a 24-hour chart with the peak
  hour called out, and 7-day and 30-day trends with the daily limit marked in red when exceeded.
- Report charts are shared UI components, so the Reports tab and the app report use the same
  scales and labels.
- Sub-minute time reads "<1m" instead of "0m", so a short session never looks like a broken
  counter.

### Added

- Peak hour, best day, daily average and active-day insights per app, derived by pure functions
  with unit tests (`AppReportDerive`).
- Tapping an app in the Reports "Today by app" list opens its report.
- Whole-device usage: `daily_usage` now stores every app you installed that has a launcher entry,
  plus every monitored app, and keeps the app label with the row (Room v3, schema 3).
- Reports now ranks today's apps by foreground time, names the top app and its share of device
  time, and shows whole-device 7-day, 30-day and by-hour charts.
- Reports rows open a report for any recorded app; apps outside the policy are marked "Not
  limited" and show their usage without a limit or lock section.

### Changed

- Budget, streaks, locks and the wellness score still read monitored apps only. Storing other apps
  cannot make them use up the budget or trigger a lock.
- Non-monitored rows are only written when an app has been used today, and rows are rewritten only
  when the time or the label changed.

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
