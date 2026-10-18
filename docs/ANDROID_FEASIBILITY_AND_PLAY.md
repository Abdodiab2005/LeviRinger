# Android feasibility and Google Play

**Verdict: CONDITIONAL GO.** Automatic alerts work only when (1) Leviro holds the call-screening role, (2) the user has turned off battery optimization for Leviro, which is the documented basis for starting the alert service from the background, and (3) notifications are allowed so the Stop action is reachable. Without (2) the app falls back to a notification or a full-screen card, and sound cannot start unattended. DND modes that block alarms cannot be bypassed.

## Orchestrator audit (2026-10-18)

The analysis below was drafted by Codex without network access, so it marks platform claims "PK". The orchestrator re-checked the deciding claims against live pages:

| Claim | Result | Source |
|---|---|---|
| Background FGS start exemptions include "the user turns off battery optimizations for the app" | Verified, exemption 13 of 14 | developer.android.com/develop/background-work/services/fgs/restrictions-bg-start |
| Call screening or Telecom binding is a documented FGS exemption | Verified absent from the list | same page |
| `SYSTEM_ALERT_WINDOW` exemption needs a visible overlay when targeting 15+ | Verified | same page |
| New apps and updates must target API 36 | Verified, since 2026-08-31 (Wear/Auto 35, TV/XR 34) | developer.android.com/google/play/requirements/target-sdk |
| `USE_FULL_SCREEN_INTENT` default grant only for calling/alarm core function; declaration required; graceful degradation when denied | Verified | support.google.com/googleplay/android-developer/answer/13392821 |
| All foreground service types are reviewed; `specialUse` only when no defined type fits | Verified | same page |
| CallScreeningService contact/presentation filtering and the 5 s response deadline | Not verified (page body did not load); kept as "prior knowledge, verify" | developer.android.com/reference/android/telecom/CallScreeningService |

Decisions taken where the draft left a choice:

