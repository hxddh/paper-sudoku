package com.titpea.papersudoku.data

import android.os.Trace
import android.util.Log
import androidx.room.withTransaction
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class GameResult(
    val puzzleKey: String,
    val kind: PuzzleKind,
    val difficulty: Difficulty,
    val durationSeconds: Long,
    val coachAsks: Int,
    val coachLevel: Int,
    val completedAt: Long,
    val epochDay: Long?,
)

/**
 * A saved page as the notebook and widget draw it: the printed and current digits, one character
 * per cell, without the undo history.
 */
data class PageSummary(
    val key: String,
    val kind: PuzzleKind,
    val difficulty: Difficulty,
    val number: Int?,
    val epochDay: Long?,
    val solved: Boolean,
    val givens: String,
    val grid: String,
    /** When the page was last written on (epoch millis); 0 where it is not known. */
    val updatedAt: Long = 0,
) {
    val size: Int get() = if (grid.length == 36) 6 else 9
    val boxRows: Int get() = if (size == 6) 2 else 3
    val boxCols: Int get() = 3

    fun valueAt(index: Int): Int = grid[index] - '0'
    fun isGiven(index: Int): Boolean = givens.getOrNull(index)?.let { it != '0' } ?: false

    companion object {
        fun of(game: GameState) = PageSummary(
            key = game.spec.key,
            kind = game.spec.kind,
            difficulty = game.spec.difficulty,
            number = game.spec.number,
            epochDay = game.spec.epochDay,
            solved = game.solved,
            givens = game.spec.givens.digits(),
            grid = game.values.digits(),
        )
    }
}

/** Saved pages and finished results. An interface so ViewModel tests can use a fake. */
interface GameStore {
    /** The saved page, or null. An unreadable save is set aside (kept, out of the way), not lost. */
    suspend fun load(puzzleKey: String): GameState?
    suspend fun save(state: GameState, now: Long)

    /** Drops a saved page: a daily only looked at is not kept as begun. */
    suspend fun forget(puzzleKey: String)

    /** Drops saved dailies that were opened but never begun (saved that way before 8.0). */
    suspend fun forgetUntouchedDailies()

    /**
     * Saves the solved page and records its first completion together, so a page is never left
     * solved but unstamped. Later replays are ignored; returns whether a result was recorded.
     */
    suspend fun saveSolved(state: GameState, result: GameResult, now: Long): Boolean

    suspend fun nextClassicNumber(difficulty: Difficulty): Int

    /** Drops unfinished practice pages other than [except]; numbering continues past them. */
    suspend fun discardUnsolvedPractice(except: String)

    /** The practice page to continue, if one is unfinished. */
    suspend fun latestUnsolvedPracticeKey(): String?

    /** Daily pages saved on or after [fromEpochDay], newest first. */
    fun dailies(fromEpochDay: Long): Flow<List<PageSummary>>

    /** One saved page, as it changes. */
    fun page(puzzleKey: String): Flow<PageSummary?>

    /** Pages the player typed in, most recently touched first. */
    val imported: Flow<List<PageSummary>>

    /** Finished pages other than dailies (all 9×9), most recently finished first. */
    val solvedNineByNine: Flow<List<PageSummary>>

    /**
     * Weekly and technique pages begun and not finished, any week: 10.0 has no other way back to
     * them (the weekly card and "Practise this" are gone).
     */
    val unfinishedOthers: Flow<List<PageSummary>>

    /** The practice page to continue, if one is unfinished. */
    val unfinishedPractice: Flow<PageSummary?>

    /** Practice solves without the Coach's answer, per difficulty. */
    val cleanPractice: Flow<Map<Difficulty, Int>>

    val days: Flow<List<DayResult>>

    /** Every stamped page, dailies and practice. */
    val stampedCount: Flow<Int>

    /** When each practice page was finished (epoch millis). */
    val practiceCompletions: Flow<List<Long>>
}

