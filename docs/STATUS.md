# Chrona — Status

Maintained by the `run-milestones` skill (global), with the project specifics in [LOOP.md](LOOP.md); the
loop resumes from this file. "Waiting for you" holds only physical [user] steps, reserved decisions and
blockers. Finished milestones are archived in [`docs/journal/`](journal/) (checkpoint results and
journal); lessons are in [LESSONS-LEARNED.md](LESSONS-LEARNED.md).

## Now
- Milestone: DV — Device validation (S25 Ultra, release 0.1.1 installed by hand).
- Items 1, 2, 3, 4 and 6 pass; 5, 7 and 8 are partly or still open (`docs/DV-CHECKLIST.md`, Results).
  ADR-014 is *Accepted*. Then comes the dogfood week.
- The emulator `Chrona_API37` has the signed release build installed (`com.nutabe.chrona`) with test alarms.
- Last update: 2026-11-19

## Milestones
| Milestone | State | Checkpoint | Commit |
|---|---|---|---|
| D1 | done | approved by the user 2026-11-17 ([journal](journal/D1.md)) | included in the M0 commit (no git existed before) |
| M0 | done | auto-passed (unattended), 2026-11-17 ([journal](journal/M0.md)) | `1455276` "scaffold Chrona project" |
| D2 | done | approved by the user 2026-11-17 ([journal](journal/D2.md)) | `aaedeff` "add screen designs" |
| M1a | done | approved by the user 2026-11-17 ([journal](journal/M1a.md)) | `2b1b521` "add ringing path skeleton" |
| M1b | done | approved (unattended), 2026-11-17 ([journal](journal/M1b.md)) | `0a3ca70` "add alarm store, scheduler and reconciliation" |
| M1c | done | approved (unattended), 2026-11-17 ([journal](journal/M1c.md)) | `ed0ff0b` "verify engine reliability" |
| M2 | done | approved (unattended), 2026-11-17 ([journal](journal/M2.md)) | `3b60aa9` "add alarm list, editor, settings and health screen" |
| M3 | done | approved (unattended), 2026-11-17 ([journal](journal/M3.md)) | `8fc9608` "add ringing screen and missions" |
| M4 | done | approved (unattended), 2026-11-17 ([journal](journal/M4.md)) | `3a3e582` "prepare release build" |
| DV | in progress: items 1, 2, 3, 4, 6 pass; 5, 7, 8 open | | |

## Tasks (current milestone: DV)
- [x] 1. Write `docs/DV-CHECKLIST.md` — done
- [x] 2. Install the release APK; grant permissions through onboarding — done (0.1.1 over 0.1.0, 2026-11-18)
- [ ] 3. Run the checklist — in progress (user)
- [ ] 4. Fix what fails, re-run the affected checks, settle the ADRs — todo (ADR-014 accepted 2026-11-18)

## Waiting for you
- [ ] **DV items still open** (`docs/DV-CHECKLIST.md`): item 5 (shake at each sensitivity, steps in hand and
  pocket, shaking not counted as steps, the sensor watchdog, the haptic on a wrong answer), item 7 (a real
  phone call during a ring, in progress), item 8 (the 2 hour deep Doze test, DND with alarms on and off, alarm
  volume at the minimum, the launcher icon), and the "Reviews carried from checkpoints" (M2 screenshots and
  dialogs, M3 recording). Then the dogfood week with Alarmy as backup.

## Journal (current milestone)
- 2026-11-17 21:28 DONE DV/1 - Files: docs/DV-CHECKLIST.md (both options in one list, the incident-log template
  for the dogfood week) - Verified: re-read against MILESTONES DV and the DV items collected in "Waiting for you".
- 2026-11-17 22:30 DV (user report, hand-installed release APK on the S25 Ultra) — the *Shake* mission was tried and the *Steps* mission worked: the activity-recognition permission was requested when the alarm was created and the count advanced correctly after starting the mission. Not stated: screen off, phone in a pocket, the One UI restart and the other DV items, so ADR-014 stays *Proposed* until the user confirms the screen-off case (DV-CHECKLIST item 1).
- 2026-11-17 22:40 USER FEEDBACK, handled outside the loop: (1) haptics were missing on most controls (time picker, Save, weekday chips, steppers, switches, numpad): every tappable now goes through `hapticClickable` and the switch, FAB, slider and time picker have their own haptics; verified with `dumpsys vibrator_manager` (performHapticFeedback from `com.nutabe.chrona.debug`). (2) The Samsung card in System status always warned "set the battery to Unrestricted": it now follows the real battery state (OK with "Keep Chrona out of Sleeping apps" when unrestricted, warning otherwise), so it no longer counts in the Settings badge. (3) Long press on an alarm opens a menu: Duplicate (opens the editor with a copy as a new alarm, not saved until Save), Select and Delete. Select starts a multi-select mode (count bar, delete, close, system back) with one undo for all deleted alarms; locked alarms cannot be selected. Verified: 178 unit tests, 40 instrumented tests, lint, and by hand on the emulator (menu, select 2, delete, undo). Release version bumped to 0.1.1 (versionCode 2) so it installs over 0.1.0.
- 2026-11-18 DV (user report, S25 Ultra, release 0.1.1): (1) the steps mission counts with the screen off, so ADR-014 is *Accepted*; (2) restart on One UI, (3) Samsung battery settings, (4) full-screen intent and heads-up, (6) reboot without unlocking: all confirmed; an alarm left overnight rang on time. The 0.1.1 update over 0.1.0 worked, and so did haptics, Duplicate, Select, Delete and Undo. Open: item 5 (rest), 7 (in progress), 8 (the rest). Not reported, so not recorded as passed: shaking not counting as steps, and the Doze test being the full 2 hours.
- 2026-11-19 NOTE DV - moved to the global `run-milestones` skill: project specifics in `docs/LOOP.md`, the local `milestone-loop` skill removed, the journal and checkpoints of D1 to M4 archived in `docs/journal/`, the M2 and M3 review items moved to the DV checklist, the M3 recording copied to `docs/evidence/`.
