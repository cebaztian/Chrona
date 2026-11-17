package com.nutabe.chrona.ui.health

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutabe.chrona.R
import com.nutabe.chrona.ui.components.ChronaIcon
import com.nutabe.chrona.ui.components.DoubleBezelCard
import com.nutabe.chrona.ui.components.EyebrowTag
import com.nutabe.chrona.ui.components.PillCta
import com.nutabe.chrona.ui.components.SecondaryPill

/** Stateless: the rows come from [healthReport] and every tap goes out as [onAction]. */
@Composable
fun HealthScreen(
    items: List<HealthItem>,
    onAction: (HealthKind) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val permissions = items.filter { it.kind in PERMISSION_KINDS }
    val device = items.filter { it.kind in DEVICE_KINDS }
    val samsung = items.firstOrNull { it.kind == HealthKind.SAMSUNG_GUIDE }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HealthTopBar(onBack, onRefresh) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Legend()
            EyebrowTag(stringResource(R.string.health_section_permissions))
            DoubleBezelCard(Modifier.fillMaxWidth().testTag("health-permissions")) {
                permissions.forEachIndexed { index, item ->
                    if (index > 0) Divider()
                    HealthRow(item, onAction)
                }
            }
            EyebrowTag(stringResource(R.string.health_section_device))
            DoubleBezelCard(Modifier.fillMaxWidth().testTag("health-device")) {
                device.forEachIndexed { index, item ->
                    if (index > 0) Divider()
                    HealthRow(item, onAction)
                }
            }
            if (samsung != null) SamsungCard(onAction = { onAction(HealthKind.SAMSUNG_GUIDE) })
        }
    }
}

private val PERMISSION_KINDS = setOf(HealthKind.EXACT_ALARMS, HealthKind.NOTIFICATIONS, HealthKind.FULL_SCREEN_INTENT, HealthKind.PHYSICAL_ACTIVITY)
private val DEVICE_KINDS = setOf(HealthKind.BATTERY, HealthKind.ALARM_VOLUME, HealthKind.DND)

@Composable
private fun HealthTopBar(onBack: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).testTag("health-back"), contentAlignment = Alignment.Center) {
                ChronaIcon(R.drawable.ic_caret_left, contentDescription = stringResource(R.string.back_cd))
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(stringResource(R.string.health_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.health_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onRefresh).testTag("health-refresh"), contentAlignment = Alignment.Center) {
                ChronaIcon(R.drawable.ic_arrow_clockwise, contentDescription = stringResource(R.string.health_refresh_cd))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LegendEntry(HealthStatus.OK, R.string.health_ok)
        LegendEntry(HealthStatus.ATTENTION, R.string.health_attention)
        LegendEntry(HealthStatus.BLOCKING, R.string.health_blocking)
    }
}

@Composable
private fun LegendEntry(status: HealthStatus, @StringRes label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChronaIcon(statusIcon(status), tint = statusColor(status), size = 18.dp)
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = statusColor(status))
    }
}

@DrawableRes
fun statusIcon(status: HealthStatus): Int = when (status) {
    HealthStatus.OK -> R.drawable.ic_check
    HealthStatus.ATTENTION -> R.drawable.ic_warning
    HealthStatus.BLOCKING -> R.drawable.ic_warning_circle
}

@Composable
fun statusColor(status: HealthStatus): Color = when (status) {
    HealthStatus.OK -> MaterialTheme.colorScheme.secondary
    HealthStatus.ATTENTION -> MaterialTheme.colorScheme.primary
    HealthStatus.BLOCKING -> MaterialTheme.colorScheme.error
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun HealthRow(item: HealthItem, onAction: (HealthKind) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fixable = item.status != HealthStatus.OK
    val value = itemValue(item)
    val detail = itemDetail(item)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("health-${item.kind.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChronaIcon(statusIcon(item.status), tint = statusColor(item.status), size = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(itemLabel(item.kind)), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                // A row that needs attention keeps its state in words next to the detail, never color alone.
                val second = if (fixable) listOfNotNull(value, detail).joinToString(" · ") else detail.orEmpty()
                if (second.isNotEmpty()) {
                    Text(second, style = MaterialTheme.typography.bodySmall, color = if (fixable) statusColor(item.status) else scheme.onSurfaceVariant)
                }
            }
            if (!fixable && value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (fixable) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SecondaryPill(stringResource(R.string.health_configure), { onAction(item.kind) }, height = 44.dp, modifier = Modifier.testTag("fix-${item.kind.name.lowercase()}"))
            }
        }
    }
}

@StringRes
private fun itemLabel(kind: HealthKind): Int = when (kind) {
    HealthKind.EXACT_ALARMS -> R.string.health_exact_alarms
    HealthKind.NOTIFICATIONS -> R.string.health_notifications
    HealthKind.FULL_SCREEN_INTENT -> R.string.health_full_screen
    HealthKind.PHYSICAL_ACTIVITY -> R.string.health_activity
    HealthKind.BATTERY -> R.string.health_battery
    HealthKind.ALARM_VOLUME -> R.string.health_volume
    HealthKind.DND -> R.string.health_dnd
    HealthKind.SAMSUNG_GUIDE -> R.string.health_samsung
}

@Composable
private fun itemValue(item: HealthItem): String? = when (item.kind) {
    HealthKind.EXACT_ALARMS, HealthKind.NOTIFICATIONS, HealthKind.FULL_SCREEN_INTENT, HealthKind.PHYSICAL_ACTIVITY ->
        stringResource(if (item.status == HealthStatus.OK) R.string.health_granted else R.string.health_missing)
    HealthKind.BATTERY -> if (item.status == HealthStatus.OK) stringResource(R.string.health_battery_ok) else null
    HealthKind.ALARM_VOLUME ->
        if ((item.percent ?: 0) > 0) stringResource(R.string.health_volume_value, item.percent ?: 0) else stringResource(R.string.health_volume_muted)
    HealthKind.DND -> null
    HealthKind.SAMSUNG_GUIDE -> null
}

@Composable
private fun itemDetail(item: HealthItem): String? = when (item.kind) {
    HealthKind.PHYSICAL_ACTIVITY -> stringResource(R.string.health_activity_detail)
    HealthKind.BATTERY -> if (item.status != HealthStatus.OK) stringResource(R.string.health_battery_bad) else null
    HealthKind.DND -> stringResource(
        when (item.dnd) {
            DndFilter.PRIORITY -> R.string.dnd_priority
            DndFilter.ALARMS_ONLY -> R.string.dnd_alarms
            DndFilter.TOTAL_SILENCE -> R.string.dnd_total
            else -> R.string.dnd_off
        },
    )
    else -> null
}

@Composable
private fun SamsungCard(onAction: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DoubleBezelCard(Modifier.fillMaxWidth().testTag("health-samsung")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ChronaIcon(statusIcon(HealthStatus.ATTENTION), tint = statusColor(HealthStatus.ATTENTION), size = 22.dp)
                Text(stringResource(R.string.health_samsung), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            }
            Text(stringResource(R.string.health_samsung_text), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            PillCta(stringResource(R.string.health_open_settings), onAction, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}
