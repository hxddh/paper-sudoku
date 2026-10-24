package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Technique
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the player has shown they know (10.0): per technique, the pages on which they wrote a digit
 * with it themselves, without being shown Why or the answer for it there. The evidence is the
 * page read back at the solve ([Review]): the digits the player wrote, each judged by what it
 * needed at that moment. A page only puts a technique in front of the player; writing with it is
 * what counts.
 *
 * Never shown as a number or a score. It decides which page practice deals next ([Path]), how
 * briefly the Coach speaks, and the handbook's ink.
 */
@Serializable
data class Learner(
    /** Pages on which the player found the technique themselves. */
    val found: Map<Technique, Int> = emptyMap(),
    /** Pages on which the Coach had to explain it (Why or the answer). */
    val helped: Map<Technique, Int> = emptyMap(),
    /** The page count ([pages]) when the technique was last found, for revisiting what fades. */
    val lastFound: Map<Technique, Int> = emptyMap(),
    /** Pages recorded. */
    val pages: Int = 0,
) {
    fun found(technique: Technique): Int = found[technique] ?: 0

    fun mastery(technique: Technique, met: Boolean): Mastery =
        Mastery.of(met || found(technique) > 0 || (helped[technique] ?: 0) > 0, found(technique))

    /**
     * Adds one solved page: [steps] is the page read back, [coach] the Coach's help on it. A
     * technique the Coach explained on this page (Why or more) is not counted as found there, even
     * if the player then wrote with it.
     */
    fun record(steps: List<ReviewStep>, coach: CoachUsage): Learner {
        val explained = coach.byTechnique.filterValues { it >= EXPLAINED }.keys
        val own = steps.mapNotNullTo(mutableSetOf()) { step ->
            step.technique?.takeIf { step.kind == ReviewStep.Kind.Placed && !step.move.coach }
        } - explained
        val page = pages + 1
        return copy(
            found = found + own.associateWith { found(it) + 1 },
            helped = helped + explained.associateWith { (helped[it] ?: 0) + 1 },
            lastFound = lastFound + own.associateWith { page },
            pages = page,
        )
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        /** Coach level 2 (Why) and above explain a technique. */
        const val EXPLAINED = 2

        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String?): Learner? =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }

        /**
         * From 9.0's clean pages per technique, which counted every technique a page needed: kept
         * as at most one page found, so nothing known is lost and nothing is claimed twice.
         */
        fun from9(cleanPages: Map<Technique, Int>): Learner =
            Learner(found = cleanPages.filterValues { it > 0 }.mapValues { 1 })
    }
}

/**
 * Practice is one path (10.0): the level still follows clean solves ([Practice]), and within it
 * each page is dealt to need what the player is about to learn, the first technique of the level
 * not yet known. Every [REVISIT_EVERY]th page goes back to the technique found longest ago, if
 * that was [FADING_AFTER] pages or more back. With nothing to learn or revisit, a plain page.
 */
object Path {
    const val REVISIT_EVERY = 4
    const val FADING_AFTER = 8

    fun target(learner: Learner, level: com.titpea.papersudoku.engine.Difficulty, number: Int): Technique? {
        // Singles are on every page; the path is about the techniques beyond them.
        val open = Technique.entries.filter { it.tier in 2..level.maxTier }
        if (number % REVISIT_EVERY == REVISIT_EVERY - 1) {
            open.filter { learner.found(it) > 0 }
                .minByOrNull { learner.lastFound[it] ?: 0 }
                ?.takeIf { learner.pages - (learner.lastFound[it] ?: 0) >= FADING_AFTER }
                ?.let { return it }
        }
        return next(learner, level)
    }

    /** The next technique to learn at [level], for "Next: X-Wing"; null once all are known. */
    fun next(learner: Learner, level: com.titpea.papersudoku.engine.Difficulty): Technique? =
        Technique.entries.firstOrNull { it.tier in 2..level.maxTier && learner.found(it) < Mastery.MASTERED }
}
