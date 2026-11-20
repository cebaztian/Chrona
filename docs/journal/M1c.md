# Chrona — M1c journal

Archived from `docs/STATUS.md` on 2026-11-19, when the project moved to the `run-milestones` skill.
Entries keep their original order and wording.

## Checkpoint

### M1c — Reliability matrix (approved unattended, 2026-11-17)

Emulator `Chrona_API37`, Android 17. 16 rows pass (row 14 at the stream minimum), 1 is partial and explained.

| # | Scenario | Result |
|---|---|---|
| 1 | App swiped from Recents | Pass: process gone, alarm still pending, rang over the keyguard |
| 2 | Doze | **Partial**: `deviceidle` cannot reach deep idle on this image; the screen-off, unplugged ring was verified (M1a). Deep Doze → DV |
| 3 | Reboot, not unlocked | Pass: `LOCKED_BOOT_COMPLETED` → reschedule → rang; no crash in `App.onCreate` |
| 4 | Lock screen | Pass: full screen over the keyguard; after Back `RingingActivity` stays; after Home only the lock screen with the "Alarm is ringing" notification shows, the alarm list is unreachable without unlocking |
| 5 | Phone in use | Pass (M1a, re-seen in M1b): heads-up, tap opens `RingingActivity` |
| 6 | Invalid sound | Pass (M1b): falls back to the bundled sound |
| 7 | Process killed mid-ring | Pass (M1a and M1b through the store): resumed about 1 s later |
| 8 | Reboot mid-ring | Pass: `RING_NOW` after boot, rang again |
| 9 | Reboot mid-snooze | Pass: snooze rescheduled to the same instant, fired within 33 ms |
| 10 | Off over 15 min mid-ring | Pass: rang again, no "missed" |
| 11 | One-shot never fired, off over 15 min | Pass: "Missed alarm" notification, disabled, not moved to tomorrow |
| 12 | Time / time zone change | Pass **after a fix**: repeating alarms are recomputed on `TIME_SET` and `TIMEZONE_CHANGED` |
| 13 | Notification swiped while ringing | Pass: re-posted |
| 14 | Alarm volume at 0 | Pass **at the minimum**: STREAM_ALARM minimum is 1; raised to the alarm level, restored after stop |
| 15 | DND on (alarms allowed) | Pass |
| 16 | App update with an alarm pending | Pass: `MY_PACKAGE_REPLACED` → rescheduled → rang |
| 17 | Re-trigger of a live ring | Pass: relaunch, time-zone change and a `BACKUP` fire leave exactly one ring |

**Caveats:** rows 5–7 are carried over from M1a/M1b (same code path, plus the store since M1b). The
One UI battery settings and the real-phone items are in DV.

**Decisions:** none left open; ADR-014 stays *Proposed* until DV. The reconciler fix is logged as `DECIDED M1c/12`.

## Journal

- 2026-11-17 15:30 START M1c — reliability matrix (17 rows). Already evidenced in M1a/M1b: row 4 (lock
  screen), 5 (heads-up tap), 6 (invalid sound), 7 (kill mid-ring, now through the store), and the
  locked-screen part of 1. To run now: 3, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17 and a re-run of 1/2.
  Method: helper functions in the scratchpad (`m1c.sh`); each row's result is logged as a `MATRIX` line.
- 2026-11-17 15:25 FINDING M1c/3 — `adb reboot` made the user-started emulator process EXIT (no qemu
  process afterwards, adb saw no device 23 min later) instead of rebooting in place, so the first
  attempt of row 3 is void (the 14:57:57 alarm passed with no device). Not an app failure. From now on
  the loop starts the emulator itself with `emulator -avd Chrona_API37 -no-snapshot` (cold boot,
  background); if a reboot test makes it exit, relaunch it the same way and continue from the boot.
