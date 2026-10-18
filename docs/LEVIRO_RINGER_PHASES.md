# Leviro Ringer recovery: phases

Branch: `feat/ringer-recovery`. Orchestrator: Claude Code (Opus 5.5). Updated during execution.

## Agents available (checked 2026-10-18)

| Agent | Version | Use in this run |
|---|---|---|
| Codex CLI | 0.163.0 | T1 feasibility doc, T3 alert engine |
| OpenCode | 2.0.32, `opencode/deepseek-v4.1-flash-free`, `opencode/glm-5.3-flash-free` | T2 rules + tests (DeepSeek), T4 UI (GLM) |
| Antigravity `agy` | 1.4.0 | Reallocated to read-only parity review. Headless writes need `--dangerously-skip-permissions`, which the owner has not approved. APK reverse analysis stayed local because decompiled output must not go to external agents. |

## Phases

- [x] **Phase 0: inventory, ADB, APK recovery** (orchestrator). Evidence: `docs/APK_SOURCE_AUDIT.md`. Installed APK is newer than source: schedules, vibrate-only, DB v2, floating alert card.
- [x] **Phase 1: feasibility and Play** (Codex T1, orchestrator audit). Verdict CONDITIONAL GO. Evidence: `docs/ANDROID_FEASIBILITY_AND_PLAY.md`.
- [x] **Build modernization + data contract** (orchestrator). AGP 8.10.1, Kotlin 2.0.21, Gradle 8.11.1, SDK 36, minSdk 29, debug suffix `.debug`, versionCode 2, DB v2, new Prefs keys. `./gradlew assembleDebug` passed. Commit `6deeb16`.
- [x] **Phase 2: rules module** (OpenCode `deepseek-v4.1-flash-free`, T2). Pure `rules/AlertRules.kt`, stricter `PhoneMatch`, 33 JVM tests. Orchestrator re-ran tests: 33/33. Commit `2d0247b`.
- [x] **Phase 3: alert engine** (Codex 0.163.0, T3). Generation-based state machine, delay, gain, notification Stop, top card. Review found 2 defects (pre-ring IDLE from the TelephonyCallback initial state cancelled every alert; two ongoing notifications); Codex fixed both in the same session. Commit `c415eaf`.
- [x] **Phase 4: UI** (OpenCode `glm-5.3-flash-free`, T4; first run failed with provider HTTP 503, retry succeeded). Restored schedule and vibrate UI, alert settings, home status, setup screen, edge-to-edge insets. Orchestrator fix: alarm-volume hint refresh. Commit `83b01e6`.
- [x] **Phase 5: verification** (orchestrator). `./gradlew clean assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL, 33 tests, 0 failures, lint 0 errors / 66 warnings. Debug APK `app/build/outputs/apk/debug/app-debug.apk` (`net.leviro.ringer.debug`, versionCode 2, 1.1-debug). Installed side by side on the realme RMX3636: first launch crashed (`NullPointerException` in `Prefs.<init>`, also present in the old source); fixed from the installed build's behavior, relaunch reached the Setup screen with no crash. Commit `7fab09f`. Bytecode diff of APK vs baseline per method found no further behavior differences. `docs/MANUAL_E2E_CHECKLIST.md` written.
- [x] **Phase 6: handoff** (orchestrator). README rewritten; Play readiness below; Antigravity read-only parity review (agy 1.4.0, sandboxed, no write violation): all 24 audit rows OK, all safety rules PASS, 113/113 strings translated; 1 regression found (caller card lost FLAG_KEEP_SCREEN_ON) and fixed; its "dual foreground notification" risk was rejected because startForeground with a new id cancels the old foreground notification.

## Decisions

1. Source is the editable baseline; APK behavior was re-implemented from bytecode reading.
2. Schedules are both global (active hours + weekdays) and per contact (time window). Both must pass (installed-build order).
3. Missing or malformed times mean no restriction (installed-build parity). `start == end` means all day.
4. Default: no alert when ringer is NORMAL, ring volume above 0 and interruption filter ALL. Opt-in "also alert when the phone rings normally".
5. Delay 0 to 30 s runs after the screening response, inside the foreground service. Rules are re-checked at playback time.
6. Loudness: per-app MediaPlayer gain 0 to 100%. Raising the shared alarm stream is opt-in, default off (installed build always did it).
7. Volume keys stop the alert only while Leviro's card is focused. Notification Stop is the dependable path.
8. No overlay, Accessibility, call log, dialer role, or DND policy changes.
9. Debug build installs as `net.leviro.ringer.debug`; the installed `net.leviro.ringer` is never replaced during development.

## Constraints discovered

- Installed APK signed with a different debug key than this machine's. A future update of the installed app needs that original key or an uninstall, which needs owner approval.
- Codex sandbox has no network and cannot write `~/.gradle`, so the orchestrator runs Gradle for Codex work.

## Play readiness (not submitted, no approval assumed)

- Target API 36 meets the rule in force since 2026-08-31.
- Permissions: READ_PHONE_STATE, READ_CONTACTS, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, WAKE_LOCK, VIBRATE, USE_FULL_SCREEN_INTENT, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. No call-log, overlay, Accessibility or DND-policy permission.
- Declarations needed in Play Console: specialUse foreground service (subtype text is in the manifest), full-screen intent (app is not a calling/alarm core app, so expect the default grant to be off; the app degrades to a notification), battery-optimization request justification (Play lint `BatteryLife` warning; acceptable-use eligibility is a review risk).
- Data safety: no data leaves the device, no INTERNET permission, `allowBackup=false`. Lint notes that Android 12+ device-to-device transfer needs `dataExtractionRules` to be excluded too; not added.
- A release signing key is required. The installed 1.0 build uses a different debug key.

## Remaining limits

- Automatic sound needs the battery-optimization exemption (documented FGS background-start exemption).
- DND modes that block alarms silence the alert; DND that already lets the caller ring can produce a double sound.
- Volume keys stop the alert only while Leviro's card is focused.
- Caller card and its touch pass-through over the system call screen are unverified on a real call.
- When the background service start is refused, the fallback notification keeps its "sounds in N s" text until the call ends or the card is opened.
- Changing the volume slider, ringer mode or DND during an active alert does not change that alert; answer, Stop or hang-up still end it.
- Only realme RMX3636 / Android 15 is available; nothing was tested on other OEMs or Android versions.

## Blockers

None for code. Real-call behavior needs the owner's manual E2E test.
