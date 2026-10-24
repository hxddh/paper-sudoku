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
class LogicalSolver(val geometry: Geometry) {

    /**
     * The simplest step, using techniques up to [maxTier] only: grading against a level never
     * searches for the harder patterns it would reject anyway (the step found is the same).
     */
    fun nextStep(board: Board, maxTier: Int = Int.MAX_VALUE): Step? =
        hiddenSingle(board, UnitKind.Box)
            ?: nakedSingle(board)
            ?: hiddenSingle(board, null)
            ?: (if (maxTier < 2) null else pointing(board) ?: claiming(board) ?: nakedPair(board))
            ?: (if (maxTier < 3) null else hiddenPair(board) ?: xWing(board))
            ?: (if (maxTier < 4) null else nakedTriple(board) ?: hiddenTriple(board) ?: swordfish(board) ?: xyWing(board))

    /** Next placement only (no eliminations). */
    fun nextSingle(board: Board): Placement? =
        hiddenSingle(board, UnitKind.Box) ?: nakedSingle(board) ?: hiddenSingle(board, null)

    fun nextElimination(board: Board): Elimination? =
        pointing(board) ?: claiming(board) ?: nakedPair(board) ?: hiddenPair(board) ?: xWing(board)
            ?: nakedTriple(board) ?: hiddenTriple(board) ?: swordfish(board) ?: xyWing(board)

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

    /** Three cells of a unit whose candidates are three digits between them take those digits. */
    private fun nakedTriple(board: Board): Elimination? {
        for ((ref, cells) in geometry.units) {
            val open = cells.filter { board.values[it] == 0 && board.candidates[it].digitCount() in 2..3 }
            for (a in open.indices) for (b in a + 1 until open.size) for (c in b + 1 until open.size) {
                val trio = listOf(open[a], open[b], open[c])
                val mask = trio.fold(0) { m, cell -> m or board.candidates[cell] }
                if (mask.digitCount() != 3) continue
                val removals = cells
                    .filter { it !in trio && board.values[it] == 0 }
                    .flatMap { cell -> (board.candidates[cell] and mask).digits().map { Candidate(cell, it) } }
                if (removals.isNotEmpty()) {
                    return Elimination(Technique.NakedTriple, mask.digits(), trio, listOf(ref), removals)
                }
            }
        }
        return null
    }

    /** Three digits that fit only the same three cells of a unit: those cells hold nothing else. */
    private fun hiddenTriple(board: Board): Elimination? {
        for ((ref, cells) in geometry.units) {
            val spots = (1..geometry.size).associateWith { digit ->
                cells.filter { board.values[it] == 0 && board.candidates[it].hasDigit(digit) }
            }.filterValues { it.size in 2..3 }
            val digits = spots.keys.toList()
            for (a in digits.indices) for (b in a + 1 until digits.size) for (c in b + 1 until digits.size) {
                val trio = listOf(digits[a], digits[b], digits[c])
                val home = trio.flatMap { spots.getValue(it) }.distinct()
                if (home.size != 3) continue
                val keep = trio.fold(0) { m, d -> m or (1 shl d) }
                val removals = home.flatMap { cell ->
                    (board.candidates[cell] and keep.inv()).digits().map { Candidate(cell, it) }
                }
                if (removals.isNotEmpty()) {
                    return Elimination(Technique.HiddenTriple, trio, home.sorted(), listOf(ref), removals)
                }
            }
        }
        return null
    }

    /** An X-Wing across three lines: a digit confined to the same three cross lines in each. */
    private fun swordfish(board: Board): Elimination? {
        for (digit in 1..geometry.size) {
            swordfishOriented(board, digit, geometry.rows, UnitKind.Row, UnitKind.Column)?.let { return it }
            swordfishOriented(board, digit, geometry.columns, UnitKind.Column, UnitKind.Row)?.let { return it }
        }
        return null
    }

