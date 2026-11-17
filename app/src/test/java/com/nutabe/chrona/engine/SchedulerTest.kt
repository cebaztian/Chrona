package com.nutabe.chrona.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SchedulerTest {
    @Test
    fun everyKindOfOneAlarmHasItsOwnRequestCode() {
        val codes = TriggerKind.entries.map { Scheduler.requestCode(7, it) }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun differentAlarmsNeverShareACode() {
        val first = TriggerKind.entries.map { Scheduler.requestCode(1, it) }.toSet()
        val second = TriggerKind.entries.map { Scheduler.requestCode(2, it) }.toSet()
        assertEquals(emptySet<Int>(), first intersect second)
    }

    @Test
    fun aSnoozeNeverReplacesTheNextOccurrence() {
        assertNotEquals(
            Scheduler.requestCode(5, TriggerKind.NEXT),
            Scheduler.requestCode(5, TriggerKind.SNOOZE),
        )
    }
}
