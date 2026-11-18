# Play submission pack — Brain Storm

Working notes for the Play Console submission. Everything here is derived from the shipped code,
not aspiration. If a value changes in code, change it here too.

| Field | Value |
|---|---|
| App name | Brain Storm |
| Package | `com.vegasol.brainstorm.consumer` |
| Category | Productivity |
| Ads | No |
| In-app purchases | No |
| Account / login | None |
| Target audience | 13+ (not designed for children) |
| Privacy policy | `docs/privacy-policy.md`, must be hosted at a public URL |
| Data safety | No data collected, no data shared (section 3) |
| Target SDK | 35 |

## 1. What to submit

Play receives the **consumer** flavour (`com.vegasol.brainstorm.consumer`). The managed
(Device Owner) flavour is for enterprise provisioning and is not uploaded; it uses a different
package and is built from the same source.

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
> SHA-256 and that the APK is signed by the same certificate as the installed app. On the Consumer
> edition Android always shows its own install dialog; the app never installs silently.

### 2.3 `SYSTEM_ALERT_WINDOW` (Display over other apps)

Core functionality: the lock screen is drawn over the app that just hit its limit. Without this
permission the app reports "Partially protected" instead of claiming enforcement worked.

### 2.4 `PACKAGE_USAGE_STATS` (Usage access)

Core functionality: foreground time comes from Android's usage event log, not from an
accessibility service or screen capture. Revoking it makes the dashboard report that monitoring is
paused.

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
| Phone screenshots | ≥ 2, min 1080 px on the long edge | dashboard, reports, settings, lock screen |
| Privacy policy URL | public, non-PDF | **to do** — host `docs/privacy-policy.md` |

Suggested short description: `Hard daily limits for the apps that eat your day.` (52 characters)

## 7. Pre-submission checklist

- [ ] Replace `<PRIVACY_CONTACT_EMAIL>` in the policy with a monitored address.
- [ ] Host the policy and paste the URL into the Console.
- [ ] Produce the phone screenshots. (The 512 icon and the feature graphic are done.)
- [ ] Decide on the accessibility declaration (section 4) and, if needed, build the Play variant
      without the service.
- [ ] Complete Data safety exactly as section 3.
- [ ] Complete the content rating questionnaire.
- [ ] Verify the FGS declaration text matches the manifest subtype string.
- [ ] Test the release build on at least one physical device: usage access, overlay, notifications,
      lock on time, lock survives reboot, monitoring restarts after an update.
- [ ] Confirm `com.vegasol.brainstorm.consumer` is still available at submission time.
