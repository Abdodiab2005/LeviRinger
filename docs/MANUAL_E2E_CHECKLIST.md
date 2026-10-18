# Manual E2E checklist (owner)

Build under test: `app/build/outputs/apk/debug/app-debug.apk`, package `net.leviro.ringer.debug`, label "Leviro Ringer Debug". It installs next to the existing `net.leviro.ringer` and does not touch its data.

## Safe install

```bash
adb -s Z5SOYXKFCAGAJRNR install app/build/outputs/apk/debug/app-debug.apk
```

Do not use `adb uninstall` or `-r` on `net.leviro.ringer`. Only one app can hold the call-screening role: granting it to the debug build takes it from the installed 1.0 app. Give it back afterward in Settings > Apps > Default apps > Caller ID & spam app.

Live log while testing (numbers are masked to the last 2 digits):

```bash
adb -s Z5SOYXKFCAGAJRNR logcat -s LeviroRinger
```

## Setup

- [ ] Setup screen: grant phone, contacts, notifications.
- [ ] Grant call-screening role to "Leviro Ringer Debug".
- [ ] Turn off battery optimization (required for automatic alerts).
- [ ] Optional: allow full-screen alerts.
- [ ] realme: allow auto-start / background activity for the debug app.
- [ ] Home shows "Active" and "Ready".

## Ringer and DND modes (priority contact calls, delay 0)

- [ ] Silent: alert sounds within about 1 s of ringing.
- [ ] Vibrate: alert sounds.
- [ ] DND with alarms allowed: alert sounds.
- [ ] DND with alarms blocked: expected limitation, alert may stay silent. Note result.
- [ ] DND that allows starred contacts, caller starred: note whether two sounds play (disclosed limitation).
- [ ] Normal ring mode, ring volume above 0: no Leviro alert, only the phone ringtone.
- [ ] Normal ring mode, ring volume 0: alert sounds.
- [ ] "Also alert when the phone rings normally" on, normal mode: alert sounds over the ringtone.

## Matching

- [ ] Priority number calls: alert. Unlisted number: no alert.
- [ ] Contact added from picker vs typed manually: both alert.
- [ ] Stored as local `0...`, caller presents `+country...` (and the reverse): alert.
- [ ] Private / hidden caller ID: no alert.
- [ ] Adding the same number twice is refused.
- [ ] Disabled contact: no alert.

## Schedules

- [ ] Global schedule on, now inside hours: alert. Outside hours: no alert.
- [ ] Overnight window (for example 22:00 to 07:00): call at 23:00 and at 06:30 alert; 12:00 does not.
- [ ] Weekday chips: deselect today, call: no alert. Overnight window after midnight follows the start day.
- [ ] Per-contact window: inside alerts, outside does not. Clear removes the window.
- [ ] Vibrate-only contact and global vibrate-only: vibration, no sound.

## Delay

- [ ] Delay 0: alert starts at ring.
- [ ] Delay 10 s: notification says the alert sounds in 10 s, then sound starts.
- [ ] Caller hangs up during the delay: no sound afterward, ever.
- [ ] Answer during the delay: no sound.
- [ ] Notification Stop during the delay: no sound for that call.

## Stopping an active alert

- [ ] Answer: alert stops within about 1 s.
- [ ] Decline: stops.
- [ ] Caller hangs up: stops.
- [ ] Notification "Stop alert": stops; the call keeps ringing normally.
- [ ] Caller card Stop button: stops.
- [ ] Volume Up / Down while the Leviro card is focused: stops.
- [ ] Volume keys while the system call screen is focused: expected limitation, the phone handles the keys (may silence the ringtone only). Use notification Stop.
- [ ] Alert never lasts more than 3 minutes.
- [ ] Master switch off during an alert: stops.

## Loudness

- [ ] Volume slider 100%, 50%, 10%: alert loudness follows. 0%: no sound, vibration and notification remain.
- [ ] System alarm volume low with boost off: alert is quiet (expected). Boost on: alert loud, alarm volume restored after.
- [ ] Settings Preview plays and stops; preview text says it cannot test real calls.

## Screen states and permissions

- [ ] Screen locked: caller card appears at the top over the lock screen without asking to unlock (needs full-screen permission); Answer/Decline still reachable.
- [ ] Screen unlocked: heads-up notification with Stop.
- [ ] Full-screen permission denied: notification only, sound still plays.
- [ ] Notifications denied: Home shows what is missing.
- [ ] Battery optimization back on: note whether sound still starts (expected to fail on Android 12+ in background).
- [ ] Role taken by another app: Home shows the role is missing; no alerts.
- [ ] Test alert menu: plays, stops by Stop or after 30 s.

## Lifecycle

- [ ] App swiped from recents, then priority call: alert.
- [ ] Force stop in Settings, then call: expected no alert until the app is opened again (Android rule).
- [ ] Reboot, do not open the app, call: note result.
- [ ] Phone idle 30+ minutes, screen off, call: alert.

## Devices

Only the realme RMX3636 (Android 15) is available. Samsung, Pixel, Xiaomi and Android 10, 12, 14, 16 are untested. Record any extra device here:

| Device | Android | Result |
|---|---|---|
| realme RMX3636 | 15 | |
