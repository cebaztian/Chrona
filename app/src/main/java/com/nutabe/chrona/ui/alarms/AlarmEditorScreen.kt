package com.nutabe.chrona.ui.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.DpSize
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nutabe.chrona.R
import com.nutabe.chrona.engine.Alarm
import com.nutabe.chrona.engine.AlarmProblem
import com.nutabe.chrona.engine.AlarmValidation
import com.nutabe.chrona.engine.Mission
import com.nutabe.chrona.ui.components.ChronaIcon
import com.nutabe.chrona.ui.components.DoubleBezelCard
import com.nutabe.chrona.ui.components.EyebrowTag
import com.nutabe.chrona.ui.components.InfoPill
import com.nutabe.chrona.ui.components.PillCta
import com.nutabe.chrona.ui.components.SecondaryPill
import com.nutabe.chrona.mission.MissionEnvironment
import com.nutabe.chrona.ui.ringing.MissionPreview
import java.time.DayOfWeek

/** What the device allows, read by the host: the steps mission and the muted-stream warning depend on it. */
data class EditorEnvironment(
    val hasStepSensor: Boolean = true,
    val activityRecognitionGranted: Boolean = true,
    val alarmStreamMuted: Boolean = false,
)

/** Stateless: the draft comes in through [state] and every change goes out as a transform of the alarm. */
@Composable
fun AlarmEditorScreen(
    state: AlarmEditorState,
    soundLabel: String,
    env: EditorEnvironment,
    onChange: ((Alarm) -> Alarm) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onPickRingtone: () -> Unit,
    onPickFile: () -> Unit,
    onRequestStepsPermission: () -> Unit,
) {
    var showTime by rememberSaveable { mutableStateOf(false) }
    var showMission by rememberSaveable { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showRamp by rememberSaveable { mutableStateOf(false) }
    var showSnooze by rememberSaveable { mutableStateOf(false) }
    var showTone by rememberSaveable { mutableStateOf(false) }
    val draft = state.draft

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { EditorTopBar(state.isNew, onBack, onDelete) },
        bottomBar = { SaveBar(state, onSave) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.locked) InfoPill(stringResource(R.string.editor_locked), icon = R.drawable.ic_lock_simple, modifier = Modifier.testTag("editor-locked"))
            TimeSection(draft, state.ringsIn) { showTime = true }
            Section(R.string.editor_section_repeat) { RepeatRow(draft.repeatDays) { day -> onChange { it.copy(repeatDays = it.repeatDays.toggled(day)) } } }
            Section(R.string.editor_section_label) { LabelField(draft.label, AlarmProblem.LABEL_TOO_LONG in state.problems) { text -> onChange { it.copy(label = text) } } }
            Section(R.string.editor_section_mission) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MissionRow(draft.mission) { showMission = true }
                    if (draft.mission != Mission.None) {
                        SecondaryPill(
                            stringResource(R.string.mission_preview),
                            onClick = { showPreview = true },
                            modifier = Modifier.fillMaxWidth().testTag("mission-preview"),
                        )
                    }
                }
            }
            Section(R.string.editor_section_sound) {
                DoubleBezelCard(Modifier.fillMaxWidth()) {
                    SettingRow(R.drawable.ic_music_notes, stringResource(R.string.sound_tone), value = soundLabel, onClick = { showTone = true }, modifier = Modifier.testTag("tone-row"))
                    RowDivider()
                    SwitchRow(R.drawable.ic_vibrate, stringResource(R.string.sound_vibration), draft.vibrate, { v -> onChange { it.copy(vibrate = v) } }, Modifier.testTag("vibration-row"))
                    RowDivider()
                    VolumeRow(draft.maxVolumePercent) { v -> onChange { it.copy(maxVolumePercent = v) } }
                    RowDivider()
                    SettingRow(R.drawable.ic_trend_up, stringResource(R.string.sound_ramp), value = rampLabel(draft.volumeRampSeconds), onClick = { showRamp = true }, modifier = Modifier.testTag("ramp-row"))
                }
                if (env.alarmStreamMuted) InfoPill(stringResource(R.string.sound_muted_warning), icon = R.drawable.ic_warning, modifier = Modifier.padding(top = 10.dp).testTag("muted-warning"))
            }
            Section(R.string.editor_section_snooze) {
                DoubleBezelCard(Modifier.fillMaxWidth()) {
                    SwitchRow(
                        R.drawable.ic_alarm,
                        stringResource(R.string.snooze_title),
                        draft.snooze.enabled,
                        { v -> onChange { it.copy(snooze = it.snooze.copy(enabled = v)) } },
                        Modifier.testTag("snooze-row"),
                        supporting = if (draft.snooze.enabled) stringResource(R.string.snooze_summary, draft.snooze.minutes, draft.snooze.maxCount) else stringResource(R.string.snooze_off),
                        onSupportingClick = if (draft.snooze.enabled) ({ showSnooze = true }) else null,
                    )
                }
            }
        }
    }

    if (showTime) {
        TimeDialog(draft.hour, draft.minute, { h, m -> onChange { it.copy(hour = h, minute = m) }; showTime = false }) { showTime = false }
    }
    if (showMission) {
        MissionSheet(draft.mission, env, { m -> onChange { it.copy(mission = m) } }, onRequestStepsPermission) { showMission = false }
    }
    if (showPreview) {
        MissionPreview(
            mission = draft.mission,
            soundUri = draft.soundUri,
            environment = MissionEnvironment(
                hasShakeSensor = true,
                hasStepSensor = env.hasStepSensor,
                activityRecognitionGranted = env.activityRecognitionGranted,
            ),
            onClose = { showPreview = false },
        )
    }
    if (showRamp) {
        RampDialog(draft.volumeRampSeconds, { s -> onChange { it.copy(volumeRampSeconds = s) }; showRamp = false }) { showRamp = false }
    }
    if (showSnooze) {
        SnoozeDialog(draft.snooze.minutes, draft.snooze.maxCount, { m, c -> onChange { it.copy(snooze = it.snooze.copy(minutes = m, maxCount = c)) } }) { showSnooze = false }
    }
    if (showTone) {
        TonePickDialog(
            onRingtones = { showTone = false; onPickRingtone() },
            onFile = { showTone = false; onPickFile() },
            onDefault = { onChange { it.copy(soundUri = null) }; showTone = false },
            onDismiss = { showTone = false },
        )
    }
}

