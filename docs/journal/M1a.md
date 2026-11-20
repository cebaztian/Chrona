# Chrona — M1a journal

Archived from `docs/STATUS.md` on 2026-11-19, when the project moved to the `run-milestones` skill.
Entries keep their original order and wording.

## Checkpoint

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

## Waiting items closed

- M1a decisions answered 2026-11-17: ADR-012 accepted as `START_STICKY` plus the 60 s backup alarm; ADR-014 stays *Proposed*; checkpoint approved.

## Journal

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