    private fun swordfishOriented(
        board: Board,
        digit: Int,
        bases: List<IntArray>,
        baseKind: UnitKind,
        coverKind: UnitKind,
    ): Elimination? {
        val positions = bases.map { cells ->
            cells.indices.filter { k -> board.values[cells[k]] == 0 && board.candidates[cells[k]].hasDigit(digit) }
        }
        val lines = bases.indices.filter { positions[it].size in 2..3 }
        for (a in lines.indices) for (b in a + 1 until lines.size) for (c in b + 1 until lines.size) {
            val trio = listOf(lines[a], lines[b], lines[c])
            val crosses = trio.flatMap { positions[it] }.distinct().sorted()
            if (crosses.size != 3) continue
            val pattern = trio.flatMap { base -> positions[base].map { k -> bases[base][k] } }
            val covers = crosses.map { k -> if (coverKind == UnitKind.Column) geometry.columns[k] else geometry.rows[k] }
            val removals = covers.flatMap { cover ->
                cover.filter { it !in pattern && board.values[it] == 0 && board.candidates[it].hasDigit(digit) }
            }.map { Candidate(it, digit) }
            if (removals.isNotEmpty()) {
                return Elimination(
                    technique = Technique.Swordfish,
                    digits = listOf(digit),
                    patternCells = pattern.sorted(),
                    units = trio.map { UnitRef(baseKind, it) } + crosses.map { UnitRef(coverKind, it) },
                    removals = removals,
                )
            }
        }
        return null
    }

    /**
     * A pivot that can only be x or y, seeing one cell that can only be x or z and another that can
     * only be y or z: whichever the pivot is, one of those two cells is z. So no cell that sees
     * both of them can be z. Digits: z first, then x and y; units: pivot to each pincer.
     */
    private fun xyWing(board: Board): Elimination? {
        val pairs = board.values.indices.filter { board.values[it] == 0 && board.candidates[it].digitCount() == 2 }
        for (pivot in pairs) {
            val xy = board.candidates[pivot]
            val wings = pairs.filter { it != pivot && geometry.sees(it, pivot) }
            for (p in wings.indices) for (q in p + 1 until wings.size) {
                val a = wings[p]
                val b = wings[q]
                val ma = board.candidates[a]
                val mb = board.candidates[b]
                val z = ma and mb and xy.inv()
                // Each pincer shares exactly one digit with the pivot, a different one each, and z.
                if (z.digitCount() != 1) continue
                if ((ma and xy).digitCount() != 1 || (mb and xy).digitCount() != 1) continue
                if ((ma and xy) == (mb and xy) || ((ma or mb) and xy) != xy) continue
                val zd = z.firstDigit()
                val removals = board.values.indices.filter { cell ->
                    cell != pivot && cell != a && cell != b && board.values[cell] == 0 &&
                        board.candidates[cell].hasDigit(zd) && geometry.sees(cell, a) && geometry.sees(cell, b)
                }.map { Candidate(it, zd) }
                if (removals.isNotEmpty()) {
                    return Elimination(
                        technique = Technique.XYWing,
                        digits = listOf(zd) + xy.digits(),
                        patternCells = listOf(pivot, a, b),
                        units = listOf(sharedUnit(pivot, a), sharedUnit(pivot, b)),
                        removals = removals,
                    )
                }
            }
        }
        return null
    }

    /** The unit two cells that see each other share: their box first, else their line. */
    private fun sharedUnit(a: Int, b: Int): UnitRef = when {
        geometry.box(a) == geometry.box(b) -> UnitRef(UnitKind.Box, geometry.box(a))
        geometry.row(a) == geometry.row(b) -> UnitRef(UnitKind.Row, geometry.row(a))
        else -> UnitRef(UnitKind.Column, geometry.column(a))
    }

    private fun sharedLine(cells: List<Int>): UnitRef? {
        val row = geometry.row(cells[0])
        if (cells.all { geometry.row(it) == row }) return UnitRef(UnitKind.Row, row)
        val column = geometry.column(cells[0])
        if (cells.all { geometry.column(it) == column }) return UnitRef(UnitKind.Column, column)
        return null
    }
}
