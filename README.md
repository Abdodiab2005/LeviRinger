# Leviro Ringer

A native Android app that plays an alarm-style alert when a chosen priority contact calls
while the phone is on **silent, vibrate, or Do Not Disturb**. When the phone already rings
normally, it stays quiet so you never hear two ringtones. Built by Leviro (leviro.net).

- Language: **Kotlin**, XML Views + ViewBinding, Material 3
- Storage: **raw SQLite** (priority list) and SharedPreferences (settings), on the device only
- Android: min **10 (API 29)**, target **16 (API 36)**
- Networking: **none**. No analytics, no backend, no third-party libraries.
- Languages: English and Arabic

## How it works

1. Leviro holds the Android **call-screening role**. For each incoming call it answers the
   screening request immediately with "allow", so the carrier call is never blocked,
   answered or silenced, then compares the caller number with your priority list.
2. If the caller matches, the rules decide: app enabled, contact enabled, global active hours
   and weekdays, the contact's own time window, and the phone's sound state. With the ringer on
   NORMAL, ring volume above 0 and no Do Not Disturb, nothing extra plays (unless you turn on
   "Also alert when the phone rings normally").
3. A short foreground service waits the configured delay (0 to 30 s), checks everything again,
   confirms the call is still ringing, then plays your sound on the **alarm stream**
   (`USAGE_ALARM`) at your chosen volume, with vibration.
4. The alert stops when you answer, decline or the caller hangs up, when you press **Stop alert**
   in the notification or on the caller card, when you press a volume key while the caller card
   is focused, or after 3 minutes.

## Build

Needs JDK 17 and the Android SDK (platform 36, build-tools 36). `local.properties` must point at
the SDK, for example `sdk.dir=/home/you/Android/Sdk`.

```bash
./gradlew assembleDebug testDebugUnitTest   # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug                         # optional static check
```

The debug build has package `net.leviro.ringer.debug` and the label "Leviro Ringer Debug". It
installs next to a release `net.leviro.ringer` and never touches its data:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

A release build (`net.leviro.ringer`, versionCode 2) needs a signing key. The phone currently
has a 1.0 build signed with a different debug key, so updating that install needs the original
key; uninstalling it would erase its contact list.

## First run: setup

The Setup screen opens on first launch and from Home or Settings:

1. **Phone, contacts and notifications** permissions.
2. **Call-screening role.** Only one app can hold it; choosing Leviro replaces apps such as a
   caller-ID or spam app.
3. **Battery optimization off.** Android only lets the alert start from the background during a
   call when this is off. Without it you get a notification but no automatic sound.
4. **Full-screen alerts** (optional, Android 14+): shows the caller card over the lock screen.
5. **Auto-start** on realme, Xiaomi, Oppo, Vivo, Huawei, Samsung and similar phones.

Home shows "Active" or "Paused", what setup is still missing, and whether the global schedule
is active right now.

## Where things are configured

- **Priority callers** (Home, +): name, number (typed or picked from contacts), note, own
  ringtone, enabled, vibrate only, and an optional daily time window. Duplicate numbers are refused.
  Each contact can also override the Settings defaults for volume (0 to 100%, with preview),
  delay (0 to 30 s), alert when the phone rings normally, caller card and raising the alarm
  volume. An override left on "Default" follows Settings.
- **Settings**: alert sound and volume (0 to 100%, with preview), delay (0 to 30 s), default
  vibrate only, alert when the phone rings normally, caller card, raise alarm volume during an
  alert (opt-in), **global schedule** (active hours plus weekdays; a start later than the end runs
  overnight and belongs to the day it starts), language, and a "How it works and limits" card.
- **Test alert** (Home menu): plays the full alert for 30 s. It cannot test a real call,
  Do Not Disturb or background behavior.

## Do Not Disturb and other limits

- Alarm-stream sound plays through silent and vibrate. Do Not Disturb modes that block alarms
  (for example "total silence") can still silence it. Leviro never changes your DND or ringer
  settings.
- If your DND settings already let this caller ring (starred contacts, repeat callers), you
  may hear both the ringtone and the alert.
- Loudness follows your volume slider and the phone's alarm volume. With the alarm volume at 0
  the alert is silent unless "Raise alarm volume" is on.
- Volume keys stop the alert only while Leviro's caller card has focus. When the phone's own
  call screen has focus, the phone handles the keys; use **Stop alert** in the notification.
- Hidden or private caller IDs cannot be matched. Saved contacts need the Contacts permission.
- Force-stopping the app, battery optimization, or aggressive phone-maker battery settings can
  prevent alerts. Behavior differs between manufacturers and Android versions.

## Docs

- `docs/APK_SOURCE_AUDIT.md`: what the installed 1.0 build had and how it was restored.
- `docs/ANDROID_FEASIBILITY_AND_PLAY.md`: architecture, permissions, Google Play notes.
- `docs/MANUAL_E2E_CHECKLIST.md`: phone test checklist.
- `docs/LEVIRO_RINGER_PHASES.md`: work log and decisions.
