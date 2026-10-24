package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class NewPagesTest {

    @Test
    fun `pages no longer dealt are still named by their keys`() {
        val wednesday = LocalDate.of(2026, 10, 21).toEpochDay()
        val sunday = Catalog.weekOf(wednesday)
        assertEquals(DayOfWeek.SUNDAY, LocalDate.ofEpochDay(sunday).dayOfWeek)
        assertTrue(sunday <= wednesday && wednesday - sunday < 7)
        assertEquals(sunday, Catalog.weekOf(sunday))
        assertEquals("weekly-$sunday", Catalog.weeklyKey(sunday))
        assertEquals(Technique.XWing, Catalog.focusTechnique(Catalog.focusPrefix(Technique.XWing) + 3))
        assertNull(Catalog.focusTechnique(Catalog.classicKey(Difficulty.Steady, 3)))
    }

    @Test
    fun `a typed-in page is checked, solved and graded`() {
        val known = Catalog.classic(Difficulty.Tricky, 3)
        val ok = Catalog.imported(known.givens) as Import.Ok
        assertEquals(known.solution, ok.spec.solution)
        assertEquals(known.difficulty, ok.spec.difficulty)
        assertFalse(ok.spec.beyondCoach)
        assertEquals(ok.spec.key, (Catalog.imported(known.givens) as Import.Ok).spec.key)

        val clash = known.givens.toMutableList().also { g ->
            val a = g.indexOfFirst { it != 0 }
            val b = known.geometry.peers[a].first { g[it] == 0 }
            g[b] = g[a]
        }
        assertEquals(Import.Reason.Clash, (Catalog.imported(clash) as Import.Invalid).reason)
        assertEquals(Import.Reason.TooFew, (Catalog.imported(List(81) { if (it < 10) known.solution[it] else 0 }) as Import.Invalid).reason)
        // Dropping clues until a second solution appears.
        var loose = known.givens
        for (i in known.givens.indices) {
            if (loose[i] == 0) continue
            val next = loose.toMutableList().also { it[i] = 0 }
            if (next.count { it != 0 } < 17) break
            val result = Catalog.imported(next)
            if (result is Import.Invalid && result.reason == Import.Reason.ManySolutions) return
            loose = next
        }
        error("never found a page with many solutions")
    }
}
