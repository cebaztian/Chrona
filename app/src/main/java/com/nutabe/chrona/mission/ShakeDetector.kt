package com.nutabe.chrona.mission

import com.nutabe.chrona.engine.Sensitivity
import kotlin.math.sqrt

/** Counts shakes from gravity-free acceleration (m/s²): one count per threshold crossing, debounced. */
class ShakeDetector(sensitivity: Sensitivity, val target: Int) {
    private val threshold = thresholdFor(sensitivity)
    private var lastCountMs: Long? = null

    var count: Int = 0
        private set

    val done: Boolean get() = count >= target

    /** Returns true when this sample counted as a shake. */
    fun onSample(timestampMs: Long, x: Float, y: Float, z: Float): Boolean {
        if (done) return false
        val magnitude = sqrt(x * x + y * y + z * z)
        if (magnitude < threshold) return false
        val last = lastCountMs
        if (last != null && timestampMs - last < DEBOUNCE_MS) return false
        lastCountMs = timestampMs
        count++
        return true
    }

    companion object {
        const val DEBOUNCE_MS = 250L

        fun thresholdFor(sensitivity: Sensitivity): Float = when (sensitivity) {
            Sensitivity.LOW -> 16f
            Sensitivity.MEDIUM -> 11f
            Sensitivity.HIGH -> 7f
        }
    }
}

/** Removes gravity from raw accelerometer samples when the device has no linear-acceleration sensor. */
class GravityFilter(private val alpha: Float = 0.8f) {
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var primed = false

    fun filter(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        if (!primed) {
            gx = x
            gy = y
            gz = z
            primed = true
        }
        gx = alpha * gx + (1 - alpha) * x
        gy = alpha * gy + (1 - alpha) * y
        gz = alpha * gz + (1 - alpha) * z
        return Triple(x - gx, y - gy, z - gz)
    }
}