/**
 * The notebook reads everything it shows from two queries, the small columns of every saved page
 * and every result, shared by all its flows and filtered in memory; the widget reads the same
 * pages. They are read again after each write made here (the only writer), not on Room's
 * invalidation: a value read before a write that has since finished is never handed out, so a
 * `first()` right after a save sees it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomGameStore(
    private val database: SudokuDatabase,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : GameStore {
    private val savedGames = database.savedGameDao()
    private val results = database.resultDao()

    /** Counts finished writes; every shared read is tagged with the count it began after. */
    private val writes = MutableStateFlow(0L)

    private class Read<T>(val after: Long, val rows: Result<T>)

    private fun <T> shared(scope: CoroutineScope, query: suspend () -> T): Flow<T> {
        val reads = writes
            .mapLatest { after ->
                val rows = try {
                    Result.success(query())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Handed to the collectors, as an unshared query would: the scope must not fail.
                    Result.failure(e)
                }
                Read(after, rows)
            }
            .shareIn(scope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MILLIS), replay = 1)
        return reads.filter { it.after >= writes.value }.map { it.rows.getOrThrow() }
    }

    private val pages: Flow<List<PageSummary>> = shared(scope) { savedGames.pages().mapNotNull(::summary) }

    private val resultRows: Flow<List<ResultRow>> = shared(scope) { results.all() }

    private suspend fun <T> write(block: suspend () -> T): T = try {
        block()
    } finally {
        writes.update { it + 1 }
    }

    override suspend fun load(puzzleKey: String): GameState? {
        val json = savedGames.get(puzzleKey)?.json ?: return null
        return withContext(Dispatchers.Default) {
            Trace.beginSection("ps.decode")
            try { GameState.decode(json) } finally { Trace.endSection() }
        } ?: null.also {
            Log.w(TAG, "Unreadable saved page $puzzleKey; set aside, starting it afresh")
            // Its stamp goes with it: the day reads as not done, and finishing it again stamps it.
            write {
                database.withTransaction {
                    savedGames.setAside(puzzleKey, "damaged-$puzzleKey-${System.currentTimeMillis()}")
                    results.forget(puzzleKey)
                }
            }
        }
    }

    override suspend fun forget(puzzleKey: String) = write { savedGames.delete(puzzleKey) }

    override suspend fun forgetUntouchedDailies() {
        for (key in savedGames.unwrittenDailyKeys()) {
            val row = savedGames.get(key) ?: continue
            val game = withContext(Dispatchers.Default) { GameState.decode(row.json) } ?: continue
            // Only the row as read: a page begun meanwhile was saved again and stays.
            if (game.untouched) write { savedGames.deleteIfUnchanged(key, row.updatedAt) }
        }
    }

    override suspend fun save(state: GameState, now: Long) {
        val entity = entity(state, now)
        write { savedGames.upsert(entity) }
    }

    override suspend fun saveSolved(state: GameState, result: GameResult, now: Long): Boolean {
        val entity = entity(state, now)
        return write {
            database.withTransaction { recordSolved(entity, result) }
        }
    }

    private suspend fun recordSolved(entity: SavedGameEntity, result: GameResult): Boolean {
        savedGames.upsert(entity)
        if (results.exists(result.puzzleKey)) return false
        results.insert(
            GameResultEntity(
                mode = modeOf(result.kind),
                durationSeconds = result.durationSeconds,
                hints = result.coachAsks,
                completedAt = result.completedAt,
                puzzleKey = result.puzzleKey,
                difficulty = result.difficulty.name,
                coachLevel = result.coachLevel,
                epochDay = result.epochDay,
            ),
        )
        return true
    }

    // Numbered by the level the page was dealt at (its key), not the grade the generator reached,
    // so a page that came out easier can never be dealt twice.
    override suspend fun nextClassicNumber(difficulty: Difficulty): Int =
        (savedGames.maxNumber(PuzzleKind.Classic.name, Catalog.classicPrefix(difficulty)) ?: -1) + 1

    override suspend fun discardUnsolvedPractice(except: String) =
        write { savedGames.discardUnsolvedPractice(except) }

    override suspend fun latestUnsolvedPracticeKey(): String? = savedGames.latestUnsolvedPracticeKey()

    override fun dailies(fromEpochDay: Long): Flow<List<PageSummary>> = pages.derive { all ->
        all.filter { it.kind == PuzzleKind.Daily && (it.epochDay ?: Long.MIN_VALUE) >= fromEpochDay }
            .sortedByDescending { it.epochDay }
    }

    override fun page(puzzleKey: String): Flow<PageSummary?> = pages.derive { all -> all.firstOrNull { it.key == puzzleKey } }

    override val imported: Flow<List<PageSummary>> = pages.derive { all ->
        all.filter { it.kind == PuzzleKind.Imported }.sortedByDescending { it.updatedAt }
    }

    override val unfinishedOthers: Flow<List<PageSummary>> = pages.derive { all ->
        all.filter { (it.kind == PuzzleKind.Weekly || it.kind == PuzzleKind.Focus) && !it.solved && it.grid != it.givens }
            .sortedByDescending { it.updatedAt }
    }

    override val solvedNineByNine: Flow<List<PageSummary>> = pages.derive { all ->
        all.filter { it.kind != PuzzleKind.Daily && it.solved }.sortedByDescending { it.updatedAt }
    }

    override val unfinishedPractice: Flow<PageSummary?> = pages.derive { all ->
        all.filter { it.kind == PuzzleKind.Classic && !it.solved }.maxByOrNull { it.updatedAt }
    }

    override val cleanPractice: Flow<Map<Difficulty, Int>> = resultRows.derive { rows ->
        rows.filter { it.mode == MODE_CLASSIC && it.coachLevel < 3 }
            .groupingBy { it.difficulty }
            .eachCount()
            .mapNotNull { (name, solved) -> Difficulty.entries.firstOrNull { it.name == name }?.let { it to solved } }
            .toMap()
    }

    override val days: Flow<List<DayResult>> = resultRows.derive { rows ->
        rows.filter { it.epochDay != null }
            .groupBy { checkNotNull(it.epochDay) }
            .toSortedMap()
            .map { (day, done) -> DayResult(day, done.minOf { it.coachLevel }, done.minOf { it.durationSeconds }) }
    }

    override val stampedCount: Flow<Int> = resultRows.derive { it.size }

    override val practiceCompletions: Flow<List<Long>> = resultRows.derive { rows ->
        rows.filter { it.mode == MODE_CLASSIC }.map { it.completedAt }
    }

    private fun <T, R> Flow<T>.derive(transform: (T) -> R): Flow<R> =
        map(transform).distinctUntilChanged().flowOn(Dispatchers.Default)

    private suspend fun entity(state: GameState, now: Long): SavedGameEntity {
        val spec = state.spec
        val json = withContext(Dispatchers.Default) { state.encode() }
        return SavedGameEntity(
            puzzleKey = spec.key,
            kind = spec.kind.name,
            difficulty = spec.difficulty.name,
            number = spec.number,
            epochDay = spec.epochDay,
            solved = state.solved,
            json = json,
            // A finished page keeps the time it was finished: reading it again later does not move
            // it in the journal, which files it under that day.
            updatedAt = if (state.solved) state.solvedAt ?: now else now,
            givens = spec.givens.digits(),
            grid = state.values.digits(),
        )
    }

    private fun summary(row: SavedPageRow): PageSummary? {
        val kind = PuzzleKind.entries.firstOrNull { it.name == row.kind } ?: return null
        val difficulty = Difficulty.entries.firstOrNull { it.name == row.difficulty } ?: return null
        if (row.grid.length != 36 && row.grid.length != 81) return null
        return PageSummary(row.puzzleKey, kind, difficulty, row.number, row.epochDay, row.solved, row.givens, row.grid, row.updatedAt)
    }

    companion object {
        const val MODE_MINI = "mini"
        const val MODE_CLASSIC = "classic"

        /** Only practice pages are "classic": the levels and the month counts read that mode. */
        fun modeOf(kind: PuzzleKind): String = when (kind) {
            PuzzleKind.Daily -> MODE_MINI
            PuzzleKind.Classic -> MODE_CLASSIC
            PuzzleKind.Weekly -> "weekly"
            PuzzleKind.Imported -> "imported"
            PuzzleKind.Focus -> "focus"
        }
        private const val TAG = "GameStore"

        /** How long the shared reads outlive their last collector (a rotation, a pane switch). */
        private const val SHARE_TIMEOUT_MILLIS = 5_000L
    }
}
