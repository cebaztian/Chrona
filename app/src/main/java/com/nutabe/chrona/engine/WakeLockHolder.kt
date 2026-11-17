package com.nutabe.chrona.engine

import android.content.Context
import android.os.PowerManager

/** Holds the short-lived wake lock that bridges `AlarmReceiver.onReceive` and `RingingService`. */
object WakeLockHolder {
    private const val TAG = "Chrona:handoff"
    private const val DEFAULT_TIMEOUT_MS = 60_000L

    private var lock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        release()
        val powerManager = context.getSystemService(PowerManager::class.java)
        lock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    @Synchronized
    fun release() {
        lock?.takeIf { it.isHeld }?.release()
        lock = null
    }
}
