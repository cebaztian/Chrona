package com.nutabe.chrona.engine

import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingTransitionsTest {
    private val transitions = RingTransitions(NextTrigger(Clock.system(ZoneId.of("America/New_York"))))
    private val now: Instant = Instant.parse("2026-11-17T13:00:00Z")
    private val daily = DayOfWeek.entries.toSet()

    private fun store(alarm: Alarm, ring: ActiveRing? = null) = StoreData(alarms = listOf(alarm), activeRing = ring)

    @Test
    fun firedRecordsTheActiveRing() {
        val result = transitions.fired(store(Alarm(id = 1, nextTriggerAt = now)), 1, now)
        assertEquals(ActiveRing(1, now, 0), result.activeRing)
    }

    @Test
    fun firedKeepsTheStartOfARingAlreadyInProgress() {
        val started = now - Duration.ofMinutes(3)
        val result = transitions.fired(store(Alarm(id = 1), ActiveRing(1, started, 2)), 1, now)
        assertEquals(ActiveRing(1, started, 2), result.activeRing)
    }

    @Test
    fun firedClearsADueSnooze() {
        val result = transitions.fired(store(Alarm(id = 1, snoozedUntil = now, snoozesUsed = 1)), 1, now)
        assertNull(result.alarms.single().snoozedUntil)
        assertEquals(1, result.alarms.single().snoozesUsed)
    }

    @Test
    fun firedMovesARepeatingAlarmToItsNextOccurrence() {
        val result = transitions.fired(store(Alarm(id = 1, hour = 8, repeatDays = daily, nextTriggerAt = now)), 1, now)
        assertEquals(Instant.parse("2026-11-18T13:00:00Z"), result.alarms.single().nextTriggerAt)
    }

    @Test
    fun firedLeavesAFutureTriggerOfARepeatingAlarmAlone() {
        val future = now + Duration.ofHours(20)
        val result = transitions.fired(store(Alarm(id = 1, repeatDays = daily, nextTriggerAt = future)), 1, now)
        assertEquals(future, result.alarms.single().nextTriggerAt)
    }

    @Test
    fun firedLeavesAOneShotTriggerInPlace() {
        val result = transitions.fired(store(Alarm(id = 1, nextTriggerAt = now)), 1, now)
        assertEquals(now, result.alarms.single().nextTriggerAt)
        assertTrue(result.alarms.single().enabled)
    }

    @Test
    fun completedDisablesAOneShotAndClearsTheRing() {
        val alarm = Alarm(id = 1, nextTriggerAt = now, snoozesUsed = 2)
        val result = transitions.completed(store(alarm, ActiveRing(1, now)), 1)
        assertNull(result.activeRing)
        assertFalse(result.alarms.single().enabled)
        assertNull(result.alarms.single().nextTriggerAt)
        assertEquals(0, result.alarms.single().snoozesUsed)
    }

    @Test
    fun completedKeepsARepeatingAlarmEnabled() {
        val next = now + Duration.ofHours(20)
        val alarm = Alarm(id = 1, repeatDays = daily, nextTriggerAt = next, snoozesUsed = 1)
        val result = transitions.completed(store(alarm, ActiveRing(1, now)), 1)
        assertTrue(result.alarms.single().enabled)
        assertEquals(next, result.alarms.single().nextTriggerAt)
        assertEquals(0, result.alarms.single().snoozesUsed)
    }

    @Test
    fun snoozeSetsTheDeadlineCountsItAndClearsTheRing() {
        val alarm = Alarm(id = 1, snooze = SnoozeConfig(true, 7, 3))
        val result = transitions.snoozed(store(alarm, ActiveRing(1, now)), 1, now)!!
        assertEquals(now + Duration.ofMinutes(7), result.snoozedUntil)
        assertEquals(result.snoozedUntil, result.data.alarms.single().snoozedUntil)
        assertEquals(1, result.data.alarms.single().snoozesUsed)
        assertNull(result.data.activeRing)
    }

    @Test
    fun snoozeIsRefusedWhenExhaustedDisabledOrUnknown() {
        assertNull(transitions.snoozed(store(Alarm(id = 1, snooze = SnoozeConfig(true, 5, 2), snoozesUsed = 2)), 1, now))
        assertNull(transitions.snoozed(store(Alarm(id = 1, snooze = SnoozeConfig(false, 5, 2))), 1, now))
        assertNull(transitions.snoozed(store(Alarm(id = 1)), 99, now))
    }

    @Test
    fun lastAllowedSnoozeStillWorks() {
        val alarm = Alarm(id = 1, snooze = SnoozeConfig(true, 5, 3), snoozesUsed = 2)
        assertNotNull(transitions.snoozed(store(alarm), 1, now))
    }

    @Test
    fun backupFireIsValidOnlyWhileThatRingIsActive() {
        val ring = ActiveRing(1, now)
        assertTrue(FireGuard.isValid(TriggerKind.BACKUP, 1, Alarm(id = 1), ring, now))
        assertFalse(FireGuard.isValid(TriggerKind.BACKUP, 1, Alarm(id = 1), null, now))
        assertFalse(FireGuard.isValid(TriggerKind.BACKUP, 2, Alarm(id = 2), ring, now))
    }

    @Test
    fun snoozeFireNeedsAPendingSnooze() {
        assertTrue(FireGuard.isValid(TriggerKind.SNOOZE, 1, Alarm(id = 1, snoozedUntil = now), null, now))
        assertFalse(FireGuard.isValid(TriggerKind.SNOOZE, 1, Alarm(id = 1), null, now))
        assertFalse(FireGuard.isValid(TriggerKind.SNOOZE, 1, null, null, now))
    }

    @Test
    fun nextFireNeedsAStoredEnabledAlarm() {
        assertTrue(FireGuard.isValid(TriggerKind.NEXT, 1, Alarm(id = 1), null, now))
        assertFalse(FireGuard.isValid(TriggerKind.NEXT, 1, Alarm(id = 1, enabled = false), null, now))
        assertFalse(FireGuard.isValid(TriggerKind.NEXT, 1, null, null, now))
    }

    @Test
    fun ringNowFireIsValidForALiveRingADueSnoozeOrADueTrigger() {
        assertTrue(FireGuard.isValid(TriggerKind.RING_NOW, 1, Alarm(id = 1), ActiveRing(1, now), now))
        assertTrue(FireGuard.isValid(TriggerKind.RING_NOW, 1, Alarm(id = 1, snoozedUntil = now), null, now))
        assertTrue(FireGuard.isValid(TriggerKind.RING_NOW, 1, Alarm(id = 1, nextTriggerAt = now - Duration.ofMinutes(2)), null, now))
    }

    @Test
    fun staleRingNowFireIsIgnoredAfterTheAlarmWasCompleted() {
        val completed = Alarm(id = 1, enabled = false)
        assertFalse(FireGuard.isValid(TriggerKind.RING_NOW, 1, completed, null, now))
        assertFalse(FireGuard.isValid(TriggerKind.RING_NOW, 1, Alarm(id = 1, nextTriggerAt = now + Duration.ofHours(5)), null, now))
        assertFalse(FireGuard.isValid(TriggerKind.RING_NOW, 1, null, null, now))
    }
}
