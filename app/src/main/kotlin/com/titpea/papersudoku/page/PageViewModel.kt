package com.titpea.papersudoku.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.titpea.papersudoku.AppContainer
import com.titpea.papersudoku.data.GameResult
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.data.UserSettings
import com.titpea.papersudoku.data.latestUnsolvedPractice
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.Practice
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate

/** Stable identity of what the page pane shows, used by the list-detail navigator. */
object PageKeys {
    const val PRACTICE = "practice"
    fun daily(epochDay: Long): String = Catalog.dailyKey(epochDay)
    fun of(game: GameState): String =
        if (game.spec.kind == PuzzleKind.Daily) game.spec.key else PRACTICE
}

/** A Coach session: what it found and how far the player has asked it to go (1 look, 2 why, 3 answer). */
data class CoachUi(val hint: CoachHint?, val level: Int)

data class PageUiState(
    val loading: Boolean = true,
    val game: GameState? = null,
    val coach: CoachUi? = null,
    val conflicts: Set<Int> = emptySet(),
    /** Monotonic time the hidden clock started, or null while it is stopped. */
    val runningSince: Long? = null,
    val settings: UserSettings = UserSettings(),
    val today: Long = 0,
    /** True only right after the solving move, so the stamp lands once. */
    val justSolved: Boolean = false,
) {
    val boardLocked: Boolean get() = loading || game?.solved != false
}

sealed interface PageEvent {
    data class Tap(val index: Int) : PageEvent
    data class Select(val index: Int) : PageEvent
    data class Digit(val digit: Int) : PageEvent
    data object ClearLock : PageEvent
    data object Erase : PageEvent
    data object Undo : PageEvent
    data object ToggleNotes : PageEvent
    data object Coach : PageEvent
    data object CoachDeeper : PageEvent
    data object CoachApply : PageEvent
    data object DismissCoach : PageEvent
    data object Foreground : PageEvent
    data object Background : PageEvent
    data object Restart : PageEvent
    data class OpenDaily(val epochDay: Long) : PageEvent
    data object OpenPractice : PageEvent
    data object NextPractice : PageEvent
}

/** One-off feedback for haptics. */
enum class PageEffect { Placed, Conflict, Solved, Toggled }

/**
 * Owns the page on the desk.
 *
 * - Every change is saved (debounced) as JSON in Room; a page survives process death, a swipe from
 *   Recents, a reboot and an update.
 * - The clock is hidden and only runs while the page is on screen; it surfaces on the stamp.
 * - Digits: with an editable cell selected a digit is written; otherwise the digit is picked up
 *   and every tap on the board writes it (digit-first), until it is put down again.
 * - Practice difficulty follows clean solves ([Practice]).
 */
