package com.titpea.papersudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuEngineTest {

    private val mini = Puzzle(
        size = 6,
        boxRows = 2,
        boxCols = 3,
        givens = listOf(
            0, 2, 0, 4, 5, 0,
            4, 0, 0, 1, 0, 3,
            0, 0, 0, 0, 6, 4,
            5, 0, 4, 0, 0, 0,
            0, 1, 2, 0, 0, 0,
            0, 0, 5, 3, 0, 0,
        ),
        solution = listOf(
            1, 2, 3, 4, 5, 6,
            4, 5, 6, 1, 2, 3,
            2, 3, 1, 5, 6, 4,
            5, 6, 4, 2, 3, 1,
            3, 1, 2, 6, 4, 5,
            6, 4, 5, 3, 1, 2,
        ),
    )

    private val classic = Puzzle(
        size = 9,
        boxRows = 3,
        boxCols = 3,
        givens = listOf(
            5, 3, 0, 0, 7, 0, 0, 0, 0,
            6, 0, 0, 1, 9, 5, 0, 0, 0,
            0, 9, 8, 0, 0, 0, 0, 6, 0,
            8, 0, 0, 0, 6, 0, 0, 0, 3,
            4, 0, 0, 8, 0, 3, 0, 0, 1,
            7, 0, 0, 0, 2, 0, 0, 0, 6,
            0, 6, 0, 0, 0, 0, 2, 8, 0,
            0, 0, 0, 4, 1, 9, 0, 0, 5,
            0, 0, 0, 0, 8, 0, 0, 7, 9,
        ),
        solution = listOf(
            5, 3, 4, 6, 7, 8, 9, 1, 2,
            6, 7, 2, 1, 9, 5, 3, 4, 8,
            1, 9, 8, 3, 4, 2, 5, 6, 7,
            8, 5, 9, 7, 6, 1, 4, 2, 3,
            4, 2, 6, 8, 5, 3, 7, 9, 1,
            7, 1, 3, 9, 2, 4, 8, 5, 6,
            9, 6, 1, 5, 3, 7, 2, 8, 4,
            2, 8, 7, 4, 1, 9, 6, 3, 5,
            3, 4, 5, 2, 8, 6, 1, 7, 9,
        ),
    )

    @Test
    fun `mini candidates exclude row column and box`() {
        val values = mini.givens.toIntArray()
        val candidates = SudokuEngine(mini).candidates(values, index = 0)

        assertTrue(1 in candidates)
        assertFalse(2 in candidates) // same box / row
        assertFalse(4 in candidates) // same box / column
        assertFalse(5 in candidates) // same box / column
    }

    @Test
    fun `mini has a valid first naked single`() {
        val values = mini.givens.toIntArray()
        val hint = SudokuEngine(mini).findNakedSingle(values)

        assertNotNull(hint)
        assertEquals(mini.solution[hint!!.index], hint.digit)
    }

    @Test
    fun `classic has a valid first naked single`() {
        val values = classic.givens.toIntArray()
        val hint = SudokuEngine(classic).findNakedSingle(values)

        assertNotNull(hint)
        assertEquals(classic.solution[hint!!.index], hint.digit)
    }

    @Test
    fun `hidden single is used when no naked single exists`() {
        val hiddenOnly = Puzzle(
            size = 9,
            boxRows = 3,
            boxCols = 3,
            givens = listOf(
                5, 3, 0, 0, 7, 0, 0, 1, 0,
                0, 7, 0, 1, 0, 0, 3, 0, 8,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 9, 7, 6, 1, 0, 2, 3,
                0, 2, 0, 0, 0, 0, 7, 0, 1,
                0, 0, 0, 0, 0, 4, 0, 5, 6,
                9, 6, 1, 0, 0, 7, 2, 0, 0,
                0, 0, 7, 0, 0, 9, 0, 3, 0,
                0, 4, 0, 0, 0, 0, 0, 0, 9,
            ),
            solution = classic.solution,
        )
        val engine = SudokuEngine(hiddenOnly)
        val values = hiddenOnly.givens.toIntArray()

        assertNull(engine.findNakedSingle(values))

        val hint = engine.findNextHint(values)

        assertNotNull(hint)
        assertEquals(Technique.HiddenSingle, hint!!.technique)
        assertEquals(28, hint.index)
        assertEquals(5, hint.digit)
        assertEquals(hiddenOnly.solution[hint.index], hint.digit)
    }

    @Test
    fun `solution is solved and givens are not`() {
        val engine = SudokuEngine(classic)
        assertTrue(engine.isSolved(classic.solution.toIntArray()))
        assertFalse(engine.isSolved(classic.givens.toIntArray()))
    }

    @Test
    fun `conflicts mark every cell in a duplicate row`() {
        val values = mini.givens.toIntArray()
        values[0] = 1
        values[1] = 1 // row 0 has two 1s

        val conflicts = SudokuEngine(mini).conflicts(values)

        assertTrue(0 in conflicts)
        assertTrue(1 in conflicts)
    }
}
