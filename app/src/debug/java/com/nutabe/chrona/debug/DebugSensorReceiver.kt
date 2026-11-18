package com.nutabe.chrona.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nutabe.chrona.engine.RingingController
import com.nutabe.chrona.engine.SensorOverrides

/**
 * Debug-only hooks for the emulator: `--ez fake_steps true` pretends a step sensor exists,
 * `--ez suppress true` drops every sensor event, `--ei steps N --el interval_ms M` injects N steps,
 * `--ei alarm_volume N` lowers the alarm stream like the volume panel would.
 */
class DebugSensorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra("fake_steps")) SensorOverrides.fakeStepSensor = intent.getBooleanExtra("fake_steps", false)
        if (intent.hasExtra("suppress")) SensorOverrides.suppressEvents = intent.getBooleanExtra("suppress", false)
        intent.getIntExtra("alarm_volume", -1).takeIf { it >= 0 }?.let {
            context.getSystemService(android.media.AudioManager::class.java)
                .setStreamVolume(android.media.AudioManager.STREAM_ALARM, it, 0)
        }
        val steps = intent.getIntExtra("steps", 0)
        if (steps > 0) RingingController.actions?.debugSteps(steps, intent.getLongExtra("interval_ms", 600L))
    }
}
