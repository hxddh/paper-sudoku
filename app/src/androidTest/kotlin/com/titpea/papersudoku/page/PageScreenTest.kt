package com.titpea.papersudoku.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.accessibility.AccessibilityChecks
import com.titpea.papersudoku.designsystem.SudokuTheme
import com.titpea.papersudoku.game.model.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PageScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val spec = Fixtures.daily

    @Before
    fun enableAccessibilityChecks() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
    }

    private fun show(
        state: PageUiState,
        events: MutableList<PageEvent> = mutableListOf(),
        onBack: (() -> Unit)? = {},
        wide: Boolean = false,
    ) {
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                Box(if (wide) Modifier.requiredSize(1000.dp, 600.dp) else Modifier.fillMaxSize()) {
                    PageScreen(
                        state = state,
                        onEvent = { events += it },
                        onBack = onBack,
                        onPrint = {},
                        onShare = {},
                    )
                }
            }
        }
    }

    @Test
    fun boardExposesCollectionAndCellDescriptions() {
        show(Fixtures.page())
        composeRule.onNodeWithTag("sudoku-board")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
        val expected = if (spec.givens[0] == 0) "empty" else "printed ${spec.givens[0]}"
        composeRule.onNodeWithTag("cell-0")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
            .assertContentDescriptionEquals("Row 1, column 1, $expected")
    }

    @Test
    fun boardKeypadAndToolbarEmitEvents() {
        val events = mutableListOf<PageEvent>()
        show(Fixtures.page(), events)
        composeRule.onNodeWithTag("cell-${Fixtures.firstEmpty}").performClick()
        composeRule.onNodeWithTag("keypad-1").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("tool-notes").performClick()
        composeRule.onNodeWithTag("coach-action").performClick()
        assertEquals(
            listOf(PageEvent.Tap(Fixtures.firstEmpty), PageEvent.Digit(1), PageEvent.ToggleNotes, PageEvent.Coach),
            events,
        )
    }

    @Test
    fun pressAndSlideAimsAndCommitsWhereTheFingerLifts() {
        val events = mutableListOf<PageEvent>()
        show(Fixtures.page(), events)
        val board = composeRule.onNodeWithTag("sudoku-board")
        val bounds = board.getUnclippedBoundsInRoot()
        val cellPx = with(composeRule.density) { (bounds.right - bounds.left).toPx() } / 6f
        board.performTouchInput {
            down(Offset(cellPx * 0.5f, cellPx * 0.5f))
            moveTo(Offset(cellPx * 1.5f, cellPx * 0.5f))
            moveTo(Offset(cellPx * 2.5f, cellPx * 1.5f))
        }
        composeRule.onNodeWithTag("loupe").assertExists()
        board.performTouchInput { up() }
        composeRule.onNodeWithTag("loupe").assertDoesNotExist()
        assertEquals(listOf<PageEvent>(PageEvent.Tap(8)), events)
    }

    @Test
    fun theWorkAreaKeepsItsHeightForToolsCoachAndSolvedNote() {
        var state by mutableStateOf(Fixtures.page())
        composeRule.setContent {
            SudokuTheme(darkTheme = false) {
                PageScreen(state = state, onEvent = {}, onBack = {}, onPrint = {}, onShare = {})
            }
        }
        fun height() = composeRule.onNodeWithTag("work-area").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        fun board() = composeRule.onNodeWithTag("sudoku-board").getUnclippedBoundsInRoot()
        val toolsHeight = height()
        val boardBefore = board()
        state = Fixtures.page(coach = Fixtures.coach(2))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("coach-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("ink-connector").assertExists()
        assertEquals(toolsHeight, height())
        assertEquals(boardBefore, board())
        state = Fixtures.page(game = Fixtures.solved())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("solved-panel").assertIsDisplayed()
        assertEquals(toolsHeight, height())
    }

    @Test
    fun coachNoteTeachesStepByStep() {
        val events = mutableListOf<PageEvent>()
        show(Fixtures.page(coach = Fixtures.coach(1)), events)
        composeRule.onNodeWithText("Show me why").assertIsDisplayed()
        composeRule.onNodeWithTag("coach-next").performClick()
        assertEquals(listOf<PageEvent>(PageEvent.CoachDeeper), events)
    }

    @Test
    fun aHeldDigitIsAnnouncedAndCanBePutDown() {
        val events = mutableListOf<PageEvent>()
        val held: GameState = Fixtures.fresh.toggleLock(3)
        show(Fixtures.page(game = held), events)
        composeRule.onNodeWithTag("status-holding").assertIsDisplayed().performClick()
        assertEquals(listOf<PageEvent>(PageEvent.ClearLock), events)
    }

    @Test
    fun aSolvedPageIsStampedAndOffersTheNotebook() {
        var back = false
        show(Fixtures.page(game = Fixtures.solved()), onBack = { back = true })
        composeRule.onNodeWithTag("solved-stamp").assertExists()
        composeRule.onNodeWithText("Solved in 2:05").assertIsDisplayed()
        composeRule.onNodeWithTag("solved-next").performClick()
        assertTrue(back)
    }

    @Test
    fun wideWindowsPutTheToolsBesideTheBoard() {
        show(Fixtures.page(), wide = true)
        val board = composeRule.onNodeWithTag("sudoku-board").fetchSemanticsNode().boundsInRoot
        val tools = composeRule.onNodeWithTag("toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue("tools should sit to the right of the board: $board vs $tools", tools.left >= board.right)
    }

    @Test
    fun thePageHeaderHidesBackWhenTheNotebookIsBeside() {
        show(Fixtures.page(), onBack = null)
        composeRule.onNodeWithTag("page-back").assertDoesNotExist()
    }

    @Test
    fun eachCellIsOneStopForTalkBack() {
        show(Fixtures.page(game = Fixtures.practice()))
        val given = Fixtures.practice().spec.givens.indexOfFirst { it != 0 }
        composeRule.onNodeWithTag("cell-$given").onChildren().assertCountEquals(0)
    }

    @Test
    fun aSolvedPageIsAnnouncedAndCannotBeRestarted() {
        show(Fixtures.page(game = Fixtures.solved()))
        composeRule.onNodeWithTag("solved-panel")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        composeRule.onNodeWithTag("page-menu").performClick()
        composeRule.onNodeWithText("Restart this puzzle").assertDoesNotExist()
    }
}
