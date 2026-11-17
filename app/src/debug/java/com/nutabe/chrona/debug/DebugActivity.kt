package com.nutabe.chrona.debug

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.nutabe.chrona.engine.Alarm
import com.nutabe.chrona.engine.AlarmStore
import com.nutabe.chrona.engine.EngineScope
import com.nutabe.chrona.engine.Scheduler
import com.nutabe.chrona.engine.TriggerKind
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/** M1a debug screen: permission status with deep links and hardcoded ring triggers. */
class DebugActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ringFromIntent(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DebugScreen(refresh)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ringFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    private fun ringFromIntent(source: Intent) {
        source.getLongExtra(EXTRA_RING_IN_SECONDS, 0L).takeIf { it > 0 }?.let(::ringIn)
        source.getLongExtra(EXTRA_ADD_ALARM_SECONDS, 0L).takeIf { it > 0 }
            ?.let {
                addAlarm(it, source.getBooleanExtra(EXTRA_INVALID_SOUND, false), source.getBooleanExtra(EXTRA_REPEAT_DAILY, false))
            }
    }

    /** Stores an alarm that fires in [seconds] and schedules it, optionally with a broken sound URI. */
    private fun addAlarm(seconds: Long, invalidSound: Boolean, repeatDaily: Boolean = false) {
        val appContext = applicationContext
        EngineScope.scope.launch {
            val at = Instant.now().plusSeconds(seconds)
            val local = at.atZone(ZoneId.systemDefault())
            val updated = AlarmStore.get(appContext).update { data ->
                val id = (data.alarms.maxOfOrNull { it.id } ?: 0L) + 1
                data.copy(
                    alarms = data.alarms + Alarm(
                        id = id,
                        hour = local.hour,
                        minute = local.minute,
                        label = if (invalidSound) "debug (invalid sound)" else "debug",
                        soundUri = if (invalidSound) INVALID_SOUND_URI else null,
                        repeatDays = if (repeatDaily) java.time.DayOfWeek.entries.toSet() else emptySet(),
                        volumeRampSeconds = 0,
                        nextTriggerAt = at,
                    ),
                )
            }
            val created = updated.alarms.maxBy { it.id }
            Scheduler.schedule(appContext, created.id, TriggerKind.NEXT, at)
        }
    }

    private fun ringIn(seconds: Long) {
        Scheduler.schedule(this, DEBUG_ALARM_ID, TriggerKind.NEXT, Instant.now().plusSeconds(seconds))
        Toast.makeText(this, "Ringing in $seconds s", Toast.LENGTH_SHORT).show()
    }

    private fun openSettings(action: String, withPackage: Boolean = true) {
        val settingsIntent = Intent(action)
        if (withPackage) settingsIntent.data = Uri.parse("package:$packageName")
        startActivity(settingsIntent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    @androidx.compose.runtime.Composable
    private fun DebugScreen(@Suppress("UNUSED_PARAMETER") tick: Int) {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val notificationManager = getSystemService(NotificationManager::class.java)
        val powerManager = getSystemService(PowerManager::class.java)
        val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
        val recognitionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
        val recognitionGranted = checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Chrona debug", style = MaterialTheme.typography.headlineSmall)
            StatusRow("Exact alarms", alarmManager.canScheduleExactAlarms()) {
                openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
            StatusRow("Notifications", notificationManager.areNotificationsEnabled()) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, withPackage = false)
                } else {
                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            StatusRow("Full-screen intent", notificationManager.canUseFullScreenIntent()) {
                openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
            StatusRow("Physical activity", recognitionGranted) {
                recognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            StatusRow("Battery unrestricted", powerManager.isIgnoringBatteryOptimizations(packageName)) {
                openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, withPackage = false)
            }
            Button(onClick = { addAlarm(60, invalidSound = false) }) { Text("Add stored alarm +1 min") }
            Button(onClick = { addAlarm(60, invalidSound = true) }) { Text("Add stored alarm +1 min, invalid sound") }
            val stored = AlarmStore.get(this@DebugActivity).state.collectAsState().value.alarms
            Text("Stored alarms: ${stored.size}")
            stored.forEach { alarm ->
                Text("#${alarm.id} ${alarm.label} next=${alarm.nextTriggerAt} snooze=${alarm.snoozedUntil} used=${alarm.snoozesUsed} on=${alarm.enabled}")
            }
            Button(onClick = { ringIn(5) }) { Text("Ring in 5 s") }
            Button(onClick = { ringIn(60) }) { Text("Ring in 1 min") }
            Button(onClick = { startForegroundService(StepSpikeService.startIntent(this@DebugActivity)) }) {
                Text("Step spike: start")
            }
            Button(onClick = { startService(StepSpikeService.stopIntent(this@DebugActivity)) }) {
                Text("Step spike: stop")
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun StatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$label: ${if (ok) "OK" else "MISSING"}")
            Button(onClick = onFix) { Text("Open") }
        }
    }

    companion object {
        const val EXTRA_RING_IN_SECONDS = "ring_in_seconds"
        const val EXTRA_ADD_ALARM_SECONDS = "add_alarm_seconds"
        const val EXTRA_INVALID_SOUND = "invalid_sound"
        const val EXTRA_REPEAT_DAILY = "repeat_daily"
        private const val INVALID_SOUND_URI = "content://com.nutabe.chrona.invalid/missing.mp3"
        private const val DEBUG_ALARM_ID = 1L
    }
}
