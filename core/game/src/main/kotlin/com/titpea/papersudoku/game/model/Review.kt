package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Board
import com.titpea.papersudoku.engine.LogicalSolver
import com.titpea.papersudoku.engine.Technique

/** One move of a page read back: the board after it, and what justified it at that moment. */
data class ReviewStep(
    val move: Move,
    /** The board right after this move. */
    val values: List<Int>,
    val kind: Kind,
    /**
     * For a correct digit, the hardest technique the simplest logical route to it needed at that
     * moment; null when the Coach's techniques could not reach it from there (a guess, or a step
     * beyond them).
     */
    val technique: Technique? = null,
) {
    enum class Kind { Placed, Mistake, Erased }
}

/**
 * Reads a page back from its move log. Every correct digit is judged against the board as it was
 * when it was written: a single there and then, or a single after the eliminations the Coach would
 * have made. A digit that disagrees with the solution is a mistake, however it was arrived at.
 */
object Review {
    private const val MAX_ELIMINATIONS = 24

    fun of(spec: PuzzleSpec, moves: List<Move>): List<ReviewStep> {
        val geometry = spec.geometry
        val solver = LogicalSolver(geometry)
        val solution = spec.solution.toIntArray()
        val values = spec.givens.toIntArray()
        return moves.map { move ->
            val kind = when {
                move.digit == 0 -> ReviewStep.Kind.Erased
                move.digit != solution[move.cell] -> ReviewStep.Kind.Mistake
                else -> ReviewStep.Kind.Placed
            }
            // Wrong digits elsewhere on the board say nothing about this one: judge it without them.
            val technique = if (kind == ReviewStep.Kind.Placed) {
                justify(IntArray(values.size) { i -> if (values[i] == solution[i]) values[i] else 0 }, move, solver)
            } else {
                null
            }
            values[move.cell] = move.digit
            ReviewStep(move, Digits(values), kind, technique)
        }
    }

    /**
     * A board after one move, one byte per cell: a long page read back keeps a board per move,
     * and a list of boxed digits is about three times the size.
     */
    private class Digits(values: IntArray) : AbstractList<Int>(), RandomAccess {
        private val cells = ByteArray(values.size) { values[it].toByte() }
        override val size: Int get() = cells.size
        override fun get(index: Int): Int = cells[index].toInt()
    }

    private fun justify(before: IntArray, move: Move, solver: LogicalSolver): Technique? {
        val board = Board.from(solver.geometry, before)
        if (board.isBroken) return null
        var hardest: Technique? = null
        repeat(MAX_ELIMINATIONS + 1) {
            single(board, move)?.let { return listOfNotNull(hardest, it).maxBy { t -> t.tier } }
            val step = solver.nextElimination(board) ?: return null
            if (hardest == null || step.technique.tier > hardest.tier) hardest = step.technique
            board.eliminate(step.removals)
        }
        return null
    }

    /** The move's digit is single in its cell, or in one of the cell's units. */
    private fun single(board: Board, move: Move): Technique? {
        val geometry = board.geometry
        val bit = 1 shl move.digit
        if (board.candidates[move.cell] and bit == 0) return null
        if (board.candidates[move.cell] == bit) return Technique.NakedSingle
        for ((_, cells) in geometry.units) {
            if (move.cell !in cells) continue
            if (cells.none { it != move.cell && board.values[it] == 0 && board.candidates[it] and bit != 0 }) {
                return Technique.HiddenSingle
            }
        }
        return null
    }
}
