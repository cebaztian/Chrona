# Chrona — Implementation Plan

A standalone, ad-free Android alarm clock written entirely in **Kotlin + Jetpack Compose**. It does
one job well: wake you up and make sure you are awake. Replaces Alarmy for personal use, without its
ads, upsells and features that go unused.

> Status: **M0 done** (2026-11-17): an empty Compose app builds and runs on the emulator, under git.
> Progress lives in [STATUS.md](STATUS.md).
> Stack decision: originally Flutter. Switched to pure Kotlin because the app is Android-only and
> its hard parts (scheduling, ringing, direct boot, sensors) are native anyway.

---

## 1. Goals and non-goals

**Goals (MVP)**
- Reliable alarms: they ring on time with the app closed, the phone in Doze, after a reboot
  (including before the first unlock), and with the screen locked.
- Three dismiss missions: **math problems**, **shake the phone**, **walk N steps**.
- Snooze with limits, gradual volume, vibration, custom sound.
- **Privacy as a feature**: no ads, no analytics, no accounts, no backend, and **no `INTERNET`
  permission in the release build**.

**Non-goals**
- No backend, sync or cloud backup.
- None of Alarmy's extras (sleep tracking, news, horoscope, wake-up check, premium tiers).
- iOS.

---

## 2. Architecture

A single-module Android app. Packages:

```
com.nutabe.chrona
├── engine/      alarm engine: store, next-trigger, scheduler, receivers, ringing service
├── mission/     pure Kotlin mission logic (math generator, shake detector, step filter)
│                + sensor adapters
├── ui/          Compose screens + ViewModels (StateFlow)
│   ├── alarms/    list + editor
│   ├── ringing/   RingingActivity + mission screens
│   └── health/    permissions & reliability checks, onboarding
└── App.kt       Application: must not touch credential-encrypted storage (see §4)
```

```
┌──────────────────────────────── UI (Compose) ───────────────────────────────┐
│ MainActivity: alarm list · editor · settings · health screen                 │
│ RingingActivity: ringing screen + mission (show-when-locked, own task)       │
│ ViewModels expose StateFlow; no DI framework (manual wiring in App)          │
└────────────────────▲─────────────────────────────────────▲───────────────────┘
                     │ AlarmRepository                     │ RingingController (StateFlow)
┌────────────────────┴─────────────────────────────────────┴───────────────────┐
│ Engine                                                                       │
│  AlarmStore         single source of truth (JSON file, device-protected)    │
│  NextTrigger        recurrence → next fire time (java.time, DST-safe)        │
│  Scheduler          AlarmManager.setAlarmClock()                             │
│  AlarmReceiver      fires → starts RingingService                            │
│  RingingService     FGS: audio (USAGE_ALARM), vibration, volume ramp,        │
│                     notification with full-screen intent, inactivity        │
│                     watchdog, sensor missions (shake, steps)                 │
│  RingingController  in-process state shared by service and RingingActivity   │
│  BootReceiver       BOOT / LOCKED_BOOT / TIME_SET / TIMEZONE / package       │
│                     replaced → reconcile                                     │
│  Permissions        status + deep links into system settings                 │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **One language, no bridge.** The UI talks to the engine directly through `AlarmRepository` (CRUD,
  next trigger) and `RingingController` (current ringing state, mission progress, snooze, complete).
- **`RingingController`** is a process-wide object that exposes a `StateFlow<RingingState>`. The
  service writes to it; `RingingActivity` observes it and sends user actions back. If the activity
  is destroyed, the alarm keeps ringing because the service owns it.

### Data model

```kotlin
@Serializable
data class Alarm(
    val id: Long,
    val hour: Int, val minute: Int,
    val label: String,
    val repeatDays: Set<DayOfWeek>,      // empty = one-shot
    val enabled: Boolean,
    val soundUri: String?,               // null = bundled default
    val vibrate: Boolean,
    val volumeRampSeconds: Int,
    val maxVolumePercent: Int,
    val snooze: SnoozeConfig,            // enabled, minutes, maxCount
    val mission: Mission,                // None | Math | Shake | Steps
    // Runtime state, persisted so a reboot, update or process death can't lose or skip a ring
    val nextTriggerAt: Instant?,         // absolute instant last handed to AlarmManager
    val snoozedUntil: Instant?,
    val snoozesUsed: Int,
)

