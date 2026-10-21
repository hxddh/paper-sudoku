package com.titpea.papersudoku.game

import com.titpea.papersudoku.data.UserSettings
import com.titpea.papersudoku.engine.CoachHint
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 10, 21) // a Wednesday: Gentle daily
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneOffset.UTC)
    private var now = 1_000L
    private val store = FakeGameStore()
    private val settings = FakeSettings()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = GameViewModel(store, settings, clock, ticker = { now }, generatorDispatcher = dispatcher)

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private fun GameViewModel.game(): GameState = checkNotNull(state.value.game)

    private fun GameViewModel.solveFully() {
        val spec = game().spec
        for (i in spec.givens.indices) {
            if (spec.givens[i] == 0) {
                onEvent(PlayEvent.Tap(i))
                onEvent(PlayEvent.Digit(spec.solution[i]))
            }
        }
    }

    @Test
    fun `opens today's daily and remembers it`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val game = vm.game()
        assertEquals(Catalog.dailyKey(today.toEpochDay()), game.spec.key)
        assertEquals(Difficulty.Gentle, game.spec.difficulty)
        assertFalse(vm.state.value.loading)
        assertEquals(game.spec.key, settings.flow.value.activeKey)
        assertTrue(store.saved.containsKey(game.spec.key))
    }

    @Test
    fun `progress is autosaved and restored by a new view model`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PlayEvent.Tap(empty))
        vm.onEvent(PlayEvent.Digit(spec.solution[empty]))
        advanceUntilIdle()

        val restored = viewModel()
        advanceUntilIdle()
        assertEquals(spec.solution[empty], restored.game().values[empty])
    }

    @Test
    fun `the clock runs only in the foreground and when not paused`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PlayEvent.Foreground)
        assertEquals(1_000L, vm.state.value.runningSince)
        now = 6_000
        vm.onEvent(PlayEvent.Background)
        assertNull(vm.state.value.runningSince)
        assertEquals(5_000L, vm.game().elapsedMillis)

        now = 60_000 // time away does not count
        vm.onEvent(PlayEvent.Foreground)
        now = 61_000
        vm.onEvent(PlayEvent.TogglePause)
        assertTrue(vm.state.value.userPaused)
        assertEquals(6_000L, vm.game().elapsedMillis)

        // Paused: the board ignores input and the clock stays stopped across foregrounding.
        val empty = vm.game().spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PlayEvent.Tap(empty))
        assertNull(vm.game().selected)
        vm.onEvent(PlayEvent.Background)
        vm.onEvent(PlayEvent.Foreground)
        assertNull(vm.state.value.runningSince)
    }

    @Test
    fun `solving records exactly once, even after a restart`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PlayEvent.Foreground)
        now = 91_000
        vm.solveFully()
        advanceUntilIdle()
        assertTrue(vm.game().solved)
        assertTrue(vm.state.value.justSolved)
        assertEquals(1, store.results.size)
        assertEquals(90L, store.results.single().durationSeconds)
        assertEquals(today.toEpochDay(), store.results.single().epochDay)
        assertTrue(vm.game().spec.hardest in settings.flow.value.techniquesMet)

        vm.onEvent(PlayEvent.Restart)
        assertFalse(vm.game().solved)
        vm.solveFully()
        advanceUntilIdle()
        assertEquals(1, store.results.size)
    }

    @Test
    fun `coach goes look, why, answer and then writes the digit`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PlayEvent.Coach)
        val coach = checkNotNull(vm.state.value.coach)
        assertEquals(1, coach.level)
        val hint = coach.hint as CoachHint.Next
        vm.onEvent(PlayEvent.CoachApply) // not yet: the answer has not been asked for
        assertEquals(0, vm.game().values[hint.placement.index])
        vm.onEvent(PlayEvent.CoachDeeper)
        vm.onEvent(PlayEvent.CoachDeeper)
        assertEquals(3, vm.state.value.coach?.level)
        vm.onEvent(PlayEvent.CoachApply)
        assertEquals(hint.placement.digit, vm.game().values[hint.placement.index])
        assertNull(vm.state.value.coach)
        assertEquals(1, vm.game().coach.asks)
        assertEquals(3, vm.game().coach.deepest)
    }

    @Test
    fun `coach points at a wrong digit first`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        val wrong = (spec.solution[empty] % spec.size) + 1
        vm.onEvent(PlayEvent.Tap(empty))
        vm.onEvent(PlayEvent.Digit(wrong))
        vm.onEvent(PlayEvent.Coach)
        assertEquals(CoachHint.Mistake(empty, wrong), vm.state.value.coach?.hint)
        vm.onEvent(PlayEvent.CoachDeeper)
        vm.onEvent(PlayEvent.CoachDeeper)
        vm.onEvent(PlayEvent.CoachApply)
        assertEquals(0, vm.game().values[empty])
    }

    @Test
    fun `mistakes are only shown when the setting is on`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PlayEvent.Tap(empty))
        vm.onEvent(PlayEvent.Digit((spec.solution[empty] % spec.size) + 1))
        assertTrue(vm.state.value.mistakes.isEmpty())
        settings.flow.value = settings.flow.value.copy(showMistakes = true)
        assertEquals(setOf(empty), vm.state.value.mistakes)
    }

    @Test
    fun `an unfinished classic is resumed from the picker and on launch`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PlayEvent.OpenClassic(Difficulty.Gentle))
        advanceUntilIdle()
        val classic = vm.game()
        assertEquals(PuzzleKind.Classic, classic.spec.kind)
        assertEquals(0, classic.spec.number)
        val empty = classic.spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PlayEvent.Tap(empty))
        vm.onEvent(PlayEvent.Digit(classic.spec.solution[empty]))
        advanceUntilIdle()

        vm.onEvent(PlayEvent.OpenDaily(today.toEpochDay()))
        advanceUntilIdle()
        assertEquals(PuzzleKind.Daily, vm.game().spec.kind)
        vm.onEvent(PlayEvent.OpenClassic(Difficulty.Gentle))
        advanceUntilIdle()
        assertEquals(classic.spec.key, vm.game().spec.key)
        assertEquals(classic.spec.solution[empty], vm.game().values[empty])

        val relaunched = viewModel()
        advanceUntilIdle()
        assertEquals(classic.spec.key, relaunched.game().spec.key)
    }

    @Test
    fun `generating the next puzzle is not charged to the current one`() = test {
        val generator = StandardTestDispatcher(testScheduler)
        val vm = GameViewModel(store, settings, clock, ticker = { now }, generatorDispatcher = generator)
        advanceUntilIdle()
        val daily = vm.game().spec.key
        vm.onEvent(PlayEvent.Foreground)
        now = 3_000
        vm.onEvent(PlayEvent.OpenClassic(Difficulty.Gentle)) // generation is queued, not yet run
        assertTrue(vm.state.value.loading)
        now = 50_000
        advanceUntilIdle()
        assertEquals(PuzzleKind.Classic, vm.game().spec.kind)
        assertEquals(2_000L, store.saved.getValue(daily).first.elapsedMillis)
    }

    @Test
    fun `next classic advances the number`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PlayEvent.OpenClassic(Difficulty.Gentle))
        advanceUntilIdle()
        vm.onEvent(PlayEvent.NextClassic)
        advanceUntilIdle()
        assertEquals(1, vm.game().spec.number)
    }

    @Test
    fun `a locked digit is written by tapping`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PlayEvent.Lock(spec.solution[empty]))
        vm.onEvent(PlayEvent.Tap(empty))
        assertEquals(spec.solution[empty], vm.game().values[empty])
    }

    @Test
    fun `launch lands on today even if yesterday's daily was active`() = test {
        settings.flow.value = UserSettings(activeKey = Catalog.dailyKey(today.toEpochDay() - 1))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(Catalog.dailyKey(today.toEpochDay()), vm.game().spec.key)
    }
}
