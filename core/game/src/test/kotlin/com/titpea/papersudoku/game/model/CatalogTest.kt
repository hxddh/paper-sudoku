package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Every page is a different puzzle: a year of dailies, and the practice stack at each level. */
class CatalogTest {

    @Test
    fun `a year of dailies has no repeats`() {
        val start = 20_454L // 2026-01-01
        val days = (start until start + 365)
        assertDistinct(days.map { Catalog.daily(it).givens })
    }

    @Test
    fun `every daily is dealt at its level`() {
        // 2027–2028 include days whose first try came out easier (2027-04-04, 2028-07-09, 2028-10-07).
        val start = LocalDate.of(2027, 1, 1).toEpochDay()
        val end = LocalDate.of(2028, 12, 31).toEpochDay()
        val missed = (start..end).filter { Catalog.daily(it).difficulty != Catalog.dailyDifficulty(it) }
        assertEquals("days dealt below their level", emptyList<Long>(), missed)
    }

    @Test
    fun `a page knows every technique it needs`() {
        val spec = Catalog.classic(Difficulty.Tricky, 0)
        assertTrue(spec.hardest in spec.techniques)
        assertTrue(spec.techniques.size > 1)
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
