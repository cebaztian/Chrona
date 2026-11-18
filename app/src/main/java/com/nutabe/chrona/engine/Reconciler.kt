package com.nutabe.chrona.engine

import java.time.Duration
import java.time.Instant

/** The four `PendingIntent` identities per alarm, so a snooze never replaces the next occurrence. */
enum class TriggerKind { NEXT, SNOOZE, RING_NOW, BACKUP }

object RingingPolicy {
    /** Forced-snooze window of the auto-silence and the grace period for a never-fired trigger. */
    val AUTO_SILENCE: Duration = Duration.ofMinutes(15)

    /** "Ring now" goes through `setAlarmClock` this far ahead so the normal receiver path runs. */
    val RING_NOW_DELAY: Duration = Duration.ofSeconds(3)

    /** Forced snoozes the auto-silence may chain before the alarm is reported as missed (ADR-015). */
    const val MAX_FORCED_CYCLES = 3

    /** Quiet gap between a forced snooze and the next ring. */
    val FORCED_SNOOZE_GAP: Duration = Duration.ofMinutes(1)
}

sealed interface ReconcileAction {
    /**
     * Hand [at] to `AlarmManager` for [kind]. For [TriggerKind.NEXT] the executor also stores it as
     * the alarm's `nextTriggerAt`.
     */
    data class Schedule(val alarmId: Long, val kind: TriggerKind, val at: Instant) : ReconcileAction

    data class PostMissed(val alarmId: Long) : ReconcileAction

    data class Disable(val alarmId: Long) : ReconcileAction
}

/** Pure: turns the persisted state and the current time into the actions that restore the schedule. */
class Reconciler(private val nextTrigger: NextTrigger) {
    /**
     * @param liveRingAlarmId the alarm this process is ringing right now, if any. A trigger for it
     * is a no-op, never a queued second ring.
     * @param recomputeRepeating true after a clock or time-zone change: a repeating alarm's next
     * trigger is recomputed from its local time instead of being rescheduled unchanged. A one-shot
     * keeps its absolute instant.
     */
    fun reconcile(
        data: StoreData,
        now: Instant,
        liveRingAlarmId: Long? = null,
        recomputeRepeating: Boolean = false,
    ): List<ReconcileAction> {
        val actions = mutableListOf<ReconcileAction>()
        for (alarm in data.alarms) {
            when {
                data.activeRing?.alarmId == alarm.id && !inForcedSnooze(alarm, now) -> {
                    if (alarm.id != liveRingAlarmId) actions += ringNow(alarm, now)
                    if (alarm.enabled && !alarm.isOneShot) actions += nextOccurrence(alarm, now, recomputeRepeating)
                }
                alarm.snoozedUntil != null -> {
                    val due = alarm.snoozedUntil
                    actions += if (due <= now) ringNow(alarm, now) else ReconcileAction.Schedule(alarm.id, TriggerKind.SNOOZE, due)
                    if (alarm.enabled && !alarm.isOneShot) actions += nextOccurrence(alarm, now, recomputeRepeating)
                }
                !alarm.enabled -> Unit
                else -> actions += reconcileNever(alarm, now, recomputeRepeating)
            }
        }
        return actions
    }

    private fun reconcileNever(alarm: Alarm, now: Instant, recomputeRepeating: Boolean): List<ReconcileAction> {
        val trigger = alarm.nextTriggerAt
            ?: return listOf(ReconcileAction.Schedule(alarm.id, TriggerKind.NEXT, nextTrigger.next(alarm, now)))
        if (recomputeRepeating && !alarm.isOneShot) {
            return listOf(ReconcileAction.Schedule(alarm.id, TriggerKind.NEXT, nextTrigger.next(alarm, now)))
        }
        if (trigger > now) return listOf(ReconcileAction.Schedule(alarm.id, TriggerKind.NEXT, trigger))
        if (Duration.between(trigger, now) < RingingPolicy.AUTO_SILENCE) return listOf(ringNow(alarm, now))
        val missed = ReconcileAction.PostMissed(alarm.id)
        return if (alarm.isOneShot) {
            listOf(missed, ReconcileAction.Disable(alarm.id))
        } else {
            listOf(missed, ReconcileAction.Schedule(alarm.id, TriggerKind.NEXT, nextTrigger.next(alarm, now)))
        }
    }

    private fun inForcedSnooze(alarm: Alarm, now: Instant) = alarm.snoozedUntil?.let { it > now } == true

    private fun ringNow(alarm: Alarm, now: Instant) =
        ReconcileAction.Schedule(alarm.id, TriggerKind.RING_NOW, now + RingingPolicy.RING_NOW_DELAY)

    private fun nextOccurrence(alarm: Alarm, now: Instant, recompute: Boolean) = ReconcileAction.Schedule(
        alarm.id,
        TriggerKind.NEXT,
        alarm.nextTriggerAt?.takeIf { !recompute && it > now } ?: nextTrigger.next(alarm, now),
    )
}
