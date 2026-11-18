package com.nutabe.chrona.mission

/** Reports expiry when nothing called [touch] for [timeoutMs]; the clock is injected by the caller. */
class Watchdog(private val timeoutMs: Long, startedAtMs: Long) {
    private var lastMs = startedAtMs

    fun touch(nowMs: Long) {
        lastMs = nowMs
    }

    fun isExpired(nowMs: Long): Boolean = nowMs - lastMs >= timeoutMs
}

object MissionTimeouts {
    /** No mission progress for this long restores full volume. */
    const val INACTIVITY_MS = 15_000L

    /** No sensor events at all for this long offers "Switch to math" (ADR-019). */
    const val SHAKE_SENSOR_MS = 30_000L
    const val STEPS_SENSOR_MS = 120_000L
}