1. Keep `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and the direct request (installed-build parity). It is the only documented exemption that fits. Play acceptable-use eligibility is a review risk, recorded for the Play declaration.
2. DND: best effort. Any interruption filter other than ALL counts as "ordinary ringing suppressed", so a DND mode that already lets this caller ring can produce a second sound. This is disclosed in the app. `INTERRUPTION_FILTER_UNKNOWN` is treated like ALL, so the ringer mode decides.
3. Ring volume 0 in NORMAL mode counts as silent and allows the alert.
4. Full-screen card stays optional (`showAlertCard`), checked with `canUseFullScreenIntent()`, with heads-up notification fallback.
5. No overlay, no Accessibility, no audio focus request, no global volume change by default. Raising the alarm stream is an opt-in setting.
6. Removed permissions: `READ_CALL_LOG`, `ACCESS_NOTIFICATION_POLICY`, `MODIFY_AUDIO_SETTINGS`, `RECEIVE_BOOT_COMPLETED`. `allowBackup="false"` so the priority list stays on the device.

## Codex analysis (drafted without network access)

Package: `net.leviro.ringer`. Scope: native Kotlin, carrier calls, local storage, offline, no backend. SDKs: min 29; target/compile 36. Test device supplied by owner: realme RMX3636, Android 15/API 35. No device tests were performed for this document. [Task requirements.]

## Evidence status

- **Observed** means inspected repository code. Paths below are relative to `app/src/main/java/net/leviro/ringer/` unless specified.
- **PK** means **from prior knowledge, verify**. Every platform or Play claim marked PK is unverified in this session. Bracketed source numbers identify official pages to check.
- **Inference** means a design recommendation or conclusion, not a documented platform guarantee.
- Documentation retrieval failed: shell HTTPS failed DNS resolution for `developer.android.com`; the in-app browser was unavailable. No source page below was opened. No quotation is presented as verified.

## Current architecture: observed facts

- `app/build.gradle`: minSdk 23; targetSdk/compileSdk 34. `app/src/main/AndroidManifest.xml` already declares screening, phone-state receiver, and a `specialUse` FGS with subtype property.
- `call/RingerCallScreeningService.kt`: responds with the default allowing response before incoming-direction, master-switch, and local number-match checks. A match calls `AlarmController.trigger`.
- `call/PhoneStateReceiver.kt`: API 29+ ignores RINGING. OFFHOOK/IDLE stop only when process-local `AlarmController.active` is true. Older versions read the incoming-number extra.
- `util/AlarmController.kt`: posts an unconditional full-screen notification and attempts an FGS start. Exceptions are swallowed. It does not check ringer mode, interruption filter, or ring volume.
- `service/AlarmService.kt`: foregrounds immediately; loops `MediaPlayer` with `USAGE_ALARM`; vibrates; raises shared `STREAM_ALARM` to maximum; restores it in `onDestroy`; uses a 3-minute partial wake-lock timeout; returns `START_NOT_STICKY`.
- `alert/AlertActivity.kt`: starts the service again on creation. Its current volume handling is `onKeyDown`, not `dispatchKeyEvent`. Neither notification contains a Stop action. Opening the activity can restart playback after a stale notification.
- `util/Prefs.kt` has no delay, schedule, or playback-gain setting. `db/Vip.kt` has per-contact enabled state and sound path, but no schedule. Master/contact edits do not cancel an active alert.
- `util/PhoneMatch.kt`: platform comparison plus last-nine-digit fallback. That fallback can match different international numbers. `VipDao` uses exact normalized lookup first.
- `util/PermissionHelper.kt` requests `READ_CALL_LOG` and direct battery exemption. Its role check treats an unavailable screening role as no role needed. This must become an unsupported-device state.
- `util/Notifications.kt` creates a high-importance silent channel and requests channel DND bypass. The source comment that alarm usage already bypasses DND is too broad. `BootReceiver` only creates channels; it does not restore an alert.
- SQLite stores priority contacts. Preferences store settings. `RingtoneStore` copies chosen audio into private files. No network client is present in the inspected Kotlin; no INTERNET permission is declared. Manifest `allowBackup="true"` needs review before promising data never leaves the device.

## Chosen architecture

**Inference:** retain the existing screening -> controller -> one alert FGS structure. Give the FGS ownership of delay, player, call-state checks, cancellation, and one current call generation. Keep SQLite and private audio files. No dialer role, call-log access, overlay, backend, exact alarm, or permanent idle FGS.

**PK [1,8]:** request `RoleManager.ROLE_CALL_SCREENING` on API 29+. Only one chosen screening app holds this role. Keep the exported screening service protected by `android.permission.BIND_SCREENING_SERVICE`. Incoming screening must respond within 5 seconds. Respond immediately, before local I/O, explicitly allowing without reject, disallow, silence, or skip-log/notification flags. Never answer, end, or reroute the carrier call.

**PK [1]:** screening provides eligible call handles without `READ_CALL_LOG`. Saved-contact calls are delivered only if the screening app has `READ_CONTACTS`. Restricted, unknown, unavailable, or payphone presentation can prevent delivery; only eligible `tel:` calls are screened. A null/empty handle cannot identify a priority contact. Do not promise every carrier or VoIP call is covered.

**PK [2,9,10]:** retain the manifest `ACTION_PHONE_STATE_CHANGED` receiver with `READ_PHONE_STATE`; the action is exempt from the implicit-broadcast ban. Use state only, never its incoming-number extra. Optionally register `TelephonyCallback.CallStateListener` on 31+ or `PhoneStateListener` on 29–30 while the FGS runs. Neither callback supplies caller identity for this design. Broadcast exemption permits delivery; it does not itself authorize an FGS start.

**Inference:** screening can precede PHONE_STATE RINGING. A match creates a candidate, not proof that the call is already ringing. Establish fresh ringing state before playback. Do not reject a new candidate solely because a pre-ring state snapshot is IDLE. Bound this pending phase; stop on a subsequent terminal state or timeout. Ambiguous ordering or concurrent-call identity must fail closed.

```text
IDLE --incoming screening match; allow call immediately--> MATCHED
MATCHED --eligible FGS starts; startForeground immediately--> WAITING_FOR_DELAY
WAITING_FOR_DELAY --PHONE_STATE RINGING--> record fresh ringing; keep waiting
WAITING_FOR_DELAY --delay elapsed (0-30s) + fresh RINGING + rules pass--> ALERTING
MATCHED / WAITING_FOR_DELAY / ALERTING --OFFHOOK or terminal IDLE--> STOPPED
MATCHED / WAITING_FOR_DELAY / ALERTING --user Stop--> STOPPED
MATCHED / WAITING_FOR_DELAY / ALERTING --app/contact disabled or deleted--> STOPPED
MATCHED / WAITING_FOR_DELAY / ALERTING --schedule no longer active--> STOPPED
MATCHED / WAITING_FOR_DELAY / ALERTING --ringer becomes normally audible--> STOPPED
MATCHED / WAITING_FOR_DELAY / ALERTING --new call--> STOPPED (invalidate old generation)
STOPPED --cancel timer; release sound/vibration/wake lock; remove notification--> IDLE
IDLE --new eligible screening match, if concurrency is unambiguous--> MATCHED
MATCHED --no legal FGS start / permission missing / rules fail--> STOPPED
WAITING_FOR_DELAY --timeout / delay elapsed but state or rules fail--> STOPPED
ALERTING --playback error / bounded alert timeout--> STOPPED
```

**Inference:** start the monotonic delay at the screening match; delay 0 still requires fresh RINGING. At playback time re-read master/contact state, schedule, ringer mode/filter, and current ringing state. Re-check during playback and on relevant changes. Serialize events; ignore stale timer, media-prepared, and Stop actions by generation. User Stop latches for that call. Duplicate delivery must not restart it. UI must send commands, never independently create playback.

**Inference:** do not gate OFFHOOK/IDLE cleanup solely on a static Boolean. Cancel pending work as well as active sound. Stop the existing service without starting a new FGS just to stop it. A stale candidate must not survive process restart. Retain `START_NOT_STICKY`; accept missed alerts after process death. Require a fresh call for recovery. A wake-lock timeout alone does not stop looping audio: add an explicit bounded session timeout.

## FGS start: the main feasibility gate

**PK [5]:** Android 12+ restricts background FGS starts for apps targeting 31+. An ineligible start can throw `ForegroundServiceStartNotAllowedException`. `specialUse`, the screening role, and `READ_PHONE_STATE` are not independently listed blanket exemptions. The documented exemptions include the user disabling battery optimizations for the app.

**PK [5,11]:** the proposed basis is the actual battery-optimization exemption, checked with `PowerManager.isIgnoringBatteryOptimizations(packageName)`. Merely declaring `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, opening its settings, or receiving screening callbacks is insufficient. Re-check eligibility before each start; handle revocation and start failure. Once started, promote immediately; do not wait 0–30 seconds before `startForeground`.

