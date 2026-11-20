# Chrona — Milestones

Detailed breakdown of the milestones summarized in [PLAN.md](PLAN.md) §7. PLAN.md owns the *what*
and *why* (architecture, failure modes, decisions); this file owns the *order of work* and *how each
milestone is verified*. Decisions are logged in [ADR.md](ADR.md).
Progress lives in [STATUS.md](STATUS.md), maintained by the global `run-milestones` skill with the
project specifics in [LOOP.md](LOOP.md); it can run the milestones unattended and resume after an
interruption. Finished milestones are archived in [`journal/`](journal/).

**Rules for every milestone**
- **Development and verification run on an emulator** (ADR-023): AVD `Chrona_API37`, image
  `system-images;android-37.0;google_apis;x86_64` (Google APIs, so `adb root` works). The agent
  starts and drives it with `emulator`, `adb` and screenshots (`adb exec-out screencap -p`).
- What only real hardware can prove (walking, Samsung One UI behavior) is collected in **DV —
  Device validation**, at the end, on the S25 Ultra (Android 17) or by installing the APK by hand.
- A milestone is done only when every exit criterion is met and its verification has been run, with
  the results reported (including anything skipped or failed).
- Each milestone (or sub-milestone) ends at a **checkpoint**: a summary of what was built,
  verification results, and explicit approval before committing (under the unattended loop, the loop
  passes the checkpoint itself and logs it; ADR-025). One commit per checkpoint. Nothing starts
  before the previous checkpoint is approved, except **D (screen design)**, which runs in
  parallel with M0–M1a and has its own gates.
- No dependency is added unless a task below names it. After any dependency change, `./gradlew
  check` re-runs the `INTERNET` check (M0, task 7).
- Debug-only tooling lives in the `debug` source set, so it never ships in the release build.
- Verification steps are tagged **[agent]** (run by Claude: builds, tests, adb commands) or
  **[user]** (needs a human: subjective judgment, approvals, and anything in DV). Emulator
  commands are recalled, not yet verified; M0 and M1a confirm them and fix this file if they differ.

---

## M0 — Environment and scaffold

**Goal:** an empty Compose app, built from the command line, running on the emulator, under git.

**Tasks**
1. **[agent]** Environment: Android Studio, SDK Platform 37, Build-Tools, Platform-Tools and JDK 21
   are installed. Install the emulator image with
   `sdkmanager "system-images;android-37.0;google_apis;x86_64"` and create the AVD `Chrona_API37`
   with `avdmanager` (a Pixel-class device profile). ⚠️ Then set `disk.dataPartition.size=6G` in
   `~/.android/avd/Chrona_API37.avd/config.ini`: the default (2 GB) is too small for this image, and
   the symptom is SurfaceFlinger crash-looping with `Assertion failed:
   !rcEnc->featureInfo()->hasReadColorBufferDma`, not a storage error.
2. **[agent]** Boot the emulator (`emulator -avd Chrona_API37`, `-no-window` when unattended) and
   wait for `sys.boot_completed`. Set a lock-screen PIN with `adb shell locksettings set-pin 1234`;
   the direct-boot tests need it.
3. **[agent]** Write the project scaffold by hand, equivalent to the *Empty Activity* (Compose)
   template: name **Chrona**, application ID and namespace **`com.nutabe.chrona`**, Kotlin, Kotlin
   DSL, version catalog. Generate the Gradle wrapper with the Gradle 8.14.3 distribution already in
   `~/.gradle/wrapper/dists`, or a newer one if AGP requires it. The existing `docs/` and `.claude/`
   stay. Fallback if this fails: **[user]** runs the Android Studio wizard with these values.
4. **[agent]** Configure the project:
   - `targetSdk = 37`, `compileSdk = 37`; AGP, Kotlin and Compose BOM at current stable.
   - Debug build: `applicationIdSuffix = ".debug"` and a "Chrona (debug)" label, so debug and
     release builds install side by side.
   - Manifest: `android:allowBackup="false"`; remove the template's backup/data extraction rules.
5. **[agent]** `.gitignore` (template plus `local.properties`, `*.jks`, `*.keystore`,
   `keystore.properties`, `.claude/settings.local.json`) and `README.md` (purpose, non-goals, build and install commands).
