# Chrona — Lessons Learned

Reusable patterns found while building Chrona (M0 to DV, 2026-11-17 to 2026-11-18). Each entry names its
source as a journal line (`date time TAG milestone`) or a checkpoint, so it can be found in
`docs/journal/<milestone>.md`.
Entries marked **(general)** also apply to other Android projects and are copied, in generic form, to
the `run-milestones` skill (`references/android-emulator.md`).

## Quick Search Index

### Emulator and adb
- An API 37 AVD needs a 6 GB data partition (general)
- Check the keyguard before typing the PIN (general)
- Git Bash rewrites `/data/...` paths (general)
- `adb reboot` can make the emulator exit (general)
- A force-stopped package gets no boot broadcast (general)
- `am start -S` to deliver extras reliably (general)
- Locked device: `run-as` and non-direct-boot activities fail (general)
- Tap by text, not by coordinates (general)
- What the emulator cannot prove (general)

### Tests and build
- Run instrumented tests before end-to-end checks (general)
- Espresso 3.5.0 fails on Android 17 (general)
- Lint does not track `local.properties` (general)
- Prove R8 by reading data back on the release build (general)
- Conditional release signing (general)

### Android platform
- `FOREGROUND_SERVICE_IMMEDIATE` for full-screen intents (general)
- `Application.onCreate` also runs for broadcasts (general)
- Recompute repeating alarms on time and time-zone changes (general)
- One-shot UI events need a holder that outlives the screen (general)
- Lock alarm and mission screens to portrait (general)
- Duck with the player gain, not the stream
- Scope oversized typography inside Material components

### Offline assets
- Pin fonts and icons to a commit, ship the licenses
- Variable fonts: one `Font` per weight

### Design tooling (Stitch)
- Small Stitch edits do not persist; regenerate

### Process (unattended loop)
- When a fix does not change the symptom, question the environment
- Concurrent sessions: stage hunks, not files
- Verify a claim against the code before recording it
- A user instruction outranks the decision policy
- Self-approved checkpoints orphan review items
- Keep STATUS short; archive the journal
- Durable docs never point at the scratchpad
- An immutable Accepted ADR goes stale

---

## Emulator and adb

### An API 37 AVD needs a 6 GB data partition (general)
**Pattern:** after `avdmanager create avd`, set `disk.dataPartition.size=6G` in
`~/.android/avd/<name>.avd/config.ini` before the first boot.
**Why:** with the default 2 GB, SurfaceFlinger crash-loops with `Assertion failed:
!rcEnc->featureInfo()->hasReadColorBufferDma`, which looks like a GPU problem and is not a storage error.
**Avoid:** chasing `-gpu` modes, `-feature` flags or emulator versions for this assertion.
**Source:** 2026-11-17 12:42 RESOLVED M0/2.

### Check the keyguard before typing the PIN (general)
**Pattern:** read `dumpsys window | grep -E "mDreamingLockscreen|isKeyguardShowing"` first; unlock with
`KEYCODE_WAKEUP`, swipe up, `input text <pin>`, `KEYCODE_ENTER` only if the keyguard is showing.
**Why:** on an unlocked device the same sequence types the PIN into the launcher search. Pressing ENTER over
an alarm screen presses its default button.
**Source:** 2026-11-17 12:47 VERIFIED M0; 15:40 MATRIX 17.

### Git Bash rewrites `/data/...` paths (general)
**Pattern:** prefix adb commands that take device paths with `MSYS_NO_PATHCONV=1`.
**Why:** Git Bash turns `/data/...` into `C:/Program Files/Git/data/...`.
**Source:** 2026-11-17 13:28 NOTE.

### `adb reboot` can make the emulator exit (general)
**Pattern:** the agent starts the emulator itself in the background
(`emulator -avd <name> -no-snapshot`), and relaunches it the same way if a reboot test makes it exit.
**Why:** a user-started emulator exited on `adb reboot` instead of rebooting, which voided a timed test.
**Source:** 2026-11-17 15:25 FINDING M1c/3.

### A force-stopped package gets no boot broadcast (general)
**Pattern:** after `am force-stop`, launch the app, wait about 30 s and check
`dumpsys package <pkg> | grep stopped=false` before a reboot test.
**Why:** the package stays `stopped=true` and `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` never reach it.
**Source:** 2026-11-17 15:31 MATRIX 3 (lesson a).

### `am start -S` to deliver extras reliably (general)
**Pattern:** use `am start -S` (force-stop, then `onCreate`) when a test passes extras to an activity.
**Why:** extras sent to a running instance do not reach `onNewIntent` reliably.
**Source:** 2026-11-17 15:31 MATRIX 3 (lesson b); 13:31 NOTE.

### Locked device: `run-as` and non-direct-boot activities fail (general)
**Pattern:** read app files and start normal activities only after unlocking.
**Why:** `run-as` cannot read the store while locked, and `am start` of an activity that is not
direct-boot aware answers "does not exist" until the user unlocks. Both are correct platform behavior.
**Source:** 2026-11-17 15:31 MATRIX 3 (lesson c); 15:40 MATRIX 17.

