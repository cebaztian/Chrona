package com.nutabe.chrona.engine

import java.time.Duration
import java.time.Instant

/** Pure state changes for the life of one ring, so they are unit tested without Android. */
class RingTransitions(private val nextTrigger: NextTrigger) {
    /**
     * An alarm fired (or a ring resumed): records the [ActiveRing], keeps the original start of a ring
     * already in progress, clears a due snooze and moves a repeating alarm to its next occurrence.
     * While another alarm rings, this one waits in [StoreData.ringQueue] instead.
     */
    fun fired(data: StoreData, alarmId: Long, now: Instant): StoreData {
        val existing = data.activeRing
        val takesTheRing = existing == null || existing.alarmId == alarmId
        val alarms = data.alarms.map { alarm ->
            if (alarm.id != alarmId) {
                alarm
            } else {
                val trigger = alarm.nextTriggerAt
                val advance = alarm.enabled && !alarm.isOneShot && (trigger == null || trigger <= now)
                alarm.copy(
                    snoozedUntil = null,
                    nextTriggerAt = if (advance) nextTrigger.next(alarm, now) else trigger,
                )
            }
        }
        return if (takesTheRing) {
            data.copy(
                activeRing = ActiveRing(alarmId, existing?.startedAt ?: now, existing?.forcedSnoozeCycles ?: 0),
                alarms = alarms,
                ringQueue = data.ringQueue - alarmId,
            )
        } else {
            data.copy(alarms = alarms, ringQueue = (data.ringQueue + alarmId).distinct())
        }
    }

    /** The alarm that should ring next once nothing rings: the first queued one that still exists. */
    fun nextQueued(data: StoreData): Long? =
        if (data.activeRing != null) null else data.ringQueue.firstOrNull { id -> data.alarms.any { it.id == id } }

    /** The mission was solved: clears the ring and the snooze count, and disables a one-shot. */
    fun completed(data: StoreData, alarmId: Long): StoreData = finished(data, alarmId)

    /** Snoozes the ring, or returns null when the alarm is unknown or has no snoozes left. */
    fun snoozed(data: StoreData, alarmId: Long, now: Instant): Result? {
        val alarm = data.alarms.firstOrNull { it.id == alarmId } ?: return null
        if (!alarm.snooze.enabled || alarm.snoozesUsed >= alarm.snooze.maxCount) return null
        val until = now + Duration.ofMinutes(alarm.snooze.minutes.toLong())
        val updated = data.copy(
            activeRing = data.activeRing?.takeUnless { it.alarmId == alarmId },
            alarms = data.alarms.map {
                if (it.id == alarmId) it.copy(snoozedUntil = until, snoozesUsed = it.snoozesUsed + 1) else it
            },
        )
        return Result(updated, until)
    }

    /**
     * The ring ran for the auto-silence limit (ADR-015). It re-rings after a short gap as a forced snooze,
     * which does not use the user's snooze allowance, until [RingingPolicy.MAX_FORCED_CYCLES] is spent.
     */
    fun autoSilenced(data: StoreData, alarmId: Long, now: Instant): AutoSilence {
        val cycles = data.activeRing?.takeIf { it.alarmId == alarmId }?.forcedSnoozeCycles ?: 0
        if (cycles >= RingingPolicy.MAX_FORCED_CYCLES) return AutoSilence.Missed(finished(data, alarmId))
        val at = now + RingingPolicy.FORCED_SNOOZE_GAP
        val updated = data.copy(
            activeRing = ActiveRing(alarmId, at, cycles + 1),
            alarms = data.alarms.map { if (it.id == alarmId) it.copy(snoozedUntil = at) else it },
        )
        return AutoSilence.Reschedule(updated, at)
    }

    private fun finished(data: StoreData, alarmId: Long): StoreData = data.copy(
        activeRing = data.activeRing?.takeUnless { it.alarmId == alarmId },
        ringQueue = data.ringQueue - alarmId,
        alarms = data.alarms.map { alarm ->
            when {
                alarm.id != alarmId -> alarm
                alarm.isOneShot -> alarm.copy(enabled = false, nextTriggerAt = null, snoozedUntil = null, snoozesUsed = 0)
                else -> alarm.copy(snoozedUntil = null, snoozesUsed = 0)
            }
        },
    )

    data class Result(val data: StoreData, val snoozedUntil: Instant)
}

sealed interface AutoSilence {
    val data: StoreData

    data class Reschedule(override val data: StoreData, val at: Instant) : AutoSilence

    data class Missed(override val data: StoreData) : AutoSilence
}

/** Decides whether a fired `PendingIntent` still matches the persisted state, so a stale one never rings. */
object FireGuard {
    fun isValid(kind: TriggerKind, alarmId: Long, alarm: Alarm?, activeRing: ActiveRing?, now: Instant): Boolean =
        when (kind) {
            TriggerKind.BACKUP -> activeRing?.alarmId == alarmId
            TriggerKind.SNOOZE -> alarm?.snoozedUntil != null
            // A deleted or disabled alarm must never ring, even if one of its intents was left behind.
            TriggerKind.NEXT -> alarm != null && alarm.enabled
            TriggerKind.RING_NOW -> activeRing?.alarmId == alarmId ||
                (alarm != null && (alarm.snoozedUntil != null || dueByTrigger(alarm, now)))
        }

    private fun dueByTrigger(alarm: Alarm, now: Instant): Boolean =
        alarm.enabled && alarm.nextTriggerAt?.let { it <= now + RingingPolicy.RING_NOW_DELAY } == true
}

/** The only rule that lets a ring stop: no mission, or the mission is solved. Notifications never stop it. */
object StopGate {
    fun allows(mission: Mission, missionDone: Boolean): Boolean = mission == Mission.None || missionDone
}
