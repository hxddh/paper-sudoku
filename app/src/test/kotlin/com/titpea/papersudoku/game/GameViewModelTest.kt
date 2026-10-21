package com.titpea.papersudoku.game

import androidx.lifecycle.SavedStateHandle
import com.titpea.papersudoku.data.GameResult
import com.titpea.papersudoku.data.Puzzles
import com.titpea.papersudoku.data.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val stats = FakeStatsRepository()
    private val mainDispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        stats.results.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        GameViewModel(handle, stats, clock)

    @Test
    fun `starts on the mini board with the keypad visible`() {
        val state = viewModel().uiState.value

        assertEquals(6, state.size)
        assertEquals(Sheet.Keypad, state.sheet)
        assertTrue(state.isMini)
        assertNull(state.selected)
    }

    @Test
    fun `selecting an empty cell and entering a digit updates state`() {
        val viewModel = viewModel()

        viewModel.onEvent(GameEvent.SelectCell(0))
        viewModel.onEvent(GameEvent.Digit(1))

        val state = viewModel.uiState.value
        assertEquals(0, state.selected)
        assertEquals(1, state.cells[0].value)
        assertFalse(state.cells[0].given)
    }

    @Test
    fun `notes mode toggles a candidate and undo restores it`() {
        val viewModel = viewModel()

        viewModel.onEvent(GameEvent.SelectCell(2))
        viewModel.onEvent(GameEvent.ToggleNotes)
        viewModel.onEvent(GameEvent.Digit(3))

        val noted = viewModel.uiState.value.cells[2]
        assertEquals(0, noted.value)
        assertEquals(setOf(3), noted.notes)

        viewModel.onEvent(GameEvent.Undo)
        assertTrue(viewModel.uiState.value.cells[2].notes.isEmpty())
    }

    @Test
    fun `coach exposes a naked single and place fills it`() {
        val viewModel = viewModel()

        viewModel.onEvent(GameEvent.Coach)
        val hint = viewModel.uiState.value.hint
        assertNotNull(hint)
        assertEquals(Sheet.Coach, viewModel.uiState.value.sheet)

        viewModel.onEvent(GameEvent.PlaceHint)
        val state = viewModel.uiState.value
        assertEquals(hint!!.digit, state.cells[hint.index].value)
        assertEquals(Sheet.Keypad, state.sheet)
        assertNull(state.hint)
    }

    @Test
    fun `toggling mode switches to a fresh classic board`() {
        val viewModel = viewModel()

        viewModel.onEvent(GameEvent.ToggleMode)

        val state = viewModel.uiState.value
        assertEquals(9, state.size)
        assertEquals(Sheet.None, state.sheet)
        assertNull(state.selected)
        assertFalse(state.isMini)
    }

    @Test
    fun `daily mini rotates with the date`() {
        val first = Puzzles.dailyMiniFor(LocalDate.ofEpochDay(0))
        val second = Puzzles.dailyMiniFor(LocalDate.ofEpochDay(1))

        assertEquals(Puzzles.mini6, first)
        assertNotEquals(first, second)
    }

    @Test
    fun `new classic game advances to another puzzle`() {
        val viewModel = viewModel()
        viewModel.onEvent(GameEvent.ToggleMode)
        val first = viewModel.uiState.value.cells.map { if (it.given) it.value else 0 }

        viewModel.onEvent(GameEvent.NewGame)
        val second = viewModel.uiState.value.cells.map { if (it.given) it.value else 0 }

        assertEquals(9, viewModel.uiState.value.size)
        assertNotEquals(first, second)
    }

    @Test
    fun `state survives a new view model with the same saved state handle`() {
        val handle = SavedStateHandle()
        val first = viewModel(handle)

        first.onEvent(GameEvent.SelectCell(0))
        first.onEvent(GameEvent.Digit(1))

        val second = viewModel(handle)
        assertEquals(1, second.uiState.value.cells[0].value)
        assertEquals(0, second.uiState.value.selected)
    }

    @Test
    fun `completing the board records one result and does not duplicate on restore`() = runTest(mainDispatcher) {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        val puzzle = Puzzles.mini6

        puzzle.givens.forEachIndexed { index, given ->
            if (given == 0) {
                viewModel.onEvent(GameEvent.SelectCell(index))
                viewModel.onEvent(GameEvent.Digit(puzzle.solution[index]))
            }
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.solved)
        assertEquals(1, stats.results.size)

        val restored = viewModel(handle)
        advanceUntilIdle()

        assertTrue(restored.uiState.value.solved)
        assertEquals(1, stats.results.size)
    }
}

private class FakeStatsRepository : StatsRepository {
    override val completedCount: Flow<Int> = MutableStateFlow(0)
    override val bestSeconds: Flow<Long?> = MutableStateFlow(null)
    val results = mutableListOf<GameResult>()

    override suspend fun record(result: GameResult) {
        results += result
    }
}
