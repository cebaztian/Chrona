# Chrona — Status

Maintained by the `milestone-loop` skill (`.claude/skills/milestone-loop/SKILL.md`). The loop
resumes from this file. Answer the "Waiting for you" items here or in the session.

## Now
- Milestone: M1b — done, approved unattended, committed locally. Next: M1c — Reliability matrix
- M1a approved by the user (ADR-012 accepted with the backup alarm; ADR-014 stays *Proposed* until DV)
- Emulator `Chrona_API37` is running (started by the user, 6 GB data partition), PIN `1234` set
- Last update: 2026-11-17 12:58

## Milestones
| Milestone | State | Checkpoint | Commit |
|---|---|---|---|
| M0 | done | auto-passed (unattended), 2026-11-17 | `1455276` "scaffold Chrona project" (local, not pushed) |
| D1 | done | approved by the user 2026-11-17 | included in the M0 commit (no git existed before) |
| D2 | in-progress: task 1 (variants) owned by the interactive session, so the loop must skip D2 | | |
| M1a | done | approved by the user 2026-11-17 | `2b1b521` "add ringing path skeleton" (local, not pushed) |
| M1b | done | approved (unattended), 2026-11-17 | "add alarm store, scheduler and reconciliation" (local, not pushed; hash in `git log`) |
| M1c | todo | | |
| M2 | todo (gated on D2) | | |
| M3 | todo | | |
| M4 | todo | | |
| DV | todo (your decision at the start: S25 over adb, or a hand-installed APK) | | |

## Tasks (last completed milestone: M0)
- [x] 1. SDK, system image and AVD `Chrona_API37`: done
- [x] 2. Boot the emulator, PIN `1234`: done
- [x] 3. Hand-written Compose scaffold and Gradle wrapper: done
- [x] 4. Project configuration (SDK 37, debug suffix, `allowBackup="false"`): done
- [x] 5. `.gitignore` and `README.md`: done
- [x] 6. `git init`: done
- [x] 7. `checkNoInternetPermission`: done

D1 tasks (done earlier): Stitch project and design system, the 11 screens, exported screenshots in
`docs/design/screens/`, data-model review.

## Waiting for you
- _(M1a decisions answered 2026-11-17: ADR-012 accepted as `START_STICKY` plus the 60 s backup alarm;
  ADR-014 stays *Proposed*; checkpoint approved. See the journal.)_
- [ ] **DV items collected so far (S25 Ultra or a hand-installed APK; never simulated):** run the step
  spike (grant Physical activity on the debug screen, "Step spike: start", lock the screen, walk,
  `logcat -s ChronaStepSpike`); check restart after a Recents swipe and under One UI's killers; deep
  Doze; `canUseFullScreenIntent()` on a fresh sideload.

## Checkpoints

### M1b — Store, scheduling and reconciliation (approved unattended, 2026-11-17)

**Verification**
| Check | Result |
|---|---|
| `./gradlew test` | Pass: 57 tests, 0 failures (NextTrigger 12, Reconciler 17, RingTransitions + FireGuard 16, Scheduler 3, StoreCodec 9). |
| `./gradlew check` (lint + `checkNoInternetPermission`) | Pass. |
| Snooze plus the next occurrence both pending (`adb shell dumpsys alarm`) | Pass. A repeating alarm that rang and was snoozed shows a SNOOZE entry at +5 min and a NEXT entry for tomorrow, with different `PendingIntent`s; `BACKUP` is cancelled. |
| Store on device | The JSON is persisted in device-protected storage (`/data/user_de/0/<pkg>/files/alarms.json`) with `activeRing` set while ringing, and after the snooze `snoozedUntil`, `snoozesUsed: 1` and the advanced `nextTriggerAt`. |
| Backup alarm | While ringing, `dumpsys alarm` shows the BACKUP about 60 s ahead; an explicit stop or snooze cancels it. |
| Process kill mid-ring | The service came back about 1 s later with a null intent and resumed from the store (`activeRing`). |
| Sound fallback chain | A stored alarm with an invalid sound URI logs "sound source 0 failed" and plays the bundled sound. |

