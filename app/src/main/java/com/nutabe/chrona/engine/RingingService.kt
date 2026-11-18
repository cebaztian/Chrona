package com.nutabe.chrona.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import com.nutabe.chrona.R
import com.nutabe.chrona.mission.MissionEnvironment
import com.nutabe.chrona.mission.MissionResolver
import com.nutabe.chrona.mission.MissionRunner
import com.nutabe.chrona.ui.ringing.RingingActivity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Foreground service that owns a ring. The ring is persisted as an `ActiveRing` in the store, so a
 * restart with a null intent resumes it. While ringing it keeps a `BACKUP` alarm armed (ADR-012) so a
 * dead process is restarted through the normal receiver path. It also runs the mission, so a pocketed
 * phone still counts steps; the activity only shows [RingingController.state].
 */
class RingingService : Service(), RingActions {
    private val handler = Handler(Looper.getMainLooper())
    private val transitions = RingTransitions(NextTrigger(Clock.systemDefaultZone()))
    private lateinit var store: AlarmStore
    private lateinit var sensors: MissionSensors
    private lateinit var alarmPlayer: AlarmPlayer

    private var ownLock: PowerManager.WakeLock? = null
    private var ringing = false
    private var ringingAlarmId = -1L
    private var info: RingInfo? = null
    private var runner: MissionRunner? = null
    private var missionDone = false
    private var vibrates = false
    private var ending = false
    private val deferredStarts = mutableListOf<Long>()
    private var lastStartId = 0

