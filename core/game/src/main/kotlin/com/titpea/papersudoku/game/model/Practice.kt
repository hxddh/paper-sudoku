package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty

/**
 * Practice is one never-ending stack of 9×9 pages whose difficulty follows the player: a level is
 * passed after [CLEAN_SOLVES_TO_ADVANCE] solves without the Coach's answer. There is no picker;
 * learning the next technique is the progression.
 */
object Practice {
    const val CLEAN_SOLVES_TO_ADVANCE = 3

    fun level(cleanSolves: Map<Difficulty, Int>): Difficulty {
        for (difficulty in Difficulty.entries) {
            if (difficulty == Difficulty.entries.last()) return difficulty
            if ((cleanSolves[difficulty] ?: 0) < CLEAN_SOLVES_TO_ADVANCE) return difficulty
        }
        return Difficulty.entries.last()
    }

    /** Clean solves still needed to reach the next level, or null at the top. */
    fun remainingToAdvance(cleanSolves: Map<Difficulty, Int>): Int? {
        val current = level(cleanSolves)
        if (current == Difficulty.entries.last()) return null
        return (CLEAN_SOLVES_TO_ADVANCE - (cleanSolves[current] ?: 0)).coerceAtLeast(1)
    }
}