**Not covered here, on purpose:** the corrupt-file path of `AlarmStore` (renaming to `alarms.json.corrupt`
plus its notification) is exercised only through the pure codec tests; the on-device check, the reboot
and locked-boot paths, `TIME_SET`/`TIMEZONE_CHANGED` and the "missed alarm" notification belong to the
M1c reliability matrix. Volume ramp and `STREAM_ALARM` restore are implemented but only the level
change was seen in the log (`alarm stream 6 -> 7 of 7`).

**Decisions:** ADR-012 *Accepted* (user, at the M1a checkpoint); ADR-014 stays *Proposed* until DV. No
new ADR was raised by M1b.

### M1a — Walking skeleton and spikes (agent work done 2026-11-17; paused for user decisions)

**Verification (emulator `Chrona_API37`, Android 17)**
| Check | Result |
|---|---|
| Rings with the app killed and the screen locked | Pass. Full screen over the keyguard; Stop ends it cleanly. App "swipe-away" approximated with `am kill`. |
| Heads-up while unlocked in another app; tap opens `RingingActivity` | Pass. |
| Forced Doze (`deviceidle force-idle`) | **Not reachable** on this image (stuck at INACTIVE). Verified instead: alarm fires with screen off, unplugged, forced inactive. Deep Doze → DV. |
| Step spike | **Emulator cannot answer** (no step sensors). Service verified for the "sensor absent" case. → DV. |
| Restart spike | `START_STICKY` restarted the service in about 1.2 s, foreground and background, ring resumed. One UI untested. |
| `./gradlew check` (lint + `checkNoInternetPermission`) | Pass. |

**Findings that change later milestones:** the foreground-service notification (and its full-screen
intent) is deferred about 10 s unless `FOREGROUND_SERVICE_IMMEDIATE` is set (fixed in
`RingingService`; keep it in M3). `docs/PLAN.md` §9 and `docs/ADR.md` (ADR-012, ADR-014 evidence) are
updated; both ADRs remain *Proposed*.

**Not in the skeleton on purpose:** `BootReceiver`, reconciliation, the store and the backup alarm
(M1b); the bundled sound is a generated placeholder WAV; the notification has no actions.

### M0 — Environment and scaffold (auto-passed, unattended, 2026-11-17)

**Agent verification**
- `./gradlew assembleDebug installDebug` → BUILD SUCCESSFUL; installed on `Chrona_API37(AVD) - 17`;
  `am start -W` Status ok; screenshot shows the app open ("Chrona" centered).
- `./gradlew check` → BUILD SUCCESSFUL; the log shows `checkNoInternetPermission: reading
  app\build\intermediates\merged_manifest\release\processReleaseMainManifest\AndroidManifest.xml`.
- Negative test: adding `android.permission.INTERNET` to the manifest made `check` fail with "The
  release manifest declares android.permission.INTERNET (ADR-002)"; reverted, `check` passes again.
- `git status`: no build output, `.gradle/`, `.kotlin/`, `local.properties` or APK is tracked.

**Pinned versions:** AGP 9.5.1, Gradle 9.6.0 (wrapper), Kotlin and Compose compiler plugin 2.4.21,
Compose BOM 2026.09.00, activity-compose 1.13.0; JDK 21 (Temurin 21.0.12); `compileSdk`/`targetSdk`
37, `minSdk` 34; Java 21 source/target.

**File tree:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
`gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew(.bat)`, `app/build.gradle.kts`,
`app/src/main/{AndroidManifest.xml, java/com/nutabe/chrona/MainActivity.kt, res/values/strings.xml}`,
`app/src/debug/res/values/strings.xml`, `.gitignore`, `README.md`, `docs/`, `.claude/skills/`.

**Known, left for later:** no launcher icon (lint `MissingApplicationIcon`); lint warns that
`allowBackup` is deprecated (kept: M0 requires `false`); the empty screen's text is near-invisible
until M2's theme; `docs/design/variants/*` (D2, interactive session) are deliberately not committed.
`./gradlew check` also runs lint, which flagged `local.properties` escaping once (fixed; the lint
analysis task does not track that file, so re-run it with `--rerun` after editing it).

**Docs:** MILESTONES.md M0 task 1 now records the 6 GB data partition. The emulator flags
(`-gpu`, `-feature`, `-no-window`) were never needed. Unverified-command note: `adb shell input swipe`
+ `input text 1234` on an already unlocked device types into the launcher; check the keyguard first.

### D1 — Structure (approved by the user, 2026-11-17)

