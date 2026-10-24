package com.titpea.papersudoku.engine

/** What the Coach would say about the current position. */
sealed interface CoachHint {

    /** An entered digit disagrees with the solution; nothing else is trustworthy until it goes. */
    data class Mistake(val index: Int, val digit: Int) : CoachHint

    /**
     * The player's pencil marks in [index] leave out [digit], which the cell still needs (10.0).
     * Said before any step: a step worked out from candidates the player no longer has would not
     * match their page.
     */
    data class MissingMark(val index: Int, val digit: Int) : CoachHint

    /**
     * The next placement, plus the eliminations it needs first ([groundwork]), in order. Only
     * eliminations the placement depends on are kept, so each one is worth explaining.
     */
    data class Next(
        val placement: Placement,
        val groundwork: List<Elimination>,
    ) : CoachHint {
        val lesson: Step get() = groundwork.lastOrNull() ?: placement
        val technique: Technique
            get() = (groundwork.map { it.technique } + placement.technique).maxBy { it.tier }
    }
}

/**
 * Rules and Coach for one puzzle. Pure Kotlin, so everything here is tested on the JVM.
 */
class SudokuEngine(private val puzzle: Puzzle) {

    val geometry: Geometry = puzzle.geometry
    private val solver = LogicalSolver(geometry)
    private val solution = puzzle.solution.toIntArray()

    fun candidates(values: IntArray, index: Int): Set<Int> {
        require(index in values.indices) { "index out of bounds" }
        if (values[index] != 0) return emptySet()
        return Board.from(geometry, values).candidates[index].digits().toSet()
    }

    /** Indices that participate in a duplicate in a row, column, or box. */
    fun conflicts(values: IntArray): Set<Int> {
        require(values.size == geometry.cellCount) { "values must contain size * size cells" }
        val result = sortedSetOf<Int>()
        for ((_, cells) in geometry.units) {
            for (a in cells.indices) {
                val va = values[cells[a]]
                if (va == 0) continue
                for (b in a + 1 until cells.size) {
                    if (values[cells[b]] == va) {
                        result += cells[a]
                        result += cells[b]
                    }
                }
            }
        }
        return result
    }

    /** Entered cells whose digit disagrees with the solution. */
    fun mistakes(values: IntArray): Set<Int> =
        values.indices.filterTo(sortedSetOf()) { values[it] != 0 && values[it] != solution[it] }

    fun isSolved(values: IntArray): Boolean =
        values.size == solution.size && values.indices.all { values[it] == solution[it] }

    /**
     * What the Coach says now. [notes] are the player's pencil marks (bit d = digit d): a cell
     * whose marks leave out its own answer is pointed out first. [near] is the cell the player is
     * on: among equally simple steps, one in its row, column or box comes first.
     */
    fun hint(values: IntArray, notes: IntArray? = null, near: Int? = null): CoachHint? {
        require(values.size == geometry.cellCount) { "values must contain size * size cells" }
        mistakes(values).firstOrNull()?.let { return CoachHint.Mistake(it, values[it]) }
        if (notes != null) {
            require(notes.size == values.size) { "notes must contain size * size cells" }
            // Nearest the player first, then in reading order.
            val order = values.indices.sortedBy { if (near != null && (it == near || geometry.sees(it, near))) 0 else 1 }
            order.firstOrNull { values[it] == 0 && notes[it] != 0 && notes[it] and (1 shl solution[it]) == 0 }
                ?.let { return CoachHint.MissingMark(it, solution[it]) }
        }
        val board = Board.from(geometry, values)
        if (board.isSolved) return null
        val prefer: ((Int) -> Boolean)? = near?.let { n -> { cell -> cell == n || geometry.sees(cell, n) } }
        val groundwork = mutableListOf<Elimination>()
        repeat(MAX_GROUNDWORK) {
            solver.nextSingle(board, prefer.takeIf { groundwork.isEmpty() })?.let { return CoachHint.Next(it, replayed(values, essential(groundwork, it))) }
            val elimination = solver.nextElimination(board) ?: return null
            groundwork += elimination
            board.eliminate(elimination.removals)
        }
        return null
    }

