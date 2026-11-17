package com.nutabe.chrona.ui.alarms

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nutabe.chrona.R
import com.nutabe.chrona.engine.Mission
import java.time.DayOfWeek

@Composable
fun ringsInText(value: RingsIn): String = when {
    value.days > 0 -> stringResource(R.string.duration_dh, value.days, value.hours)
    value.hours > 0 -> stringResource(R.string.duration_hm, value.hours, value.minutes)
    value.minutes > 0 -> stringResource(R.string.duration_m, value.minutes)
    else -> stringResource(R.string.duration_lt_minute)
}

@Composable
fun dayInitial(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.day_initial_1
        DayOfWeek.TUESDAY -> R.string.day_initial_2
        DayOfWeek.WEDNESDAY -> R.string.day_initial_3
        DayOfWeek.THURSDAY -> R.string.day_initial_4
        DayOfWeek.FRIDAY -> R.string.day_initial_5
        DayOfWeek.SATURDAY -> R.string.day_initial_6
        DayOfWeek.SUNDAY -> R.string.day_initial_7
    },
)

@Composable
fun repeatText(summary: RepeatSummary): String = when (summary) {
    RepeatSummary.Once -> stringResource(R.string.repeat_once)
    RepeatSummary.EveryDay -> stringResource(R.string.repeat_every_day)
    RepeatSummary.Weekdays -> stringResource(R.string.repeat_weekdays)
    RepeatSummary.Weekends -> stringResource(R.string.repeat_weekends)
    is RepeatSummary.Custom -> summary.days.map { dayInitial(it) }.joinToString("")
}

/** Null for "no mission": the list shows no chip then. */
@Composable
fun missionSummaryText(mission: Mission): String? = when (mission) {
    Mission.None -> null
    is Mission.Math -> stringResource(R.string.mission_math_summary, mission.count)
    is Mission.Shake -> stringResource(R.string.mission_shake_summary, mission.count)
    is Mission.Steps -> stringResource(R.string.mission_steps_summary, mission.count)
}

fun missionIcon(mission: Mission): Int? = when (mission) {
    Mission.None -> null
    is Mission.Math -> R.drawable.ic_calculator
    is Mission.Shake -> R.drawable.ic_vibrate
    is Mission.Steps -> R.drawable.ic_person_simple_walk
}
