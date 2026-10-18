# Leviro Ringer — Recovery, Modernization & Feature Delivery

> **Execution prompt for the coding agent running on my Linux development machine.**
> **You are the ORCHESTRATOR, not the primary implementer.**
> Execute this plan, not merely write an implementation proposal.

## Mission and project context

The repository in the **current working directory** contains the original **Leviro Ringer** Android app, written natively in **Kotlin** for **Android 10+**. I also have an installed build on my Android phone, reachable through **ADB**. The source code might be older than the installed APK. Roughly 50–60% of the product may already be implemented, including extra features such as configurable schedules/time windows for when special ringing is active. **Preserve existing features, do not assume the current sources or APK are newer, and never start from scratch before performing an evidence-based audit.**

### What the app does

Users select specific phone numbers/contacts as *priority callers*. When one calls while the phone is **Silent, Vibrate, or in a DND state that suppresses ordinary ringing**, Leviro Ringer should play a distinctive, user-configured **alarm-style alert** so the user notices the call. The original incoming cellular call must continue normally. When the phone is already in ordinary audible RING mode and standard calls can ring normally, **do not play a redundant second ringtone**. Existing scheduling rules must still apply.

The app is deliberately **small, local-first, offline-capable**, with no backend, account system, cloud, ads, analytics, or unnecessary architecture. This is **Android-only**: do not implement iOS.

## Agent orchestration — REQUIRED

1. **Immediately discover and read the local `Delegate` skill** (and its referenced operational documentation) if installed. Use the actual skill protocol; never invent delegation calls.
2. Act as **Orchestrator**: inspect, split, delegate, review, integrate, resolve conflicts, run essential verification, and report. Delegate implementation work through that skill rather than doing all coding yourself.
3. Available/preferred **Implementer** pool, depending on the actual local tools/configuration:
   - **Codex**: Kotlin Android framework integration, call lifecycle, sound playback, permission design, implementation of complex core logic.
   - **AGY**: installed-APK reverse analysis, source/API comparison, existing feature inventory, targeted restoration and compatibility work.
   - **OpenCode** using **DeepSeek V4.1 Flash** or **GLM 5.3 Flash** (whichever is actually configured and available): local persistence, schedules, UI, settings, and targeted fixes.
4. These are **preferences, not assumptions about installed capabilities**. Discover which agents/models work; if one is missing, reallocate to an available implementer and record it in the execution log. Never claim an agent was run without evidence.
5. For parallel development, provide each agent a **bounded ticket**: context, exact owned files/modules, acceptance criteria, API/interface contract, dependencies, and mandatory handoff. Prefer separate worktrees/branches and disjoint file ownership. Avoid multiple agents simultaneously editing the same Android manifest, Gradle file, shared model, or UI screen.
6. You own **integration and final review**. Merge/rebase agent changes safely, reconcile API boundaries, and verify only real outcomes. Avoid cyclic delegation, redundant reviewers, agent thrashing, and parallel work before interfaces are agreed.
7. Keep a short persistent `docs/LEVIRO_RINGER_PHASES.md` plan with phase checkboxes, owner, decisions, status, discovered constraints, blockers, and completion evidence. Update it **during execution**. Do not stop after planning.
8. Execute autonomously where safe. Ask me **only** for genuinely unavoidable physical-device actions/approval, Play Console credentials, or destructive changes. I will personally perform the end-to-end call tests. **No push, release, Play Console submission, or removal/replacement of my existing installed app without my explicit approval.**

## Hard engineering and safety rules

