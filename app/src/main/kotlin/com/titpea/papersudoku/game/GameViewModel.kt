package com.titpea.papersudoku.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.titpea.papersudoku.AppContainer
import com.titpea.papersudoku.data.GameResult
import com.titpea.papersudoku.data.Puzzles
import com.titpea.papersudoku.data.StatsRepository
import com.titpea.papersudoku.engine.Hint
import com.titpea.papersudoku.engine.SudokuEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CellUi(
    val value: Int,
    val given: Boolean,
    val notes: Set<Int>,
)

data class HintUi(val index: Int, val digit: Int)

enum class Sheet { None, Keypad, Coach }

data class GameUiState(
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val cells: List<CellUi>,
    val selected: Int?,
    val notesMode: Boolean,
    val sheet: Sheet,
    val hint: HintUi?,
    val conflicts: Set<Int>,
    val solved: Boolean,
) {
    val isMini: Boolean get() = size == 6
}

sealed interface GameEvent {
    data class SelectCell(val index: Int) : GameEvent
    data class Digit(val digit: Int) : GameEvent
    data object Undo : GameEvent
    data object Erase : GameEvent
    data object ToggleNotes : GameEvent
    data object Coach : GameEvent
    data object PlaceHint : GameEvent
    data object DismissSheet : GameEvent
    data object NewGame : GameEvent
    data object ToggleMode : GameEvent
}

/**
 * State holder for the game.
 *
 * - `SavedStateHandle` persists the in-progress board, notes, selection, mode, and counters across
 *   process death / rotation.
 * - `StatsRepository` (Room) records completed games exactly once.
 * - `GameViewModel.factory` wires both through the app container; tests pass a fake repository.
 */
class GameViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private var puzzle = Puzzles.mini6
    private var engine = SudokuEngine(puzzle)
    private var values = puzzle.givens.toIntArray()
    private var notes = MutableList(puzzle.givens.size) { mutableSetOf<Int>() }
    private var selected: Int? = null
    private var notesMode = false
    private var sheet = Sheet.Keypad
    private var hint: Hint? = null
    private val history = ArrayDeque<Snapshot>()

    private var startedAt: Long = savedStateHandle.get<Long>(KEY_STARTED_AT) ?: System.currentTimeMillis()
    private var hintsUsed: Int = savedStateHandle.get<Int>(KEY_HINTS) ?: 0
    private var recorded: Boolean = savedStateHandle.get<Boolean>(KEY_RECORDED) ?: false

    private val _uiState = MutableStateFlow(buildState())
    val uiState = _uiState.asStateFlow()

    init {
        if (!restore()) {
            resetBoard()
        }
    }

    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.SelectCell -> selectCell(event.index)
            is GameEvent.Digit -> enterDigit(event.digit)
            GameEvent.Undo -> undo()
            GameEvent.Erase -> erase()
            GameEvent.ToggleNotes -> {
                notesMode = !notesMode
                publish()
            }
            GameEvent.Coach -> coach()
            GameEvent.PlaceHint -> placeHint()
            GameEvent.DismissSheet -> dismissSheet()
            GameEvent.NewGame -> resetBoard()
            GameEvent.ToggleMode -> toggleMode()
        }
    }

    private fun selectCell(index: Int) {
        if (index !in values.indices) return
        selected = index
        hint = null
        sheet = if (puzzle.givens[index] != 0) {
            if (puzzle.size == 6) Sheet.Keypad else Sheet.None
        } else {
            Sheet.Keypad
        }
        publish()
    }

    private fun enterDigit(digit: Int) {
        if (digit !in 1..puzzle.size) return
        val index = selected ?: return
        if (puzzle.givens[index] != 0) return

        if (notesMode) {
            if (values[index] != 0) return
            pushHistory()
            val candidates = notes[index]
            if (!candidates.add(digit)) candidates.remove(digit)
        } else {
            pushHistory()
            values[index] = digit
            notes[index].clear()
        }
        hint = null
        sheet = Sheet.Keypad
        publish()
    }

    private fun undo() {
        val snapshot = history.removeLastOrNull() ?: return
        values = snapshot.values.copyOf()
        notes = snapshot.notes.map { it.toMutableSet() }.toMutableList()
        selected = snapshot.selected
        hint = null
        sheet = if (puzzle.size == 6) Sheet.Keypad else Sheet.None
        publish()
    }

    private fun erase() {
        val index = selected ?: return
        if (puzzle.givens[index] != 0) return
        if (values[index] == 0 && notes[index].isEmpty()) return
        pushHistory()
        values[index] = 0
        notes[index].clear()
        hint = null
        publish()
    }

    private fun coach() {
        hint = engine.findNakedSingle(values)
        if (hint != null) hintsUsed += 1
        selected = null
        sheet = Sheet.Coach
        publish()
    }

    private fun placeHint() {
        val current = hint ?: return
        pushHistory()
        values[current.index] = current.digit
        notes[current.index].clear()
        hint = null
        selected = null
        sheet = Sheet.Keypad
        publish()
    }

    private fun dismissSheet() {
        hint = null
        selected = null
        sheet = if (puzzle.size == 6) Sheet.Keypad else Sheet.None
        publish()
    }

    private fun resetBoard() {
        values = puzzle.givens.toIntArray()
        notes = MutableList(puzzle.givens.size) { mutableSetOf() }
        selected = null
        notesMode = false
        hint = null
        history.clear()
        sheet = if (puzzle.size == 6) Sheet.Keypad else Sheet.None
        startedAt = System.currentTimeMillis()
        hintsUsed = 0
        recorded = false
        publish()
    }

    private fun toggleMode() {
        puzzle = if (puzzle.size == 6) Puzzles.classic9 else Puzzles.mini6
        engine = SudokuEngine(puzzle)
        resetBoard()
    }

    private fun pushHistory() {
        history.addLast(
            Snapshot(
                values = values.copyOf(),
                notes = notes.map { it.toSet() },
                selected = selected,
            ),
        )
        if (history.size > 100) history.removeFirst()
    }

    private fun publish() {
        val next = buildState()
        _uiState.value = next
        persist()
        maybeRecord(next)
    }

    private fun buildState(): GameUiState {
        val cells = values.indices.map { index ->
            CellUi(
                value = values[index],
                given = puzzle.givens[index] != 0,
                notes = notes[index].toSet(),
            )
        }
        return GameUiState(
            size = puzzle.size,
            boxRows = puzzle.boxRows,
            boxCols = puzzle.boxCols,
            cells = cells,
            selected = selected,
            notesMode = notesMode,
            sheet = sheet,
            hint = hint?.let { HintUi(it.index, it.digit) },
            conflicts = engine.conflicts(values),
            solved = engine.isSolved(values),
        )
    }

    private fun persist() {
        savedStateHandle[KEY_MODE] = if (puzzle.size == 6) MODE_MINI else MODE_CLASSIC
        savedStateHandle[KEY_VALUES] = ArrayList(values.toList())
        savedStateHandle[KEY_NOTES] = ArrayList(notes.map { it.joinToString(",") })
        savedStateHandle[KEY_SELECTED] = selected
        savedStateHandle[KEY_NOTES_MODE] = notesMode
        savedStateHandle[KEY_HINTS] = hintsUsed
        savedStateHandle[KEY_STARTED_AT] = startedAt
        savedStateHandle[KEY_RECORDED] = recorded
    }

    private fun maybeRecord(state: GameUiState) {
        if (!state.solved || recorded) return
        recorded = true
        savedStateHandle[KEY_RECORDED] = true
        val mode = if (puzzle.size == 6) MODE_MINI else MODE_CLASSIC
        val duration = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
        viewModelScope.launch {
            statsRepository.record(
                GameResult(
                    mode = mode,
                    durationSeconds = duration,
                    hints = hintsUsed,
                    completedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun restore(): Boolean {
        val mode = savedStateHandle.get<String>(KEY_MODE) ?: return false
        val savedValues = savedStateHandle.get<ArrayList<Int>>(KEY_VALUES) ?: return false

        puzzle = if (mode == MODE_CLASSIC) Puzzles.classic9 else Puzzles.mini6
        if (savedValues.size != puzzle.givens.size) return false

        engine = SudokuEngine(puzzle)
        values = savedValues.toIntArray()
        val savedNotes = savedStateHandle.get<ArrayList<String>>(KEY_NOTES)
        notes = MutableList(puzzle.givens.size) { index ->
            savedNotes
                ?.getOrNull(index)
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?.toMutableSet()
                ?: mutableSetOf()
        }
        selected = savedStateHandle.get<Int>(KEY_SELECTED)?.takeIf { it in values.indices }
        notesMode = savedStateHandle.get<Boolean>(KEY_NOTES_MODE) ?: false
        startedAt = savedStateHandle.get<Long>(KEY_STARTED_AT) ?: System.currentTimeMillis()
        hintsUsed = savedStateHandle.get<Int>(KEY_HINTS) ?: 0
        recorded = savedStateHandle.get<Boolean>(KEY_RECORDED) ?: false
        sheet = if (puzzle.size == 6) Sheet.Keypad else Sheet.None
        hint = null
        history.clear()
        publish()
        return true
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_VALUES = "values"
        private const val KEY_NOTES = "notes"
        private const val KEY_SELECTED = "selected"
        private const val KEY_NOTES_MODE = "notes_mode"
        private const val KEY_HINTS = "hints"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_RECORDED = "recorded"

        private const val MODE_MINI = "mini"
        private const val MODE_CLASSIC = "classic"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    statsRepository = container.statsRepository,
                )
            }
        }
    }

    private data class Snapshot(
        val values: IntArray,
        val notes: List<Set<Int>>,
        val selected: Int?,
    )
}
