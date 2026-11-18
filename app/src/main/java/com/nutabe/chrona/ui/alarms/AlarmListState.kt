package com.nutabe.chrona.ui.alarms

import com.nutabe.chrona.engine.Alarm
import com.nutabe.chrona.engine.StoreData
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Time left until an alarm, floored to whole minutes. All zeros means "less than a minute". */
data class RingsIn(val days: Int, val hours: Int, val minutes: Int)

/** Absolute difference, so a DST change between the two instants is counted as it really passes. */
fun ringsIn(now: Instant, next: Instant): RingsIn? {
    if (!next.isAfter(now)) return null
    val total = Duration.between(now, next).toMinutes()
    return RingsIn(days = (total / (24 * 60)).toInt(), hours = (total / 60 % 24).toInt(), minutes = (total % 60).toInt())
}

sealed interface RepeatSummary {
    data object Once : RepeatSummary

    data object EveryDay : RepeatSummary

    data object Weekdays : RepeatSummary

    data object Weekends : RepeatSummary

    /** Days in week order, Monday first. */
    data class Custom(val days: List<DayOfWeek>) : RepeatSummary
}

private val WEEKDAYS = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun repeatSummary(days: Set<DayOfWeek>): RepeatSummary = when {
    days.isEmpty() -> RepeatSummary.Once
    days.size == 7 -> RepeatSummary.EveryDay
    days == WEEKDAYS -> RepeatSummary.Weekdays
    days == WEEKEND -> RepeatSummary.Weekends
    else -> RepeatSummary.Custom(days.sortedBy { it.value })
}

enum class LockReason { RINGING, SNOOZED }

/** ADR-016: a ringing or snoozed alarm is shown locked, with no toggle, edit or delete. */
data class AlarmItem(val alarm: Alarm, val lock: LockReason?, val snoozedUntilClock: String? = null)

data class AlarmListState(
    val items: List<AlarmItem> = emptyList(),
    /** Time to the next enabled alarm, or null when nothing is scheduled. */
    val nextRingsIn: RingsIn? = null,
    /** Ids chosen for a bulk action, or null when the list is not in selection mode. */
    val selection: Set<Long>? = null,
) {
    /** Keeps only ids that are still selectable (present and not locked); an empty result leaves selection mode. */
    fun withSelection(chosen: Set<Long>?): AlarmListState {
        val valid = chosen?.intersect(items.filter { it.lock == null }.mapTo(HashSet()) { it.alarm.id })
        return copy(selection = valid?.takeIf { it.isNotEmpty() })
    }
}

fun alarmListState(data: StoreData, now: Instant, zone: ZoneId = ZoneId.systemDefault()): AlarmListState {
    val items = data.alarms
        .sortedWith(compareBy<Alarm>({ it.hour }, { it.minute }, { it.id }))
        .map { alarm ->
            val lock = when {
                data.isRingBusy(alarm.id) -> LockReason.RINGING
                alarm.snoozedUntil != null -> LockReason.SNOOZED
                else -> null
            }
            val snoozedClock = alarm.snoozedUntil?.takeIf { lock == LockReason.SNOOZED }?.atZone(zone)
                ?.let { formatClock(it.hour, it.minute) }
            AlarmItem(alarm, lock, snoozedClock)
        }
    val next = data.alarms
        .filter { it.enabled }
        .mapNotNull { it.snoozedUntil ?: it.nextTriggerAt }
        .filter { it.isAfter(now) }
        .minOrNull()
    return AlarmListState(items, next?.let { ringsIn(now, it) })
}

fun formatClock(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
