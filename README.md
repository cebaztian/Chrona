# Chrona

A standalone, ad-free Android alarm clock in Kotlin and Jetpack Compose. It does one job: wake you up
and make sure you are awake. Alarms ring on time with the app closed, in Doze, after a reboot and with
the screen locked, and are dismissed with a mission (math, shake, steps).

**Privacy is a feature:** no ads, no analytics, no accounts, no backend, and no `INTERNET` permission
in the release build (enforced by `./gradlew check`).

**Non-goals:** backend, sync or cloud backup; sleep tracking, news, horoscope, premium tiers; iOS.

Plan, decisions and progress live in [`docs/`](docs/): [PLAN](docs/PLAN.md), [ADR](docs/ADR.md),
[MILESTONES](docs/MILESTONES.md), [STATUS](docs/STATUS.md).

## Requirements

- JDK 21
- Android SDK with platform 37 and platform-tools (`sdk.dir` in `local.properties`, which is not
  committed)
- Gradle is provided by the wrapper (9.6.0)

## Build and install

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew installDebug       # install it on the connected device or emulator
./gradlew check              # verification, including the INTERNET permission check
```

The debug build installs side by side with release as `com.nutabe.chrona.debug` ("Chrona (debug)").

## Emulator

Development runs on the AVD `Chrona_API37` (`system-images;android-37.0;google_apis;x86_64`). Set
`disk.dataPartition.size=6G` in its `config.ini`; the 2 GB default makes the system crash-loop. See
[MILESTONES](docs/MILESTONES.md), M0.
