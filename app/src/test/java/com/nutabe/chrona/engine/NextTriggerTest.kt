package com.nutabe.chrona.engine

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NextTriggerTest {
    private val newYork = ZoneId.of("America/New_York")
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun trigger(zone: ZoneId) = NextTrigger(Clock.system(zone))

    private fun at(zone: ZoneId, text: String): Instant = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone).toInstant()

    private fun local(zone: ZoneId, instant: Instant) = instant.atZone(zone).toLocalDateTime().toString()

    private fun next(zone: ZoneId, from: Instant, hour: Int, minute: Int, days: Set<DayOfWeek> = emptySet()) =
        trigger(zone).next(hour, minute, days, from)

    @Test
    fun oneShotLaterTodayFiresToday() {
        val result = next(newYork, at(newYork, "2026-11-17T06:00"), 7, 0)
        assertEquals("2026-11-17T07:00", local(newYork, result))
    }

    @Test
    fun oneShotAlreadyPassedFiresTomorrow() {
        val result = next(newYork, at(newYork, "2026-11-17T08:00"), 7, 0)
        assertEquals("2026-11-18T07:00", local(newYork, result))
    }

    @Test
    fun exactlyNowMovesToTheNextDay() {
        val result = next(newYork, at(newYork, "2026-11-17T07:00"), 7, 0)
        assertEquals("2026-11-18T07:00", local(newYork, result))
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val result = next(newYork, at(newYork, "2026-11-20T08:00"), 7, 0, weekdays)
        assertEquals("2026-11-23T07:00", local(newYork, result))
    }

    @Test
    fun everyDayFiresTodayWhenStillAhead() {
        val result = next(newYork, at(newYork, "2026-11-17T06:00"), 7, 0, DayOfWeek.entries.toSet())
        assertEquals("2026-11-17T07:00", local(newYork, result))
    }

    @Test
    fun sameWeekdayAfterItsTimeLandsOnDaySeven() {
        val result = next(newYork, at(newYork, "2026-11-17T08:00"), 7, 0, setOf(DayOfWeek.TUESDAY))
        assertEquals("2026-11-24T07:00", local(newYork, result))
    }

    @Test
    fun newYorkSpringForwardGapMovesForwardByTheGap() {
        val result = next(newYork, at(newYork, "2026-03-08T01:00"), 2, 30)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), result)
        assertEquals("2026-03-08T03:30", local(newYork, result))
    }

    @Test
    fun newYorkFallBackFiresOnTheFirstOccurrence() {
        val result = next(newYork, at(newYork, "2026-11-01T00:00"), 1, 30)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), result)
    }

    @Test
    fun newYorkRepeatedTimeAfterItsFirstOccurrenceWaitsForTomorrow() {
        val result = next(newYork, Instant.parse("2026-11-01T05:45:00Z"), 1, 30)
        assertEquals("2026-11-02T01:30", local(newYork, result))
    }

    @Test
    fun madridSpringForwardGapMovesForwardByTheGap() {
        val result = next(madrid, at(madrid, "2026-03-29T01:00"), 2, 30)
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), result)
        assertEquals("2026-03-29T03:30", local(madrid, result))
    }

    @Test
    fun madridFallBackFiresOnTheFirstOccurrence() {
        val result = next(madrid, at(madrid, "2026-10-25T01:00"), 2, 30)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), result)
    }

    @Test
    fun alarmOverloadMatchesTheExplicitArguments() {
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = setOf(DayOfWeek.TUESDAY))
        val from = at(newYork, "2026-11-17T08:00")
        assertEquals(trigger(newYork).next(7, 0, setOf(DayOfWeek.TUESDAY), from), trigger(newYork).next(alarm, from))
    }
}