The screens looked half-rendered in the Stitch web canvas only. The stored HTML and the exported
screenshots are complete. Extra defect for D2: 24-hour times show "AM".

**Data-model review.** Every field the screens show or edit maps to PLAN.md §2:
- **List:** time, label, `repeatDays`, mission summary, `enabled`, `snoozedUntil`.
- **Editor:** time, `repeatDays`, label, `mission`, `soundUri`, `vibrate`, `maxVolumePercent`,
  `volumeRampSeconds`, `snooze` (enabled, minutes, maxCount).
- **Ringing screen:** "Quedan 2 de 3" = `snooze.maxCount − snoozesUsed`.
- **Settings:** `settings.onboardingDone`.
- **Mission picker:** Math (difficulty, count), Shake (count, sensitivity), Steps (count).

No field is missing or unused. Two clarifications, no model change:
- **Sound name.** "Amanecer" is derived at runtime: the ringtone title from `RingtoneManager`, or
  the document's display name. If it can't be read, the screen shows "Sonido personalizado".
- **Mission progress.** "Problema 2 de 3" is in-memory state in `RingingController` and is not
  persisted. After a process death, the ring resumes but the mission restarts from zero.

**Visual defects found** (left for D2, which restyles every screen):
- **01** alarm list: warm/brown border on the snoozed card.
- **03** editor: two filled icons.
- **04** mission picker: blur/scrim and a drop shadow; the "Listo" check sits in an orange circle
  instead of `#16292B`; heavy use of orange.
- **05** settings: monospace fallback font and invented footer copy.
- **07** onboarding: English app bar ("Permissions", "Skip").
- **09** math: progress bar above the status bar.
- **10** shake: the "/ 30" detached from the counter.
- **11** steps:
  - The lock icon is misplaced, and the walking icon is filled.
  - "Cambiar a matemáticas" looks enabled although it is gated.
  - An extra "Volumen reducido" line was added.

No screen has a brown background, white text on orange, or more than one orange-filled action.

## Journal
- 2026-11-17 START D1/1 — create Stitch project and design system
- 2026-11-17 DONE D1/1 — Files: docs/design/stitch-DESIGN.md — Verified: Stitch returned the
  project and design-system ids
- 2026-11-17 START D1/2 — generate the 11 screens (4 directly; 7 delegated)
- 2026-11-17 DONE D1/2–3 — Files: docs/design/screens/01–11 *.png — Verified: 11 valid PNGs, palette
  checked per screen
- 2026-11-17 DONE D1/4 — data-model review, no model change
- 2026-11-17 ADR-017/018 accepted; ADR-023 (emulator-first, DV at the end) accepted; M0 tasks
  became [agent]
- 2026-11-17 PAUSE checkpoint D1 — waiting for review
- 2026-11-17 START M0/1 — install emulator image android-37.0 google_apis x86_64, create AVD
  `Chrona_API37`. Found: `ANDROID_HOME` is unset and `.claude/settings.local.json` does not exist;
  SDK is at `%LOCALAPPDATA%/Android/Sdk` (platform 37.0 present, only a 36 system image). Commands
  use an explicit SDK path instead.
- 2026-11-17 DONE M0/1 — Files: none in the repo (SDK: system-images/android-37.0/google_apis/x86_64,
  AVD `Chrona_API37` on pixel_8; sdkmanager also updated the emulator package as a dependency of the
  image) — Verified: `emulator -list-avds` lists Chrona_API37
- 2026-11-17 START M0/2 — boot the emulator, wait for boot_completed, set PIN 1234
- 2026-11-17 12:10 FINDING M0/2 — with `-gpu swiftshader_indirect -no-window`, SurfaceFlinger
  crash-loops every ~10 s (RegionSampling thread, `mapper.ranchu.so` GoldfishMapper::readFromHost),
  which restarts system_server; `cmd lock_settings` is intermittently unavailable. Restarted with
  `-gpu host`. If that also fails, try `-gpu angle_indirect`. MILESTONES.md/SKILL.md emulator flags
  must be updated with whichever mode works.
- 2026-11-17 12:13 FINDING M0/2 — `-gpu host` also crash-loops SurfaceFlinger (same RegionSampling
  abort). Restarted with `-gpu angle_indirect`. D2 is owned by the interactive session; the loop skips it.
