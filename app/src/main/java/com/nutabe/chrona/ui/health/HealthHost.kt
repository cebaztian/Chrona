package com.nutabe.chrona.ui.health

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutabe.chrona.AppContainer
import com.nutabe.chrona.engine.Mission

/** Reads what the device reports. Everything the rules in [healthReport] need, and nothing else. */
fun readHealthFacts(context: Context, hasStepsAlarm: Boolean): HealthFacts {
    val notifications = context.getSystemService(NotificationManager::class.java)
    val audio = context.getSystemService(AudioManager::class.java)
    val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
    return HealthFacts(
        exactAlarms = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        notifications = notifications.areNotificationsEnabled(),
        fullScreenIntent = notifications.canUseFullScreenIntent(),
        activityRecognition = context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED,
        hasStepsAlarm = hasStepsAlarm,
        ignoringBatteryOptimizations = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        alarmVolumePercent = audio.getStreamVolume(AudioManager.STREAM_ALARM) * 100 / maxVolume,
        dnd = when (notifications.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> DndFilter.PRIORITY
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> DndFilter.ALARMS_ONLY
            NotificationManager.INTERRUPTION_FILTER_NONE -> DndFilter.TOTAL_SILENCE
            else -> DndFilter.OFF
        },
        isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
    )
}

/** The system screen where the user can fix [kind]. */
fun settingsIntent(context: Context, kind: HealthKind): Intent {
    val packageUri = Uri.parse("package:${context.packageName}")
    return when (kind) {
        HealthKind.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
        HealthKind.NOTIFICATIONS ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        HealthKind.FULL_SCREEN_INTENT -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
        HealthKind.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        HealthKind.ALARM_VOLUME -> Intent(Settings.ACTION_SOUND_SETTINGS)
        HealthKind.DND -> Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
        HealthKind.PHYSICAL_ACTIVITY, HealthKind.SAMSUNG_GUIDE -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    }
}

/** Facts that refresh every time the screen comes back to the foreground (the user just changed a setting). */
@Composable
fun rememberHealthItems(container: AppContainer, refreshKey: Int = 0): List<HealthItem> {
    val context = LocalContext.current
    val data by container.repository.data.collectAsState()
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val hasStepsAlarm = data.alarms.any { it.mission is Mission.Steps }
    return remember(resumes, refreshKey, hasStepsAlarm) { healthReport(readHealthFacts(context, hasStepsAlarm)) }
}

@Composable
fun HealthHost(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val items = rememberHealthItems(container, refresh)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    HealthScreen(
        items = items,
        onAction = { kind ->
            if (kind == HealthKind.PHYSICAL_ACTIVITY &&
                context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                context.startActivity(settingsIntent(context, kind))
            }
        },
        onBack = onBack,
        onRefresh = { refresh++ },
    )
}
