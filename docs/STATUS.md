# Chrona — Status

Maintained by the `milestone-loop` skill (`.claude/skills/milestone-loop/SKILL.md`). The loop
resumes from this file. Answer the "Waiting for you" items here or in the session.

## Now
- Milestone: M0 — done, checkpoint `auto-passed (unattended)`, committed locally (not pushed)
- Next: M1a — Walking skeleton and spikes
- Emulator `Chrona_API37` is running (started by the user, 6 GB data partition), PIN `1234` set
- Last update: 2026-11-17 12:58

## Milestones
| Milestone | State | Checkpoint | Commit |
|---|---|---|---|
| M0 | done | auto-passed (unattended), 2026-11-17 | "scaffold Chrona project" (first commit; `git log`) |
| D1 | done | approved by the user 2026-11-17 | included in the M0 commit (no git existed before) |
| D2 | in-progress: task 1 (variants) owned by the interactive session, so the loop must skip D2 | | |
| M1a | todo | | |
| M1b | todo (gated on D1 and M1a) | | |
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
- _(D1 approved 2026-11-17; the emulator question is resolved: see the 12:42 journal line.)_

## Checkpoints

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
- 2026-11-17 DONE checkpoint D1 — approved by the user (screens fine; Stitch web canvas renders them partially). M1b's D1 gate is met.
- 2026-11-17 START D2/1 — variants of alarm list and ringing screen, both vibe archetypes (interactive session)
- 2026-11-17 FINDING D2/1 — Stitch timed out on 6 of 9 generation calls; saved variants/list-A1, list-A2 (Editorial) and ringing-B1 (Ethereal, rendered as desktop). list-B and ringing-A may exist only in the Stitch web UI. Waiting for the user to check the web UI and pick a direction.
