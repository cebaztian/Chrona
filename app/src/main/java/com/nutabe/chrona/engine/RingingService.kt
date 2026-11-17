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
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import com.nutabe.chrona.R
import com.nutabe.chrona.ui.ringing.RingingActivity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Foreground service that owns a ring. The ring is persisted as an `ActiveRing` in the store, so a
 * restart with a null intent resumes it. While ringing it keeps a `BACKUP` alarm armed (ADR-012) so a
 * dead process is restarted through the normal receiver path.
 */
class RingingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val transitions = RingTransitions(NextTrigger(Clock.systemDefaultZone()))
    private lateinit var store: AlarmStore

    private var player: MediaPlayer? = null
    private var ownLock: PowerManager.WakeLock? = null
    private var ringing = false
    private var ringingAlarmId = -1L
    private var originalAlarmVolume: Int? = null
    private var rampStartMs = 0L
    private var rampDurationMs = 0L

    private val rearmBackup = object : Runnable {
        override fun run() {
            Scheduler.schedule(this@RingingService, ringingAlarmId, TriggerKind.BACKUP, Instant.now() + BACKUP_AHEAD)
            handler.postDelayed(this, BACKUP_REARM_MS)
        }
    }

    private val rampVolume = object : Runnable {
        override fun run() {
            val current = player ?: return
            val fraction = ((SystemClock.elapsedRealtime() - rampStartMs).toFloat() / rampDurationMs).coerceIn(MIN_RAMP, 1f)
            current.setVolume(fraction, fraction)
            if (fraction < 1f) handler.postDelayed(this, RAMP_STEP_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = AlarmStore.get(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action} flags=$flags startId=$startId ringing=$ringing")
        when (intent?.action) {
            ACTION_STOP -> complete()
            ACTION_SNOOZE -> snooze()
            ACTION_REPOST -> if (ringing) enterForeground(currentAlarm())
            ACTION_START -> startRinging(intent.getLongExtra(Scheduler.EXTRA_ALARM_ID, -1L))
            null -> resumeAfterRestart()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy ringing=$ringing")
        releaseResources()
        if (RingingController.state.value is RingingState.Ringing) RingingController.onIdle()
        super.onDestroy()
    }

    private fun currentAlarm(): Alarm =
        store.state.value.alarms.firstOrNull { it.id == ringingAlarmId } ?: Alarm(id = ringingAlarmId)

    private fun startRinging(alarmId: Long) {
        if (ringing) {
            Log.i(TAG, "already ringing, ignoring start for alarm $alarmId")
            WakeLockHolder.release()
            return
        }
        ringingAlarmId = alarmId
        val alarm = currentAlarm()
        enterForeground(alarm)
        ownLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Chrona:ringing")
            .apply { acquire(MAX_RING_MS) }
        WakeLockHolder.release()
        applyAlarmVolume(alarm)
        val soundOk = startSound(alarm)
        if (alarm.vibrate || !soundOk) startVibration()
        ringing = true
        RingingController.onRinging(alarmId, Instant.now())
        handler.post(rearmBackup)
        persistFire(alarmId)
    }

    private fun persistFire(alarmId: Long) {
        val appContext = applicationContext
        EngineScope.scope.launch {
            val updated = store.update { transitions.fired(it, alarmId, Instant.now()) }
            updated.alarms.firstOrNull { it.id == alarmId }
                ?.takeIf { it.enabled && !it.isOneShot }
                ?.nextTriggerAt
                ?.let { Scheduler.schedule(appContext, alarmId, TriggerKind.NEXT, it) }
        }
    }

    private fun resumeAfterRestart() {
        val ring = store.state.value.activeRing
        if (ring == null) {
            Log.i(TAG, "restarted with a null intent and no active ring: stopping")
            stopSelf()
            return
        }
        Log.i(TAG, "restarted with a null intent, active ring for alarm ${ring.alarmId}: resuming")
        startRinging(ring.alarmId)
    }

    private fun complete() {
        Log.i(TAG, "stop requested")
        val alarmId = ringingAlarmId
        Scheduler.cancel(this, alarmId, TriggerKind.BACKUP)
        finishRinging()
        RingingController.onIdle()
        EngineScope.scope.launch { store.update { transitions.completed(it, alarmId) } }
    }

    private fun snooze() {
        if (!ringing) return
        val alarmId = ringingAlarmId
        val now = Instant.now()
        val result = transitions.snoozed(store.state.value, alarmId, now)
        if (result == null) {
            Log.i(TAG, "snooze unavailable for alarm $alarmId")
            return
        }
        Log.i(TAG, "snoozed until ${result.snoozedUntil}")
        Scheduler.schedule(this, alarmId, TriggerKind.SNOOZE, result.snoozedUntil)
        Scheduler.cancel(this, alarmId, TriggerKind.BACKUP)
        RingingController.onSnoozed(alarmId, result.snoozedUntil)
        finishRinging()
        EngineScope.scope.launch { store.update { transitions.snoozed(it, alarmId, now)?.data ?: it } }
    }

    private fun finishRinging() {
        releaseResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enterForeground(alarm: Alarm) {
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
        val repost = PendingIntent.getService(
            this,
            1,
            Intent(this, RingingService::class.java).setAction(ACTION_REPOST),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.label.ifBlank { getString(R.string.ringing_title) })
            .setContentText(getString(R.string.ringing_text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setDeleteIntent(repost)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        Log.i(TAG, "foreground entered")
    }

    private fun applyAlarmVolume(alarm: Alarm) {
        runCatching {
            val audio = getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            originalAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val target = (max * alarm.maxVolumePercent / 100f).roundToInt().coerceIn(1, max)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            Log.i(TAG, "alarm stream ${originalAlarmVolume} -> $target of $max")
        }.onFailure { Log.w(TAG, "could not set the alarm stream volume", it) }
    }

    private fun restoreAlarmVolume() {
        val original = originalAlarmVolume ?: return
        originalAlarmVolume = null
        runCatching { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, original, 0) }
    }

    /** Chosen sound, then the bundled one; returns false when neither plays (vibration is the last resort). */
    private fun startSound(alarm: Alarm): Boolean {
        val sources = buildList<(MediaPlayer) -> Unit> {
            alarm.soundUri?.let { uri -> add { it.setDataSource(this@RingingService, Uri.parse(uri)) } }
            add { candidate ->
                resources.openRawResourceFd(R.raw.alarm_default).use {
                    candidate.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
        }
        for ((index, source) in sources.withIndex()) {
            val candidate = MediaPlayer()
            try {
                candidate.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                source(candidate)
                candidate.isLooping = true
                candidate.prepare()
                player = candidate
                startRamp(alarm)
                candidate.start()
                Log.i(TAG, "MediaPlayer started (source ${if (index == 0 && alarm.soundUri != null) "chosen" else "bundled"})")
                return true
            } catch (error: Exception) {
                Log.w(TAG, "sound source $index failed", error)
                player = null
                candidate.release()
            }
        }
        Log.e(TAG, "no sound could be played, falling back to vibration only")
        return false
    }

    private fun startRamp(alarm: Alarm) {
        val current = player ?: return
        if (alarm.volumeRampSeconds <= 0) {
            current.setVolume(1f, 1f)
            return
        }
        rampStartMs = SystemClock.elapsedRealtime()
        rampDurationMs = Duration.ofSeconds(alarm.volumeRampSeconds.toLong()).toMillis()
        current.setVolume(MIN_RAMP, MIN_RAMP)
        handler.postDelayed(rampVolume, RAMP_STEP_MS)
    }

    private fun startVibration() {
        getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun releaseResources() {
        handler.removeCallbacks(rearmBackup)
        handler.removeCallbacks(rampVolume)
        player?.runCatching { stop(); release() }
        player = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        ownLock?.takeIf { it.isHeld }?.release()
        ownLock = null
        WakeLockHolder.release()
        restoreAlarmVolume()
        ringing = false
    }

    companion object {
        const val TAG = "ChronaRing"
        const val ACTION_START = "com.nutabe.chrona.action.START_RING"
        const val ACTION_STOP = "com.nutabe.chrona.action.STOP_RING"
        const val ACTION_SNOOZE = "com.nutabe.chrona.action.SNOOZE_RING"
        private const val ACTION_REPOST = "com.nutabe.chrona.action.REPOST_NOTIFICATION"

        private const val CHANNEL_ID = "ringing"
        private const val NOTIFICATION_ID = 1
        private const val MIN_RAMP = 0.1f
        private const val RAMP_STEP_MS = 500L
        private const val BACKUP_REARM_MS = 20_000L
        private val BACKUP_AHEAD: Duration = Duration.ofSeconds(60)
        private val MAX_RING_MS = RingingPolicy.AUTO_SILENCE.toMillis()

        fun startIntent(context: Context, alarmId: Long): Intent =
            Intent(context, RingingService::class.java)
                .setAction(ACTION_START)
                .putExtra(Scheduler.EXTRA_ALARM_ID, alarmId)

        fun stopIntent(context: Context): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_STOP)

        fun snoozeIntent(context: Context): Intent =
            Intent(context, RingingService::class.java).setAction(ACTION_SNOOZE)
    }
}
