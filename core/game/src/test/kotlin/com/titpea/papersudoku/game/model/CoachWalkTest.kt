package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Board
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.engine.Technique
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Following the Coach from the givens to the end, every hint's eliminations, applied in order,
 * each remove something and together leave the placement as a single: the note never claims a
 * cell is settled when it is not.
 */
class CoachWalkTest {

    @Test
    fun `every hint's steps lead to its placement`() {
        val pages = (0 until 6).map { Catalog.classic(Difficulty.Tricky, it) } +
            (0 until 4).map { Catalog.classic(Difficulty.Steady, it) } +
            (20_454L until 20_468L).map { Catalog.daily(it) }
        for (spec in pages) {
            val engine = SudokuEngine(spec.puzzle)
            val values = spec.givens.toIntArray()
            while (!engine.isSolved(values)) {
                val hint = engine.hint(values) as CoachHint.Next
                val board = Board.from(spec.geometry, values)
                for (step in hint.groundwork) {
                    assertTrue(
                        "${spec.key}: ${step.technique} removes nothing",
                        step.removals.any { board.candidates[it.index] and (1 shl it.digit) != 0 },
                    )
                    board.eliminate(step.removals)
                }
                val placement = hint.placement
                val unit = placement.unit
                val single = if (placement.technique == Technique.HiddenSingle && unit != null) {
                    spec.geometry.cells(unit).count { values[it] == 0 && board.candidates[it] and (1 shl placement.digit) != 0 } == 1
                } else {
                    board.candidates[placement.index] == 1 shl placement.digit
                }
                assertTrue("${spec.key}: ${hint.groundwork.map { it.technique }} do not settle the cell", single)
                values[placement.index] = placement.digit
            }
        }
    }
}
