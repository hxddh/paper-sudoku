package com.titpea.papersudoku.page

import android.os.Build
import android.os.Trace
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.titpea.papersudoku.AppContainer
import com.titpea.papersudoku.data.FirstHint
import com.titpea.papersudoku.data.GameResult
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.data.UserSettings
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.DigitMask
import com.titpea.papersudoku.engine.Step
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.GeneratingPuzzleSource
import com.titpea.papersudoku.game.model.Practice
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.titpea.papersudoku.game.model.Review
import com.titpea.papersudoku.game.model.Path
import com.titpea.papersudoku.game.model.PuzzleKind
import com.titpea.papersudoku.game.model.PuzzleSource
import com.titpea.papersudoku.game.model.PuzzleSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
    /** Practice is one place whatever page it is on; every other page is itself. */
    fun of(game: GameState): String =
        if (game.spec.kind == PuzzleKind.Classic) PRACTICE else game.spec.key
}

/** A page to open: what the notebook, the widget or a restored session asks for. */
sealed interface PageTarget {
    data class Daily(val epochDay: Long) : PageTarget

    /** The practice page to continue, or the next one on the path. */
    data object Practice : PageTarget

    /** A page by its saved key: a page typed in, a finished page in the notebook, a restored one. */
    data class Saved(val key: String) : PageTarget

    /** A page typed in just now (kept once it opens). */
    data class New(val spec: PuzzleSpec) : PageTarget

    /** True when [game] is this page, so asking for it again carries on with it. */
    fun isShowing(game: GameState): Boolean = when (this) {
        is Daily -> game.spec.key == Catalog.dailyKey(epochDay)
        Practice -> game.spec.kind == PuzzleKind.Classic && !game.solved
        is Saved -> game.spec.key == key
        is New -> game.spec.key == spec.key
    }

    companion object {
        /** Every practice page's key starts with this. */
        const val PRACTICE_KEYS = "classic-"

        /** What a key saved for the open page asks for after process death. */
        fun fromKey(key: String): PageTarget = if (key == PageKeys.PRACTICE) Practice else Saved(key)
    }
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
    /** An earlier day's daily was just finished and today's page is still unstamped. */
    val todayWaiting: Boolean = false,
    /** The hardest technique the player wrote with themselves on the page just solved (10.0). */
    val foundYourself: Technique? = null,
    /**
     * A digit pressed while a printed digit is selected: the board shows where that digit is, and
     * nothing is picked up (11.0). Gone with the next thing the player does.
     */
    val peek: Int? = null,
    /** A first-time hint to show while what it is about lasts (11.0); each is shown once. */
    val firstHint: FirstHint? = null,
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
    data object FillCandidates : PageEvent
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

    /** A page the player typed in, checked and graded. */
    data class OpenImported(val spec: PuzzleSpec) : PageEvent