sealed interface Mission {
    data object None : Mission
    data class Math(val difficulty: Difficulty, val count: Int) : Mission
    data class Shake(val count: Int, val sensitivity: Sensitivity) : Mission
    data class Steps(val count: Int) : Mission
}

@Serializable
data class ActiveRing(                   // at most one; null when nothing is ringing
    val alarmId: Long,
    val startedAt: Instant,
    val forcedSnoozeCycles: Int,         // auto-silence cycles used, so a reboot can't reset the cap
)

@Serializable
data class StoreData(
    val schemaVersion: Int,
    val alarms: List<Alarm>,
    val activeRing: ActiveRing?,
    val settings: AppSettings,           // onboardingDone
)
```

Serialized with `kotlinx.serialization` to one JSON file written atomically (`AtomicFile`) in
**device-protected storage**. The dataset is tiny, so Room would be overkill. Every field has a
default and the `Json` instance uses `ignoreUnknownKeys`, so adding fields later never makes an
existing file unreadable.

**One-shot lifecycle.** A one-shot alarm is disabled when its ring ends (mission solved or
auto-silence cap reached), not when it fires. Its trigger is stored as an absolute instant, so it never
silently rolls over to "tomorrow".

**Reconciliation** (on boot, time change, package update and app start), per enabled alarm:
- If `ActiveRing` is set or `snoozedUntil` is in the past, **however long ago**: **ring now**.
  Otherwise, powering the phone off for a while would be a way to skip the mission.
- If a never-fired `nextTriggerAt` is in the past by less than the auto-silence window (default
  15 min): **ring now**.
- Ringing now means scheduling `setAlarmClock` a few seconds ahead, so the normal `AlarmReceiver` path
  runs and no FGS is started from boot.
- If a never-fired `nextTriggerAt` is further in the past: post a **"missed alarm"** notification. A
  one-shot is disabled; a repeating alarm moves to its next occurrence.
- Otherwise: reschedule `nextTriggerAt` (or `snoozedUntil`) unchanged.

---

## 3. The ringing path, end to end

1. **Schedule.** `AlarmManager.setAlarmClock(info, operation)` for the next occurrence of each
   enabled alarm. It is exempt from Doze and shows the system alarm icon. The `showIntent` opens the
   editor.
2. **Fire.** `AlarmReceiver` starts `RingingService` as a foreground service with type
   **`systemExempted`**. Apps holding `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` are eligible (see
   §9). It persists `ActiveRing` and then schedules a repeating alarm's *next* occurrence right
   away.
   - **Wake-lock handoff.** `AlarmManager` holds a wake lock only for the duration of `onReceive`.
     `AlarmReceiver` therefore acquires a partial wake lock with a short timeout, and
     `RingingService` releases it after it has taken its own.
   - **Process death.** `RingingService` returns `START_STICKY`. When restarted with a null intent, it
     resumes from the persisted `ActiveRing`. One UI may not restart it, or may not let it return to
     the foreground. So, while `ActiveRing` is set, a backup `setAlarmClock` is kept re-armed about
     60 s ahead; the M1a spike decides whether it is needed.
   - **`PendingIntent` identity.** Request codes are keyed by (alarm id, kind): `NEXT`, `SNOOZE`,
     `RING_NOW` and `BACKUP`. A snooze never replaces the next occurrence.
3. **Ring immediately.** `MediaPlayer` with `AudioAttributes.USAGE_ALARM`, looping, with a volume
   ramp. Vibration uses `USAGE_ALARM` attributes. A wake lock is held while ringing.
4. **Show the UI.** The service posts a high-priority notification on an alarm channel with a
   **full-screen intent** pointing to **`RingingActivity`**. It is a Compose activity with its own
   task affinity and `excludeFromRecents`.
   - Only this activity uses `setShowWhenLocked(true)`, `setTurnScreenOn(true)` and
     `FLAG_KEEP_SCREEN_ON`.
   - It calls `finish()` as soon as the mission completes or the alarm snoozes, so the alarm list is
     never reachable over the keyguard.
   - It has no heavy startup and appears instantly.
5. **Phone unlocked and in use.** In this state Android shows the full-screen intent as a
   **heads-up notification**, not full screen. Its body (tap) opens the mission. Its only action
   buttons are **"Solve to stop"** and **"Snooze"** (only if snoozes remain). **There is never a
   "Dismiss" action.**
6. **Mission.** While a mission is active, volume drops to about 20%. The **inactivity watchdog**
   restores full volume if there is no mission progress for N seconds (default 15). Completing the
   mission stops the service, audio and vibration, clears `ActiveRing` and `snoozesUsed`, clears the
   notification, and disables the alarm if it is a one-shot.
   - **Sensor watchdog.** If a sensor mission (shake or steps) receives **no sensor events at all**
     for 30 s (**2 min** for steps, since the detector is silent while you stand still; ADR-019),
     `RingingActivity` offers **"Switch to math"**. A sensor that exists but delivers
     nothing must never leave the alarm undismissable.
7. **Snooze.** Snooze persists `snoozedUntil` and increments `snoozesUsed`, schedules a one-shot
   `setAlarmClock` for now + N min, and stops ringing. Once `maxCount` is reached the Snooze action
   disappears.
8. **Back button / app switch.** `RingingActivity` blocks back. If the user leaves the activity, the
   service keeps ringing and the notification remains. On Android 14+ users can swipe away FGS
   notifications, so the service re-posts it.
9. **Auto-silence (safety).** Ringing stops after a configurable maximum (default 15 min) so a phone
   left at home doesn't ring forever. Stopping for good would let you sleep through 15 minutes as a
   way to skip the mission, so auto-silence acts as a **forced snooze that re-rings**, with a hard cap
   of 3 cycles (§10, decision 5). It does not consume the user's snooze allowance. After the cap the alarm
   stops and is reported as "missed".
10. **No bypass while ringing or snoozed.**
    - **Volume.** `RingingActivity` consumes the hardware volume keys. The service watches
      `STREAM_ALARM` and restores the alarm's level if it is lowered from the volume panel.
    - **Editing.** In `MainActivity`, the alarm that is ringing or snoozed is locked: it cannot be
      toggled, edited or deleted, and it shows "Solve to stop" instead.

---

## 4. Failure modes (what makes a silent or missed alarm)

| Failure | Handling |
|---|---|
| **Reboot overnight, phone not yet unlocked** (direct boot) | `BootReceiver` listens to `LOCKED_BOOT_COMPLETED`. `BootReceiver`, `AlarmReceiver`, `RingingService` and `RingingActivity` are **all** `directBootAware`. `AlarmStore` lives in **device-protected storage**. The bundled default sound lives in the APK. A user-chosen ringtone may be unreadable before unlock, so fall back to the bundled sound. |
| Startup code touching credential-encrypted storage before unlock | `App.onCreate()` and any startup initializer must use only the device-protected context, which a crash there would break. Ban default `SharedPreferences` and AndroidX App Startup initializers that read user storage. The locked-boot test in M1 proves this. |
| Reboot / time change / timezone change | Run reconciliation (§2) on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`. |
| **Reboot, update or process death mid-ring or mid-snooze** | The persisted `ActiveRing`, `snoozedUntil` and `snoozesUsed` survive. Reconciliation re-rings, and `START_STICKY` resumes the service. A reboot is never a way to skip the mission. |
| Phone off at alarm time | Reconciliation: re-ring if within the auto-silence window, otherwise send a "missed alarm" notification. A one-shot never rolls over to the next day. |
| Sensor present but delivering nothing | Sensor watchdog (§3.6) offers math after 30 s with no events (2 min for steps). |
| DST transitions | `NextTrigger` uses `java.time.ZonedDateTime`. Unit tests cover nonexistent times (spring forward) and repeated times (fall back). |
| Exact-alarm permission revoked | `USE_EXACT_ALARM` is granted at install and the user can't revoke it (confirm at M1). The health screen still checks `canScheduleExactAlarms()`. |
| Chosen sound missing or failing to load | Catch `MediaPlayer` errors, then fall back to the bundled sound, then to vibration only as a last resort. |
| **Alarm stream volume at 0** | When ringing, set `STREAM_ALARM` to the alarm's configured level and restore it after. Warn in the editor if the stream is muted. |
| Second alarm fires while one is ringing | Queue it: finish the current mission, then ring the next one. Never stack two players. A trigger for the alarm that is *already* ringing (app start, `TIME_SET`, `BACKUP`) is a no-op, not a second ring. |
| Incoming or ongoing phone call | Request audio focus. During a call, use a soft repeated tone instead of full volume, and resume normally after the call. |
| Do Not Disturb | `USAGE_ALARM` follows the user's DND "allow alarms" setting, which is on by default. Show this in the health screen. |
| **Force-stop** from system settings | Android cancels all alarms. This is a platform limitation and cannot be fixed. Document it in the app. The next app launch reschedules everything. |
| OEM battery killers (target device: Samsung) | Request to ignore battery optimizations. The health screen links to Samsung's settings: battery set to "Unrestricted", and Chrona kept out of "Sleeping apps" / "Deep sleeping apps" (or added to "Never auto sleeping apps"). Based on dontkillmyapp.com; confirm the setting names on One UI at M1. |
| Background audio restrictions (Android 17) | Android 17 hardens background audio. Apps targeting API 37 are exempt when they hold the exact-alarm permission **and** use `USAGE_ALARM`. Chrona does both. |
| Mission impossible at ring time (sensor missing, permission revoked) | Fall back to **math** automatically. The user always has a way to stop the alarm. |

