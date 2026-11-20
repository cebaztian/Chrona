# Chrona — M1b journal

Archived from `docs/STATUS.md` on 2026-11-19, when the project moved to the `run-milestones` skill.
Entries keep their original order and wording.

## Checkpoint

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

## Journal

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
