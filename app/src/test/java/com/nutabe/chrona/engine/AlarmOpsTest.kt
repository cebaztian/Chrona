package com.nutabe.chrona.engine

import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmOpsTest {
    private val ops = AlarmOps(NextTrigger(Clock.system(ZoneId.of("America/New_York"))))

    // Tuesday 2026-11-17 08:00 in New York.
    private val now: Instant = Instant.parse("2026-11-17T13:00:00Z")
    private val tomorrow7: Instant = Instant.parse("2026-11-18T12:00:00Z")

    private fun applied(outcome: AlarmOutcome) = outcome as AlarmOutcome.Applied

    @Test
    fun creatingAssignsAnIdSchedulesTheNextTriggerAndStoresIt() {
        val result = applied(ops.save(StoreData(), Alarm(hour = 7, minute = 0), now))
        assertEquals(1L, result.alarm.id)
        assertEquals(tomorrow7, result.alarm.nextTriggerAt)
        assertEquals(listOf<AlarmEffect>(AlarmEffect.Schedule(1, TriggerKind.NEXT, tomorrow7)), result.effects)
        assertEquals(listOf(result.alarm), result.data.alarms)
    }

    @Test
    fun creatingADisabledAlarmSchedulesNothing() {
        val result = applied(ops.save(StoreData(), Alarm(enabled = false), now))
        assertEquals(null, result.alarm.nextTriggerAt)
        assertEquals(listOf<AlarmEffect>(AlarmEffect.Cancel(1, TriggerKind.NEXT)), result.effects)
    }

    @Test
    fun idsKeepGrowingAfterADelete() {
        val first = applied(ops.save(StoreData(), Alarm(), now)).data
        val second = applied(ops.save(first, Alarm(), now)).data
        val afterDelete = applied(ops.delete(second, 2)).data
        val third = applied(ops.save(afterDelete, Alarm(), now))
        assertEquals(3L, third.alarm.id)
    }

    @Test
    fun deletingTheHighestIdOfAnOldFileStillRetiresIt() {
        val legacy = StoreData(alarms = listOf(Alarm(id = 1), Alarm(id = 2), Alarm(id = 3)), nextAlarmId = 1)
        val afterDelete = applied(ops.delete(legacy, 3)).data
        assertEquals(4L, applied(ops.save(afterDelete, Alarm(), now)).alarm.id)
    }

    @Test
    fun undoRestoresTheDeletedAlarmUnderItsOwnIdEvenAfterAnotherWasCreated() {
        val one = applied(ops.save(StoreData(), Alarm(hour = 9), now)).data
        val deleted = applied(ops.delete(one, 1))
        val another = applied(ops.save(deleted.data, Alarm(hour = 10), now))
        val restored = applied(ops.restore(another.data, deleted.alarm, now))
        assertEquals(listOf(2L, 1L), restored.data.alarms.map { it.id })
        assertEquals(2, restored.data.alarms.map { it.id }.toSet().size)
    }

    @Test
    fun restoringOverAnExistingIdIsAConflict() {
        val data = StoreData(alarms = listOf(Alarm(id = 1)))
        assertEquals(AlarmOutcome.Conflict, ops.restore(data, Alarm(id = 1), now))
    }

    @Test
    fun deleteCancelsEveryKindOfPendingIntent() {
        val data = StoreData(alarms = listOf(Alarm(id = 4)))
        val result = applied(ops.delete(data, 4))
        assertEquals(TriggerKind.entries.map { AlarmEffect.Cancel(4, it) }, result.effects)
        assertTrue(result.data.alarms.isEmpty())
    }

    @Test
    fun editingTheTimeRecomputesAndReschedules() {
        val stored = applied(ops.save(StoreData(), Alarm(hour = 7), now)).data
        val edited = applied(ops.save(stored, stored.alarms.single().copy(hour = 9), now))
        assertEquals(Instant.parse("2026-11-17T14:00:00Z"), edited.alarm.nextTriggerAt)
        assertEquals(listOf<AlarmEffect>(AlarmEffect.Schedule(1, TriggerKind.NEXT, Instant.parse("2026-11-17T14:00:00Z"))), edited.effects)
    }

    @Test
    fun disablingCancelsTheNextTriggerAndClearsIt() {
        val stored = applied(ops.save(StoreData(), Alarm(), now)).data
        val off = applied(ops.setEnabled(stored, 1, false, now))
        assertFalse(off.alarm.enabled)
        assertEquals(null, off.alarm.nextTriggerAt)
        assertEquals(listOf<AlarmEffect>(AlarmEffect.Cancel(1, TriggerKind.NEXT)), off.effects)
    }

    @Test
    fun enablingAgainSchedulesTheNextOccurrence() {
        val stored = StoreData(alarms = listOf(Alarm(id = 1, hour = 7, enabled = false)))
        val on = applied(ops.setEnabled(stored, 1, true, now))
        assertEquals(tomorrow7, on.alarm.nextTriggerAt)
    }

    @Test
    fun aRingingAlarmCannotBeToggledEditedOrDeleted() {
        val data = StoreData(alarms = listOf(Alarm(id = 1)), activeRing = ActiveRing(1, now))
        assertEquals(AlarmOutcome.Locked, ops.setEnabled(data, 1, false, now))
        assertEquals(AlarmOutcome.Locked, ops.save(data, Alarm(id = 1, hour = 9), now))
        assertEquals(AlarmOutcome.Locked, ops.delete(data, 1))
    }

    @Test
    fun aSnoozedAlarmCannotBeToggledEditedOrDeleted() {
        val data = StoreData(alarms = listOf(Alarm(id = 1, snoozedUntil = now + Duration.ofMinutes(5))))
        assertEquals(AlarmOutcome.Locked, ops.setEnabled(data, 1, false, now))
        assertEquals(AlarmOutcome.Locked, ops.save(data, Alarm(id = 1, hour = 9), now))
        assertEquals(AlarmOutcome.Locked, ops.delete(data, 1))
    }

    @Test
    fun otherAlarmsStayEditableWhileOneRings() {
        val data = StoreData(alarms = listOf(Alarm(id = 1), Alarm(id = 2)), activeRing = ActiveRing(1, now))
        assertTrue(ops.setEnabled(data, 2, false, now) is AlarmOutcome.Applied)
    }

    @Test
    fun unknownIdsAreNotFound() {
        assertEquals(AlarmOutcome.NotFound, ops.setEnabled(StoreData(), 9, true, now))
        assertEquals(AlarmOutcome.NotFound, ops.delete(StoreData(), 9))
        assertEquals(AlarmOutcome.NotFound, ops.save(StoreData(), Alarm(id = 9), now))
    }

    @Test
    fun aRepeatingAlarmIsScheduledOnItsNextMatchingDay() {
        val result = applied(ops.save(StoreData(), Alarm(hour = 7, repeatDays = setOf(DayOfWeek.FRIDAY)), now))
        assertEquals(Instant.parse("2026-11-20T12:00:00Z"), result.alarm.nextTriggerAt)
    }
}