### Tap by text, not by coordinates (general)
**Pattern:** find bounds with `adb shell uiautomator dump` and tap the centre of the element with the
wanted text.
**Why:** fixed coordinates break with every layout change and locale.
**Source:** 2026-11-17 15:55 STEP M1c/10-11.

### What the emulator cannot prove (general)
**Pattern:** plan these for a device validation (DV) phase from the start:
- deep Doze (`deviceidle force-idle` stops at INACTIVE on the API 37 `google_apis` image);
- step sensors (none; feed the same pipeline from a debug-only fake source);
- `STREAM_ALARM` at 0 (its minimum is 1 on API 37);
- OEM behavior (One UI killers, battery pages), real calls and walking.
`dumpsys alarm` right after `am start` shows nothing until the process runs `onCreate`; wait about 2 s.
**Source:** 2026-11-17 13:33 PARTIAL M1a; 13:38 DONE M1a/6; 15:42 MATRIX 14; 13:31 NOTE.

## Tests and build

### Run instrumented tests before end-to-end checks (general)
**Pattern:** run `connectedDebugAndroidTest` first, then reinstall or start the manual emulator checks; the
device must be unlocked and awake.
**Why:** AGP can uninstall the app after the instrumented run.
**Source:** 2026-11-17 17:35 DECIDED M2 dependencies.

### Espresso 3.5.0 fails on Android 17 (general)
**Pattern:** declare `androidx.test.espresso:espresso-core:3.7.0` as `androidTestImplementation`.
**Why:** `ui-test-junit4` resolves 3.5.0, which reflects on the hidden
`InputManager.getInstance` and fails every test with `NoSuchMethodException`.
**Source:** 2026-11-17 19:50 DECIDED M2 espresso pin.

### Lint does not track `local.properties` (general)
**Pattern:** after editing `local.properties`, re-run lint with `--rerun`.
**Why:** the lint analysis task does not declare it as an input, so a stale result is reused.
**Source:** M0 checkpoint, "Known, left for later".

### Prove R8 by reading data back on the release build (general)
**Pattern:** on the minified release APK, save every serialized subtype through the UI, force-stop, relaunch
and read it back; also open every typed navigation route and decode the store in direct boot. Add keep rules
only after an observed failure.
**Why:** kotlinx.serialization and typed routes needed no keep rules here, which only a read-back proves.
**Source:** 2026-11-17 20:47 DECIDED M4 release verification; M4 checkpoint.

### Conditional release signing (general)
**Pattern:** keystore in a git-ignored folder in the project (`.signing/`), passwords in a git-ignored
`keystore.properties`, and a signing config that applies only when that file exists. Generate the password
with a CSPRNG and never print it. Back both up privately before relying on the release build.
**Why:** a clone still builds, and a sideloaded app can only be updated with the same key.
**Source:** 2026-11-17 20:46 DECIDED M4 keystore location.

## Android platform

### `FOREGROUND_SERVICE_IMMEDIATE` for full-screen intents (general)
**Pattern:** call `Notification.Builder.setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` on a
foreground-service notification that carries a full-screen intent.
**Why:** Android defers foreground-service notifications by about 10 s; the full-screen UI arrived 10 s
after the sound. With the flag: 0.18 s.
**Source:** 2026-11-17 13:31 VERIFIED M1a.

### `Application.onCreate` also runs for broadcasts (general)
**Pattern:** do user-start work (such as reconciling scheduled alarms) in the launcher activity, not in
`Application.onCreate`; touch only the device-protected context there.
**Why:** `App.onCreate` also runs when an alarm broadcast starts the process, and reconciling there queued
a duplicate ring.
**Source:** 2026-11-17 15:12 DECIDED M1b reconcile on start.

### Recompute repeating alarms on time and time-zone changes (general)
**Pattern:** on `TIME_SET` and `TIMEZONE_CHANGED`, recompute the next trigger of repeating alarms; keep
one-shots at their absolute instant; call `TimeZone.setDefault(null)` first.
**Why:** rescheduling the stored instant unchanged leaves a daily alarm on the old local time.
**Source:** 2026-11-17 15:41 DECIDED M1c/12; 15:42 MATRIX 12.

### One-shot UI events need a holder that outlives the screen (general)
**Pattern:** keep a "deleted, offer undo" event in a `StateFlow` held by the app container and let the list
consume it.
**Why:** a `SharedFlow` event emitted while the editor was on top had no collector (the list was not
composed), so the undo snackbar never appeared.
**Source:** 2026-11-17 19:10 DONE M2 block 3B.

### Lock alarm and mission screens to portrait (general)
**Pattern:** `screenOrientation="portrait"` on the ringing activity.
**Why:** sensor tests that drive the accelerometer, or a phone tilted on a nightstand, rotated it and cut its
buttons off.
**Source:** 2026-11-17 22:31 FINDING M3.

