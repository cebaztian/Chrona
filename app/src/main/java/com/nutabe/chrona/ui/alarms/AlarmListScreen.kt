package com.nutabe.chrona.ui.alarms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nutabe.chrona.R
import com.nutabe.chrona.ui.components.ChronaFab
import com.nutabe.chrona.ui.components.ChronaIcon
import com.nutabe.chrona.ui.components.ChronaSwitch
import com.nutabe.chrona.ui.components.DoubleBezelCard
import com.nutabe.chrona.ui.components.EyebrowTag
import com.nutabe.chrona.ui.components.InfoPill
import com.nutabe.chrona.ui.components.hapticClickable

/** Stateless: everything comes in as [state] and goes out as callbacks, so UI tests never touch the store. */
@Composable
fun AlarmListScreen(
    state: AlarmListState,
    snackbarHostState: SnackbarHostState,
    onToggle: (id: Long, enabled: Boolean) -> Unit,
    onOpen: (id: Long) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onDuplicate: (id: Long) -> Unit = {},
    onDelete: (id: Long) -> Unit = {},
    onSelect: (id: Long) -> Unit = {},
    onToggleSelected: (id: Long) -> Unit = {},
    onCancelSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
) {
    val selection = state.selection
    BackHandler(enabled = selection != null, onBack = onCancelSelection)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selection == null) ListTopBar(onSettings) else SelectionTopBar(selection.size, onCancelSelection, onDeleteSelected)
        },
        floatingActionButton = {
            if (selection == null) ChronaFab(R.drawable.ic_plus, stringResource(R.string.add_alarm_cd), onAdd, Modifier.testTag("add-alarm"))
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("alarm-list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.nextRingsIn?.let { next -> item(key = "header") { NextAlarmHeader(next) } }
                items(state.items, key = { it.alarm.id }) { item -> AlarmCard(
                        item = item,
                        selection = selection,
                        onToggle = onToggle,
                        onOpen = onOpen,
                        onDuplicate = onDuplicate,
                        onDelete = onDelete,
                        onSelect = onSelect,
                        onToggleSelected = onToggleSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListTopBar(onSettings: () -> Unit) {
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.alarms_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Box(Modifier.size(48.dp).hapticClickable(onClick = onSettings).testTag("open-settings"), contentAlignment = Alignment.Center) {
                ChronaIcon(R.drawable.ic_gear_six, contentDescription = stringResource(R.string.settings_cd), size = 28.dp)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).hapticClickable(onClick = onClose).testTag("selection-close"), contentAlignment = Alignment.Center) {
                ChronaIcon(R.drawable.ic_x, contentDescription = stringResource(R.string.selection_close_cd), size = 28.dp)
            }
            Text(
                pluralStringResource(R.plurals.alarms_selected, count, count),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).testTag("selection-count"),
            )
            Box(Modifier.size(48.dp).clip(CircleShape).hapticClickable(onClick = onDelete).testTag("selection-delete"), contentAlignment = Alignment.Center) {
                ChronaIcon(R.drawable.ic_trash, contentDescription = stringResource(R.string.selection_delete_cd), size = 28.dp)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun NextAlarmHeader(next: RingsIn) {
    val duration = ringsInText(next)
    val full = stringResource(R.string.rings_in, duration)
    val accent = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        val start = full.indexOf(duration)
        if (start < 0) {
            append(full)
        } else {
            append(full.substring(0, start))
            withStyle(SpanStyle(color = accent)) { append(duration) }
            append(full.substring(start + duration.length))
        }
    }
    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EyebrowTag(stringResource(R.string.next_alarm_eyebrow))
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("rings-in"))
    }
}

@Composable
private fun AlarmCard(
    item: AlarmItem,
    selection: Set<Long>?,
    onToggle: (Long, Boolean) -> Unit,
    onOpen: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onToggleSelected: (Long) -> Unit,
) {
    val alarm = item.alarm
    val selecting = selection != null
    val selected = selection?.contains(alarm.id) == true
    var menuOpen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val locked = item.lock != null
    DoubleBezelCard(
        modifier = Modifier.fillMaxWidth().testTag("alarm-${alarm.id}"),
        alpha = if (alarm.enabled || locked) 1f else 0.5f,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    when {
                        locked -> Modifier
                        selecting -> Modifier.hapticClickable { onToggleSelected(alarm.id) }.semantics { this.selected = selected }
                        else -> Modifier.hapticClickable(onLongClick = { menuOpen = true }) { onOpen(alarm.id) }
                    },
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatClock(alarm.hour, alarm.minute), style = MaterialTheme.typography.displaySmall, color = scheme.onSurface)
                val repeat = repeatText(repeatSummary(alarm.repeatDays))
                Text(
                    listOf(alarm.label.ifBlank { null }, repeat).filterNotNull().joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                missionSummaryText(alarm.mission)?.let { summary ->
                    InfoPill(summary, icon = missionIcon(alarm.mission))
                }
                if (locked) {
                    val note = if (item.lock == LockReason.SNOOZED) {
                        stringResource(R.string.locked_snoozed, item.snoozedUntilClock.orEmpty())
                    } else {
                        stringResource(R.string.locked_ringing)
                    }
                    InfoPill(note, icon = R.drawable.ic_bell, textColor = scheme.onSurface, borderColor = scheme.primary.copy(alpha = 0.7f), modifier = Modifier.testTag("locked-note"))
                }
            }
            if (locked) {
                Box(Modifier.size(48.dp).border(1.dp, scheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
                    ChronaIcon(R.drawable.ic_lock_simple, size = 22.dp)
                }
            } else if (selecting) {
                SelectionMark(selected, Modifier.testTag("mark-${alarm.id}"))
            } else {
                ChronaSwitch(alarm.enabled, { onToggle(alarm.id, it) }, Modifier.testTag("toggle-${alarm.id}"))
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = scheme.surfaceContainer,
            modifier = Modifier.testTag("alarm-menu-${alarm.id}"),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.alarm_duplicate), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface) },
                onClick = {
                    menuOpen = false
                    onDuplicate(alarm.id)
                },
                modifier = Modifier.testTag("duplicate-${alarm.id}"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.alarm_select), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface) },
                onClick = {
                    menuOpen = false
                    onSelect(alarm.id)
                },
                modifier = Modifier.testTag("select-${alarm.id}"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.alarm_delete), style = MaterialTheme.typography.bodyLarge, color = scheme.error) },
                onClick = {
                    menuOpen = false
                    onDelete(alarm.id)
                },
                modifier = Modifier.testTag("delete-${alarm.id}"),
            )
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(scheme.primary) else Modifier.border(1.5.dp, scheme.secondary, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) ChronaIcon(R.drawable.ic_check, tint = scheme.onPrimary, size = 20.dp)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EyebrowTag(stringResource(R.string.empty_eyebrow))
        DoubleBezelCard(Modifier.size(96.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ChronaIcon(R.drawable.ic_alarm, size = 36.dp) }
        }
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, textAlign = TextAlign.Center)
        Text(stringResource(R.string.empty_subtitle), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        InfoPill(stringResource(R.string.empty_note), icon = R.drawable.ic_check)
    }
}
