# Chrona — Status

Maintained by the `milestone-loop` skill (`.claude/skills/milestone-loop/SKILL.md`). The loop
resumes from this file. Answer the "Waiting for you" items here or in the session.

## Now
- **Loop stopped:** M0, D1, D2, M1a, M1b and M1c are done and committed locally (not pushed). D2 was
  approved on 2026-11-17, so M2 — Alarm management UI is next: re-run `/loop /milestone-loop`. The
  reference screens are `docs/design/final/01`–`11`; DESIGN.md is the spec.
- The emulator `Chrona_API37` is running (cold-booted by the loop with `-no-snapshot`), PIN `1234`
- Last update: 2026-11-17 16:25

## Milestones
| Milestone | State | Checkpoint | Commit |
|---|---|---|---|
| M0 | done | auto-passed (unattended), 2026-11-17 | `1455276` "scaffold Chrona project" (local, not pushed) |
| D1 | done | approved by the user 2026-11-17 | included in the M0 commit (no git existed before) |
| D2 | done | approved by the user 2026-11-17 | "add screen designs" (local, not pushed) |
| M1a | done | approved by the user 2026-11-17 | `2b1b521` "add ringing path skeleton" (local, not pushed) |
| M1b | done | approved (unattended), 2026-11-17 | `0a3ca70` "add alarm store, scheduler and reconciliation" (local, not pushed) |
| M1c | done | approved (unattended), 2026-11-17 | `ed0ff0b` "verify engine reliability" (local, not pushed) |
| M2 | todo (D2 approved; next for the loop) | | |
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
- _(D2 approved 2026-11-17; M2 is unblocked. Re-run `/loop /milestone-loop`.)_
- [ ] **DV items collected so far (S25 Ultra or a hand-installed APK; never simulated):** run the step
  spike (grant Physical activity on the debug screen, "Step spike: start", lock the screen, walk,
  `logcat -s ChronaStepSpike`); check restart after a Recents swipe and under One UI's killers; deep
  Doze; `canUseFullScreenIntent()` on a fresh sideload; the One UI battery settings (PLAN §4).

## Checkpoints

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

### D2 — Final screens (approved by the user, 2026-11-17)

Files: `docs/design/final/01`–`11` (780 px wide, 2×). Stitch project
`12862449765537649811`. Seven screens were restyled in one pass; 02, 06, 07, 09 and 10 needed a second
pass, and 11 a third (its "06:30 · ALARMA ACTIVA" header removed, as on 10). Small Stitch edits only patch the live canvas: the stored HTML and screenshot keep the old
version. The fixes were re-run as full regenerations, which are stored.

Final Stitch screen ids (the project also holds stale duplicates titled "… Chrona Final"):
01 `54db0721529a4745b951b29ffbbdfaa3`, 02 `e1464473cf514fe2a176bbb4d2df3e0a`,
03 `400c98e9dea64a66a26a22ae55e06c43`, 04 `0114b4ae3b824da8abea200a028fc522`,
05 `cafc1a02235d4fd98de73a7e067c7783`, 06 `960bfa7730e14d8f94d6eb24ff4de033`,
07 `5e2ec403addd4c5f96415c8363040172`, 08 `e2b8e1aaba0e427bb61f514c561fd750`,
09 `592827d477cf4d9b9d074e1c84411e4a`, 10 `4ae46131dcfd42798f89d0acb174f437`,
11 `64f68e33148e4f27b121c0d9222a015c`.

