package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.BacktrackingSolver
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Geometry
import com.titpea.papersudoku.engine.Grader
import com.titpea.papersudoku.engine.Puzzle
import com.titpea.papersudoku.engine.PuzzleGenerator
import com.titpea.papersudoku.engine.SplitMix64
import com.titpea.papersudoku.engine.Technique
import kotlinx.serialization.Serializable

/**
 * Where a page comes from. Stored by name: Daily (the 6×6 page of the day), Classic (practice),
 * Weekly (9.0: the Sunday 9×9 page, open all week), Imported (9.0: a page typed in by the player),
 * Focus (9.0: a page for practising one technique, outside the practice levels).
 */
enum class PuzzleKind { Daily, Classic, Weekly, Imported, Focus }

/**
 * A puzzle plus where it came from. Givens and solution are stored, never re-derived, so a saved
 * game keeps working even if a future generator produces different puzzles for the same seed.
 */
@Serializable
data class PuzzleSpec(
    val key: String,
    val kind: PuzzleKind,
    val difficulty: Difficulty,
    val hardest: Technique,
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val givens: List<Int>,
    val solution: List<Int>,
    /** Local date of a Daily, as epoch day. */
    val epochDay: Long? = null,
    /** Sequence number of a Classic within its difficulty. */
    val number: Int? = null,
    /**
     * Every technique a logical solve of this page uses (the hardest is [hardest]). Empty in pages
     * saved before 7.0; read [techniquesUsed] instead.
     */
    val techniques: Set<Technique> = emptySet(),
    /** An imported page logic cannot finish with the Coach's techniques (9.0). */
    val beyondCoach: Boolean = false,
) {
    /** The techniques this page needs, falling back to the hardest for older saves. */
    val techniquesUsed: Set<Technique> get() = techniques.ifEmpty { setOf(hardest) }

    val puzzle: Puzzle get() = Puzzle(size, boxRows, boxCols, givens, solution)
    val geometry: Geometry get() = Geometry.of(size, boxRows, boxCols)
    val cellCount: Int get() = size * size
}

/**
 * Deterministic puzzle source. The same date gives everybody the same Daily, offline.
 */
object Catalog {
    /** Bump when the generator changes; part of every seed. */
    const val GENERATOR_VERSION: Long = 3

    /**
     * Bump when what the catalog deals for a seed changes without the seeds changing (7.0: a daily
     * that misses its level is dealt again; specs carry their techniques). Part of the cache key.
     */
    const val REVISION: Int = 1

    /** A weekend daily that comes out easier is dealt again from a derived seed, this many times. */
    private const val DAILY_RETRIES = 8

    /** Weekdays are gentle; the weekend Mini needs one elimination idea. */
    fun dailyDifficulty(epochDay: Long): Difficulty {
        val dayOfWeek = Math.floorMod(epochDay + 3, 7L) // 0 = Monday; 1970-01-01 was a Thursday.
        return if (dayOfWeek >= 5) Difficulty.Steady else Difficulty.Gentle
    }

    fun dailyKey(epochDay: Long): String = "daily-$epochDay"

    /** The day of a daily key, or null for any other key. */
    fun dayOf(key: String): Long? = key.takeIf { it.startsWith("daily-") }?.removePrefix("daily-")?.toLongOrNull()

    fun classicKey(difficulty: Difficulty, number: Int): String = classicPrefix(difficulty) + number

    /** Every practice page dealt at [difficulty] has a key starting with this. */
    fun classicPrefix(difficulty: Difficulty): String = "classic-${difficulty.name.lowercase()}-"

    /** The week's 9×9 page is dealt on Sunday and stays open until the next one. */
    fun weekOf(epochDay: Long): Long = epochDay - Math.floorMod(epochDay + 4, 7L) // 1970-01-04 was a Sunday.

    fun weeklyKey(sunday: Long): String = "weekly-$sunday"

    fun dayOfWeekly(key: String): Long? = key.takeIf { it.startsWith("weekly-") }?.removePrefix("weekly-")?.toLongOrNull()

    fun weekly(sunday: Long): PuzzleSpec {
        val generated = PuzzleGenerator(Geometry.Classic).generate(seed(WEEKLY_STREAM, sunday), Difficulty.Steady)
        return spec(weeklyKey(sunday), PuzzleKind.Weekly, generated, epochDay = sunday)
    }

    /** Every practice page dealt to practise [technique] has a key starting with this. */
    fun focusPrefix(technique: Technique): String = "focus-${technique.name.lowercase()}-"

    /** The technique a focus page's key practises, or null for any other key. */
    fun focusTechnique(key: String): Technique? = Technique.entries.firstOrNull { key.startsWith(focusPrefix(it)) }

    /** The level whose pages are graded up to [technique]: where it is taught. */
    fun focusLevel(technique: Technique): Difficulty = Difficulty.forTier(technique.tier)