- **The checked-out source is the editable baseline.** The APK is a **reference build/evidence**, not a substitute for editable Kotlin source. Decompile only to identify behavior/assets/logic lost from older source; recovered Dex/Java is not guaranteed to equal original Kotlin, especially if obfuscated.
- Work on a fresh feature branch such as `feat/ringer-recovery` after checking the working tree; protect existing uncommitted changes. Never run `git clean -fdx`, discard local changes, reset history, or overwrite unknown files. Make small, coherent commits per phase.
- **Do not uninstall the installed phone app, clear its storage, change its roles/permissions, or install a replacement over it** during the audit. Protect installed user preferences and contact rules. Incompatible signing certificates can prevent update installs—do not work around that by uninstalling.
- APK extraction and inspection should be **passive and limited to this app**. Do not pull the user's contacts, call log, personal databases, private app data, or unrelated packages. Do not send extracted APKs, phone numbers, or secrets to external services/agents. Agents can work with **sanitized feature summaries** when feasible. Keep binary audit output outside version control (e.g. `.audit/` ignored).
- Prefer documented Android SDK APIs and an honest Google Play compatibility strategy. **No root, hidden APIs, Accessibility-based key interception, call-log workarounds, spoofed dialer roles, notification-permission tricks, or OEM-specific hacks** just to simulate success.
- Never claim universal behavior across manufacturers, DND profiles, or Android versions. Document real limits and offer graceful fallback.
- Target **Google Play's current requirements** rather than freezing old target SDK values. As of October 2026, new apps/updates to Google Play must target **Android 16 / API 36 or above** (normal phone apps). Try to retain **minSdk 29 (Android 10)** unless actual evidence requires a change. Check actual SDK/Gradle/toolchain availability before changes.
- Aim for the **smallest production-maintainable solution**. Keep existing XML/View architecture if that is what the project uses; adopt Jetpack Compose only if already used or clearly justified. No unnecessary rewrite, DI framework, cloud service, or database migration complexity.
- **Testing budget is intentionally small**: minimal unit tests for risky decision logic + a successful debug build + targeted static check. I will do **manual E2E on the phone**. Do not create a large test suite, emulator farm, CI overhaul, or extensive screenshot tests.

---

# Phase 0 — Inventory, ADB acquisition, installed APK recovery

**Lead: Orchestrator; delegate evidence gathering to AGY/OpenCode if available.**

### 0.1 Protect and inspect the local project

- Find repository root, inspect `git status`, branch, recent history, existing README, build scripts, Android manifest(s), `applicationId`, `namespace`, min/target/compile SDK, Kotlin/AGP/Gradle, dependency versions, existing tests, UI framework, and signing setup **without exposing signing secrets**.
- Locate existing features and entry points: incoming-call observation, screening/call listeners/receivers, ringtone/audio playback, volume controls, DND logic, permission onboarding, contacts, saved numbers, scheduling, per-contact overrides, persistence, notifications, overlay UI, boot handling, translations, themes, and failure-handling.
- Determine whether it currently builds; record initial compile failures independently from regression defects.

### 0.2 Inspect the phone and pull the installed app via ADB

Verify `adb` exists and exactly which Android device is selected. Example workflow (adapt to discovered device/package; **never assume the package ID**):

```bash
adb devices -l
adb -s "$SERIAL" shell getprop ro.build.version.release
adb -s "$SERIAL" shell pm list packages -3
# Derive the correct package name by comparing source applicationId, installed label and package metadata.
adb -s "$SERIAL" shell pm path "$PKG"
# Each line is package:/data/app/.../base.apk or split_config.*.apk.
# Extract paths from ALL returned lines and adb pull each file into .audit/installed-apk/.
sha256sum .audit/installed-apk/*.apk
```

- If the app is split into **base + multiple split APKs**, preserve and inspect all splits; do not pretend one extracted `base.apk` is the complete install.
- Use local tools when available, e.g. `apkanalyzer`, `aapt`/`aapt2`, `apksigner`, `jadx`, `apktool`, Gradle/Android SDK. Install only safe missing tools using the machine's approved workflow; do not download opaque executables.
- Extract **package, versionName, versionCode, min/target SDK, manifest permissions, services/receivers/providers, requested roles, signing cert fingerprint, resources/assets/strings, relevant classes, persistence schema clues** and UI feature evidence. Never assume a successful decompilation means complete reconstruction.
- Identify whether the APK's package ID and signing identity match the source's intended app, and whether any debug install would create a new app or conflict with the phone's installed copy. Preserve a separate debug `applicationIdSuffix` when useful to prevent replacing installed data.
- If ADB isn't authorized/available, continue the source audit and record an **APK audit blocker**. Do not falsely declare comparison complete.

### 0.3 Produce an evidence-based feature comparison

Create `docs/APK_SOURCE_AUDIT.md` including:

