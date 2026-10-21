package com.titpea.papersudoku.page

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
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 10, 21) // a Wednesday: Gentle daily
    private var instant = Instant.parse("2026-10-21T09:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }
    private var now = 1_000L
    private val store = FakeGameStore()
    private val settings = FakeSettings()
    private var dailySaves = 0

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(generator: kotlinx.coroutines.CoroutineDispatcher = dispatcher) =
        PageViewModel(store, settings, clock, ticker = { now }, generatorDispatcher = generator, onDailySaved = { dailySaves++ })

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private fun PageViewModel.game(): GameState = checkNotNull(state.value.game)

    private fun PageViewModel.solveFully() {
        val spec = game().spec
        for (i in spec.givens.indices) {
            if (spec.givens[i] == 0) {
                onEvent(PageEvent.Tap(i))
                onEvent(PageEvent.Digit(spec.solution[i]))
            }
        }
    }

    @Test
    fun `opens today's page and saves it`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val game = vm.game()
        assertEquals(Catalog.dailyKey(today.toEpochDay()), game.spec.key)
        assertEquals(Difficulty.Gentle, game.spec.difficulty)
        assertFalse(vm.state.value.loading)
        assertTrue(store.saved.containsKey(game.spec.key))
        assertTrue(dailySaves > 0)
    }

    @Test
    fun `progress is autosaved and restored by a new view model`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(spec.solution[empty]))
        advanceUntilIdle()
        val restored = viewModel()
        advanceUntilIdle()
        assertEquals(spec.solution[empty], restored.game().values[empty])
    }

    @Test
    fun `the hidden clock runs only while the page is on screen`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Foreground)
        assertEquals(1_000L, vm.state.value.runningSince)
        now = 6_000
        vm.onEvent(PageEvent.Background)
        assertNull(vm.state.value.runningSince)
        assertEquals(5_000L, vm.game().elapsedMillis)
        now = 60_000
        vm.onEvent(PageEvent.Foreground)
        now = 61_000
        vm.onEvent(PageEvent.Background)
        assertEquals(6_000L, vm.game().elapsedMillis)
    }

    @Test
    fun `a digit with nothing to write into is picked up, then written by tapping`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        val digit = spec.solution[empty]
        vm.onEvent(PageEvent.Digit(digit))
        assertEquals(digit, vm.game().lockedDigit)
        vm.onEvent(PageEvent.Tap(empty))
        assertEquals(digit, vm.game().values[empty])
        val other = (digit % spec.size) + 1
        vm.onEvent(PageEvent.Digit(other)) // swap what is held
        assertEquals(other, vm.game().lockedDigit)
        vm.onEvent(PageEvent.Digit(other)) // put it down
        assertNull(vm.game().lockedDigit)
    }

    @Test
    fun `a selected empty cell takes the digit`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(spec.solution[empty]))
        assertEquals(spec.solution[empty], vm.game().values[empty])
        assertNull(vm.game().lockedDigit)
    }

    @Test
    fun `solving records exactly once, even after a restart`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Foreground)
        now = 91_000
        vm.solveFully()
        advanceUntilIdle()
        assertTrue(vm.game().solved)
        assertTrue(vm.state.value.justSolved)
        assertEquals(1, store.results.size)
        assertEquals(90L, store.results.single().durationSeconds)
        assertTrue(vm.game().spec.hardest in settings.flow.value.techniquesMet)
        vm.onEvent(PageEvent.Restart)
        assertFalse(vm.game().solved)
        vm.solveFully()
        advanceUntilIdle()
        assertEquals(1, store.results.size)
    }

    @Test
    fun `coach goes look, why, answer and then writes the digit`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Coach)
        val coach = checkNotNull(vm.state.value.coach)
        assertEquals(1, coach.level)
        val hint = coach.hint as CoachHint.Next
        vm.onEvent(PageEvent.CoachApply)
        assertEquals(0, vm.game().values[hint.placement.index])
        vm.onEvent(PageEvent.CoachDeeper)
        vm.onEvent(PageEvent.CoachDeeper)
        vm.onEvent(PageEvent.CoachApply)
        assertEquals(hint.placement.digit, vm.game().values[hint.placement.index])
        assertNull(vm.state.value.coach)
        assertEquals(3, vm.game().coach.deepest)
    }

    @Test
    fun `coach points at a wrong digit first`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        val spec = vm.game().spec
        val empty = spec.givens.indexOfFirst { it == 0 }
        val wrong = (spec.solution[empty] % spec.size) + 1
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(wrong))
        vm.onEvent(PageEvent.Coach)
        assertEquals(CoachHint.Mistake(empty, wrong), vm.state.value.coach?.hint)
    }

    @Test
    fun `practice starts gentle, resumes, and follows clean solves`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val first = vm.game()
        assertEquals(PuzzleKind.Classic, first.spec.kind)
        assertEquals(Difficulty.Gentle, first.spec.difficulty)
        val empty = first.spec.givens.indexOfFirst { it == 0 }
        vm.onEvent(PageEvent.Tap(empty))
        vm.onEvent(PageEvent.Digit(first.spec.solution[empty]))
        advanceUntilIdle()

        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay()))
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        assertEquals(first.spec.key, vm.game().spec.key)
        assertEquals(first.spec.solution[empty], vm.game().values[empty])

        store.clean.value = mapOf(Difficulty.Gentle to 3)
        vm.onEvent(PageEvent.NextPractice)
        advanceUntilIdle()
        assertEquals(Difficulty.Steady, vm.game().spec.difficulty)
    }

    @Test
    fun `preparing the next page is not charged to the current one`() = test {
        val generator = StandardTestDispatcher(testScheduler)
        val vm = viewModel(generator)
        advanceUntilIdle()
        val daily = vm.game().spec.key
        vm.onEvent(PageEvent.Foreground)
        now = 3_000
        vm.onEvent(PageEvent.OpenPractice)
        assertTrue(vm.state.value.loading)
        now = 50_000
        advanceUntilIdle()
        assertEquals(PuzzleKind.Classic, vm.game().spec.kind)
        assertEquals(2_000L, store.saved.getValue(daily).first.elapsedMillis)
    }

    @Test
    fun `a finished daily left open past midnight turns to the new day`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.solveFully()
        advanceUntilIdle()
        assertTrue(vm.game().solved)
        vm.onEvent(PageEvent.Background)
        instant = Instant.parse("2026-10-22T07:00:00Z")
        vm.onEvent(PageEvent.Foreground)
        advanceUntilIdle()
        assertEquals(Catalog.dailyKey(today.toEpochDay() + 1), vm.game().spec.key)
    }

    @Test
    fun `an unfinished daily stays open past midnight`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.Background)
        instant = Instant.parse("2026-10-22T07:00:00Z")
        vm.onEvent(PageEvent.Foreground)
        advanceUntilIdle()
        assertEquals(Catalog.dailyKey(today.toEpochDay()), vm.game().spec.key)
    }

    @Test
    fun `a practice page set aside is not offered again`() = test {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val setAside = vm.game().spec
        vm.onEvent(PageEvent.NextPractice)
        advanceUntilIdle()
        val replacement = vm.game().spec
        assertEquals((setAside.number ?: 0) + 1, replacement.number)
        assertFalse(store.saved.containsKey(setAside.key))

        vm.solveFully()
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenDaily(today.toEpochDay()))
        advanceUntilIdle()
        vm.onEvent(PageEvent.OpenPractice)
        advanceUntilIdle()
        val next = vm.game().spec
        assertTrue(next.key != setAside.key && next.key != replacement.key)
        assertEquals((replacement.number ?: 0) + 1, next.number)
    }
}
