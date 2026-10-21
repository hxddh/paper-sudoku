package com.titpea.papersudoku.engine

/**
 * Mutable working board for logical solving: placed values plus candidate bitmasks.
 * Candidates are derived from values only; a person's notes are never trusted here.
 */
class Board private constructor(
    val geometry: Geometry,
    val values: IntArray,
    val candidates: IntArray,
) {
    val isSolved: Boolean get() = values.none { it == 0 }

    fun copy(): Board = Board(geometry, values.copyOf(), candidates.copyOf())

    fun place(index: Int, digit: Int) {
        values[index] = digit
        candidates[index] = 0
        val bit = (1 shl digit).inv()
        for (peer in geometry.peers[index]) candidates[peer] = candidates[peer] and bit
    }

    fun eliminate(removals: List<Candidate>) {
        for (r in removals) candidates[r.index] = candidates[r.index] and (1 shl r.digit).inv()
    }

    fun apply(step: Step) {
        when (step) {
            is Placement -> place(step.index, step.digit)
            is Elimination -> eliminate(step.removals)
        }
    }

    /** True when some empty cell has no candidates left: the position is contradictory. */
    val isBroken: Boolean get() = values.indices.any { values[it] == 0 && candidates[it] == 0 }

    companion object {
        fun from(geometry: Geometry, values: IntArray): Board {
            require(values.size == geometry.cellCount) { "values must contain size * size cells" }
            val candidates = IntArray(geometry.cellCount)
            for (i in values.indices) {
                if (values[i] != 0) continue
                var used = 0
                for (peer in geometry.peers[i]) {
                    val v = values[peer]
                    if (v != 0) used = used or (1 shl v)
                }
                candidates[i] = geometry.allDigits and used.inv()
            }
            return Board(geometry, values.copyOf(), candidates)
        }
    }
}

/**
 * Finds the next human-style step, simplest technique first.
 */
class LogicalSolver(private val geometry: Geometry) {

    fun nextStep(board: Board): Step? =
        hiddenSingle(board, UnitKind.Box)
            ?: nakedSingle(board)
            ?: hiddenSingle(board, null)
            ?: pointing(board)
            ?: claiming(board)
            ?: nakedPair(board)
            ?: hiddenPair(board)
            ?: xWing(board)

    /** Next placement only (no eliminations). */
    fun nextSingle(board: Board): Placement? =
        hiddenSingle(board, UnitKind.Box) ?: nakedSingle(board) ?: hiddenSingle(board, null)

    fun nextElimination(board: Board): Elimination? =
        pointing(board) ?: claiming(board) ?: nakedPair(board) ?: hiddenPair(board) ?: xWing(board)

    private fun nakedSingle(board: Board): Placement? {
        for (i in board.values.indices) {
            val c = board.candidates[i]
            if (board.values[i] == 0 && c.digitCount() == 1) {
                return Placement(i, c.firstDigit(), Technique.NakedSingle, null)
            }
        }
        return null
    }

    private fun hiddenSingle(board: Board, only: UnitKind?): Placement? {
        for ((ref, cells) in geometry.units) {
            if (only != null && ref.kind != only) continue
            if (only == null && ref.kind == UnitKind.Box) continue
            for (digit in 1..geometry.size) {
                var spot = -1
                var count = 0
                for (cell in cells) {
                    if (board.values[cell] == digit) {
                        count = -1
                        break
                    }
                    if (board.values[cell] == 0 && board.candidates[cell].hasDigit(digit)) {
                        count++
                        spot = cell
                    }
                }
                if (count == 1) return Placement(spot, digit, Technique.HiddenSingle, ref)
            }
        }
        return null
    }

    /** A digit confined to one line inside a box can be removed from the rest of that line. */
    private fun pointing(board: Board): Elimination? {
        for ((b, boxCells) in geometry.boxes.withIndex()) {
            for (digit in 1..geometry.size) {
                val spots = boxCells.filter { board.values[it] == 0 && board.candidates[it].hasDigit(digit) }
                if (spots.size < 2) continue
                val line = sharedLine(spots) ?: continue
                val removals = geometry.cells(line)
                    .filter { geometry.box(it) != b && board.values[it] == 0 && board.candidates[it].hasDigit(digit) }
                    .map { Candidate(it, digit) }
                if (removals.isNotEmpty()) {
                    return Elimination(
                        technique = Technique.Pointing,
                        digits = listOf(digit),
                        patternCells = spots,
                        units = listOf(UnitRef(UnitKind.Box, b), line),
                        removals = removals,
                    )
                }
            }
        }
        return null
    }

