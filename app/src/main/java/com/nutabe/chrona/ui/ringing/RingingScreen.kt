package com.nutabe.chrona.ui.ringing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nutabe.chrona.R
import com.nutabe.chrona.engine.Difficulty
import com.nutabe.chrona.engine.Mission
import com.nutabe.chrona.engine.RingInfo
import com.nutabe.chrona.ui.alarms.missionIcon
import com.nutabe.chrona.ui.components.ChronaIcon
import com.nutabe.chrona.ui.components.DoubleBezelCard
import com.nutabe.chrona.ui.components.EyebrowTag
import com.nutabe.chrona.ui.components.PillCta
import com.nutabe.chrona.ui.components.SecondaryPill
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The ringing screen before the mission: the time, the alarm label, the mission it asks for, and the exits. */
@Composable
fun RingingScreen(
    info: RingInfo,
    now: LocalDateTime,
    onStartMission: () -> Unit,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(scheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        EyebrowTag(stringResource(R.string.ringing_eyebrow))
        Spacer(Modifier.height(20.dp))
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.secondary,
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm", locale)),
            style = MaterialTheme.typography.displayLarge,
            color = scheme.onSurface,
        )
        if (info.label.isNotBlank()) {
            Text(info.label, style = MaterialTheme.typography.headlineSmall, color = scheme.secondary)
        }
        Spacer(Modifier.weight(1f))
        if (info.mission != Mission.None) MissionCard(info)
        Spacer(Modifier.weight(1f))
        if (info.mission == Mission.None) {
            PillCta(
                stringResource(R.string.ringing_stop),
                onStop,
                Modifier.fillMaxWidth(),
                trailingIcon = R.drawable.ic_check_regular,
                height = 64.dp,
            )
        } else {
            PillCta(
                stringResource(R.string.ringing_start_mission),
                onStartMission,
                Modifier.fillMaxWidth(),
                trailingIcon = R.drawable.ic_arrow_right_regular,
                height = 64.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (info.canSnooze) {
            SecondaryPill(
                stringResource(R.string.ringing_snooze_minutes, info.snoozeMinutes),
                onSnooze,
                Modifier.fillMaxWidth(),
                leadingIcon = R.drawable.ic_alarm_regular,
                height = 64.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        SnoozeNote(info)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MissionCard(info: RingInfo) {
    val scheme = MaterialTheme.colorScheme
    DoubleBezelCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EyebrowTag(stringResource(R.string.ringing_mission_required))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                missionIcon(info.mission)?.let {
                    Row(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.surfaceContainerHigh),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { ChronaIcon(it, tint = scheme.onSurface, size = 26.dp) }
                }
                Column {
                    Text(missionTitle(info.mission), style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                    Text(missionDetail(info.mission), style = MaterialTheme.typography.bodySmall, color = scheme.secondary)
                }
            }
            if (info.fellBackToMath) {
                Text(stringResource(R.string.ringing_fallback_note), style = MaterialTheme.typography.bodySmall, color = scheme.secondary)
            }
        }
    }
}

@Composable
private fun SnoozeNote(info: RingInfo) {
    val text = if (info.canSnooze) {
        pluralStringResource(R.plurals.ringing_snoozes_left, info.snoozesLeft, info.snoozesLeft, info.snoozeMax)
    } else {
        stringResource(R.string.ringing_no_snoozes)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun missionTitle(mission: Mission): String = when (mission) {
    Mission.None -> ""
    is Mission.Math -> stringResource(R.string.mission_math_title)
    is Mission.Shake -> stringResource(R.string.mission_shake_title)
    is Mission.Steps -> stringResource(R.string.mission_steps_title)
}

@Composable
private fun missionDetail(mission: Mission): String = when (mission) {
    Mission.None -> ""
    is Mission.Math -> stringResource(R.string.ringing_detail_math, mission.count, difficultyName(mission.difficulty))
    is Mission.Shake -> stringResource(R.string.ringing_detail_shake, mission.count)
    is Mission.Steps -> stringResource(R.string.ringing_detail_steps, mission.count)
}

@Composable
private fun difficultyName(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
}
