# Leviro Ringer

A native Android app that plays a loud alarm when a chosen contact calls — even when the
phone is on **silent, vibrate, or Do Not Disturb**. Built by Leviro (leviro.net).

- Language: **Kotlin**
- Storage: **raw SQLite** (no Room, no ORM)
- Min Android: **6.0 (API 23)** • Target: **14 (API 34)**
- Networking: **none**. No analytics. No third-party libraries — only first-party
  AndroidX / Google Material.

## How it works (the short version)

The trick is to **not fight the system ringer**. The alarm sound plays on the **ALARM audio
stream** with `USAGE_ALARM` attributes, which by design plays through silent/vibrate/DND
without changing any of your system settings.

Caller detection is version-split:
- **Android 6–9 (API 23–28):** a `PHONE_STATE` broadcast receiver reads the incoming number.
- **Android 10+ (API 29+):** a `CallScreeningService` (call-screening role) reads the number.
- The `PHONE_STATE` receiver also handles **call-end detection on every version**, so the
  alarm stops automatically when the call is answered or ends.

When a match is found, a full-screen-intent notification launches `AlertActivity` over the
lock screen, and `AlarmService` (a foreground service) plays the looping alarm + vibration.
Stop with the on-screen button, **either volume key**, or by the call ending.

## Build the APK

### Option A — Android Studio (easiest)
1. Open Android Studio → **Open** → select this folder.
2. Let it sync (it downloads Gradle + dependencies and repairs the Gradle wrapper).
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. When the popup appears, click **locate** → grab
   `app/build/outputs/apk/debug/app-debug.apk`.

### Option B — Command line
This project ships without the `gradle-wrapper.jar` binary. First generate the wrapper
(needs a local Gradle install, any 8.x), then build:
```bash
gradle wrapper            # one-time, creates gradlew + the wrapper jar
./gradlew assembleDebug   # output: app/build/outputs/apk/debug/app-debug.apk
```
You also need the Android SDK installed and a `local.properties` pointing to it
(Android Studio writes this automatically), e.g.:
```
sdk.dir=/Users/you/Library/Android/sdk
```

## Install on a phone
- USB + adb: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Or copy the APK to the phone and tap it (enable **Install unknown apps** for your file
  manager when prompted).

## First run — required setup
Open the app and complete **Setup** (it opens automatically the first time):
1. Grant **phone / call log / contacts / notifications**.
2. **Android 10+**: grant the **call-screening role** (this is what lets the app read the
   caller number).
3. Turn off **battery optimization** for the app.
4. **Android 14+**: allow **full-screen alerts**.
5. (Optional) grant **Do Not Disturb access** as a safety net.
6. **Xiaomi/Redmi/Poco, Oppo, Vivo, Huawei/Honor, Samsung, etc.:** enable **Auto-start**
   for the app. No app can toggle this for you — the button opens the right screen, you flip
   the switch. Without it, the system will kill the app and the alarm will not fire.

Use the **Test alarm** item in the top-right menu to verify sound + lock-screen behavior
without waiting for a real call.

## Honest limitations
- "Run in background without being killed" cannot be 100% guaranteed on Android. The
  combination above (foreground service + battery exemption + manifest receivers + OEM
  auto-start) is the best the platform allows. Aggressive OEM skins are the main risk, and
  only the manual Auto-start toggle fully addresses them.
- `READ_CALL_LOG` / `READ_PHONE_STATE` are restricted on the Play Store. This app is meant
  for **sideloading**, where that's not an issue.