    /** A digit confined to one box inside a line can be removed from the rest of that box. */
    private fun claiming(board: Board): Elimination? {
        for ((ref, cells) in geometry.units) {
            if (ref.kind == UnitKind.Box) continue
            for (digit in 1..geometry.size) {
                val spots = cells.filter { board.values[it] == 0 && board.candidates[it].hasDigit(digit) }
                if (spots.size < 2) continue
                val box = geometry.box(spots[0])
                if (spots.any { geometry.box(it) != box }) continue
                val removals = geometry.boxes[box]
                    .filter { it !in spots && board.values[it] == 0 && board.candidates[it].hasDigit(digit) }
                    .filter { !cells.contains(it) }
                    .map { Candidate(it, digit) }
                if (removals.isNotEmpty()) {
                    return Elimination(
                        technique = Technique.Claiming,
                        digits = listOf(digit),
                        patternCells = spots,
                        units = listOf(ref, UnitRef(UnitKind.Box, box)),
                        removals = removals,
                    )
                }
            }
        }
        return null
    }

    private fun nakedPair(board: Board): Elimination? {
        for ((ref, cells) in geometry.units) {
            val pairs = cells.filter { board.values[it] == 0 && board.candidates[it].digitCount() == 2 }
            for (a in pairs.indices) {
                for (b in a + 1 until pairs.size) {
                    val mask = board.candidates[pairs[a]]
                    if (board.candidates[pairs[b]] != mask) continue
                    val removals = cells
                        .filter { it != pairs[a] && it != pairs[b] && board.values[it] == 0 }
                        .flatMap { cell ->
                            (board.candidates[cell] and mask).digits().map { Candidate(cell, it) }
                        }
                    if (removals.isNotEmpty()) {
                        return Elimination(
                            technique = Technique.NakedPair,
                            digits = mask.digits(),
                            patternCells = listOf(pairs[a], pairs[b]),
                            units = listOf(ref),
                            removals = removals,
                        )
                    }
                }
            }
        }
        return null
    }

    private fun hiddenPair(board: Board): Elimination? {
        for ((ref, cells) in geometry.units) {
            val spotsByDigit = (1..geometry.size).associateWith { digit ->
                cells.filter { board.values[it] == 0 && board.candidates[it].hasDigit(digit) }
            }
            for (d1 in 1..geometry.size) {
                val s1 = spotsByDigit.getValue(d1)
                if (s1.size != 2) continue
                for (d2 in d1 + 1..geometry.size) {
                    if (spotsByDigit.getValue(d2) != s1) continue
                    val keep = (1 shl d1) or (1 shl d2)
                    val removals = s1.flatMap { cell ->
                        (board.candidates[cell] and keep.inv()).digits().map { Candidate(cell, it) }
                    }
                    if (removals.isNotEmpty()) {
                        return Elimination(
                            technique = Technique.HiddenPair,
                            digits = listOf(d1, d2),
                            patternCells = s1,
                            units = listOf(ref),
                            removals = removals,
                        )
                    }
                }
            }
        }
        return null
    }

    private fun xWing(board: Board): Elimination? {
        for (digit in 1..geometry.size) {
            xWingOriented(board, digit, geometry.rows, UnitKind.Row, UnitKind.Column)?.let { return it }
            xWingOriented(board, digit, geometry.columns, UnitKind.Column, UnitKind.Row)?.let { return it }
        }
        return null
    }

    private fun xWingOriented(
        board: Board,
        digit: Int,
        bases: List<IntArray>,
        baseKind: UnitKind,
        coverKind: UnitKind,
    ): Elimination? {
        val positions = bases.map { cells ->
            cells.indices.filter { k ->
                val cell = cells[k]
                board.values[cell] == 0 && board.candidates[cell].hasDigit(digit)
            }
        }
        for (a in bases.indices) {
            if (positions[a].size != 2) continue
            for (b in a + 1 until bases.size) {
                if (positions[b] != positions[a]) continue
                val (k1, k2) = positions[a]
                val covers = listOf(k1, k2).map { k ->
                    if (coverKind == UnitKind.Column) geometry.columns[k] else geometry.rows[k]
                }
                val corners = listOf(bases[a][k1], bases[a][k2], bases[b][k1], bases[b][k2])
                val removals = covers.flatMap { cover ->
                    cover.filter { cell ->
                        cell !in corners && board.values[cell] == 0 && board.candidates[cell].hasDigit(digit)
                    }
                }.map { Candidate(it, digit) }
                if (removals.isNotEmpty()) {
                    return Elimination(
                        technique = Technique.XWing,
                        digits = listOf(digit),
                        patternCells = corners.sorted(),
                        units = listOf(
                            UnitRef(baseKind, a),
                            UnitRef(baseKind, b),
                            UnitRef(coverKind, k1),
                            UnitRef(coverKind, k2),
                        ),
                        removals = removals,
                    )
                }
            }
        }
        return null
    }

    private fun sharedLine(cells: List<Int>): UnitRef? {
        val row = geometry.row(cells[0])
        if (cells.all { geometry.row(it) == row }) return UnitRef(UnitKind.Row, row)
        val column = geometry.column(cells[0])
        if (cells.all { geometry.column(it) == column }) return UnitRef(UnitKind.Column, column)
        return null
    }
}
