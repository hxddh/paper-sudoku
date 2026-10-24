package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Technique

/**
 * How well a technique is known, from clean pages that needed it: met (the Coach explained it or
 * a page needed it), practised (one clean page needed it), mastered ([MASTERED] clean pages). Ink
 * in the handbook, not a score: nothing is lost by a page done with the answer.
 */
enum class Mastery {
    Unmet, Met, Practised, Mastered;

    companion object {
        const val MASTERED = 3

        fun of(met: Boolean, cleanPages: Int): Mastery = when {
            cleanPages >= MASTERED -> Mastered
            cleanPages >= 1 -> Practised
            met -> Met
            else -> Unmet
        }

        /** The first technique in learning order not yet mastered, to suggest next. */
        fun nextToLearn(clean: Map<Technique, Int>): Technique? =
            Technique.entries.firstOrNull { (clean[it] ?: 0) < MASTERED }
    }
}
