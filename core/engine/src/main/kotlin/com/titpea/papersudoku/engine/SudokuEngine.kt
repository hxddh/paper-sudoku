package com.titpea.papersudoku.engine

/** What the Coach would say about the current position. */
sealed interface CoachHint {

    /** An entered digit disagrees with the solution; nothing else is trustworthy until it goes. */
    data class Mistake(val index: Int, val digit: Int) : CoachHint

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

    fun hint(values: IntArray): CoachHint? {
        require(values.size == geometry.cellCount) { "values must contain size * size cells" }
        mistakes(values).firstOrNull()?.let { return CoachHint.Mistake(it, values[it]) }
        val board = Board.from(geometry, values)
        if (board.isSolved) return null
        val groundwork = mutableListOf<Elimination>()
        repeat(MAX_GROUNDWORK) {
            solver.nextSingle(board)?.let { return CoachHint.Next(it, essential(groundwork, it)) }
            val elimination = solver.nextElimination(board) ?: return null
            groundwork += elimination
            board.eliminate(elimination.removals)
        }
        return null
    }

    /**
     * The eliminations [single] depends on. Working back from the single, an elimination is kept
     * when it removed a candidate that something kept relies on; what it relies on in turn (its
     * digits across its units, or every digit of a naked pair) is then needed as well. Whatever
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
            if (step.technique == Technique.NakedPair) {
                for (cell in step.patternCells) needed[cell] = all
            } else {
                val digits = step.digits.fold(0) { mask, d -> mask or (1 shl d) }
                for (u in step.units) for (cell in geometry.cells(u)) needed[cell] = needed[cell] or digits
            }
        }
        return kept.toList()
    }

    private companion object {
        const val MAX_GROUNDWORK = 24
    }
}