    /**
     * The eliminations [single] depends on. Working back from the single, an elimination is kept
     * when it removed a candidate that something kept relies on; what it relies on in turn (its
     * digits across its units, or every candidate of the cells a naked pair, a naked triple or an
     * XY-Wing is made of) is then needed as well. Whatever
     * is dropped touched none of that, so the kept steps stay valid and still lead to the single.
     */
    private fun essential(groundwork: List<Elimination>, single: Placement): List<Elimination> {
        if (groundwork.size < 2) return groundwork
        val all = (1..geometry.size).fold(0) { mask, d -> mask or (1 shl d) }
        val needed = IntArray(geometry.cellCount)
        val unit = single.unit
        if (single.technique == Technique.HiddenSingle && unit != null) {
            for (cell in geometry.cells(unit)) needed[cell] = needed[cell] or (1 shl single.digit)
        } else {
            needed[single.index] = all
        }
        val kept = ArrayDeque<Elimination>()
        for (step in groundwork.asReversed()) {
            if (step.removals.none { needed[it.index] and (1 shl it.digit) != 0 }) continue
            kept.addFirst(step)
            if (step.technique in CELL_PATTERNS) {
                for (cell in step.patternCells) needed[cell] = all
            } else {
                val digits = step.digits.fold(0) { mask, d -> mask or (1 shl d) }
                for (u in step.units) for (cell in geometry.cells(u)) needed[cell] = needed[cell] or digits
            }
        }
        return kept.toList()
    }

    /**
     * The kept steps as the player sees them: replayed from [values], each crossing out everything
     * its own pattern rules out there. (Found with the dropped steps applied, a step's removals
     * could miss candidates those steps had already taken, so the note and the board disagreed.)
     */
    private fun replayed(values: IntArray, steps: List<Elimination>): List<Elimination> {
        if (steps.isEmpty()) return steps
        val board = Board.from(geometry, values)
        return steps.map { step ->
            val removals = removalsOf(step, board).ifEmpty { step.removals }
            board.eliminate(removals)
            step.copy(removals = removals)
        }
    }

    /** Every candidate on [board] that [step]'s pattern rules out, by the technique's own rule. */
    private fun removalsOf(step: Elimination, board: Board): List<Candidate> {
        fun has(cell: Int, digit: Int) = board.values[cell] == 0 && (board.candidates[cell] shr digit) and 1 == 1
        val units = step.units.map { geometry.cells(it).toList() }
        return when (step.technique) {
            // Units: the box, then the line the digit is confined to.
            Technique.Pointing -> {
                val d = step.digits[0]
                units[1].filter { it !in units[0] && has(it, d) }.map { Candidate(it, d) }
            }
            // Units: the line, then the box the digit is confined to.
            Technique.Claiming -> {
                val d = step.digits[0]
                units[1].filter { it !in units[0] && has(it, d) }.map { Candidate(it, d) }
            }
            Technique.NakedPair, Technique.NakedTriple -> units[0]
                .filter { it !in step.patternCells }
                .flatMap { cell -> step.digits.filter { has(cell, it) }.map { Candidate(cell, it) } }
            Technique.HiddenPair, Technique.HiddenTriple -> step.patternCells.flatMap { cell ->
                (1..geometry.size).filter { it !in step.digits && has(cell, it) }.map { Candidate(cell, it) }
            }
            // Units: the two base lines, then the two cover lines.
            Technique.XWing -> {
                val d = step.digits[0]
                (units[2] + units[3]).filter { it !in step.patternCells && has(it, d) }.map { Candidate(it, d) }
            }
            // Units: the three base lines, then the three cover lines.
            Technique.Swordfish -> {
                val d = step.digits[0]
                (units[3] + units[4] + units[5]).filter { it !in step.patternCells && has(it, d) }.map { Candidate(it, d) }
            }
            // Pattern: the pivot, then the two pincers; digits: z first.
            Technique.XYWing -> {
                val z = step.digits[0]
                val (_, a, b) = step.patternCells
                (0 until geometry.cellCount)
                    .filter { it !in step.patternCells && geometry.sees(it, a) && geometry.sees(it, b) && has(it, z) }
                    .map { Candidate(it, z) }
            }
            else -> step.removals
        }
    }

    private companion object {
        const val MAX_GROUNDWORK = 24

        /** Patterns that rest on their cells holding nothing else, not on a digit's places. */
        val CELL_PATTERNS = setOf(Technique.NakedPair, Technique.NakedTriple, Technique.XYWing)
    }
}
