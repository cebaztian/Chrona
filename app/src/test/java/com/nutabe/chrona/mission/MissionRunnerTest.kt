package com.nutabe.chrona.mission

import com.nutabe.chrona.engine.Difficulty
import com.nutabe.chrona.engine.Mission
import com.nutabe.chrona.engine.Sensitivity
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionRunnerTest {
    private fun runner(mission: Mission, start: Long = 0) = MissionRunner(mission, start, Random(3))

    private fun MissionRunner.answerCurrent(nowMs: Long = 0): MathOutcome =
        submitMath((view as MissionView.MathView).let { eval(it.problem) }, nowMs)

    private fun eval(text: String): Int {
        val tokens = text.split(" ")
        var terms = mutableListOf(tokens[0].toInt())
        var i = 1
        while (i < tokens.size) {
            val value = tokens[i + 1].toInt()
            when (tokens[i]) {
                "×" -> terms[terms.lastIndex] = terms.last() * value
                "+" -> terms += value
                "−" -> terms += -value
            }
            i += 2
        }
        return terms.sum()
    }

    @Test
    fun aMathMissionFinishesAfterItsProblems() {
        val r = runner(Mission.Math(Difficulty.EASY, 2))
        assertEquals(MathOutcome.CORRECT, r.answerCurrent())
        assertFalse(r.done)
        assertEquals(MathOutcome.FINISHED, r.answerCurrent())
        assertTrue(r.done)
    }

    @Test
    fun aWrongAnswerCountsWrongAndKeepsTheMissionOpen() {
        val r = runner(Mission.Math(Difficulty.EASY, 1))
        assertEquals(MathOutcome.WRONG, r.submitMath(-1, 0))
        assertEquals(1, (r.view as MissionView.MathView).wrongCount)
        assertFalse(r.done)
    }

    @Test
    fun shakingCompletesAShakeMission() {
        val r = runner(Mission.Shake(3, Sensitivity.HIGH))
        for (t in 0L..1_000L step 300) r.onShake(t, 30f, 0f, 0f)
        assertTrue(r.done)
        assertEquals(MissionRunner.Kind.SHAKE, r.kind)
    }

    @Test
    fun stepsCompleteAStepsMissionAtWalkingCadence() {
        val r = runner(Mission.Steps(5))
        for (i in 0L until 5) r.onStep(i * 500)
        assertTrue(r.done)
    }

    @Test
    fun aFastBurstOfStepsDoesNotCount() {
        val r = runner(Mission.Steps(20))
        for (i in 0L until 100) r.onStep(i * 80)
        assertEquals(1, (r.view as MissionView.StepsView).count)
    }

    @Test
    fun theCounterFallbackCountsTheDelta() {
        val r = runner(Mission.Steps(10))
        r.onStepCounter(500f, 0)
        r.onStepCounter(506f, 6_000)
        assertEquals(6, (r.view as MissionView.StepsView).count)
        r.onStepCounter(530f, 12_000)
        assertTrue(r.done)
    }

    @Test
    fun aShakeMissionOffersMathAfterThirtySecondsOfSilence() {
        val r = runner(Mission.Shake(10, Sensitivity.MEDIUM))
        assertFalse(r.tick(29_000).let { r.view.offerMath })
        r.tick(30_000)
        assertTrue(r.view.offerMath)
    }

    @Test
    fun aStepsMissionWaitsTwoMinutesBeforeOfferingMath() {
        val r = runner(Mission.Steps(50))
        r.tick(119_000)
        assertFalse(r.view.offerMath)
        r.tick(120_000)
        assertTrue(r.view.offerMath)
    }

    @Test
    fun aSensorThatKeepsReportingNeverOffersMathEvenWithoutProgress() {
        val r = runner(Mission.Shake(10, Sensitivity.LOW))
        for (t in 0L..120_000L step 1_000) {
            r.onShake(t, 0.1f, 0f, 0f)
            r.tick(t)
        }
        assertFalse(r.view.offerMath)
    }

    @Test
    fun switchingToMathReplacesTheSensorMission() {
        val r = runner(Mission.Shake(10, Sensitivity.MEDIUM))
        r.switchToMath(5_000)
        assertEquals(MissionRunner.Kind.SHAKE, r.kind)
        r.tick(30_000)
        r.switchToMath(31_000)
        assertEquals(MissionRunner.Kind.MATH, r.kind)
        val view = r.view as MissionView.MathView
        assertEquals(MissionResolver.FALLBACK.count, view.target)
        assertFalse(view.offerMath)
        for (t in 32_000L..200_000L step 5_000) r.tick(t)
        assertFalse(r.view.offerMath)
    }

    @Test
    fun sensorEventsAreIgnoredOnceTheMissionIsMath() {
        val r = runner(Mission.Shake(10, Sensitivity.HIGH))
        r.tick(30_000)
        r.switchToMath(30_000)
        val before = r.progressVersion
        r.onShake(31_000, 50f, 0f, 0f)
        r.onStep(32_000)
        assertEquals(before, r.progressVersion)
    }

    @Test
    fun noProgressForFifteenSecondsFlagsInactivity() {
        val r = runner(Mission.Math(Difficulty.EASY, 3))
        assertFalse(r.tick(14_999))
        assertTrue(r.tick(15_000))
    }

    @Test
    fun progressAndTouchesResetTheInactivityWindow() {
        val r = runner(Mission.Math(Difficulty.EASY, 3))
        r.answerCurrent(10_000)
        assertFalse(r.tick(24_000))
        assertTrue(r.tick(25_000))
        r.touch(26_000)
        assertFalse(r.tick(40_000))
    }

    @Test
    fun aWrongAnswerAlsoCountsAsEngagement() {
        val r = runner(Mission.Math(Difficulty.EASY, 3))
        r.submitMath(-1, 10_000)
        assertFalse(r.tick(24_000))
    }

    @Test
    fun progressVersionOnlyMovesOnProgress() {
        val r = runner(Mission.Shake(10, Sensitivity.MEDIUM))
        val start = r.progressVersion
        r.onShake(0, 1f, 0f, 0f)
        assertEquals(start, r.progressVersion)
        r.onShake(10, 30f, 0f, 0f)
        assertEquals(start + 1, r.progressVersion)
    }

    @Test(expected = IllegalStateException::class)
    fun aRunNeedsARealMission() {
        runner(Mission.None)
    }
}
