package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every page is a different puzzle: a year of dailies, and the practice stack at each level. */
class CatalogTest {

    @Test
    fun `a year of dailies has no repeats`() {
        val start = 20_454L // 2026-01-01
        val days = (start until start + 365)
        assertDistinct(days.map { Catalog.daily(it).givens })
    }

    @Test
    fun `practice pages do not repeat`() {
        for (difficulty in Difficulty.entries) {
            assertDistinct((0 until PAGES).map { Catalog.classic(difficulty, it).givens }, difficulty.name)
        }
    }

    private fun assertDistinct(pages: List<List<Int>>, label: String = "dailies") {
        val repeats = pages.groupBy { it }.values.filter { it.size > 1 }.sumOf { it.size - 1 }
        assertEquals("$label: repeated puzzles", 0, repeats)
    }

    private companion object {
        const val PAGES = 60
    }
}