6. **[agent]** `git init`.
7. **[agent]** **`INTERNET` check**: a Gradle verification task, `checkNoInternetPermission`, that
   depends on the release manifest-merge task, reads its output, and fails if it contains
   `android.permission.INTERNET`. `check` depends on it. No plugin or library needed.

**Verification**
- **[agent]** `./gradlew assembleDebug installDebug` on the emulator; a screenshot shows the app
  open.
- **[agent]** `./gradlew check` succeeds and the task log shows the merged manifest was read.
- **[agent]** Negative test: temporarily add `INTERNET`, confirm `check` fails, revert.
- **[agent]** `git status` shows no build outputs, IDE caches or `local.properties`.

**Exit criteria:** the app runs on the emulator; the `INTERNET` check has been seen to fail and pass.

**Checkpoint:** file tree and pinned versions. On approval: commit ("scaffold Chrona project"). No
remote is configured; pushing is a separate decision.

---

## D — Screen design (Google Stitch + `high-end-visual-design`)

**Goal:** every screen designed and approved before it is built, so M2 and M3 only implement an
agreed design.

**Timing:** runs in parallel with M0–M1a, since it needs no code.
- **D1** must be approved before M1b, because it reviews the data model.
- **D2** must be approved before M2.

**Tools**
- **Google Stitch** (MCP) generates and iterates mobile screens. Its HTML/Tailwind output is a
  **visual reference only**; M2 and M3 implement the screens in Compose and do not convert that code.
- The global **`high-end-visual-design`** skill sets the visual language. It is written for the web,
  so the table below translates its rules to an Android alarm app.
- [DESIGN.md](DESIGN.md) stays the source of truth for color and contrast. Where the skill and
  usability conflict, usability for a half-asleep user wins.

**How the skill's rules apply to Chrona**

| Skill rule | In Chrona |
|---|---|
| Vibe archetype (pick one) | Choose between *Editorial Luxury* (sage and deep tones, close to the palette) and *Ethereal Glass* (dark, glowing accents). Generate both as Stitch variants in D2 and pick one. In either case the backgrounds are the brand Main, not `#050505`. |
| Banned fonts (Roboto, Inter…) | Replace the system font with a bundled font (OFL license, a file in `res/font`, no network). Candidates: Plus Jakarta Sans, Geist. It **must have tabular figures** (DESIGN.md §4). This changes DESIGN.md §4 and is decided in D2. |
| Banned icons (Material, thick Lucide) | Thin-line icons (Phosphor Light or Remix Line), imported as vector drawables, **only the ~20 used**. On the ringing and mission screens, use one weight heavier so sleepy eyes can read them. |
| Double-bezel cards | Alarm cards, health items and the mission panel: an outer shell (Main + 6 %, hairline outline in Second at low alpha) around an inner core (Main + 10 %), with concentric radii. Hairlines are decorative; interactive boundaries keep ≥ 3:1. |
| Pill CTAs with button-in-button icon | "Start mission", Save and the FAB: Accent pill with a nested icon circle. |
| Eyebrow tags | Small uppercase labels above sections ("NEXT ALARM", "MISSION"). |
| Macro-whitespace (`py-24`+) | **Adapted:** generous spacing, but the list still shows at least 4 alarms on the S25 Ultra without scrolling. |
| Spring and custom-bezier motion | Compose `spring()` and custom easing; nothing linear. Respect the system's "Remove animations" setting. |
| Scroll entry animations | **Not applied.** The alarm list is a utility and appears instantly. |
| Floating island nav | Only where it fits: a floating bottom action bar in the editor. The app has no tab navigation. |
| Glass / `backdrop-blur`, grain overlay | **Not applied.** Costly on Android, and noise and glare are wrong on a screen seen in the dark. |
| Ringing and mission screens | No decorative motion or texture; 64 dp touch targets (DESIGN.md §5). |

**Screens.** Copy is in Spanish, the primary language, so string length is tested.
1. Alarm list: populated, empty state, and with a snoozed/locked alarm.
2. Alarm editor.
3. Mission picker and mission settings (math, shake, steps).
4. Settings.
5. Health screen, with OK, attention and blocking states.
6. Onboarding (2–3 steps).
7. Ringing screen.
8. Math mission (numpad, wrong-answer state).
9. Shake mission.
10. Steps mission, including the "Switch to math" state (ADR-019).
11. Mission preview (from the editor).

