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
        assertEquals("000402000500500206204001002000305000", Catalog.daily(20_000).givens.joinToString(""))
        assertEquals("400301000640020000000060014000306002", Catalog.daily(20_382).givens.joinToString(""))
    }

    @Test
    fun `practice pages are pinned`() {
        assertEquals(
            "005007020008001075070000004810030000050602010000010053300000040120400300040700900",
            Catalog.classic(Difficulty.Gentle, 0).givens.joinToString(""),
        )
        assertEquals(
            "000809400730002080004000030000600005006010800500007000020000600070500023001903000",
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
}
