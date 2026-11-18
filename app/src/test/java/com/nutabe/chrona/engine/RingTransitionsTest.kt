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

    private val two = StoreData(alarms = listOf(Alarm(id = 1, nextTriggerAt = now), Alarm(id = 2, nextTriggerAt = now)))

    @Test
    fun aSecondAlarmWaitsInTheQueueWhileAnotherRings() {
        val ringing = transitions.fired(two, 1, now)
        val queued = transitions.fired(ringing, 2, now)
        assertEquals(1L, queued.activeRing?.alarmId)
        assertEquals(listOf(2L), queued.ringQueue)
    }

    @Test
    fun aQueuedRepeatingAlarmStillMovesToItsNextOccurrence() {
        val repeating = StoreData(
            alarms = listOf(Alarm(id = 1, nextTriggerAt = now), Alarm(id = 2, repeatDays = daily, nextTriggerAt = now)),
        )
        val queued = transitions.fired(transitions.fired(repeating, 1, now), 2, now)
        assertTrue(queued.alarms.single { it.id == 2L }.nextTriggerAt!! > now)
    }

    @Test
    fun theSameAlarmFiringTwiceIsNotQueuedBehindItself() {
        val result = transitions.fired(transitions.fired(two, 1, now), 1, now)
        assertTrue(result.ringQueue.isEmpty())
    }

    @Test
    fun aQueuedAlarmIsQueuedOnlyOnce() {
        val result = transitions.fired(transitions.fired(transitions.fired(two, 1, now), 2, now), 2, now)
        assertEquals(listOf(2L), result.ringQueue)
    }

    @Test
    fun theQueuedAlarmRingsOnceTheCurrentOneEnds() {
        val queued = transitions.fired(transitions.fired(two, 1, now), 2, now)
        assertNull(transitions.nextQueued(queued))
        val done = transitions.completed(queued, 1)
        assertEquals(2L, transitions.nextQueued(done))
        val ringing = transitions.fired(done, 2, now)
        assertEquals(2L, ringing.activeRing?.alarmId)
        assertTrue(ringing.ringQueue.isEmpty())
    }

    @Test
    fun aQueueEntryForADeletedAlarmIsSkipped() {
        val data = StoreData(alarms = listOf(Alarm(id = 3)), ringQueue = listOf(9L, 3L))
        assertEquals(3L, transitions.nextQueued(data))
    }

    @Test
    fun snoozingTheCurrentAlarmAlsoLetsTheQueuedOneRing() {
        val queued = transitions.fired(transitions.fired(two, 1, now), 2, now)
        val snoozed = transitions.snoozed(queued, 1, now)!!.data
        assertEquals(2L, transitions.nextQueued(snoozed))
    }

    @Test
    fun theFirstAutoSilenceIsAForcedSnoozeThatKeepsTheSnoozeAllowance() {
        val ringing = transitions.fired(store(Alarm(id = 1, snoozesUsed = 1)), 1, now)
        val result = transitions.autoSilenced(ringing, 1, now) as AutoSilence.Reschedule
        val at = now + RingingPolicy.FORCED_SNOOZE_GAP
        assertEquals(at, result.at)
        assertEquals(ActiveRing(1, at, 1), result.data.activeRing)
        assertEquals(at, result.data.alarms.single().snoozedUntil)
        assertEquals(1, result.data.alarms.single().snoozesUsed)
    }

    @Test
    fun theForcedSnoozeCountSurvivesTheNextRing() {
        val ringing = transitions.fired(store(Alarm(id = 1)), 1, now)
        val forced = (transitions.autoSilenced(ringing, 1, now) as AutoSilence.Reschedule).data
        val again = transitions.fired(forced, 1, now + RingingPolicy.FORCED_SNOOZE_GAP)
        assertEquals(1, again.activeRing?.forcedSnoozeCycles)
        assertNull(again.alarms.single().snoozedUntil)
    }

    @Test
    fun afterThreeForcedSnoozesTheAlarmIsMissed() {
        var data = transitions.fired(store(Alarm(id = 1, repeatDays = daily, nextTriggerAt = now, snoozesUsed = 2)), 1, now)
        repeat(RingingPolicy.MAX_FORCED_CYCLES) {
            val forced = transitions.autoSilenced(data, 1, now) as AutoSilence.Reschedule
            data = transitions.fired(forced.data, 1, forced.at)
        }
        val missed = transitions.autoSilenced(data, 1, now) as AutoSilence.Missed
        assertNull(missed.data.activeRing)
        assertNull(missed.data.alarms.single().snoozedUntil)
        assertEquals(0, missed.data.alarms.single().snoozesUsed)
        assertTrue(missed.data.alarms.single().enabled)
    }

    @Test
    fun aMissedOneShotIsDisabled() {
        val ring = ActiveRing(1, now, RingingPolicy.MAX_FORCED_CYCLES)
        val missed = transitions.autoSilenced(store(Alarm(id = 1), ring), 1, now) as AutoSilence.Missed
        assertFalse(missed.data.alarms.single().enabled)
    }

    @Test
    fun aRingWithoutAMissionMayStop() {
        assertTrue(StopGate.allows(Mission.None, missionDone = false))
    }

    @Test
    fun aRingWithAnUnsolvedMissionMayNotStop() {
        for (mission in listOf(Mission.Math(), Mission.Shake(), Mission.Steps())) {
            assertFalse(StopGate.allows(mission, missionDone = false))
            assertTrue(StopGate.allows(mission, missionDone = true))
        }
    }
}