- 2026-11-17 15:31 MATRIX 3 PASS — reboot, never unlocked: stored one-shot for 15:29:11; after the reboot
  `BootReceiver: LOCKED_BOOT_COMPLETED` → `reconcile: Schedule(NEXT 15:29:11)` (also visible in `dumpsys
  alarm`), then `AlarmReceiver … valid=true` → foreground → `MediaPlayer started` at 15:29:11.466;
  `RingingActivity` had focus with `isKeyguardShowing=true`; crash buffer empty. Test-setup lessons
  (not app bugs): (a) a package that was force-stopped shortly before the reboot comes back
  `stopped=true` and gets no boot broadcast, so wait ~30 s after launching it (and check `dumpsys
  package … stopped=false`) before rebooting; (b) `am start` with extras delivered to a running
  instance does not reach `onNewIntent` reliably, use `am start -S` (onCreate); (c) `run-as` cannot
  read the store while the device is locked.
- 2026-11-17 15:31 MATRIX 8 PASS — reboot mid-ring: after boot `reconcile: Schedule(RING_NOW +3 s)` →
  `AlarmReceiver kind=RING_NOW valid=true` → rang again at 15:30:37, `RingingActivity` over the keyguard.
- 2026-11-17 15:32 MATRIX 9 (in progress) — snoozed at 15:30:46 until 15:35:46.911, rebooted; after boot
  `reconcile: Schedule(SNOOZE 15:35:46.911)` with the same instant. Pending: it must ring at 15:35:47.
- 2026-11-17 15:36 MATRIX 9 PASS — the snooze set before the reboot fired at 15:35:46.944 for a target of
  15:35:46.911 (33 ms), `AlarmReceiver kind=SNOOZE valid=true`, `RingingActivity` over the keyguard.
- 2026-11-17 15:40 MATRIX 17 PASS — (a) relaunching the app while ringing: `reconcile: 0 actions`, the
  service stays alive, no second `MediaPlayer started`; (b) `cmd alarm set-timezone Asia/Tokyo` while
  ringing: `BootReceiver: TIMEZONE_CHANGED` → `reconcile: 0 actions`; (c) a `BACKUP` fire while ringing
  (`adb root`, `am broadcast -n …AlarmReceiver --es kind BACKUP`): `valid=true` → `already ringing,
  ignoring start`. Exactly one ring each time. Harness notes: `MainActivity` is not direct-boot aware,
  so `am start` of it fails with "does not exist" until the user is unlocked (correct design);
  `unlock()` must not send ENTER while `RingingActivity` is on screen, it presses Stop.
- 2026-11-17 15:41 DECIDED M1c/12 — chose to FIX the reconciler so `TIME_SET` and `TIMEZONE_CHANGED`
  recompute the next trigger of repeating alarms (own call; the matrix row expects a recomputed
  trigger in `dumpsys alarm`, which supersedes the earlier "keep it unchanged" note; no option was
  recommended against it). One-shots keep their absolute instant. Boot and package-replaced still
  reschedule unchanged.
- 2026-11-17 15:42 FIX M1c/12 — Files: engine/Reconciler.kt (`recomputeRepeating` flag),
  engine/ReconcileExecutor.kt, engine/BootReceiver.kt (`TIME_SET` and `TIMEZONE_CHANGED` recompute
  repeating alarms; also `TimeZone.setDefault(null)`), ReconcilerTest (+3: now 20) — Verified:
  `./gradlew test` → 60 tests, 0 failures.
- 2026-11-17 15:42 MATRIX 12 PASS — a daily alarm at 15:55 (Bogota): `cmd alarm set-timezone Asia/Tokyo`
  → `reconcile: Schedule(NEXT 2026-11-18T06:55:00Z)`, stored `nextTriggerAt` and `dumpsys alarm` both
  moved to 2026-11-18 15:55 local (+10 h 14 min); back to Bogota → today 15:55:00. Clock: `adb root` +
  `date -u` +1 h → `TIME_SET` → recomputed to 2026-11-18T20:55Z; restored → today 15:55. Time zone and
  clock restored (device and host agree).
