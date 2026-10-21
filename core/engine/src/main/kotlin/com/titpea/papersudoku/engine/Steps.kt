package com.titpea.papersudoku.engine

/**
 * The techniques the Coach can teach, in the order a person usually learns them.
 * [tier] drives difficulty: a puzzle is as hard as the hardest technique it needs.
 */
enum class Technique(val tier: Int) {
    HiddenSingle(1),
    NakedSingle(1),
    Pointing(2),
    Claiming(2),
    NakedPair(2),
    HiddenPair(3),
    XWing(3),
}

enum class Difficulty(val maxTier: Int) {
    Gentle(1),
    Steady(2),
    Tricky(3);

    companion object {
        fun forTier(tier: Int): Difficulty = entries.first { tier <= it.maxTier }
    }
}

data class Candidate(val index: Int, val digit: Int)

/** One logical deduction. */
sealed interface Step {
    val technique: Technique
}

/**
 * Place [digit] at [index]. For a hidden single, [unit] is where the digit has only one home;
 * for a naked single it is null (the reason is the cell itself).
 */
data class Placement(
    val index: Int,
    val digit: Int,
    override val technique: Technique,
    val unit: UnitRef?,
) : Step

/**
 * Remove [removals] from the candidates. [patternCells] and [digits] are what the person should
 * look at (the pair, the pointing cells, the X-Wing corners); [units] are where the pattern lives.
 */
data class Elimination(
    override val technique: Technique,
    val digits: List<Int>,
    val patternCells: List<Int>,
    val units: List<UnitRef>,
    val removals: List<Candidate>,
) : Step
