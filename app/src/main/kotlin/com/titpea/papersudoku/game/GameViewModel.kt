package com.titpea.papersudoku.game

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
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
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

/** A Coach session: what it found and how far the player has asked it to go (1 look, 2 why, 3 answer). */
data class CoachUi(val hint: CoachHint?, val level: Int)

enum class SlotStatus { New, InProgress, Solved }

data class PickerUi(
    val today: Long,
    val daily: SlotStatus,
    /** For each difficulty, the number of an unfinished Classic, if any. */
    val classicInProgress: Map<Difficulty, Int?>,
)

data class PlayUiState(
    val loading: Boolean = true,
    val game: GameState? = null,
    val coach: CoachUi? = null,
    val conflicts: Set<Int> = emptySet(),
    val mistakes: Set<Int> = emptySet(),
    val userPaused: Boolean = false,
    /** Monotonic time the clock started running, or null while it is stopped. */
    val runningSince: Long? = null,
    val settings: UserSettings = UserSettings(),
    val today: Long = 0,
    val picker: PickerUi? = null,
    /** True only right after the solving move, so the stamp animates once. */
    val justSolved: Boolean = false,
) {
    val boardLocked: Boolean get() = loading || userPaused || game?.solved != false
}

sealed interface PlayEvent {
    data class Tap(val index: Int) : PlayEvent
    data class Select(val index: Int) : PlayEvent
    data class Digit(val digit: Int) : PlayEvent
    data class Lock(val digit: Int) : PlayEvent
    data object Erase : PlayEvent
    data object Undo : PlayEvent
    data object ToggleNotes : PlayEvent
    data object Coach : PlayEvent
    data object CoachDeeper : PlayEvent
    data object CoachApply : PlayEvent
    data object DismissCoach : PlayEvent
    data object TogglePause : PlayEvent
    data object Foreground : PlayEvent
    data object Background : PlayEvent
    data object Restart : PlayEvent
    data object NextClassic : PlayEvent
    data class OpenDaily(val epochDay: Long) : PlayEvent
    data class OpenClassic(val difficulty: Difficulty) : PlayEvent
    data object OpenPicker : PlayEvent
    data object ClosePicker : PlayEvent
}

/** One-off feedback for haptics. */
enum class PlayEffect { Placed, Conflict, Solved, Toggled }

/**
 * Owns the puzzle on the desk.
 *
 * - Every change is saved (debounced) as JSON in Room, so a game survives process death, a swipe
 *   from Recents, a reboot, and an update.
 * - The clock only runs while the screen is visible and not paused; time is folded into the
 *   game whenever it stops.
 * - Results are recorded once per puzzle.
 */
