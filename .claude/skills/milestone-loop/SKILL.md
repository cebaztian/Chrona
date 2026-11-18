---
name: milestone-loop
description: Work through Chrona's milestones (docs/MILESTONES.md) unattended, one task at a time, resuming from docs/STATUS.md after any interruption. It never waits for the user - every decision takes the recommended option, or goes to the advisor when none is recommended. Use when the user runs /milestone-loop, runs it under /loop, or asks to "continue the milestones", "resume the implementation" or "work unattended". Pass "status" to only print the current state.
---

# Chrona milestone loop

Runs the plan in `docs/MILESTONES.md` without supervision, with the least possible intervention from
the user (their instruction, 2026-11-17). Each iteration it reads the state, does the next agent task,
verifies it and records the result. **It does not stop to ask the user:** decisions follow the
[decision policy](#decision-policy), and checkpoints approve themselves and continue into the next
milestone. Because every step is written to `docs/STATUS.md` before and after it runs, an
interrupted session (crash, usage limit, closed terminal, context reset) resumes where it stopped.

**How to run it**
- Unattended: `/loop /milestone-loop` (no interval, so the loop paces itself).
- One iteration: `/milestone-loop`.
- State only: `/milestone-loop status` prints the "Now" and "Waiting for you" sections of
  `docs/STATUS.md` and changes nothing.

## Sources of truth (read at the start of every iteration)

| File | Owns |
|---|---|
| `docs/STATUS.md` | Where the work is: current milestone, task states, waiting items, journal |
| `docs/MILESTONES.md` | The tasks, their order, verification, exit criteria and checkpoints |
| `docs/PLAN.md` | Architecture, data model, failure modes |
| `docs/ADR.md` | Decisions. Respect *Accepted*. *Proposed* and *Open* ones are resolved only through the decision policy, never silently |
| `docs/DESIGN.md` | Colors, typography, components |
| `C:\Users\Sebas ROG\.claude\CLAUDE.md` | Global rules (English artifacts, surgical changes, verify before done) |

Never rely on memory of a previous iteration: re-read `docs/STATUS.md` every time. It is the only
thing that survives an interruption.

## Decision policy

Anything that used to wait for the user - a choice between options, a *Proposed* or *Open* ADR, a
checkpoint approval, an ambiguity in the plan - is decided in the loop, in this order:

1. **A recommended option exists.** Take it. A recommendation is one the text marks as
   recommended, or a stated default (for example "the milestone's default", "default is to keep
   ...", "(Recommended)"). This includes a recommendation written by an earlier iteration.
2. **No recommendation.** Consult the **`advisor`** tool (it takes no parameters and sees the whole
   conversation). Write the question, the options and the evidence you have as visible text right
   before the call, then follow its answer. If the answer contradicts evidence you already hold,
   make one more advisor call to reconcile instead of silently picking a side.
3. **Record it** in `docs/STATUS.md`, as a journal line:
   `DECIDED <topic> — chose <option> (recommended | advisor) — why`. For an ADR, set its status to
   *Accepted* with the date and "(decided unattended: recommended option | advisor)", and update the
   index row. Never leave a question in "Waiting for you" that this policy can answer.
4. **Do not ask the user.** No `AskUserQuestion` and no pause for an answer while the loop runs.

This policy overrides older wording that asks for the user's approval or answer: the
"explicit approval before committing" rule in `docs/MILESTONES.md`, "pause on user items" in ADR-022,
and any "Waiting for you" step that is a decision rather than a physical action.

Limits of the policy:
- An *Accepted* ADR is not edited in place. If the plan must change, the advisor approves a **new**
  ADR that supersedes it, and the old one's status line says so.
- A dependency the milestone does not name needs an advisor approval, recorded as a `DECIDED` line.
  The `INTERNET` permission is never added (ADR-002).
- The [hard limits](#hard-limits-never-even-unattended) below are not decisions and are never
  delegated.
- When the advisor cannot settle a blocker after one reconcile call, or a task fails after two
  distinct attempts, mark it `blocked: <reason>` with the evidence, move to the next task that does
  not depend on it, and stop only if nothing is left to do (step 5).

## Emulator (ADR-023)

All development and verification run on the AVD `Chrona_API37` (Android 17, Google APIs, PIN
`1234`).

- **Tools:** the SDK is at `$ANDROID_HOME` (set in `.claude/settings.local.json`). Always invoke
  the tools in the form the permission rules allow, so an unattended run isn't stopped by a
  prompt:
  - `"$ANDROID_HOME/platform-tools/adb" …`
  - `"$ANDROID_HOME/emulator/emulator" …`
  - `"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" …` and `avdmanager.bat`
  - `./gradlew …`
  - `git status|log|diff|add|commit …`

  `git push` is denied.
- **Is it up?** `adb devices` lists `emulator-5554` and
  `adb shell getprop sys.boot_completed` returns `1`.
  - If not, start it in the background (`run_in_background`):
    `emulator -avd Chrona_API37 -no-window -no-snapshot-save`.
  - Wait with `adb wait-for-device`, then poll `sys.boot_completed`; don't use `sleep`.
- **Observing:** `adb exec-out screencap -p > <scratchpad>/shot.png`, then Read the image.
  Use `adb shell uiautomator dump` for element bounds, `logcat` for crashes and `dumpsys` for state.
- **Acting:** `input keyevent|tap|swipe|text`, `adb emu sensor`, `adb emu gsm`, `adb reboot`,
  `adb emu kill`.
- **Unlocking after a reboot:** `input keyevent KEYCODE_WAKEUP`, swipe up, `input text 1234`, then
  `KEYCODE_ENTER`.
- **Waiting real time** (for example, the 16-minute power-off tests): end the iteration with
  `ScheduleWakeup` set to the wait, and record the pending check in "Now". Never hold the session
  with `sleep`.
- **DV steps are always [user]:** walking, One UI behavior, the real phone. Never simulate them.
- **Unverified commands:** many of the emulator commands in MILESTONES.md are from memory. When
  one fails, find the working form (`adb shell cmd <service> help`), use it, and fix MILESTONES.md
  in the same task.

## Iteration procedure

### 1. Recover

1. Read `docs/STATUS.md`. If it does not exist, create it from the template at the end of this file,
   with every milestone `todo`.
2. Run `git status --short` and `git log --oneline -5` (once git exists).
3. If a task is marked `in-progress`, the previous run was interrupted mid-task:
   - Compare the working tree with the task's "Files" line in the journal.
   - Run the task's verification (build or test).
   - If it passes, mark the task `done`. If it fails or is half-written, finish the task, or revert
     **only the files that task touched** and redo it.
   - Never discard changes you can't attribute to the interrupted task. List them under "Waiting
     for you" instead.
   - Add a journal line: `RESUMED <task> — <what was found, what was done>`.
4. If "Waiting for you" has items the user has since answered (a checked box, an answer written in
   the file, or an answer in this conversation), apply them, move them to the journal and continue.

### 2. Pick the next task

- Take the current milestone's first task that is not `done` and is tagged **[agent]**.
- Respect the gates in `docs/MILESTONES.md`:
  - D1 approved before M1b.
  - D2 approved before M2.
  - M1a's spike decisions recorded in `ADR.md` before M1b.
  - Each milestone starts only after the previous checkpoint has passed.
- **Decisions are not [user] tasks:** they go through the [decision policy](#decision-policy).
- **[user] tasks are the ones that need a human body or a physical device** (DV: walking, One UI
  behavior, the real phone). The agent never does them or simulates them. Add each to "Waiting for
  you" with exact instructions (commands to run, what to observe), **without blocking**: move on to
  the next agent task that does not depend on them. They are handed over together at the end.
- If every remaining task is blocked or [user], go to step 5 and stop.

### 3. Do the task

1. In `docs/STATUS.md`, mark the task `in-progress` and add a journal line:
   `START <milestone>/<task> — <one-line intent>`.
2. Do exactly that task: follow `docs/MILESTONES.md`, nothing more.
   - Surgical changes.
   - No dependencies except those the milestone names.
   - English code and docs; minimal comments.
3. Verify it as the milestone says: `./gradlew` build, tests, `check`, and `adb` commands when the
   phone is connected (`adb devices` shows it).
   - If the phone is not connected, the device steps go to "Waiting for you".
4. Mark it `done`, or `blocked: <reason>`. Add a journal line:
   `DONE <task> — Files: <paths> — Verified: <command → result>`.
   Report failures faithfully; never mark a failing task `done`.

### 4. Checkpoint (when all of the milestone's agent tasks and agent verifications are done)

1. Run the milestone's full **[agent]** verification and record the results in `docs/STATUS.md`
   under "Checkpoints".
2. **Local commit, never push.**
   - Stage by path, never `git add -A`.
   - One commit per checkpoint, with the subject given in `docs/MILESTONES.md`.
   - No AI co-author or attribution trailers. Never `--no-verify`, `--force` or `--amend`.
   - If a hook fails, fix its cause (never bypass it). If it still fails after the advisor's
     guidance, mark the checkpoint `blocked: <reason>` and go to step 5.
   - Only stage hunks and files that belong to this job. A file another session also edited (for
     example D2 work in `docs/ADR.md`, `docs/DESIGN.md` or `docs/STATUS.md`) is staged with a
     filtered patch (`git apply --cached`), not whole; unrelated files stay unstaged.
3. Update the docs the milestone says to update (PLAN.md §9, ADR.md, DESIGN.md).
4. **Approve and continue.** The checkpoint approves itself:
   - Resolve every decision the milestone raised (its *Proposed* ADRs, spike outcomes, open
     questions) with the [decision policy](#decision-policy), and record each.
   - Mark the milestone `approved (unattended)` in the table, with the commit, and move on to the
     next milestone as soon as its gates are met. The decisions made above are what satisfy those
     gates. Do not pause.
   - [user] verifications of the milestone (DV) are added to "Waiting for you" and do not block.
   - The only gate the loop cannot satisfy by itself is a milestone owned by another session, such
     as D2's visual direction: if the next milestone needs it and it is not approved, go to step 5
     and stop.

### 5. End the iteration

- **Continue:** if there is more agent work, schedule the next iteration with `ScheduleWakeup`
  (about 60 s, `noop: false`). Do this after every few tasks too, so each iteration starts with a
  fresh read of the state and the context stays small. Reaching a checkpoint is not a reason to
  stop: approve it (step 4) and schedule the next iteration.
- **Stop:** only when no agent task is left that does not depend on a [user] task, an unapproved
  milestone owned by another session, or a `blocked` item. Call `ScheduleWakeup` with `stop: true`.
  Then tell the user, in Spanish:
  - what was done since the last report, including every `DECIDED` line (what was chosen, and
    whether by recommendation or advisor);
  - the commits made (subject and hash);
  - the "Waiting for you" checklist (DV steps and blockers), if any.
- **Not under /loop:** finish with the same summary.

## Hard limits (never, even unattended)

- `git push`, any remote change, publishing, or tagging a release.
- Deleting or overwriting files the current task didn't create, or touching anything outside
  `C:\projects\Chrona`.
- Adding the `INTERNET` permission, or a dependency the milestone doesn't name without an advisor
  approval recorded as a `DECIDED` line.
- Editing an *Accepted* ADR in place. Supersede it with a new, advisor-approved ADR instead.
  (*Proposed* and *Open* ADRs are resolved through the decision policy; that is no longer a limit.)
- Bypassing hooks or signing, `--force`, `--amend`, or rewriting history.
- Doing a [user] step by simulation (for example, claiming a reboot test passed without the user).
- Sending personal data to external services. Stitch prompts describe screens only.
- Doing D2 picks (visual direction, font, icons) on the user's behalf.

## `docs/STATUS.md` template

```markdown
# Chrona — Status

Maintained by the `milestone-loop` skill. The loop resumes from this file. "Waiting for you" holds
only physical [user] steps (DV) and blockers; decisions are made by the loop and logged as
`DECIDED` journal lines.

## Now
- Milestone: <id> — <name>
- Task: <id> — <state>
- Last update: <YYYY-MM-DD HH:MM>

## Milestones
| Milestone | State | Checkpoint | Commit |
|---|---|---|---|
| M0 | todo | | |
| D1 | todo | | |
| D2 | todo | | |
| M1a | todo | | |
| M1b | todo | | |
| M1c | todo | | |
| M2 | todo | | |
| M3 | todo | | |
| M4 | todo | | |

## Tasks (current milestone)
- [ ] <task> — todo | in-progress | done | blocked: <reason>

## Waiting for you
- [ ] <exact physical step or blocker> (needed for: <task or gate>)

## Checkpoints
<per milestone: verification results, decisions recorded, commit>

## Journal
<append-only: YYYY-MM-DD HH:MM START|DONE|DECIDED|RESUMED|BLOCKED|STOP ...>
```
