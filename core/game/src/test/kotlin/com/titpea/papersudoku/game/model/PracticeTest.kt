package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeTest {

    @Test
    fun `practice starts gentle and advances after three clean solves per level`() {
        assertEquals(Difficulty.Gentle, Practice.level(emptyMap()))
        assertEquals(3, Practice.remainingToAdvance(emptyMap()))
        assertEquals(Difficulty.Gentle, Practice.level(mapOf(Difficulty.Gentle to 2)))
        assertEquals(1, Practice.remainingToAdvance(mapOf(Difficulty.Gentle to 2)))
        assertEquals(Difficulty.Steady, Practice.level(mapOf(Difficulty.Gentle to 3)))
        assertEquals(Difficulty.Steady, Practice.level(mapOf(Difficulty.Gentle to 5, Difficulty.Steady to 2)))
        val tricky = mapOf(Difficulty.Gentle to 3, Difficulty.Steady to 3)
        assertEquals(Difficulty.Tricky, Practice.level(tricky))
        assertEquals(3, Practice.remainingToAdvance(tricky))
        // 9.0: a player already past Tricky goes on to Deep at once.
        val top = tricky + (Difficulty.Tricky to 3)
        assertEquals(Difficulty.Deep, Practice.level(top))
        assertNull(Practice.remainingToAdvance(top))
    }

    @Test
    fun `steady solves do not skip gentle`() {
        assertEquals(Difficulty.Gentle, Practice.level(mapOf(Difficulty.Steady to 10)))
    }
}
