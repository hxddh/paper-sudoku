package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [CatalogTable] deals exactly what the search deals, in one carve. Checking every entry against
 * the search would take minutes, so every 37th entry of each part is checked, and its first and
 * last; carve counts, not the clock, keep the speed honest.
 */
class CatalogTableTest {

    private val firstDay = LocalDate.of(2026, 1, 1).toEpochDay()
    private val lastDay = LocalDate.of(2029, 12, 31).toEpochDay()

    private val pathRows = Difficulty.entries.flatMap { level ->
        Technique.entries.filter { it.tier in 2..level.maxTier }.map { level to it }
    }

    @Test
    fun `the table is for this generator and covers what it says`() {
        assertEquals(Catalog.GENERATOR_VERSION, CatalogTableData.GENERATOR_VERSION)
        assertTrue(CatalogTable.current)
        assertEquals(firstDay, CatalogTableData.DAILY_FIRST)
        assertEquals((lastDay - firstDay + 1).toInt(), CatalogTable.dailyCount)
        for (level in Difficulty.entries) assertEquals(level.name, 300, CatalogTable.classicCount(level))
        for ((level, technique) in pathRows) assertEquals("$level $technique", 100, CatalogTable.pathCount(level, technique))
    }

    @Test
    fun `dailies from the table are the searched ones`() {
        val days = (firstDay..lastDay).toList()
        for (day in sample(days)) {
            assertSame("daily $day", Catalog.dealDaily(day, table = false).spec, Catalog.dealDaily(day).spec)
        }
    }

    @Test
    fun `practice pages from the table are the searched ones`() {
        for (level in Difficulty.entries) {
            for (n in sample((0 until 300).toList())) {
                assertSame("$level $n", Catalog.dealClassic(level, n, table = false).spec, Catalog.dealClassic(level, n).spec)
            }
        }
    }

    @Test
    fun `path pages from the table are the searched ones`() {
        val all = pathRows.flatMap { (level, technique) -> (0 until 100).map { Triple(level, technique, it) } }
        for ((level, technique, n) in sample(all)) {
            assertSame(
                "$level $technique $n",
                Catalog.dealPractice(level, n, technique, table = false).spec,
                Catalog.dealPractice(level, n, technique).spec,
            )
        }
    }

    @Test
    fun `every tabled daily and practice page is one carve`() {
        for (day in firstDay..lastDay) {
            val dealt = Catalog.dealDaily(day)
            assertEquals("daily $day", 1, dealt.carves)
            assertEquals("daily $day", Catalog.dailyDifficulty(day), dealt.spec.difficulty)
        }
        for (level in Difficulty.entries) {
            for (n in 0 until 300) assertEquals("$level $n", 1, Catalog.dealClassic(level, n).carves)
        }
        for ((level, technique) in pathRows) {
            for (n in 0 until 100) assertEquals("$level $technique $n", 1, Catalog.dealPractice(level, n, technique).carves)
        }
    }

    @Test
    fun `a path page past the table costs at most its budget and is the same each time`() {
        // The rarest technique at its level: most of its tries miss.
        for ((level, technique) in listOf(Difficulty.Deep to Technique.HiddenTriple, Difficulty.Tricky to Technique.XWing)) {
            val dealt = Catalog.dealPractice(level, 100, technique)
            assertTrue("$technique: ${dealt.carves} carves", dealt.carves <= Catalog.PATH_CARVES)
            assertEquals(dealt.spec, Catalog.practice(level, 100, technique))
            val plain = Catalog.classic(level, 100)
            if (dealt.spec.key == plain.key) {
                assertEquals(plain, dealt.spec)
            } else {
                assertEquals(Catalog.practiceKey(level, 100, technique), dealt.spec.key)
                assertTrue(technique in dealt.spec.techniques)
                assertEquals(level, dealt.spec.difficulty)
            }
        }
    }

    @Test
    fun `past the table pages are searched as before`() {
        val day = lastDay + 1
        val dealt = Catalog.dealDaily(day)
        assertEquals(Catalog.dealDaily(day, table = false).spec, dealt.spec)
        assertEquals(Catalog.dailyDifficulty(day), dealt.spec.difficulty)
        assertEquals(Catalog.dealClassic(Difficulty.Steady, 300, table = false).spec, Catalog.classic(Difficulty.Steady, 300))
    }

    private fun assertSame(label: String, searched: PuzzleSpec, tabled: PuzzleSpec) = assertEquals(label, searched, tabled)

    private fun <T> sample(entries: List<T>): List<T> =
        entries.filterIndexed { i, _ -> i % 37 == 0 || i == entries.lastIndex }
}
