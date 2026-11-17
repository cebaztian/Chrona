package com.nutabe.chrona.ui.alarms

import com.nutabe.chrona.engine.ActiveRing
import com.nutabe.chrona.engine.Alarm
import com.nutabe.chrona.engine.NextTrigger
import com.nutabe.chrona.engine.StoreData
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmListStateTest {
    private val newYork = ZoneId.of("America/New_York")
    private val now: Instant = Instant.parse("2026-11-17T13:00:00Z")

    @Test
    fun ringsInSplitsDaysHoursAndMinutes() {
        assertEquals(RingsIn(0, 7, 12), ringsIn(now, now + Duration.ofMinutes(7 * 60 + 12)))
        assertEquals(RingsIn(2, 3, 5), ringsIn(now, now + Duration.ofMinutes(2 * 24 * 60 + 3 * 60 + 5)))
        assertEquals(RingsIn(0, 0, 45), ringsIn(now, now + Duration.ofMinutes(45)))
    }

    @Test
    fun underAMinuteIsAllZeros() {
        assertEquals(RingsIn(0, 0, 0), ringsIn(now, now + Duration.ofSeconds(40)))
    }

    @Test
    fun aPastOrCurrentInstantHasNothingLeft() {
        assertNull(ringsIn(now, now))
        assertNull(ringsIn(now, now - Duration.ofMinutes(1)))
    }

    @Test
    fun acrossMidnightItCountsRealTime() {
        val from = ZonedDateTime.of(2026, 11, 17, 22, 0, 0, 0, newYork).toInstant()
        val next = NextTrigger(Clock.system(newYork)).next(6, 0, emptySet(), from)
        assertEquals(RingsIn(0, 8, 0), ringsIn(from, next))
    }

    @Test
    fun acrossTheSpringForwardNightOneHourIsLost() {
        val from = ZonedDateTime.of(2026, 3, 7, 22, 0, 0, 0, newYork).toInstant()
        val next = NextTrigger(Clock.system(newYork)).next(6, 0, emptySet(), from)
        assertEquals(RingsIn(0, 7, 0), ringsIn(from, next))
    }

    @Test
    fun acrossTheFallBackNightOneHourIsGained() {
        val from = ZonedDateTime.of(2026, 10, 31, 22, 0, 0, 0, newYork).toInstant()
        val next = NextTrigger(Clock.system(newYork)).next(6, 0, emptySet(), from)
        assertEquals(RingsIn(0, 9, 0), ringsIn(from, next))
    }

    @Test
    fun repeatSummaryRecognisesTheCommonSets() {
        assertEquals(RepeatSummary.Once, repeatSummary(emptySet()))
        assertEquals(RepeatSummary.EveryDay, repeatSummary(DayOfWeek.entries.toSet()))
        assertEquals(RepeatSummary.Weekdays, repeatSummary(DayOfWeek.entries.take(5).toSet()))
        assertEquals(RepeatSummary.Weekends, repeatSummary(setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)))
    }

    @Test
    fun otherSetsAreListedMondayFirst() {
        assertEquals(
            RepeatSummary.Custom(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)),
            repeatSummary(setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)),
        )
    }

    @Test
    fun theListIsSortedByTimeThenId() {
        val data = StoreData(alarms = listOf(Alarm(id = 3, hour = 8), Alarm(id = 2, hour = 6, minute = 30), Alarm(id = 1, hour = 6, minute = 30)))
        assertEquals(listOf(1L, 2L, 3L), alarmListState(data, now).items.map { it.alarm.id })
    }

    @Test
    fun aRingingAlarmIsLockedAsRinging() {
        val data = StoreData(alarms = listOf(Alarm(id = 1)), activeRing = ActiveRing(1, now))
        assertEquals(LockReason.RINGING, alarmListState(data, now).items.single().lock)
    }

    @Test
    fun aSnoozedAlarmIsLockedAsSnoozed() {
        val data = StoreData(alarms = listOf(Alarm(id = 1, snoozedUntil = now + Duration.ofMinutes(5))))
        assertEquals(LockReason.SNOOZED, alarmListState(data, now).items.single().lock)
    }

    @Test
    fun aSnoozedAlarmShowsWhenTheSnoozeEndsInLocalTime() {
        val data = StoreData(alarms = listOf(Alarm(id = 1, snoozedUntil = Instant.parse("2026-11-17T11:39:00Z"))))
        assertEquals("06:39", alarmListState(data, now, newYork).items.single().snoozedUntilClock)
    }

    @Test
    fun otherAlarmsAreNotLocked() {
        val data = StoreData(alarms = listOf(Alarm(id = 1), Alarm(id = 2)), activeRing = ActiveRing(1, now))
        assertNull(alarmListState(data, now).items.first { it.alarm.id == 2L }.lock)
    }

    @Test
    fun theHeaderCountsTheNearestEnabledAlarm() {
        val data = StoreData(
            alarms = listOf(
                Alarm(id = 1, nextTriggerAt = now + Duration.ofHours(10)),
                Alarm(id = 2, nextTriggerAt = now + Duration.ofHours(3)),
                Alarm(id = 3, enabled = false, nextTriggerAt = now + Duration.ofHours(1)),
            ),
        )
        assertEquals(RingsIn(0, 3, 0), alarmListState(data, now).nextRingsIn)
    }

    @Test
    fun aSnoozeCountsAsTheNextRing() {
        val data = StoreData(
            alarms = listOf(Alarm(id = 1, snoozedUntil = now + Duration.ofMinutes(4), nextTriggerAt = now + Duration.ofHours(20))),
        )
        assertEquals(RingsIn(0, 0, 4), alarmListState(data, now).nextRingsIn)
    }

    @Test
    fun withoutEnabledAlarmsThereIsNoHeader() {
        assertNull(alarmListState(StoreData(alarms = listOf(Alarm(id = 1, enabled = false))), now).nextRingsIn)
        assertNull(alarmListState(StoreData(), now).nextRingsIn)
    }

    @Test
    fun clockIsTwentyFourHourWithLeadingZeros() {
        assertEquals("06:05", formatClock(6, 5))
        assertEquals("23:59", formatClock(23, 59))
    }
}
