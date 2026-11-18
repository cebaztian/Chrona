package com.nutabe.chrona.mission

import com.nutabe.chrona.engine.Mission
import kotlin.random.Random

/** What a mission screen shows; the same shapes feed the ringing screen and the editor preview. */
sealed interface MissionView {
    val done: Boolean
    val offerMath: Boolean

    data class MathView(
        val problem: String,
        val solved: Int,
        val target: Int,
        val wrongCount: Int,
        override val offerMath: Boolean = false,
    ) : MissionView {
        override val done: Boolean get() = solved >= target
    }

    data class ShakeView(val count: Int, val target: Int, override val offerMath: Boolean) : MissionView {
        override val done: Boolean get() = count >= target
    }

    data class StepsView(val count: Int, val target: Int, override val offerMath: Boolean) : MissionView {
        override val done: Boolean get() = count >= target
    }
}

/**
 * One run of a mission, pure and clock-free: callers pass timestamps. Sensor events only ever feed it;
 * a sensor that goes quiet makes [offerMath] true (ADR-019), and [tick] reports when nothing progressed
 * for the inactivity window so the caller can restore full volume.
 */
class MissionRunner(mission: Mission, private val startMs: Long, private val random: Random = Random.Default) {
    enum class Kind { MATH, SHAKE, STEPS }

    var kind: Kind = Kind.MATH
        private set

    private var math: MathSession? = null
    private var shake: ShakeDetector? = null
    private var stepCount = 0
    private var stepTarget = 0
    private var cadence = StepCadenceFilter()
    private var counterDelta = StepCounterDelta()
    private var wrongCount = 0
    private var offer = false
    private var lastProgressMs = startMs
    private var sensorDog: Watchdog? = null

    /** Increases on every counted unit of progress, so a caller can tell that something moved. */
    var progressVersion = 0
        private set

    init {
        when (mission) {
            Mission.None -> error("A mission run needs a mission")
            is Mission.Math -> startMath(mission)
            is Mission.Shake -> {
                kind = Kind.SHAKE
                shake = ShakeDetector(mission.sensitivity, mission.count)
                sensorDog = Watchdog(MissionTimeouts.SHAKE_SENSOR_MS, startMs)
            }
            is Mission.Steps -> {
                kind = Kind.STEPS
                stepTarget = mission.count
                sensorDog = Watchdog(MissionTimeouts.STEPS_SENSOR_MS, startMs)
            }
        }
    }

    val done: Boolean get() = view.done

    val view: MissionView
        get() = when (kind) {
            Kind.MATH -> {
                val session = checkNotNull(math)
                MissionView.MathView(session.current.text, session.solved, session.target, wrongCount, offer)
            }
            Kind.SHAKE -> MissionView.ShakeView(checkNotNull(shake).count, checkNotNull(shake).target, offer)
            Kind.STEPS -> MissionView.StepsView(stepCount, stepTarget, offer)
        }

    fun submitMath(answer: Int, nowMs: Long): MathOutcome {
        val session = math ?: return MathOutcome.WRONG
        val outcome = session.submit(answer)
        lastProgressMs = nowMs
        when (outcome) {
            MathOutcome.WRONG -> wrongCount++
            else -> progressVersion++
        }
        return outcome
    }

    /** The user interacted without progressing, for example by pressing a numpad key. */
    fun touch(nowMs: Long) {
        lastProgressMs = nowMs
    }

    fun onShake(timestampMs: Long, x: Float, y: Float, z: Float) {
        val detector = shake ?: return
        sensorDog?.touch(timestampMs)
        if (detector.onSample(timestampMs, x, y, z)) progressed(timestampMs)
    }

    fun onStep(timestampMs: Long) {
        if (kind != Kind.STEPS) return
        sensorDog?.touch(timestampMs)
        if (stepCount < stepTarget && cadence.accept(timestampMs)) {
            stepCount++
            progressed(timestampMs)
        }
    }

    fun onStepCounter(value: Float, timestampMs: Long) {
        if (kind != Kind.STEPS) return
        sensorDog?.touch(timestampMs)
        val gained = counterDelta.onCounter(value, timestampMs).coerceAtMost(stepTarget - stepCount)
        if (gained > 0) {
            stepCount += gained
            progressed(timestampMs)
        }
    }

    /** Replaces a sensor mission that cannot be completed with the fallback math mission. */
    fun switchToMath(nowMs: Long) {
        if (kind == Kind.MATH || !offer) return
        startMath(MissionResolver.FALLBACK)
        sensorDog = null
        offer = false
        lastProgressMs = nowMs
        progressVersion++
    }

    /** Returns true when nothing progressed for the inactivity window. */
    fun tick(nowMs: Long): Boolean {
        if (sensorDog?.isExpired(nowMs) == true) offer = true
        return nowMs - lastProgressMs >= MissionTimeouts.INACTIVITY_MS
    }

    private fun progressed(nowMs: Long) {
        lastProgressMs = nowMs
        progressVersion++
    }

    private fun startMath(mission: Mission.Math) {
        kind = Kind.MATH
        shake = null
        math = MathSession(mission.difficulty, mission.count, MathGenerator(random))
    }
}
