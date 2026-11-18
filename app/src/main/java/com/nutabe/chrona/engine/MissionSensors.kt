package com.nutabe.chrona.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.util.Log
import com.nutabe.chrona.mission.GravityFilter
import com.nutabe.chrona.mission.MissionRunner

/** Debug hooks for the emulator, which has no step sensor. Nothing but the debug build sets them. */
object SensorOverrides {
    @Volatile
    var fakeStepSensor = false

    @Volatile
    var suppressEvents = false
}

/**
 * Feeds a [MissionRunner] from the device sensors. Registered only while a sensor mission runs and
 * unregistered the moment it ends or is replaced. Timestamps use the boot clock, like the service ticks.
 */
class MissionSensors(context: Context, private val handler: Handler) {
    private val manager = context.getSystemService(SensorManager::class.java)
    private var listener: SensorEventListener? = null

    val hasShake: Boolean
        get() = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null ||
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    val hasSteps: Boolean
        get() = SensorOverrides.fakeStepSensor ||
            manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null ||
            manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun start(runner: MissionRunner, onEvent: () -> Unit) {
        stop()
        when (runner.kind) {
            MissionRunner.Kind.SHAKE -> startShake(runner, onEvent)
            MissionRunner.Kind.STEPS -> startSteps(runner, onEvent)
            MissionRunner.Kind.MATH -> Unit
        }
    }

    fun stop() {
        listener?.let { manager.unregisterListener(it) }
        listener = null
    }

    private fun startShake(runner: MissionRunner, onEvent: () -> Unit) {
        val linear = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linear ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val filter = GravityFilter()
        val shakeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (SensorOverrides.suppressEvents) return
                val (x, y, z) = if (linear != null) {
                    Triple(event.values[0], event.values[1], event.values[2])
                } else {
                    filter.filter(event.values[0], event.values[1], event.values[2])
                }
                runner.onShake(event.timestamp / NANOS_PER_MS, x, y, z)
                onEvent()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(shakeListener, sensor, SensorManager.SENSOR_DELAY_GAME, handler)
        listener = shakeListener
        Log.i(TAG, "shake sensor registered: ${sensor.name}")
    }

    private fun startSteps(runner: MissionRunner, onEvent: () -> Unit) {
        val detector = manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val counter = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val sensor = detector ?: counter ?: return
        val stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (SensorOverrides.suppressEvents) return
                val now = event.timestamp / NANOS_PER_MS
                if (detector != null) runner.onStep(now) else runner.onStepCounter(event.values[0], now)
                onEvent()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_FASTEST, 0, handler)
        listener = stepListener
        Log.i(TAG, "step sensor registered: ${sensor.name}")
    }

    private companion object {
        const val TAG = RingingService.TAG
        const val NANOS_PER_MS = 1_000_000L
    }
}
