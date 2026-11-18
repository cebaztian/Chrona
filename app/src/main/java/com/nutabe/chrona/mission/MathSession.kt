package com.nutabe.chrona.mission

import com.nutabe.chrona.engine.Difficulty

enum class MathOutcome { CORRECT, WRONG, FINISHED }

/** The math mission: [target] problems in a row; a wrong answer replaces the problem so guessing earns nothing. */
class MathSession(
    private val difficulty: Difficulty,
    val target: Int,
    private val generator: MathGenerator = MathGenerator(),
) {
    var current: MathProblem = generator.next(difficulty)
        private set

    var solved: Int = 0
        private set

    val done: Boolean get() = solved >= target

    fun submit(answer: Int): MathOutcome {
        if (done) return MathOutcome.FINISHED
        if (answer != current.answer) {
            current = generator.next(difficulty, current)
            return MathOutcome.WRONG
        }
        solved++
        if (done) return MathOutcome.FINISHED
        current = generator.next(difficulty, current)
        return MathOutcome.CORRECT
    }
}

/** The numpad's typed digits; the keyboard is our own, so there is no paste and no system calculator. */
data class NumpadInput(val digits: String = "") {
    val value: Int? get() = digits.toIntOrNull()

    fun press(digit: Int): NumpadInput {
        require(digit in 0..9)
        if (digits.length >= MAX_DIGITS) return this
        return NumpadInput(if (digits == "0") digit.toString() else digits + digit)
    }

    fun backspace(): NumpadInput = NumpadInput(digits.dropLast(1))

    companion object {
        const val MAX_DIGITS = 6
    }
}
