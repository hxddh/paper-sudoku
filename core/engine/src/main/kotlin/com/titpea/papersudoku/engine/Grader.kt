package com.titpea.papersudoku.engine

/** How a puzzle solves by logic alone. */
data class Grade(
    val difficulty: Difficulty,
    val hardest: Technique,
    val techniques: Set<Technique>,
    val steps: Int,
)

class Grader(private val geometry: Geometry) {
    private val solver = LogicalSolver(geometry)

    /**
     * Solves [givens] with the Coach's techniques only. Returns null if logic gets stuck (the
     * puzzle would need guessing or a technique we do not teach) or if [maxTier] is exceeded.
     */
    fun grade(givens: IntArray, maxTier: Int = Difficulty.Tricky.maxTier): Grade? {
        val board = Board.from(geometry, givens)
        val used = linkedSetOf<Technique>()
        var hardest = Technique.HiddenSingle
        var steps = 0
        while (!board.isSolved) {
            val step = solver.nextStep(board) ?: return null
            if (step.technique.tier > maxTier) return null
            used += step.technique
            if (step.technique.tier > hardest.tier) hardest = step.technique
            board.apply(step)
            if (board.isBroken) return null
            steps++
        }
        return Grade(Difficulty.forTier(hardest.tier), hardest, used, steps)
    }
}
