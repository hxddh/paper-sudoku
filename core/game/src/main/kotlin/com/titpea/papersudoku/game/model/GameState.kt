package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.DigitMask
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.engine.Technique
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Snapshot(
    val values: List<Int>,
    val notes: List<Int>,
    val selected: Int?,
)

/**
 * A pencil mark [digit] in [cell] that a wrong digit at [by] took away (written there, or left out
 * when candidates were pencilled in). Kept only for wrong digits, so the list stays small: when
 * the Coach takes the mistake back, the mark comes back with it.
 */
@Serializable
data class Struck(val cell: Int, val digit: Int, val by: Int)

/** One change of a digit on the page, in order: [digit] 0 is an erasure. For reading a page back. */
@Serializable
data class Move(val cell: Int, val digit: Int, val coach: Boolean = false)

/**
 * How much the Coach was needed. [deepest] is 0 (never), 1 (look), 2 (why) or 3 (answer).
 */
@Serializable
data class CoachUsage(
    val asks: Int = 0,
    val deepest: Int = 0,
    /** How far the Coach went for each technique it taught on this page (10.0). */
    val byTechnique: Map<Technique, Int> = emptyMap(),
) {
    fun reached(level: Int, technique: Technique? = null): CoachUsage = copy(
        deepest = maxOf(deepest, level),
        byTechnique = if (technique == null) byTechnique else byTechnique + (technique to maxOf(byTechnique[technique] ?: 0, level)),
    )
}

/**
 * Complete, serializable state of one game. Every transition is a pure function returning a new
 * state, so undo is a list of snapshots and saving is one JSON string.
 *
 * Notes are bitmasks (bit `d` = digit `d`).
 */