    /**
     * Practice for one technique: the next page at its level that needs it. Pages are drawn from
     * their own stream and checked; a technique that is rare at its level (Swordfish needs about
     * one Deep page in sixteen) takes longer to deal. After [FOCUS_TRIES] pages without it, the
     * last one is dealt anyway: still at the technique's level, so still worth doing.
     */
    fun focus(technique: Technique, number: Int): PuzzleSpec {
        val level = focusLevel(technique)
        val generator = PuzzleGenerator(Geometry.Classic)
        var generated = generator.generate(seed(FOCUS_STREAM + technique.ordinal, number.toLong() * FOCUS_TRIES), level)
        var attempt = 1
        while (technique !in generated.grade.techniques && attempt < FOCUS_TRIES) {
            generated = generator.generate(seed(FOCUS_STREAM + technique.ordinal, number.toLong() * FOCUS_TRIES + attempt), level)
            attempt++
        }
        return spec(focusPrefix(technique) + number, PuzzleKind.Focus, generated, number = number)
    }

    /**
     * A page typed in by the player: a 9×9 grid with one solution. Graded with the Coach's
     * techniques when they can finish it; otherwise kept, marked as beyond the Coach.
     */
    fun imported(givens: List<Int>): Import {
        val geometry = Geometry.Classic
        if (givens.size != geometry.cellCount || givens.any { it !in 0..9 }) return Import.Invalid(Import.Reason.Shape)
        val clashes = givens.indices.filter { i -> givens[i] != 0 && geometry.peers[i].any { givens[it] == givens[i] } }
        if (clashes.isNotEmpty()) return Import.Invalid(Import.Reason.Clash, clashes)
        if (givens.count { it != 0 } < MIN_IMPORT_CLUES) return Import.Invalid(Import.Reason.TooFew)
        val solver = BacktrackingSolver(geometry)
        val grid = givens.toIntArray()
        when (solver.countSolutions(grid, limit = 2)) {
            0 -> return Import.Invalid(Import.Reason.NoSolution)
            1 -> Unit
            else -> return Import.Invalid(Import.Reason.ManySolutions)
        }
        val solution = checkNotNull(solver.solve(grid)).toList()
        val grade = Grader(geometry).grade(grid, Difficulty.Deep.maxTier)
        val key = "imported-" + java.lang.Long.toHexString(SplitMix64.mix(givens.fold(17L) { h, v -> h * 31 + v }))
        return Import.Ok(
            PuzzleSpec(
                key = key,
                kind = PuzzleKind.Imported,
                difficulty = grade?.difficulty ?: Difficulty.Deep,
                hardest = grade?.hardest ?: Technique.entries.last(),
                size = geometry.size,
                boxRows = geometry.boxRows,
                boxCols = geometry.boxCols,
                givens = givens,
                solution = solution,
                techniques = grade?.techniques.orEmpty(),
                beyondCoach = grade == null,
            ),
        )
    }

    fun daily(epochDay: Long): PuzzleSpec {
        val difficulty = dailyDifficulty(epochDay)
        val generator = PuzzleGenerator(Geometry.Mini)
        val base = seed(1, epochDay)
        // The generator settles for its best try when it never reaches the level; a weekend page
        // must need its elimination idea, so the day is dealt again (the first try is unchanged,
        // so every day that already matched keeps its puzzle).
        var generated = generator.generate(base, difficulty)
        var attempt = 1L
        while (generated.grade.difficulty != difficulty && attempt <= DAILY_RETRIES) {
            generated = generator.generate(SplitMix64.mix(base xor attempt), difficulty)
            attempt++
        }
        return spec(dailyKey(epochDay), PuzzleKind.Daily, generated, epochDay = epochDay)
    }

    fun classic(difficulty: Difficulty, number: Int): PuzzleSpec {
        val generated = PuzzleGenerator(Geometry.Classic)
            .generate(seed(2 + difficulty.ordinal.toLong(), number.toLong()), difficulty)
        return spec(classicKey(difficulty, number), PuzzleKind.Classic, generated, number = number)
    }

    /**
     * Hashed, not stepped: SplitMix64 itself steps its state by a fixed constant, so seeds that
     * differ by a multiple of it (as `n * constant` did up to 2) replay each other's draws, and a
     * page whose generator retried could deal the same puzzle as a later page.
     */
    private const val WEEKLY_STREAM = 10L
    private const val FOCUS_STREAM = 20L
    private const val FOCUS_TRIES = 40
    /** Fewer than 17 clues never has a single solution. */
    private const val MIN_IMPORT_CLUES = 17

    private fun seed(stream: Long, n: Long): Long =
        SplitMix64.mix(SplitMix64.mix((GENERATOR_VERSION shl 32) xor stream) xor n)

    private fun spec(
        key: String,
        kind: PuzzleKind,
        generated: com.titpea.papersudoku.engine.GeneratedPuzzle,
        epochDay: Long? = null,
        number: Int? = null,
    ): PuzzleSpec {
        val p = generated.puzzle
        return PuzzleSpec(
            key = key,
            kind = kind,
            difficulty = generated.grade.difficulty,
            hardest = generated.grade.hardest,
            size = p.size,
            boxRows = p.boxRows,
            boxCols = p.boxCols,
            givens = p.givens,
            solution = p.solution,
            epochDay = epochDay,
            number = number,
            techniques = generated.grade.techniques,
        )
    }
}

/** What became of a typed-in page. */
sealed interface Import {
    data class Ok(val spec: PuzzleSpec) : Import

    data class Invalid(val reason: Reason, val cells: List<Int> = emptyList()) : Import

    enum class Reason { Shape, Clash, TooFew, NoSolution, ManySolutions }
}
