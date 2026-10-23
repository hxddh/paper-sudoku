package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Board
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Elimination
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.engine.Technique
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Everything the Coach shows is true at the moment it shows it. Following the Coach (and, to reach
 * other positions, writing correct digits in a random order), every hint's steps, replayed in order:
 * - each cross out something still there;
 * - each leave the board as their sentence says (a naked pair's digits gone from the rest of its
 *   unit, a pointing digit gone from the rest of its line, …), so nothing the note rules out is left
 *   uncrossed;
 * - together leave the placement single in the way its last sentence claims: one candidate in the
 *   cell for a naked single, one place in the unit for a hidden single.
 */
class CoachWalkTest {

    @Test
    fun `every step the Coach shows is true`() {
        val pages = (0 until 8).map { Catalog.classic(Difficulty.Tricky, it) } +
            (0 until 6).map { Catalog.classic(Difficulty.Steady, it) } +
            (20_454L until 20_470L).map { Catalog.daily(it) }
        for (spec in pages) {
            walk(spec) { _, _ -> null }
            val random = Random(spec.key.hashCode().toLong())
            walk(spec) { values, _ ->
                values.indices.filter { values[it] == 0 }.let { it[random.nextInt(it.size)] }
            }
        }
    }

    /** Fills the page cell by cell: the Coach's placement, or the cell [pick] chooses (correctly). */
    private fun walk(spec: PuzzleSpec, pick: (IntArray, CoachHint.Next) -> Int?) {
        val engine = SudokuEngine(spec.puzzle)
        val geometry = spec.geometry
        val values = spec.givens.toIntArray()
        while (!engine.isSolved(values)) {
            val hint = engine.hint(values) as CoachHint.Next
            val board = Board.from(geometry, values)
            fun has(cell: Int, digit: Int) = values[cell] == 0 && (board.candidates[cell] shr digit) and 1 == 1
            for (step in hint.groundwork) {
                assertTrue("${spec.key}: ${step.technique} crosses out nothing", step.removals.any { has(it.index, it.digit) })
                board.eliminate(step.removals)
                assertTrue("${spec.key}: ${step.technique} leaves what it rules out", holds(step, geometry::cells, ::has))
            }
            val p = hint.placement
            val unit = p.unit
            if (p.technique == Technique.HiddenSingle && unit != null) {
                assertTrue("${spec.key}: hidden single not single", geometry.cells(unit).count { has(it, p.digit) } == 1)
            } else {
                assertTrue("${spec.key}: naked single not single", board.candidates[p.index] == 1 shl p.digit)
            }
            val cell = pick(values, hint) ?: p.index
            values[cell] = spec.solution[cell]
        }
    }

    /** The step's sentence, checked on the board after it. */
    private fun holds(step: Elimination, cells: (com.titpea.papersudoku.engine.UnitRef) -> IntArray, has: (Int, Int) -> Boolean): Boolean {
        val units = step.units.map { cells(it).toList() }
        val d = step.digits.first()
        return when (step.technique) {
            // "Every place a D could go is in <line>": none left in the line outside the box.
            Technique.Pointing -> units[1].none { it !in units[0] && has(it, d) }
            // "Every place a D could go is inside <box>": none left in the box outside the line.
            Technique.Claiming -> units[1].none { it !in units[0] && has(it, d) }
            // "They take both, so no other cell there can."
            Technique.NakedPair -> units[0].none { it !in step.patternCells && step.digits.any { digit -> has(it, digit) } }
            // "Those cells hold nothing else."
            Technique.HiddenPair -> step.patternCells.all { cell -> (1..9).none { it !in step.digits && has(cell, it) } }
            // "The rest of <cover lines> can't be D."
            Technique.XWing -> (units[2] + units[3]).none { it !in step.patternCells && has(it, d) }
            else -> true
        }
    }
}