private fun Set<DayOfWeek>.toggled(day: DayOfWeek): Set<DayOfWeek> = if (day in this) this - day else this + day

@Composable
private fun EditorTopBar(isNew: Boolean, onBack: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButtonBox(R.drawable.ic_caret_left, stringResource(R.string.back_cd), "editor-back", onBack)
            Text(
                stringResource(if (isNew) R.string.editor_title_new else R.string.editor_title_edit),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (isNew) Box(Modifier.size(48.dp)) else IconButtonBox(R.drawable.ic_trash, stringResource(R.string.delete_cd), "editor-delete", onDelete)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun IconButtonBox(icon: Int, description: String, tag: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).testTag(tag), contentAlignment = Alignment.Center) {
        ChronaIcon(icon, contentDescription = description, size = 24.dp)
    }
}

@Composable
private fun Section(label: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EyebrowTag(stringResource(label))
        content()
    }
}

@Composable
private fun TimeSection(alarm: Alarm, ringsIn: RingsIn?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.clickable(onClick = onClick).testTag("time-boxes"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimeBox("%02d".format(alarm.hour), "time-hour")
            Text(":", style = MaterialTheme.typography.displayMedium, color = scheme.secondary)
            TimeBox("%02d".format(alarm.minute), "time-minute")
        }
        if (ringsIn != null) {
            val duration = ringsInText(ringsIn)
            val full = stringResource(R.string.rings_in, duration)
            val accent = scheme.primary
            val text = buildAnnotatedString {
                val start = full.indexOf(duration)
                if (start < 0) append(full) else {
                    append(full.substring(0, start))
                    withStyle(SpanStyle(color = accent)) { append(duration) }
                    append(full.substring(start + duration.length))
                }
            }
            Row(
                Modifier.border(1.dp, scheme.secondary.copy(alpha = 0.5f), CircleShape).padding(horizontal = 16.dp, vertical = 8.dp).testTag("editor-rings-in"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChronaIcon(R.drawable.ic_clock, size = 18.dp)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TimeBox(text: String, tag: String) {
    DoubleBezelCard(Modifier.width(132.dp)) {
        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag(tag))
        }
    }
}

@Composable
private fun RepeatRow(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val isOn = day in selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .then(if (isOn) Modifier.background(scheme.secondaryContainer) else Modifier.border(1.dp, scheme.secondary.copy(alpha = 0.7f), CircleShape))
                    .clickable { onToggle(day) }
                    .semantics { role = Role.Checkbox; this.selected = isOn }
                    .testTag("day-${day.value}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(dayInitial(day), style = MaterialTheme.typography.bodyMedium, color = if (isOn) scheme.onSecondaryContainer else scheme.secondary)
            }
        }
    }
}

