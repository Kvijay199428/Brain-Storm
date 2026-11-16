# Brain Storm (Android)

Digital-wellbeing app: a per-app daily limit **and** a combined daily budget → fixed-duration
lockout → 3D brain mascot (Brainy).
Kotlin · Jetpack Compose · Room · WorkManager · three.js mascot (bundled offline, rendered in a WebView).

## What it does

1. **One-time setup** fixes the whole policy: the apps to watch, each app's daily limit, a
   combined daily budget, the lock duration and the daily reset hour.
2. Brainy measures real foreground time from Android's own usage event log.
3. When an app hits its own limit, that app locks. When combined usage across all monitored
   apps hits the budget, **every** monitored app locks.
4. Locks run their full configured duration. There is no early unlock anywhere in the app.

## Build

1. Android Studio (Ladybug or newer) → open the `BrainStorm` folder → let Gradle sync (JDK 17+).
2. Variant: `consumerDebug` (Play Store edition) or `managedDebug` (Device Owner edition).
3. Run on a real device (Android 10+). Grant: Usage access, Display over other apps, Notifications.

Command line:

```
./gradlew :app:assembleConsumerDebug :app:assembleManagedDebug
./gradlew :app:testConsumerDebugUnitTest :app:lintConsumerDebug
```

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

## Code map

| Area | Where |
|---|---|
| App + service locator, Room, usage engine, limit/budget rules, detectors | `Core.kt` |
| Enforcement abstraction, `LockoutManager` (only writer of restriction state), overlay blocker | `Lockout.kt` |
| Foreground service, `ReconcileWorker`, boot receiver | `Service.kt` |
| Design tokens (colour, spacing, radii, thresholds) | `theme/Theme.kt` |
| Shared UI primitives, formatters, duration picker, app icon | `ui/Common.kt`, `ui/InstalledApps.kt` |
| Mascot enum + pooled WebViews (`brainyPause`/`brainyResume`) | `mascot/Mascot.kt` |
| One-time policy wizard | `feature/setup/SetupWizard.kt` |
| Dashboard (state, pure derivation, ViewModel, components) | `feature/dashboard/**` |
| Apps list (read-only + add) and app details (read-only) | `feature/apps/AppsScreen.kt` |
| Reports: 7/30-day charts, streaks, lock history | `feature/reports/ReportsScreen.kt` |
| Settings (read-only policy + monitoring health) | `feature/settings/SettingsScreen.kt` |
| Lockout screen | `feature/lockout/LockoutScreen.kt` |
| Shell, bottom navigation, detail routing | `feature/shell/MainShell.kt` |

Dashboard state is derived by pure functions in `DashboardDerivation.kt` (`Derive`) so the
mascot mood, the progress colours and the wellness score cannot drift from what is rendered.
`DashboardViewModel` combines Room flows with a 1-second clock; the clock is consumed only by
the countdown composables.

## Wellness score

App-defined and shown as such: `60 × (1 − used/budget) + 25 × (apps under limit) + 15` for a
lockout-free day, clamped to 0–100. Explained in an in-app sheet.

## Managed edition (Device Owner)

Dev provisioning on a factory-reset test device:

```
adb shell dpm set-device-owner com.vegasol.brainstorm.managed/com.vegasol.brainstorm.BrainStormAdminReceiver
```

## Honest limits

- **Content counting is not implemented.** No `ContentDetector` ships, so `contentCount` stays 0
  and every count limit is hidden. The dashboard and reports show `Not available` rather than a
  fabricated number. Time limits work normally.
- **Media-playback detection does not exist**, so the app never claims it. Rows say
  "time only".
- **Consumer blocking is best-effort.** The user can revoke permissions or uninstall. The
  dashboard separates *monitoring active*, *limit reached* and *enforcement applied*: a failed
  `EnfResult` is stored as `NOT ENFORCED:` and the header shows "Partially protected".
- **The setup freeze is UI-level.** Without Device Owner, clearing app data or uninstalling
  resets every policy value. It is high-friction, not tamper-proof.
- **No hourly activity graph.** `daily_usage` stores per-day totals only; adding an hourly
  timeline would need a schema migration and a new table.
- **Dark theme only** in this pass; tokens are structured for a later light scheme.
- Simplified vs the blueprint: one Gradle module (packages instead of ~40 modules), no Hilt,
  SharedPreferences instead of DataStore, compileSdk 35.

## Release checklist (BS-054…062)

Unit tests for `LimitEvaluator`/`Derive` (done) · reboot + process-death tests · OEM battery
tests (Samsung, Xiaomi, OnePlus) · Play declarations: foreground service `specialUse`,
`SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS` · privacy policy + Data Safety · confirm app ID
availability · replace launcher icon.