### Permissions health screen

One screen with a traffic-light status for each item and a button that deep-links to the right
system setting:

- Exact alarms (`AlarmManager.canScheduleExactAlarms()`)
- Notifications (`POST_NOTIFICATIONS`, Android 13+)
- Full-screen intent (`NotificationManager.canUseFullScreenIntent()`, Android 14+)
- Physical activity (`ACTIVITY_RECOGNITION`), shown only if any alarm uses the steps mission
- Battery optimization exemption
- Alarm volume > 0
- DND state, read through `getCurrentInterruptionFilter()`, which needs no special access. Reading
  whether DND specifically allows alarms may need notification-policy access (confirm at M1). If it
  does, show only the filter and explain the setting rather than request that access.
- OEM autostart guidance, where applicable

The same checks run as a first-launch onboarding.

### Manifest permissions

| Permission | Why |
|---|---|
| `USE_EXACT_ALARM` | Exact alarms. Auto-granted to alarm apps (API 33+). |
| `RECEIVE_BOOT_COMPLETED` | Reconciliation after reboot. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | `RingingService`. |
| `POST_NOTIFICATIONS` | Ringing and missed-alarm notifications (API 33+). |
| `USE_FULL_SCREEN_INTENT` | `RingingActivity` over the lock screen. |
| `WAKE_LOCK`, `VIBRATE` | Ringing. |
| `ACTIVITY_RECOGNITION` | Steps mission (API 29+). |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Health screen request. Play restricts it, which doesn't matter for a sideloaded APK (§10). |

