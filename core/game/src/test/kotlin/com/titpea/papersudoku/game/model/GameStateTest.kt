package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.DigitMask
import com.titpea.papersudoku.engine.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateTest {

    private val spec = Catalog.daily(20_000)
    private val empty = spec.givens.indexOfFirst { it == 0 }
    private val answer = spec.solution[empty]

    @Test
    fun `daily is deterministic and keyed by date`() {
        assertEquals(spec, Catalog.daily(20_000))
        assertEquals("daily-20000", spec.key)
        assertEquals(20_000L, spec.epochDay)
        assertNotEquals(spec.givens, Catalog.daily(20_001).givens)
        assertEquals(Catalog.dailyDifficulty(20_000), spec.difficulty)
    }

    @Test
    fun `weekend dailies are steady`() {
        // 2026-10-24 is a Saturday, 2026-10-21 a Wednesday.
        assertEquals(Difficulty.Steady, Catalog.dailyDifficulty(20_750))
        assertEquals(Difficulty.Gentle, Catalog.dailyDifficulty(20_747))
        assertEquals(Difficulty.Steady, Catalog.daily(20_750).difficulty)
    }

    @Test
    fun `classic numbers give different puzzles per difficulty`() {
        val a = Catalog.classic(Difficulty.Gentle, 0)
        val b = Catalog.classic(Difficulty.Gentle, 1)
        assertEquals(9, a.size)
        assertNotEquals(a.givens, b.givens)
        assertEquals("classic-gentle-1", b.key)
    }

    @Test
    fun `input places, repeats clear, and givens are protected`() {
        var s = GameState.new(spec).select(empty).input(answer, autoClearNotes = true)
        assertEquals(answer, s.values[empty])
        s = s.input(answer, autoClearNotes = true)
        assertEquals(0, s.values[empty])
        val given = spec.givens.indexOfFirst { it != 0 }
        assertEquals(s.values, s.select(given).input(1, true).values)
    }

    @Test
    fun `placing a digit clears it from peer notes when asked`() {
        val peer = spec.geometry.peers[empty].first { spec.givens[it] == 0 }
        val noted = GameState.new(spec).select(peer).toggleNotesMode().input(answer, true).toggleNotesMode()
        assertTrue(DigitMask.contains(noted.notes[peer], answer))
        assertFalse(DigitMask.contains(noted.select(empty).input(answer, true).notes[peer], answer))
        assertTrue(DigitMask.contains(noted.select(empty).input(answer, false).notes[peer], answer))
    }

    @Test
    fun `a page only looked at is untouched, and undoing back to blank makes it so again`() {
        val fresh = GameState.new(spec).select(empty).addElapsed(30_000)
        assertTrue(fresh.untouched)
        val written = fresh.input(answer, true)
        assertFalse(written.untouched)
        assertTrue(written.undo().untouched)
        assertFalse(fresh.toggleNotesMode().input(1, true).untouched)
        assertFalse(fresh.coachAsked().untouched)
    }

    @Test
    fun `erasing a mistake gives back the pencil marks the wrong digit crossed out`() {
        // A wrong digit the peer could still hold: nothing printed around it rules it out.
        val (peer, wrong) = spec.geometry.peers[empty].filter { spec.givens[it] == 0 }.firstNotNullOf { p ->
            (1..spec.size).firstOrNull { d -> d != answer && spec.geometry.peers[p].none { it != empty && spec.givens[it] == d } }?.let { p to it }
        }
        val noted = GameState.new(spec).select(peer).toggleNotesMode().input(wrong, true).toggleNotesMode()
        val slip = noted.select(empty).input(wrong, true)
        assertFalse(DigitMask.contains(slip.notes[peer], wrong))
        val erased = slip.eraseMistake(empty)
        assertEquals(0, erased.values[empty])
        assertTrue(DigitMask.contains(erased.notes[peer], wrong))
        // One undo takes the whole correction back.
        assertEquals(slip.values, erased.undo().values)
        assertEquals(slip.notes, erased.undo().notes)
    }

    @Test
    fun `erasing a mistake does not give back a mark a later digit ruled out`() {
        val classic = Catalog.classic(Difficulty.Tricky, 0)
        val geometry = classic.geometry
        // A wrong digit, a pencilled cell it crosses out, and a correct placement of the same digit
        // that also sees that cell (and does not see the wrong one).
        val empty = classic.givens.indices.filter { classic.givens[it] == 0 }
        val case = empty.asSequence().flatMap { wrong ->
            geometry.peers[wrong].asSequence().filter { classic.givens[it] == 0 }.flatMap { marked ->
                val digit = classic.solution[marked]
                geometry.peers[marked].asSequence()
                    .filter { it != wrong && classic.givens[it] == 0 && classic.solution[it] != digit && !geometry.sees(it, wrong) }
                    .mapNotNull { later -> empty.firstOrNull { classic.solution[it] == classic.solution[later] && geometry.sees(it, marked) && it != wrong && !geometry.sees(it, wrong) }?.let { Triple(wrong, marked, it) } }
                    .filter { (w, _, l) -> classic.solution[w] != classic.solution[l] }
            }
        }.first()
        val (wrong, marked, later) = case
        val digit = classic.solution[later]
        val start = GameState.new(classic).select(marked).toggleNotesMode().input(digit, true).toggleNotesMode()
        val slipped = start.select(wrong).input(digit, true).select(later).input(digit, true)
        val erased = slipped.eraseMistake(wrong)
        assertEquals(0, erased.values[wrong])
        assertFalse(DigitMask.contains(erased.notes[marked], digit))
    }

    @Test
    fun `pencilling in candidates fills only unmarked empty cells, and undoes in one step`() {
        val classic = Catalog.classic(Difficulty.Steady, 0)
        val cell = classic.givens.indexOfFirst { it == 0 }
        val marked = GameState.new(classic).select(cell).toggleNotesMode().input(classic.solution[cell], true).toggleNotesMode()
        val filled = marked.pencilInCandidates()
        assertEquals(marked.notes[cell], filled.notes[cell])
        for (i in classic.givens.indices) {
            if (i == cell || classic.givens[i] != 0) continue
            assertTrue(DigitMask.contains(filled.notes[i], classic.solution[i]))
            val seen = classic.geometry.peers[i].map { classic.givens[it] }.toSet()
            assertTrue(DigitMask.digits(filled.notes[i]).none { it in seen })
        }
        assertEquals(marked.notes, filled.undo().notes)
        assertEquals(marked.coach, filled.coach)
        assertEquals(filled, filled.pencilInCandidates())
    }

    @Test
    fun `undo walks back values notes and selection`() {
        val s0 = GameState.new(spec).select(empty)
        val s1 = s0.toggleNotesMode().input(1, true).input(2, true)
        assertEquals(DigitMask.of(listOf(1, 2)), s1.notes[empty])
        assertEquals(DigitMask.of(listOf(1)), s1.undo().notes[empty])
        assertEquals(0, s1.undo().undo().notes[empty])
        assertEquals(s1.undo().undo().history, emptyList<Snapshot>())
    }

    @Test
    fun `locked digit writes on tap`() {
        val s = GameState.new(spec).toggleLock(answer).tap(empty, true)
        assertEquals(answer, s.values[empty])
        assertEquals(answer, s.lockedDigit)
        assertNull(s.toggleLock(answer).lockedDigit)
    }

    @Test
    fun `solving settles once and freezes the board`() {
        var s = GameState.new(spec)
        for (i in spec.solution.indices) if (spec.givens[i] == 0) s = s.select(i).input(spec.solution[i], true)
        assertFalse(s.solved)
        s = s.addElapsed(61_000).settle(now = 123)
        assertTrue(s.solved)
        assertEquals(123L, s.solvedAt)
        assertEquals(s, s.settle(now = 456))
        assertEquals(s, s.undo())
        assertEquals(61_000L, s.addElapsed(5_000).elapsedMillis)
        assertTrue(s.remaining.drop(1).all { it == 0 })
    }

    @Test
    fun `round trips through json and rejects tampered saves`() {
        val s = GameState.new(spec).select(empty).input(answer, true).coachAsked().coachReached(2).addElapsed(9_000)
        assertEquals(s, GameState.decode(s.encode()))
        val tampered = s.copy(values = s.values.map { if (it == 0) 0 else it }.toMutableList().also {
            val given = spec.givens.indexOfFirst { g -> g != 0 }
            it[given] = (spec.givens[given] % spec.size) + 1
        })
        assertNull(GameState.decode(tampered.encode()))
        assertNull(GameState.decode("{not json"))
    }

    @Test
    fun `coach usage keeps the deepest level`() {
        val s = GameState.new(spec).coachAsked().coachReached(3).coachAsked()
        assertEquals(CoachUsage(asks = 2, deepest = 3), s.coach)
    }

    @Test
    fun `restart clears the board but keeps the coach on record`() {
        val s = GameState.new(spec).select(empty).input(answer, true).coachAsked().coachReached(3).addElapsed(5_000)
        val restarted = s.restart()
        assertEquals(spec.givens, restarted.values)
        assertEquals(0L, restarted.elapsedMillis)
        assertEquals(3, restarted.coach.deepest)
    }

    @Test
    fun `a solved page cannot be restarted`() {
        var s = GameState.new(spec)
        for (i in spec.solution.indices) if (spec.givens[i] == 0) s = s.select(i).input(spec.solution[i], true)
        s = s.settle(now = 1)
        assertEquals(s, s.restart())
    }

    @Test
    fun `the held digit is put down once every copy is placed`() {
        val digit = spec.solution[empty]
        val cells = spec.solution.indices.filter { spec.solution[it] == digit && spec.givens[it] == 0 }
        var s = GameState.new(spec).toggleLock(digit)
        for (cell in cells.dropLast(1)) s = s.tap(cell, autoClearNotes = true)
        assertEquals(digit, s.lockedDigit)
        s = s.tap(cells.last(), autoClearNotes = true)
        assertNull(s.lockedDigit)
    }

    @Test
    fun `damaged saves that parse are still refused`() {
        val good = GameState.new(spec).select(empty).input(answer, true)
        assertNull(GameState.decode(good.copy(values = good.values.toMutableList().also { it[empty] = 12 }).encode()))
        assertNull(GameState.decode(good.copy(lockedDigit = 0).encode()))
        assertNull(GameState.decode(good.copy(history = listOf(Snapshot(listOf(1), listOf(0), null))).encode()))
        assertNull(GameState.decode(good.copy(elapsedMillis = -1).encode()))
        assertEquals(good, GameState.decode(good.encode()))
    }

    @Test
    fun `keys name their day and level`() {
        assertEquals(20_000L, Catalog.dayOf(Catalog.dailyKey(20_000)))
        assertNull(Catalog.dayOf(Catalog.classicKey(Difficulty.Steady, 3)))
        assertEquals("classic-steady-", Catalog.classicPrefix(Difficulty.Steady))
        assertTrue(Catalog.classicKey(Difficulty.Steady, 3).startsWith(Catalog.classicPrefix(Difficulty.Steady)))
    }

    @Test
    fun `saves with an impossible puzzle are refused`() {
        val good = GameState.new(Catalog.daily(20_000))
        val json = good.encode()
        fun tampered(transform: (String) -> String) = GameState.decode(transform(json))
        // No board at all.
        assertNull(tampered { it.replace("\"size\":6", "\"size\":0").replace("\"boxRows\":2", "\"boxRows\":0").replace("\"boxCols\":3", "\"boxCols\":0") })
        // A given that disagrees with the solution.
        val given = good.spec.givens.indexOfFirst { it != 0 }
        val wrong = good.spec.givens.toMutableList().also { it[given] = it[given] % 6 + 1 }
        assertNull(GameState.decode(good.copy(spec = good.spec.copy(givens = wrong), values = wrong).encode()))
        // Marked solved with a board that isn't.
        assertNull(GameState.decode(good.copy(solved = true).encode()))
    }
}
