# APK vs source audit

Audit date: 2026-10-18. Evidence files stay in `.audit/` (git-ignored).

## Acquisition

- Device: realme RMX3636, Android 15 (API 35), build RMX3636_15.0.0.1910(EX01), serial Z5SOYXKFCAGAJRNR over USB.
- Package: `net.leviro.ringer`, label "Leviro Ringer". Two unrelated Leviro packages (`net.leviro.levix`, `net.leviro.levix.dev`) were not touched.
- Installed build: versionName 1.0, versionCode 1, minSdk 23, targetSdk 34, compileSdk 34.
- Flags: DEBUGGABLE, TEST_ONLY (an Android Studio debug install). First install 2026-05-31, last update 2026-06-08. Source files on disk are dated 2026-05-30, so the APK is newer than the source.
- Splits: none. `pm path` returned only `base.apk`.
- Pulled to `.audit/installed-apk/base.apk`, 5,445,961 bytes, SHA-256 `8ad8da833cbb83fe120f04e307782a8820c21c5e56383ed36bd89429ebef2503`.
- Signature: APK Signature Scheme v2 only, `CN=Android Debug`, certificate SHA-256 `049bf874f450546ba1f1f88dc569b1b93cb19680f06c286858a6a6bc48f7b215`.
- This machine's `~/.android/debug.keystore` has SHA-256 `4C:97:62:46:91:B3:C8:ED:...:34:76`. The keys differ, so a debug build from this machine cannot update the installed app. Debug builds therefore use `applicationIdSuffix ".debug"` (`net.leviro.ringer.debug`) and install side by side.
- Tools: SDK `aapt2`, `apksigner`, `apkanalyzer`, `dexdump` (build-tools 36.0.0). No jadx or apktool. No app data, contacts, call log or databases were read from the phone.

## Method

`dexdump -d` disassembly of the 24 app classes, `aapt2 dump` of the manifest, string and layout resources, and a view-id diff of the 6 app layouts against `app/src/main/res/layout`. Disassembly is not original Kotlin, so behavior below is reconstructed from bytecode, not copied.

## Feature comparison

Confidence: **observed** (seen in APK bytecode/resources), **source-confirmed** (present in source), **inferred**.

| Feature | Installed APK evidence | Source evidence (2026-05-30) | Status | Action |
|---|---|---|---|---|
| Priority contact list (name, number, note, enabled, custom ringtone) | `vips` table, `VipDao`, `item_vip` layout (observed) | Same (source-confirmed) | match | keep |
| Number matching (platform compare + last 9 digits) | `PhoneMatch.matches`, `lastDigits` default 9 (observed) | Same | match | tightened: two `+` numbers must match fully |
| Caller detection API 29+ | `RingerCallScreeningService`, respond first, then match (observed) | Same | match | keep, rebuilt engine |
| Caller detection API < 29 via PHONE_STATE number extra | present (observed) | present | match | removed: minSdk 29, no READ_CALL_LOG |
| Call-end stop on OFFHOOK/IDLE | `PhoneStateReceiver` (observed) | Same | match | keep, now also cancels pending delay |
| Per-contact vibrate-only | `vibrate_only` column, `swVibrateOnly`, string "Vibrate only (no sound)" (observed) | absent | APK-only | restored |
| Per-contact time window | `start_time`/`end_time` columns, `btnStartTime`/`btnEndTime`, TimePickerDialog defaults 00:00 and 23:59 (observed) | absent | APK-only | restored, plus a Clear button |
| Global schedule (active hours) | Prefs `schedule_enabled` (false), `schedule_start` "22:00", `schedule_end` "07:00", `swScheduleEnabled`, `layoutSchedule` (observed) | absent | APK-only | restored with same keys and defaults; weekday mask added |
| Global vibrate-only default | Prefs `default_vibrate` (false), `swGlobalVibrate` (observed) | absent | APK-only | restored |
| Schedule semantics | `AlarmController.isWithinTimeRange`: missing or unparsable times mean "no restriction"; start <= end uses an inclusive same-day range; start > end crosses midnight (observed) | absent | APK-only | restored in pure `rules` module; start == end now means all day (APK: one minute) |
| Rule order | master on, then global schedule (if enabled), then contact window, then vibrate = contact OR global (observed) | master only | APK-only | restored and extended (ringer/DND check, delay) |
| DB schema v2 + v1 to v2 migration | `DB_VERSION` 2, three `ALTER TABLE` statements (observed) | v1 | APK-only | restored verbatim, so the release update keeps installed data |
| Alert screen as floating card | `Theme.LeviRinger.Alert` parent `Theme.Material3.Dark.Dialog`, floating, translucent, dimmed; card layout; window gravity center (observed) | full-screen page | APK-only | card kept, moved to the top, no dim, outside touches pass to the call screen |
| Keyguard handling | `requestDismissKeyguard` on API 27+ (observed) | flags only | APK-only | dropped: it pops the unlock prompt over the incoming call |
| Volume keys stop alert | `dispatchKeyEvent` for VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE (observed); `setVolumeControlStream(STREAM_ALARM)` | `onKeyDown` for UP/DOWN | APK newer | keep APK behavior; works only while the card is focused |
| Alarm stream forced to max | `maximizeAlarmVolume`/`restoreAlarmVolume` (observed) | same | match | now opt-in setting, default off; per-app gain slider added |
| Idempotent service start | null checks before wake lock, vibrator, volume, player (observed) | not idempotent | APK newer | superseded by generation-based state machine |
| Full-screen intent notification + FGS (specialUse) | present (observed) | present | match | kept as optional caller card with fallback |
| Onboarding (permissions, role, battery, FSI, DND, OEM auto-start) | same 6 rows (observed) | same | match | DND row removed (permission removed); call-log wording removed |
| Language EN/AR | `values-ar`, language radio group (observed) | same | match | keep; all new strings translated |
| Test alarm menu | present (observed) | present | match | keep, labeled as not validating real calls |
| Boot receiver (creates channels) | present (observed) | present | match | removed: no user-visible effect |
| Startup crash guard | `Prefs.<init>` falls back to the passed context when `applicationContext` is null (observed) | uses `applicationContext` directly; crashes in `RingerApp.attachBaseContext` (reproduced on the phone with the debug build) | APK-only fix | restored |
| Import/export, favorites, widgets, themes, analytics | not found | not found | absent in both | not added |

## Discrepancies and decisions

1. The APK's DB is version 2. A release build from v1 source would crash on downgrade or miss columns. Source now ships the same v2 schema and migration.
2. Malformed stored times keep the APK behavior (no restriction). The UI only writes `HH:mm` from a TimePicker, so this path is defensive.
3. `start == end` changed from a one-minute window to all day. A one-minute window is not a usable schedule.
4. Per-contact windows had no way to be cleared in the APK. A Clear button was added.
5. Recovered behavior was re-implemented in Kotlin from the bytecode reading. No decompiled code was committed or sent to external agents; implementers received feature summaries only.