- 2026-11-17 12:17 FINDING M0/2 — `-gpu angle_indirect` crash-loops too, so the GPU mode is not the
  cause. Abort message: `Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`: emulator
  37.2.13 (updated by sdkmanager) advertises ReadColorBufferDma and the API 37 image's mapper rejects
  it. Restarted with `-gpu swiftshader_indirect -feature -ReadColorBufferDma`. If it boots stable,
  put these flags in MILESTONES.md and SKILL.md.
- 2026-11-17 12:19 FINDING M0/2 — `-feature -ReadColorBufferDma` is a bad feature name (emulator log:
  `Bad feature name`), so the crash continued. Trying `-feature -GLDirectMem,-GLDMA,-GLDMA2`.
- 2026-11-17 12:23 FINDING M0/2 — `-feature -GLDirectMem,-GLDMA,-GLDMA2` still crashed (same
  assertion). Per the user, read C:\projects\alcancy: its `frontend/scripts/run-emulator.ps1` boots
  with plain `emulator -avd <name>` (visible window, no GPU or feature flags) on an API 36 image.
  Its LESSONS: GBoard can persist a FLOATING layout across restarts; reset it with `am force-stop`,
  `pm clear`, `am force-stop` on `com.google.android.inputmethod.latin`, then `ime enable` and
  `ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`, before the
  app starts (needed from M2, when text fields exist). Now trying the same plain launch with a window.