### Duck with the player gain, not the stream
**Pattern:** lower the volume during a mission with the `MediaPlayer` gain; keep `STREAM_ALARM` for the
alarm's level.
**Why:** the observer that restores `STREAM_ALARM` (so the user cannot silence the alarm with the keys) would
undo a duck done on the stream.
**Source:** 2026-11-17 21:08 DECIDED M3 stop gate.

### Scope oversized typography inside Material components
**Pattern:** override the text style of the Material 3 time picker locally (52 sp here).
**Why:** the picker draws its hour and minute boxes with the theme's `displayLarge`, which was 88 sp for the
list's clock and clipped the digits.
**Source:** M4 checkpoint, "Bug found and fixed".

## Offline assets

### Pin fonts and icons to a commit, ship the licenses
**Pattern:** download fonts and icons from a pinned commit, record URL, SHA and license in `THIRD_PARTY.md`,
ship the license texts in `assets/licenses/`, and rename files to resource-safe names. Convert icons with a
script that fails on anything that is not a filled `<path>`, then screenshot an icon sheet.
**Why:** an app without `INTERNET` cannot fetch them at runtime, and the strict converter plus the sheet
make a broken icon visible at build time instead of on a screen.
**Source:** 2026-11-17 17:35 DECIDED M2 dependencies and assets; 17:55 DONE M2 block 1.

### Variable fonts: one `Font` per weight
**Pattern:** declare one `Font` per used weight with `FontVariation.weight`, and confirm the lightest
weight on a screenshot.
**Why:** each weight the design uses (Light 88 sp for the clock) must render as that weight; the
screenshot check is what proves it.
**Source:** 2026-11-17 17:35 DECIDED M2 design gaps (5); 17:55 DONE M2 block 1.

## Design tooling (Stitch)

### Small Stitch edits do not persist; regenerate
**Pattern:** apply fixes as full screen regenerations; export from the stored version.
**Why:** small edits patch only the live canvas; the stored HTML and screenshot keep the old version.
Generation calls also time out often (6 of 9 in one batch): check the web UI before regenerating.
**Source:** D2 checkpoint; 2026-11-17 FINDING D2/1.

## Process (unattended loop)

### When a fix does not change the symptom, question the environment
**Pattern:** after two fixes that leave the same symptom, stop varying flags and check the configuration
the plan assumed (here, the AVD config).
**Why:** five emulator launches with different GPU and feature flags failed the same way; the cause was the
2 GB data partition, found by the user.
**Source:** 2026-11-17 12:10 to 12:42, FINDING / BLOCKED / RESOLVED M0/2.

### Concurrent sessions: stage hunks, not files
**Pattern:** when another session also edits a doc, stage only your hunks with a filtered patch
(`git apply --cached`) and leave the rest unstaged.
**Why:** design work (D2) and the loop edited `ADR.md` and `STATUS.md` at the same time.
**Source:** 2026-11-17 15:20 COMMIT M1b.

### Verify a claim against the code before recording it
**Pattern:** before a `DECIDED` or `DONE` line names a class or interface, check that it exists.
**Why:** a decision recorded a `StepSource` interface that was never built; a later correction had to
explain the real seam (`MissionSensors`).
**Source:** 2026-11-17 20:48 CORRECTION M3.

### A user instruction outranks the decision policy
**Pattern:** when the user has said how a specific decision waits (here, ADR-014 stays *Proposed* until
DV), the loop records it as a stated default and does not decide it.
**Why:** the general policy would otherwise have accepted it without the device evidence.
**Source:** 2026-11-17 21:07 DECIDED M3 ADR-014.

### Self-approved checkpoints orphan review items
**Pattern:** move every `[user]` review of an auto-approved checkpoint into the DV checklist.
**Why:** the M2 and M3 "review against the designs" boxes stayed open in STATUS after both milestones closed.
**Source:** STATUS "Waiting for you", 2026-11-18 (moved on 2026-11-19 to DV-CHECKLIST, "Reviews carried
from checkpoints").

### Keep STATUS short; archive the journal
**Pattern:** STATUS holds Now, Waiting and the milestone table; the journal is archived per milestone.
**Why:** STATUS reached 844 lines and a full read was truncated, while the loop recovers from that file.
**Source:** 2026-11-18 review of the method.

### Durable docs never point at the scratchpad
**Pattern:** move evidence the user must review (recordings, screenshots) into the repo or a git-ignored
project folder before naming it in a doc.
**Why:** the M3 screen recording was linked from STATUS but lived in a session scratchpad that can be
cleared (copied to `docs/evidence/` on 2026-11-19).
**Source:** STATUS "Waiting for you", M3 item (before the 2026-11-19 trim).

### An immutable Accepted ADR goes stale
**Pattern:** allow a dated "Update" note on an Accepted ADR for consequences that changed, and supersede it
only when the decision itself changes.
**Why:** ADR-023's consequences still said ADR-012 and 014 stay *Proposed* after ADR-012 was accepted.
**Source:** 2026-11-17 14:05 APPLIED (FLAG).
