package com.nutabe.chrona.mission

import kotlin.math.ceil

/**
 * Rejects steps that arrive faster than a human can walk, so shaking the phone is not walking. Every
 * event, counted or not, moves the reference, so a sustained burst counts once and then nothing.
 */
class StepCadenceFilter(private val minIntervalMs: Long = MIN_INTERVAL_MS) {
    private var lastEventMs: Long? = null

    fun accept(timestampMs: Long): Boolean {
        val last = lastEventMs
        lastEventMs = timestampMs
        return last == null || timestampMs - last >= minIntervalMs
    }

    companion object {
        const val MIN_INTERVAL_MS = 250L
    }
}

/**
 * Turns the cumulative step-counter value into steps since the mission started. The first value is the
 * baseline; a drop (the counter reset on reboot) re-baselines; the gain between two events is capped at
 * the human cadence for the elapsed time, since counter events are batched.
 */
class StepCounterDelta(private val maxStepsPerSecond: Int = MAX_STEPS_PER_SECOND) {
    private var lastValue: Float? = null
    private var lastEventMs = 0L

    fun onCounter(value: Float, timestampMs: Long): Int {
        val previous = lastValue
        val previousMs = lastEventMs
        lastValue = value
        lastEventMs = timestampMs
        if (previous == null || value < previous) return 0
        val elapsed = (timestampMs - previousMs).coerceAtLeast(0)
        val allowed = ceil(elapsed * maxStepsPerSecond / 1000.0).toInt() + 1
        return minOf((value - previous).toInt(), allowed)
    }

    companion object {
        const val MAX_STEPS_PER_SECOND = 4
    }
}