| # | Screen | Palette rules | One Accent action | Targets | `tnum` | Spanish fits | Banned patterns | Notes |
|---|---|---|---|---|---|---|---|---|
| 01 | List | ✅ | ✅ FAB | ✅ | ✅ | ✅ | ✅ | Snoozed card hairline is sage, not brown |
| 02 | Empty list | ✅ | ✅ FAB | ✅ | — | ✅ | ✅ | |
| 03 | Editor | ✅ | ✅ Save | ✅ | ✅ | ✅ (mission row wraps to two lines, no truncation) | ✅ | Outline icons only |
| 04 | Mission picker | ✅ | ✅ Listo | ✅ | ✅ | ✅ | ✅ | No blur or shadow; selection is an outline |
| 05 | Settings | ✅ | ✅ none | ✅ | ✅ | ✅ | ✅ | Invented footer removed |
| 06 | Health | ✅ (icon + label) | ✅ Abrir ajustes | ✅ via DESIGN.md §6 (pills render at 40 dp; build at ≥ 48 dp) | ✅ | ✅ | ✅ | Icons rendered faint |
| 07 | Onboarding | ✅ | ✅ Conceder | ✅ | ✅ | ✅ | ✅ | Spanish top bar; exact alarms "Concedido" (USE_EXACT_ALARM) |
| 08 | Ringing | ✅ | ✅ Iniciar misión | ✅ 64 dp | ✅ | ✅ | ✅ | No glow |
| 09 | Math | ✅ (error icon + text) | ✅ confirm key | ✅ 72 dp keys | ✅ | ✅ | ✅ | No italics |
| 10 | Shake | ✅ | ✅ none | ✅ 64 dp | ✅ | ✅ | ✅ | "/ 30" on the counter baseline; disabled switch pill |
| 11 | Steps | ✅ | ✅ none | ✅ 64 dp | ✅ | ✅ | ✅ | Outline walking icon; gated switch looks disabled |

The D1 defect "extra 'Volumen reducido' line" on 11 was wrong: PLAN §3.6 lowers the volume during
a mission, so all three mission screens now show the note. Render quirks to ignore in M2 are listed
in DESIGN.md §6.

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
- 2026-11-17 15:20 COMMIT M1b — `0a3ca70` "add alarm store, scheduler and reconciliation" (32 paths, local
  only). Staged by path; `docs/ADR.md` only with my hunks (ADR-012/014), via a filtered patch. Left
  unstaged because they belong to the interactive session or are the loop's own definition:
  `docs/DESIGN.md`, `docs/MILESTONES.md`, the rest of `docs/ADR.md` (ADR-024/025, ADR-022/023 status
  lines), `docs/design/variants/`, and `.claude/skills/milestone-loop/SKILL.md`. The hash cell above
  and this line ride with the next commit. APPROVED M1b (unattended); next: M1c.
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
- 2026-11-17 DONE checkpoint D1 — approved by the user (screens fine; Stitch web canvas renders them partially). M1b's D1 gate is met.
- 2026-11-17 START D2/1 — variants of alarm list and ringing screen, both vibe archetypes (interactive session)
- 2026-11-17 FINDING D2/1 — Stitch timed out on 6 of 9 generation calls; saved variants/list-A1, list-A2 (Editorial) and ringing-B1 (Ethereal, rendered as desktop). list-B and ringing-A may exist only in the Stitch web UI. Waiting for the user to check the web UI and pick a direction.
- 2026-11-17 DONE D2/1+3 — user chose the A+B hybrid; fonts Newsreader + Plus Jakarta Sans (tnum), Phosphor icons (ADR-024; DESIGN.md §4 updated). Variants: docs/design/variants/ (5 of 8; Stitch timeouts).
- 2026-11-17 START D2/2 — apply the hybrid to the 11 screens via edit_screens → docs/design/final/ (interactive session)
- 2026-11-17 DONE D2/2 — Files: docs/design/final/01–11 *.png — Verified: each PNG viewed; checklist in "D2 — Final screens". Second pass on 02, 06, 07, 09, 10 and a third on 11 (in-place Stitch edits did not persist; re-run as regenerations). MILESTONES D2/5 now points at docs/design/final/.
- 2026-11-17 DONE D2/4–5 — Files: docs/DESIGN.md (§4 sizes, no italics, icon tint; §5 spacing, corners, Components table; §6 reference screens + Stitch link; §7 motion values) — Verified: re-read.
- 2026-11-17 PAUSE checkpoint D2 — waiting for the user's review (interactive session).
- 2026-11-17 APPROVED checkpoint D2 — by the user. COMMIT "add screen designs" (local only).
