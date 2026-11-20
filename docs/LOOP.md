# Chrona — Loop overlay

What the `run-milestones` skill needs to know about Chrona and cannot guess. Read at the start of every
iteration. Commands below were run during M0 to M4 unless marked otherwise.

## Environment

- **Kind:** Android emulator, AVD `Chrona_API37`, image `system-images;android-37.0;google_apis;x86_64`
  (Android 17, Google APIs so `adb root` works), lock-screen PIN `1234` (ADR-023).
- **SDK:** `C:/Users/Sebas ROG/AppData/Local/Android/Sdk` (from `local.properties`). `ANDROID_HOME` is not
  set in the environment; set it in `.claude/settings.local.json` (`env`) or use the full path.
- **Is it up?** `adb devices` lists `emulator-5554` and `adb shell getprop sys.boot_completed` returns `1`.
- **Start it** in the background (`run_in_background`): `emulator -avd Chrona_API37 -no-snapshot`
  (`-no-window` when unattended). Wait with `adb wait-for-device`, then poll `sys.boot_completed`.
- **Observe:** `adb exec-out screencap -p > <scratchpad>/shot.png` and read the image;
  `adb shell uiautomator dump` for element bounds; `logcat` (tags `ChronaRing`, `ChronaReconcile`,
  `ChronaStore`, `ChronaDebug`, `ChronaStepSpike`) for behavior; `dumpsys alarm|notification|audio|deviceidle` for state.
- **Act:** `input keyevent|tap|swipe|text`, `adb emu sensor set acceleration x:y:z`, `adb emu gsm call|accept|cancel`,
  `adb reboot`, `adb emu kill`, `cmd alarm set-timezone`, `cmd notification set_dnd`, `pm grant|revoke`.
- **Unlock after a reboot:** check the keyguard first; then `KEYCODE_WAKEUP`, swipe up, `input text 1234`,
  `KEYCODE_ENTER` (never ENTER while `RingingActivity` is on screen: it presses its button).
- **Builds:** release `com.nutabe.chrona`, debug `com.nutabe.chrona.debug` (side by side). The debug build
  has a second launcher, "Chrona debug", with permission status, ring triggers, the step spike and sensor
  overrides.
- **References to read first:** `~/.claude/skills/run-milestones/references/android-emulator.md` and the
  index of [LESSONS-LEARNED.md](LESSONS-LEARNED.md).

## Allowed command forms

Not yet configured in `.claude/settings.local.json` (it only sets `MCP_TOOL_TIMEOUT`). Allow these and deny
`git push` before the next unattended run:

- `"<sdk>/platform-tools/adb" ...`
- `"<sdk>/emulator/emulator" ...`
- `"<sdk>/cmdline-tools/latest/bin/sdkmanager.bat" ...` and `avdmanager.bat ...`
- `./gradlew ...`
- `git status|log|diff|add|commit|apply ...`

In Git Bash, prefix adb commands that take `/data/...` paths with `MSYS_NO_PATHCONV=1`.

## Verification commands

| Purpose | Command | Pass looks like |
|---|---|---|
| Build, unit tests, lint, no-INTERNET check | `./gradlew check` | BUILD SUCCESSFUL; the log reads the release merged manifest |
| Unit tests only | `./gradlew test` | 0 failures in `app/build/test-results` |
| UI tests (emulator unlocked and awake) | `./gradlew connectedDebugAndroidTest` | 0 failures; reinstall the app afterwards |
| Release build | `./gradlew assembleRelease` | signed APK in `app/build/outputs/apk/release/` (`apksigner verify`) |
| Install debug | `./gradlew installDebug` | `am start -W` Status ok |

## Gates

- No milestone is open: M0 to M4 are done and DV is in progress (user). The next work comes from DV
  failures (DV task 4) or a new plan.
- Owned by an interactive session (the loop skips them and never stages their files): design work (D1,
  D2, and any new screen design).

## Reserved decisions

- The visual direction, fonts and icon set (ADR-024), and any new screen design.
- The launcher icon as a final brand choice (approved "for now" only).
- Whether a DV item passes: only the user's report on the S25 Ultra counts.

## Extra hard limits

- Never add the `INTERNET` permission (ADR-002); `./gradlew check` must keep failing if it appears.
- Never touch the release keystore (`.signing/`, `keystore.properties`) beyond reading it for a build.
- Design-tool (Stitch) prompts describe screens only.

## Device or real-world validation

- Samsung Galaxy S25 Ultra, Android 17, One UI. The user installs the release APK by hand (option b); the
  agent can run adb steps only if the user connects it (option a).
- Checklist and results: [DV-CHECKLIST.md](DV-CHECKLIST.md).
