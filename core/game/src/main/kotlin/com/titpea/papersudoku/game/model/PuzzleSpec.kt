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

    /**
     * The week's 9×9 page (9.0) was dealt on Sunday and stayed open until the next one. No longer
     * dealt; its key still names saved pages, which open and are shown as before.
     */
    fun weekOf(epochDay: Long): Long = epochDay - Math.floorMod(epochDay + 4, 7L) // 1970-01-04 was a Sunday.

    fun weeklyKey(sunday: Long): String = "weekly-$sunday"

    /** Every page dealt to practise [technique] (9.0) has a key starting with this. */
    fun focusPrefix(technique: Technique): String = "focus-${technique.name.lowercase()}-"

    /** The technique a focus page's key practises, or null for any other key. */
    fun focusTechnique(key: String): Technique? = Technique.entries.firstOrNull { key.startsWith(focusPrefix(it)) }

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

    fun daily(epochDay: Long): PuzzleSpec = dealDaily(epochDay).spec

    /**
     * The daily, from [CatalogTable] when the day is in it (one carve), else by searching. A
     * weekend page must need its elimination idea: the generator settles for its best try when
     * it never reaches the level, so the day is dealt again from a derived seed (the first try is
     * unchanged, so every day that already matched keeps its puzzle).
     */
    internal fun dealDaily(epochDay: Long, table: Boolean = true): Dealt {
        val difficulty = dailyDifficulty(epochDay)
        val generator = PuzzleGenerator(Geometry.Mini)
        val base = seed(1, epochDay)
        fun seedOf(attempt: Long) = if (attempt == 0L) base else SplitMix64.mix(base xor attempt)
        val tabled = if (table) CatalogTable.daily(epochDay) else null
        if (tabled != null) {
            val generated = generator.carveAt(seedOf(tabled.attempt), tabled.draws, difficulty)
            return Dealt(spec(dailyKey(epochDay), PuzzleKind.Daily, generated, epochDay = epochDay), 1, tabled)
        }
        var attempt = 0L
        var carving = generator.search(base, difficulty)
        var carves = carving.carves
        while (carving.generated.grade.difficulty != difficulty && attempt < DAILY_RETRIES) {
            attempt++
            carving = generator.search(seedOf(attempt), difficulty)
            carves += carving.carves
        }
        val spec = spec(dailyKey(epochDay), PuzzleKind.Daily, carving.generated, epochDay = epochDay)
        return Dealt(spec, carves, TableEntry(attempt, carving.draws))
    }

    fun classic(difficulty: Difficulty, number: Int): PuzzleSpec = dealClassic(difficulty, number).spec

    /** Practice page [number] at [difficulty], from [CatalogTable] when it is there (one carve). */
    internal fun dealClassic(difficulty: Difficulty, number: Int, table: Boolean = true): Dealt {
        val generator = PuzzleGenerator(Geometry.Classic)
        val seed = seed(2 + difficulty.ordinal.toLong(), number.toLong())
        val key = classicKey(difficulty, number)
        val tabled = if (table) CatalogTable.classic(difficulty, number) else null
        if (tabled != null) {
            val generated = generator.carveAt(seed, tabled.draws, difficulty)
            return Dealt(spec(key, PuzzleKind.Classic, generated, number = number), 1, tabled)
        }
        val carving = generator.search(seed, difficulty)
        return Dealt(spec(key, PuzzleKind.Classic, carving.generated, number = number), carving.carves, TableEntry(0, carving.draws))
    }

    /**
     * Practice page [number] at [difficulty], dealt to need [target] when there is one (10.0: the
     * path, see [Path]). Without a target it is exactly [classic]. With one, it comes from that
     * technique's own stream and carries it in its key ("classic-steady-12-pointing"), so the same
     * page is dealt again for the same key; if no page at the level needs the technique within
     * [PATH_CARVES] carves (the plain page included), the plain page is dealt instead.
     */
    fun practice(difficulty: Difficulty, number: Int, target: Technique?): PuzzleSpec =
        dealPractice(difficulty, number, target).spec

    /**
     * Each try at a path page is carved from a seed of its own ([pathSeed]), not drawn one after
     * another from one stream: which try needs the target depends only on the key, never on the
     * tries before it, so [CatalogTable] keeps the try for the early pages of each level and
     * technique (one carve), and the tries could be carved in parallel.
     */
    internal fun dealPractice(difficulty: Difficulty, number: Int, target: Technique?, table: Boolean = true): Dealt {
        if (target == null) return dealClassic(difficulty, number, table)
        val generator = PuzzleGenerator(Geometry.Classic)
        val key = practiceKey(difficulty, number, target)
        fun dealt(attempt: Int, generated: com.titpea.papersudoku.engine.GeneratedPuzzle, carves: Int) =
            Dealt(spec(key, PuzzleKind.Classic, generated, number = number), carves, TableEntry(attempt.toLong(), 0))
        val tabled = if (table) CatalogTable.path(difficulty, target, number) else null
        if (tabled != null) {
            if (tabled == CatalogTable.MISS) return plainInstead(difficulty, number, table, 0)
            return dealt(tabled, generator.carveAt(pathSeed(difficulty, number, target, tabled), 0, difficulty), 1)
        }
        for (attempt in 0 until PATH_CARVES - 1) {
            val generated = generator.carveAt(pathSeed(difficulty, number, target, attempt), 0, difficulty)
            if (generated.grade.difficulty == difficulty && target in generated.grade.techniques) {
                return dealt(attempt, generated, attempt + 1)
            }
        }
        return plainInstead(difficulty, number, table, PATH_CARVES - 1)
    }

    private fun plainInstead(difficulty: Difficulty, number: Int, table: Boolean, tried: Int): Dealt {
        val plain = dealClassic(difficulty, number, table)
        return Dealt(plain.spec, tried + plain.carves, CatalogTable.MISS_ENTRY)
    }

    /** The seed of try [attempt] at path page [number] of [difficulty] for [target]. */
    private fun pathSeed(difficulty: Difficulty, number: Int, target: Technique, attempt: Int): Long {
        require(number >= 0 && attempt in 0 until 0x10000)
        return seed(PATH_STREAM + target.ordinal, (difficulty.ordinal.toLong() shl 48) or (number.toLong() shl 16) or attempt.toLong())
    }

    fun practiceKey(difficulty: Difficulty, number: Int, target: Technique?): String =
        classicKey(difficulty, number) + (target?.let { "-" + it.name.lowercase() } ?: "")

    /** The technique a practice page was dealt to need, from its key; null for a plain page. */
    fun practiceTarget(key: String): Technique? =
        Technique.entries.firstOrNull { key.startsWith("classic-") && key.endsWith("-" + it.name.lowercase()) }

    /**
     * Hashed, not stepped: SplitMix64 itself steps its state by a fixed constant, so seeds that
     * differ by a multiple of it (as `n * constant` did up to 2) replay each other's draws, and a
     * page whose generator retried could deal the same puzzle as a later page.
     */
    // Streams 10 (weekly) and 20 + technique (focus) dealt 9.0's pages: not to be reused.
    private const val PATH_STREAM = 40L
    /** Carves a path page may cost at most, the plain page it falls back to included. */
    internal const val PATH_CARVES = 600
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

/**
 * A page and what dealing it cost: [carves] carves, and where the page sits in its seeds
 * ([entry]), which is what [CatalogTable] keeps.
 */
internal class Dealt(val spec: PuzzleSpec, val carves: Int, val entry: TableEntry)

/** What a page is in [CatalogTable]. */
internal data class TableEntry(val attempt: Long, val draws: Long)

/** What became of a typed-in page. */
sealed interface Import {
    data class Ok(val spec: PuzzleSpec) : Import

    data class Invalid(val reason: Reason, val cells: List<Int> = emptyList()) : Import

    enum class Reason { Shape, Clash, TooFew, NoSolution, ManySolutions }
}