| Feature | Installed APK evidence | Source evidence | Status | Action |
|---|---|---|---|---|
| Priority contact numbers | ... | ... | match / APK-only / source-only / unknown | ... |

Include all discovered functionality, including **schedules/time windows, different days, per-contact rules, import/export, favorites, custom sounds, toggles, onboarding, privacy, notifications, themes, widgets** *if present* (do not invent these features). Note confidence level (**observed, source-confirmed, inferred, unknown**), discrepancies, and any recovered assets/rule behavior.

### Exit gate

We know what exists in the APK, what exists in source, what is missing, what is broken, what can be safely reused, and where the real recovery work belongs. **Do not delete existing features to simplify implementation.**

---

# Phase 1 — Android feasibility & Google Play compliance spike (GO / NO-GO)

**Delegate Android feasibility to Codex; Orchestrator independently audits conclusions.**

Before implementing the rest, resolve the key question: **Can this phone detect incoming priority cellular calls and play a timely local alert in real-world background conditions, using documented Android APIs and without restricted call-log permissions?**

### 1.1 Incoming-number and call-lifecycle architecture

- Evaluate **`CallScreeningService`** registered using **`RoleManager.ROLE_CALL_SCREENING`** (must be granted by the user). Verify saved-contact handling with **`READ_CONTACTS`** where appropriate and allowed. Explain that only one chosen screening app may own this role, potentially conflicting with Truecaller/other call-screening apps.
- **Always call `respondToCall` within the system's 5-second deadline**, and allow the call through. Do not hold screening for the configured alert delay, do not block/reject calls, do not mute the telephony ringtone as a side effect.
- Investigate call-state observation with documented **`TelephonyCallback.CallStateListener` on API 31+** and compatible APIs on API 29–30, requesting `READ_PHONE_STATE` only as needed. Number retrieval through call-state listeners must **not** rely on restricted `READ_CALL_LOG`.
- Check edge cases: saved and unsaved numbers, restricted/private caller ID, international formatting (local/+country code), concurrent/dual-SIM behavior, repeated calls, quick hangup, system-call-screen ownership, and interrupted background lifecycles.
- Draw a small call-state/lifecycle diagram and prototype only the minimum integration necessary to prove the architecture. If it is NOT viable, **stop dependent feature work**, document the exact OS or policy blocker and recommend a narrow viable alternative. Do not invent a workaround.

### 1.2 Permissions/policy review