### D1 — Structure

**Tasks**
1. **[agent]** Create the Stitch project "Chrona". Upload a Stitch-oriented design file
   (`docs/design/stitch-DESIGN.md`: the DESIGN.md tokens plus the adapted skill rules), and create
   the design system from it (`deviceType: MOBILE`).
2. **[agent]** Generate all 11 screens with that design system. Prompts describe content and
   hierarchy and contain no personal data.
3. **[agent]** Export a screenshot of each screen to `docs/design/screens/`.
4. **[agent]** **Data-model review:** list every field the screens show or edit. Check each against
   `Alarm`, `SnoozeConfig`, `Mission` and `AppSettings` (PLAN.md §2), and record what is missing or
   unused.

**Verification**
- **[user]** Each screen has the right content and hierarchy (layout, not the final look).
- **[agent]** The data-model diff is resolved, and PLAN.md §2 is updated if anything changed.

**Checkpoint D1:** screenshots + data-model diff. On approval: commit `docs/design/` and any
PLAN.md change, if git exists by then (M0); otherwise in the M0 commit.

### D2 — Visual direction and final screens

**Tasks**
1. **[agent]** Generate variants (`generate_variants`, `EXPLORE`) of the alarm list and the ringing
   screen, for both vibe archetypes. **[user]** picks one direction.
2. **[agent]** Apply the chosen direction to all screens (`apply_design_system` / `edit_screens`)
   and iterate on feedback.
3. **[user]** Decide the font and the icon set (the two candidates above, or keep the system font).
4. **[agent]** Update DESIGN.md: typography, icons, component specs (double-bezel card, pill CTA,
   eyebrow tag), spacing and motion values, and add an ADR for the visual direction.
