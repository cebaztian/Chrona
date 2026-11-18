package com.nutabe.chrona.ui.ringing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nutabe.chrona.R
import com.nutabe.chrona.mission.MissionView
import com.nutabe.chrona.mission.NumpadInput
import com.nutabe.chrona.ui.components.ChronaIcon
import com.nutabe.chrona.ui.components.DoubleBezelCard
import com.nutabe.chrona.ui.components.EyebrowTag
import com.nutabe.chrona.ui.components.hapticClickable
import com.nutabe.chrona.ui.theme.ChronaText
import kotlin.math.roundToInt

/**
 * The mission screen for any [MissionView]; the ringing screen and the editor preview both use it, so
 * what the user rehearses is what runs at 6 am.
 */
@Composable
fun MissionScreen(
    view: MissionView,
    onSubmitMath: (Int) -> Unit,
    onTouch: () -> Unit,
    onSwitchToMath: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (view) {
            is MissionView.MathView -> MathContent(view, onSubmitMath, onTouch)
            is MissionView.ShakeView -> SensorContent(
                eyebrow = stringResource(R.string.mission_eyebrow_shake),
                icon = R.drawable.ic_vibrate_regular,
                count = view.count,
                target = view.target,
                unit = null,
                instruction = stringResource(R.string.mission_shake_instruction),
                lockHint = null,
                switchHint = stringResource(R.string.mission_switch_hint_shake),
                volumeNote = stringResource(R.string.mission_volume_shake),
                offerMath = view.offerMath,
                onSwitchToMath = onSwitchToMath,
            )
            is MissionView.StepsView -> SensorContent(
                eyebrow = stringResource(R.string.mission_eyebrow_steps),
                icon = R.drawable.ic_person_simple_walk_regular,
                count = view.count,
                target = view.target,
                unit = stringResource(R.string.mission_steps_unit),
                instruction = stringResource(R.string.mission_steps_instruction),
                lockHint = stringResource(R.string.mission_steps_lock_hint),
                switchHint = stringResource(R.string.mission_switch_hint_steps),
                volumeNote = stringResource(R.string.mission_volume_steps),
                offerMath = view.offerMath,
                onSwitchToMath = onSwitchToMath,
            )
        }
    }
}

@Composable
private fun ColumnScope.MathContent(view: MissionView.MathView, onSubmit: (Int) -> Unit, onTouch: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var input by remember { mutableStateOf(NumpadInput()) }
    var showWrong by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(view.wrongCount) {
        if (view.wrongCount == 0) return@LaunchedEffect
        input = NumpadInput()
        showWrong = true
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        for (offset in listOf(-12f, 12f, -8f, 8f, 0f)) shake.animateTo(offset, tween(50))
    }
    LaunchedEffect(view.solved) {
        input = NumpadInput()
        showWrong = false
    }
    val barDescription = stringResource(R.string.mission_progress_description, view.solved, view.target)
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .semantics {
                contentDescription = barDescription
                progressBarRangeInfo = ProgressBarRangeInfo(view.solved.toFloat(), 0f..view.target.toFloat())
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth(view.solved.toFloat() / view.target.coerceAtLeast(1))
                .height(4.dp)
                .background(scheme.primary),
        )
    }
    Spacer(Modifier.weight(1f))
    EyebrowTag(stringResource(R.string.mission_problem_of, (view.solved + 1).coerceAtMost(view.target), view.target))
    Spacer(Modifier.height(16.dp))
    DoubleBezelCard(Modifier.fillMaxWidth().offset { IntOffset(shake.value.dp.roundToPx(), 0) }) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${view.problem} = ?",
                style = ChronaText.math,
                color = scheme.onSurface,
                modifier = Modifier.testTag("math-problem"),
            )
            Row(
                Modifier.height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showWrong) {
                    ChronaIcon(R.drawable.ic_x_circle_regular, tint = scheme.error, size = 18.dp)
                    Text(stringResource(R.string.mission_wrong), style = MaterialTheme.typography.bodySmall, color = scheme.error)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.background)
                    .border(1.dp, if (showWrong) scheme.error else scheme.secondary.copy(alpha = 0.5f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Text(input.digits, style = ChronaText.math, color = scheme.onSurface, modifier = Modifier.testTag("math-input"))
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Numpad(
        onDigit = {
            showWrong = false
            input = input.press(it)
            onTouch()
        },
        onBackspace = {
            input = input.backspace()
            onTouch()
        },
        onConfirm = { input.value?.let(onSubmit) },
    )
    Spacer(Modifier.height(12.dp))
    VolumeNote(stringResource(R.string.mission_volume_math))
}

@Composable
private fun Numpad(onDigit: (Int) -> Unit, onBackspace: () -> Unit, onConfirm: () -> Unit) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit -> Key(Modifier.weight(1f).testTag("key-$digit"), onClick = { onDigit(digit) }) { KeyLabel(digit.toString()) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val backspace = stringResource(R.string.numpad_backspace)
            Key(Modifier.weight(1f).testTag("key-backspace").semantics { contentDescription = backspace }, onClick = onBackspace) {
                ChronaIcon(R.drawable.ic_backspace_regular, tint = MaterialTheme.colorScheme.onSurface, size = 28.dp)
            }
            Key(Modifier.weight(1f).testTag("key-0"), onClick = { onDigit(0) }) { KeyLabel("0") }
            val confirm = stringResource(R.string.numpad_confirm)
            Key(
                Modifier.weight(1f).testTag("key-confirm").semantics { contentDescription = confirm },
                onClick = onConfirm,
                accent = true,
            ) { ChronaIcon(R.drawable.ic_check_regular, tint = MaterialTheme.colorScheme.onPrimary, size = 30.dp) }
        }
    }
}

