package com.nutabe.chrona.ui.ringing

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nutabe.chrona.R
import com.nutabe.chrona.engine.AlarmPlayer
import com.nutabe.chrona.engine.Mission
import com.nutabe.chrona.engine.MissionSensors
import com.nutabe.chrona.mission.MissionEnvironment
import com.nutabe.chrona.mission.MissionResolver
import com.nutabe.chrona.mission.MissionRunner
import com.nutabe.chrona.ui.components.SecondaryPill
import kotlinx.coroutines.delay

/**
 * Rehearses a mission exactly as it runs at ring time, but without `RingingService`: the runner, the
 * sensors and the optional sound live and die with this dialog. Sensors are registered only while it is open.
 */
@Composable
fun MissionPreview(
    mission: Mission,
    soundUri: String?,
    environment: MissionEnvironment,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val resolved = remember { MissionResolver.resolve(mission, environment) }
    if (resolved.mission == Mission.None) return
    val runner = remember { MissionRunner(resolved.mission, SystemClock.elapsedRealtime()) }
    var view by remember { mutableStateOf(runner.view) }
    var soundOff by remember { mutableStateOf(false) }
    val player = remember { AlarmPlayer(context) }

    fun refresh() {
        view = runner.view
    }

    DisposableEffect(runner) {
        val sensors = MissionSensors(context, Handler(Looper.getMainLooper()))
        sensors.start(runner, ::refresh)
        onDispose {
            sensors.stop()
            player.release()
        }
    }
    LaunchedEffect(soundOff) {
        player.release()
        if (!soundOff) player.start(soundUri, DUCK_GAIN)
    }
    LaunchedEffect(runner) {
        while (true) {
            val inactive = runner.tick(SystemClock.elapsedRealtime())
            player.setGain(if (inactive) 1f else DUCK_GAIN)
            refresh()
            delay(1_000)
        }
    }
    LaunchedEffect(view.done) {
        if (view.done) {
            delay(DONE_SHOW_MS)
            onClose()
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().testTag("mission-preview-screen")) {
            MissionScreen(
                view = view,
                onSubmitMath = {
                    runner.submitMath(it, SystemClock.elapsedRealtime())
                    refresh()
                },
                onTouch = { runner.touch(SystemClock.elapsedRealtime()) },
                onSwitchToMath = {
                    runner.switchToMath(SystemClock.elapsedRealtime())
                    if (runner.kind == MissionRunner.Kind.MATH) {
                        refresh()
                    }
                },
                modifier = Modifier.padding(top = 56.dp),
            )
            Row(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                SecondaryPill(
                    stringResource(R.string.preview_sound_off),
                    onClick = { soundOff = !soundOff },
                    leadingIcon = if (soundOff) R.drawable.ic_check_regular else null,
                    height = 40.dp,
                    modifier = Modifier.testTag("preview-sound-off"),
                )
                SecondaryPill(
                    stringResource(R.string.preview_close),
                    onClick = onClose,
                    leadingIcon = R.drawable.ic_x_circle_regular,
                    height = 40.dp,
                    modifier = Modifier.testTag("preview-close"),
                )
            }
        }
    }
}

private const val DUCK_GAIN = 0.2f
private const val DONE_SHOW_MS = 800L
