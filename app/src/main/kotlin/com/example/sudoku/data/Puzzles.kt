package com.example.sudoku.data

import com.example.sudoku.engine.Puzzle

/**
 * Bundled starter puzzle bank. Production should generate/validate a larger bank at build time
 * (one unique solution, no guessing, graded by technique) instead of generating on the device.
 */
object Puzzles {

    val mini6 = Puzzle(
        size = 6,
        boxRows = 2,
        boxCols = 3,
        givens = listOf(
            0, 2, 0, 4, 5, 0,
            4, 0, 0, 1, 0, 3,
            0, 0, 0, 0, 6, 4,
            5, 0, 4, 0, 0, 0,
            0, 1, 2, 0, 0, 0,
            0, 0, 5, 3, 0, 0,
        ),
        solution = listOf(
            1, 2, 3, 4, 5, 6,
            4, 5, 6, 1, 2, 3,
            2, 3, 1, 5, 6, 4,
            5, 6, 4, 2, 3, 1,
            3, 1, 2, 6, 4, 5,
            6, 4, 5, 3, 1, 2,
        ),
    )

    val classic9 = Puzzle(
        size = 9,
        boxRows = 3,
        boxCols = 3,
        givens = listOf(
            5, 3, 0, 0, 7, 0, 0, 0, 0,
            6, 0, 0, 1, 9, 5, 0, 0, 0,
            0, 9, 8, 0, 0, 0, 0, 6, 0,
            8, 0, 0, 0, 6, 0, 0, 0, 3,
            4, 0, 0, 8, 0, 3, 0, 0, 1,
            7, 0, 0, 0, 2, 0, 0, 0, 6,
            0, 6, 0, 0, 0, 0, 2, 8, 0,
            0, 0, 0, 4, 1, 9, 0, 0, 5,
            0, 0, 0, 0, 8, 0, 0, 7, 9,
        ),
        solution = listOf(
            5, 3, 4, 6, 7, 8, 9, 1, 2,
            6, 7, 2, 1, 9, 5, 3, 4, 8,
            1, 9, 8, 3, 4, 2, 5, 6, 7,
            8, 5, 9, 7, 6, 1, 4, 2, 3,
            4, 2, 6, 8, 5, 3, 7, 9, 1,
            7, 1, 3, 9, 2, 4, 8, 5, 6,
            9, 6, 1, 5, 3, 7, 2, 8, 4,
            2, 8, 7, 4, 1, 9, 6, 3, 5,
            3, 4, 5, 2, 8, 6, 1, 7, 9,
        ),
    )
}
