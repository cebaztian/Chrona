package com.nutabe.chrona.ui.alarms

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutabe.chrona.R
import com.nutabe.chrona.ui.components.ChronaIcon
import com.nutabe.chrona.ui.components.ChronaSwitch
import com.nutabe.chrona.ui.components.hapticClickable
import com.nutabe.chrona.ui.theme.ChronaText

/** A row of the sound and snooze groups: leading icon, label, optional value and caret. */
@Composable
fun SettingRow(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.hapticClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChronaIcon(icon)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            ChronaIcon(R.drawable.ic_caret_right, size = 20.dp)
        }
    }
}

@Composable
fun SwitchRow(
    @DrawableRes icon: Int,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onSupportingClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChronaIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.then(if (onSupportingClick != null) Modifier.hapticClickable(onClick = onSupportingClick) else Modifier),
                )
            }
        }
        ChronaSwitch(checked, onCheckedChange)
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 20.dp))
}

/** Minus / value / plus control with 48 dp targets, clamped to [range]. */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange, modifier: Modifier = Modifier, tag: String = "stepper") {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.border(1.dp, scheme.outline.copy(alpha = 0.5f), CircleShape).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(R.drawable.ic_minus, stringResource(R.string.decrease_cd), value > range.first, Modifier.testTag("$tag-minus")) {
            onChange((value - 1).coerceAtLeast(range.first))
        }
        Text(
            value.toString(),
            style = ChronaText.number.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp).testTag("$tag-value"),
        )
        StepperButton(R.drawable.ic_plus, stringResource(R.string.increase_cd), value < range.last, Modifier.testTag("$tag-plus")) {
            onChange((value + 1).coerceAtMost(range.last))
        }
    }
}

@Composable
private fun StepperButton(@DrawableRes icon: Int, description: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).hapticClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChronaIcon(
            icon,
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 1f else 0.35f),
            size = 22.dp,
            contentDescription = description,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(hour: Int, minute: Int, onConfirm: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    val haptics = LocalHapticFeedback.current
    var firstValue by remember { mutableStateOf(true) }
    LaunchedEffect(state.hour, state.minute) {
        if (firstValue) firstValue = false else haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = { DialogAction(stringResource(R.string.editor_ok), "time-ok") { onConfirm(state.hour, state.minute) } },
        dismissButton = { DialogAction(stringResource(R.string.editor_cancel), "time-cancel", onDismiss) },
        text = {
            // The picker draws its hour and minute boxes in displayLarge (88 sp), which does not fit them.
            val typography = MaterialTheme.typography
            MaterialTheme(typography = typography.copy(displayLarge = typography.displayLarge.copy(fontSize = 52.sp))) {
                TimePicker(state)
            }
        },
    )
}

@Composable
fun RampDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {},
        title = { Text(stringResource(R.string.sound_ramp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                listOf(0, 10, 30, 60, 120).forEach { seconds ->
                    val selected = seconds == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .hapticClickable { onSelect(seconds) }
                            .padding(vertical = 14.dp)
                            .testTag("ramp-$seconds"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            rampLabel(seconds),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) ChronaIcon(R.drawable.ic_check, tint = MaterialTheme.colorScheme.primary, size = 20.dp)
                    }
                }
            }
        },
    )
}

@Composable
fun SnoozeDialog(minutes: Int, maxCount: Int, onChange: (minutes: Int, maxCount: Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = { DialogAction(stringResource(R.string.editor_ok), "snooze-ok", onDismiss) },
        title = { Text(stringResource(R.string.snooze_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledStepper(stringResource(R.string.snooze_minutes), minutes, 1..30, "snooze-minutes") { onChange(it, maxCount) }
                LabeledStepper(stringResource(R.string.snooze_max), maxCount, 1..10, "snooze-max") { onChange(minutes, it) }
            }
        },
    )
}

@Composable
fun LabeledStepper(label: String, value: Int, range: IntRange, tag: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Stepper(value, onChange, range, tag = tag)
    }
}

@Composable
fun TonePickDialog(onRingtones: () -> Unit, onFile: () -> Unit, onDefault: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {},
        title = { Text(stringResource(R.string.sound_pick_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                listOf(
                    R.string.sound_pick_ringtones to onRingtones,
                    R.string.sound_pick_file to onFile,
                    R.string.sound_pick_default to onDefault,
                ).forEach { (label, action) ->
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().hapticClickable(onClick = action).padding(vertical = 16.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun DialogAction(text: String, tag: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(CircleShape).hapticClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp).testTag(tag),
    )
}

@Composable
fun rampLabel(seconds: Int): String =
    if (seconds == 0) stringResource(R.string.sound_ramp_off) else stringResource(R.string.sound_ramp_seconds, seconds)
