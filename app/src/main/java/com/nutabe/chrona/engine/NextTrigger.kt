package com.nutabe.chrona.engine

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Computes the next fire instant of an alarm. A local time inside a DST gap moves forward by the
 * length of the gap; a repeated local time resolves to its first occurrence, so it fires once.
 */
class NextTrigger(private val clock: Clock) {
    fun next(alarm: Alarm, from: Instant = clock.instant()): Instant =
        next(alarm.hour, alarm.minute, alarm.repeatDays, from)

    fun next(hour: Int, minute: Int, repeatDays: Set<DayOfWeek>, from: Instant = clock.instant()): Instant {
        val zone = clock.zone
        val today = from.atZone(zone).toLocalDate()
        val time = LocalTime.of(hour, minute)
        for (offset in 0L..MAX_DAY_OFFSET) {
            val date = today.plusDays(offset)
            if (repeatDays.isNotEmpty() && date.dayOfWeek !in repeatDays) continue
            val candidate = ZonedDateTime.of(date, time, zone).toInstant()
            if (candidate.isAfter(from)) return candidate
        }
        error("No trigger within $MAX_DAY_OFFSET days for $hour:$minute $repeatDays")
    }

    private companion object {
        // Day 7 shares today's weekday, so a repeating alarm whose time passed today lands on day 7.
        const val MAX_DAY_OFFSET = 7L
    }
}