There is no `INTERNET` permission and no storage permission. A custom sound picked through
`RingtoneManager` or the system file picker uses a persisted URI grant. The exact-alarm pairing and
the full list must be **confirmed at M1** against the docs, since they come from prior knowledge.

---

## 5. Missions

All mission logic is pure Kotlin in `mission/`, with no Android types, and is unit tested. Sensor
adapters only feed events into it.

### 5.1 Math
- **Difficulty tiers**
  - Easy: `a + b`, both 2-digit.
  - Medium: `a × b + c`, 2-digit × 1-digit.
  - Hard: `a × b + c − d`, mixed 2–3 digits.
- **Problem count**: 1–10 per alarm.
- **Own numpad.** No system keyboard, no calculator, no copy and paste.
- **Wrong answer** generates a fresh problem, so guessing gets no reward. A light haptic signals an
  error.
- **Answer bounds**: results are always positive integers.
- The generator takes an injectable `Random` so it can be tested.
- Math is also the **universal fallback** for any mission that cannot run.

### 5.2 Shake
- **Sensor**: `TYPE_LINEAR_ACCELERATION` (gravity removed), or `TYPE_ACCELEROMETER` with a
  high-pass filter if the device lacks it.
- **Detection**: a shake counts when the magnitude crosses a sensitivity threshold, with a debounce
  of about 250 ms between counts.
- **Target count**: 10–100, default 30. **Sensitivity**: low, medium or high, which maps to the
  threshold.
