package com.nutabe.chrona.ui.health

enum class HealthKind {
    EXACT_ALARMS,
    NOTIFICATIONS,
    FULL_SCREEN_INTENT,
    PHYSICAL_ACTIVITY,
    BATTERY,
    ALARM_VOLUME,
    DND,
    SAMSUNG_GUIDE,
}

/** DESIGN.md §6: OK is Second with a check, attention is Accent with a warning, blocking is error with an icon. */
enum class HealthStatus { OK, ATTENTION, BLOCKING }

enum class DndFilter { OFF, PRIORITY, ALARMS_ONLY, TOTAL_SILENCE }

/** What the device reports, read by the host so the rules below stay pure. */
data class HealthFacts(
    val exactAlarms: Boolean,
    val notifications: Boolean,
    val fullScreenIntent: Boolean,
    val activityRecognition: Boolean,
    val hasStepsAlarm: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val alarmVolumePercent: Int,
    val dnd: DndFilter,
    val isSamsung: Boolean,
)

data class HealthItem(
    val kind: HealthKind,
    val status: HealthStatus,
    val percent: Int? = null,
    val dnd: DndFilter? = null,
)

/** Builds the rows of the health screen, in display order. */
fun healthReport(facts: HealthFacts): List<HealthItem> = buildList {
    add(HealthItem(HealthKind.EXACT_ALARMS, if (facts.exactAlarms) HealthStatus.OK else HealthStatus.BLOCKING))
    add(HealthItem(HealthKind.NOTIFICATIONS, if (facts.notifications) HealthStatus.OK else HealthStatus.BLOCKING))
    add(HealthItem(HealthKind.FULL_SCREEN_INTENT, if (facts.fullScreenIntent) HealthStatus.OK else HealthStatus.ATTENTION))
    if (facts.hasStepsAlarm) {
        add(HealthItem(HealthKind.PHYSICAL_ACTIVITY, if (facts.activityRecognition) HealthStatus.OK else HealthStatus.ATTENTION))
    }
    add(HealthItem(HealthKind.BATTERY, if (facts.ignoringBatteryOptimizations) HealthStatus.OK else HealthStatus.ATTENTION))
    add(
        HealthItem(
            HealthKind.ALARM_VOLUME,
            if (facts.alarmVolumePercent > 0) HealthStatus.OK else HealthStatus.ATTENTION,
            percent = facts.alarmVolumePercent,
        ),
    )
    add(
        HealthItem(
            HealthKind.DND,
            when (facts.dnd) {
                DndFilter.OFF, DndFilter.ALARMS_ONLY -> HealthStatus.OK
                DndFilter.PRIORITY -> HealthStatus.ATTENTION
                DndFilter.TOTAL_SILENCE -> HealthStatus.BLOCKING
            },
            dnd = facts.dnd,
        ),
    )
    if (facts.isSamsung) add(HealthItem(HealthKind.SAMSUNG_GUIDE, HealthStatus.ATTENTION))
}

/** Number of rows that are not OK: the badge in Settings. */
fun issueCount(items: List<HealthItem>): Int = items.count { it.status != HealthStatus.OK }
