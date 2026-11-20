# Chrona - Device validation checklist (DV)

Written by the milestone loop (now the `run-milestones` skill) for MILESTONES.md "DV", task 1. It covers both options, so it does not
depend on the choice you make at the start of DV:

- **(a) USB or wireless debugging:** the agent runs the `adb` steps and reads `logcat`. Connect the S25 Ultra
  (`adb devices` must list it), install the **debug** build for the items that need `run-as`, the process kill
  and the step source, and the **release** build for the final run.
- **(b) Hand-installed APK:** you install `app/build/outputs/apk/release/app-release.apk`
  (`./gradlew assembleRelease`) and do each item by hand. Without `adb` the only evidence is what you see, so
  each item says what to look for. Items marked **(a only)** need `adb`.

The release build is `com.nutabe.chrona`; the debug build is `com.nutabe.chrona.debug`. Both can be installed.
Back up `.signing/chrona-release.jks` and `keystore.properties` before you rely on the release build.

Never mark an item passed from the emulator: this list exists for what the emulator cannot show.

## 0. Setup

1. Install the APK. Open Chrona and go through onboarding, granting notifications, full-screen and exact
   alarms. Check **Settings > System status**: nothing should be "Blocking".
2. Note the One UI version and the exact names of the battery pages the health screen links to
   (Device care > Battery > Background usage limits, "Sleeping apps", "Never sleeping apps").
3. Create a test alarm: label `dv`, mission *Math*, 2 problems. Keep Alarmy as the real alarm all week.

## 1. Step spike (ADR-014)

The question: do steps keep counting inside the service with the screen off and the phone in a pocket?

- (a) Debug build. Create an alarm with the *Steps* mission, 40 steps. Let it ring, tap "Solve to stop" and
  "Start mission", lock the screen, put the phone in a trouser pocket and walk for 2 minutes.
  `adb logcat -s ChronaRing:I` must show `step sensor registered` and the count must rise (unlock and read
  the count, or watch `mission started: STEPS` then `stop requested` when it completes).
- (b) Same without logcat: after walking, unlock; the screen must show a count that moved, or the alarm must
  have ended.
- **Pass:** the count advances with the screen off. **Fail:** it stops while the screen is off. Then apply the
  PLAN section 5.3 fallback (count only while `RingingActivity` is visible, with the screen kept on) by starting
  and stopping `MissionSensors` from the activity instead of the service, and re-run this item.
- Also check: shaking the phone in your hand must **not** count as steps (cadence filter), and vibration is off
  during the steps mission.

## 2. Restart behaviour on One UI (ADR-012)

- (a only) While an alarm rings: `adb shell am kill com.nutabe.chrona.debug` or swipe Chrona away from Recents.
  The ring must resume within about a minute (the service restarts, or the 60 s `BACKUP` alarm re-rings it).
  Log: `onStartCommand action=null` or `AlarmReceiver fired ... kind=BACKUP`.
- (b) Swipe Chrona away from Recents while an alarm rings; it must keep ringing or ring again within a minute.
- Repeat with the phone idle for 10 minutes before the alarm, and again after "Sleeping apps" has had time
  to act (leave the alarm for the next morning).
- **Pass:** it always rings again and the mission is still required. **Fail:** silence. Then ADR-012 is
  superseded by a stronger mechanism and the user is told.

## 3. Samsung battery settings

- Confirm the names on the health screen match your One UI ("Background usage limits", "Sleeping apps",
  "Never sleeping apps"). Fix the strings if they differ.
- Put Chrona in "Sleeping apps" on purpose and check whether the health screen warns (it should detect it; if it
  cannot, note it, as it is a known gap).
- Set the battery to "Unrestricted" for Chrona and confirm the warning clears.

## 4. Full-screen intent and heads-up

- Locked screen, screen off: the alarm must wake the phone and show the ringing screen over the lock screen.
- Phone in use (another app in front): a heads-up notification with "Solve to stop" and "Snooze", no "Dismiss";
  tapping it opens the ringing screen.