- 2026-11-17 15:50 MATRIX 1 PASS — real swipe from Recents (`KEYCODE_APP_SWITCH`, swipe up on the card):
  the process disappeared (`pidof` empty), the alarm stayed pending in `dumpsys alarm`, then with the
  screen off it rang at 15:50:25 and `RingingActivity` had focus with `isKeyguardShowing=true`.
- 2026-11-17 15:46 MATRIX 13 PASS — with the notification showing in the shade, a horizontal swipe on it
  → `REPOST_NOTIFICATION` → `foreground entered`; the notification is present again
  (`dumpsys notification`) and the service stays alive.
- 2026-11-17 15:42 MATRIX 14 PASS (at the stream minimum) — STREAM_ALARM cannot be 0 on API 37 (range
  [1..7]); set to 1 → `alarm stream 1 -> 7 of 7` while ringing → 1 again after Stop. The row's literal
  "volume 0" is not reachable on this image.
- 2026-11-17 15:44 MATRIX 15 PASS — `cmd notification set_dnd priority` (zen_mode 1): `dumpsys audio`
  shows the MediaPlayer `state:started`, `usage=USAGE_ALARM`; `RingingActivity` took focus; DND restored.
- 2026-11-17 15:47 MATRIX 16 PASS — `adb install -r` with an alarm pending (due 15:47:50.561):
  `MY_PACKAGE_REPLACED` → `reconcile: Schedule(NEXT 15:47:50.561)`; it rang at 15:47:50.565.
- 2026-11-17 15:55 MATRIX 2 PARTIAL — same limitation as M1a: `deviceidle` cannot reach deep idle on this
  image; the screen-off, unplugged case was verified then and is covered again by the screen-off rows.
  Deep Doze → DV.
- 2026-11-17 15:55 STEP M1c/10-11 — long power-off started, see "Now". Harness lessons: a text-based tap
  (`uiautomator` bounds) replaces fixed coordinates; the debug screen got an "Add stored alarm +4 min"
  button for this scenario.
- 2026-11-17 16:15 MATRIX 10 + 11 PASS — power-off 15:54:38 → cold boot 16:14:10 (19.5 min). Alarm 1
  was ringing at the kill (`activeRing` persisted); alarm 2, a one-shot due 15:56:18, had never fired
  (18 min overdue). After the boot, with the device locked: `reconcile: 3 actions
  [Schedule(1, RING_NOW +3 s), PostMissed(2), Disable(2)]`; alarm 1 rang again at 16:14:33.67
  (`RingingActivity` had focus); a "Missed alarm" notification (id 102) was posted for alarm 2; after
  Stop and unlock the store had `activeRing: null`, alarm 1 completed (disabled, one-shot) and alarm 2
  `enabled=false, nextTriggerAt=null` (not moved to tomorrow).
- 2026-11-17 16:20 DONE M1c — Files: engine/{Reconciler,ReconcileExecutor,BootReceiver}.kt (fix for row
  12), debug/DebugActivity.kt (+4 min button, a `ChronaDebug` log), app/src/test/.../ReconcilerTest.kt,
  docs/PLAN.md §9 (observed in M1c; items still for DV/M2), docs/MILESTONES.md untouched by me —
  Verified: `./gradlew check` → BUILD SUCCESSFUL (lint + `checkNoInternetPermission`); `./gradlew test`
  → 60 tests, 0 failures. Emulator settings restored (time zone, DND, clock, screen timeout); the alarm
  stream volume ended at 7 instead of 6.
- 2026-11-17 16:25 COMMIT M1c — `ed0ff0b` "verify engine reliability" (7 paths, local only). Left
  unstaged (not mine or the loop's own definition): `docs/ADR.md` (ADR-024/025 and status lines),
  `docs/DESIGN.md`, `docs/MILESTONES.md`, `docs/design/variants/`, `.claude/skills/milestone-loop/SKILL.md`.
  This line, the hash cell and the Now/Waiting edits ride with the next commit. APPROVED M1c (unattended).
- 2026-11-17 16:25 STOP — M2 is gated on D2 (owned by the interactive session; the loop never picks
  the visual direction). No agent task is left that does not depend on it.
