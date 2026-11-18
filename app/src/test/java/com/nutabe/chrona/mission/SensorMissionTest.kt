package com.nutabe.chrona.mission

import com.nutabe.chrona.engine.Difficulty
import com.nutabe.chrona.engine.Mission
import com.nutabe.chrona.engine.Sensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorMissionTest {
    private fun shakes(detector: ShakeDetector, burst: List<Pair<Long, Float>>) =
        burst.count { (t, magnitude) -> detector.onSample(t, magnitude, 0f, 0f) }

    @Test
    fun aStrongBurstCountsOnceEveryDebounceWindow() {
        val detector = ShakeDetector(Sensitivity.MEDIUM, 30)
        val counted = shakes(detector, (0L..1_000L step 50).map { it to 20f })
        assertEquals(1_000 / ShakeDetector.DEBOUNCE_MS + 1, counted.toLong())
    }

    @Test
    fun aSlowTiltBelowTheThresholdNeverCounts() {
        for (sensitivity in Sensitivity.entries) {
            val detector = ShakeDetector(sensitivity, 5)
            val below = ShakeDetector.thresholdFor(sensitivity) - 0.5f
            assertEquals(0, shakes(detector, (0L..10_000L step 20).map { it to below }))
        }
    }

    @Test
    fun theSensitivityOrdersTheThresholds() {
        assertTrue(ShakeDetector.thresholdFor(Sensitivity.HIGH) < ShakeDetector.thresholdFor(Sensitivity.MEDIUM))
        assertTrue(ShakeDetector.thresholdFor(Sensitivity.MEDIUM) < ShakeDetector.thresholdFor(Sensitivity.LOW))
    }

    @Test
    fun aMediumShakeCountsOnHighButNotOnLow() {
        val medium = 12f
        assertEquals(1, shakes(ShakeDetector(Sensitivity.HIGH, 5), listOf(0L to medium)))
        assertEquals(0, shakes(ShakeDetector(Sensitivity.LOW, 5), listOf(0L to medium)))
    }

    @Test
    fun theMagnitudeUsesAllThreeAxes() {
        val detector = ShakeDetector(Sensitivity.MEDIUM, 5)
        assertTrue(detector.onSample(0, 7f, 7f, 7f))
    }

    @Test
    fun theDetectorStopsAtTheTarget() {
        val detector = ShakeDetector(Sensitivity.HIGH, 3)
        shakes(detector, (0L..5_000L step 300).map { it to 30f })
        assertEquals(3, detector.count)
        assertTrue(detector.done)
    }

    @Test
    fun theGravityFilterLeavesAShakeAndDropsAStillPhone() {
        val filter = GravityFilter()
        var last = Triple(0f, 0f, 0f)
        repeat(100) { last = filter.filter(0f, 0f, 9.81f) }
        assertEquals(0f, last.third, 0.1f)
        val jolt = filter.filter(0f, 0f, 9.81f + 25f)
        assertTrue(jolt.third > 15f)
    }

    @Test
    fun walkingCadenceStepsAreAllAccepted() {
        val filter = StepCadenceFilter()
        val accepted = (0L until 20).count { filter.accept(it * 500) }
        assertEquals(20, accepted)
    }

    @Test
    fun aRunningCadenceOfThreeAndAHalfStepsPerSecondIsAccepted() {
        val filter = StepCadenceFilter()
        assertEquals(20, (0L until 20).count { filter.accept(it * 286) })
    }

    @Test
    fun aBurstFasterThanTheCadenceLimitCountsOnce() {
        val filter = StepCadenceFilter()
        val accepted = (0L until 50).count { filter.accept(it * 100) }
        assertEquals(1, accepted)
    }

    @Test
    fun walkingResumesAfterABurst() {
        val filter = StepCadenceFilter()
        (0L until 10).forEach { filter.accept(it * 100) }
        assertTrue(filter.accept(2_000))
        assertTrue(filter.accept(2_500))
    }

    @Test
    fun theCounterDeltaStartsFromTheFirstValue() {
        val delta = StepCounterDelta()
        assertEquals(0, delta.onCounter(5_000f, 0))
        assertEquals(10, delta.onCounter(5_010f, 8_000))
    }

    @Test
    fun theCounterDeltaCapsAJumpAtTheHumanCadence() {
        val delta = StepCounterDelta()
        delta.onCounter(1_000f, 0)
        val gained = delta.onCounter(1_500f, 2_000)
        assertTrue("gained $gained", gained <= 2 * StepCounterDelta.MAX_STEPS_PER_SECOND + 1)
    }

    @Test
    fun theCounterDeltaRebaselinesWhenTheCounterResets() {
        val delta = StepCounterDelta()
        delta.onCounter(9_000f, 0)
        assertEquals(0, delta.onCounter(12f, 5_000))
        assertEquals(4, delta.onCounter(16f, 10_000))
    }

    @Test
    fun aWatchdogExpiresOnlyAfterItsTimeoutWithoutAnyTouch() {
        val dog = Watchdog(30_000, startedAtMs = 0)
        assertFalse(dog.isExpired(29_999))
        assertTrue(dog.isExpired(30_000))
        dog.touch(25_000)
        assertFalse(dog.isExpired(54_000))
        assertTrue(dog.isExpired(55_000))
    }

    @Test
    fun stepsWaitLongerThanShakeBeforeOfferingMath() {
        assertEquals(30_000L, MissionTimeouts.SHAKE_SENSOR_MS)
        assertEquals(120_000L, MissionTimeouts.STEPS_SENSOR_MS)
    }

    private val full = MissionEnvironment(hasShakeSensor = true, hasStepSensor = true, activityRecognitionGranted = true)

    @Test
    fun aRunnableMissionIsKept() {
        for (mission in listOf(Mission.None, Mission.Math(), Mission.Shake(), Mission.Steps())) {
            assertEquals(ResolvedMission(mission, false), MissionResolver.resolve(mission, full))
        }
    }

    @Test
    fun shakeFallsBackToMathWithoutAnAccelerometer() {
        val resolved = MissionResolver.resolve(Mission.Shake(), full.copy(hasShakeSensor = false))
        assertEquals(ResolvedMission(MissionResolver.FALLBACK, true), resolved)
    }

    @Test
    fun stepsFallBackToMathWithoutASensorOrThePermission() {
        assertTrue(MissionResolver.resolve(Mission.Steps(), full.copy(hasStepSensor = false)).fellBackToMath)
        assertTrue(MissionResolver.resolve(Mission.Steps(), full.copy(activityRecognitionGranted = false)).fellBackToMath)
        assertEquals(Mission.Math(Difficulty.MEDIUM, 3), MissionResolver.FALLBACK)
    }
}
