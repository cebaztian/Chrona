# Chrona — Architecture Decision Records

A log of the decisions behind [PLAN.md](PLAN.md) and [MILESTONES.md](MILESTONES.md). Each entry is
immutable once accepted: a change of mind adds a new ADR that supersedes the old one.

**Statuses:** *Accepted* (decided), *Proposed* (to be confirmed by a spike or check, named in the
entry), *Open* (waiting for a decision), *Superseded*.

| # | Decision | Status |
|---|---|---|
| 001 | Kotlin + Jetpack Compose, not Flutter | Accepted |
| 002 | Standalone, no network, no `INTERNET` permission | Accepted |
| 003 | Sideloaded APK only | Accepted |
| 004 | Target device and SDK levels | Accepted |
| 005 | Application ID `com.nutabe.chrona` | Accepted |
| 006 | JSON file in device-protected storage, no database | Accepted |
| 007 | Manual dependency injection | Accepted |
| 008 | `setAlarmClock` + `USE_EXACT_ALARM` | Accepted (permission set confirmed at M1) |
| 009 | Ringing in a `systemExempted` foreground service + dedicated `RingingActivity` | Accepted |
| 010 | Every ringing component is direct-boot aware | Accepted |
| 011 | Persisted runtime state and reconciliation | Accepted |
| 012 | Process-death recovery: `START_STICKY` + backup alarm | Accepted |
| 013 | Missions: math, shake, steps; math as universal fallback | Accepted |
| 014 | Steps counted in `RingingService` | Accepted (DV on the S25, 2026-11-18) |
| 015 | Auto-silence as a capped forced snooze | Accepted |
| 016 | No-bypass rules while ringing or snoozed | Accepted |
| 017 | Debug and release builds side by side; no backup | Accepted |
| 018 | Spanish and English strings from the first screen | Accepted |
| 019 | Sensor watchdog: 2 min for steps, 30 s for shake | Accepted |
| 020 | Brand palette, dark only, no dynamic color | Accepted |
| 021 | Screens designed in Stitch with the `high-end-visual-design` language | Accepted |
| 022 | Unattended milestone loop: local commits, pause on user items | Superseded by 025 |
| 023 | Develop and verify on an Android 17 emulator; real device at the end (DV) | Accepted |
| 024 | Visual direction: editorial serif + tabular sans hybrid; Phosphor icons | Accepted |
| 025 | Unattended loop decides on its own: recommended option, else advisor | Accepted |

---

## ADR-001 — Kotlin + Jetpack Compose, not Flutter

- **Status:** Accepted, 2026-11-17.
- **Context:** The first plan used Flutter. The app is Android-only, and its hard parts (exact
  alarms, foreground service, full-screen intent, direct boot, sensors) are native APIs that Flutter
  would reach through platform channels anyway.
- **Decision:** Pure Kotlin, Jetpack Compose (Material 3), ViewModel + `StateFlow`.
- **Consequences:** One language and no bridge layer. iOS is out of reach, which matches the
  non-goals.

## ADR-002 — Standalone, no network, no `INTERNET` permission

- **Status:** Accepted, 2026-11-17.
- **Context:** Alarmy's ads and tracking are the reason to replace it. Privacy is a feature.
- **Decision:** No backend, accounts, analytics or ads. The release build must not declare
  `INTERNET`; a Gradle task (`checkNoInternetPermission`, part of `check`) fails the build if the
  merged release manifest contains it.
- **Consequences:** Every dependency is vetted for transitive permissions. No cloud backup or sync;
  a future export/import is a local file.

## ADR-003 — Sideloaded APK only

- **Status:** Accepted, 2026-11-17.
- **Context:** Personal use. Play adds policy declarations for exact alarms, full-screen intents and
  foreground-service types.
- **Decision:** Distribute as a signed APK installed by hand. Avoid Play-incompatible choices only
  where that costs nothing.
- **Consequences:** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is acceptable. Updates require the same
  signing key, so the keystore must be backed up (M4).

## ADR-004 — Target device and SDK levels

- **Status:** Accepted, 2026-11-17.
- **Context:** Single user with a Samsung Galaxy S25 Ultra on Android 17 (API 37). Several APIs the
  engine needs are gated at 31–34.
- **Decision:** `minSdk 34`, `targetSdk 37`, `compileSdk 37`. Test on the S25 Ultra only (superseded for day-to-day testing by ADR-023: emulator first, device in DV).
- **Consequences:** Every API-gated piece (`systemExempted`, `canUseFullScreenIntent()`,
  `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`) is always available: no per-API branches. Still installable
  on a spare phone with Android 14+. Samsung One UI battery management is the OEM to handle.

