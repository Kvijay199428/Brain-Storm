# Brain Storm (Android)

Digital-wellbeing app: a per-app daily limit **and** a combined daily budget → fixed-duration
lockout → 3D brain mascot (Brainy).
Kotlin · Jetpack Compose · Room · WorkManager · three.js mascot (bundled offline, rendered in a WebView).

## Changelog

Release notes for every version are in [CHANGELOG.md](CHANGELOG.md). Published builds, tags and
assets are listed on the [GitHub Releases page](https://github.com/Kvijay199428/Brain-Storm/releases).

## What it does

1. **One-time setup** fixes the whole policy: the apps to watch, each app's daily limit, a
   combined daily budget, the lock duration and the daily reset hour.
2. Brainy measures real foreground time from Android's own usage event log.
3. When an app hits its own limit, that app locks. When combined usage across all monitored
   apps hits the budget, **every** monitored app locks.
4. Locks run their full configured duration. There is no early unlock anywhere in the app.
5. Optionally — off by default — an experimental detector counts new YouTube Shorts. It never
   triggers a lock; time remains the enforced limit.

## App discovery in setup

Setup lists the apps installed on the phone and asks how to choose which ones to limit:

- **Recommended** matches those apps against a catalog of 831 reviewed video and social apps
  shipped in `app/src/main/assets/recommended_apps.json`. Matching is case-insensitive on package
  name, then on a normalized app name when that name is unambiguous. Matched apps are pre-selected
  and badged; every tick can still be changed.
- **Manual** starts with nothing selected and the user marks the apps themselves.

The mode can be changed from the Apps step. The catalog is generated from
`tools/data/recommended_apps.csv` by `python tools/build_recommended_apps.py`; the source CSV is
committed so the list can be reviewed and regenerated. Android's own app category is no longer
used to pre-tick apps. Discovery uses the existing `<queries>` block for launcher activities;
`QUERY_ALL_PACKAGES` is not requested.

## Content detection (opt-in, experimental)

Time limits never depend on it. Counting is a separate feature that only runs after you enable
Android's accessibility service for Brain Storm:

- `ContentAccessibilityService` reads the labels of the apps that ship a detector. A new item is
  counted when the visible signature changes, debounced to at most one per 3 seconds.
- One detector ships: **YouTube Shorts**, marked `EXPERIMENTAL` because it matches YouTube's
  accessibility labels, not a public API. **No Instagram Reels detector ships.**
- Enable it in Settings → Content detection → Accessibility settings. Until Android reports the
  service as enabled, the dashboard says "counting is off" (or "no detector") instead of showing
  a number.
- Counts are **informational**: `LimitEvaluator` only enforces a content limit for a `SUPPORTED`
  detector, and none ships. Time limits keep working exactly as before.
- Labels are analysed on the device and are not stored; only a per-item content key (a hash) goes
  into Room, so the same item cannot be counted twice. There is no upload and no account.
- Android turns accessibility services off when an app is updated or force-stopped. The dashboard
  reports that state honestly; re-enable counting from Settings after an update.
- Debug builds add a probe activity plus a `.debug` application ID, so the whole
  service → detector → Room → dashboard pipeline can be tested without a live Shorts feed. The
  probe is absent from release APKs.
- Play policy: accessibility APIs need an explicit declaration and review. The detector stays
  beta-only until that review is done.

## Build

1. Android Studio (Ladybug or newer) → open the `BrainStorm` folder → let Gradle sync (JDK 17+).
2. Variant: `debug`; there is only one package (`com.vegasol.brainstorm`), so no edition to pick.
3. Run on a real device (Android 10+). Grant: Usage access, Display over other apps, Notifications.

Command line:

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

#### Editions are decided at runtime, not at build time

The Device Owner code path ships in every APK. `EnforcementProvider.isDeviceOwner()` asks Android;
when the answer is yes, lockouts suspend the package, and when it is no, they fall back to the
overlay. Settings → **Enforcement edition** reports which one is live.

To provision a device as owner:

```
adb shell dpm set-device-owner com.vegasol.brainstorm/com.vegasol.brainstorm.BrainStormAdminReceiver
```

Release builds are signed with `brainstorm-upload.jks`; credentials live in the gitignored
`keystore.properties` (see `keystore.properties.example`). Without them the release variant builds
unsigned instead of failing.

## Releases and in-app updates

Every push to `main` publishes a release; nothing is released by hand.

| Piece | Where |
|---|---|
| Version name + channel | `version.properties` (`versionName=3.2.1`, `channel=beta`; stable line: `3.2.0`, `stable`) |
| `versionCode` | the git commit count — strictly increases with every push |
| Tag | `v<versionName>-<channel>.<versionCode>`, e.g. `v3.2.1-beta.29` |
| Assets | `Brain-Storm-<version>_<channel>.apk`, `version.json`, `SHA256SUMS` |
| Release notes | the `CHANGELOG.md` section whose heading matches `versionName`; CI fails without one |
| CI | `.github/workflows/release.yml` |
| Local equivalent | `tools/release.ps1` (`-SkipPublish` to build + checksum only) |

Stable publishes a full release and hides the Brainy Mascot settings card; beta publishes a
pre-release and keeps the mascot controls for testing.

`version.json` is the updater's contract:

```json
{
  "schema": 2,
  "versionCode": 28,
  "versionName": "3.2.0",
  "channel": "stable",
  "tag": "v3.2.0-stable.28",
  "package": "com.vegasol.brainstorm",
  "apk": { "file": "Brain-Storm-3.2.0_stable.apk", "sha256": "…", "size": 19629365 },
  "apks": {
    "consumer": { "file": "Brain-Storm-3.2.0_stable.apk", "sha256": "…", "size": 19629365 },
    "managed":  { "file": "Brain-Storm-3.2.0_stable.apk", "sha256": "…", "size": 19629365 }
  }
}
```

`apks.consumer` / `apks.managed` exist only so **1.0.0** installs, which look up
`apks.<edition>`, can see the 2.0.0 build and report the package change instead of claiming to be
up to date. Both point at the same APK and both disappear in the release after 2.0.0.

Settings → **Updates** checks the release list at most once every six hours (or on demand), reads
`version.json`, and only offers a build whose `versionCode` is higher. Before handing anything to
Android it verifies the file's SHA-256 **and** that the APK is signed by the same certificate as the
installed app; a mismatch deletes the download and says so.

If `package` in `version.json` differs from the installed package, the app does not download at
all: Android cannot replace one package with another, so the card explains the one-time reinstall
and offers a link to the release page.

Android shows its own install dialog for every update. A silent install is only attempted when the
app really is the Device Owner, and the app says "installed silently" only after Android reports
success.

## Policy model

| Rule | Behaviour |
|---|---|
| Per-app limit | Set once when the app is added. 5-minute steps, default `1h 00m`. Immutable. |
| Daily budget | Set once in the wizard, hours + minutes. Must be **strictly below** the sum of per-app limits, otherwise it could never fire — the wizard blocks that. |
| Monitored set | Grow-only. Adding an app is the only mutation in the app; nothing can be removed, paused or edited. |
| Per-app trigger | `foregroundSeconds ≥ timeLimitMin` → that app locks (`triggerType = TIME`). |
| Budget trigger | `Σ foregroundSeconds ≥ budget` → all monitored apps lock (`triggerType = BUDGET`). |
| Lock duration | Fixed `lockHours` from activation, never shortened. |
| Reset | Usage totals roll over at `resetHour`; active locks keep running across midnight. |
| Escape hatch | None in-app. Clearing app data or uninstalling resets everything — stated in the wizard and in Settings. |

The **budget is deliberately not derived** from the per-app limits: every app already stops at
its own limit, so total usage can never exceed their sum and a derived budget would be dead
code. `budgetCheck()` in `Core.kt` encodes that rule and is unit tested.

Usage accounting merges overlapping foreground spans (a main activity and a PiP window cannot
book the same second twice) and reconciles them with Android's per-package visible time by taking
the larger value. Enforcement has an explicit confirmed state — `PENDING`, `ENFORCED`, `PARTIAL`,
`EXPIRED`, `RELEASED` — and active lockouts are rechecked independently of the foreground app.
On a standard install the overlay can only report `PARTIAL`: it cannot stop a third-party audio or
PiP session. Device Owner package suspension remains the only full enforcement path.

## Code map

| Area | Where |
|---|---|
| App + service locator, Room, usage engine, limit/budget rules, detectors | `Core.kt` |
| Enforcement abstraction, `LockoutManager` (only writer of restriction state), overlay blocker | `Lockout.kt` |
| Confirmed enforcement state transitions | `LockoutEnforcer.kt` |
| Overlap-free usage intervals and visible-time reconciliation | `UsageVisibilityTracker.kt` |
| Foreground service, `ReconcileWorker`, boot receiver | `Service.kt` |
| Design tokens (colour, spacing, radii, thresholds) | `theme/Theme.kt` |
| Shared UI primitives, formatters, duration picker, app icon | `ui/Common.kt`, `ui/InstalledApps.kt` |
| Mascot enum + pooled WebViews (`brainyPause`/`brainyResume`) | `mascot/Mascot.kt` |
| One-time policy wizard | `feature/setup/SetupWizard.kt` |
| Dashboard (state, pure derivation, ViewModel, components) | `feature/dashboard/**` |
| Apps list (read-only + add) and per-app reports (limits, hourly + 7/30-day charts) | `feature/apps/AppsScreen.kt`, `feature/apps/AppReportScreen.kt` |
| Reports: whole-device 7/30-day charts, today-by-hour, per-app ranking, streaks, lock history | `feature/reports/ReportsScreen.kt`, `feature/reports/ReportsDerive.kt` |
| Which apps count as "the device" (pure rule + cached PackageManager lookup) | `DeviceScope.kt` |
| Shared bar charts | `ui/Charts.kt` |
| Settings (read-only policy + monitoring health) | `feature/settings/SettingsScreen.kt` |
| Update check, verified download, install hand-off | `update/UpdateClient.kt`, `update/ApkInstaller.kt`, `update/UpdateManager.kt` |
| Opt-in accessibility service, Shorts detector, debug probe | `detect/**` |
| Lockout screen | `feature/lockout/LockoutScreen.kt` |
| Shell, bottom navigation, detail routing | `feature/shell/MainShell.kt` |

Dashboard state is derived by pure functions in `DashboardDerivation.kt` (`Derive`) so the
mascot mood, the progress colours and the wellness score cannot drift from what is rendered.
`DashboardViewModel` combines Room flows with a 1-second clock; the clock is consumed only by
the countdown composables.

## Wellness score

App-defined and shown as such: `60 × (1 − used/budget) + 25 × (apps under limit) + 15` for a
lockout-free day, clamped to 0–100. Explained in an in-app sheet.

## Device Owner provisioning

Optional; without it the app enforces through the overlay. On a factory-reset test device:

```
adb shell dpm set-device-owner com.vegasol.brainstorm/com.vegasol.brainstorm.BrainStormAdminReceiver
```

Changing the package (as 2.0.0 does) means re-provisioning: remove the old owner first with
`dpm remove-active-admin` on the old package name, which usually requires a factory reset.

## Play submission

The declarations, Data Safety answers, foreground-service justification and the
accessibility-declaration text live in [`docs/play-compliance.md`](docs/play-compliance.md). The
policy text to host publicly is [`docs/privacy-policy.md`](docs/privacy-policy.md) — it must be
published at a URL, and the contact placeholder must be replaced first.

## Honest limits

- **Content counting is experimental and opt-in.** One detector ships (YouTube Shorts) and it is
  `EXPERIMENTAL`: it recognises UI labels that YouTube can change at any time. There is no Reels
  detector and no content-based lock. With the accessibility service off, the UI says so instead
  of rendering `0` as if it were a measurement.
- **Media-playback detection does not exist**, so the app never claims it. Rows say
  "time only" unless a detector is available.
- **Consumer blocking is best-effort.** The user can revoke permissions or uninstall. The
  dashboard separates *monitoring active*, *limit reached* and *enforcement applied*: a failed
  `EnfResult` is stored as `NOT ENFORCED:` and the header shows "Partially protected".
- **Background monitoring is best-effort.** Android may pause background work, and some
  manufacturers (Xiaomi, Oppo, vivo, Samsung) are stricter than the platform default. Settings
  shows the battery-optimisation state, the monitor's heartbeat and a link to the system battery
  screen. The app restarts monitoring after a reboot or an app update, and a stale heartbeat is
  reported instead of pretending protection is active.
- **The setup freeze is UI-level.** Without Device Owner, clearing app data or uninstalling
  resets every policy value. It is high-friction, not tamper-proof.
- **Hourly data starts with 2.1.0.** Reports draws today's foreground time per clock hour
  (`hourly_usage`, Room v2). Time is split at hour boundaries so a session that began at 10:58 is
  credited to both 10 and 11. Days recorded before the v1→v2 migration have no hourly rows, and the
  chart shows today only — it never invents history.
- **Whole-device scope.** `daily_usage` stores every app you installed that has a launcher entry,
  plus every monitored app, with the label recorded at write time (Room v3). Brain Storm, launchers
  and the system UI are excluded. Budget, locks, streaks and the wellness score still read monitored
  apps only, so an app outside the policy can never spend the budget or trigger a lock.
- **Dark theme only** in this pass; tokens are structured for a later light scheme.
- Simplified vs the blueprint: one Gradle module (packages instead of ~40 modules), no Hilt,
  SharedPreferences instead of DataStore, compileSdk 35.

## Release checklist (BS-054…062)

Unit tests for `LimitEvaluator`/`Derive`/detector signals (done) · reboot + process-death tests · OEM battery
tests (Samsung, Xiaomi, OnePlus) · Play declarations: foreground service `specialUse`,
`SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS`, `BIND_ACCESSIBILITY_SERVICE` + accessibility policy
review · privacy policy + Data Safety · confirm app ID availability · replace launcher icon.