@Serializable
data class GameState(
    val spec: PuzzleSpec,
    val values: List<Int>,
    val notes: List<Int>,
    val selected: Int? = null,
    val notesMode: Boolean = false,
    val lockedDigit: Int? = null,
    val history: List<Snapshot> = emptyList(),
    val elapsedMillis: Long = 0,
    val coach: CoachUsage = CoachUsage(),
    val solved: Boolean = false,
    /** Wall-clock time the game was first solved, for the journal. */
    val solvedAt: Long? = null,
    /** Marks taken away by wrong digits (9.0). */
    val struck: List<Struck> = emptyList(),
    /** Every digit written or erased, in order (9.0); pages begun earlier have none. */
    val moves: List<Move> = emptyList(),
) {
    val size: Int get() = spec.size

    fun isGiven(index: Int): Boolean = spec.givens[index] != 0

    fun isEditable(index: Int): Boolean = !solved && !isGiven(index)

    /**
     * Nothing done on the page yet: no digit or note written (or all undone) and the Coach never
     * asked. Only looking at a page does not begin it.
     */
    val untouched: Boolean
        get() = !solved && history.isEmpty() && coach.asks == 0 && values == spec.givens && notes.all { it == 0 }

    /** How many more times each digit 1..size can still be placed. Index 0 is unused. */
    val remaining: List<Int>
        get() {
            val counts = IntArray(size + 1) { if (it == 0) 0 else size }
            for (v in values) if (v != 0) counts[v]--
            return counts.toList()
        }

    fun select(index: Int?): GameState =
        if (index == null || index in values.indices) copy(selected = index) else this

    /** A tap on the board: with a locked digit it also writes that digit. */
    fun tap(index: Int, autoClearNotes: Boolean): GameState {
        val selectedState = select(index)
        val digit = lockedDigit ?: return selectedState
        return if (selectedState.isEditable(index)) selectedState.input(digit, autoClearNotes) else selectedState
    }

    fun toggleNotesMode(): GameState = copy(notesMode = !notesMode)

    fun toggleLock(digit: Int): GameState =
        if (digit !in 1..size) this else copy(lockedDigit = if (lockedDigit == digit) null else digit)

    /** Enters [digit] at the selection: a note in notes mode, otherwise a value (again = clear). */
    fun input(digit: Int, autoClearNotes: Boolean): GameState {
        if (digit !in 1..size) return this
        val index = selected ?: return this
        if (!isEditable(index)) return this
        return if (notesMode) {
            if (values[index] != 0) this
            else remember().copy(notes = notes.replace(index, DigitMask.toggle(notes[index], digit)))
        } else if (values[index] == digit) {
            remember().copy(values = values.replace(index, 0), moves = moves + Move(index, 0))
        } else {
            remember().place(index, digit, autoClearNotes)
        }
    }

    /** Writes the Coach's answer. */
    fun placeHint(index: Int, digit: Int, autoClearNotes: Boolean): GameState {
        if (!isEditable(index) || digit !in 1..size) return this
        return remember().place(index, digit, autoClearNotes, coach = true).copy(selected = index)
    }

    fun erase(): GameState {
        val index = selected ?: return this
        if (!isEditable(index)) return this
        if (values[index] == 0 && notes[index] == 0) return this
        val logged = if (values[index] != 0) moves + Move(index, 0) else moves
        return remember().copy(values = values.replace(index, 0), notes = notes.replace(index, 0), moves = logged)
    }

    /**
     * Takes back a wrong digit the Coach pointed out. The pencil marks it took away (crossed out
     * when it was written, or left out when candidates were pencilled in) come back wherever the
     * digit is possible again, so a cell's own answer is never left missing from its notes.
     */
    fun eraseMistake(index: Int): GameState {
        if (!isEditable(index) || values[index] == 0) return this
        val digit = values[index]
        val after = values.replace(index, 0)
        val geometry = spec.geometry
        // Possible again: no digit left on the board that rules it out (a second slip of the same
        // digit, or a later correct one, keeps its mark away).
        fun possible(cell: Int) = after[cell] == 0 && geometry.peers[cell].none { after[it] == digit }
        val back = struck.filter { it.digit == digit && after[it.by] != digit && possible(it.cell) }
        val restored = back.fold(notes) { n, s -> n.replace(s.cell, n[s.cell] or (1 shl digit)) }
        return remember().copy(
            values = after,
            notes = restored.replace(index, 0),
            selected = index,
            struck = struck - back.toSet(),
            moves = moves + Move(index, 0, coach = true),
        )
    }

    /** Gives back a pencil mark the Coach found missing (10.0); one undo takes it away again. */
    fun restoreMark(index: Int, digit: Int): GameState {
        if (!isEditable(index) || values[index] != 0 || digit !in 1..size || DigitMask.contains(notes[index], digit)) return this
        return remember().copy(notes = notes.replace(index, notes[index] or (1 shl digit)), selected = index)
    }

    /**
     * Pencils in every candidate (each digit not yet in the cell's row, column or box) in the
     * empty cells that have no marks yet. Cells already marked keep the player's own marks. Only
     * notes: the Coach's help and the clean stamp are untouched. One undo takes it all back.
     */
    fun pencilInCandidates(): GameState {
        if (solved) return this
        val engine = SudokuEngine(spec.puzzle)
        val board = values.toIntArray()
        val geometry = spec.geometry
        val left = mutableListOf<Struck>()
        val filled = notes.mapIndexed { i, mask ->
            if (values[i] != 0 || mask != 0) return@mapIndexed mask
            // A digit left out only because of a wrong digit is remembered, to come back with it.
            for (peer in geometry.peers[i]) {
                val v = values[peer]
                if (v != 0 && !isGiven(peer) && v != spec.solution[peer]) left += Struck(i, v, peer)
            }
            DigitMask.of(engine.candidates(board, i))
        }
        return if (filled == notes) this else remember().copy(notes = filled, struck = struck + left)
    }

    fun undo(): GameState {
        if (solved) return this
        val last = history.lastOrNull() ?: return this
        val changed = values.indices.filter { last.values[it] != values[it] }.map { Move(it, last.values[it]) }
        return copy(
            values = last.values,
            notes = last.notes,
            selected = last.selected,
            history = history.dropLast(1),
            moves = moves + changed,
        )
    }

    /** A clean board with the clock reset; the Coach's help stays on record. A solved page stays solved. */
    fun restart(): GameState = if (solved) this else new(spec).copy(coach = coach)

    fun addElapsed(millis: Long): GameState =
        if (solved || millis <= 0) this else copy(elapsedMillis = elapsedMillis + millis)

    fun coachAsked(): GameState = copy(coach = coach.copy(asks = coach.asks + 1).reached(1))

    fun coachReached(level: Int, technique: Technique? = null): GameState = copy(coach = coach.reached(level, technique))

    /** Marks the game solved if the board matches the solution. */
    fun settle(now: Long): GameState =
        if (!solved && values == spec.solution) {
            copy(solved = true, solvedAt = now, selected = null, lockedDigit = null, history = emptyList())
        } else {
            this
        }

    fun encode(): String = json.encodeToString(serializer(), this)

    private fun place(index: Int, digit: Int, autoClearNotes: Boolean, coach: Boolean = false): GameState {
        var newNotes = notes.replace(index, 0)
        var newStruck = struck
        if (autoClearNotes) {
            val peers = spec.geometry.peers[index]
            // A wrong digit remembers the marks it takes, so taking it back gives them back.
            if (digit != spec.solution[index]) {
                newStruck = newStruck + peers.filter { DigitMask.contains(notes[it], digit) }.map { Struck(it, digit, index) }
            }
            newNotes = newNotes.mapIndexed { i, mask ->
                if (peers.contains(i)) DigitMask.remove(mask, digit) else mask
            }
        }
        val placed = copy(
            values = values.replace(index, digit),
            notes = newNotes,
            struck = newStruck,
            moves = moves + Move(index, digit, coach),
        )
        // Every copy of the held digit is on the board: put it down.
        return if (placed.lockedDigit != null && placed.remaining[placed.lockedDigit] <= 0) placed.copy(lockedDigit = null) else placed
    }

    private fun remember(): GameState {
        val snapshot = Snapshot(values, notes, selected)
        return copy(history = (history + snapshot).takeLast(MAX_HISTORY))
    }

    companion object {
        const val MAX_HISTORY = 200

        private val json = Json { ignoreUnknownKeys = true }

        fun new(spec: PuzzleSpec): GameState = GameState(
            spec = spec,
            values = spec.givens,
            notes = List(spec.cellCount) { 0 },
        )

        /** Null if the text is not a game this version understands. */
        fun decode(text: String): GameState? = runCatching {
            json.decodeFromString(serializer(), text).takeIf { it.isConsistent() }
        }.getOrNull()

        /** A readable but damaged save is refused rather than crashing later (remaining, undo). */
        private fun GameState.isConsistent(): Boolean {
            // The puzzle itself first: a size the board can't have, givens that disagree with the
            // solution or a solution out of range would throw as soon as the page is shown.
            if (runCatching { spec.puzzle.geometry }.isFailure) return false
            val cells = spec.cellCount
            val digits = 0..spec.size
            val noteMask = (1 shl (spec.size + 1)) - 1
            fun boardOk(v: List<Int>, n: List<Int>, sel: Int?) =
                v.size == cells && n.size == cells && v.all { it in digits } &&
                    n.all { it and noteMask.inv() == 0 } && (sel == null || sel in 0 until cells)
            return spec.givens.size == cells && spec.solution.size == cells &&
                boardOk(values, notes, selected) &&
                values.indices.all { spec.givens[it] == 0 || values[it] == spec.givens[it] } &&
                (lockedDigit == null || lockedDigit in 1..spec.size) &&
                history.all { boardOk(it.values, it.notes, it.selected) } &&
                coach.deepest in 0..3 && coach.byTechnique.values.all { it in 1..3 } && elapsedMillis >= 0 &&
                struck.all { it.cell in 0 until cells && it.by in 0 until cells && it.digit in 1..spec.size } &&
                moves.all { it.cell in 0 until cells && it.digit in digits } &&
                // "Solved" with a wrong board would lock it for good.
                (!solved || values == spec.solution)
        }
    }
}

private fun List<Int>.replace(index: Int, value: Int): List<Int> =
    toMutableList().also { it[index] = value }