class GameViewModel(
    private val store: GameStore,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
    private val ticker: () -> Long,
    private val generatorDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayUiState(today = today()))
    val state: StateFlow<PlayUiState> = _state.asStateFlow()

    private val _effects = Channel<PlayEffect>(Channel.BUFFERED)
    val effects: Flow<PlayEffect> = _effects.receiveAsFlow()

    private var engine: SudokuEngine? = null
    private var engineKey: String? = null
    private var foreground = false
    private var saveJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _state.update { it.copy(settings = settings).withDerived() }
            }
        }
        viewModelScope.launch {
            val initial = settingsStore.settings.first()
            val today = today()
            store.pruneUnsolvedDailies(today - KEEP_UNFINISHED_DAILY_DAYS)
            val active = initial.activeKey
            val resumed = active
                ?.takeIf { it.startsWith("classic-") }
                ?.let { store.load(it) }
            if (resumed != null) show(resumed) else openDaily(today)
        }
    }

    fun onEvent(event: PlayEvent) {
        val settings = _state.value.settings
        when (event) {
            is PlayEvent.Tap -> mutate { it.tap(event.index, settings.autoClearNotes) }
            is PlayEvent.Select -> mutate { it.select(event.index) }
            is PlayEvent.Digit -> mutate { it.input(event.digit, settings.autoClearNotes) }
            is PlayEvent.Lock -> mutate(effect = PlayEffect.Toggled) { it.toggleLock(event.digit) }
            PlayEvent.Erase -> mutate { it.erase() }
            PlayEvent.Undo -> mutate { it.undo() }
            PlayEvent.ToggleNotes -> mutate(effect = PlayEffect.Toggled) { it.toggleNotesMode() }
            PlayEvent.Coach -> openCoach()
            PlayEvent.CoachDeeper -> deepenCoach()
            PlayEvent.CoachApply -> applyCoach()
            PlayEvent.DismissCoach -> _state.update { it.copy(coach = null) }
            PlayEvent.TogglePause -> togglePause()
            PlayEvent.Foreground -> onForeground()
            PlayEvent.Background -> onBackground()
            PlayEvent.Restart -> restart()
            PlayEvent.NextClassic -> nextClassic()
            is PlayEvent.OpenDaily -> openDaily(event.epochDay)
            is PlayEvent.OpenClassic -> openClassic(event.difficulty)
            PlayEvent.OpenPicker -> openPicker()
            PlayEvent.ClosePicker -> _state.update { it.copy(picker = null) }
        }
    }

    // region Board

    private fun mutate(effect: PlayEffect? = null, block: (GameState) -> GameState) {
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
            feedback = PlayEffect.Solved
            record(after)
        } else if (after.values != before.values) {
            val placed = after.values.indices.firstOrNull { after.values[it] != before.values[it] && after.values[it] != 0 }
            if (placed != null) {
                val conflicts = engineFor(after).conflicts(after.values.toIntArray())
                feedback = if (placed in conflicts) PlayEffect.Conflict else PlayEffect.Placed
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

    private fun togglePause() {
        val current = _state.value
        val game = current.game ?: return
        if (game.solved || current.loading) return
        if (current.userPaused) {
            _state.update { it.copy(userPaused = false, runningSince = if (foreground) ticker() else null) }
        } else {
            _state.update { it.copy(game = foldClock(it), userPaused = true, runningSince = null, coach = null) }
            saveNow()
        }
    }

    private fun onForeground() {
        foreground = true
        val today = today()
        _state.update { it.copy(today = today) }
        val game = _state.value.game
        val day = game?.spec?.epochDay
        if (game != null && game.solved && day != null && day < today) {
            openDaily(today)
            return
        }
        _state.update {
            if (it.game != null && !it.game.solved && !it.userPaused && it.runningSince == null) {
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

    private fun foldClock(state: PlayUiState): GameState? {
        val game = state.game ?: return null
        val since = state.runningSince ?: return game
        return game.addElapsed(ticker() - since)
    }

    // endregion

    // region Puzzles

    private fun restart() {
        val game = _state.value.game ?: return
        _state.update {
            it.copy(
                game = game.restart(),
                coach = null,
                userPaused = false,
                runningSince = if (foreground) ticker() else null,
                justSolved = false,
            ).withDerived()
        }
        saveNow()
    }

    private fun nextClassic() {
        val difficulty = _state.value.game?.spec?.difficulty ?: Difficulty.Gentle
        load {
            val number = store.nextClassicNumber(difficulty)
            GameState.new(withContext(generatorDispatcher) { Catalog.classic(difficulty, number) })
        }
    }

    private fun openDaily(epochDay: Long) {
        load {
            store.load(Catalog.dailyKey(epochDay))
                ?: GameState.new(withContext(generatorDispatcher) { Catalog.daily(epochDay) })
        }
    }

    private fun openClassic(difficulty: Difficulty) {
        val current = _state.value.game
        if (current != null && current.spec.kind == PuzzleKind.Classic &&
            current.spec.difficulty == difficulty && !current.solved
        ) {
            _state.update { it.copy(picker = null) }
            return
        }
        load {
            store.latestUnsolvedClassic(difficulty) ?: run {
                val number = store.nextClassicNumber(difficulty)
                GameState.new(withContext(generatorDispatcher) { Catalog.classic(difficulty, number) })
            }
        }
    }

    private fun openPicker() {
        viewModelScope.launch {
            val today = today()
            val daily = store.load(Catalog.dailyKey(today))
            val status = when {
                daily == null -> SlotStatus.New
                daily.solved -> SlotStatus.Solved
                else -> SlotStatus.InProgress
            }
            val classic = Difficulty.entries.associateWith { d ->
                store.latestUnsolvedClassic(d)?.spec?.number
            }
            _state.update { it.copy(picker = PickerUi(today, status, classic)) }
        }
    }

    private fun load(source: suspend () -> GameState) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, picker = null, coach = null) }
        loadJob = viewModelScope.launch { show(source()) }
    }

    private suspend fun show(next: GameState) {
        val previous = foldClock(_state.value)
        if (previous != null && previous.spec.key != next.spec.key) store.save(previous, clock.millis())
        _state.update {
            it.copy(
                loading = false,
                game = next,
                coach = null,
                picker = null,
                userPaused = false,
                runningSince = if (foreground && !next.solved) ticker() else null,
                justSolved = false,
            ).withDerived()
        }
        store.save(next, clock.millis())
        settingsStore.update { it.copy(activeKey = next.spec.key) }
    }

    // endregion

    private fun record(game: GameState) {
        val spec = game.spec
        viewModelScope.launch {
            store.save(game, clock.millis())
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

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            _state.value.game?.let { store.save(foldClock(_state.value) ?: it, clock.millis()) }
        }
    }

    private fun saveNow() {
        saveJob?.cancel()
        val game = _state.value.game ?: return
        saveJob = viewModelScope.launch { store.save(game, clock.millis()) }
    }

    private fun engineFor(game: GameState): SudokuEngine {
        val cached = engine
        if (cached != null && engineKey == game.spec.key) return cached
        return SudokuEngine(game.spec.puzzle).also {
            engine = it
            engineKey = game.spec.key
        }
    }

    private fun PlayUiState.withDerived(): PlayUiState {
        val game = game ?: return this
        val values = game.values.toIntArray()
        val engine = engineFor(game)
        return copy(
            conflicts = engine.conflicts(values),
            mistakes = if (settings.showMistakes && !game.solved) engine.mistakes(values) else emptySet(),
        )
    }

    private fun today(): Long = LocalDate.now(clock).toEpochDay()

    companion object {
        private const val SAVE_DEBOUNCE_MILLIS = 400L
        private const val KEEP_UNFINISHED_DAILY_DAYS = 60L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameViewModel(
                    store = container.gameStore,
                    settingsStore = container.settingsStore,
                    clock = container.clock,
                    ticker = container.ticker,
                )
            }
        }
    }
}
