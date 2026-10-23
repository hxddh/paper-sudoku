package com.titpea.papersudoku.page

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.titpea.papersudoku.AppContainer
import com.titpea.papersudoku.data.GameResult
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.data.UserSettings
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Step
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.GeneratingPuzzleSource
import com.titpea.papersudoku.game.model.Practice
import com.titpea.papersudoku.game.model.PuzzleKind
import com.titpea.papersudoku.game.model.PuzzleSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Stable identity of what the page pane shows, used by the list-detail navigator. */
object PageKeys {
    const val PRACTICE = "practice"
    fun daily(epochDay: Long): String = Catalog.dailyKey(epochDay)
    fun dayOf(key: String): Long? = Catalog.dayOf(key)
    fun of(game: GameState): String =
        if (game.spec.kind == PuzzleKind.Daily) game.spec.key else PRACTICE
}

/**
 * A Coach session: what it found and how far the player has asked it to go (1 look, 2 why,
 * 3 answer). When a placement needs eliminations first, Why walks through each of them in turn
 * ([step]), so everything the note says is true at the moment it says it.
 */
data class CoachUi(val hint: CoachHint?, val level: Int, val step: Int = 0) {
    /** What the note explains in order: each elimination needed, or else the placement itself. */
    val steps: List<Step>
        get() = (hint as? CoachHint.Next)?.let { next -> next.groundwork.ifEmpty { listOf(next.placement) } }.orEmpty()

    /** The step on the page now. */
    val lesson: Step? get() = steps.getOrNull(step)

    /** True once the note has reached the step the answer follows from. */
    val lastStep: Boolean get() = step >= steps.lastIndex
}

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
    /** Where practice stands after the practice page on the desk was solved. */
    val practiceAfter: PracticeOutcome? = null,
) {
    val boardLocked: Boolean get() = loading || game?.solved != false
}

/**
 * Practice after a solve: the level the next page is dealt at, how many clean pages remain before
 * the one after it (null at the top), and whether this solve moved the level up.
 */
data class PracticeOutcome(val level: Difficulty, val remaining: Int?, val leveledUp: Boolean)

sealed interface PageEvent {
    data class Tap(val index: Int) : PageEvent
    data class Select(val index: Int) : PageEvent
    data class Digit(val digit: Int) : PageEvent

    /** A digit typed on a keyboard: always written into the selection, never picked up. */
    data class TypeDigit(val digit: Int) : PageEvent
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
    data object OpenToday : PageEvent
    data object OpenPractice : PageEvent
    data object NextPractice : PageEvent

