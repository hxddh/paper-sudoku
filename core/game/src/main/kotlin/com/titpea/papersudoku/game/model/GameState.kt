package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.DigitMask
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Snapshot(
    val values: List<Int>,
    val notes: List<Int>,
    val selected: Int?,
)

/**
 * How much the Coach was needed. [deepest] is 0 (never), 1 (look), 2 (why) or 3 (answer).
 */
@Serializable
data class CoachUsage(
    val asks: Int = 0,
    val deepest: Int = 0,
) {
    fun reached(level: Int): CoachUsage = copy(deepest = maxOf(deepest, level))
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
) {
    val size: Int get() = spec.size

    fun isGiven(index: Int): Boolean = spec.givens[index] != 0

    fun isEditable(index: Int): Boolean = !solved && !isGiven(index)

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
            remember().copy(values = values.replace(index, 0))
        } else {
            remember().place(index, digit, autoClearNotes)
        }
    }

    /** Writes the Coach's answer. */
    fun placeHint(index: Int, digit: Int, autoClearNotes: Boolean): GameState {
        if (!isEditable(index) || digit !in 1..size) return this
        return remember().place(index, digit, autoClearNotes).copy(selected = index)
    }

    fun erase(): GameState {
        val index = selected ?: return this
        if (!isEditable(index)) return this
        if (values[index] == 0 && notes[index] == 0) return this
        return remember().copy(values = values.replace(index, 0), notes = notes.replace(index, 0))
    }

    fun undo(): GameState {
        if (solved) return this
        val last = history.lastOrNull() ?: return this
        return copy(
            values = last.values,
            notes = last.notes,
            selected = last.selected,
            history = history.dropLast(1),
        )
    }

    /** A clean board with the clock reset; the Coach's help stays on record. A solved page stays solved. */
    fun restart(): GameState = if (solved) this else new(spec).copy(coach = coach)

    fun addElapsed(millis: Long): GameState =
        if (solved || millis <= 0) this else copy(elapsedMillis = elapsedMillis + millis)

    fun coachAsked(): GameState = copy(coach = coach.copy(asks = coach.asks + 1).reached(1))

    fun coachReached(level: Int): GameState = copy(coach = coach.reached(level))

    /** Marks the game solved if the board matches the solution. */
    fun settle(now: Long): GameState =
        if (!solved && values == spec.solution) {
            copy(solved = true, solvedAt = now, selected = null, lockedDigit = null, history = emptyList())
        } else {
            this
        }

    fun encode(): String = json.encodeToString(serializer(), this)

    private fun place(index: Int, digit: Int, autoClearNotes: Boolean): GameState {
        var newNotes = notes.replace(index, 0)
        if (autoClearNotes) {
            val peers = spec.geometry.peers[index]
            newNotes = newNotes.mapIndexed { i, mask ->
                if (peers.contains(i)) DigitMask.remove(mask, digit) else mask
            }
        }
        val placed = copy(values = values.replace(index, digit), notes = newNotes)
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
                coach.deepest in 0..3 && elapsedMillis >= 0
        }
    }
}

private fun List<Int>.replace(index: Int, value: Int): List<Int> =
    toMutableList().also { it[index] = value }
