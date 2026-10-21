package com.titpea.papersudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    private val engine = SudokuEngine(Fixtures.mini)

    @Test
    fun `candidates exclude row column and box digits`() {
        val values = Fixtures.mini.givens.toIntArray()
        assertEquals(setOf(1, 3, 6), engine.candidates(values, 0))
        assertEquals(emptySet<Int>(), engine.candidates(values, 1))
    }

    @Test
    fun `conflicts mark both duplicates`() {
        val values = Fixtures.mini.givens.toIntArray().also { it[0] = 2 }
        assertEquals(setOf(0, 1), engine.conflicts(values))
    }

    @Test
    fun `coach points at a wrong entry before anything else`() {
        val values = Fixtures.mini.givens.toIntArray().also { it[0] = 3 }
        assertEquals(CoachHint.Mistake(0, 3), engine.hint(values))
    }

    @Test
    fun `coach walks a puzzle to the end with correct placements`() {
        val values = Fixtures.mini.givens.toIntArray()
        while (true) {
            val hint = engine.hint(values) ?: break
            hint as CoachHint.Next
            assertEquals(Fixtures.mini.solution[hint.placement.index], hint.placement.digit)
            values[hint.placement.index] = hint.placement.digit
        }
        assertTrue(engine.isSolved(values))
        assertNull(engine.hint(values))
    }

    @Test
    fun `coach lays groundwork when no single is available`() {
        val generator = PuzzleGenerator(Geometry.Classic)
        val found = (1L..40L).asSequence().mapNotNull { seed ->
            val puzzle = generator.generate(seed, Difficulty.Steady).puzzle
            val e = SudokuEngine(puzzle)
            val values = puzzle.givens.toIntArray()
            var hint = e.hint(values) as CoachHint.Next?
            while (hint != null && hint.groundwork.isEmpty()) {
                values[hint.placement.index] = hint.placement.digit
                hint = e.hint(values) as CoachHint.Next?
            }
            hint?.let { puzzle to it }
        }.first()
        val (puzzle, hint) = found
        assertTrue(hint.technique.tier >= 2)
        assertEquals(puzzle.solution[hint.placement.index], hint.placement.digit)
    }

    @Test
    fun `x-wing is found on a textbook position`() {
        // Digit 1 in rows 1 and 4 is limited to columns 2 and 7, so it leaves r0c2. The extra 1s in
        // row 0 keep pointing and claiming from firing first.
        val g = Geometry.Classic
        val board = Board.from(g, IntArray(81))
        val withoutOne = g.allDigits and (1 shl 1).inv()
        for (cell in 0 until 81) board.candidates[cell] = withoutOne
        for (cell in listOf(0, 2, 5, 11, 16, 38, 43)) board.candidates[cell] = g.allDigits
        val step = LogicalSolverAccess.xWingOnly(g, board)
        assertEquals(Technique.XWing, step?.technique)
        assertEquals(listOf(Candidate(2, 1)), (step as Elimination).removals)
        assertEquals(listOf(11, 16, 38, 43), step.patternCells)
    }
}

/** Runs the solver on a hand-built board where the earlier techniques have nothing to do. */
object LogicalSolverAccess {
    fun xWingOnly(g: Geometry, board: Board): Step? = LogicalSolver(g).nextElimination(board)
}