## ADR-005 — Application ID `com.nutabe.chrona`

- **Status:** Accepted, 2026-11-17.
- **Context:** The ID is permanent for a sideloaded app; changing it means a new app and lost alarms.
- **Decision:** `com.nutabe.chrona` (debug build: `com.nutabe.chrona.debug`, see ADR-017).
- **Consequences:** None beyond the above.

## ADR-006 — JSON file in device-protected storage, no database

- **Status:** Accepted, 2026-11-17.
- **Context:** A handful of alarms. The store must be readable before the first unlock after a
  reboot.
- **Decision:** One JSON file (`kotlinx.serialization`) written with `AtomicFile` in
  device-protected storage, holding `StoreData(schemaVersion, alarms, activeRing)`. Every field has a
  default and the parser ignores unknown keys. An unparseable file is renamed to `.corrupt`, the
  store starts empty and a notification says so.
- **Consequences:** No Room or DataStore. Device-protected storage is less protected than
  credential-encrypted storage, which is acceptable for alarm settings. `App.onCreate()` must never
  touch credential-encrypted storage.

## ADR-007 — Manual dependency injection

- **Status:** Accepted, 2026-11-17.
- **Context:** A small single-module app.
- **Decision:** Wire dependencies by hand in `App`; no Hilt or Koin.
- **Consequences:** Less build tooling. Revisit only if the object graph becomes painful.

## ADR-008 — `setAlarmClock` + `USE_EXACT_ALARM`

- **Status:** Accepted, 2026-11-17. The exact permission set is confirmed against the docs at M1.
- **Context:** Alarms must fire on time in Doze. `minSdk 34` (ADR-004).
- **Decision:** Schedule with `AlarmManager.setAlarmClock()`; declare `USE_EXACT_ALARM` only.
  `PendingIntent` request codes are keyed by (alarm id, kind: `NEXT`, `SNOOZE`, `RING_NOW`,
  `BACKUP`).
- **Consequences:** Exempt from Doze; the system shows the alarm icon. A snooze never replaces the
  next occurrence. Force-stop still cancels all alarms (platform limitation, documented).

## ADR-009 — Ringing in a `systemExempted` foreground service + dedicated `RingingActivity`

- **Status:** Accepted, 2026-11-17.
- **Context:** Ringing must survive the activity being closed. Showing the main activity over the
  lock screen would expose the app to anyone holding the phone.
- **Decision:** `AlarmReceiver` starts `RingingService` (FGS type `systemExempted`, allowed with the
  exact-alarm permission), which plays `USAGE_ALARM` audio and posts a full-screen intent to
  `RingingActivity`. Only that activity shows over the lock screen; it has its own task, is excluded
  from recents and finishes when the ring ends. The receiver hands its wake lock to the service.
- **Consequences:** `RingingController` (in-process `StateFlow`) connects service and activity. While
  the phone is in use, the alarm appears as a heads-up notification with "Solve to stop" and
  "Snooze" only. Android 17's background-audio hardening is satisfied by exact alarm + `USAGE_ALARM`.

## ADR-010 — Every ringing component is direct-boot aware

- **Status:** Accepted, 2026-11-17.
- **Context:** A reboot overnight leaves the phone locked until the user unlocks it.
- **Decision:** Both receivers, `RingingService` and `RingingActivity` are `directBootAware`; the
  bundled sound ships in the APK; a user-chosen sound that can't be read before unlock falls back to
  it.
- **Consequences:** Verified by M1c row 3 (reboot without unlocking).

## ADR-011 — Persisted runtime state and reconciliation

- **Status:** Accepted, 2026-11-17.
- **Context:** Storing only configuration loses a ring or snooze on reboot, update or process death,
  and makes rebooting a way to skip the mission.
- **Decision:** Persist `nextTriggerAt`, `snoozedUntil`, `snoozesUsed` per alarm and an `ActiveRing`
  record (with `forcedSnoozeCycles`). A `Reconciler` runs on boot, time and timezone change, package
  update and app start:
  - `ActiveRing` or a past `snoozedUntil` **always** re-rings, however old.
  - A never-fired trigger re-rings within 15 min, otherwise it becomes "missed" (one-shots are
    disabled, never rolled to tomorrow).
  - Re-ringing goes through a near-term `setAlarmClock`, so no FGS is started from boot.
