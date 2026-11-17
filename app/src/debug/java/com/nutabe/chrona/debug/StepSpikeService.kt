package com.nutabe.chrona.debug

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/**
 * M1a step spike: a silent foreground service (same type as `RingingService`) that registers the
 * step detector and counter and logs every event with a timestamp.
 */
class StepSpikeService : Service(), SensorEventListener {
    private var lock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSpike()
            ACTION_START -> startSpike()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregister()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        Log.i(TAG, "STEP ${event.sensor.stringType} value=${event.values.firstOrNull()} " +
            "sensorTs=${event.timestamp} elapsed=${SystemClock.elapsedRealtime()}")
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private fun startSpike() {
        enterForeground()
        val granted = checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "start: ACTIVITY_RECOGNITION granted=$granted")
        val manager = getSystemService(SensorManager::class.java).also { sensorManager = it }
        lock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Chrona:stepspike")
            .apply { acquire(MAX_SPIKE_MS) }
        listOf(Sensor.TYPE_STEP_DETECTOR, Sensor.TYPE_STEP_COUNTER).forEach { type ->
            val sensor = manager.getDefaultSensor(type)
            if (sensor == null) {
                Log.w(TAG, "sensor type $type unavailable on this device")
            } else {
                val registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0)
                Log.i(TAG, "registered ${sensor.name} (${sensor.stringType}) ok=$registered")
            }
        }
    }

    private fun stopSpike() {
        Log.i(TAG, "stop")
        unregister()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun unregister() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        lock?.takeIf { it.isHeld }?.release()
        lock = null
    }

    private fun enterForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Step spike", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Step spike running")
            .setOngoing(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
    }

    companion object {
        const val TAG = "ChronaStepSpike"
        private const val ACTION_START = "com.nutabe.chrona.debug.action.START_STEP_SPIKE"
        private const val ACTION_STOP = "com.nutabe.chrona.debug.action.STOP_STEP_SPIKE"
        private const val CHANNEL_ID = "step_spike"
        private const val NOTIFICATION_ID = 2
        private const val MAX_SPIKE_MS = 10 * 60_000L

        fun startIntent(context: Context): Intent =
            Intent(context, StepSpikeService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, StepSpikeService::class.java).setAction(ACTION_STOP)
    }
}
