package com.nutabe.chrona.mission

import com.nutabe.chrona.engine.Difficulty
import kotlin.random.Random

data class MathProblem(val text: String, val answer: Int)

/** Builds arithmetic problems whose answer is always a positive integer; [random] is injectable for tests. */
class MathGenerator(private val random: Random = Random.Default) {
    fun next(difficulty: Difficulty, previous: MathProblem? = null): MathProblem {
        var problem = build(difficulty)
        while (problem.text == previous?.text) problem = build(difficulty)
        return problem
    }

    private fun build(difficulty: Difficulty): MathProblem = when (difficulty) {
        Difficulty.EASY -> {
            val a = random.nextInt(10, 100)
            val b = random.nextInt(10, 100)
            MathProblem("$a + $b", a + b)
        }
        Difficulty.MEDIUM -> {
            val a = random.nextInt(10, 100)
            val b = random.nextInt(2, 10)
            val c = random.nextInt(10, 100)
            MathProblem("$a × $b + $c", a * b + c)
        }
        Difficulty.HARD -> {
            val a = random.nextInt(12, 100)
            val b = random.nextInt(12, 50)
            val c = random.nextInt(100, 1000)
            val d = random.nextInt(10, 100)
            MathProblem("$a × $b + $c − $d", a * b + c - d)
        }
    }
}