@Composable
private fun LabelField(value: String, error: Boolean, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= AlarmValidation.MAX_LABEL + 10) onChange(it) },
        modifier = Modifier.fillMaxWidth().testTag("label-field"),
        singleLine = true,
        isError = error,
        shape = MaterialTheme.shapes.small,
        placeholder = { Text(stringResource(R.string.editor_label_hint), color = scheme.onSurfaceVariant) },
        leadingIcon = { ChronaIcon(R.drawable.ic_tag) },
        trailingIcon = { ChronaIcon(R.drawable.ic_pencil_simple) },
        supportingText = {
            if (error) Text(stringResource(R.string.problem_label), color = scheme.error)
            else Text(stringResource(R.string.editor_label_count, value.length, AlarmValidation.MAX_LABEL), color = scheme.onSurfaceVariant)
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline,
            errorBorderColor = scheme.error,
            cursorColor = scheme.primary,
        ),
    )
}

@Composable
private fun MissionRow(mission: Mission, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DoubleBezelCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (mission != Mission.None) Modifier.border(1.dp, scheme.primary, MaterialTheme.shapes.large) else Modifier)
                .clickable(onClick = onClick)
                .padding(16.dp)
                .testTag("mission-row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp).border(1.dp, scheme.secondary.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                ChronaIcon(missionIcon(mission) ?: R.drawable.ic_bell)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.mission_eyebrow).uppercase(), style = MaterialTheme.typography.labelSmall, color = scheme.secondary)
                Text(missionSummaryText(mission) ?: stringResource(R.string.mission_none_title), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            }
            ChronaIcon(R.drawable.ic_caret_right, size = 20.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRow(percent: Int, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChronaIcon(R.drawable.ic_speaker_low)
            Text(stringResource(R.string.sound_volume), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
            Text("$percent%", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.testTag("volume-value"))
        }
        val colors = SliderDefaults.colors(
            thumbColor = scheme.primary,
            activeTrackColor = scheme.primary,
            inactiveTrackColor = scheme.surfaceContainerHigh,
        )
        val interaction = remember { MutableInteractionSource() }
        Slider(
            value = percent.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(10, 100)) },
            valueRange = 10f..100f,
            modifier = Modifier.testTag("volume-slider"),
            colors = colors,
            interactionSource = interaction,
            thumb = { SliderDefaults.Thumb(interaction, colors = colors, thumbSize = DpSize(22.dp, 22.dp)) },
            track = { state ->
                SliderDefaults.Track(
                    state,
                    modifier = Modifier.height(6.dp),
                    colors = colors,
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp,
                )
            },
        )
    }
}

@Composable
private fun SaveBar(state: AlarmEditorState, onSave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.secondary.copy(alpha = 0.18f), CircleShape)
            .padding(6.dp),
    ) {
        PillCta(
            stringResource(R.string.editor_save), onSave, Modifier.fillMaxWidth().testTag("save"),
            trailingIcon = R.drawable.ic_check, enabled = state.canSave,
        )
    }
}
