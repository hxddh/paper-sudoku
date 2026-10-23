package com.titpea.papersudoku.data

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class PuzzleCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val version = "-v${Catalog.GENERATOR_VERSION}.${Catalog.REVISION}.json"

    @Test
    fun `pages are generated once, then read back`() = runBlocking {
        val dir = folder.newFolder("puzzles")
        val today = LocalDate.now().toEpochDay()
        val source = CachedPuzzleSource(dir, Dispatchers.Unconfined)
        val first = source.daily(today)
        val file = File(dir, Catalog.dailyKey(today) + version)
        assertTrue(file.exists())
        assertEquals(first, source.daily(today))
        assertEquals(Catalog.classic(Difficulty.Gentle, 2), source.classic(Difficulty.Gentle, 2))
    }

    @Test
    fun `a damaged file is regenerated`() = runBlocking {
        val dir = folder.newFolder("puzzles")
        val today = LocalDate.now().toEpochDay()
        File(dir, Catalog.dailyKey(today) + version).writeText("{not json")
        assertEquals(Catalog.daily(today), CachedPuzzleSource(dir, Dispatchers.Unconfined).daily(today))
    }

    @Test
    fun `stale files are pruned`() = runBlocking {
        val dir = folder.newFolder("puzzles")
        val today = LocalDate.now().toEpochDay()
        val oldGenerator = File(dir, Catalog.dailyKey(today) + "-v0.json").apply { writeText("{}") }
        val pastDay = File(dir, Catalog.dailyKey(today - 5) + version).apply { writeText("{}") }
        val practice = File(dir, Catalog.classicKey(Difficulty.Steady, 4) + version).apply { writeText("{}") }
        CachedPuzzleSource(dir, Dispatchers.Unconfined).daily(today)
        assertFalse(oldGenerator.exists())
        assertFalse(pastDay.exists())
        assertTrue(practice.exists())
    }
}
