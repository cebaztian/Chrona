package com.nutabe.chrona.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nutabe.chrona.R
import com.nutabe.chrona.ui.theme.ChronaText
import com.nutabe.chrona.ui.theme.ChronaTheme

/** Debug-only sheet to check the theme on a screenshot: type scale, tabular figures, icons and colors. */
class DesignSheetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChronaTheme { Sheet() } }
    }
}

private val lightIcons = listOf(
    R.drawable.ic_gear_six, R.drawable.ic_lock_simple, R.drawable.ic_bell, R.drawable.ic_bell_ringing,
    R.drawable.ic_plus, R.drawable.ic_check, R.drawable.ic_arrow_right, R.drawable.ic_alarm,
    R.drawable.ic_calculator, R.drawable.ic_vibrate, R.drawable.ic_person_simple_walk, R.drawable.ic_speaker_low,
    R.drawable.ic_speaker_high, R.drawable.ic_warning, R.drawable.ic_warning_circle, R.drawable.ic_trash,
    R.drawable.ic_x, R.drawable.ic_caret_left, R.drawable.ic_caret_right, R.drawable.ic_music_notes,
    R.drawable.ic_battery_charging, R.drawable.ic_moon, R.drawable.ic_shield_check, R.drawable.ic_clock,
    R.drawable.ic_pencil_simple, R.drawable.ic_arrow_u_up_left,
)

private val regularIcons = listOf(
    R.drawable.ic_alarm_regular, R.drawable.ic_calculator_regular, R.drawable.ic_vibrate_regular,
    R.drawable.ic_person_simple_walk_regular, R.drawable.ic_speaker_low_regular, R.drawable.ic_check_regular,
    R.drawable.ic_arrow_right_regular, R.drawable.ic_lock_simple_regular, R.drawable.ic_bell_ringing_regular,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sheet() {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("PRÓXIMA ALARMA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text("06:00", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
        Text("06:30 · 11:11 · 20:08", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Text("Alarmas", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Suena en 7 h 12 min · Gimnasio · LMXJV",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text("12", style = ChronaText.counter, color = MaterialTheme.colorScheme.primary)
            Text(" / 30", style = ChronaText.counterTotal, color = MaterialTheme.colorScheme.secondary)
        }
        Text("17 + 26 = 43", style = ChronaText.math, color = MaterialTheme.colorScheme.onSurface)
        Text("Light (24 dp)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        IconRow(lightIcons)
        Text("Regular", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        IconRow(regularIcons)
        Text("Palette", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val scheme = MaterialTheme.colorScheme
            listOf(
                scheme.background, scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest, scheme.secondary, scheme.primary, scheme.error, scheme.outlineVariant,
            ).forEach { Box(Modifier.size(36.dp).background(it)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconRow(icons: List<Int>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        icons.forEach {
            Icon(painterResource(it), contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp))
        }
    }
}