    /** A saved page by key (a page the player typed in, reopened from the notebook). */
    data class OpenSaved(val key: String) : PageEvent

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
 * - Digits: with an editable cell selected a digit is written; with no cell selected it is picked
 *   up and every tap on the board writes it (digit-first), until it is put down again; with a
 *   printed digit selected it only shows where that digit is (11.0). Tapping the selected cell
 *   again lets go of it.
 * - The Coach's note stays on the page with the keys: its marks last until its step is done or
 *   the page changes elsewhere (11.0).
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
    /** Where CPU work off the page runs: the Coach's search, reading a page back. */
    private val compute: CoroutineDispatcher = Dispatchers.Default,
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
    private var coachJob: Job? = null
    private var openings = 0
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
        open(savedState.get<String>(OPEN_PAGE)?.let(PageTarget::fromKey) ?: PageTarget.Daily(today()))
    }

    fun onEvent(event: PageEvent) {
        // Showing where a digit is lasts until the next thing the player does.
        val peeked = _state.value.peek
        if (peeked != null) _state.update { it.copy(peek = null) }
        when (event) {
            is PageEvent.Tap -> mutate { g ->
                // Tapping the selected cell again lets go of it, so a digit can be picked up.
                if (g.lockedDigit == null && g.selected == event.index) g.select(null) else g.tap(event.index, TIDY_NOTES)
            }
            is PageEvent.Select -> mutate { it.select(event.index) }
            is PageEvent.Digit -> digit(event.digit, peeked)
            is PageEvent.TypeDigit -> mutate { g ->
                val selected = g.selected
                if (selected != null && g.isEditable(selected)) g.input(event.digit, TIDY_NOTES) else g
            }
            PageEvent.ClearLock -> mutate(PageEffect.Toggled) { g -> g.lockedDigit?.let { g.toggleLock(it) } ?: g }
            PageEvent.Erase -> mutate { it.erase() }
            PageEvent.Undo -> mutate { it.undo() }
            PageEvent.ToggleNotes -> {
                mutate(PageEffect.Toggled) { it.toggleNotesMode() }
                if (_state.value.game?.notesMode == true) firstTime(FirstHint.Pencil)
            }
            PageEvent.FillCandidates -> mutate { if (it.size == 9) it.pencilInCandidates() else it }
            PageEvent.Coach -> openCoach()
            PageEvent.CoachDeeper -> deepenCoach()
            PageEvent.CoachApply -> applyCoach()
            PageEvent.DismissCoach -> _state.update { it.copy(coach = null) }
            PageEvent.Foreground -> onForeground()
            PageEvent.Background -> onBackground()
            PageEvent.Restart -> restart()
            is PageEvent.OpenDaily -> open(PageTarget.Daily(event.epochDay))
            PageEvent.OpenToday -> open(PageTarget.Daily(today()))
            PageEvent.OpenPractice -> open(PageTarget.Practice)
            PageEvent.NextPractice -> nextPractice()
            is PageEvent.OpenImported -> open(PageTarget.New(event.spec))
            is PageEvent.OpenSaved -> open(PageTarget.Saved(event.key))
            PageEvent.StampLanded -> _state.update { it.copy(justSolved = false) }
        }
        settleFirstHint()
    }

    // region Board

    /**
     * Write into the selected cell; with no cell selected, pick the digit up (digit-first); with a
     * printed digit selected, show where [digit] is ([peeked] is the digit shown until now: the
     * same key again hides it). A picked-up digit is put down by tapping it again; another digit
     * swaps it.
     */
    private fun digit(digit: Int, peeked: Int?) {
        val current = _state.value
        val game = current.game ?: return
        val selected = game.selected
        when {
            // Holding a digit: tapping it again puts it down, another digit swaps it.
            game.lockedDigit != null -> mutate(PageEffect.Toggled) { it.toggleLock(digit) }
            selected == null -> {
                mutate(PageEffect.Toggled) { it.toggleLock(digit) }
                if (_state.value.game?.lockedDigit != null) firstTime(FirstHint.PickUp)
            }
            game.isEditable(selected) -> mutate { it.input(digit, TIDY_NOTES) }
            // A printed digit selected: where this digit is, and no mode to leave afterwards.
            !current.boardLocked && digit in 1..game.size ->
                _state.update { it.copy(peek = digit.takeIf { d -> d != peeked }) }
        }
    }

    /** Shows [hint] now if it was never shown, and remembers that it was. */
    private fun firstTime(hint: FirstHint) {
        if (hint in _state.value.settings.hintsSeen) return
        _state.update { it.copy(firstHint = hint) }
        viewModelScope.launch { updateSettings { it.copy(hintsSeen = it.hintsSeen + hint) } }
    }

    /** A first-time hint lasts as long as what it is about: the pencil, the digit in hand, the note. */
    private fun settleFirstHint() {
        _state.update { s ->
            val live = when (s.firstHint) {
                null -> true
                FirstHint.Pencil -> s.game?.notesMode == true
                FirstHint.PickUp -> s.game?.lockedDigit != null
                FirstHint.Coach -> s.coach != null
            }
            if (live) s else s.copy(firstHint = null)
        }
    }

    private fun mutate(effect: PageEffect? = null, block: (GameState) -> GameState) {
        val current = _state.value
        val before = current.game ?: return
        if (current.boardLocked) return
        var after = block(before)
        if (after == before) return

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
                coach = it.coach?.takeIf { coach -> coach.outlives(before, after) },
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

    /**
     * Whether the note (and its marks) stays through a change from [before] to [after]: it stays
     * on the page with the keys until its step is done or the page changes elsewhere. A digit
     * written or erased anywhere ends it (the right digit in its cell is the step done, by itself);
     * a pencil mark ends it only when the note was about that mark, or had nothing to say.
     */
    private fun CoachUi.outlives(before: GameState, after: GameState): Boolean {
        if (after.values != before.values) return false
        if (after.notes == before.notes) return true
        return when (val about = hint) {
            is CoachHint.MissingMark -> !DigitMask.contains(after.notes[about.index], about.digit)
            null -> false
            else -> true
        }
    }

    /**
     * The Coach reads the page as the player has it: their digits, their pencil marks and where
     * they are. The search runs off the main thread; if the page changes meanwhile, it is dropped.
     * Asked again with the note still open, it reads the page again: the same step keeps the note
     * as far as it went, another starts a new note.
     */
    private fun openCoach() {
        val current = _state.value
        val game = current.game ?: return
        if (current.boardLocked) return
        val engine = engineFor(game)
        coachJob?.cancel()
        coachJob = viewModelScope.launch {
            val hint = withContext(compute) { engine.hint(game.values.toIntArray(), game.notes.toIntArray(), game.selected) }
            val now = _state.value
            val live = now.game ?: return@launch
            if (now.boardLocked || live.values != game.values || live.notes != game.notes) return@launch
            val open = now.coach
            if (open != null && open.hint == hint) return@launch
            val updated = if (hint != null) live.coachAsked() else live
            _state.update { it.copy(game = updated, coach = CoachUi(hint, level = 1)) }
            if (hint != null) firstTime(FirstHint.Coach)
            scheduleSave()
        }
    }

    private fun deepenCoach() {
        val current = _state.value
        val coach = current.coach ?: return
        val game = current.game ?: return
        if (coach.hint == null || coach.level >= 3) return
        val hint = coach.hint
        // Why goes through each elimination before the answer is offered.
        val next = if (coach.level == 2 && !coach.lastStep) coach.copy(step = coach.step + 1) else coach.copy(level = coach.level + 1)
        // Being shown which digit is wrong, or which mark is missing, is not being given an
        // answer: the page stays clean.
        val recorded = if (hint is CoachHint.Next) next.level else minOf(next.level, 2)
        // How far the Coach went for the technique it is teaching, for what the player knows.
        val technique = (hint as? CoachHint.Next)?.let { next.lesson?.technique ?: it.technique }
        _state.update { it.copy(game = game.coachReached(recorded, technique), coach = next) }
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
        when (val hint = coach.hint) {
            is CoachHint.Next -> mutate { it.placeHint(hint.placement.index, hint.placement.digit, TIDY_NOTES) }
            is CoachHint.Mistake -> mutate { it.eraseMistake(hint.index) }
            is CoachHint.MissingMark -> mutate { it.restoreMark(hint.index, hint.digit) }
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
        // A daily of that day left blank turns over too: nothing on it is lost.
        val finished = game != null && game.solved && (game.spec.epochDay == previous.today || solvedOn == previous.today)
        val blank = game != null && game.untouched && game.spec.epochDay == previous.today
        if (today > previous.today && game != null && game.spec.kind == PuzzleKind.Daily && (finished || blank)) {
            open(PageTarget.Daily(today))
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

    /**
     * The one way a page is opened: the page asked for is the one in memory (carry on with it),
     * or it is resolved (its save, else a new page) while the current one is put away.
     */
    private fun open(target: PageTarget) {
        // Choosing a page is the reference point for the midnight turn in [onForeground].
        _state.update { it.copy(today = today(), peek = null, firstHint = null) }
        val current = _state.value.game
        if (current != null && target.isShowing(current)) {
            resumeCurrent()
            return
        }
        load { resolve(target) }
    }

    private suspend fun resolve(target: PageTarget): GameState = when (target) {
        is PageTarget.Daily -> dailyFor(target.epochDay)
        PageTarget.Practice -> openablePractice()
        is PageTarget.New -> store.load(target.spec.key) ?: GameState.new(target.spec)
        // A daily never begun is not kept, so after a night away its key finds nothing: the page
        // of the new day is the one to open. A practice page dropped meanwhile gives way to the
        // practice page to continue; anything else lives only in its save.
        is PageTarget.Saved -> store.load(target.key) ?: when {
            target.key.startsWith(PageTarget.PRACTICE_KEYS) -> openablePractice()
            else -> dailyFor(today())
        }
    }

    private suspend fun dailyFor(epochDay: Long): GameState =
        store.load(Catalog.dailyKey(epochDay)) ?: GameState.new(puzzles.daily(epochDay))

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

    /** The next practice page on the path: its level, number and the technique it is dealt to need. */
    private suspend fun nextPracticeSpec(): Triple<Difficulty, Int, Technique?> {
        val level = Practice.level(store.cleanPractice.first())
        val number = store.nextClassicNumber(level)
        val learner = settingsStore.settings.first().learner
        return Triple(level, number, Path.target(learner, level, number))
    }

    private suspend fun newPractice(): GameState {
        val (level, number, target) = nextPracticeSpec()
        return GameState.new(puzzles.practice(level, number, target))
    }

    private fun load(keepPrevious: Boolean = true, source: suspend () -> GameState) {
        loadJob?.cancel()
        // Stop the current page's clock now: preparing the next page is not time spent on it.
        _state.update { it.copy(game = foldClock(it), runningSince = null, loading = true, coach = null) }
        loadJob = viewModelScope.launch {
            val cookie = ++openings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.beginAsyncSection("ps.openPage", cookie)
            val next = try {
                source()
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.endAsyncSection("ps.openPage", cookie)
            }
            show(next, keepPrevious)
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
                todayWaiting = false,
                foundYourself = null,
            ).withDerived()
        }
        savedState[OPEN_PAGE] = next.spec.key
        persist(next)
    }

    /** Tomorrow's daily and the next practice page are generated now, so they open instantly. */
    private suspend fun prepareAhead() {
        try {
            puzzles.daily(today() + 1)
            val (level, number, target) = nextPracticeSpec()
            puzzles.practice(level, number, target)
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
            val first = store.saveSolved(
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
            val day = spec.epochDay
            if (spec.kind == PuzzleKind.Daily && day != null && day < today()) {
                val waiting = store.load(Catalog.dailyKey(today()))?.solved != true
                _state.update { if (it.game?.spec?.key == spec.key) it.copy(todayWaiting = waiting) else it }
            }
            if (spec.kind == PuzzleKind.Classic) {
                val clean = store.cleanPractice.first()
                val level = Practice.level(clean)
                val outcome = PracticeOutcome(level, Practice.remainingToAdvance(clean), leveledUp = level > spec.difficulty)
                _state.update { if (it.game?.spec?.key == spec.key) it.copy(practiceAfter = outcome) else it }
            }
            // Every technique the page needed has been met. What the player found themselves is
            // read from the page itself: each digit they wrote, judged by what it needed then.
            // A page typed in that the Coach can't finish has no techniques on record.
            if (spec.beyondCoach) return@launch
            val steps = if (first) withContext(compute) { Review.of(spec, game.moves, game.movesFrom) } else emptyList()
            var found: Technique? = null
            updateSettings { settings ->
                val learner = if (first) settings.learner.record(steps, game.coach) else settings.learner
                found = Technique.entries.filter { it.tier >= 2 && learner.found(it) > settings.learner.found(it) }.maxByOrNull { it.tier }
                settings.copy(techniquesMet = settings.techniquesMet + spec.techniquesUsed, learner = learner)
            }
            found?.let { technique -> _state.update { if (it.game?.spec?.key == spec.key) it.copy(foundYourself = technique) else it } }
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

        /** Writing a digit always tidies it out of the pencil marks it sees (a setting before 11.0). */
        private const val TIDY_NOTES = true
        private const val OPEN_PAGE = "open_page"
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
