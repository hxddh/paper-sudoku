package com.titpea.papersudoku.notebook

import com.titpea.papersudoku.data.GameResult
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import com.titpea.papersudoku.page.FakeGameStore
import com.titpea.papersudoku.page.FakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 10, 21).toEpochDay()
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneOffset.UTC)
    private val store = FakeGameStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(firstDay: Long) = NotebookViewModel(store, FakeSettings(), clock, firstDay)

    private suspend fun NotebookViewModel.loaded(): NotebookUiState = state.first { it.loaded }

    @Test
    fun `a new notebook has no wall of blank days`() = runTest(dispatcher) {
        val state = viewModel(firstDay = today).loaded()
        assertTrue(state.pastDays.isEmpty())
        assertEquals(today, state.todayPage?.epochDay)
    }

    @Test
    fun `the notebook begins on the install day`() = runTest(dispatcher) {
        val state = viewModel(firstDay = today - 3).loaded()
        assertEquals(listOf(today - 1, today - 2, today - 3), state.pastDays)
    }

    @Test
    fun `a notebook older than a year still reaches its first day and pages`() = runTest(dispatcher) {
        store.save(GameState.new(Catalog.daily(today - 500)), now = 1)
        val state = viewModel(firstDay = today - 400).loaded()
        assertEquals(500, state.pastDays.size)
        assertEquals(today - 500, state.pastDays.last())
        assertFalse(state.pages.getValue(today - 500).saved == null)
    }

    @Test
    fun `pages from before the install (a restored backup) open the notebook`() = runTest(dispatcher) {
        store.save(GameState.new(Catalog.daily(today - 10)), now = 1)
        val state = viewModel(firstDay = today).loaded()
        assertEquals(today - 10, state.pastDays.last())
        assertFalse(state.pages.getValue(today - 10).saved == null)
    }

    @Test
    fun `practice pages count as stamped`() = runTest(dispatcher) {
        val practice = GameState.new(Catalog.classic(Difficulty.Gentle, 0))
        store.saveSolved(practice, GameResult(practice.spec.key, PuzzleKind.Classic, Difficulty.Gentle, 60, 0, 0, 1, null), now = 1)
        val vm = viewModel(firstDay = today)
        assertEquals(1, vm.state.first { it.loaded && it.stamped == 1 }.stamped)
    }
}
