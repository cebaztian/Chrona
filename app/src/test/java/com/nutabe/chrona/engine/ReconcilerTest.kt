package com.nutabe.chrona.engine

import com.nutabe.chrona.engine.ReconcileAction.Disable
import com.nutabe.chrona.engine.ReconcileAction.PostMissed
import com.nutabe.chrona.engine.ReconcileAction.Schedule
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcilerTest {
    private val zone = ZoneId.of("America/New_York")
    private val reconciler = Reconciler(NextTrigger(Clock.system(zone)))

    // Tuesday 2026-11-17 08:00 in New York.
    private val now: Instant = Instant.parse("2026-11-17T13:00:00Z")
    private val daily = DayOfWeek.entries.toSet()

    private fun store(vararg alarms: Alarm, activeRing: ActiveRing? = null) =
        StoreData(alarms = alarms.toList(), activeRing = activeRing)

    private fun ringNow(id: Long) = Schedule(id, TriggerKind.RING_NOW, now + RingingPolicy.RING_NOW_DELAY)

    private fun nextAfter(alarm: Alarm) = NextTrigger(Clock.system(zone)).next(alarm, now)

    @Test
    fun emptyStoreProducesNothing() {
        assertTrue(reconciler.reconcile(StoreData(), now).isEmpty())
    }

    @Test
    fun disabledAlarmIsIgnored() {
        val alarm = Alarm(id = 1, enabled = false, nextTriggerAt = now - Duration.ofHours(2))
        assertTrue(reconciler.reconcile(store(alarm), now).isEmpty())
    }

    @Test
    fun enabledAlarmWithoutATriggerGetsOne() {
        val alarm = Alarm(id = 1, hour = 9, minute = 0)
        assertEquals(listOf(Schedule(1, TriggerKind.NEXT, nextAfter(alarm))), reconciler.reconcile(store(alarm), now))
    }

    @Test
    fun futureTriggerIsRescheduledUnchanged() {
        val at = now + Duration.ofHours(3)
        val alarm = Alarm(id = 1, nextTriggerAt = at)
        assertEquals(listOf(Schedule(1, TriggerKind.NEXT, at)), reconciler.reconcile(store(alarm), now))
    }

    @Test
    fun neverFiredTriggerJustInsideTheWindowRingsNow() {
        val alarm = Alarm(id = 1, nextTriggerAt = now - RingingPolicy.AUTO_SILENCE + Duration.ofSeconds(1))
        assertEquals(listOf(ringNow(1)), reconciler.reconcile(store(alarm), now))
    }

    @Test
    fun neverFiredOneShotExactlyAtTheWindowIsMissedAndDisabled() {
        val alarm = Alarm(id = 1, nextTriggerAt = now - RingingPolicy.AUTO_SILENCE)
        assertEquals(listOf(PostMissed(1), Disable(1)), reconciler.reconcile(store(alarm), now))
    }

    @Test
    fun neverFiredRepeatingAlarmPastTheWindowIsMissedAndMovesToItsNextOccurrence() {
        val alarm = Alarm(id = 1, hour = 7, repeatDays = daily, nextTriggerAt = now - Duration.ofHours(1))
        assertEquals(
            listOf(PostMissed(1), Schedule(1, TriggerKind.NEXT, nextAfter(alarm))),
            reconciler.reconcile(store(alarm), now),
        )
    }

    @Test
    fun persistedActiveRingThatIsNotLiveRingsAgain() {
        val alarm = Alarm(id = 1, nextTriggerAt = now - Duration.ofMinutes(5))
        val ring = ActiveRing(alarmId = 1, startedAt = now - Duration.ofMinutes(5))
        assertEquals(listOf(ringNow(1)), reconciler.reconcile(store(alarm, activeRing = ring), now))
    }

    @Test
    fun activeRingAlwaysRingsAgainHoweverOld() {
        val alarm = Alarm(id = 1)
        val ring = ActiveRing(alarmId = 1, startedAt = now - Duration.ofDays(3))
        assertEquals(listOf(ringNow(1)), reconciler.reconcile(store(alarm, activeRing = ring), now))
    }

    @Test
    fun triggerForTheLiveRingIsANoOp() {
        val alarm = Alarm(id = 1)
        val ring = ActiveRing(alarmId = 1, startedAt = now - Duration.ofMinutes(1))
        assertTrue(reconciler.reconcile(store(alarm, activeRing = ring), now, liveRingAlarmId = 1).isEmpty())
    }

    @Test
    fun liveRingOfARepeatingAlarmStillKeepsItsNextOccurrence() {
        val next = now + Duration.ofHours(20)
        val alarm = Alarm(id = 1, repeatDays = daily, nextTriggerAt = next)
        val ring = ActiveRing(alarmId = 1, startedAt = now)
        assertEquals(
            listOf(Schedule(1, TriggerKind.NEXT, next)),
            reconciler.reconcile(store(alarm, activeRing = ring), now, liveRingAlarmId = 1),
        )
    }

    @Test
    fun repeatingAlarmRingingAfterRebootRingsAndKeepsItsNextOccurrence() {
        val next = now + Duration.ofHours(20)
        val alarm = Alarm(id = 1, repeatDays = daily, nextTriggerAt = next)
        val ring = ActiveRing(alarmId = 1, startedAt = now - Duration.ofMinutes(2))
        assertEquals(
            listOf(ringNow(1), Schedule(1, TriggerKind.NEXT, next)),
            reconciler.reconcile(store(alarm, activeRing = ring), now),
        )
    }

    @Test
    fun snoozeDueHoweverLongAgoRingsAgain() {
        val alarm = Alarm(id = 1, snoozedUntil = now - Duration.ofDays(1), nextTriggerAt = now - Duration.ofDays(1))
        assertEquals(listOf(ringNow(1)), reconciler.reconcile(store(alarm), now))
    }

    @Test
    fun snoozedOneShotWithAPastTriggerOnlySchedulesTheSnooze() {
        val due = now + Duration.ofMinutes(4)
        val alarm = Alarm(id = 1, snoozedUntil = due, nextTriggerAt = now - Duration.ofMinutes(5))
        assertEquals(listOf(Schedule(1, TriggerKind.SNOOZE, due)), reconciler.reconcile(store(alarm), now))
    }

    @Test
    fun snoozedRepeatingAlarmSchedulesTheSnoozeAndItsNextOccurrence() {
        val due = now + Duration.ofMinutes(4)
        val next = now + Duration.ofHours(23)
        val alarm = Alarm(id = 1, repeatDays = daily, snoozedUntil = due, nextTriggerAt = next)
        assertEquals(
            listOf(Schedule(1, TriggerKind.SNOOZE, due), Schedule(1, TriggerKind.NEXT, next)),
            reconciler.reconcile(store(alarm), now),
        )
    }

    @Test
    fun repeatingAlarmKeepsItsStoredLocalTimeAfterATimezoneChange() {
        // PLAN §2 reschedules nextTriggerAt unchanged; this documents that it is not recomputed (M1c).
        val stale = now + Duration.ofHours(6)
        val alarm = Alarm(id = 1, hour = 7, repeatDays = daily, nextTriggerAt = stale)
        assertEquals(listOf(Schedule(1, TriggerKind.NEXT, stale)), reconciler.reconcile(store(alarm), now))
    }

    @Test
    fun alarmsAreReconciledIndependently() {
        val first = Alarm(id = 1, nextTriggerAt = now + Duration.ofHours(1))
        val second = Alarm(id = 2, nextTriggerAt = now - Duration.ofHours(2))
        assertEquals(
            listOf(Schedule(1, TriggerKind.NEXT, first.nextTriggerAt!!), PostMissed(2), Disable(2)),
            reconciler.reconcile(store(first, second), now),
        )
    }
}
