package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Everybody gets the same daily from the date, and saved pages outlive releases. A generator or
 * format change that breaks either must be deliberate: bump [Catalog.GENERATOR_VERSION] or keep the
 * old format readable, then update these pins.
 */
class GoldenTest {

    @Test
    fun `dailies are pinned`() {
        assertEquals("000002120004001300005200600013300000", Catalog.daily(20_000).givens.joinToString(""))
        assertEquals("000062000450640000000043014000360000", Catalog.daily(20_382).givens.joinToString(""))
    }

    @Test
    fun `practice pages are pinned`() {
        assertEquals(
            "926000000008065020007000400063000008070504030800000270001000300050670900000000165",
            Catalog.classic(Difficulty.Gentle, 0).givens.joinToString(""),
        )
        assertEquals(
            "890000001401005200000004003007009000300040006000100300500800000003500907700000035",
            Catalog.classic(Difficulty.Tricky, 0).givens.joinToString(""),
        )
    }

    @Test
    fun `a page saved by 3_0 still opens`() {
        val saved = """{"spec":{"key":"daily-20000","kind":"Daily","difficulty":"Gentle","hardest":"HiddenSingle","size":6,"boxRows":2,"boxCols":3,"givens":[0,0,0,4,0,2,0,0,0,5,0,0,5,0,0,2,0,6,2,0,4,0,0,1,0,0,2,0,0,0,3,0,5,0,0,0],"solution":[1,5,3,4,6,2,4,2,6,5,1,3,5,3,1,2,4,6,2,6,4,3,5,1,6,4,2,1,3,5,3,1,5,6,2,4],"epochDay":20000},"values":[1,0,0,4,0,2,0,0,0,5,0,0,5,0,0,2,0,6,2,0,4,0,0,1,0,0,2,0,0,0,3,0,5,0,0,0],"notes":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"selected":0,"history":[{"values":[0,0,0,4,0,2,0,0,0,5,0,0,5,0,0,2,0,6,2,0,4,0,0,1,0,0,2,0,0,0,3,0,5,0,0,0],"notes":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],"selected":0}],"elapsedMillis":61000,"coach":{"asks":1,"deepest":2}}"""
        val game = GameState.decode(saved)
        assertNotNull(game)
        game!!
        assertEquals(1, game.values[0])
        assertEquals(61_000L, game.elapsedMillis)
        assertEquals(2, game.coach.deepest)
        assertEquals(0, game.undo().values[0])
    }

    @Test
    fun `stored names are pinned`() {
        // These names are written to the database, saved JSON and settings: renaming one would
        // silently orphan saved pages, results or techniques met.
        assertEquals(listOf("Gentle", "Steady", "Tricky", "Deep"), Difficulty.entries.map { it.name })
        assertEquals(listOf("Daily", "Classic"), PuzzleKind.entries.map { it.name })
        assertEquals(
            listOf(
                "HiddenSingle", "NakedSingle", "Pointing", "Claiming", "NakedPair", "HiddenPair", "XWing",
                "NakedTriple", "HiddenTriple", "Swordfish", "XYWing",
            ),
            com.titpea.papersudoku.engine.Technique.entries.map { it.name },
        )
    }
}
