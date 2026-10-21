package com.titpea.papersudoku.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.accessibility.AccessibilityChecks
import com.titpea.papersudoku.designsystem.SudokuTheme
import com.titpea.papersudoku.engine.SudokuEngine
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val day = 20_747L // 2026-10-21
    private val spec = Catalog.daily(day)
    private val fresh = GameState.new(spec)
    private val firstEmpty = spec.givens.indexOfFirst { it == 0 }

    @Before
    fun enableAccessibilityChecks() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
    }

    private fun show(
        state: PlayUiState,
        events: MutableList<PlayEvent> = mutableListOf(),
        onJournal: () -> Unit = {},
        wide: Boolean = false,
    ) {
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                Box(if (wide) Modifier.requiredSize(1000.dp, 600.dp) else Modifier.fillMaxSize()) {
                    PlayScreen(
                        state = state,
                        onEvent = { events += it },
                        onOpenJournal = onJournal,
                        onOpenSettings = {},
                        onPrint = {},
                        onShare = {},
                        ticker = { 0L },
                    )
                }
            }
        }
    }

    private fun state(game: GameState = fresh, coach: CoachUi? = null, paused: Boolean = false) =
        PlayUiState(loading = false, game = game, coach = coach, userPaused = paused, today = day)

    @Test
    fun boardExposesCollectionAndCellDescriptions() {
        show(state())
        composeRule.onNodeWithTag("sudoku-board")
            .assertExists()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
        val expected = if (spec.givens[0] == 0) "empty" else "printed ${spec.givens[0]}"
        composeRule.onNodeWithTag("cell-0")
            .assertHasClickAction()
            .assertContentDescriptionEquals("Row 1, column 1, $expected")
    }

    @Test
    fun boardKeypadAndDockEmitEvents() {
        val events = mutableListOf<PlayEvent>()
        show(state(), events)
        composeRule.onNodeWithTag("cell-$firstEmpty").performClick()
        composeRule.onNodeWithTag("keypad-1").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("dock-notes").performClick()
        composeRule.onNodeWithTag("coach-action").performClick()
        assertEquals(
            listOf(PlayEvent.Tap(firstEmpty), PlayEvent.Digit(1), PlayEvent.ToggleNotes, PlayEvent.Coach),
            events,
        )
    }

    @Test
    fun coachPanelTeachesStepByStep() {
        val events = mutableListOf<PlayEvent>()
        val hint = SudokuEngine(spec.puzzle).hint(spec.givens.toIntArray())
        show(state(coach = CoachUi(hint, level = 1)), events)
        composeRule.onNodeWithTag("coach-panel").assertIsDisplayed()
        composeRule.onNodeWithText("Show me why").assertIsDisplayed()
        composeRule.onNodeWithTag("coach-next").performClick()
        assertEquals(listOf<PlayEvent>(PlayEvent.CoachDeeper), events)
    }

    @Test
    fun solvedPageIsStampedAndLeadsToTheJournal() {
        var solved = fresh
        for (i in spec.givens.indices) if (spec.givens[i] == 0) solved = solved.select(i).input(spec.solution[i], true)
        solved = solved.addElapsed(125_000).settle(now = 1L)
        var journalOpened = false
        show(state(game = solved), onJournal = { journalOpened = true })
        composeRule.onNodeWithTag("solved-stamp").assertExists()
        composeRule.onNodeWithTag("solved-panel").assertIsDisplayed()
        composeRule.onNodeWithText("2:05 on the clock").assertIsDisplayed()
        composeRule.onNodeWithTag("solved-next").performClick()
        assertTrue(journalOpened)
    }

    @Test
    fun pausingHidesTheBoard() {
        val given = spec.givens.indexOfFirst { it != 0 }
        show(state(paused = true))
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
        composeRule.onNodeWithTag("cell-$given")
            .assertContentDescriptionEquals("Row ${given / 6 + 1}, column ${given % 6 + 1}, empty")
    }

    @Test
    fun wideWindowsPutTheToolsBesideTheBoard() {
        show(state(), wide = true)
        val board = composeRule.onNodeWithTag("sudoku-board").fetchSemanticsNode().boundsInRoot
        val coach = composeRule.onNodeWithTag("coach-action").fetchSemanticsNode().boundsInRoot
        assertTrue("tools should sit to the right of the board: $board vs $coach", coach.left >= board.right)
    }
}