**Unknown:** whether Telecom's binding grants this app a temporary background-FGS allowance on each supported release/OEM. No verified documentation establishes it here. Do not build correctness on a presumed binding exemption. Verify the current restriction page and test API 31–36. Any additional exemption needs a documented basis.

**PK [11]; inference:** Play restricts direct battery-exemption requests to acceptable core-function cases. This app's timely incoming-call alert is an argument, not proof of eligibility. If the request is ineligible, remove that manifest permission and direct request. A settings link does not guarantee the user changes the actual exemption. Without an actual documented exemption, unattended playback is unavailable. A notification tap can bring visible UI forward, then start playback after revalidation, but user interaction does not satisfy the automatic-alert promise.

**PK [6,15]:** target 34+ requires a declared FGS type and its permission. `specialUse` requires `FOREGROUND_SERVICE_SPECIAL_USE` and `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. It is reviewed by Play. The type is not permission to start from the background. Do not use `phoneCall` merely because a carrier call exists: this app does not manage calls through the required dialer/self-managed-call facilities.

**Inference:** describe the whole service honestly: incoming-call-triggered pending delay plus short alarm playback, immediate notification, cancellation on call end or user Stop. Confirm whether Play accepts `specialUse` for that combined purpose. `mediaPlayback` describes audio playback but cannot justify arbitrary pre-playback waiting or bypass start restrictions. Do not change types merely to evade review.

## Redundant ringing, DND, audio, and keys

| Case | Evaluation and chosen behavior |
| --- | --- |
| NORMAL + `INTERRUPTION_FILTER_ALL` | **Inference:** skip app audio when `STREAM_RING` volume is above 0. This covers the requested ordinary audible-ring case using public state, but is not proof of physical audibility. [PK APIs: 12,13] |
| NORMAL + ring volume 0 | **Inference:** the proposed two-value predicate would skip incorrectly. Include `getStreamVolume(STREAM_RING)`; zero is a candidate for alert. Test how each device couples zero volume to ringer mode. [PK: 12] |
| SILENT or VIBRATE | **Inference:** candidate for alarm audio, subject to all other rules and DND. Silent ringer state does not guarantee the alarm stream is audible. [PK: 12,14] |
| NORMAL + DND priority/starred/repeat-caller exception | **Inference:** reject “any non-ALL filter means alert” as exact suppression detection. It can duplicate an already-permitted carrier ringtone. Global filter state does not identify whether this specific call will audibly ring. Policy inspection can improve a heuristic, not establish universal certainty. [PK: 13,14] |
| DND total silence or alarms disallowed | **PK [13,14]:** `USAGE_ALARM` classifies audio. It does not bypass every DND policy. `INTERRUPTION_FILTER_NONE` suppresses interruptions; priority rules can disallow alarms. No guaranteed app sound. Do not secretly turn DND off. |
| Unknown interruption filter/state | **Inference:** fail closed; no automatic alarm on an unknown state. Show setup/diagnostic status when UI is opened. |
| Bluetooth, hearing aids, wired audio, car audio | **PK [14,18]; inference:** routing and focus vary by device/accessory. Alarm usage is not a guaranteed handset-speaker route. Test connected/disconnected transitions. Do not change call routing or force speakerphone. |
| Ring state changes while alerting | **Inference:** stop if calls become normally audible or rules stop passing. There remains a race between state updates and physical ringtone output. No claim of perfect deduplication. |

**PK [12,13]:** `getRingerMode()`, `getStreamVolume()`, and `getCurrentInterruptionFilter()` do not require `ACCESS_NOTIFICATION_POLICY` merely to read these values. Notification-policy access is needed for protected policy changes; obtaining it does not itself bypass DND. Remove that permission for the chosen read-only design, along with the DND-access onboarding request and `setBypassDnd(true)` assumption. Do not silently change interruption filters or ringer settings.

**Inference:** release default: suppress audio for NORMAL/ALL/positive ring volume; allow a best-effort candidate in silent/vibrate and supported DND configurations; disclose DND exception duplicates. If product requires zero duplicates whenever DND already permits a call, this design is insufficient. Choose a narrower supported DND scope or another validated approach before release.

**PK [14,18]:** use looping `MediaPlayer` with alarm attributes and per-player left/right gain `0.0–1.0` for the 0–100% setting. This is gain, not a percentage of system loudness; it cannot overcome stream volume 0, DND, audio focus, routing, or hardware limits. For target 35+, requesting audio focus requires a top app or running FGS. Request after foreground promotion, handle denial/loss, and release focus on stop. Never seize telephony audio focus to interfere with an answered call.

**Inference:** remove automatic shared `STREAM_ALARM` maximization from the default design. It affects other alarms; `onDestroy` restoration is not guaranteed after a kill. Optional future explicit opt-in must warn about shared volume, save once per session, avoid overwriting later user changes, and provide crash recovery with a user-visible decision for ambiguous state. Even that cannot guarantee perfect restoration. Keep this feature out of the initial modernization. [PK API behavior: 12,17]

**PK [19]; inference:** without privileged/global interception, this design receives volume keys only through its own focused activity's event dispatch. Implement `dispatchKeyEvent` for volume up/down there, consuming the relevant down/up sequence. The dialer, keyguard, or another focused window may receive the keys instead. No Accessibility service or global key capture. Always provide notification Stop and on-screen Stop; the notification action targets an explicit, non-exported component with an immutable PendingIntent.

## Notification, lock screen, and overlays

**PK [13,16,20]; inference:** use an `IMPORTANCE_HIGH`, `CATEGORY_CALL` alert notification with Stop, content intent, and privacy-conscious lock-screen text. Keep notification sound off to avoid a third sound source. High importance/category request presentation; they do not guarantee heads-up, DND bypass, FSI, or dialer privileges. User channel settings and OEM behavior still apply.

**PK [16,21]:** Android 14+ restricts `USE_FULL_SCREEN_INTENT`; check `NotificationManager.canUseFullScreenIntent()` and offer system settings only if keeping the feature. Play limits default grants to apps whose core function is calling or alarms and revokes default access for ineligible apps. A priority-call sound companion is not automatically an eligible calling/alarm app. User special-access grant and Play default-grant eligibility are separate questions. File the required declaration if retained; do not assume approval.

**Inference:** make FSI optional. With permission, post the system-mediated full-screen intent; use `setShowWhenLocked`/`setTurnScreenOn` in the card. Never rely on direct background `startActivity`. With FSI denied, fall back to the high-importance notification and heads-up where permitted. DND, denied notifications, or disabled channels can suppress even that fallback. FSI and `CATEGORY_CALL` do not authorize an otherwise illegal FGS start.

**PK [7,5]; inference:** do not add `SYSTEM_ALERT_WINDOW`. On Android 15 for target 35+, the overlay-based FGS exemption requires permission plus a currently visible overlay. Permission alone is insufficient. Creating an overlay just to obtain the exemption adds intrusive UI, special access, and Play review risk. It still would not solve DND audio or call identity. No overlay is justified here.

## Edge cases

| Case | Required behavior / limit |
| --- | --- |
| Saved vs unsaved numbers | **PK [1]:** READ_CONTACTS is needed for screening delivery of saved contacts. **Inference:** request it for saved priority callers; without it, support only eligible unsaved/manual numbers. A contact-picker URI grant is not equivalent to this permission for screening delivery. |
| Private/restricted ID; null handle | **PK [1]:** some presentations are excluded before callback. **Inference:** skip null, empty, unsupported-scheme handles. Never infer identity from recent calls or alert for every private caller. |
| Local vs +country formats | **Observed:** last-nine fallback exists. **Inference:** replace ambiguous suffix matching with country-aware canonical matching; use an explicit region when needed. Never assume +20 globally. Test trunk prefixes, roaming, short numbers, and collisions. |
| Dual-SIM and call waiting | **PK [2,9]; inference:** aggregate phone state does not reliably correlate every screened handle with every concurrent call. Callbacks can be subscription-scoped, but this needs explicit correlation. Initial policy: stop/suppress on ambiguity, existing OFFHOOK, or overlapping calls. No promise of call-waiting alerts. Test both SIMs and rapid state ordering. |
| Repeated calls / duplicate events | **Inference:** deduplicate callbacks within one generation. A new call after confirmed terminal state gets a new generation and delay. Do not let an old IDLE or Stop cancel a new call without checking ownership; suppress if correlation is uncertain. DND repeat-caller rules may already make that next call audible. |
| Quick hangup during delay | **Inference:** OFFHOOK/IDLE invalidates the timer; recheck ringing at expiry and before asynchronous player start. No stale sound after hangup. |
| Truecaller-like app holds screening role | **PK [8]; inference:** explain replacement of the selected screening app. If user declines, no number matching; leave the carrier call untouched. No call-log fallback. |
| Process death during delay/playback | **PK [17]; inference:** no durable timer or guaranteed cleanup callback. Player dies with process; shared-volume restoration can fail. Do not replay stale candidates after restart. Next eligible call can start a new session. |
| OEM battery killers / force-stop | **PK [11,17]; inference:** a battery exemption and wake lock do not guarantee execution against all OEM restrictions or force-stop. Offer device-specific guidance; no automatic settings changes. Re-test RMX3636 locked, idle, overnight, and after reboot. |

## Permissions and roles

All Android/Play assertions in this table are **PK: from prior knowledge, verify** against the linked sources. Fallbacks and keep/remove choices are **inference**.

| Permission/role | Purpose | How the user grants it | Target-36 behavior | Play policy status | Fallback if denied |
| --- | --- | --- | --- | --- | --- |
| ROLE_CALL_SCREENING | Eligible caller handle without call logs | RoleManager system role dialog | Available from 29; verify available and held; one chosen screening app [1,8] | Role is not a call-log or FGS policy waiver | Disable automatic matching; explain role conflict |
| READ_CONTACTS | Saved-contact screening delivery and contact selection | Runtime permission | Dangerous permission; handle revocation [1,22] | Personal/sensitive data; purpose disclosure; no upload [4] | Eligible unsaved/manual callers only |
| READ_PHONE_STATE | RINGING/OFFHOOK/IDLE; no number | Runtime permission | Required for chosen receiver/callback [2,9] | Sensitive data; request only for call lifecycle [4] | Disable automatic playback; safe stopping cannot be established |
| POST_NOTIFICATIONS | Alert, Stop, visible FGS notification | Runtime permission on 33+ | Denial does not prohibit FGS start; FGS still needs notification; visibility differs [20] | Explain alerts; respect denial | Chosen product gates automatic alerts off until usable Stop notification exists |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_SPECIAL_USE | Short pending-delay and alarm service | Manifest normal permissions; no runtime dialog | Declare type/property; promote immediately; independent start eligibility [5,6] | FGS declaration and specialUse justification/review [15] | No unattended service alert; UI test only |
| USE_FULL_SCREEN_INTENT | Optional lock-screen card | Manifest; special-access settings on 34+ | Check canUseFullScreenIntent; default grant limited [16,21] | FSI declaration if kept; core calling/alarm eligibility reviewed | High-importance notification; heads-up only if allowed |
| ACCESS_NOTIFICATION_POLICY | Not needed for chosen read-only checks | Special DND-access settings if changing policy | Reads above do not require it; target 35+ policy changes are scoped differently [7,13] | Remove unnecessary special access | Read state; respect DND; no bypass promise |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Request actual exemption; candidate FGS-start basis | User confirms system exemption dialog | Manifest permission alone grants no exemption; check actual state [5,11] | Direct requests restricted to acceptable core use cases; justify necessity | General settings guidance; no automatic alert unless actual eligibility established |
| WAKE_LOCK | Keep short delay/playback CPU work alive | Manifest normal permission | Bounded partial lock; release every terminal path [23] | Explain background work as part of FGS use | No guaranteed timing if removed; do not claim it prevents process kills |
| VIBRATE | Optional alert vibration | Manifest normal permission | System/DND/user settings can suppress [14] | Ordinary declared use | Audio/notification only |
| RECEIVE_BOOT_COMPLETED | Current receiver only recreates channels | Manifest normal permission | Boot does not restore roles, grants, or stale alerts; type-specific FGS restrictions apply [7,10] | Unneeded here; remove | Create channels in Application/service; wait for next eligible call |
| MODIFY_AUDIO_SETTINGS | Current shared alarm-volume changes; audio-focus use if required by chosen APIs | Manifest normal permission | Does not grant DND bypass; volume changes can have protected effects [12,18] | Minimize shared-setting changes | Remove if no retained API requires it; use per-player gain; verify audio-focus implementation first |
| SYSTEM_ALERT_WINDOW (not planned) | Draw overlay | Separate system special-access screen | Target 35+: overlay FGS exemption also needs visible overlay [5,7] | Extra sensitive access/review burden; not a policy shortcut | Standard notification/card; do not add |
| READ_CALL_LOG (remove) | Legacy number capture | Runtime permission, subject to restricted-permission rules | Unneeded with screening on min 29 [1,24] | Restricted by Play SMS/Call Log policy; role screening is not blanket authorization [24] | Screening only; no fallback log access |

**Inference — proposed manifest cleanup, not performed:** remove `READ_CALL_LOG` and all legacy number-extra code/requests. Remove `ACCESS_NOTIFICATION_POLICY` and its setup flow for the read-only design. Remove `RECEIVE_BOOT_COMPLETED` and the channel-only boot receiver. Do not add `WRITE_CALL_LOG`, `SYSTEM_ALERT_WINDOW`, Accessibility, dialer role, or exact-alarm permissions. Remove `MODIFY_AUDIO_SETTINGS` only after verifying the retained focus APIs do not need it. Remove `USE_FULL_SCREEN_INTENT` if the optional card is dropped. Remove `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` if direct-request eligibility is not established; then revisit the automatic-start feasibility gate.

## Target 36 modernization and proof

- **Task requirement; PK [3]:** use minSdk 29, targetSdk/compileSdk 36. The supplied current Play rule requires API 36 for new apps and updates; verify the live deadline, exceptions, and Play Console status before submission. This is a requirement here, not a claim that the live page was checked.
- **PK [25,26]:** edge-to-edge is enforced on Android 15 for apps targeting 35+; Android 16 removes the opt-out for apps targeting 36 on Android 16. Handle system-bar/display-cutout/IME insets in all screens, including alert Stop controls. Test gesture navigation, three-button navigation, Arabic, large fonts, and lock screen.
- **PK [5,6,7]:** target 36 includes Android 12 background-start limits, Android 14 FGS types, and Android 15 changes. Android 15's six-hour limits concern specific types such as dataSync/mediaProcessing, not blanket specialUse permission. BOOT_COMPLETED restrictions are type-specific; do not boot-start this service. Overlay and audio-focus changes matter as described above.
- **PK [7,13]:** target 35+ apps cannot assume broad legacy global DND policy control. Chosen design reads state and does not manipulate global DND.
- **PK [27,28]:** target 34+ context-registered receivers need explicit export flags unless solely receiving system broadcasts under the documented exception. Existing private Stop receiver correctly uses NOT_EXPORTED. Telephony broadcasts can originate from privileged apps; if adding a runtime receiver, follow the documented EXPORTED guidance for those senders and validate actions. Manifest receivers use `android:exported`, not runtime flags. Restrict internal command components; keep screening protected by its binding permission.
- **Inference:** minimum release proof: actual incoming carrier calls on RMX3636/API 35 plus an API-36 device/emulator for platform gates, with API 29/30/31/34 coverage for compatibility boundaries. An API-35 phone alone does not prove target-36 runtime behavior on Android 16.
- **Inference:** test NORMAL audible/zero volume, silent, vibrate, DND all/priority/alarms/none, starred and repeat callers, saved/unsaved/private numbers, both SIMs, call waiting, Bluetooth, quick answer/hangup, delay 0/30, schedule boundary, disable/delete during wait/playback, Stop races, new calls, denied/revoked grants, role replacement, process kill, force-stop/relaunch, reboot, and screen-off idle.
- **Inference:** record callback order, fresh ringing state, actual FGS-start result/exemption, playback start/stop times, and notification/FSI results locally without retaining phone numbers. Pass: allowed carrier call unchanged; no audio for ordinary audible NORMAL/ALL calls; no stale sound after stop/end; one player; no leaked lock/focus; safe denied-permission behavior. Explicitly record unsupported DND/concurrent-call outcomes instead of claiming success.

## User disclosures

Proposed disclosure text, **inference**, based on PK limitations above:

1. Alarm-style sound can work when ringtone mode is silent or vibrate. DND can still block it. This app cannot guarantee emergency notification or override every quiet mode.
2. Some DND exceptions already let calls ring. An extra alert can occur. Bluetooth or other accessories can change where sound is heard. Alarm volume still matters.
3. Volume keys stop the alert only while this app's card has focus. Use notification Stop or the on-screen Stop button otherwise.
4. You must choose Leviro as the screening app. It can replace another screening app. Hidden caller IDs cannot be matched; saved contacts need Contacts permission.
5. Android/OEM battery controls, force-stop, process death, and denied permissions can prevent alerts. Setup does not guarantee delivery.
6. Full-screen cards require system permission and may be unavailable. Notification/head-up presentation also depends on notification, channel, and DND settings.

## Play readiness

**PK [4,15,21,24]; inference:** remove call-log access before submission. Provide FGS declarations, user-facing purpose, demo/video evidence where required, and a truthful explanation of why work must begin at an incoming call and end immediately afterward. No claim of Play approval.

Proposed `specialUse` justification text (**inference**, for review):

> Leviro Ringer alerts users to incoming carrier calls from priority numbers they selected locally. After screening allows the carrier call unchanged, a visible foreground service waits the user's 0–30 second delay, verifies that the call is still ringing and alert rules remain active, then plays the selected alarm-style sound. A persistent notification provides Stop. Answering, ending, disabling the rule, or pressing Stop cancels pending work and playback. No idle monitoring service, call blocking, answering, call-log access, or data upload is performed.

**PK [11,15,21]; inference:** separately justify the direct battery-exemption request, including why background limits break the core timely-alert function and why a compliant alternative is insufficient. Verify acceptable-use eligibility rather than asserting it. If FSI stays, complete the FSI declaration and accurately describe the priority-call companion function; do not relabel it as a dialer or scheduled alarm to obtain a default grant.

**Observed; PK [4,29]; inference:** inspected app code is local-only and lacks INTERNET permission. State no app-operated transmission only after auditing the release artifact and dependencies. Complete privacy policy and Data safety accurately: contacts, priority numbers, notes, settings, and imported audio remain local for the chosen design; permissions are not themselves proof of collection off device. Explain retention, deletion, and backups. Current `allowBackup=true` can permit OS backup/device transfer; disable or explicitly exclude sensitive data in the implementation before promising no data leaves the device. No backend or analytics is planned.

## Official sources

**All entries: not opened; claims are from prior knowledge, verify.** URLs are verification targets, not proof of successful retrieval. Items 1–7 include every URL required by the task.

1. https://developer.android.com/reference/android/telecom/CallScreeningService
2. https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener
3. https://developer.android.com/google/play/requirements/target-sdk
4. https://support.google.com/googleplay/android-developer/answer/10208820
5. https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
6. https://developer.android.com/develop/background-work/services/fgs/service-types
7. https://developer.android.com/about/versions/15/behavior-changes-15
8. https://developer.android.com/reference/android/app/role/RoleManager#ROLE_CALL_SCREENING
9. https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_PHONE_STATE_CHANGED
10. https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
11. https://developer.android.com/training/monitoring-device-state/doze-standby
12. https://developer.android.com/reference/android/media/AudioManager
13. https://developer.android.com/reference/android/app/NotificationManager
14. https://developer.android.com/reference/android/media/AudioAttributes
15. https://support.google.com/googleplay/android-developer/answer/13392821
16. https://developer.android.com/about/versions/14/behavior-changes-14#secure-fsi
17. https://developer.android.com/guide/components/services
18. https://developer.android.com/media/optimize/audio-focus
19. https://developer.android.com/reference/android/app/Activity#dispatchKeyEvent(android.view.KeyEvent)
20. https://developer.android.com/develop/ui/views/notifications/notification-permission
21. https://support.google.com/googleplay/android-developer/answer/13392821#full-screen-intent-requirements
22. https://developer.android.com/training/permissions/requesting
23. https://developer.android.com/develop/background-work/background-tasks/awake/wakelock
24. https://support.google.com/googleplay/android-developer/answer/10208820#sms_call_log
25. https://developer.android.com/develop/ui/views/layout/edge-to-edge
26. https://developer.android.com/about/versions/16/behavior-changes-16#edge-to-edge
27. https://developer.android.com/about/versions/14/behavior-changes-14#runtime-receivers-exported
28. https://developer.android.com/develop/background-work/background-tasks/broadcasts
29. https://developer.android.com/identity/data/autobackup

## Report

1. **Verdict: CONDITIONAL GO.** Three gates: documented/legal FGS start plus Play-compliant exemption request; accepted DND/audibility/role/notification limitations; passing modernization and device tests. Universal DND bypass or perfect duplicate prevention is unsupported.
2. **File touched:** `docs/ANDROID_FEASIBILITY_AND_PLAY.md` only. No app implementation, build, or device test performed.
3. **Prior knowledge, verify:** every Android/Play claim marked PK, including screening delivery, lifecycle permissions, FGS exemptions/types, API-36 policy, FSI, DND reads/audio restrictions, overlay changes, keys, receiver flags, and backup. Sources 1–29 were not opened. Telecom-binding FGS exemption remains unknown.
4. **Orchestrator decisions:** accept best-effort DND behavior or narrow scope; establish battery-request/FGS eligibility; retain or drop FSI; choose backup exclusions. Recommendation: no overlay, no default shared-volume boost. Result: feasible with those gates; release readiness unproven.
