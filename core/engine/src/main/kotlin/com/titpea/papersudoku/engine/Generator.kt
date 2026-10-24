package com.titpea.papersudoku.engine

/**
 * SplitMix64: tiny, fast, and fully specified here, so a seed produces the same puzzle on every
 * device and every Kotlin version. (The standard library does not promise a stable algorithm.)
 */
class SplitMix64(seed: Long) {
    private var state = seed

    /** Values drawn so far. The state is the seed plus this many steps of [GAMMA]. */
    var draws: Long = 0
        private set

    fun nextLong(): Long {
        state += GAMMA
        draws++
        return mix(state)
    }

    /** Uniform-enough integer in `0 until bound` for small bounds. */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
    }

    companion object {
        private const val GAMMA = -0x61c8864680b583ebL

        /**
         * The stream of [seed] with its first [draws] values already drawn. The state only ever
         * steps by [GAMMA], so this is exact: it draws what `SplitMix64(seed)` draws after them.
         */
        fun skipped(seed: Long, draws: Long): SplitMix64 = SplitMix64(seed + draws * GAMMA)

        /** SplitMix64's output function: a full-avalanche 64-bit hash, for deriving seeds. */
        fun mix(value: Long): Long {
            var z = value
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}

data class GeneratedPuzzle(
    val puzzle: Puzzle,
    val grade: Grade,
)

/**
 * What a search of a seed's stream dealt: [generated], whose carve began [draws] values into the
 * stream, found after [carves] carves. [PuzzleGenerator.carveAt] with the same seed and [draws]
 * carves it again at once.
 */
data class Carving(
    val generated: GeneratedPuzzle,
    val draws: Long,
    val carves: Int,
)

/**
 * Builds puzzles that the Coach can always explain.
 *
 * A random full grid is carved in rotationally symmetric pairs. A removal is kept only while the
 * puzzle still solves by logic using techniques up to the requested tier; logical solvability with
 * sound techniques implies a unique solution. The result is retried until the hardest technique
 * needed matches the requested difficulty.
 */
class PuzzleGenerator(private val geometry: Geometry) {
    private val solver = BacktrackingSolver(geometry)
    private val grader = Grader(geometry)

    fun generate(
        seed: Long,
        difficulty: Difficulty,
        maxAttempts: Int = 3000,
        /** What a page must be to be dealt: by default, exactly [difficulty]. */
        accept: (Grade) -> Boolean = { it.difficulty == difficulty },
    ): GeneratedPuzzle = search(seed, difficulty, maxAttempts, accept).generated

    /**
     * [generate], also saying where in the stream the page was carved: carves are drawn one after
     * another from one [SplitMix64], so the page is the carve that starts at its [Carving.draws].
     */
    fun search(
        seed: Long,
        difficulty: Difficulty,
        maxAttempts: Int = 3000,
        accept: (Grade) -> Boolean = { it.difficulty == difficulty },
    ): Carving {
        val random = SplitMix64(seed)
        var best: Carving? = null
        for (n in 1..maxAttempts) {
            val start = random.draws
            val candidate = carve(random, difficulty)
            if (accept(candidate.grade)) return Carving(candidate, start, n)
            if (best == null || candidate.grade.hardest.tier > best.generated.grade.hardest.tier) {
                best = Carving(candidate, start, n)
            }
        }
        return checkNotNull(best).copy(carves = maxAttempts)
    }

    /**
     * The single carve that starts [draws] values into [seed]'s stream: with the draws a [search]
     * reported, the same page in one carve instead of all the carves before it.
     */
    fun carveAt(seed: Long, draws: Long, difficulty: Difficulty): GeneratedPuzzle =
        carve(SplitMix64.skipped(seed, draws), difficulty)

    private fun carve(random: SplitMix64, difficulty: Difficulty): GeneratedPuzzle {
        val solution = solver.randomGrid(random)
        val givens = solution.copyOf()
        val last = geometry.cellCount - 1
        val pairs = (0..last / 2).map { it to last - it }.toMutableList()
        random.shuffle(pairs)
        for ((a, b) in pairs) {
            val va = givens[a]
            val vb = givens[b]
            givens[a] = 0
            givens[b] = 0
            if (grader.grade(givens, difficulty.maxTier) == null) {
                givens[a] = va
                givens[b] = vb
            }
        }
        val grade = checkNotNull(grader.grade(givens, difficulty.maxTier))
        val puzzle = Puzzle(
            size = geometry.size,
            boxRows = geometry.boxRows,
            boxCols = geometry.boxCols,
            givens = givens.toList(),
            solution = solution.toList(),
        )
        return GeneratedPuzzle(puzzle, grade)
    }
}