- **Consequences:** Reconciliation is pure Kotlin and fully unit tested. One-shots are disabled when
  the ring ends, not when it fires.

## ADR-012 — Process-death recovery: `START_STICKY` + backup alarm

- **Status:** Accepted, 2026-11-17 (chosen by the user at the M1a checkpoint, after the restart
  spike).
- **Context:** `START_STICKY` may not restart the service on One UI, or may not let it return to the
  foreground from the background.
- **Decision:** `START_STICKY` resumes from `ActiveRing`, and while a ring is active a `BACKUP`
  alarm is kept re-armed about 60 s ahead so a dead process is restarted through the normal
  receiver path.
- **Consequences:** At most about 60 s of silence after a crash. The backup alarm is built in M1b
  (task 6). Whether it is also needed on One UI is checked in DV, but it is kept either way.
- **Spike evidence (M1a, API 37 emulator, AOSP; not One UI):** the process was killed mid-ring
  (`run-as … kill <pid>`) twice. (a) App in the foreground: died 13:31:43.571, `Scheduling restart of
  crashed service … in 1000ms`, `onStartCommand action=null` at 13:31:44.745, resumed from the active-ring
  marker, re-entered the foreground, sound back. (b) App in the background (launcher focused): died
  13:33:36.071, restart logged 13:33:37.261, same recovery. No foreground-service exception. So
  `START_STICKY` alone was enough on AOSP, with about 1.2 s of silence. This does not cover One UI
  killers or a swipe from Recents; those are DV. The user chose to keep the backup alarm anyway, since
  the emulator cannot show One UI's behavior.
- **DV evidence (2026-11-18, S25 Ultra, One UI):** the user confirmed restart behavior (DV-CHECKLIST
  item 2) and that an alarm set overnight rang as expected. The backup alarm stays.

## ADR-013 — Missions: math, shake, steps; math as universal fallback

- **Status:** Accepted, 2026-11-17.
- **Context:** The user wants to stop alarms by shaking, solving math problems or walking.
- **Decision:** Mission logic is pure Kotlin in `mission/`, unit tested, fed by thin sensor adapters.
  Math (own numpad, fresh problem after a wrong answer) is the fallback whenever a sensor or
  permission is missing at ring time. `ACTIVITY_RECOGNITION` is requested in the editor, never at
  ring time. There is never a "Dismiss" action.
- **Consequences:** The user always has a way to stop the alarm, and never one that skips the work.

## ADR-014 — Steps counted in `RingingService`

- **Status:** Accepted, 2026-11-18 (DV on the S25 Ultra: steps keep counting with the screen off).
- **Context:** People pocket the phone or press power while walking; a listener scoped to the
  activity would stop counting.
- **Decision (proposed):** Register `TYPE_STEP_DETECTOR` (latency 0; `TYPE_STEP_COUNTER` delta as
  fallback) inside the foreground service, with a cadence filter (≤ ~3–4 steps/s) and vibration off
  during the mission.
- **Alternative if the spike fails:** count only while `RingingActivity` is visible, with the screen
  kept on; turning the screen off pauses counting and restores full volume.
- **Consequences:** The outcome changes where the M3 sensor adapters live.
- **Spike evidence (M1a):** the API 37 emulator cannot answer. `dumpsys sensorservice` lists no step
  sensor, `pm list features` has no `sensor.stepcounter` or `sensor.stepdetector`, and `adb emu sensor
  status` has no step channel. The debug `StepSpikeService` (a `systemExempted` foreground service
  holding a partial wake lock) starts and stops cleanly and logs "sensor type 18/19 unavailable". Per
  M1a, the proposal is kept pending DV on the S25, where the spike is run with the screen off while
  walking; a debug-only fake step source feeds the same pipeline for tests in M3. The user confirmed
  at the M1a checkpoint (2026-11-17) that it stays *Proposed* until DV.
- **DV evidence (2026-11-18, release APK on the S25 Ultra):** the user reported that the steps mission
  counts with the screen off, so counting stays in the service and the fallback is not needed.

## ADR-015 — Auto-silence as a capped forced snooze

- **Status:** Accepted, 2026-11-17.
- **Context:** A phone left at home must not ring forever, but stopping for good after 15 min would
  let the user sleep through the mission.
- **Decision:** After 15 min of ringing, stop and re-ring as a forced snooze, up to 3 cycles
  (persisted), then stop and post "missed". Forced snoozes don't consume the user's snooze allowance.
