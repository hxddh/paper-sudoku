package com.titpea.papersudoku.game.model

import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Geometry
import com.titpea.papersudoku.engine.Puzzle
import com.titpea.papersudoku.engine.PuzzleGenerator
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
) {
    val puzzle: Puzzle get() = Puzzle(size, boxRows, boxCols, givens, solution)
    val geometry: Geometry get() = Geometry.of(size, boxRows, boxCols)
    val cellCount: Int get() = size * size
}

/**
 * Deterministic puzzle source. The same date gives everybody the same Daily, offline.
 */
object Catalog {
    /** Bump when the generator changes; part of every seed. */
    const val GENERATOR_VERSION: Long = 2

    /** Weekdays are gentle; the weekend Mini needs one elimination idea. */
    fun dailyDifficulty(epochDay: Long): Difficulty {
        val dayOfWeek = Math.floorMod(epochDay + 3, 7L) // 0 = Monday; 1970-01-01 was a Thursday.
        return if (dayOfWeek >= 5) Difficulty.Steady else Difficulty.Gentle
    }

    fun dailyKey(epochDay: Long): String = "daily-$epochDay"

    fun classicKey(difficulty: Difficulty, number: Int): String =
        "classic-${difficulty.name.lowercase()}-$number"

    fun daily(epochDay: Long): PuzzleSpec {
        val difficulty = dailyDifficulty(epochDay)
        val generated = PuzzleGenerator(Geometry.Mini).generate(seed(1, epochDay), difficulty)
        return spec(dailyKey(epochDay), PuzzleKind.Daily, generated, epochDay = epochDay)
    }

    fun classic(difficulty: Difficulty, number: Int): PuzzleSpec {
        val generated = PuzzleGenerator(Geometry.Classic)
            .generate(seed(2 + difficulty.ordinal.toLong(), number.toLong()), difficulty)
        return spec(classicKey(difficulty, number), PuzzleKind.Classic, generated, number = number)
    }

    private fun seed(stream: Long, n: Long): Long =
        (GENERATOR_VERSION shl 56) xor (stream shl 48) xor (n * -0x61c8864680b583ebL)

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
        )
    }
}
