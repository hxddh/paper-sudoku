package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.engine.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTest {

    @Test
    fun `a page solved the Coach's way reads back with the Coach's techniques`() {
        val spec = Catalog.classic(Difficulty.Tricky, 0)
        val engine = SudokuEngine(spec.puzzle)
        var game = GameState.new(spec)
        val taught = mutableListOf<Technique>()
        while (!engine.isSolved(game.values.toIntArray())) {
            val hint = engine.hint(game.values.toIntArray()) as CoachHint.Next
            taught += hint.technique
            game = game.select(hint.placement.index).input(hint.placement.digit, true)
        }
        val review = Review.of(spec, game.moves)
        assertEquals(game.moves.size, review.size)
        assertTrue(review.all { it.kind == ReviewStep.Kind.Placed && it.technique != null })
        // Read back no harder than the Coach taught it at each move.
        review.zip(taught).forEach { (step, t) -> assertTrue("${step.technique} vs $t", step.technique!!.tier <= t.tier) }
        assertEquals(spec.solution, review.last().values)
    }

    @Test
    fun `a wrong digit is a mistake, and its erasure is logged`() {
        val spec = Catalog.daily(20_000)
        val cell = spec.givens.indexOfFirst { it == 0 }
        val wrong = (1..spec.size).first { it != spec.solution[cell] }
        val game = GameState.new(spec).select(cell).input(wrong, true).eraseMistake(cell)
        val review = Review.of(spec, game.moves)
        assertEquals(listOf(ReviewStep.Kind.Mistake, ReviewStep.Kind.Erased), review.map { it.kind })
        assertTrue(review.last().move.coach)
    }
}