5. **[agent]** Export the final screenshots to `docs/design/final/` (the reference for M2 and M3;
   `docs/design/screens/` keeps D1's superseded structure screens) and link the Stitch project
   from DESIGN.md.

**Verification** (**[agent]**, per screen, reported as a checklist)
- Palette rules from DESIGN.md §1: no Accent on Second, text on Accent is Main, color never alone.
- One Accent-filled action per screen.
- Touch targets ≥ 48 dp, and ≥ 64 dp on the ringing and mission screens.
- Times and counters use tabular figures.
- Spanish copy fits without truncation.
- None of the skill's banned patterns, except where the table above adapts them.

**Exit criteria:** all 11 screens approved, and DESIGN.md updated to match.

**Checkpoint D2:** final screens + checklist. On approval: commit ("add screen designs").

---

## M1 — Alarm engine

The riskiest milestone, so it is split into three checkpoints. M1a proves the platform behaviors on
the emulator **before** any engine code is built on top of them. Behavior specific to the S25 is
re-checked in DV.

### M1a — Walking skeleton and spikes

**Goal:** one hardcoded alarm travels the whole ringing path on the emulator, and the two platform
unknowns (step sensors in the service, service restart) get the answers the emulator can give.

**Tasks**
1. Manifest permissions per PLAN.md §4 and `directBootAware="true"` on every component below.
2. `AlarmReceiver` + `WakeLockHolder`: partial wake lock with a timeout, starts `RingingService`; the
   service releases it once it holds its own.
3. `RingingService` (`foregroundServiceType="systemExempted"`): alarm notification channel,
   full-screen intent, `MediaPlayer` with `USAGE_ALARM` looping the bundled sound (`res/raw`),
   vibration.
4. Minimal `RingingActivity`: show-when-locked, turn-screen-on, keep-screen-on, own task,
   `excludeFromRecents`, a "Stop" button, `finish()`.
5. Debug screen: permission status with deep links, "ring in 1 min" (hardcoded, `setAlarmClock`),
   "step spike".
6. **Step spike**: a silent service mode that registers `TYPE_STEP_DETECTOR` and `TYPE_STEP_COUNTER`
   and logs every event with a timestamp. `ACTIVITY_RECOGNITION` is requested from the debug screen.
7. **Restart spike**: kill the process mid-ring and observe whether `START_STICKY` restarts the
   service *and* lets it return to the foreground (AOSP behavior; One UI is re-checked in DV).

**Verification**
- **[agent]** Rings with the app swiped away and the screen locked (`input keyevent KEYCODE_SLEEP`):
  a screenshot shows it full screen over the keyguard.
- **[agent]** Rings as a heads-up while the emulator is unlocked and in another app; tapping it
  (`input tap`) opens `RingingActivity`.
- **[agent]** Forced Doze: `adb shell dumpsys battery unplug`, `adb shell dumpsys deviceidle
  force-idle`, alarm fires; then `deviceidle unforce`, `battery reset`. ⚠️ On the API 37
  `google_apis` image, `force-idle` stays at `INACTIVE` ("Unable to go deep idle"), so deep Doze
  could not be reproduced: the screen-off, unplugged check is the most this emulator gives, and deep
  Doze is re-checked in DV.
- **[agent]** Step spike: check whether the emulator exposes step sensors (`adb shell dumpsys
  sensorservice`) and whether `adb emu sensor` can drive them.
  - If it can, verify that events keep arriving inside the service with the screen off.
  - If not, record that the emulator can't answer. ADR-014 stays *Proposed* until DV, steps are
    counted in the service as proposed, and a debug-only fake step source feeds the same pipeline
    for tests.
- **[agent]** Restart spike: `adb shell run-as com.nutabe.chrona.debug kill <pid>` mid-ring; logcat shows
  whether the service came back in the foreground and the sound resumed.

**Exit criteria / decisions recorded in ADR.md:**
- Steps are counted in the service (spike passed, or proposal kept pending DV) or with the screen
  kept on (PLAN.md §5.3 fallback).
- Restart strategy: `START_STICKY` alone, or plus the backup alarm (M1b task 6). The backup alarm is
  the default unless the spike clearly shows `START_STICKY` is enough.

**Checkpoint:** spike results + decisions. On approval: commit ("add ringing path skeleton").

### M1b — Store, scheduling and reconciliation

**Goal:** real alarms, persisted, scheduled and reconciled.

**Dependencies added:** `kotlinx-serialization-json` and its Gradle plugin.

**Tasks** (each pure-logic step lands with its tests)
1. **Model** per PLAN.md §2, including `ActiveRing` with `forcedSnoozeCycles` and a store-level
   `schemaVersion`. `Instant` serialized as epoch millis. Every field has a default, and the
   `Json` instance uses `ignoreUnknownKeys = true`, so later model changes never hit the corruption
   path.
2. **`AlarmStore`**: `AtomicFile` in `createDeviceProtectedStorageContext()`, `StateFlow<StoreData>`,
   mutators serialized with a `Mutex`. A file that fails to parse is renamed to
   `alarms.json.corrupt`, the store starts empty and a notification says so.
3. **`NextTrigger`** (pure, injected `Clock`): one-shot today/tomorrow, weekdays, all days, "today
   already passed", DST gap and overlap in `America/New_York` and `Europe/Madrid`.
4. **`Reconciler`** (pure): `(StoreData, now) → actions`, per PLAN.md §2. **`ActiveRing` and
   `snoozedUntil` always re-ring, however old**; only a never-fired `nextTriggerAt` can expire into
   "missed". **Idempotence:** a trigger for the alarm already in the live `ActiveRing` (from app start,
   `TIME_SET` or the `BACKUP` alarm) is a no-op, never a queued second ring. Tests cover every
   branch, including that one.
5. **`Scheduler`**: `setAlarmClock`; `PendingIntent` request codes keyed by **(alarm id, kind)**
   with kinds `NEXT`, `SNOOZE`, `RING_NOW` and `BACKUP`, so a snooze never replaces the next
   occurrence.
6. **Backup alarm** (per the M1a decision): while `ActiveRing` is set, the service keeps a `BACKUP`
   alarm re-armed about 60 s ahead. If the process dies, the backup fires and the normal receiver
   path restarts the ring. It is cancelled when the ring ends.
7. **`RingingController`**: `StateFlow<RingingState>` (`Idle`, `Ringing`, `Snoozed`); actions
   `stop()` and `snooze()`.
8. **`RingingService`** now uses the store: persists `ActiveRing`; sound fallback chain (chosen →
   bundled → vibration only); sets `STREAM_ALARM` to the alarm's level and restores it; volume
   ramp; re-posts the notification if swiped (delete intent).
9. **`BootReceiver`**: `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`,
   `MY_PACKAGE_REPLACED` → `Reconciler`. The app also reconciles on start. `App.onCreate()` touches
   only the device-protected context.
10. Minimal `RingingActivity` gains a "Snooze" button. The debug screen lists stored alarms with
    their `nextTriggerAt`, and can create an alarm with an invalid sound URI.

**Verification**
- **[agent]** `./gradlew test` green: store round-trip, corruption, unknown keys, `NextTrigger`,
  `Reconciler`.
- **[agent]** A snooze plus the alarm's next occurrence both appear in `adb shell dumpsys alarm`.

**Checkpoint:** test report. On approval: commit ("add alarm store, scheduler and reconciliation").

### M1c — Reliability matrix

**Goal:** every failure mode in PLAN.md §4 that the engine owns is proven on the emulator. The
emulator has the PIN from M0, so a reboot leaves it locked (direct boot).

| # | Scenario | Who | How / expected |
|---|---|---|---|
| 1 | App swiped from recents | agent | Rings with the screen locked (screenshot) |
| 2 | Doze | agent | As in M1a |
| 3 | Reboot, not unlocked | agent | Alarm 3 min ahead, `adb reboot`, don't unlock; rings, no crash in `App.onCreate` (logcat) |
| 4 | Lock screen | agent | Full screen over the keyguard; the alarm list is unreachable (screenshots after back and home) |
| 5 | Phone in use | agent | Heads-up; tapping it opens `RingingActivity` |
| 6 | Invalid sound | agent | Falls back to the bundled sound |
| 7 | Process killed mid-ring | agent | `run-as com.nutabe.chrona.debug kill <pid>`; ringing resumes (restart or backup alarm) |
| 17 | Re-trigger of a live ring | agent | While ringing: relaunch the app, change the time zone, let the `BACKUP` alarm fire. Still exactly one ring, nothing queued |
| 8 | Reboot mid-ring | agent | `adb reboot`; rings again after boot |
| 9 | Reboot mid-snooze | agent | `adb reboot`; the snooze fires on time after boot |
| 10 | **Off > 15 min mid-ring or mid-snooze** | agent | `adb emu kill`, wait 16 min, cold boot; rings again, no "missed" |
| 11 | One-shot never fired, off across alarm time + 15 min | agent | Same method; "missed" notification, alarm disabled, not moved to tomorrow |
| 12 | Time / timezone change | agent | `adb shell cmd alarm set-timezone …`; time via `adb root` + `date`; `dumpsys alarm` shows the recomputed trigger |
| 13 | Notification swiped while ringing | agent | Swipe it away in the shade (`input swipe`); re-posted |
| 14 | Alarm volume at 0 | agent | `adb shell cmd media_session volume --stream 4 --set 0`; rings at the configured level, volume restored afterwards |
| 15 | DND on (alarms allowed) | agent | `adb shell cmd notification set_dnd priority`; rings |
| 16 | App update with an alarm pending | agent | `adb install -r` the debug APK; alarm still rings |

Also: **[agent]** update the "confirm at M1" items in PLAN.md §9. The One UI battery settings move
to DV.

**Exit criteria:** all 17 rows pass (or a failure is explained and accepted at the checkpoint).

**Checkpoint:** filled-in matrix. On approval: commit ("verify engine reliability", fixes included).

---

## M2 — Alarm management UI

**Goal:** create and manage alarms with the real UI; settings, onboarding and health screen.

**Dependencies added:** Navigation Compose, `lifecycle-viewmodel-compose` (if not already in the
template).

**Tasks**
1. App shell: Material 3 theme from [DESIGN.md](DESIGN.md) (dark only, no dynamic color; ADR-020), Navigation Compose, manual DI container built in `App`.
2. **Alarm list**: sorted by time, enable toggle, repeat summary, mission icon, "rings in 7 h 12 min"
   header, FAB. The alarm that is ringing or snoozed is locked (no toggle, edit or delete) and shows
   "Solve to stop".
3. **Editor**: Material 3 time picker; repeat-day chips; label; sound picker
   (`RingtoneManager.ACTION_RINGTONE_PICKER`, plus "choose file" via `OpenDocument` with
   `takePersistableUriPermission`); vibration; ramp; max volume; snooze (on/off, minutes, max
   count); mission type and its settings.
   - Steps: disabled if the device lacks step sensors; picking it requests `ACTIVITY_RECOGNITION` in
     context.
   - Warning when `STREAM_ALARM` is muted.
   - Until M3, an alarm with any mission still rings with the plain Stop/Snooze screen.
4. **Delete with undo** (snackbar).
5. **Settings screen**: "Run onboarding again", a link to the health screen, and the app version. The
   onboarding-done flag lives in `StoreData.settings` (PLAN.md §2).
6. **Health screen** (PLAN.md §4) with deep links: notifications, full-screen intent
   (`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`), battery optimization, alarm volume, DND filter,
   physical activity (only if a steps alarm exists), Samsung battery guide.
7. **Onboarding**: the same checks on first launch, skippable.
8. **Strings**: every user-facing string in resources, in English (`values/`) and Spanish
   (`values-es/`), written as each screen is built.

**Verification**
- **[agent]** Compose UI tests: editor validation, list lock for ringing/snoozed alarms.
- **[agent]** Unit test: "rings in …" formatting with a fixed `Clock`, across midnight and DST.
- **[agent]** On the emulator: create, edit, toggle, delete + undo. Everything persists across an app
  restart and a reboot, and an alarm made in the UI rings. Screenshots in Spanish and English
  (`adb shell cmd locale set-app-locales`).
- **[agent]** The health screen matches reality: revoke and grant each permission with
  `adb shell pm revoke` / `pm grant` / `appops`.
- **[user]** Review the screenshots against the D2 designs.

**Exit criteria:** alarms are fully manageable without the debug screen.

**Checkpoint:** screenshots + results. On approval: commit ("add alarm list, editor, settings and
health screen").

---

## M3 — Ringing screen and missions

**Goal:** the real wake-up experience: missions that cannot be bypassed.

**Tasks**
1. **Mission logic** (`mission/`, pure Kotlin, tests first):
   - `MathGenerator`: injected `Random`, tiers per PLAN.md §5.1, positive integer answers.
   - `ShakeDetector`: magnitude threshold per sensitivity, ~250 ms debounce.
   - `StepCadenceFilter` (rejects > ~3–4 steps/s) and `StepCounterDelta` (counter fallback).
2. **Sensor adapters**: shake (`TYPE_LINEAR_ACCELERATION`, accelerometer + high-pass fallback) and
   steps (detector, counter fallback), placed where the M1a decision says. They are registered only
   while a mission is active.
3. **`RingingController` state machine**: `Ringing` → `MissionActive(progress)` → `Done`, plus
   `Snoozed`; progress feeds the watchdogs.
4. **Full `RingingActivity`**: time, label, "Start mission", "Snooze" (while snoozes remain); mission
   screens: math with its own numpad, shake progress, steps progress.
5. **Ringing rules** (PLAN.md §3):
   - Volume drops to ~20 % during a mission; the inactivity watchdog restores full volume after 15 s
     without progress.
   - Sensor watchdog: no sensor events for 30 s (shake) or 2 min (steps) → "Switch to math"
     (ADR-019).
   - Back is blocked; volume keys are consumed; a `STREAM_ALARM` observer restores the level.
   - Vibration is off during steps.
6. **Notification actions**: "Solve to stop" and "Snooze" only; never "Dismiss".
7. **Snooze limits and auto-silence**: 15 min → forced snooze, up to 3 cycles (persisted in
   `ActiveRing.forcedSnoozeCycles`) → stop + "missed" notification. Forced snoozes don't consume the
   snooze allowance.
8. **Second alarm** while one rings: queued; rings after the current one ends.
9. **Fallback to math** at ring start when a sensor or permission is missing.
10. **Mission preview** from the editor, with a "sound off" option; runs the mission UI without
    `RingingService` (sensor listeners scoped to the preview).

**Verification**
- **[agent]** `./gradlew test`: math bounds and determinism per tier; shake detector on recorded
  traces; cadence filter and counter delta on fake timestamps; state machine transitions.
- **[agent]** **No-bypass checklist** on the emulator, with `input keyevent` and screenshots. Each
  attempt must leave the alarm ringing or re-ringing:
  - back, home, recents, notification shade;
  - volume keys and the volume panel;
  - editing, toggling or deleting the alarm in the app while it rings or is snoozed;
  - rebooting, and powering off for more than 15 min;
  - waiting out auto-silence through all 3 cycles.
- **[agent]** Shake on the emulator: `adb emu sensor set acceleration x:y:z` bursts complete the
  mission at each sensitivity; slow tilts don't count.
- **[agent]** Steps on the emulator: emulator step sensors if M1a found them, otherwise the
  debug-only fake step source. A burst faster than the cadence limit doesn't count. Real walking is
  in DV.
- **[agent]** Sensor watchdog offers math when events are suppressed (debug toggle).
- **[agent]** Two alarms at the same minute: the second rings after the first is solved.
- **[user]** Review the screen recording (`adb shell screenrecord`) against the D2 designs.

**Exit criteria:** every mission completable; every bypass attempt fails; tests green.

**Checkpoint:** results + a short screen recording. On approval: commit ("add ringing screen and
missions").

---

## M4 — Hardening and release

**Goal:** a signed release APK that replaces Alarmy as the daily alarm.

**Tasks**
1. **Calls and audio focus**: request alarm audio focus. During a call, detected through audio focus
   loss and `AudioManager.mode` (no `READ_PHONE_STATE`), play a soft repeated tone, then resume.
2. **Polish**: review the ES/EN strings, a dark theme pass, an adaptive launcher icon with a
   monochrome layer (themed icons), accessibility labels on mission controls.
3. **Release signing**: keystore created outside the repo; `keystore.properties` git-ignored and read
   by Gradle. **Back up the keystore privately**: a sideloaded app can only be updated with the same
   key.
4. **R8**: `isMinifyEnabled = true`, `isShrinkResources = true`; confirm kotlinx.serialization
   classes survive (add rules if needed).
5. Install the release build alongside the debug one (different application ID) and recreate your
   alarms in it. There is no export yet.

**Verification**
- **[agent]** `./gradlew assembleRelease check`.
- **[agent]** M1c matrix and M3 no-bypass checklist re-run on the **release** build on the emulator,
  with
  these exceptions, which need a debuggable build and are covered by the earlier debug runs:
  - Row 6 (invalid sound): in release, pick a sound file, then delete it.
  - Row 7 (process kill) and the M3 sensor-watchdog check: debug only.
  - Row 16 (app update): `adb install -r` the release APK.
- **[agent]** During a simulated call (`adb emu gsm call <number>` / `gsm cancel`): soft tone, then
  normal ringing after hanging up.

**Checkpoint:** release summary. On approval: commit ("prepare release build").

---

## DV — Device validation (S25 Ultra or APK)

**Goal:** prove on real hardware what the emulator can't, then use Chrona as the daily alarm.

**Decision (user, at the start of DV):**
- (a) Connect the S25 Ultra over USB or wireless debugging, so the agent runs the `adb` steps.
- (b) Install the signed APK by hand and follow a checklist the agent writes.

**Tasks**
1. **[agent]** Write `docs/DV-CHECKLIST.md`: every item below, with exact steps for the chosen
   option.
2. **[user]** Install the release APK; grant permissions through onboarding.
3. Run the checklist:
   - **Step spike** (ADR-014): walk 2 minutes with the phone in a pocket and the screen off; steps
     keep counting inside the service. If not, apply the PLAN.md §5.3 fallback (screen kept on).
   - **Restart behavior on One UI** (ADR-012): kill the process mid-ring (option a) or force a crash
     from the debug build; the ring resumes.
   - **Samsung battery settings**: confirm the One UI names used by the health screen, and that a
     "Sleeping apps" listing is detected.
   - Full-screen intent over the Samsung lock screen, and heads-up while in use.
   - Shake and steps missions in hand and in a pocket; shaking must not count as walking.
   - Reboot without unlocking, with an alarm 3 minutes ahead.
   - A real phone call during a ring.
4. **[agent]** Fix whatever fails, re-run the affected emulator checks, and update the ADRs (014 and
   012 become *Accepted* or are superseded).

**Checkpoint DV:** filled-in checklist. On approval: commit ("validate on device").

**Dogfood week:** one week of daily use, keeping a short incident log. **Keep Alarmy as a backup
alarm during this week.**

**Final checkpoint:** incident log. Fixes, if any, go in their own commit. On approval: git tag
`v1.0.0`.
