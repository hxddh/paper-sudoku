package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandbookExamplesTest {

    @Test
    fun `every technique has a position where the Coach teaches exactly it`() {
        for (technique in Technique.entries) {
            val position = HandbookExamples.of(technique)
            val hint = position.hint
            assertTrue("$technique: at most one elimination", hint.groundwork.size <= 1)
            assertEquals(technique, hint.lesson.technique)
            // A real position: givens kept, every digit on it agrees with the solution.
            val board = position.board
            val puzzle = position.puzzle
            assertTrue(board.indices.all { puzzle.givens[it] == 0 || board[it] == puzzle.givens[it] })
            assertTrue(board.indices.all { board[it] == 0 || board[it] == puzzle.solution[it] })
        }
    }
}