- Apps targeting Android 12+ get rate-limited accelerometer data. That is fine for shake detection,
  so `HIGH_SAMPLING_RATE_SENSORS` is not needed.

### 5.3 Steps (walk N steps)
- **Sensor choice**
  - Primary: `TYPE_STEP_DETECTOR`, registered with `maxReportLatencyUs = 0`. It fires one event per
    step; documented latency is under 2 s.
  - Fallback: `TYPE_STEP_COUNTER`. It is cumulative since boot and has up to 10 s of latency, so use
    the delta from the value at mission start.
  - If neither sensor exists, the editor warns and disables the option. At ring time, fall back to
    math.
- **Permission**: `ACTIVITY_RECOGNITION` is a runtime permission on Android 10+ (API 29).
  - Request it **in the editor** when the user picks the steps mission, never at ring time: the user
    is half asleep and a dialog may not work over the keyguard.
  - It appears in the health screen.
  - If it was revoked by ring time, fall back to math.
- **Counted in `RingingService`, not the activity.** People pocket the phone or press power while
  walking; an activity-scoped listener would stop counting and make the mission impossible.
  `RingingActivity` only displays progress from `RingingController`.
- **Anti-cheat cadence filter**: ignore steps that arrive faster than a plausible human cadence
  (more than about 3–4 per second), so shaking the phone doesn't count as walking.
- **Vibration off during the steps mission** by default. A vibrating phone can trigger false steps
  on accelerometer-based detectors.
- **Target**: 20–500 steps, default 50. A large progress display, with the screen kept on while the
  activity is visible.
- **Inactivity watchdog**: no step for N seconds restores full volume.

### 5.4 Shared rules
- **Preview** in the editor, with an option to run it with sound off.
- Back is blocked while ringing. Missions cannot be skipped from the notification.
- Sensors are registered only while a mission is active and unregistered immediately after.

---

