package com.titpea.papersudoku.data

import com.titpea.papersudoku.engine.Puzzle
import com.titpea.papersudoku.engine.SudokuEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleBankTest {

    @Test
    fun `every bundled puzzle has one solution and is solvable with singles`() {
        val all = Puzzles.dailyMini + Puzzles.classic
        assertTrue("daily bank should cover two months", Puzzles.dailyMini.size >= 60)
        assertTrue("classic bank should cover a month", Puzzles.classic.size >= 30)

        all.forEachIndexed { index, puzzle ->
            assertValidSolution(puzzle, index)
            assertEquals("puzzle $index must have one solution", 1, countSolutions(puzzle))
            assertTrue("puzzle $index must be singles-solvable", solvableWithSingles(puzzle))
        }
    }

    @Test
    fun `daily and classic selection rotate deterministically`() {
        assertEquals(Puzzles.mini6, Puzzles.dailyMiniFor(java.time.LocalDate.ofEpochDay(0)))
        assertEquals(Puzzles.classic9, Puzzles.classicFor(java.time.LocalDate.ofEpochDay(0)))
        assertEquals(
            Puzzles.dailyMini[1],
            Puzzles.dailyMiniFor(java.time.LocalDate.ofEpochDay(1)),
        )
    }

    private fun assertValidSolution(puzzle: Puzzle, index: Int) {
        val solution = puzzle.solution
        for (row in 0 until puzzle.size) {
            assertEquals(
                "puzzle $index row $row",
                (1..puzzle.size).toSet(),
                solution.subList(row * puzzle.size, (row + 1) * puzzle.size).toSet(),
            )
        }
        for (column in 0 until puzzle.size) {
            assertEquals(
                "puzzle $index column $column",
                (1..puzzle.size).toSet(),
                (0 until puzzle.size).map { solution[it * puzzle.size + column] }.toSet(),
            )
        }
        for (boxRow in 0 until puzzle.size step puzzle.boxRows) {
            for (boxColumn in 0 until puzzle.size step puzzle.boxCols) {
                val values = buildList {
                    for (row in boxRow until boxRow + puzzle.boxRows) {
                        for (column in boxColumn until boxColumn + puzzle.boxCols) {
                            add(solution[row * puzzle.size + column])
                        }
                    }
                }
                assertEquals("puzzle $index box $boxRow/$boxColumn", (1..puzzle.size).toSet(), values.toSet())
            }
        }
    }

    private fun solvableWithSingles(puzzle: Puzzle): Boolean {
        val engine = SudokuEngine(puzzle)
        val values = puzzle.givens.toIntArray()
        while (!engine.isSolved(values)) {
            val hint = engine.findNextHint(values) ?: return false
            values[hint.index] = hint.digit
        }
        return true
    }

    private fun countSolutions(puzzle: Puzzle): Int {
        val engine = SudokuEngine(puzzle)
        return countSolutions(engine, puzzle.givens.toIntArray(), limit = 2)
    }

    private fun countSolutions(engine: SudokuEngine, values: IntArray, limit: Int): Int {
        var bestIndex = -1
        var bestCandidates: Set<Int> = emptySet()

        values.indices.forEach { index ->
            if (values[index] != 0) return@forEach
            val candidates = engine.candidates(values, index)
            if (candidates.isEmpty()) return 0
            if (bestIndex == -1 || candidates.size < bestCandidates.size) {
                bestIndex = index
                bestCandidates = candidates
            }
        }

        if (bestIndex == -1) return 1

        var count = 0
        for (digit in bestCandidates) {
            values[bestIndex] = digit
            count += countSolutions(engine, values, limit - count)
            values[bestIndex] = 0
            if (count >= limit) break
        }
        return count
    }
}