class PageViewModel(
    private val store: GameStore,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
    private val ticker: () -> Long,
    private val generatorDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onDailySaved: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(PageUiState(today = today()))
    val state: StateFlow<PageUiState> = _state.asStateFlow()

    private val _effects = Channel<PageEffect>(Channel.BUFFERED)
    val effects: Flow<PageEffect> = _effects.receiveAsFlow()

    private var engine: SudokuEngine? = null
    private var engineKey: String? = null
    private var foreground = false
    private var saveJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings -> _state.update { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            store.pruneUnsolvedDailies(today() - KEEP_UNFINISHED_DAILY_DAYS)
        }
        openDaily(today())
    }

    fun onEvent(event: PageEvent) {
        val settings = _state.value.settings
        when (event) {
            is PageEvent.Tap -> mutate { it.tap(event.index, settings.autoClearNotes) }
            is PageEvent.Select -> mutate { it.select(event.index) }
            is PageEvent.Digit -> digit(event.digit)
            PageEvent.ClearLock -> mutate(PageEffect.Toggled) { g -> g.lockedDigit?.let { g.toggleLock(it) } ?: g }
            PageEvent.Erase -> mutate { it.erase() }
            PageEvent.Undo -> mutate { it.undo() }
            PageEvent.ToggleNotes -> mutate(PageEffect.Toggled) { it.toggleNotesMode() }
            PageEvent.Coach -> openCoach()
            PageEvent.CoachDeeper -> deepenCoach()
            PageEvent.CoachApply -> applyCoach()
            PageEvent.DismissCoach -> _state.update { it.copy(coach = null) }
            PageEvent.Foreground -> onForeground()
            PageEvent.Background -> onBackground()
            PageEvent.Restart -> restart()
            is PageEvent.OpenDaily -> openDaily(event.epochDay)
            PageEvent.OpenPractice -> openPractice()
            PageEvent.NextPractice -> nextPractice()
        }
    }

    // region Board

    /**
     * Write into the selected cell, or pick the digit up (digit-first) when there is nothing to
     * write into. A picked-up digit is put down by tapping it again; another digit swaps it.
     */
    private fun digit(digit: Int) {
        val game = _state.value.game ?: return
        val autoClear = _state.value.settings.autoClearNotes
        val selected = game.selected
        when {
            // Holding a digit: tapping it again puts it down, another digit swaps it.
            game.lockedDigit != null -> mutate(PageEffect.Toggled) { it.toggleLock(digit) }
            selected != null && game.isEditable(selected) -> mutate { it.input(digit, autoClear) }
            else -> mutate(PageEffect.Toggled) { it.toggleLock(digit) }
        }
    }

    private fun mutate(effect: PageEffect? = null, block: (GameState) -> GameState) {
        val current = _state.value
        val before = current.game ?: return
        if (current.boardLocked) return
        var after = block(before)
        if (after == before) return

        val boardChanged = after.values != before.values || after.notes != before.notes
        var running = current.runningSince
        var feedback = effect
        if (after.values == after.spec.solution) {
            running?.let { after = after.addElapsed(ticker() - it) }
            running = null
            after = after.settle(clock.millis())
            feedback = PageEffect.Solved
            record(after)
        } else if (after.values != before.values) {
            val placed = after.values.indices.firstOrNull { after.values[it] != before.values[it] && after.values[it] != 0 }
            if (placed != null) {
                val conflicts = engineFor(after).conflicts(after.values.toIntArray())
                feedback = if (placed in conflicts) PageEffect.Conflict else PageEffect.Placed
            }
        }
        _state.update {
            it.copy(
                game = after,
                runningSince = running,
                coach = if (boardChanged) null else it.coach,
                justSolved = after.solved && !before.solved,
            ).withDerived()
        }
        scheduleSave()
        feedback?.let { _effects.trySend(it) }
    }

    // endregion

    // region Coach

    private fun openCoach() {
        val current = _state.value
        val game = current.game ?: return
        if (current.boardLocked) return
        val hint = engineFor(game).hint(game.values.toIntArray())
        val updated = if (hint != null) game.coachAsked() else game
        _state.update { it.copy(game = updated, coach = CoachUi(hint, level = 1)) }
        scheduleSave()
    }

    private fun deepenCoach() {
        val current = _state.value
        val coach = current.coach ?: return
        val game = current.game ?: return
        if (coach.hint == null || coach.level >= 3) return
        val level = coach.level + 1
        _state.update { it.copy(game = game.coachReached(level), coach = coach.copy(level = level)) }
        val hint = coach.hint
        if (level >= 2 && hint is CoachHint.Next) {
            viewModelScope.launch {
                settingsStore.update { it.copy(techniquesMet = it.techniquesMet + hint.technique) }
            }
        }
        scheduleSave()
    }

    private fun applyCoach() {
        val coach = _state.value.coach ?: return
        if (coach.level < 3) return
        val autoClear = _state.value.settings.autoClearNotes
        when (val hint = coach.hint) {
            is CoachHint.Next -> mutate { it.placeHint(hint.placement.index, hint.placement.digit, autoClear) }
            is CoachHint.Mistake -> mutate { it.select(hint.index).erase() }
            null -> Unit
        }
    }

    // endregion

    // region Clock

    private fun onForeground() {
        foreground = true
        val today = today()
        _state.update { it.copy(today = today) }
        _state.update {
            if (it.game != null && !it.game.solved && !it.loading && it.runningSince == null) {
                it.copy(runningSince = ticker())
            } else {
                it
            }
        }
    }

    private fun onBackground() {
        foreground = false
        _state.update { it.copy(game = foldClock(it), runningSince = null) }
        saveNow()
    }

    private fun foldClock(state: PageUiState): GameState? {
        val game = state.game ?: return null
        val since = state.runningSince ?: return game
        return game.addElapsed(ticker() - since)
    }

    // endregion

    // region Pages

    private fun restart() {
        val game = _state.value.game ?: return
        _state.update {
            it.copy(
                game = game.restart(),
                coach = null,
                runningSince = if (foreground) ticker() else null,
                justSolved = false,
            ).withDerived()
        }
        saveNow()
    }

    private fun openDaily(epochDay: Long) {
        val current = _state.value.game
        if (current != null && current.spec.key == Catalog.dailyKey(epochDay) && !_state.value.loading) return
        load {
            store.load(Catalog.dailyKey(epochDay))
                ?: GameState.new(withContext(generatorDispatcher) { Catalog.daily(epochDay) })
        }
    }

    private fun openPractice() {
        val current = _state.value.game
        if (current != null && current.spec.kind == PuzzleKind.Classic && !current.solved) return
        load { store.latestUnsolvedPractice() ?: newPractice() }
    }

    private fun nextPractice() {
        load { newPractice() }
    }

    private suspend fun newPractice(): GameState {
        val level = Practice.level(store.cleanPractice.first())
        val number = store.nextClassicNumber(level)
        return GameState.new(withContext(generatorDispatcher) { Catalog.classic(level, number) })
    }

    private fun load(source: suspend () -> GameState) {
        loadJob?.cancel()
        // Stop the current page's clock now: preparing the next page is not time spent on it.
        _state.update { it.copy(game = foldClock(it), runningSince = null, loading = true, coach = null) }
        loadJob = viewModelScope.launch { show(source()) }
    }

    private suspend fun show(next: GameState) {
        val previous = _state.value.game
        if (previous != null && previous.spec.key != next.spec.key) persist(previous)
        _state.update {
            it.copy(
                loading = false,
                game = next,
                coach = null,
                runningSince = if (foreground && !next.solved) ticker() else null,
                justSolved = false,
            ).withDerived()
        }
        persist(next)
    }

    // endregion

    private fun record(game: GameState) {
        val spec = game.spec
        viewModelScope.launch {
            persist(game)
            store.recordOnce(
                GameResult(
                    puzzleKey = spec.key,
                    kind = spec.kind,
                    difficulty = spec.difficulty,
                    durationSeconds = game.elapsedMillis / 1_000,
                    coachAsks = game.coach.asks,
                    coachLevel = game.coach.deepest,
                    completedAt = game.solvedAt ?: clock.millis(),
                    epochDay = spec.epochDay,
                ),
            )
            settingsStore.update { it.copy(techniquesMet = it.techniquesMet + spec.hardest) }
        }
    }

    private suspend fun persist(game: GameState) {
        store.save(game, clock.millis())
        if (game.spec.kind == PuzzleKind.Daily) onDailySaved()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            foldClock(_state.value)?.let { persist(it) }
        }
    }

    private fun saveNow() {
        saveJob?.cancel()
        val game = _state.value.game ?: return
        saveJob = viewModelScope.launch { persist(game) }
    }

    private fun engineFor(game: GameState): SudokuEngine {
        val cached = engine
        if (cached != null && engineKey == game.spec.key) return cached
        return SudokuEngine(game.spec.puzzle).also {
            engine = it
            engineKey = game.spec.key
        }
    }

    private fun PageUiState.withDerived(): PageUiState {
        val game = game ?: return this
        return copy(conflicts = engineFor(game).conflicts(game.values.toIntArray()))
    }

    private fun today(): Long = LocalDate.now(clock).toEpochDay()

    companion object {
        private const val SAVE_DEBOUNCE_MILLIS = 400L
        private const val KEEP_UNFINISHED_DAILY_DAYS = 365L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PageViewModel(
                    store = container.gameStore,
                    settingsStore = container.settingsStore,
                    clock = container.clock,
                    ticker = container.ticker,
                    onDailySaved = container.onDailySaved,
                )
            }
        }
    }
}