@Composable
private fun Key(modifier: Modifier, onClick: () -> Unit, accent: Boolean = false, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (accent) scheme.primary else scheme.surfaceContainerHigh)
            .hapticClickable(type = if (accent) HapticFeedbackType.Confirm else HapticFeedbackType.VirtualKey, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun KeyLabel(text: String) {
    Text(text, style = ChronaText.math, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ColumnScope.SensorContent(
    eyebrow: String,
    icon: Int,
    count: Int,
    target: Int,
    unit: String?,
    instruction: String,
    lockHint: String?,
    switchHint: String,
    volumeNote: String,
    offerMath: Boolean,
    onSwitchToMath: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    EyebrowTag(eyebrow)
    Spacer(Modifier.weight(1f))
    ProgressRing(count, target, icon, unit)
    Spacer(Modifier.height(32.dp))
    Text(instruction, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, textAlign = TextAlign.Center)
    if (lockHint != null) {
        Spacer(Modifier.height(6.dp))
        Text(lockHint, style = MaterialTheme.typography.bodySmall, color = scheme.secondary, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.weight(1f))
    SwitchToMath(offerMath, onSwitchToMath)
    if (!offerMath) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChronaIcon(R.drawable.ic_timer_regular, tint = scheme.secondary, size = 18.dp)
            Text(switchHint, style = MaterialTheme.typography.bodySmall, color = scheme.secondary)
        }
    }
    Spacer(Modifier.height(12.dp))
    VolumeNote(volumeNote)
}

@Composable
private fun ProgressRing(count: Int, target: Int, icon: Int, unit: String?) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceContainerHigh
    val accent = scheme.primary
    val fraction = (count.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
    val description = stringResource(R.string.mission_progress_description, count, target)
    Box(
        Modifier
            .size(248.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(count.toFloat(), 0f..target.toFloat())
            }
            .drawBehind {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(accent, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChronaIcon(icon, tint = scheme.secondary, size = 28.dp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$count", style = ChronaText.counter, color = accent, modifier = Modifier.testTag("mission-count"))
                Text(" / $target", style = ChronaText.counterTotal, color = scheme.secondary, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (unit != null) Text(unit.uppercase(), style = MaterialTheme.typography.labelSmall, color = scheme.secondary)
        }
    }
}

/** ADR-019: a dashed, locked pill until the sensor has been silent long enough, then a real button. */
@Composable
private fun SwitchToMath(enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.mission_switch_to_math)
    if (enabled) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(CircleShape)
                .border(BorderStroke(1.dp, scheme.secondary), CircleShape)
                .hapticClickable(onClick = onClick)
                .testTag("switch-to-math"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) { Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.secondary) }
    } else {
        val dashColor = scheme.secondary.copy(alpha = 0.4f)
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .drawBehind {
                    drawRoundRect(
                        dashColor,
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
                .semantics { disabled() }
                .testTag("switch-to-math-locked"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            ChronaIcon(R.drawable.ic_lock_simple_regular, tint = scheme.secondary.copy(alpha = 0.45f), size = 20.dp)
            Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.secondary.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun VolumeNote(text: String) {
    val secondary = MaterialTheme.colorScheme.secondary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChronaIcon(R.drawable.ic_speaker_low_regular, tint = secondary, size = 18.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = secondary)
    }
}
