package com.nutabe.chrona.mission

import com.nutabe.chrona.engine.Difficulty
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathTest {
    private fun generator(seed: Int = 1) = MathGenerator(Random(seed))

    @Test
    fun everyTierAlwaysYieldsAPositiveIntegerAnswer() {
        for (difficulty in Difficulty.entries) {
            val g = generator(difficulty.ordinal)
            repeat(2_000) {
                val problem = g.next(difficulty)
                assertTrue("${problem.text} = ${problem.answer}", problem.answer > 0)
            }
        }
    }

    @Test
    fun theTextAndTheAnswerAgree() {
        val g = generator()
        repeat(500) {
            val easy = g.next(Difficulty.EASY)
            val (a, b) = easy.text.split(" + ").map(String::toInt)
            assertEquals(a + b, easy.answer)
            assertTrue(a in 10..99 && b in 10..99)
        }
        repeat(500) {
            val medium = g.next(Difficulty.MEDIUM)
            val (left, c) = medium.text.split(" + ")
            val (a, b) = left.split(" × ").map(String::toInt)
            assertEquals(a * b + c.toInt(), medium.answer)
            assertTrue(a in 10..99 && b in 2..9)
        }
        repeat(500) {
            val hard = g.next(Difficulty.HARD)
            val (head, d) = hard.text.split(" − ")
            val (product, c) = head.split(" + ")
            val (a, b) = product.split(" × ").map(String::toInt)
            assertEquals(a * b + c.toInt() - d.toInt(), hard.answer)
            assertTrue(c.toInt() in 100..999)
        }
    }

    @Test
    fun hardIsHarderThanEasy() {
        val g = generator()
        val easyAverage = (1..500).map { g.next(Difficulty.EASY).answer }.average()
        val mediumAverage = (1..500).map { g.next(Difficulty.MEDIUM).answer }.average()
        val hardAverage = (1..500).map { g.next(Difficulty.HARD).answer }.average()
        assertTrue(easyAverage < mediumAverage && mediumAverage < hardAverage)
    }

    @Test
    fun theSameSeedGivesTheSameProblems() {
        val first = generator(7)
        val second = generator(7)
        repeat(50) { assertEquals(first.next(Difficulty.MEDIUM), second.next(Difficulty.MEDIUM)) }
    }

    @Test
    fun aNewProblemDiffersFromThePreviousOne() {
        val g = generator()
        var previous = g.next(Difficulty.EASY)
        repeat(300) {
            val next = g.next(Difficulty.EASY, previous)
            assertNotEquals(previous.text, next.text)
            previous = next
        }
    }

    @Test
    fun aSessionFinishesAfterTheTargetNumberOfCorrectAnswers() {
        val session = MathSession(Difficulty.EASY, 3, generator())
        assertEquals(MathOutcome.CORRECT, session.submit(session.current.answer))
        assertEquals(MathOutcome.CORRECT, session.submit(session.current.answer))
        assertFalse(session.done)
        assertEquals(MathOutcome.FINISHED, session.submit(session.current.answer))
        assertTrue(session.done)
        assertEquals(3, session.solved)
    }

    @Test
    fun aWrongAnswerReplacesTheProblemAndCountsNothing() {
        val session = MathSession(Difficulty.EASY, 2, generator())
        val before = session.current
        assertEquals(MathOutcome.WRONG, session.submit(before.answer + 1))
        assertEquals(0, session.solved)
        assertNotEquals(before.text, session.current.text)
    }

    @Test
    fun aFinishedSessionStaysFinished() {
        val session = MathSession(Difficulty.EASY, 1, generator())
        session.submit(session.current.answer)
        assertEquals(MathOutcome.FINISHED, session.submit(0))
        assertEquals(1, session.solved)
    }

    @Test
    fun theNumpadBuildsANumberDigitByDigit() {
        val input = NumpadInput().press(4).press(2)
        assertEquals(42, input.value)
        assertEquals(4, input.backspace().value)
        assertNull(input.backspace().backspace().value)
    }

    @Test
    fun theNumpadDropsALeadingZeroAndCapsTheLength() {
        assertEquals(5, NumpadInput().press(0).press(5).value)
        var input = NumpadInput()
        repeat(10) { input = input.press(9) }
        assertEquals(NumpadInput.MAX_DIGITS, input.digits.length)
    }
}