    /** The stamp has landed; rotating or reopening the page will not land it again. */
    data object StampLanded : PageEvent
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
    private val puzzles: PuzzleSource = GeneratingPuzzleSource(),
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** Called when today's daily is saved at a moment worth redrawing the widget for. */
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
    private var recordJob: Job? = null
    private var discardedKey: String? = null
    /** Deals the next practice page; outlives a load that is cancelled by opening another page. */
    private var practiceDeal: Deferred<GameState>? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings -> _state.update { it.copy(settings = settings) } }
        }
        // Pages saved by earlier versions just for being opened are not begun.
        viewModelScope.launch {
            try {
                store.forgetUntouchedDailies()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not tidy unbegun pages", e)
            }
        }
        // After process death, reopen the page that was on the desk.
        val open = savedState.get<String>(OPEN_PAGE)
        when {
            open != null && PageKeys.dayOf(open) != null -> openDaily(PageKeys.dayOf(open)!!)
            open != null && open.startsWith(CLASSIC_PREFIX) -> load { store.load(open) ?: openablePractice() }
            open == PageKeys.PRACTICE -> openPractice()
            else -> openDaily(today())
        }
    }

    fun onEvent(event: PageEvent) {
        val settings = _state.value.settings
        when (event) {
            is PageEvent.Tap -> mutate { it.tap(event.index, settings.autoClearNotes) }
            is PageEvent.Select -> mutate { it.select(event.index) }
            is PageEvent.Digit -> digit(event.digit)
            is PageEvent.TypeDigit -> mutate { g ->
                val selected = g.selected
                if (selected != null && g.isEditable(selected)) g.input(event.digit, settings.autoClearNotes) else g
            }
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
            PageEvent.OpenToday -> openDaily(today())
            PageEvent.OpenPractice -> openPractice()
            PageEvent.NextPractice -> nextPractice()
            PageEvent.StampLanded -> _state.update { it.copy(justSolved = false) }
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
                // A solve is dated now: an earlier day's page finished today points at today's.
                today = if (after.solved && !before.solved) today() else it.today,
            ).withDerived()
        }
        scheduleSave()
        feedback?.let { _effects.trySend(it) }
        // The one-line how-to is shown until the player writes a first digit.
        if (after.values != before.values && !current.settings.wroteFirstDigit) {
            viewModelScope.launch { updateSettings { it.copy(wroteFirstDigit = true) } }
        }
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
        val hint = coach.hint
        // Why goes through each elimination before the answer is offered.
        val next = if (coach.level == 2 && !coach.lastStep) coach.copy(step = coach.step + 1) else coach.copy(level = coach.level + 1)
        // Being shown which digit is wrong is not being given an answer: the page stays clean.
        val recorded = if (hint is CoachHint.Mistake) minOf(next.level, 2) else next.level
        _state.update { it.copy(game = game.coachReached(recorded), coach = next) }
        val lesson = next.lesson
        if (next.level == 2 && lesson != null) {
            viewModelScope.launch {
                updateSettings { it.copy(techniquesMet = it.techniquesMet + lesson.technique) }
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
            is CoachHint.Mistake -> mutate { it.eraseMistake(hint.index) }
            null -> Unit
        }
    }

    // endregion

    // region Clock

    private fun onForeground() {
        foreground = true
        val today = today()
        val previous = _state.value
        // Left open on a finished daily past midnight: the new day's page is the one to show. The
        // reference day is the day the page was chosen or solved, so it is not moved here; an
        // older page only being looked at stays where it is.
        val game = previous.game
        val solvedOn = game?.solvedAt?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate().toEpochDay() }
        if (today > previous.today && game != null && game.solved && game.spec.kind == PuzzleKind.Daily &&
            (game.spec.epochDay == previous.today || solvedOn == previous.today)
        ) {
            openDaily(today)
            return
        }
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
        // Leaving ends the landing: coming back shows the stamp already on the page.
        _state.update { it.copy(game = foldClock(it), runningSince = null, justSolved = false) }
        saveNow(notifyWidget = true)
    }

    private fun foldClock(state: PageUiState): GameState? {
        val game = state.game ?: return null
        val since = state.runningSince ?: return game
        return game.addElapsed(ticker() - since)
    }

    // endregion

    // region Pages

    private fun restart() {
        val current = _state.value
        val game = current.game ?: return
        if (current.loading || game.solved) return
        _state.update {
            it.copy(
                game = game.restart(),
                coach = null,
                runningSince = if (foreground) ticker() else null,
                justSolved = false,
                practiceAfter = null,
            ).withDerived()
        }
        saveNow()
    }

    private fun openDaily(epochDay: Long) {
        // Choosing a page is the reference point for the midnight turn in [onForeground].
        _state.update { it.copy(today = today()) }
        val current = _state.value.game
        if (current != null && current.spec.key == Catalog.dailyKey(epochDay)) {
            resumeCurrent()
            return
        }
        load {
            store.load(Catalog.dailyKey(epochDay)) ?: GameState.new(puzzles.daily(epochDay))
        }
    }

    private fun openPractice() {
        _state.update { it.copy(today = today()) }
        val current = _state.value.game
        if (current != null && current.spec.kind == PuzzleKind.Classic && !current.solved) {
            resumeCurrent()
            return
        }
        load { openablePractice() }
    }

    private suspend fun openablePractice(): GameState {
        // A page still being dealt is the one to continue: wait for it, so the page it replaces
        // (already set aside) is never offered again in the meantime.
        practiceDeal?.takeIf { it.isActive }?.let { runCatching { it.await() } }
        return store.latestUnsolvedPracticeKey()?.let { store.load(it) } ?: newPractice()
    }

    /**
     * The page asked for is the one already in memory: if another page was being prepared, stop
     * that and carry on with this one (its unsaved time and last moves are kept).
     */
    private fun resumeCurrent() {
        if (!_state.value.loading) return
        loadJob?.cancel()
        val game = _state.value.game
        if (game != null && game.spec.key == discardedKey) {
            // Taken back before the new page was dealt: keep it, and write it again in case the
            // deal had already dropped its row.
            practiceDeal?.cancel()
            discardedKey = null
            viewModelScope.launch { persist(game) }
        }
        _state.update {
            val game = it.game
            it.copy(
                loading = false,
                runningSince = if (foreground && game != null && !game.solved) ticker() else null,
            )
        }
    }

    private fun nextPractice() {
        // A page set aside unfinished is dropped, so the notebook never offers it again.
        saveJob?.cancel()
        _state.value.game?.takeIf { it.spec.kind == PuzzleKind.Classic && !it.solved }?.let { discardedKey = it.spec.key }
        // The deal runs on its own: if another page is opened meanwhile, the new practice page is
        // still saved and the old one still dropped, so Practice never offers the page set aside.
        val deal = viewModelScope.async {
            // A solve still being recorded counts towards the level of the next page.
            recordJob?.join()
            newPractice().also {
                store.save(it, clock.millis())
                store.discardUnsolvedPractice(except = it.spec.key)
            }
        }
        practiceDeal = deal
        load(keepPrevious = false) { deal.await() }
    }

    private suspend fun nextPracticeSpec() =
        Practice.level(store.cleanPractice.first()).let { level -> level to store.nextClassicNumber(level) }

    private suspend fun newPractice(): GameState {
        val (level, number) = nextPracticeSpec()
        return GameState.new(puzzles.classic(level, number))
    }

    private fun load(keepPrevious: Boolean = true, source: suspend () -> GameState) {
        loadJob?.cancel()
        // Stop the current page's clock now: preparing the next page is not time spent on it.
        _state.update { it.copy(game = foldClock(it), runningSince = null, loading = true, coach = null) }
        loadJob = viewModelScope.launch {
            show(source(), keepPrevious)
            prepareAhead()
        }
    }

    private suspend fun show(next: GameState, keepPrevious: Boolean) {
        val previous = _state.value.game
        if (keepPrevious && previous != null && previous.spec.key != next.spec.key) persist(previous)
        _state.update {
            it.copy(
                loading = false,
                game = next,
                coach = null,
                runningSince = if (foreground && !next.solved) ticker() else null,
                justSolved = false,
                practiceAfter = null,
            ).withDerived()
        }
        savedState[OPEN_PAGE] = next.spec.key
        persist(next)
    }

    /** Tomorrow's daily and the next practice page are generated now, so they open instantly. */
    private suspend fun prepareAhead() {
        try {
            puzzles.daily(today() + 1)
            val (level, number) = nextPracticeSpec()
            puzzles.classic(level, number)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not prepare the next pages", e)
        }
    }

    // endregion

    private fun record(game: GameState) {
        val spec = game.spec
        recordJob = viewModelScope.launch {
            store.saveSolved(
                game,
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
                clock.millis(),
            )
            notifyWidget(game)
            if (spec.kind == PuzzleKind.Classic) {
                val clean = store.cleanPractice.first()
                val level = Practice.level(clean)
                val outcome = PracticeOutcome(level, Practice.remainingToAdvance(clean), leveledUp = level > spec.difficulty)
                _state.update { if (it.game?.spec?.key == spec.key) it.copy(practiceAfter = outcome) else it }
            }
            // Every technique the page needed, not only its hardest.
            updateSettings { it.copy(techniquesMet = it.techniquesMet + spec.techniquesUsed) }
        }
    }

    private suspend fun persist(game: GameState, notifyWidget: Boolean = false) {
        // A practice page set aside is never written back (a late save would revive it).
        if (game.spec.key == discardedKey && !game.solved) return
        // A daily only looked at is not begun: nothing is kept, so the notebook and the widget
        // still offer it as a fresh page (and a page undone back to blank is fresh again).
        if (game.spec.kind == PuzzleKind.Daily && game.untouched) store.forget(game.spec.key) else store.save(game, clock.millis())
        if (notifyWidget) notifyWidget(game)
    }

    /** The widget shows today's daily; it is redrawn when that page is solved or put away. */
    private fun notifyWidget(game: GameState) {
        if (game.spec.kind == PuzzleKind.Daily && game.spec.epochDay == today()) onDailySaved()
    }

    private suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        try {
            settingsStore.update(transform)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not update settings", e)
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            foldClock(_state.value)?.let { persist(it) }
        }
    }

    private fun saveNow(notifyWidget: Boolean = false) {
        saveJob?.cancel()
        val game = _state.value.game ?: return
        saveJob = viewModelScope.launch { persist(game, notifyWidget) }
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
        private const val OPEN_PAGE = "open_page"
        private const val CLASSIC_PREFIX = "classic-"
        private const val TAG = "PageViewModel"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PageViewModel(
                    store = container.gameStore,
                    settingsStore = container.settingsStore,
                    clock = container.clock,
                    ticker = container.ticker,
                    puzzles = container.puzzles,
                    savedState = createSavedStateHandle(),
                    onDailySaved = container.onDailySaved,
                )
            }
        }
    }
}
