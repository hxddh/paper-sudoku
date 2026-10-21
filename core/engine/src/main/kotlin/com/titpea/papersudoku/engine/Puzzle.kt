package com.titpea.papersudoku.engine

/**
 * Immutable puzzle data. `givens` contains 0 for an empty cell; `solution` is a full board.
 * The engine is intentionally Android-free so it can be tested on the JVM.
 */
data class Puzzle(
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val givens: List<Int>,
    val solution: List<Int>,
) {
    init {
        require(size in 4..9) { "size must be between 4 and 9" }
        require(boxRows * boxCols == size) { "boxRows * boxCols must equal size" }
        require(givens.size == size * size) { "givens must contain size * size cells" }
        require(solution.size == size * size) { "solution must contain size * size cells" }
        require(givens.all { it in 0..size }) { "givens must be 0..size" }
        require(solution.all { it in 1..size }) { "solution must be 1..size" }
        require(givens.indices.all { givens[it] == 0 || givens[it] == solution[it] }) {
            "every given must match the solution"
        }
    }
}
