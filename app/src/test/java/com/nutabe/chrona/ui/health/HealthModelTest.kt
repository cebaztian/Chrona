package com.nutabe.chrona.ui.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthModelTest {
    private val healthy = HealthFacts(
        exactAlarms = true,
        notifications = true,
        fullScreenIntent = true,
        activityRecognition = true,
        hasStepsAlarm = false,
        ignoringBatteryOptimizations = true,
        alarmVolumePercent = 80,
        dnd = DndFilter.OFF,
        isSamsung = false,
    )

    private fun statusOf(facts: HealthFacts, kind: HealthKind) = healthReport(facts).first { it.kind == kind }.status

    @Test
    fun aHealthyDeviceHasNoIssues() {
        assertEquals(0, issueCount(healthReport(healthy)))
    }

    @Test
    fun missingExactAlarmsAndNotificationsBlock() {
        assertEquals(HealthStatus.BLOCKING, statusOf(healthy.copy(exactAlarms = false), HealthKind.EXACT_ALARMS))
        assertEquals(HealthStatus.BLOCKING, statusOf(healthy.copy(notifications = false), HealthKind.NOTIFICATIONS))
    }

    @Test
    fun fullScreenIntentAndBatteryNeedAttention() {
        assertEquals(HealthStatus.ATTENTION, statusOf(healthy.copy(fullScreenIntent = false), HealthKind.FULL_SCREEN_INTENT))
        assertEquals(HealthStatus.ATTENTION, statusOf(healthy.copy(ignoringBatteryOptimizations = false), HealthKind.BATTERY))
    }

    @Test
    fun physicalActivityIsShownOnlyWhenAStepsAlarmExists() {
        assertTrue(healthReport(healthy).none { it.kind == HealthKind.PHYSICAL_ACTIVITY })
        val withSteps = healthy.copy(hasStepsAlarm = true, activityRecognition = false)
        assertEquals(HealthStatus.ATTENTION, statusOf(withSteps, HealthKind.PHYSICAL_ACTIVITY))
        assertEquals(HealthStatus.OK, statusOf(withSteps.copy(activityRecognition = true), HealthKind.PHYSICAL_ACTIVITY))
    }

    @Test
    fun aMutedAlarmStreamNeedsAttentionAndReportsThePercent() {
        val item = healthReport(healthy.copy(alarmVolumePercent = 0)).first { it.kind == HealthKind.ALARM_VOLUME }
        assertEquals(HealthStatus.ATTENTION, item.status)
        assertEquals(0, item.percent)
        assertEquals(80, healthReport(healthy).first { it.kind == HealthKind.ALARM_VOLUME }.percent)
    }

    @Test
    fun doNotDisturbFiltersMapToStatuses() {
        assertEquals(HealthStatus.OK, statusOf(healthy.copy(dnd = DndFilter.OFF), HealthKind.DND))
        assertEquals(HealthStatus.OK, statusOf(healthy.copy(dnd = DndFilter.ALARMS_ONLY), HealthKind.DND))
        assertEquals(HealthStatus.ATTENTION, statusOf(healthy.copy(dnd = DndFilter.PRIORITY), HealthKind.DND))
        assertEquals(HealthStatus.BLOCKING, statusOf(healthy.copy(dnd = DndFilter.TOTAL_SILENCE), HealthKind.DND))
        assertEquals(DndFilter.PRIORITY, healthReport(healthy.copy(dnd = DndFilter.PRIORITY)).first { it.kind == HealthKind.DND }.dnd)
    }

    @Test
    fun theSamsungGuideAppearsOnlyOnSamsung() {
        assertFalse(healthReport(healthy).any { it.kind == HealthKind.SAMSUNG_GUIDE })
        val samsung = healthReport(healthy.copy(isSamsung = true)).first { it.kind == HealthKind.SAMSUNG_GUIDE }
        assertEquals(HealthStatus.OK, samsung.status)
        assertNull(samsung.percent)
    }

    @Test
    fun theSamsungGuideWarnsOnlyWhileTheBatteryIsRestricted() {
        val restricted = healthy.copy(isSamsung = true, ignoringBatteryOptimizations = false)
        assertEquals(HealthStatus.ATTENTION, statusOf(restricted, HealthKind.SAMSUNG_GUIDE))
        assertEquals(HealthStatus.OK, statusOf(restricted.copy(ignoringBatteryOptimizations = true), HealthKind.SAMSUNG_GUIDE))
    }

    @Test
    fun theBadgeCountsEveryRowThatIsNotOk() {
        val facts = healthy.copy(exactAlarms = false, fullScreenIntent = false, dnd = DndFilter.PRIORITY)
        assertEquals(3, issueCount(healthReport(facts)))
    }

    @Test
    fun rowsKeepTheDesignOrder() {
        val kinds = healthReport(healthy.copy(hasStepsAlarm = true, isSamsung = true)).map { it.kind }
        assertEquals(
            listOf(
                HealthKind.EXACT_ALARMS, HealthKind.NOTIFICATIONS, HealthKind.FULL_SCREEN_INTENT, HealthKind.PHYSICAL_ACTIVITY,
                HealthKind.BATTERY, HealthKind.ALARM_VOLUME, HealthKind.DND, HealthKind.SAMSUNG_GUIDE,
            ),
            kinds,
        )
    }
}
