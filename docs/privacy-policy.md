# Brain Storm privacy policy

**App:** Brain Storm — package `com.vegasol.brainstorm.consumer` (Consumer edition) and
`com.vegasol.brainstorm.managed` (Managed edition)
**Effective date:** 18 November 2026
**Contact:** `<PRIVACY_CONTACT_EMAIL>` — replace before publishing

Brain Storm is a digital-wellbeing app that enforces the time limits you choose. There is no
account, no advertising, no analytics and no tracking. This policy describes exactly what the app
reads, what it stores and what leaves the device.

## 1. Data that stays on your device

Everything the app records is stored in a private database inside the app's own storage. Other
apps cannot read it.

| Stored | Why |
|---|---|
| Package name and display name of the apps you chose to monitor | To measure and lock only those apps |
| Foreground seconds per app, per day and per clock hour | To draw the dashboard, limits and reports |
| Lockout records (which app, which trigger, when it started and expires) | To keep a lock running for its full duration and to show history |
| Content keys (a truncated SHA-256 hash) for counted items | To avoid counting the same item twice |
| Your policy: budget, per-app limits, lock duration, reset hour | To enforce the policy you set once |

There is no account, so the app holds no name, email address, phone number, contact list,
location, calendar, photo, file, microphone or camera data. It does not request those
permissions.

## 2. Network requests

The app makes network requests in only one place: **Settings → Updates**.

- It reads the public release list from the GitHub API (`api.github.com`).
- When you accept an update it downloads `version.json` and the APK from `github.com`.
- GitHub sees your IP address and a `User-Agent` string, exactly as any browser would. GitHub's
  own privacy policy applies to those requests.
- The request is not authenticated, does not carry a device identifier, and is not linked to your
  usage data. No usage data is ever included in the request.

There are no other servers. There is no telemetry, crash reporting, advertising SDK or analytics
SDK in the app. You can confirm this in the Gradle dependency list and in the `INTERNET`
permission usage: `update/UpdateClient.kt` is the only network code.

## 3. Permissions and what they are used for

| Permission | Used for | Optional? |
|---|---|---|
| Usage access (`PACKAGE_USAGE_STATS`) | Reading Android's own foreground-time event log for the apps you selected. Required for the core feature. | Required |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Drawing the lock screen on top of a locked app. | Strongly recommended |
| Notifications (`POST_NOTIFICATIONS`) | The ongoing "monitoring" notice and update results. | Optional |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) | Keeping the monitor alive so limits are enforced on time. | Required while monitoring |
| Install unknown apps (`REQUEST_INSTALL_PACKAGES`) | Handing a verified update APK to Android's own installer. Android shows its dialog; the app never installs silently on the Consumer edition. | Optional |
| Internet / network state | The update check described above. | Required only for updates |
| Accessibility service (`BIND_ACCESSIBILITY_SERVICE`) | The optional content counter described in section 4. | Optional, off by default |

## 4. Accessibility service: optional content counting

This is **off unless you turn it on** in Android's accessibility settings. When it is on:

- Android delivers window events only for the package listed in
  `res/xml/content_accessibility_service.xml`. In release builds that is `com.google.android.youtube`
  only; debug builds additionally list the app's own debug package for testing.
- The app walks the visible window tree of that app and reads resource ids, text labels and content
  descriptions. It uses them to recognise the YouTube Shorts player and to decide whether the
  visible item changed.
- The labels are analysed in memory and then discarded. Only a 16-character hash of the item, the
  package name, the date and a timestamp are stored, so the same item is not counted twice.
- Nothing read through the accessibility service is uploaded, logged to disk or shared.
- The service does **not** perform gestures, does not read password fields and does not monitor any
  app outside the whitelist. It cannot lock an app: locks are always time-based.

Turning the service off in Android settings stops it immediately. Android also turns it off when
the app is updated or force-stopped; the dashboard reports that state instead of showing a number.

## 5. Retention and deletion

- Usage rows, hourly buckets and content keys are kept until you clear the app's data or uninstall
  the app. There is no automatic pruning and no cloud copy.
- Lockout records are kept for history and are shown in Reports.
- To delete everything: Android Settings → Apps → Brain Storm → Storage → **Clear data**, or
  uninstall the app. Because there is no account, deletion is immediate and complete.
- Uninstalling removes the policy as well. That is a deliberate, documented limit: Brain Storm is
  high-friction, not tamper-proof.

## 6. Children

The app is not directed at children under 13 and does not knowingly process data from them. It
has no social features, no ads and no user-generated content.

## 7. Changes

If a future version changes what is processed, this policy will be updated before that version is
published, and the effective date above will change. The app's source and this document live in
the same repository, so the history of both is public.

## 8. Contact

Questions or deletion help: `<PRIVACY_CONTACT_EMAIL>`.
