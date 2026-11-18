package com.nutabe.chrona.ui.ringing

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nutabe.chrona.engine.RingingController
import com.nutabe.chrona.engine.RingingState
import com.nutabe.chrona.engine.liveAlarmId
import com.nutabe.chrona.ui.theme.ChronaTheme
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * Shows the ring owned by `RingingService`. Back and the volume keys do nothing; the activity only
 * renders [RingingController.state] and forwards the user's actions, then finishes when the ring ends.
 */
class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ChronaTheme {
                BackHandler {}
                RingingHost(onFinish = ::finish)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> true
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> true
        else -> super.onKeyUp(keyCode, event)
    }
}

@Composable
private fun RingingHost(onFinish: () -> Unit) {
    val context = LocalContext.current
    val state by RingingController.state.collectAsState()
    var seenRing by remember { mutableStateOf(false) }
    val live = state.liveAlarmId != null
    LaunchedEffect(live) {
        if (live) {
            seenRing = true
        } else {
            delay(if (seenRing) ENDED_DELAY_MS else START_GRACE_MS)
            onFinish()
        }
    }
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000)
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val current = state) {
            is RingingState.Ringing -> RingingScreen(
                info = current.info,
                now = now,
                onStartMission = { RingingController.actions?.startMission() },
                onStop = { RingingController.stop(context) },
                onSnooze = { RingingController.snooze(context) },
            )
            is RingingState.MissionActive -> MissionScreen(
                view = current.view,
                onSubmitMath = { RingingController.actions?.submitMath(it) },
                onTouch = { RingingController.actions?.touch() },
                onSwitchToMath = { RingingController.actions?.switchToMath() },
            )
            else -> Unit
        }
    }
}

private const val ENDED_DELAY_MS = 700L
private const val START_GRACE_MS = 4_000L
