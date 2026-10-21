package com.titpea.papersudoku.engine

/** A single next-step hint: place [digit] at [index]. */
data class Hint(val index: Int, val digit: Int)

/**
 * Small, pure Kotlin engine for the skeleton.
 *
 * It currently covers naked singles, candidate lists, conflict detection, and completion checks.
 * The production engine should add the techniques the product teaches (pairs, pointing, box-line,
 * X-Wing, ...), a difficulty grader, and a full puzzle validator.
 */
class SudokuEngine(private val puzzle: Puzzle) {

    fun candidates(values: IntArray, index: Int): Set<Int> {
        require(values.size == puzzle.size * puzzle.size) { "values must contain size * size cells" }
        require(index in values.indices) { "index out of bounds" }

        val row = index / puzzle.size
        val col = index % puzzle.size
        val used = mutableSetOf<Int>()

        for (c in 0 until puzzle.size) used += values[row * puzzle.size + c]
        for (r in 0 until puzzle.size) used += values[r * puzzle.size + col]

        val boxRow = (row / puzzle.boxRows) * puzzle.boxRows
        val boxCol = (col / puzzle.boxCols) * puzzle.boxCols
        for (r in boxRow until boxRow + puzzle.boxRows) {
            for (c in boxCol until boxCol + puzzle.boxCols) {
                used += values[r * puzzle.size + c]
            }
        }

        return (1..puzzle.size).filterNot { it in used }.toSet()
    }

    /** Returns the first empty cell with exactly one candidate, or null. */
    fun findNakedSingle(values: IntArray): Hint? {
        for (index in values.indices) {
            if (values[index] != 0) continue
            val candidates = candidates(values, index)
            if (candidates.size == 1) {
                return Hint(index = index, digit = candidates.first())
            }
        }
        return null
    }

    /** Indices that participate in a duplicate in a row, column, or box. */
    fun conflicts(values: IntArray): Set<Int> {
        require(values.size == puzzle.size * puzzle.size) { "values must contain size * size cells" }

        val result = mutableSetOf<Int>()
        val units = buildList {
            for (r in 0 until puzzle.size) {
                add((0 until puzzle.size).map { c -> r * puzzle.size + c })
            }
            for (c in 0 until puzzle.size) {
                add((0 until puzzle.size).map { r -> r * puzzle.size + c })
            }
            var boxRow = 0
            while (boxRow < puzzle.size) {
                var boxCol = 0
                while (boxCol < puzzle.size) {
                    val unit = mutableListOf<Int>()
                    for (r in boxRow until boxRow + puzzle.boxRows) {
                        for (c in boxCol until boxCol + puzzle.boxCols) {
                            unit += r * puzzle.size + c
                        }
                    }
                    add(unit)
                    boxCol += puzzle.boxCols
                }
                boxRow += puzzle.boxRows
            }
        }

        for (unit in units) {
            val byValue = unit
                .filter { values[it] != 0 }
                .groupBy { values[it] }
            for (indices in byValue.values) {
                if (indices.size > 1) result += indices
            }
        }
        return result
    }

    fun isSolved(values: IntArray): Boolean {
        if (values.size != puzzle.solution.size) return false
        return values.indices.all { values[it] == puzzle.solution[it] }
    }
}
