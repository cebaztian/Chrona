package com.nutabe.chrona.ui.alarms

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutabe.chrona.R
import com.nutabe.chrona.engine.Difficulty
import com.nutabe.chrona.engine.Mission
import com.nutabe.chrona.engine.Sensitivity
import com.nutabe.chrona.ui.components.ChronaIcon
import com.nutabe.chrona.ui.components.DoubleBezelCard
import com.nutabe.chrona.ui.components.EyebrowTag
import com.nutabe.chrona.ui.components.InfoPill
import com.nutabe.chrona.ui.components.PillCta
import com.nutabe.chrona.ui.components.hapticClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionSheet(
    mission: Mission,
    env: EditorEnvironment,
    onChange: (Mission) -> Unit,
    onSelectSteps: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainer,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(scheme.secondary.copy(alpha = 0.6f))) },
    ) {
        Column(Modifier.testTag("mission-sheet").navigationBarsPadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.mission_picker_title), style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface)
                    Box(Modifier.size(48.dp).border(1.dp, scheme.secondary.copy(alpha = 0.5f), CircleShape).clip(CircleShape).hapticClickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                        ChronaIcon(R.drawable.ic_x, contentDescription = stringResource(R.string.close_cd), size = 20.dp)
                    }
                }
                Text(stringResource(R.string.mission_picker_subtitle), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)

                MissionOption(R.drawable.ic_bell, R.string.mission_none_title, R.string.mission_none_hint, mission == Mission.None, "mission-none") { onChange(Mission.None) }

                val math = mission as? Mission.Math
                MissionOption(R.drawable.ic_calculator, R.string.mission_math_title, R.string.mission_math_hint, math != null, "mission-math") { onChange(Mission.Math()) }
                if (math != null) {
                    SettingsPanel {
                        EyebrowTag(stringResource(R.string.mission_difficulty))
                        ChipRow {
                            Choice(R.string.difficulty_easy, math.difficulty == Difficulty.EASY, "difficulty-easy") { onChange(math.copy(difficulty = Difficulty.EASY)) }
                            Choice(R.string.difficulty_medium, math.difficulty == Difficulty.MEDIUM, "difficulty-medium") { onChange(math.copy(difficulty = Difficulty.MEDIUM)) }
                            Choice(R.string.difficulty_hard, math.difficulty == Difficulty.HARD, "difficulty-hard") { onChange(math.copy(difficulty = Difficulty.HARD)) }
                        }
                        LabeledStepper(stringResource(R.string.mission_problems), math.count, 1..10, "math-count") { onChange(math.copy(count = it)) }
                    }
                }

                val shake = mission as? Mission.Shake
                MissionOption(R.drawable.ic_vibrate, R.string.mission_shake_title, R.string.mission_shake_hint, shake != null, "mission-shake") { onChange(Mission.Shake()) }
                if (shake != null) {
                    SettingsPanel {
                        LabeledStepper(stringResource(R.string.mission_shakes), shake.count, 5..100, "shake-count") { onChange(shake.copy(count = it)) }
                        EyebrowTag(stringResource(R.string.mission_sensitivity))
                        ChipRow {
                            Choice(R.string.sensitivity_low, shake.sensitivity == Sensitivity.LOW, "sensitivity-low") { onChange(shake.copy(sensitivity = Sensitivity.LOW)) }
                            Choice(R.string.sensitivity_medium, shake.sensitivity == Sensitivity.MEDIUM, "sensitivity-medium") { onChange(shake.copy(sensitivity = Sensitivity.MEDIUM)) }
                            Choice(R.string.sensitivity_high, shake.sensitivity == Sensitivity.HIGH, "sensitivity-high") { onChange(shake.copy(sensitivity = Sensitivity.HIGH)) }
                        }
                    }
                }

                val steps = mission as? Mission.Steps
                MissionOption(
                    R.drawable.ic_person_simple_walk, R.string.mission_steps_title, R.string.mission_steps_hint, steps != null, "mission-steps",
                    enabled = env.hasStepSensor,
                    note = when {
                        !env.hasStepSensor -> stringResource(R.string.mission_steps_unavailable)
                        !env.activityRecognitionGranted -> stringResource(R.string.mission_steps_needs_permission)
                        else -> null
                    },
                ) { if (env.activityRecognitionGranted) onChange(Mission.Steps()) else onSelectSteps() }
                if (steps != null) {
                    SettingsPanel {
                        LabeledStepper(stringResource(R.string.mission_steps_count), steps.count, 10..500, "steps-count") { onChange(steps.copy(count = it)) }
                    }
                }
            }
            PillCta(
                stringResource(R.string.mission_done), onDismiss, trailingIcon = R.drawable.ic_check, height = 56.dp,
                modifier = Modifier.fillMaxWidth().padding(20.dp).testTag("mission-done"),
            )
        }
    }
}

@Composable
private fun MissionOption(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes hint: Int,
    selected: Boolean,
    tag: String,
    enabled: Boolean = true,
    note: String? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    DoubleBezelCard(Modifier.fillMaxWidth().testTag(tag), alpha = if (enabled) 1f else 0.5f) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (selected) Modifier.border(1.5.dp, scheme.primary, MaterialTheme.shapes.large) else Modifier)
                .hapticClickable(enabled = enabled, onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp).border(1.dp, scheme.secondary.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) { ChronaIcon(icon) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    if (selected) EyebrowTag(stringResource(R.string.mission_active))
                }
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (note != null) InfoPill(note, icon = R.drawable.ic_info, modifier = Modifier.padding(top = 6.dp))
            }
            if (selected) {
                ChronaIcon(R.drawable.ic_check, tint = scheme.primary)
            } else {
                Box(Modifier.size(24.dp).border(1.5.dp, scheme.secondary.copy(alpha = 0.6f), CircleShape))
            }
        }
    }
}

@Composable
private fun SettingsPanel(content: @Composable () -> Unit) {
    DoubleBezelCard(Modifier.fillMaxWidth().padding(start = 24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Selected: `secondaryContainer`. Unselected: Second outline. Never Accent (DESIGN.md §5). */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Choice(@StringRes label: Int, selected: Boolean, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .weight(1f)
            .height(48.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(scheme.secondaryContainer) else Modifier.border(1.dp, scheme.secondary.copy(alpha = 0.6f), CircleShape),
            )
            .hapticClickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) scheme.onSecondaryContainer else scheme.secondary,
        )
    }
}