For each requested permission/role, document **purpose, user consent/onboarding, target-API behavior, whether Google Play permits it, and fallback**. Specifically review `READ_CONTACTS`, `READ_PHONE_STATE`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW` (only if overlay is offered), `ACCESS_NOTIFICATION_POLICY` (only when genuinely needed), `FOREGROUND_SERVICE` and **correct service types** (if needed), and optional full-screen intent rules. **Avoid `READ_CALL_LOG` and `WRITE_CALL_LOG`** unless the app can genuinely meet and obtain Play-approved criteria; never impersonate Default Dialer. The `phoneCall` foreground-service type is **not** a generic loophole for screening an ordinary carrier call.

Review restrictions on starting foreground services from the background on Android 12–16, Android 14+ service type rules, Android 15+ overlay/FGS limitations, full-screen-intent rules, notification channels, battery optimizations, OEM behavior, and DND policy. Do not change device-wide DND settings silently. A `USAGE_ALARM` playback request **does not guarantee bypass of every DND mode**. Explain limitations in user-facing help.

### 1.3 Decision document

Write `docs/ANDROID_FEASIBILITY_AND_PLAY.md` with a **GO / CONDITIONAL GO / NO-GO** verdict, concrete permissions, chosen architecture, limitations/alternatives, and source links. Do not assert Play approval ahead of Google's review.

### Exit gate

Only proceed with the relevant implementation phases after a **technically justified GO or CONDITIONAL GO**. If a hard OS/policy blocker exists, finish the audit and present the blocker, not an unsafe imitation of the feature.

---

# Phase 2 — Recover missing features, model rules, preserve existing data

**Delegate persistence, schedules, and APK/source parity to AGY or OpenCode.**

### Existing behavior preservation

- Retain all verified features from the latest available source/APK, especially configurable **active hours/schedules** controlling when the priority ringtone is permitted.
- Determine whether schedules are **global, per-number, or both**. Preserve the behavior observed in the APK; if ambiguities exist, document the chosen conservative interpretation rather than silently redefining it.
- Preserve installed-user data formats where practical. If schema changes are necessary, make small safe migrations. Do not alter the installed release application data during development.
- Keep contact names/numbers and app configuration on-device; use existing persistence implementation unless demonstrably broken.

### Rules and edge cases

Create a small, testable rule evaluator (no Android UI dependency) that decides:

`ShouldAlert = AppEnabled AND NumberMatches AND RuleEnabled AND IsInActiveSchedule AND NeedsSpecialAlert AND CallIsStillRinging AND NotManuallySilenced`

- Normalize numbers carefully; prefer a mature existing number parser if already in use. Match variants of local/international formats without falsely conflating different numbers. Handle unknown/private IDs safely.
- Evaluate weekday, start/end time, overnight windows crossing midnight (e.g. 22:00–06:00), midnight boundary, system timezone/time changes, and missing/invalid schedules; never randomly ring outside the selected times.
- Define deterministic precedence: global defaults → specific contact overrides (when configured) → current device/call state → user stop action.
- Define clear enabled/disabled state, no duplicated priority entries, and no alert when a call no longer matches the live criteria.

### Exit gate

Restored APK-only features (when recoverable), stable persisted configuration, deterministic rules, and no accidental regression of scheduling.

---

# Phase 3 — Reliable incoming-call and alarm-style alert engine

**Primary implementer: Codex.**

Implement/repair the core with a single well-encapsulated controller/state machine, such as:

`IDLE → MATCHED → WAITING_FOR_DELAY → ALERTING → STOPPED → IDLE`

Transitions must be **idempotent**, cancelable, race-safe, and lifecycle-aware. Only one alert should be active per incoming call. Use structured async cancellation (e.g. Kotlin coroutines if the app already uses them), avoid leaked jobs or permanent background loops. Consider process death and OS restrictions explicitly.

### Required features and acceptance criteria

#### 3.1 User-controlled alert loudness

- Add **0–100%** user-configurable playback gain with immediate preview and persistent settings; optional **per-contact override** if it fits the existing model.
- Use appropriate Android **`AudioAttributes.USAGE_ALARM`**/audio focus only as justified. Prefer control of this app's player output (`MediaPlayer`/ExoPlayer/SoundPool according to existing architecture), **not forcibly changing the phone's global alarm/ringer/media volume**. Explain that effective loudness may still depend on system alarm-stream volume, device routing, and DND.
- Restore/release audio resources and focus cleanly. If gain=0 is allowed, make it obvious it means no audible alert.

#### 3.2 Stop on volume buttons (best effort, with honesty)

- Desired product behavior: pressing either **Volume Up or Volume Down** while Leviro's alert is sounding immediately silences **only Leviro's alert**.
- **DO NOT claim or implement universal global capture of physical volume keys when another app's incoming-call UI owns focus**: Android does not offer a general Play-safe API for that. Use `dispatchKeyEvent`/equivalent **only in Leviro's own focused UI when appropriate**; explore documented reliable signals if any, without hijacking system controls, screen focus, or Accessibility.
- If reliable global interception is unavailable, document this as an OS limitation and add dependable accessible alternatives: **Stop alert** in a high-priority notification, and **Stop** on Leviro's optional alert UI/overlay. **Never let the alert run indefinitely because the user cannot intercept hardware keys.**

#### 3.3 Configurable delay before the special ringtone

- Add a clear global `delaySeconds` control, initially supporting **0–30 seconds**, step 1 or sensible presets; allow optional per-contact override if straightforward.
- Delay occurs **after rapid call-screen response** (not inside the 5-second screening deadline). The controller re-checks call still ringing, matching contact, enable state, active schedule, and ringer/DND eligibility **at playback time**.
- If call is answered, declined, canceled, or stops ringing during the delay, **no audio should play**. Cancel pending jobs promptly on new call/state transitions.

#### 3.4 No redundant ringtone during normal audible calls

- Primary default rule: **when device `ringerMode == RINGER_MODE_NORMAL` and current interruption filter permits normal calls (`INTERRUPTION_FILTER_ALL`), suppress Leviro's extra alert**.
- When Silent/Vibrate is active, or DND is genuinely suppressing ordinary ringing, evaluate the contact and schedule rules for a special alert.
- Be careful with DND modes that already allow starred/priority callers, RING mode at zero system ring volume, alarms blocked by DND, Bluetooth output, and OEM exceptions. If reliable determination of actual audible telephony playback is unavailable, avoid promises and surface a user-controlled explicit fallback behavior. Prioritize avoiding double alerts while documenting any limitations.

#### 3.5 Lifecycle guarantees

- Stop alert immediately on **OFFHOOK** (answered), **IDLE** (call ended), explicit Stop, app disabled, contact disabled, schedule invalidation, or a new incompatible call.
- Avoid racing callback events, persistent alarms after hangup, double playback, duplicate notifications, memory leaks, and excessive battery use.
- Do not intercept, block, or dismiss the underlying carrier call. Avoid the app drawing over or impairing system Answer/Decline buttons.

### Exit gate

Core alert behavior is implemented, compiles, and its pure rules/delay cancellation can be checked without a physical phone call. Actual cellular-callback reliability remains for user E2E.

---

# Phase 4 — Caller alert UX, settings, permission guidance

**Primary implementer: OpenCode/AGY; coordinate with Codex over the controller API.**

### 4.1 Optional overlay resembling a minimal Truecaller caller card

Desired experience: a compact, readable **Leviro Priority Call** banner/card over or near the incoming-call screen, showing name/number when permission allows, priority status, and **Stop Leviro Alert**. It should not replace the system call UI, cover answer/reject buttons, or attempt to answer the call. Do not imply Truecaller-level platform privileges.

- Evaluate a **heads-up notification with action** as a safer first fallback; implement `TYPE_APPLICATION_OVERLAY` **only if special user permission is granted, behavior is stable, and Play policy is met**.
- Support lock screen/notification restrictions **where legitimately permitted**; do not rely on `USE_FULL_SCREEN_INTENT` unless it qualifies, and do not use invasive focus capture to solve the volume-button limitation.
- User can disable the overlay independently. Handle display/permission failures gracefully. Always remove overlay on call end or alert stop.

### 4.2 Minimal, polished native Android settings

Keep/adapt the app's existing visual approach. Required screens or sections (reuse existing where possible):

1. **Home/status**: clearly state Enabled/Paused, whether call-screening role and essential permissions are ready, and why the app may not work on this device.
2. **Priority callers**: add from contacts or enter phone number manually, edit/delete, enable/disable, handle duplicates.
3. **Schedules / active hours**: preserve existing time rules and controls (weekday, start/end, overnight windows); make active/inactive state legible.
4. **Alert settings**: ringtone preview, playback volume slider, delay, show overlay toggle, whether to trigger when phone normally rings (default **OFF**, and only if this option fits requirements).
5. **Permission setup**: clear, sequential explanations for screening role, contacts, phone state, notifications, and optional overlay or DND access. Show current permission state and buttons leading to Android's legitimate system screens. No misleading permission prompts.
6. **Test Alert**: play/stop a local preview of the special sound, show configured loudness and output limitations. **Clearly label that preview cannot validate actual background-call or DND behavior.**

Preserve app language/theme if currently present. Avoid a full UI rewrite unless genuinely necessary.

### Exit gate

User can configure priority callers, schedules, sound, volume, delay, permissions, and Stop; the UI reflects persisted values and actual permission states.

---

# Phase 5 — Lean verification, APK deliverable, and owner E2E checklist

**Orchestrator runs verification. Implementers fix their own defects.**

### Do only essential automated checks

1. A successful **Gradle debug build** using the project's actual module and variant.
2. Focused JVM unit tests for **number matching (where testable), overnight schedules, RING/DND eligibility, duplicate-call suppression, delayed cancellation / state transitions**; add only those with high regression value.
3. Run an existing targeted lint/static check if reasonably fast. Do not expand a large test suite or CI.
4. Install or launch a **separate debug variant** only if this won't overwrite the phone's release install or user data; otherwise **do not install** and explain why.
5. Verify the APK output path actually exists and log version/build metadata; do not label build/test results successful until commands exit successfully.

### Produce `docs/MANUAL_E2E_CHECKLIST.md` for MY manual call testing

Create short, clear checkboxes covering at least:

- Priority contact calls while **Silent**, **Vibrate**, and **DND (alarms allowed/blocked)**.
- Normal RING mode: **no double ringtone**.
- Priority number vs unlisted number; saved contacts vs manual entry; local vs +country prefix.
- Within schedule, outside schedule, weekday/overnight schedule crossing midnight.
- 0-second and nonzero delays; caller hangs up/answers **during delay** (must never ring afterward).
- Hang up, answer, decline during active alert (must stop quickly).
- Test loudness slider and device alarm-stream volume; Volume Up/Down when Leviro UI focused vs system InCall UI focused, with **expected OS limitations called out**.
- Overlay/notification Stop, screen unlocked/locked; permissions denied/revoked.
- App backgrounded/force-stopped/device restarted; test behavior only within documented platform restrictions.
- If available, test Samsung/Pixel/Xiaomi (or real available OEM devices) and Android 10, 12, 14, 15, 16. **Do not claim these were tested if they were not.**

No automated outgoing test calls, no user call-log scraping, and no demand for a complete automated E2E suite.

### Exit gate

Build artifact exists, focused verification passes (or truthful blockers are recorded), and the manual E2E plan is ready. Report which items require my physical testing.

---

# Phase 6 — Handoff, release-readiness review, and final summary

- Update the main `docs/LEVIRO_RINGER_PHASES.md` with completed/blocked phases, owners, meaningful decisions, TODOs, and evidence from commands.
- Produce a compact README/update note: how to build and launch, how to grant screening role and necessary permissions, what happens in DND, key device limitations, where schedules are configured, how to silence alerts.
- List **every APK/source parity item**, recovered features, deliberate deviations, and any remaining unsupported requirements.
- Check **Google Play readiness**: currently required target SDK; manifest permissions; disclosure/consent/privacy practices; handling of `CallScreeningService`, roles, overlays, notifications, DND, foreground services; avoid assuming Play policy approval.
- Keep the app local-first. Do not request Play account credentials, create a store release, or publish anything.
- Create small logical commits on the feature branch; no remote push unless explicitly authorized.

## Final report format (concise, factual)

1. **ADB/APK acquisition:** device recognized? package ID, versionCode/versionName, split APKs, SHA-256, extraction paths; blocker if any.
2. **Baseline parity:** source vs APK differences, recovered vs not recoverable features, schedule implementation status.
3. **Platform verdict:** GO / CONDITIONAL GO / NO-GO; documented Android and Play limitations.
4. **Delivered changes:** by Phase, file paths, key commits, specific behavior.
5. **Delegation:** which actual agent/model worked on each ticket, or exact missing capability.
6. **Verification:** exact Gradle commands and pass/fail; APK output path; never overstate tests.
7. **Remaining limitations:** especially DND bypass, background Volume key capture, overlay behavior, OEM differences.
8. **My manual E2E tasks:** link to checklist; clear safe install instructions that won't erase the currently installed app.

## Authoritative Android references to verify against at execution time

- CallScreeningService lifecycle, scope, 5-second response deadline: https://developer.android.com/reference/android/telecom/CallScreeningService
- TelephonyCallback.CallStateListener: https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener
- Current Google Play target API requirements: https://developer.android.com/google/play/requirements/target-sdk
- Google Play sensitive SMS/Call Log permissions: https://support.google.com/googleplay/android-developer/answer/10208820
- Android restrictions on background foreground-service starts: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Foreground service type requirements: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android 15 behavior changes (including overlay + FGS limitations): https://developer.android.com/about/versions/15/behavior-changes-15

**Start now** with Phase 0: discover the Delegate skill and agent availability, inspect the repo without modifying user data, connect to the phone with ADB, identify the correct installed package, pull all APK splits safely, then build the comparison matrix. Proceed through the phases and delegate implementation after validating the Phase 1 feasibility gate. This is an **execution** request, not just a planning request.
