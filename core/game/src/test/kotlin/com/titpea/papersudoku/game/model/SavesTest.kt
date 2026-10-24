package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 10.0 saves: short undo history, packed moves, and every 9.0 save still opens. */
class SavesTest {

    private val spec = Catalog.classic(Difficulty.Tricky, 0)

    /** 300 moves over the whole page: notes in every empty cell, digits written, erased, undone. */
    private fun scripted(): GameState {
        var s = GameState.new(spec).pencilInCandidates()
        val empty = spec.givens.indices.filter { spec.givens[it] == 0 }
        var n = 0
        while (s.moves.size < 300) {
            val cell = empty[n % empty.size]
            s = s.select(cell)
            s = when (n % 5) {
                0, 1 -> s.input(spec.solution[cell], true)
                2 -> s.input(spec.solution[cell] % 9 + 1, true)
                3 -> s.erase().toggleNotesMode().input(spec.solution[cell], true).toggleNotesMode()
                else -> s.undo()
            }
            n++
        }
        return s.addElapsed(600_000).coachAsked()
    }

    @Test
    fun `a 300 move game saves in 20 KB or less`() {
        val game = scripted()
        assertTrue(game.history.size > GameState.SAVED_HISTORY)
        val saved = game.encode()
        val nine = Json.encodeToString(GameState.serializer(), game)
        println("300-move save: ${saved.toByteArray().size} bytes; as 9.0 wrote it: ${nine.toByteArray().size} bytes")
        assertTrue("${saved.length} bytes", saved.toByteArray().size <= 20 * 1024)
        val back = assertDecodes(saved)
        assertEquals(game.copy(history = game.history.takeLast(GameState.SAVED_HISTORY)), back)
        assertEquals(game.moves, back.moves)
        // Undo still walks back as far as was kept.
        var undone = back
        repeat(GameState.SAVED_HISTORY) { undone = undone.undo() }
        assertEquals(game.history[game.history.size - GameState.SAVED_HISTORY].values, undone.values)
        assertTrue(undone.history.isEmpty())
    }

    @Test
    fun `a save in the 9_0 format still decodes`() {
        val game = scripted()
        // 9.0 wrote the state as it is, with every undo step and moves as objects.
        val nine = Json.encodeToString(GameState.serializer(), game)
        assertTrue(nine.contains("\"moves\":[{\"cell\""))
        assertEquals(game, assertDecodes(nine))
    }

    @Test
    fun `a 9_0 save written by hand decodes`() {
        val daily = Catalog.daily(20_000)
        val cell = daily.givens.indexOfFirst { it == 0 }
        val digit = daily.solution[cell]
        val values = daily.givens.toMutableList().also { it[cell] = digit }
        val saved = """{"spec":${Json.encodeToString(PuzzleSpec.serializer(), daily)},"values":${values},""" +
            """"notes":${List(36) { 0 }},"selected":$cell,"history":[{"values":${daily.givens},""" +
            """"notes":${List(36) { 0 }},"selected":$cell}],"moves":[{"cell":$cell,"digit":$digit},""" +
            """{"cell":$cell,"digit":0,"coach":true},{"cell":$cell,"digit":$digit}]}"""
        val game = assertDecodes(saved)
        assertEquals(listOf(Move(cell, digit), Move(cell, 0, coach = true), Move(cell, digit)), game.moves)
        assertEquals(daily.givens, game.undo().values)
        // Saved again it is packed, and reads back the same.
        val again = game.encode()
        assertFalse(again.contains("\"cell\""))
        assertEquals(game, assertDecodes(again))
    }

    @Test
    fun `packing keeps every field`() {
        val s = Snapshot(List(81) { it % 10 }, List(81) { (it * 37) % 1023 }, 80)
        assertEquals(s, Packing.snapshot(Packing.snapshots(listOf(s))!!.single()))
        assertEquals(s.copy(selected = null), Packing.snapshot(Packing.snapshots(listOf(s.copy(selected = null)))!!.single()))
        val moves = listOf(Move(0, 0), Move(80, 9, coach = true), Move(35, 1))
        assertEquals(moves, Packing.moves(moves)!!.map(Packing::move))
    }

    private fun assertDecodes(text: String): GameState {
        val game = GameState.decode(text)
        assertNotNull(text.take(200), game)
        return game!!
    }
}
