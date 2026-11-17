package com.nutabe.chrona.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmValidationTest {
    private fun problems(alarm: Alarm) = AlarmValidation.validate(alarm)

    @Test
    fun aDefaultAlarmIsValid() {
        assertTrue(problems(Alarm()).isEmpty())
    }

    @Test
    fun timeMustBeAClockTime() {
        assertEquals(setOf(AlarmProblem.HOUR), problems(Alarm(hour = 24)))
        assertEquals(setOf(AlarmProblem.MINUTE), problems(Alarm(minute = 60)))
        assertTrue(problems(Alarm(hour = 0, minute = 0)).isEmpty())
        assertTrue(problems(Alarm(hour = 23, minute = 59)).isEmpty())
    }

    @Test
    fun labelHasALimit() {
        assertTrue(problems(Alarm(label = "x".repeat(AlarmValidation.MAX_LABEL))).isEmpty())
        assertEquals(setOf(AlarmProblem.LABEL_TOO_LONG), problems(Alarm(label = "x".repeat(AlarmValidation.MAX_LABEL + 1))))
    }

    @Test
    fun snoozeBounds() {
        assertEquals(setOf(AlarmProblem.SNOOZE_MINUTES), problems(Alarm(snooze = SnoozeConfig(true, 0, 3))))
        assertEquals(setOf(AlarmProblem.SNOOZE_MINUTES), problems(Alarm(snooze = SnoozeConfig(true, 31, 3))))
        assertEquals(setOf(AlarmProblem.SNOOZE_COUNT), problems(Alarm(snooze = SnoozeConfig(true, 5, 0))))
        assertEquals(setOf(AlarmProblem.SNOOZE_COUNT), problems(Alarm(snooze = SnoozeConfig(true, 5, 11))))
    }

    @Test
    fun volumeAndRampBounds() {
        assertEquals(setOf(AlarmProblem.VOLUME), problems(Alarm(maxVolumePercent = 9)))
        assertEquals(setOf(AlarmProblem.VOLUME), problems(Alarm(maxVolumePercent = 101)))
        assertEquals(setOf(AlarmProblem.RAMP), problems(Alarm(volumeRampSeconds = -1)))
        assertEquals(setOf(AlarmProblem.RAMP), problems(Alarm(volumeRampSeconds = 301)))
        assertTrue(problems(Alarm(volumeRampSeconds = 0)).isEmpty())
    }

    @Test
    fun missionCountsHavePerMissionBounds() {
        assertEquals(setOf(AlarmProblem.MISSION_COUNT), problems(Alarm(mission = Mission.Math(count = 0))))
        assertEquals(setOf(AlarmProblem.MISSION_COUNT), problems(Alarm(mission = Mission.Math(count = 11))))
        assertEquals(setOf(AlarmProblem.MISSION_COUNT), problems(Alarm(mission = Mission.Shake(count = 4))))
        assertEquals(setOf(AlarmProblem.MISSION_COUNT), problems(Alarm(mission = Mission.Steps(count = 9))))
        assertEquals(setOf(AlarmProblem.MISSION_COUNT), problems(Alarm(mission = Mission.Steps(count = 501))))
        assertTrue(problems(Alarm(mission = Mission.Steps(count = 50))).isEmpty())
    }

    @Test
    fun severalProblemsAreAllReported() {
        assertEquals(
            setOf(AlarmProblem.HOUR, AlarmProblem.VOLUME),
            problems(Alarm(hour = 30, maxVolumePercent = 0)),
        )
    }
}
