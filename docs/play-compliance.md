# Play submission pack — Brain Storm

Working notes for the Play Console submission. Everything here is derived from the shipped code,
not aspiration. If a value changes in code, change it here too.

| Field | Value |
|---|---|
| App name | Brain Storm |
| Package | `com.vegasol.brainstorm` |
| Category | Productivity |
| Ads | No |
| In-app purchases | No |
| Account / login | None |
| Target audience | 13+ (not designed for children) |
| Privacy policy | `docs/privacy-policy.md`, must be hosted at a public URL |
| Data safety | No data collected, no data shared (section 3) |
| Target SDK | 35 |

## 1. What to submit

Play receives the same APK that is published on GitHub (`com.vegasol.brainstorm`). There is no
longer a separate Play build: the Device Owner code path is compiled in but stays inert unless the
installing device is provisioned as owner, which a Play install never is. The device-admin
declaration this adds is the item to revisit with Play policy before submission (section 4).

## 2. Permission declarations

### 2.1 Foreground service — `specialUse`

Manifest (already declared):

```xml
<service android:name=".MonitorService"
         android:exported="false"
         android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
              android:value="digital_wellbeing_app_usage_limit_enforcement" />
</service>
```

Text for the Play Console "foreground service" declaration:

> Brain Storm enforces time limits the user sets for specific apps. A foreground service is
> required to read Android's foreground-app state and to raise the lock screen the moment a limit
> is reached; without it the system pauses the monitor and a lock would fire late or not at all.
> The service shows a low-priority, ongoing notification at all times, does no network work, and
> stops when the user disables monitoring or uninstalls the app. It is not used for location,
> media playback, data sync, calls or any of the other predefined types, hence `specialUse`
> with the subtype `digital_wellbeing_app_usage_limit_enforcement`.

### 2.2 `REQUEST_INSTALL_PACKAGES`

> Used only to install an update of Brain Storm itself, which the user requests in Settings →
> Updates. Before handing the APK to Android's package installer the app verifies the published
> SHA-256 and that the APK is signed by the same certificate as the installed app. Android shows
> its own install dialog; a silent install is only attempted when the device is provisioned as
> Device Owner, which a Play install never is.

### 2.3 `SYSTEM_ALERT_WINDOW` (Display over other apps)

Core functionality: the lock screen is drawn over the app that just hit its limit. Without this
permission the app reports "Partially protected" instead of claiming enforcement worked.

### 2.4 `PACKAGE_USAGE_STATS` (Usage access)

Core functionality: foreground time comes from Android's usage event log, not from an
accessibility service or screen capture. Revoking it makes the dashboard report that monitoring is
paused.

### 2.5 Device admin (`BIND_DEVICE_ADMIN`)

The unified APK declares a `DeviceAdminReceiver` because the Device Owner enforcement path now ships
in the same package as the normal one. It only acts when the app has been provisioned as Device
Owner through `dpm set-device-owner`; a Play install can never reach that state, so the receiver
stays inert and enforcement uses the overlay. Play's device-admin policy is stricter than the old
consumer build needed to be — decide before submitting whether to drop the receiver from the Play
artefact and cut a Play-only build, exactly as section 4 discusses for accessibility.

## 3. Data safety answers

| Question | Answer | Why |
|---|---|---|
| Does the app collect or share any required user data types? | **No** | All processing is on-device. The update check sends no user data to GitHub. |
| Is any data shared with third parties? | **No** | No SDKs, no ads, no analytics. |
| Is data encrypted in transit? | Yes (HTTPS) | Only update metadata and the APK are downloaded; no user data is sent. |
| Can users request data deletion? | Not applicable | No account and no server copy. Clearing app data or uninstalling deletes everything locally (stated in the policy). |
| Data processed ephemerally | Yes | Accessibility labels are read in memory and discarded; only a hash is stored. |
| Independent security review | No | Small local app; not claimed. |

Do **not** tick "App activity → App interactions" or similar: nothing is transmitted off the
device, so declaring collection would be false.

## 4. Accessibility declaration

The service is optional and off by default. Use this text in the Play Console accessibility
declaration and the accompanying video script:

> Brain Storm's accessibility service is an optional content counter. It is disabled unless the
> user turns it on in Android settings, and it is restricted by manifest to a single package
> (`com.google.android.youtube` in release builds). It reads the resource ids and text labels of
> that window solely to recognise when a new YouTube Short is shown, so the user's
> self-reported "clips watched" figure is not invented. Labels are analysed in memory and
> discarded; only a truncated hash of the counted item is stored locally. The service performs no
> gestures, reads no password fields, monitors no other app, sends nothing off the device, and
> cannot enforce a lock — time limits are enforced independently of it, and the app works fully
> with the service switched off.

**Known risk — decide before submitting.** Google's AccessibilityService policy expects the API to
serve users with disabilities or to be the only way to deliver a core feature. A Shorts counter is
likely to be questioned. If review rejects it, ship the Play build with the service and the
`BIND_ACCESSIBILITY_SERVICE` declaration removed; the dashboard already reports content counting
as unavailable in that case. Keep the counter in the sideloaded/GitHub build. The app does not
depend on it.

