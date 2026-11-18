package com.nutabe.chrona.engine

import java.time.DayOfWeek
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCodecTest {
    private val full = StoreData(
        alarms = listOf(
            Alarm(
                id = 1,
                hour = 6,
                minute = 45,
                label = "Gym",
                repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                soundUri = "content://media/1",
                mission = Mission.Math(Difficulty.HARD, 5),
                nextTriggerAt = Instant.parse("2026-11-18T11:45:00Z"),
                snoozedUntil = Instant.parse("2026-11-17T13:05:00Z"),
                snoozesUsed = 2,
            ),
            Alarm(id = 2, mission = Mission.Shake(20, Sensitivity.HIGH)),
            Alarm(id = 3, mission = Mission.Steps(40)),
            Alarm(id = 4, mission = Mission.None),
        ),
        activeRing = ActiveRing(alarmId = 1, startedAt = Instant.parse("2026-11-17T13:00:00Z"), forcedSnoozeCycles = 1),
        settings = AppSettings(onboardingDone = true),
    )

    private fun decodeOk(text: String): StoreData =
        (StoreCodec.decode(text) as StoreCodec.DecodeResult.Ok).data

    @Test
    fun roundTripKeepsEverything() {
        assertEquals(full, decodeOk(StoreCodec.encode(full)))
    }

    @Test
    fun instantsAreStoredAsEpochMillis() {
        val text = StoreCodec.encode(StoreData(alarms = listOf(Alarm(nextTriggerAt = Instant.ofEpochMilli(1_700_000_000_123)))))
        assertTrue(text, text.contains("\"nextTriggerAt\":1700000000123"))
    }

    @Test
    fun missionDiscriminatorsAreStable() {
        val text = StoreCodec.encode(full)
        listOf("math", "shake", "steps", "none").forEach { assertTrue("$it in $text", text.contains("\"type\":\"$it\"")) }
    }

    @Test
    fun weekdaysAreStoredAsIsoNumbers() {
        val text = StoreCodec.encode(full)
        assertTrue(text, text.contains("\"repeatDays\":[1,5]"))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val text = """{"schemaVersion":1,"futureField":{"a":1},"alarms":[{"id":9,"hour":8,"extra":true}]}"""
        val data = decodeOk(text)
        assertEquals(9L, data.alarms.single().id)
        assertEquals(8, data.alarms.single().hour)
    }

    @Test
    fun missingFieldsFallBackToDefaults() {
        val data = decodeOk("{}")
        assertEquals(StoreData(), data)
    }

    @Test
    fun aFileWithoutTheIdCounterStillAllocatesAboveItsHighestId() {
        val data = decodeOk("""{"schemaVersion":1,"alarms":[{"id":1},{"id":7}]}""")
        assertEquals(1L, data.nextAlarmId)
        assertEquals(8L, data.allocateId().first)
    }

    @Test
    fun theIdCounterSurvivesARoundTrip() {
        val data = StoreData(alarms = listOf(Alarm(id = 2)), nextAlarmId = 9)
        assertEquals(9L, decodeOk(StoreCodec.encode(data)).nextAlarmId)
    }

    @Test
    fun garbageIsReportedAsCorrupt() {
        assertTrue(StoreCodec.decode("not json at all") is StoreCodec.DecodeResult.Corrupt)
    }

    @Test
    fun truncatedFileIsReportedAsCorrupt() {
        val text = StoreCodec.encode(full)
        assertTrue(StoreCodec.decode(text.take(text.length / 2)) is StoreCodec.DecodeResult.Corrupt)
    }

    @Test
    fun emptyFileIsReportedAsCorrupt() {
        assertTrue(StoreCodec.decode("") is StoreCodec.DecodeResult.Corrupt)
    }

    @Test
    fun aFileWithoutTheRingQueueDecodesWithAnEmptyOne() {
        val old = """{"schemaVersion":1,"alarms":[],"activeRing":null,"settings":{"onboardingDone":true},"nextAlarmId":3}"""
        assertEquals(emptyList<Long>(), decodeOk(old).ringQueue)
    }

    @Test
    fun theRingQueueRoundTrips() {
        val data = StoreData(alarms = listOf(Alarm(id = 1), Alarm(id = 2)), ringQueue = listOf(2L, 1L))
        assertEquals(listOf(2L, 1L), decodeOk(StoreCodec.encode(data)).ringQueue)
    }
}
