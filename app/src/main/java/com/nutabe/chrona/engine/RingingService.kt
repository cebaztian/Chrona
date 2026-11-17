package com.nutabe.chrona.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import com.nutabe.chrona.R
import com.nutabe.chrona.ui.ringing.RingingActivity
import java.io.File

/**
 * Foreground service that owns a ring. Skeleton for M1a: one bundled sound, vibration and a
 * full-screen intent. The active-ring marker is a file in device-protected storage so a restart
 * with a null intent can resume (restart spike).
 */
class RingingService : Service() {
    private var player: MediaPlayer? = null
    private var ownLock: PowerManager.WakeLock? = null
    private var ringing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action} flags=$flags startId=$startId ringing=$ringing")
        when (intent?.action) {
            ACTION_STOP -> stopRinging()
            ACTION_START -> startRinging(intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L))
            null -> resumeAfterRestart()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy ringing=$ringing")
        releaseResources()
        super.onDestroy()
    }

    private fun startRinging(alarmId: Long) {
        if (ringing) {
            Log.i(TAG, "already ringing, ignoring start for alarm $alarmId")
            WakeLockHolder.release()
            return
        }
        markerFile().writeText(alarmId.toString())
        enterForeground()
        ownLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Chrona:ringing")
            .apply { acquire(MAX_RING_MS) }
        WakeLockHolder.release()
        startSound()
        startVibration()
        ringing = true
    }

    private fun resumeAfterRestart() {
        val marker = markerFile()
        if (!marker.exists()) {
            Log.i(TAG, "restarted with a null intent and no active ring: stopping")
            stopSelf()
            return
        }
        Log.i(TAG, "restarted with a null intent, active ring found: resuming")
        startRinging(marker.readText().toLongOrNull() ?: -1L)
    }

    private fun stopRinging() {
        Log.i(TAG, "stop requested")
        markerFile().delete()
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enterForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.ringing_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.ringing_title))
            .setContentText(getString(R.string.ringing_text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        Log.i(TAG, "foreground entered")
    }

    private fun startSound() {
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                resources.openRawResourceFd(R.raw.alarm_default).use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                isLooping = true
                prepare()
                start()
            }
            Log.i(TAG, "MediaPlayer started")
        }.onFailure { Log.e(TAG, "MediaPlayer failed", it) }
    }

    private fun startVibration() {
        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun releaseResources() {
        player?.runCatching { stop(); release() }
        player = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        ownLock?.takeIf { it.isHeld }?.release()
        ownLock = null
        WakeLockHolder.release()
        ringing = false
    }

    private fun markerFile(): File =
        File(createDeviceProtectedStorageContext().filesDir, ACTIVE_RING_FILE)

    companion object {
        const val TAG = "ChronaRing"
        const val ACTION_START = "com.nutabe.chrona.action.START_RING"
        const val ACTION_STOP = "com.nutabe.chrona.action.STOP_RING"

        private const val CHANNEL_ID = "ringing"
        private const val NOTIFICATION_ID = 1
        private const val ACTIVE_RING_FILE = "active_ring"
        private const val MAX_RING_MS = 15 * 60_000L

        fun startIntent(context: Context, alarmId: Long): Intent =
            Intent(context, RingingService::class.java)
                .setAction(ACTION_START)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)

        fun stopIntent(context: Context): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_STOP)
    }
}
