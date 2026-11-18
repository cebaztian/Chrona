package com.nutabe.chrona.engine

import java.time.Instant

/** What the repository must do on `AlarmManager` after a change has been stored. */
sealed interface AlarmEffect {
    data class Schedule(val alarmId: Long, val kind: TriggerKind, val at: Instant) : AlarmEffect

    data class Cancel(val alarmId: Long, val kind: TriggerKind) : AlarmEffect
}

sealed interface AlarmOutcome {
    /** [alarm] is the alarm as stored, or the deleted one for a delete. */
    data class Applied(val data: StoreData, val effects: List<AlarmEffect>, val alarm: Alarm) : AlarmOutcome

    /** ADR-016: a ringing or snoozed alarm cannot be toggled, edited or deleted. */
    data object Locked : AlarmOutcome

    data object NotFound : AlarmOutcome

    /** Restoring an alarm whose id is already in use. */
    data object Conflict : AlarmOutcome
}

/** Pure alarm mutations: the new state plus the scheduling effects it implies. */
class AlarmOps(private val nextTrigger: NextTrigger) {
    fun isLocked(data: StoreData, alarm: Alarm): Boolean =
        data.isRingBusy(alarm.id) || alarm.snoozedUntil != null

    /** Creates the alarm when `draft.id` is 0, otherwise edits it. */
    fun save(data: StoreData, draft: Alarm, now: Instant): AlarmOutcome {
        if (draft.id == 0L) {
            val (id, counted) = data.allocateId()
            val created = scheduled(draft.copy(id = id, snoozedUntil = null, snoozesUsed = 0), now)
            return AlarmOutcome.Applied(counted.copy(alarms = counted.alarms + created), effectsFor(created), created)
        }
        val existing = data.alarms.firstOrNull { it.id == draft.id } ?: return AlarmOutcome.NotFound
        if (isLocked(data, existing)) return AlarmOutcome.Locked
        val edited = scheduled(draft.copy(snoozedUntil = null, snoozesUsed = 0), now)
        return AlarmOutcome.Applied(data.replace(edited), effectsFor(edited), edited)
    }

    fun setEnabled(data: StoreData, id: Long, enabled: Boolean, now: Instant): AlarmOutcome {
        val existing = data.alarms.firstOrNull { it.id == id } ?: return AlarmOutcome.NotFound
        if (isLocked(data, existing)) return AlarmOutcome.Locked
        val changed = scheduled(existing.copy(enabled = enabled), now)
        return AlarmOutcome.Applied(data.replace(changed), effectsFor(changed), changed)
    }

    /** Removes the alarm and cancels every pending intent of it; the counter keeps its id retired. */
    fun delete(data: StoreData, id: Long): AlarmOutcome {
        val existing = data.alarms.firstOrNull { it.id == id } ?: return AlarmOutcome.NotFound
        if (isLocked(data, existing)) return AlarmOutcome.Locked
        val updated = data.copy(
            alarms = data.alarms.filterNot { it.id == id },
            nextAlarmId = maxOf(data.nextAlarmId, id + 1),
        )
        return AlarmOutcome.Applied(updated, TriggerKind.entries.map { AlarmEffect.Cancel(id, it) }, existing)
    }

    /** Undo of a delete: puts the alarm back under its own id. */
    fun restore(data: StoreData, alarm: Alarm, now: Instant): AlarmOutcome {
        if (data.alarms.any { it.id == alarm.id }) return AlarmOutcome.Conflict
        val restored = scheduled(alarm.copy(snoozedUntil = null, snoozesUsed = 0), now)
        return AlarmOutcome.Applied(data.copy(alarms = data.alarms + restored), effectsFor(restored), restored)
    }

    private fun scheduled(alarm: Alarm, now: Instant): Alarm =
        alarm.copy(nextTriggerAt = if (alarm.enabled) nextTrigger.next(alarm, now) else null)

    private fun effectsFor(alarm: Alarm): List<AlarmEffect> =
        alarm.nextTriggerAt?.let { listOf(AlarmEffect.Schedule(alarm.id, TriggerKind.NEXT, it)) }
            ?: listOf(AlarmEffect.Cancel(alarm.id, TriggerKind.NEXT))

    private fun StoreData.replace(alarm: Alarm) = copy(alarms = alarms.map { if (it.id == alarm.id) alarm else it })
}

enum class AlarmProblem { HOUR, MINUTE, LABEL_TOO_LONG, SNOOZE_MINUTES, SNOOZE_COUNT, VOLUME, RAMP, MISSION_COUNT }

/** Editor rules, kept out of the UI so they are unit tested. */
object AlarmValidation {
    const val MAX_LABEL = 40

    fun validate(alarm: Alarm): Set<AlarmProblem> = buildSet {
        if (alarm.hour !in 0..23) add(AlarmProblem.HOUR)
        if (alarm.minute !in 0..59) add(AlarmProblem.MINUTE)
        if (alarm.label.length > MAX_LABEL) add(AlarmProblem.LABEL_TOO_LONG)
        if (alarm.snooze.minutes !in 1..30) add(AlarmProblem.SNOOZE_MINUTES)
        if (alarm.snooze.maxCount !in 1..10) add(AlarmProblem.SNOOZE_COUNT)
        if (alarm.maxVolumePercent !in 10..100) add(AlarmProblem.VOLUME)
        if (alarm.volumeRampSeconds !in 0..300) add(AlarmProblem.RAMP)
        val countOk = when (val mission = alarm.mission) {
            Mission.None -> true
            is Mission.Math -> mission.count in 1..10
            is Mission.Shake -> mission.count in 5..100
            is Mission.Steps -> mission.count in 10..500
        }
        if (!countOk) add(AlarmProblem.MISSION_COUNT)
    }
}