- **Consequences:** Worst case, about an hour of intermittent ringing.

## ADR-016 — No-bypass rules while ringing or snoozed

- **Status:** Accepted, 2026-11-17.
- **Context:** Each easy way to silence the alarm defeats the mission.
- **Decision:** Back is blocked; hardware volume keys are consumed in `RingingActivity`; a
  `STREAM_ALARM` observer restores the level if lowered from the volume panel; the ringing or snoozed
  alarm can't be toggled, edited or deleted in the app; a swiped notification is re-posted.
- **Consequences:** Verified by the M3 no-bypass checklist. Force-stop and uninstall remain possible
  (platform limits).

## ADR-017 — Debug and release builds side by side; no backup

- **Status:** Accepted, 2026-11-17 (confirmed by the user).
- **Context:** Debug and release builds have different signing keys; with the same application ID,
  switching would require an uninstall and lose the alarms. Cloud backup is a non-goal.
- **Decision:** Debug build uses `applicationIdSuffix ".debug"`. `android:allowBackup="false"`.
  Debug-only tooling lives in the `debug` source set.
- **Consequences:** Both builds can be installed at once; the release build never contains debug
  screens.

## ADR-018 — Spanish and English strings from the first screen

- **Status:** Accepted, 2026-11-17 (confirmed by the user).
- **Context:** The UI must be in Spanish and English; retrofitting strings at the end is error-prone.
- **Decision:** Every user-facing string lives in `values/` (English) and `values-es/` (Spanish),
  written as each screen is built (M2 onwards). M4 only reviews them.
- **Consequences:** None beyond a little more work per screen.

## ADR-019 — Sensor watchdog: 2 min for steps, 30 s for shake

- **Status:** Accepted, 2026-11-17 (option b, chosen by the user).
- **Context:** The sensor watchdog offers "Switch to math" after 30 s with no sensor events, so a
  sensor that exists but delivers nothing can't make the alarm undismissable. But the step detector
  is also silent while the user stands still, so on a steps alarm the watchdog can't tell a dead
  sensor from "not walking yet".
- **Options:**
  - (a) Keep 30 s: "Switch to math" appears on every steps alarm after 30 s of standing still.
  - (b) A longer timeout for steps (for example 2 min).
- **Decision:** (b). The watchdog offers "Switch to math" after **2 min** without step events on a
  steps mission, and after **30 s** without events on a shake mission.
- **Consequences:** Math is not an easy escape from walking. A truly broken step sensor costs up to
  2 min of ringing before the escape hatch appears.

## ADR-020 — Brand palette, dark only, no dynamic color

- **Status:** Accepted, 2026-11-17 (dark only confirmed by the user).
- **Context:** The user defined a 60-30-10 palette: Main `#16292B`, Second `#9FB3AB`, Accent
  `#FF8A1F`. Main is nearly black, and the app is mostly used in the dark.
- **Decision:** The palette is implemented as Material 3 color schemes, as specified in
  [DESIGN.md](DESIGN.md):
  - **Dark only.** There is no light theme, whatever the system setting.
  - Dynamic color is off.
  - Text on the Accent is Main. Accent and Second are never paired with each other (contrast 1.07).
- **Consequences:**
  - The app looks the same regardless of the wallpaper.
  - Every `ColorScheme` role is set explicitly, so no component falls back to Material's purple.
  - One color scheme to design, build and test. The Accent and Second would fail as text on light
    backgrounds anyway (2.36 / 2.21 on white).

## ADR-021 — Screens designed in Stitch with the `high-end-visual-design` language

- **Status:** Accepted, 2026-11-17 (requested by the user). The font and icon choices inside it are
  decided at D2.
- **Context:** Designing the screens while coding them in M2/M3 risks rework, and the data model is
  fixed in M1b.
- **Decision:**
  - A design milestone (D) runs in parallel with M0–M1a.
  - Screens are generated and iterated in Google Stitch, using the global `high-end-visual-design`
    skill as the visual language.
  - The skill is web-oriented, so its rules are translated in MILESTONES.md (D). Blur, grain and
    scroll entry animations are dropped; double-bezel cards, pill CTAs, eyebrow tags and spring
    motion are kept.
  - Stitch output is a visual reference only, never code to convert.
- **Consequences:**
  - Design content (screen descriptions, palette) is sent to Google Stitch, an external service.
    Prompts contain no personal data.
  - Replacing the system font and Material icons adds bundled assets: a font file and about 20
    vector drawables. These are assets, not library dependencies.