    private var originalAlarmVolume: Int? = null
    private var targetAlarmVolume = 0
    private var volumeObserver: ContentObserver? = null
    private var rampFraction = 1f
    private var ducked = false
    private var audioFocus: AudioFocusRequest? = null
    private var modeListener: AudioManager.OnModeChangedListener? = null
    private var inCall = false
    private var tone: ToneGenerator? = null
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
            rampFraction = ((SystemClock.elapsedRealtime() - rampStartMs).toFloat() / rampDurationMs).coerceIn(MIN_RAMP, 1f)
            applyGain()
            if (rampFraction < 1f) handler.postDelayed(this, RAMP_STEP_MS)
        }
    }

    private val missionTick = object : Runnable {
        override fun run() {
            onMissionEvent()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val callBeep = object : Runnable {
        override fun run() {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, CALL_BEEP_LENGTH_MS)
            handler.postDelayed(this, CALL_BEEP_EVERY_MS)
        }
    }

    private val autoSilence = Runnable { onAutoSilence() }
    private val finishMission = Runnable { if (ringing) complete() }

    override fun onCreate() {
        super.onCreate()
        store = AlarmStore.get(this)
        sensors = MissionSensors(this, handler)
        alarmPlayer = AlarmPlayer(this)
        RingingController.actions = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        Log.i(TAG, "onStartCommand action=${intent?.action} flags=$flags startId=$startId ringing=$ringing")
        when (intent?.action) {
            ACTION_STOP -> requestStop()
            ACTION_SNOOZE -> snooze()
            ACTION_REPOST -> if (ringing) enterForeground(currentAlarm())
            ACTION_START -> startRinging(intent.getLongExtra(Scheduler.EXTRA_ALARM_ID, -1L))
            null -> resumeAfterRestart()
        }
        if (intent?.action in IDLE_STOPPABLE && !ringing && !ending) stopSelf(startId)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy ringing=$ringing")
        releaseResources()
        RingingController.actions = null
        if (RingingController.state.value.liveAlarmId != null) RingingController.onIdle()
        super.onDestroy()
    }

    private fun currentAlarm(): Alarm =
        store.state.value.alarms.firstOrNull { it.id == ringingAlarmId } ?: Alarm(id = ringingAlarmId)

    private fun startRinging(alarmId: Long) {
        if (ending) {
            Log.i(TAG, "a ring is ending, deferring alarm $alarmId")
            deferredStarts += alarmId
            WakeLockHolder.release()
            return
        }
        if (ringing) {
            if (alarmId != ringingAlarmId) {
                Log.i(TAG, "alarm $ringingAlarmId rings, queueing alarm $alarmId")
                persistFire(alarmId)
            }
            WakeLockHolder.release()
            return
        }
        val active = store.state.value.activeRing
        val target = if (active != null && active.alarmId != alarmId) active.alarmId else alarmId
        if (target != alarmId) {
            Log.i(TAG, "alarm $target still owns the ring, queueing alarm $alarmId")
            persistFire(alarmId)
        }
        begin(target)
    }

    private fun begin(alarmId: Long) {
        ringingAlarmId = alarmId
        val alarm = currentAlarm()
        val ringInfo = buildInfo(alarm)
        info = ringInfo
        enterForeground(alarm)
        ownLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Chrona:ringing")
            .apply { acquire(MAX_RING_MS + Duration.ofMinutes(1).toMillis()) }
        WakeLockHolder.release()
        applyAlarmVolume(alarm)
        watchAlarmVolume()
        runner = null
        missionDone = false
        ducked = false
        prepareRamp(alarm)
        val soundOk = alarmPlayer.start(alarm.soundUri, rampFraction)
        if (soundOk) startRamp()
        vibrates = alarm.vibrate
        if (vibrates || !soundOk) startVibration()
        ringing = true
        watchCalls()
        RingingController.publish(RingingState.Ringing(ringInfo))
        handler.post(rearmBackup)
        scheduleAutoSilence(alarmId)
        persistFire(alarmId)
    }

    private fun buildInfo(alarm: Alarm): RingInfo {
        val env = MissionEnvironment(
            hasShakeSensor = sensors.hasShake,
            hasStepSensor = sensors.hasSteps,
            activityRecognitionGranted = SensorOverrides.fakeStepSensor ||
                checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED,
        )
        val resolved = MissionResolver.resolve(alarm.mission, env)
        val now = Instant.now()
        return RingInfo(
            alarmId = alarm.id,
            startedAt = store.state.value.activeRing?.takeIf { it.alarmId == alarm.id }?.startedAt ?: now,
            label = alarm.label,
            mission = resolved.mission,
            fellBackToMath = resolved.fellBackToMath,
            canSnooze = transitions.snoozed(store.state.value, alarm.id, now) != null,
            snoozeMinutes = alarm.snooze.minutes,
            snoozesLeft = (alarm.snooze.maxCount - alarm.snoozesUsed).coerceAtLeast(0),
            snoozeMax = alarm.snooze.maxCount,
        )
    }

    private fun scheduleAutoSilence(alarmId: Long) {
        val now = Instant.now()
        val started = store.state.value.activeRing?.takeIf { it.alarmId == alarmId }?.startedAt ?: now
        val remaining = (RingingPolicy.AUTO_SILENCE - Duration.between(started, now)).toMillis()
        handler.postDelayed(autoSilence, remaining.coerceIn(0L, MAX_RING_MS))
    }

    private fun persistFire(alarmId: Long) {
        val appContext = applicationContext
        EngineScope.serial.launch {
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

    /** Stop is only honored when there is no mission or it is solved; the mission itself ends the ring. */
    private fun requestStop() {
        val current = info
        if (!ringing || current == null) return
        if (StopGate.allows(current.mission, missionDone)) {
            complete()
        } else {
            Log.w(TAG, "stop refused: the mission is not solved")
        }
    }

    private fun complete() {
        Log.i(TAG, "stop requested")
        val alarmId = ringingAlarmId
        Scheduler.cancel(this, alarmId, TriggerKind.BACKUP)
        endRing(RingingState.Idle) { transitions.completed(it, alarmId) }
    }

    private fun snooze() {
        if (!ringing || ending) return
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
        endRing(RingingState.Snoozed(alarmId, result.snoozedUntil)) { transitions.snoozed(it, alarmId, now)?.data ?: it }
    }

    private fun onAutoSilence() {
        if (!ringing || ending) return
        val alarmId = ringingAlarmId
        val now = Instant.now()
        val outcome = transitions.autoSilenced(store.state.value, alarmId, now)
        Scheduler.cancel(this, alarmId, TriggerKind.BACKUP)
        when (outcome) {
            is AutoSilence.Reschedule -> {
                Log.i(TAG, "auto-silence: forced snooze until ${outcome.at}")
                Scheduler.schedule(this, alarmId, TriggerKind.SNOOZE, outcome.at)
                endRing(RingingState.Snoozed(alarmId, outcome.at)) { transitions.autoSilenced(it, alarmId, now).data }
            }
            is AutoSilence.Missed -> {
                Log.i(TAG, "auto-silence: forced snoozes spent, alarm $alarmId is missed")
                ReconcileExecutor.postMissed(this, currentAlarm())
                endRing(RingingState.Idle) { transitions.autoSilenced(it, alarmId, now).data }
            }
        }
    }

    /**
     * Ends the current ring: frees the hardware at once, persists the change in order, and only then
     * decides what is next, so an alarm that fires in between cannot read a stale ring.
     */
    private fun endRing(state: RingingState, update: (StoreData) -> StoreData) {
        ending = true
        releaseResources()
        RingingController.publish(state)
        EngineScope.serial.launch {
            val updated = store.update(update)
            handler.post {
                ending = false
                proceed(updated)
            }
        }
    }

    private fun proceed(updated: StoreData) {
        val pending = deferredStarts.toList()
        deferredStarts.clear()
        val next = transitions.nextQueued(updated) ?: if (updated.activeRing == null) pending.firstOrNull() else null
        pending.filter { it != next }.forEach(::persistFire)
        if (next != null) {
            Log.i(TAG, "next in line: alarm $next")
            begin(next)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(lastStartId)
        }
    }

    private fun enterForeground(alarm: Alarm) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.ringing_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        val open = openRingingIntent(0)
        val repost = PendingIntent.getService(
            this,
            1,
            Intent(this, RingingService::class.java).setAction(ACTION_REPOST),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.label.ifBlank { getString(R.string.ringing_title) })
            .setContentText(getString(R.string.ringing_text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setDeleteIntent(repost)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_bell),
                    getString(R.string.notification_solve),
                    openRingingIntent(2),
                ).build(),
            )
        if (info?.canSnooze == true) {
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_moon),
                    getString(R.string.notification_snooze),
                    PendingIntent.getService(
                        this,
                        3,
                        snoozeIntent(this),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build(),
            )
        }
        startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        Log.i(TAG, "foreground entered")
    }

    private fun openRingingIntent(requestCode: Int) = PendingIntent.getActivity(
        this,
        requestCode,
        Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun applyAlarmVolume(alarm: Alarm) {
        runCatching {
            val audio = getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            originalAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            targetAlarmVolume = (max * alarm.maxVolumePercent / 100f).roundToInt().coerceIn(1, max)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, targetAlarmVolume, 0)
            Log.i(TAG, "alarm stream $originalAlarmVolume -> $targetAlarmVolume of $max")
        }.onFailure { Log.w(TAG, "could not set the alarm stream volume", it) }
    }

    /** The volume panel must not lower an alarm that rings: put the level back when it changes (PLAN section 3). */
    private fun watchAlarmVolume() {
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (!ringing) return
                val audio = getSystemService(AudioManager::class.java)
                if (audio.getStreamVolume(AudioManager.STREAM_ALARM) != targetAlarmVolume) {
                    Log.i(TAG, "alarm stream changed behind our back, restoring $targetAlarmVolume")
                    audio.setStreamVolume(AudioManager.STREAM_ALARM, targetAlarmVolume, 0)
                }
            }
        }
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        volumeObserver = observer
    }

    /**
     * Asks for transient audio focus so other players yield, and follows `AudioManager.mode`. A focus request
     * that is denied (an alarm that fires during a call) changes nothing: the alarm still rings, softly, until
     * the call ends. The auto-silence keeps counting during a call, and the mission is still required after it.
     */
    private fun watchCalls() {
        val audio = getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setOnAudioFocusChangeListener({ change -> Log.i(TAG, "audio focus change $change") }, handler)
            .build()
        audioFocus = request
        Log.i(TAG, "audio focus request result ${audio.requestAudioFocus(request)} (1 granted, 0 failed, 2 delayed)")
        val listener = AudioManager.OnModeChangedListener { mode -> handler.post { onAudioMode(mode) } }
        audio.addOnModeChangedListener(mainExecutor, listener)
        modeListener = listener
        onAudioMode(audio.mode)
    }

    private fun onAudioMode(mode: Int) {
        if (!ringing) return
        val busy = CallPolicy.isBusy(mode)
        if (busy == inCall) return
        inCall = busy
        Log.i(TAG, if (busy) "call detected (audio mode $mode): alarm paused, soft tone" else "call ended: alarm resumes")
        if (busy) {
            alarmPlayer.pause()
            tone = ToneGenerator(AudioManager.STREAM_ALARM, CALL_BEEP_VOLUME)
            handler.post(callBeep)
        } else {
            handler.removeCallbacks(callBeep)
            tone?.release()
            tone = null
            alarmPlayer.resume()
        }
    }

    private fun stopWatchingCalls() {
        val audio = getSystemService(AudioManager::class.java)
        modeListener?.let { audio.removeOnModeChangedListener(it) }
        modeListener = null
        audioFocus?.let { audio.abandonAudioFocusRequest(it) }
        audioFocus = null
        handler.removeCallbacks(callBeep)
        tone?.release()
        tone = null
        inCall = false
    }

    private fun restoreAlarmVolume() {
        volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
        volumeObserver = null
        val original = originalAlarmVolume ?: return
        originalAlarmVolume = null
        runCatching { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, original, 0) }
    }

    private fun prepareRamp(alarm: Alarm) {
        if (alarm.volumeRampSeconds <= 0) {
            rampFraction = 1f
            rampDurationMs = 0L
        } else {
            rampFraction = MIN_RAMP
            rampDurationMs = Duration.ofSeconds(alarm.volumeRampSeconds.toLong()).toMillis()
        }
    }

    private fun startRamp() {
        if (rampDurationMs <= 0L) return
        rampStartMs = SystemClock.elapsedRealtime()
        handler.postDelayed(rampVolume, RAMP_STEP_MS)
    }

    /** The ramp and the mission ducking both act on the player gain, never on the alarm stream. */
    private fun applyGain() {
        alarmPlayer.setGain(rampFraction * if (ducked) DUCK_GAIN else 1f)
    }

    private fun startVibration() {
        getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun stopVibration() {
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
    }

    override fun startMission() {
        val current = info
        if (!ringing || ending || runner != null || current == null || current.mission == Mission.None) return
        val mission = MissionRunner(current.mission, SystemClock.elapsedRealtime())
        runner = mission
        ducked = true
        applyGain()
        if (mission.kind == MissionRunner.Kind.STEPS) stopVibration()
        sensors.start(mission, ::onMissionEvent)
        handler.postDelayed(missionTick, TICK_MS)
        Log.i(TAG, "mission started: ${mission.kind}")
        publishMission()
    }

    override fun submitMath(answer: Int) {
        runner?.submitMath(answer, SystemClock.elapsedRealtime())
        onMissionEvent()
    }

    override fun touch() {
        runner?.touch(SystemClock.elapsedRealtime())
    }

    override fun switchToMath() {
        val mission = runner ?: return
        val wasSteps = mission.kind == MissionRunner.Kind.STEPS
        mission.switchToMath(SystemClock.elapsedRealtime())
        if (mission.kind != MissionRunner.Kind.MATH) return
        sensors.stop()
        if (wasSteps && vibrates) startVibration()
        Log.i(TAG, "switched to math")
        onMissionEvent()
    }

    override fun debugSteps(count: Int, intervalMs: Long) {
        for (i in 0 until count) {
            handler.postDelayed(
                {
                    runner?.onStep(SystemClock.elapsedRealtime())
                    onMissionEvent()
                },
                DEBUG_TOKEN,
                i * intervalMs,
            )
        }
    }

    private fun onMissionEvent() {
        val mission = runner ?: return
        val inactive = mission.tick(SystemClock.elapsedRealtime())
        if (ducked == inactive) {
            ducked = !inactive
            applyGain()
            Log.i(TAG, if (ducked) "mission volume ducked" else "no progress, full volume restored")
        }
        publishMission()
        if (mission.done && !missionDone) {
            missionDone = true
            sensors.stop()
            handler.postDelayed(finishMission, DONE_SHOW_MS)
        }
    }

    private fun publishMission() {
        val current = info ?: return
        val mission = runner ?: return
        RingingController.publish(RingingState.MissionActive(current, mission.view))
    }

    private fun releaseResources() {
        handler.removeCallbacks(rearmBackup)
        handler.removeCallbacks(rampVolume)
        handler.removeCallbacks(missionTick)
        handler.removeCallbacks(autoSilence)
        handler.removeCallbacks(finishMission)
        handler.removeCallbacksAndMessages(DEBUG_TOKEN)
        sensors.stop()
        stopWatchingCalls()
        runner = null
        info = null
        missionDone = false
        ducked = false
        alarmPlayer.release()
        stopVibration()
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
        private val IDLE_STOPPABLE = setOf(ACTION_STOP, ACTION_SNOOZE, ACTION_REPOST)

        private const val CHANNEL_ID = "ringing"
        private const val NOTIFICATION_ID = 1
        private const val MIN_RAMP = 0.1f
        private const val DUCK_GAIN = 0.2f
        private const val RAMP_STEP_MS = 500L
        private const val TICK_MS = 1_000L
        private const val DONE_SHOW_MS = 800L
        private const val CALL_BEEP_EVERY_MS = 4_000L
        private const val CALL_BEEP_LENGTH_MS = 300
        private const val CALL_BEEP_VOLUME = 30
        private const val BACKUP_REARM_MS = 20_000L
        private val BACKUP_AHEAD: Duration = Duration.ofSeconds(60)
        private val MAX_RING_MS = RingingPolicy.AUTO_SILENCE.toMillis()
        private val DEBUG_TOKEN = Any()

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
