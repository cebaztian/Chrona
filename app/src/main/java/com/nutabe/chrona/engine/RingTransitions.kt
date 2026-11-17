package com.nutabe.chrona.engine

import java.time.Duration
import java.time.Instant

/** Pure state changes for the life of one ring, so they are unit tested without Android. */
class RingTransitions(private val nextTrigger: NextTrigger) {
    /**
     * An alarm fired (or a ring resumed): records the [ActiveRing], keeps the original start of a ring
     * already in progress, clears a due snooze and moves a repeating alarm to its next occurrence.
     */
    fun fired(data: StoreData, alarmId: Long, now: Instant): StoreData {
        val existing = data.activeRing?.takeIf { it.alarmId == alarmId }
        return data.copy(
            activeRing = ActiveRing(alarmId, existing?.startedAt ?: now, existing?.forcedSnoozeCycles ?: 0),
            alarms = data.alarms.map { alarm ->
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
            },
        )
    }

    /** The mission was solved: clears the ring and the snooze count, and disables a one-shot. */
    fun completed(data: StoreData, alarmId: Long): StoreData = data.copy(
        activeRing = data.activeRing?.takeUnless { it.alarmId == alarmId },
        alarms = data.alarms.map { alarm ->
            when {
                alarm.id != alarmId -> alarm
                alarm.isOneShot -> alarm.copy(enabled = false, nextTriggerAt = null, snoozedUntil = null, snoozesUsed = 0)
                else -> alarm.copy(snoozedUntil = null, snoozesUsed = 0)
            }
        },
    )

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

    data class Result(val data: StoreData, val snoozedUntil: Instant)
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
