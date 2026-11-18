package com.nutabe.chrona.ui.alarms

import com.nutabe.chrona.engine.Alarm
import com.nutabe.chrona.engine.Difficulty
import com.nutabe.chrona.engine.Mission
import java.time.DayOfWeek
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyDraftTest {
    private val source = Alarm(
        id = 9,
        hour = 6,
        minute = 45,
        label = "Gym",
        repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        enabled = false,
        soundUri = "content://tone",
        volumeRampSeconds = 60,
        mission = Mission.Math(Difficulty.HARD, 4),
        nextTriggerAt = Instant.parse("2026-11-18T11:45:00Z"),
        snoozedUntil = Instant.parse("2026-11-18T11:50:00Z"),
        snoozesUsed = 2,
    )

    @Test
    fun theCopyIsANewAlarmWithTheSameSettings() {
        val copy = copyDraft(source)
        assertEquals(0L, copy.id)
        assertEquals(source.copy(id = 0, enabled = true, nextTriggerAt = null, snoozedUntil = null, snoozesUsed = 0), copy)
        assertEquals("Gym", copy.label)
        assertEquals(Mission.Math(Difficulty.HARD, 4), copy.mission)
    }

    @Test
    fun theCopyDoesNotInheritScheduleState() {
        val copy = copyDraft(source)
        assertNull(copy.nextTriggerAt)
        assertNull(copy.snoozedUntil)
        assertEquals(0, copy.snoozesUsed)
        assertTrue(copy.enabled)
    }
}
