package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Geometry
import com.titpea.papersudoku.engine.Puzzle
import com.titpea.papersudoku.engine.PuzzleGenerator
import com.titpea.papersudoku.engine.SplitMix64
import com.titpea.papersudoku.engine.Technique
import kotlinx.serialization.Serializable

enum class PuzzleKind { Daily, Classic }

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