## ADR-022 — Unattended milestone loop: local commits, pause on user items

- **Status:** Superseded by ADR-025 (2026-11-17). Originally accepted 2026-11-17 (both rules
  chosen by the user).
- **Context:** The user wants the milestones worked on unattended, in another session, with
  automatic recovery after an unexpected stop. The global rules require a checkpoint before every
  commit, and many verifications need the user's hands.
- **Decision:** A project skill, `milestone-loop` (run as `/loop /milestone-loop`), with these
  rules:
  - The only state is `docs/STATUS.md`, written before and after every task, so an interrupted run
    resumes from it.
  - At each checkpoint it makes **one local commit and never pushes**.
  - It **pauses** whenever a checkpoint has [user] verifications, decisions or gating approvals, and
    leaves a checklist under "Waiting for you".
  - It continues past checkpoints that are agent-only.
- **Consequences:**
  - Commits made unattended are reviewed after the fact; they are local and revertible.
  - Proposed and Open ADRs and the D2 visual choices are never decided by the loop.
  - Unattended runs need the routine commands pre-allowed in the permission settings.

## ADR-023 — Develop and verify on an Android 17 emulator; real device at the end (DV)

- **Status:** Accepted, 2026-11-17 (the user's decision).
- **Context:** The user will develop entirely against the emulator. Only at the end will they choose
  between connecting the S25 Ultra and installing the APK by hand. An emulator also lets the agent
  run almost every verification itself, which suits the unattended loop (ADR-022).
- **Decision:**
  - Development and verification use the AVD `Chrona_API37`, with the image
    `system-images;android-37.0;google_apis;x86_64` and a lock-screen PIN for the direct-boot tests.
    The agent drives it through `adb`, `adb emu` and screenshots.
  - What needs real hardware moves to a final **DV** milestone: walking, One UI's battery
    management and process restarts, the Samsung lock screen, and real calls.
- **Consequences:**
  - ADR-012 (restart) and ADR-014 (steps in the service) stay *Proposed* until DV, unless the
    emulator answers them.
  - If DV disproves ADR-014, the steps adapter changes late; PLAN.md §5.3 documents that fallback.
  - Most [user] verification steps became [agent] steps. The user reviews screenshots and
    recordings, and makes the DV decision.

## ADR-024 — Visual direction: editorial serif + tabular sans hybrid; Phosphor icons

- **Status:** Accepted, 2026-11-17 (chosen by the user in D2).
- **Context:** D2 compared two archetypes in Stitch (`docs/design/variants/`):
  - **A, Editorial:** serif, magazine-like, the most distinctive.
  - **B, Ethereal:** geometric sans and hairlines, technical.

  A's serifs had proportional digits, which make live times and counters jitter.
- **Decision:**
  - A's character for titles, labels and composition, with B's numerals.
  - **Newsreader** (serif) for text, and **Plus Jakarta Sans** with `tnum` for every time and
    counter. The figure widths were verified from the font files.
  - No italics, and 24-hour times.
  - **Phosphor** icons: Light in general, Regular on the ringing and mission screens.
- **Consequences:**
  - Two font files and about 20 vector drawables are bundled as assets; no library dependency.
  - The system font is no longer used; this supersedes the "system font" line of the original
    DESIGN.md.

## ADR-025 — Unattended loop decides on its own: recommended option, else advisor

- **Status:** Accepted, 2026-11-17 (requested by the user). Supersedes ADR-022's "pause on user
  items".
- **Context:** Pausing at every decision or checkpoint approval kept the loop from running truly
  unattended.
- **Decision:** The `milestone-loop` skill never waits for the user's answer.
  - Every decision (an option choice, a *Proposed* or *Open* ADR, a checkpoint approval, an
    ambiguity) takes the recommended or default option.
  - When there is none, it follows the advisor, with one reconcile call if the advice contradicts
    the evidence.
  - Each decision is logged in `docs/STATUS.md` as a `DECIDED` line.
  - From ADR-022, these stay: one local commit per checkpoint, never push, and *Accepted* ADRs are
    superseded rather than edited.
  - Only physical steps that need a human or the real phone (DV) still go to "Waiting for you".
- **Consequences:**
  - The user reviews decisions and commits after the fact, through the journal and `git log`.
  - MILESTONES.md's "explicit approval before committing" applies to interactive work only. Under
    the loop, a checkpoint is passed by the loop itself.
