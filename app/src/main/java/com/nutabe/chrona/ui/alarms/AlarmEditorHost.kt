package com.nutabe.chrona.ui.alarms

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutabe.chrona.AppContainer
import com.nutabe.chrona.R
import com.nutabe.chrona.engine.AlarmOutcome
import com.nutabe.chrona.engine.Mission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Connects the stateless editor to the repository, the permission system and the sound pickers. */
@Composable
fun AlarmEditorHost(container: AppContainer, alarmId: Long, copyOf: Long, onClose: () -> Unit) {
    val viewModel = viewModel(key = "editor-$alarmId-$copyOf") { AlarmEditorViewModel(container.repository, alarmId, copyOf) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var env by remember { mutableStateOf(readEnvironment(context)) }
    LifecycleResumeEffect(Unit) {
        env = readEnvironment(context)
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        env = readEnvironment(context)
        if (granted) viewModel.update { it.copy(mission = Mission.Steps()) }
    }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val picked = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        if (result.resultCode == Activity.RESULT_OK && picked != null) viewModel.update { it.copy(soundUri = picked.toString()) }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.update { it.copy(soundUri = uri.toString()) }
        }
    }

    AlarmEditorScreen(
        state = state,
        soundLabel = rememberSoundLabel(state.draft.soundUri),
        env = env,
        onChange = viewModel::update,
        onBack = onClose,
        onDelete = {
            scope.launch {
                viewModel.delete()?.let { container.deletedAlarms.value = listOf(it) }
                onClose()
            }
        },
        onSave = {
            scope.launch {
                when (viewModel.save()) {
                    is AlarmOutcome.Applied, AlarmOutcome.Locked -> onClose()
                    else -> Unit
                }
            }
        },
        onPickRingtone = {
            ringtoneLauncher.launch(
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, state.draft.soundUri?.let(Uri::parse)),
            )
        },
        onPickFile = { fileLauncher.launch(arrayOf("audio/*")) },
        onRequestStepsPermission = { permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
    )
}

private fun readEnvironment(context: Context): EditorEnvironment {
    val sensors = context.getSystemService(SensorManager::class.java)
    val audio = context.getSystemService(AudioManager::class.java)
    return EditorEnvironment(
        hasStepSensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null ||
            sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null,
        activityRecognitionGranted =
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED,
        alarmStreamMuted = audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0 || audio.isStreamMute(AudioManager.STREAM_ALARM),
    )
}

/** "Default" for the bundled sound, otherwise the ringtone title or the file name, else "Custom sound". */
@Composable
private fun rememberSoundLabel(soundUri: String?): String {
    val context = LocalContext.current
    val default = stringResource(R.string.sound_default)
    val custom = stringResource(R.string.sound_custom)
    return produceState(if (soundUri == null) default else custom, soundUri) {
        value = if (soundUri == null) {
            default
        } else {
            withContext(Dispatchers.IO) { resolveSoundTitle(context, Uri.parse(soundUri)) } ?: custom
        }
    }.value
}

private fun resolveSoundTitle(context: Context, uri: Uri): String? = runCatching {
    RingtoneManager.getRingtone(context, uri)?.getTitle(context)?.takeIf { it.isNotBlank() }
        ?: context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}.getOrNull()