- Fresh sideload: check `canUseFullScreenIntent()` without granting anything by `adb`. In the debug build
  the debug screen shows "Full-screen intent". If it is denied, the health screen must send you to the page.
- Notification swiped away while ringing: it must come back.

## 5. Missions in hand and in a pocket

- *Math:* the numpad only (no keyboard, no calculator), a wrong answer gives a new problem and a short haptic.
- *Shake:* at each sensitivity, slow movements must not count and a real shake must. Check what feels right.
- *Steps:* see section 1. Walk in the house, carry the phone in a pocket and in your hand.
- Sensor watchdog: with the phone lying still, "Switch to math" appears after 30 s (shake) or 2 min (steps).

## 6. Reboot without unlocking

- Set an alarm 3 minutes ahead, restart the phone and **do not unlock it**. It must ring on time over the lock
  screen and the mission must work without unlocking. (a only) `adb logcat` shows `LOCKED_BOOT_COMPLETED`.

## 7. A real phone call during a ring

- Call the phone while an alarm rings, answer, then hang up. The alarm must go quiet (a soft repeating beep) while
  you are on the call and resume after. Repeat with the alarm firing while you are already on a call.
- Note whether the call audio and the beep are acceptable; this is the part the emulator cannot judge.

## 8. Other platform facts

- Deep Doze: leave the phone unplugged and still for 2 hours with an alarm set; it must ring on time.
- Do Not Disturb with "Alarms" allowed: it must ring. DND with alarms off: note what happens.
- Alarm volume at the minimum: the alarm raises it to its level and restores it after.
- The launcher icon (and the themed icon with a monochrome layer): is it acceptable? This is your call.
- Haptics (DESIGN section 7): the time picker, Save, weekday chips, switches, numpad and the long press all vibrate
  at a level you can feel on One UI; say which ones are too faint or too strong.
- Alarm list long press: Duplicate, Select and Delete work; select three alarms, delete them and Undo restores all.

## Reviews carried from checkpoints

Reviews that the self-approved M2 and M3 checkpoints could not do. Moved here from STATUS on 2026-11-19.

- [ ] M2: compare the list, editor, mission sheet, settings, health and onboarding with
  `docs/design/final/01`–`07`. On the S25, also open the steps option, the time-picker, ramp and snooze dialogs
  and the ringtone and file pickers (never hand-exercised on the emulator).
- [ ] M3: compare the recording `docs/evidence/m3-ring-and-math.mp4` (ring screen, "Start mission", a wrong
  answer, two problems) with `docs/design/final/08`–`11`. The shake feel and the haptic on a wrong answer are
  in item 5; real walking with the screen off passed in item 1.

## Results

| # | Item | Result (pass / fail / note) | Date |
|---|---|---|---|
| 1 | Step spike | Pass: the steps mission counts with the screen off (user report) | 2026-11-18 |
| 2 | Restart on One UI | Pass (user confirmed) | 2026-11-18 |
| 3 | Samsung battery settings | Pass (user confirmed) | 2026-11-18 |
| 4 | Full-screen intent and heads-up | Pass (user confirmed); an overnight alarm rang as set | 2026-11-18 |
| 5 | Missions in hand and pocket | Partial: shake and steps tried on 2026-11-17; the rest pending | |
| 6 | Reboot without unlocking | Pass (user confirmed) | 2026-11-18 |
| 7 | Real phone call | In progress | |
| 8 | Doze, DND, volume, icon | Partial: haptics, list long press (Duplicate, Select, Delete, Undo) and the 0.1.1 update over 0.1.0 pass; DND, volume at minimum, icon and the 2-hour Doze test pending | 2026-11-18 |

After the run the agent fixes what failed, re-runs the affected emulator checks, and updates ADR-014 and
ADR-012 to *Accepted* or supersedes them. Checkpoint DV: commit "validate on device".

## Dogfood week incident log

Use Chrona as the daily alarm for a week and keep Alarmy as a backup. One line per incident.

| Date | What happened | Alarm | Alarmy rang? | Notes |
|---|---|---|---|---|
| | | | | |
