package com.titpea.papersudoku.engine

/**
 * Exhaustive backtracking with bitmasks and "fewest candidates first". Used to prove uniqueness
 * and to build full grids; never shown to the player.
 */
class BacktrackingSolver(private val geometry: Geometry) {

    /** Counts solutions, stopping once [limit] is reached. */
    fun countSolutions(values: IntArray, limit: Int = 2): Int {
        val state = State.from(geometry, values) ?: return 0
        return state.count(limit)
    }

    /** A random complete grid. Deterministic for a given [random] state. */
    fun randomGrid(random: SplitMix64): IntArray {
        val state = checkNotNull(State.from(geometry, IntArray(geometry.cellCount)))
        check(state.fill(random)) { "an empty grid always has a solution" }
        return state.values
    }

    private class State(
        val geometry: Geometry,
        val values: IntArray,
        val rowMask: IntArray,
        val colMask: IntArray,
        val boxMask: IntArray,
    ) {
        private fun free(cell: Int): Int =
            geometry.allDigits and
                (rowMask[geometry.row(cell)] or colMask[geometry.column(cell)] or boxMask[geometry.box(cell)]).inv()

        private fun set(cell: Int, digit: Int) {
            val bit = 1 shl digit
            values[cell] = digit
            rowMask[geometry.row(cell)] = rowMask[geometry.row(cell)] or bit
            colMask[geometry.column(cell)] = colMask[geometry.column(cell)] or bit
            boxMask[geometry.box(cell)] = boxMask[geometry.box(cell)] or bit
        }

        private fun clear(cell: Int, digit: Int) {
            val bit = (1 shl digit).inv()
            values[cell] = 0
            rowMask[geometry.row(cell)] = rowMask[geometry.row(cell)] and bit
            colMask[geometry.column(cell)] = colMask[geometry.column(cell)] and bit
            boxMask[geometry.box(cell)] = boxMask[geometry.box(cell)] and bit
        }

        /** Empty cell with the fewest options, or -1 when full, or -2 when some cell has none. */
        private fun pick(): Int {
            var best = -1
            var bestCount = Int.MAX_VALUE
            for (cell in values.indices) {
                if (values[cell] != 0) continue
                val n = free(cell).digitCount()
                if (n == 0) return -2
                if (n < bestCount) {
                    best = cell
                    bestCount = n
                    if (n == 1) break
                }
            }
            return best
        }

        fun count(limit: Int): Int {
            val cell = pick()
            if (cell == -1) return 1
            if (cell == -2) return 0
            var total = 0
            var options = free(cell)
            while (options != 0) {
                val digit = options.firstDigit()
                options = options and (options - 1)
                set(cell, digit)
                total += count(limit - total)
                clear(cell, digit)
                if (total >= limit) break
            }
            return total
        }

        fun fill(random: SplitMix64): Boolean {
            val cell = pick()
            if (cell == -1) return true
            if (cell == -2) return false
            val options = free(cell).digits().toMutableList()
            random.shuffle(options)
            for (digit in options) {
                set(cell, digit)
                if (fill(random)) return true
                clear(cell, digit)
            }
            return false
        }

        companion object {
            fun from(geometry: Geometry, values: IntArray): State? {
                val state = State(
                    geometry,
                    IntArray(geometry.cellCount),
                    IntArray(geometry.size),
                    IntArray(geometry.size),
                    IntArray(geometry.size),
                )
                for (cell in values.indices) {
                    val digit = values[cell]
                    if (digit == 0) continue
                    if (state.free(cell) and (1 shl digit) == 0) return null
                    state.set(cell, digit)
                }
                return state
            }
        }
    }
}