## 6. Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 with the brand palette in [DESIGN.md](DESIGN.md) (dark-first, no dynamic color), Navigation Compose |
| State | ViewModel + `StateFlow`; manual DI (no Hilt, too much for this size) |
| Persistence | `kotlinx.serialization` JSON + `AtomicFile` in device-protected storage |
| Audio | `MediaPlayer` / `AudioAttributes.USAGE_ALARM` |
| Sensors | `SensorManager` directly (no library) |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`), Gradle wrapper |
| i18n | Android string resources (`values/`, `values-es/`) |
| Tests | JUnit (pure logic), Compose UI tests, device matrix |

**Versions are not pinned in this plan.** Pin them at scaffold time to the current stable releases
(AGP, Kotlin, Compose BOM), and set `targetSdk` to the latest stable API.

---

## 7. Milestones

Summary only. Tasks, order, verification and checkpoints are in [MILESTONES.md](MILESTONES.md),
which is authoritative where the two differ.

| Milestone | Outcome |
|---|---|
| M0 — Environment and scaffold | Empty Compose app on the emulator, under git, with the `INTERNET` check |
| D — Screen design (parallel to M0–M1a) | All screens in Stitch with the `high-end-visual-design` language; D1 before M1b, D2 before M2 |
| M1a — Walking skeleton and spikes | Hardcoded alarm rings end to end; step-sensor and restart spikes decided |
| M1b — Store, scheduling, reconciliation | Persisted alarms, scheduling and reconciliation with tests |
| M1c — Reliability matrix | Every engine failure mode in §4 proven on the emulator |
| M2 — Alarm management UI | List, editor, settings, onboarding, health screen; ES/EN strings |
| M3 — Ringing screen and missions | Math, shake and steps missions; no-bypass rules |
| M4 — Hardening and release | Signed release APK, verified on the emulator |
| DV — Device validation | S25 Ultra (adb) or a hand-installed APK: walking, One UI, real calls; then a dogfood week with Alarmy as backup |

### Later (optional, only if you want them)
- Home-screen widget showing the next alarm (Glance).
- Another mission type (for example, typing a phrase or a memory sequence).
- Export and import alarms as a JSON file. This is a local file, so it needs no network.

---

## 8. Testing strategy

- **JUnit (pure Kotlin)**
  - `NextTrigger` with a fixed `Clock` covers one-shot, weekdays, "today already passed", all-days,
    and DST gaps and overlaps in at least two time zones.
  - Reconciliation with a fixed `Clock`: re-ring inside the window, "missed" outside it, one-shot
    disabled and never rolled to tomorrow, `ActiveRing` and `snoozedUntil` honored.
  - `AlarmStore` serialization round-trip, and a corrupt file falls back safely.
  - Math generator: answer bounds per difficulty, no negatives, deterministic with a seed.
  - Shake detector: counts, debounce, thresholds on recorded accelerometer traces.
  - Step cadence filter: accepts a walking cadence, rejects bursts, and computes counter deltas.
- **Compose UI tests**: editor validation, mission screens driven by fake controller state.
- **Emulator matrix** (M1c, re-run in M4; driven by the agent through `adb`, ADR-023):
  - App swiped, Doze, reboot locked, reboot unlocked.
  - Screen locked vs in use.
  - Alarm volume 0, DND on, during a call.
  - Two alarms at the same minute, invalid sound URI.
  - Shake through `adb emu sensor`; steps through emulator sensors or a debug-only fake source.

  Real walking, One UI behavior and real calls can't be proven on the emulator: they run in **DV**
  (MILESTONES.md) on the S25 Ultra or with a hand-installed APK.

---

## 9. Platform facts this plan relies on

Verified against the Android developer docs on 2026-11-17. Re-check them at M1, and check any newer
Android release notes then.

- **`systemExempted` foreground-service type.** Allowed for "apps holding `SCHEDULE_EXACT_ALARM` or
  `USE_EXACT_ALARM` permission". Without one of them, declaring the type throws
  `ForegroundServiceTypeNotAllowedException`.
- **Android 17 background audio hardening.** For apps targeting API 37, the foreground service needs
  while-in-use capabilities *unless* the app has the exact-alarm permission and plays `USAGE_ALARM`
  audio.
- **`BOOT_COMPLETED` restrictions (Android 15+)** apply to starting some FGS types such as
  `mediaPlayback` and `dataSync` from boot. Chrona only **reschedules** on boot and never starts an
  FGS there.
- **Step sensors** need the `ACTIVITY_RECOGNITION` permission on Android 10 (API 29) and higher.
  - Step detector: one event per step, latency expected below 2 s.
  - Step counter: steps since the last reboot, up to 10 s of latency but more accurate.
- **Accelerometer** is rate-limited for apps targeting Android 12+.

**From prior knowledge, not re-verified today. Confirm at M1:**
- `USE_EXACT_ALARM` is auto-granted but restricted by Play policy to alarm, clock and calendar apps,
  which Chrona is. `SCHEDULE_EXACT_ALARM` is user-revocable and denied by default for new installs
  on Android 14+.
- `USE_FULL_SCREEN_INTENT` is restricted on Android 14+ to calling and alarm apps. On Play it is
  auto-granted to alarm apps. When sideloaded, check `canUseFullScreenIntent()` and deep-link to the
  setting if needed.
- When the device is unlocked and in use, a full-screen intent is shown as a heads-up notification.
- A foreground service keeps step-sensor events flowing with the screen off. This is checked by the
  M1 sensor spike.
- The manifest permission list in §4: that `USE_EXACT_ALARM` alone is enough at `minSdk` 34, and the
  API levels in §10 decision 2.
- Whether reading DND's "allow alarms" setting needs notification-policy access.

---

## 10. Decisions

Resolved on 2026-11-17.

| # | Question | Decision |
|---|---|---|
| 1 | Distribution | **Sideloaded APK only** for now. Avoid Play-incompatible choices where it costs nothing. |
| 2 | Target device | **Samsung Galaxy S25 Ultra, Android 17 (API 37).** `minSdk` **34**: every API-gated piece (`systemExempted`, `canUseFullScreenIntent()`, `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`) is available at 34, so there are no per-API branches. `targetSdk` and `compileSdk` are 37. Development and testing run on an Android 17 emulator; the S25 Ultra (or a hand-installed APK) is validated at the end in DV (ADR-023). |
| 3 | Application ID | **`com.nutabe.chrona`** |
| 4 | UI languages | **Spanish and English.** |
| 5 | After auto-silence | **Re-ring as a forced snooze, capped at 3 cycles**, then stop and report "missed". |