- 2026-11-17 12:26 FINDING M0/2 — alcancy's plain launch (window, no flags) crash-loops too: the
  cause is emulator 37.2.13 + the API 37 image (r08), not the flags. sdkmanager offers only emulator
  37.2.13 (no downgrade path). Last cheap try: `-gpu guest`. If it fails, PAUSE and ask the user:
  use the API 36 image (as alcancy does; conflicts with ADR-023's "Android 17"), or a different
  emulator build.
- 2026-11-17 12:32 BLOCKED M0/2 — `-gpu guest` boots (emulator reports 25 s) but `adb shell` times
  out three times; emulator killed. PAUSE: see "Waiting for you".
- 2026-11-17 12:42 RESOLVED M0/2 — the user found the root cause: the AVD was created with a 2 GB
  data partition and the API 37 image needs at least 6 GB; the SurfaceFlinger/mapper assertion was a
  symptom of that, not of the GPU mode or the emulator version. The user started the emulator by hand
  (`disk.dataPartition.size=6G` in the AVD config). Verified: boot_completed=1, no crash entries,
  SurfaceFlinger pid stable over 20 s. `locksettings set-pin 1234` → "Pin set"; verify with 1234
  succeeds and with 0000 is rejected. API 36 and other options are not needed; ADR-023 stands.
  Flags `-gpu`, `-feature` and `-no-window` were never the fix; the earlier findings are kept above
  as history.
- 2026-11-17 DONE M0/2 — Files: none in the repo — Verified: see RESOLVED line
- 2026-11-17 12:43 START M0/3 — hand-written Compose scaffold (com.nutabe.chrona), Gradle wrapper,
  version catalog
- 2026-11-17 12:55 DONE M0/3–4 — Files: settings.gradle.kts, build.gradle.kts, gradle.properties,
  gradle/libs.versions.toml, gradle/wrapper/*, gradlew(.bat), app/build.gradle.kts,
  app/src/main/{AndroidManifest.xml,java/com/nutabe/chrona/MainActivity.kt,res/values/strings.xml},
  app/src/debug/res/values/strings.xml, local.properties (untracked) — Verified: `./gradlew
  assembleDebug` → BUILD SUCCESSFUL. Pinned: AGP 9.5.1 (needs Gradle ≥ 9.6.0, so the 8.14.3 dist was
  only used to generate the wrapper), Gradle 9.6.0, Kotlin/Compose plugin 2.4.21, Compose BOM
  2026.09.00, activity-compose 1.13.0. AGP 9 has built-in Kotlin, so `kotlin-android` is not applied.
  Debug: `applicationIdSuffix=".debug"`, label "Chrona (debug)" (src/debug strings). Manifest:
  `allowBackup="false"`, no backup/extraction rules. No launcher icon yet (default).
- 2026-11-17 12:47 VERIFIED M0 (part) — `./gradlew installDebug` → installed on `Chrona_API37(AVD) - 17`;
  `am start -W` Status ok (1.4 s); `mCurrentFocus` is `com.nutabe.chrona.debug/.MainActivity`;
  screenshot shows the app with "Chrona" centered. Cosmetic, left for M2's theme: the text is nearly
  illegible (dark framework window theme + light default MaterialTheme). Lesson: the emulator was
  already unlocked, so `input swipe` + `input text 1234` typed the PIN into the launcher search; check
  `dumpsys window | grep mDreamingLockscreen|isKeyguardShowing` before unlocking.
- 2026-11-17 12:50 DONE M0/5 — Files: .gitignore (template plus the listed entries; also
  `.claude/scheduled_tasks.lock`, a runtime lock that would otherwise be committed), README.md —
  Verified: verified together with task 6 (`git status`)
- 2026-11-17 12:52 DONE M0/5–6 — `git init -b main` — Verified: `git status --short --ignored` lists
  `.gradle/`, `app/build/`, `local.properties`, `.claude/scheduled_tasks.lock` as ignored; no build
  output, `.kotlin/`, `local.properties` or APK among untracked. COMMIT GUARD: `docs/design/variants/*`
  and `docs/design/screens/*` belong to D1/D2 (interactive session): stage by path, and keep D2's
  untracked files out of the M0 commit unless the user says otherwise.
- 2026-11-17 12:53 START M0/7 — `checkNoInternetPermission` Gradle task (release merged manifest,
  wired into `check`)
- 2026-11-17 12:57 DONE M0/7 — Files: app/build.gradle.kts — Verified: `./gradlew check` → success with
  the "reading …merged_manifest…" line; with INTERNET added → `checkNoInternetPermission FAILED`;
  reverted → success
- 2026-11-17 12:58 CHECKPOINT M0 — auto-passed (unattended); local commit "scaffold Chrona project"
- 2026-11-17 13:02 COMMIT M0 — `1455276` "scaffold Chrona project" (33 files, local only). Git identity:
  the repo had none; per the user's answer, set in Chrona's LOCAL config only (same as alcancy's
  local config). `docs/design/variants/` left untracked (D2). STATUS.md edits after the commit (hash
  line, this entry) ride with the next commit.
- 2026-11-17 13:20 START M1a/1–4 — manifest permissions + directBootAware, `AlarmReceiver` +
  `WakeLockHolder`, `RingingService` (systemExempted FGS, channel, full-screen intent, MediaPlayer
  USAGE_ALARM loop, vibration), `RingingActivity`; bundled sound is a generated placeholder WAV
  (`res/raw/alarm_default.wav`)
- 2026-11-17 13:27 DONE M1a/1–4 — Files: app/src/main/AndroidManifest.xml (10 permissions from PLAN §4;
  `directBootAware` on `RingingActivity`, `AlarmReceiver`, `RingingService`), engine/{WakeLockHolder,
  AlarmScheduler,AlarmReceiver,RingingService}.kt, ui/ringing/RingingActivity.kt,
  res/raw/alarm_default.wav (generated placeholder), res/values/strings.xml — Verified: `assembleDebug`
  OK; installed; a 5 s `setAlarmClock` → logcat `AlarmReceiver fired` → `onStartCommand START_RING` →
  `foreground entered` (systemExempted) → `MediaPlayer started`. `BootReceiver` is deliberately absent
  (M1b). The ring marker is a file in device-protected storage (`files/active_ring`).
- 2026-11-17 13:27 DONE M1a/5 (part) — Files: app/src/debug/AndroidManifest.xml,
  app/src/debug/java/.../debug/DebugActivity.kt (second launcher "Chrona debug"; extra
  `ring_in_seconds` schedules a ring from `adb`) — Verified: screenshot shows status rows (exact
  alarms OK, notifications OK, full-screen intent OK, activity recognition MISSING, battery MISSING)
  and the two ring buttons. Cosmetic: the title overlaps the status bar (debug only).
  `USE_EXACT_ALARM`: `canScheduleExactAlarms()` is true on the API 37 emulator without any grant,
  consistent with PLAN §4 (to be confirmed in DV).
- 2026-11-17 13:28 NOTE — Git Bash rewrites `/data/...` arguments to `C:/Program Files/Git/data/...`;
  prefix adb commands with `MSYS_NO_PATHCONV=1`. `run-as` can read `/data/user_de/0/<pkg>/files`.
  A ring started from the debug screen cannot be stopped from `adb` (components are not exported):
  use `am force-stop` and delete `files/active_ring`, or tap Stop on `RingingActivity`.
- 2026-11-17 13:31 VERIFIED M1a — rings with the screen locked: ring at 13:22:05 with the screen off and
  the keyguard up (PIN set); SystemUI woke the device with `full_screen_intent` and `RingingActivity`
  took focus with `isKeyguardShowing=true`; screenshot shows "Alarm" + Stop over the keyguard. Tapping
  Stop → `STOP_RING` → service `onDestroy`, marker file deleted, no service left.
  FINDING: the first run showed the UI only 10 s after the sound (wake-up at 13:22:15). Cause: Android
  defers foreground-service notifications by ~10 s, and the full-screen intent rides on that
  notification. Fix: `Notification.Builder.setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)`
  in `RingingService`. Re-run: sound 13:23:28.620, wake-up 13:23:28.802 (0.18 s). To feed into M1b/M3.
  Caveat: "app swiped away" was approximated with `am kill` on the cached process, not a Recents swipe.
- 2026-11-17 13:31 NOTE — a `dumpsys alarm | grep` run right after `am start` shows no pending alarm
  because the freshly started process has not run `onCreate` yet; wait ~2 s. `DebugActivity` now also
  handles `onNewIntent` (the extra was ignored when the task was brought to the front).
- 2026-11-17 13:33 VERIFIED M1a — heads-up while unlocked and in another app: with Settings in the
  foreground, the ring at 13:24:41 showed a heads-up ("Alarm · now / Alarm is ringing") over Settings
  (screenshot); `input tap` on it made `topResumedActivity` = `RingingActivity`; Stop ended the ring.
- 2026-11-17 13:33 PARTIAL M1a — forced Doze NOT reached: on this API 37 emulator image
  `dumpsys|cmd deviceidle force-idle [deep]` answers "Unable to go deep idle; stopped at INACTIVE", and
  `force-inactive` plus six `step deep` calls leave deep and light at INACTIVE
  (`mUseMotionSensor=true`, `mMotionSensor=null`, `mForceIdle=false`). What was verified instead: with
  the battery unplugged (`dumpsys battery unplug`), the screen off and the device forced inactive, the
  60 s alarm fired at 13:26:56, `RingingService` started in the foreground and `RingingActivity` took
  focus; state restored (`cmd deviceidle unforce`, `battery reset`, deep=ACTIVE). Deep-Doze behavior
  moves to DV (S25). Not proven: that `setAlarmClock` + the `systemExempted` FGS start survive real deep
  idle.
- 2026-11-17 13:38 DONE M1a/6 + 5 — Files: app/src/debug/java/.../debug/StepSpikeService.kt (silent
  `systemExempted` FGS with a partial wake lock; registers `TYPE_STEP_DETECTOR` and
  `TYPE_STEP_COUNTER` and logs `STEP <type> value sensorTs elapsed`), app/src/debug/AndroidManifest.xml,
  DebugActivity (start/stop buttons) — Verified: started from the debug screen: log `start:
  ACTIVITY_RECOGNITION granted=false`, `sensor type 18 unavailable`, `sensor type 19 unavailable`,
  stop → service gone.
  SPIKE RESULT (emulator): it cannot answer. `dumpsys sensorservice` has no step sensor, `pm list
  features` has no `sensor.stepcounter`/`stepdetector`, and `adb emu sensor status` lists no step
  channel. So ADR-014 stays *Proposed* until DV; steps are counted in the service as proposed; the
  debug-only fake step source for tests belongs to M3. To run in DV: grant Physical activity on the
  debug screen, tap "Step spike: start", lock the screen, walk, read `logcat -s ChronaStepSpike`.
- 2026-11-17 13:38 START M1a/7 — restart spike: kill the process mid-ring, watch `START_STICKY`
- 2026-11-17 13:50 DONE M1a/7 — restart spike (2 kills, foreground and background): see ADR-012
  evidence. `START_STICKY` alone resumed the ring in about 1.2 s on AOSP.
- 2026-11-17 13:52 COMMIT M1a — `2b1b521` "add ringing path skeleton" (15 files, local only).
  STATUS.md edits after the commit (this line, the hash cell) ride with the next commit.
- 2026-11-17 13:52 PAUSE checkpoint M1a — waiting for the ADR-012 and ADR-014 decisions and approval
- 2026-11-17 14:05 APPLIED user answers to the M1a checkpoint — (1) ADR-012: `START_STICKY` plus the
  backup alarm → ADR-012 *Accepted* (ADR.md updated, index row too); (2) ADR-014: stays *Proposed*
  until DV (note added); (3) checkpoint M1a approved. M1b's gates (D1 approved, M1a decisions
  recorded) are met.
  FLAG: ADR-023's "Consequences" still says ADR-012 and ADR-014 stay *Proposed* until DV; it is an
  *Accepted* ADR, so it was not edited. ADR.md and STATUS.md also carry uncommitted D2 edits
  (ADR-024, DESIGN.md, D2 notes) from the interactive session; they are not mine and are not staged
  with my commits unless the user says so.
- 2026-11-17 14:35 START M1b/1,3,4 — block 1: serialization plugin + json + JUnit 4, model, `NextTrigger`,
  `Reconciler`, each with tests. Plan: block 2 = AlarmStore, Scheduler, backup alarm, RingingController;
  block 3 = service on the store, BootReceiver/App, debug screen, `dumpsys alarm` check.
- 2026-11-17 14:45 POLICY — the user changed the loop's contract: minimum intervention. Decisions take
  the recommended option, or go to the advisor when none is recommended; checkpoints approve
  themselves (milestone and ADRs) and the loop continues; only physical DV steps and blockers are
  left for the user. Defined in `.claude/skills/milestone-loop/SKILL.md` ("Decision policy"). The
  uncommitted M1b dependency edits (libs.versions.toml) were rejected by the user just before this
  change and not applied; M1b block 1 restarts from its START line.
- 2026-11-17 14:50 DECIDED M1b dependencies — chose to add `junit:junit:4.13.2` as `testImplementation`
  now (advisor: PLAN §6 names JUnit and M1b verification needs `./gradlew test`); declare
  `kotlinx-coroutines-core` explicitly in block 2, pinned to the version that already resolves
  transitively; `coroutines-test`, Robolectric and mockk NOT approved (use `runBlocking`).
  `kotlinx-serialization-json` 1.13.0 + plugin 2.4.21 are named by M1b.
- 2026-11-17 14:50 DECIDED M1b design (advisor) — (1) Reconciler precedence per alarm: persisted
  `ActiveRing` (not live) → ring now; `snoozedUntil` due → ring now, future → `Schedule(SNOOZE)` plus
  `Schedule(NEXT)` only if repeating; only then `nextTriggerAt`; (2) "ring now" is
  `Schedule(RING_NOW, now+3 s)`; `TriggerKind` has all four kinds from the start; (3) `NextTrigger`
  scans days 0..7 inclusive; (4) serialization: epoch-millis `Instant`, ISO-number `DayOfWeek`, stable
  `@SerialName` on every `Mission` subtype; (5) a pure `StoreCodec` separate from the Android
  `AlarmStore`, so store tests run on the JVM; (6) a single `AUTO_SILENCE` constant shared with the
  service, boundary: elapsed `<` window rings, `>=` is missed. Note for M1c: PLAN says to reschedule
  `nextTriggerAt` unchanged after `TIMEZONE_CHANGED`, which leaves repeating alarms on the old local
  time; follow PLAN, document with a test, revisit in the M1c matrix.
- 2026-11-17 15:05 DONE M1b/1,3,4 (block 1) — Files: gradle/libs.versions.toml, build.gradle.kts,
  app/build.gradle.kts (serialization plugin, `kotlinx-serialization-json`, JUnit), engine/{Serializers,
  Model,StoreCodec,NextTrigger,Reconciler}.kt, app/src/test/.../engine/{NextTriggerTest,ReconcilerTest,
  StoreCodecTest}.kt — Verified: `./gradlew test` → 38 tests, 0 failures (12 + 17 + 9, from the XML
  reports). The codec covers the store tests of the M1b verification (round trip, corruption, unknown
  keys); the Android `AlarmStore` (AtomicFile) comes in block 2. Tests use JUnit only (no
  `kotlin-test`).
- 2026-11-17 15:10 START M1b/2,5,6,7 (block 2) — `AlarmStore` (AtomicFile, device-protected, Mutex,
  StateFlow, corrupt-file rename + notification), `Scheduler` with (alarm id, kind) request codes
  replacing `AlarmScheduler`, the BACKUP re-arm loop in `RingingService`, `RingingController`
- 2026-11-17 15:25 DONE M1b/2,5,6,7 (block 2, on-device checks deferred to block 3) — Files:
  engine/{AlarmStore,Scheduler,RingingController}.kt (new), engine/{AlarmReceiver,RingingService}.kt,
  debug/DebugActivity.kt, engine/AlarmScheduler.kt (removed, superseded by `Scheduler`),
  gradle/libs.versions.toml + app/build.gradle.kts (`kotlinx-coroutines-core` 1.9.0, the version already
  resolved transitively), app/src/test/.../SchedulerTest.kt — Verified: `./gradlew test assembleDebug`
  → BUILD SUCCESSFUL; 41 tests, 0 failures (12 + 17 + 3 + 9). Notes: `RingingService` re-arms a `BACKUP`
  alarm 60 s ahead every 20 s while ringing and cancels it only on an explicit stop (a system-destroyed
  service keeps it); `MAX_RING_MS` now uses `RingingPolicy.AUTO_SILENCE`; `RingingController.snooze()`
  is deferred to block 3 with the service's snooze handling. The receiver does not yet ignore a
  `BACKUP` fire when no ring is active; that check needs the store and lands in block 3.
- 2026-11-17 15:40 START M1b/8,9,10 (block 3) — pure `RingTransitions` (fired / completed / snoozed) and
  `FireGuard` (ignore stale BACKUP / RING_NOW / SNOOZE fires) with tests; then `RingingService` on the
  store (ActiveRing, sound fallback chain, STREAM_ALARM level + restore, volume ramp, repost on swipe,
  snooze), `ReconcileExecutor`, `BootReceiver`, `App`, Snooze button, debug list + alarm creation;
  then the `dumpsys alarm` check. Design note: reconciliation on app start lives in `MainActivity`
  (user-initiated start), not `App.onCreate`, because `App` also runs when an alarm fires and would
  queue a duplicate RING_NOW.
- 2026-11-17 15:10 DONE M1b/2,5,6,7,8,9,10 (block 3) — Files: engine/{RingTransitions(+FireGuard),
  EngineScope,ReconcileExecutor,BootReceiver}.kt (new), App.kt (new), engine/{RingingService (rewritten
  on the store),AlarmReceiver,RingingController}.kt, ui/ringing/RingingActivity.kt (Snooze),
  MainActivity.kt (reconcile on start), AndroidManifest.xml (`App`, `BootReceiver` with the five
  actions, direct-boot aware), strings.xml, debug/DebugActivity.kt (stored-alarm list, "Add stored alarm"
  with a valid or an invalid sound URI, extras `add_alarm_seconds` / `invalid_sound` / `repeat_daily`),
  app/src/test/.../RingTransitionsTest.kt — Verified: see the checkpoint.
- 2026-11-17 15:12 DECIDED M1b reconcile on start — chose `MainActivity.onCreate` rather than
  `App.onCreate` (own design call, no option was recommended or contested): `App` also runs when an
  alarm fires and would queue a duplicate RING_NOW. `FireGuard` additionally drops stale BACKUP /
  RING_NOW / SNOOZE fires in the receiver. Known limits recorded: the original `STREAM_ALARM` level is
  held in memory, so after a process death mid-ring the restored value is the already-raised one;
  one-shot completion and snooze persist asynchronously on `EngineScope`.
- 2026-11-17 DONE checkpoint D1 — approved by the user (screens fine; Stitch web canvas renders them partially). M1b's D1 gate is met.
- 2026-11-17 START D2/1 — variants of alarm list and ringing screen, both vibe archetypes (interactive session)
- 2026-11-17 FINDING D2/1 — Stitch timed out on 6 of 9 generation calls; saved variants/list-A1, list-A2 (Editorial) and ringing-B1 (Ethereal, rendered as desktop). list-B and ringing-A may exist only in the Stitch web UI. Waiting for the user to check the web UI and pick a direction.
- 2026-11-17 DONE D2/1+3 — user chose the A+B hybrid; fonts Newsreader + Plus Jakarta Sans (tnum), Phosphor icons (ADR-024; DESIGN.md §4 updated). Variants: docs/design/variants/ (5 of 8; Stitch timeouts).
- 2026-11-17 START D2/2 — apply the hybrid to the 11 screens via edit_screens → docs/design/final/ (interactive session)