### 4b. Lockout window detection (beta, opt-in)

A **second**, separate accessibility service (`LockoutAccessibilityService`) may be enabled from
Settings → Lockout detection. It listens only for `TYPE_WINDOW_STATE_CHANGED`, checks the local
lockout table, and looks at a window's package and size to notice a restricted app shown in
picture-in-picture. It does **not** read window text, perform gestures, or monitor any package that
does not already have an active lockout. Its only action is to show Brain Storm's blocking screen.

A separate Notification-listener service (`PlaybackNotificationListener`) may be enabled to let
`MediaSessionManager` report which packages have active playback. No notification content is read
or stored.

**Honest limitation:** neither service can guarantee stopping third-party audio or video on a
standard install. They are detection and disclosure mechanisms; Device Owner package suspension is
the only full enforcement path. The UI reports `PARTIAL` enforcement in standard mode.

An additional **opt-in beta toggle** ("Attempt to pause playback") uses the already-granted
Notification access to call `pause()` on media sessions belonging to locked packages. This is best
effort: an app may ignore the command or resume playback, and it is never reported as full
enforcement. Google Play may view using notification access to control playback as out of scope;
remove this toggle from a Play build if review objects. The former hidden-API `forceStopPackage()`
toggle has been removed entirely: hidden-API enforcement blocks it on modern Android and it is not
permitted in a Play build. All enforcement events now go through one coordinator with per-attempt
deduplication and capability-level reporting, so a partial result is disclosed rather than hidden.
If either declaration is rejected, both services and the pause toggle can be removed without
affecting usage accounting, lockout creation or the Device Owner path.

The optional PiP close feature extends the same lockout accessibility service with
`canRetrieveWindowContent` and `canPerformGestures`. It is off by default behind a separate toggle,
inspects only System UI windows after a restricted PiP window is detected, never reads or stores
window text, and clicks only a verified close control (no arbitrary coordinates). Because it
interacts with another app's controls through Accessibility, this capability needs its own Play
policy assessment and disclosure; the fallback is a message asking the user to close PiP manually.

An additional **assisted PiP-disable workflow** is offered in setup and Settings. It is started only
by the user after a confirmation explaining that Android settings will be changed. The accessibility
service opens app-details pages, locates a clearly labelled Picture-in-picture row and a checked
switch, clicks only that verified control, and re-reads the AppOps state to confirm the change. It
aborts on ambiguous nodes, uses per-app timeouts and never changes any other setting. This is also a
sensitive Accessibility use that needs its own Play policy assessment and disclosure; it is not
advertised as universally supported across OEM Settings layouts.

## 5. Content rating (IARC questionnaire)

| Question | Answer |
|---|---|
| Violence, sexuality, nudity, language, controlled substances | None |
| Gambling, simulated gambling | No |
| User-generated content or online interaction | No |
| Shares location | No |
| Purchases | No |
| Ads | No |
| Unrestricted web access | No |

Expected outcome: rated for everyone / 3+, subject to IARC's confirmation.

## 6. Store listing inputs

| Asset | Requirement | Status |
|---|---|---|
| App name | ≤ 30 characters | "Brain Storm" |
| Short description | ≤ 80 characters | e.g. "Hard daily limits for the apps that eat your day." |
| Full description | ≤ 4000 characters | describe the one-time freeze, budget rule, no early unlock, honest limits |
| App icon | 512 × 512 PNG, 32-bit | `docs/store/play-icon-512.png` (from `docs/store/icon.svg`) |
| Feature graphic | 1024 × 500 PNG/JPEG | `docs/store/feature-graphic-1024x500.png` (from `docs/store/feature-graphic.svg`) |
| Phone screenshots | ≥ 2, min 1080 px on the long edge | `docs/store/screenshots/01-dashboard.png` … `04-settings.png` (1080 × 2160, 2:1) |
| Privacy policy URL | public, non-PDF | **to do** — host `docs/privacy-policy.md` |

Suggested short description: `Hard daily limits for the apps that eat your day.` (52 characters)

## 7. Pre-submission checklist

- [ ] Replace `<PRIVACY_CONTACT_EMAIL>` in the policy with a monitored address.
- [ ] Host the policy and paste the URL into the Console.
- [ ] Produce the phone screenshots. (The 512 icon, the feature graphic and four phone
      screenshots are done.)
- [ ] Decide on the accessibility declaration (section 4) and, if needed, build the Play variant
      without the service.
- [ ] Decide on the device-admin receiver (section 2.5): keep it inert in the unified APK or cut a
      Play-only build without it.
- [ ] Complete Data safety exactly as section 3.
- [ ] Complete the content rating questionnaire.
- [ ] Verify the FGS declaration text matches the manifest subtype string.
- [ ] Test the release build on at least one physical device: usage access, overlay, notifications,
      lock on time, lock survives reboot, monitoring restarts after an update.
- [ ] Confirm `com.vegasol.brainstorm` is still available at submission time.
