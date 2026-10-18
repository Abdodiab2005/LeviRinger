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
- [ ] **Phase 2: rules module** (OpenCode DeepSeek, T2). Pure `rules/AlertRules.kt`, stricter `PhoneMatch`, JVM tests.
- [ ] **Phase 3: alert engine** (Codex, T3). Generation-based state machine, delay, gain, notification Stop, top card.
- [ ] **Phase 4: UI** (OpenCode GLM, T4). Restored schedule and vibrate UI, alert settings, home status, onboarding.
- [ ] **Phase 5: verification + E2E checklist** (orchestrator).
- [ ] **Phase 6: handoff** (orchestrator).

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

## Blockers

None so far.
