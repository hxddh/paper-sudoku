package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnerTest {

    private fun placed(technique: Technique?, coach: Boolean = false) =
        ReviewStep(Move(0, 1, coach), emptyList(), ReviewStep.Kind.Placed, technique)

    @Test
    fun `a technique counts only when the player wrote with it`() {
        val learner = Learner().record(
            listOf(placed(Technique.HiddenSingle), placed(Technique.Pointing, coach = true)),
            CoachUsage(),
        )
        assertEquals(1, learner.found(Technique.HiddenSingle))
        assertEquals(0, learner.found(Technique.Pointing))
        assertEquals(1, learner.pages)
    }

    @Test
    fun `a technique the Coach explained on the page is not found there`() {
        val coach = CoachUsage().reached(2, Technique.XWing)
        val learner = Learner().record(listOf(placed(Technique.XWing)), coach)
        assertEquals(0, learner.found(Technique.XWing))
        assertEquals(1, learner.helped[Technique.XWing])
        assertEquals(Mastery.Met, learner.mastery(Technique.XWing, met = false))
    }

    @Test
    fun `a look does not count as explaining`() {
        val coach = CoachUsage().reached(1, Technique.XWing)
        val learner = Learner().record(listOf(placed(Technique.XWing)), coach)
        assertEquals(1, learner.found(Technique.XWing))
    }

    @Test
    fun `three pages make a technique known and the record survives a round trip`() {
        var learner = Learner()
        repeat(3) { learner = learner.record(listOf(placed(Technique.NakedPair), placed(Technique.NakedPair)), CoachUsage()) }
        assertEquals(3, learner.found(Technique.NakedPair))
        assertEquals(Mastery.Mastered, learner.mastery(Technique.NakedPair, met = true))
        assertEquals(learner, Learner.decode(learner.encode()))
        assertNull(Learner.decode("not json"))
    }

    @Test
    fun `9_0 clean pages carry over as at most one page found`() {
        val learner = Learner.from9(mapOf(Technique.XWing to 5, Technique.Pointing to 0))
        assertEquals(1, learner.found(Technique.XWing))
        assertEquals(0, learner.found(Technique.Pointing))
    }

    @Test
    fun `the path deals what is next to learn, and revisits what fades`() {
        assertNull(Path.target(Learner(), Difficulty.Gentle, 0))
        assertEquals(Technique.Pointing, Path.target(Learner(), Difficulty.Steady, 0))
        val knowsSteady = Learner(
            found = mapOf(Technique.Pointing to 3, Technique.Claiming to 3, Technique.NakedPair to 3),
            lastFound = mapOf(Technique.Pointing to 1, Technique.Claiming to 9, Technique.NakedPair to 10),
            pages = 12,
        )
        assertNull(Path.next(knowsSteady, Difficulty.Steady))
        assertEquals(Technique.HiddenPair, Path.target(knowsSteady, Difficulty.Tricky, 0))
        // Every fourth page goes back to the technique found longest ago.
        assertEquals(Technique.Pointing, Path.target(knowsSteady, Difficulty.Tricky, 3))
        // Not if it was found recently.
        assertEquals(Technique.HiddenPair, Path.target(knowsSteady.copy(pages = 5), Difficulty.Tricky, 3))
    }

    @Test
    fun `a page dealt for a technique needs it and keeps it in its key`() {
        val page = Catalog.practice(Difficulty.Steady, 4, Technique.Pointing)
        assertTrue(Technique.Pointing in page.techniques)
        assertEquals(Difficulty.Steady, page.difficulty)
        assertEquals(PuzzleKind.Classic, page.kind)
        assertEquals(Technique.Pointing, Catalog.practiceTarget(page.key))
        assertEquals(page, Catalog.practice(Difficulty.Steady, 4, Technique.Pointing))
        // Without a target, exactly the plain page.
        assertEquals(Catalog.classic(Difficulty.Steady, 4), Catalog.practice(Difficulty.Steady, 4, null))
        assertNull(Catalog.practiceTarget(Catalog.classicKey(Difficulty.Steady, 4)))
    }

    @Test
    fun `the Coach points at a pencil mark that leaves out the answer, nearest first`() {
        val spec = Catalog.classic(Difficulty.Steady, 2)
        val engine = SudokuEngine(spec.puzzle)
        val empties = spec.givens.indices.filter { spec.givens[it] == 0 }
        val (far, near) = empties.first() to empties.last()
        val notes = IntArray(spec.cellCount)
        // Marks that leave out each cell's answer.
        for (cell in listOf(far, near)) notes[cell] = (2..9).fold(0) { m, d -> m or (1 shl d) } and (1 shl spec.solution[cell]).inv()
        val values = spec.givens.toIntArray()
        assertEquals(CoachHint.MissingMark(far, spec.solution[far]), engine.hint(values, notes))
        assertEquals(CoachHint.MissingMark(near, spec.solution[near]), engine.hint(values, notes, near = near))
        // Given back, it is no longer pointed out.
        var game = GameState.new(spec).copy(notes = notes.toList())
        game = game.restoreMark(far, spec.solution[far]).restoreMark(near, spec.solution[near])
        assertTrue(engine.hint(values, game.notes.toIntArray()) is CoachHint.Next)
    }

    @Test
    fun `among equal singles the Coach prefers the player's area`() {
        val spec = Catalog.classic(Difficulty.Gentle, 1)
        val engine = SudokuEngine(spec.puzzle)
        val values = spec.givens.toIntArray()
        val plain = engine.hint(values) as CoachHint.Next
        val geometry = spec.geometry
        // A cell far from the default answer that has a single near it.
        val near = spec.givens.indices.firstOrNull { cell ->
            spec.givens[cell] == 0 && cell != plain.placement.index && !geometry.sees(cell, plain.placement.index) &&
                (engine.hint(values, near = cell) as? CoachHint.Next)?.placement?.let { it.index == cell || geometry.sees(it.index, cell) } == true
        }
        assertNotNull(near)
        val hinted = engine.hint(values, near = near) as CoachHint.Next
        assertTrue(hinted.groundwork.isEmpty())
        assertTrue(hinted.placement.index == near || geometry.sees(hinted.placement.index, near!!))
    }
}
