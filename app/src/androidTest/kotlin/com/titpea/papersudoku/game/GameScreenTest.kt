package com.titpea.papersudoku.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.accessibility.AccessibilityChecks
import com.titpea.papersudoku.data.Puzzles
import com.titpea.papersudoku.designsystem.SudokuTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GameScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun enableAccessibilityChecks() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
    }

    @Test
    fun boardExposesCollectionAndCellDescriptions() {
        showMini(selected = null)

        composeRule.onNodeWithTag("sudoku-board")
            .assertExists()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))

        composeRule.onNodeWithTag("cell-0")
            .assertExists()
            .assertHasClickAction()
            .assertContentDescriptionEquals("Row 1, column 1, empty")
    }

    @Test
    fun selectingACellAndKeypadDigitEmitsEvents() {
        val events = mutableListOf<GameEvent>()
        showMini(selected = null, events = events)

        composeRule.onNodeWithTag("cell-2").performClick()
        composeRule.onNodeWithTag("keypad-1").performClick()
        composeRule.onNodeWithTag("coach-action").performClick()

        assertEquals(
            listOf(
                GameEvent.SelectCell(2),
                GameEvent.Digit(1),
                GameEvent.Coach,
            ),
            events,
        )
    }

    @Test
    fun expandedLayoutShowsBoardAndSidePanel() {
        composeRule.setContent {
            SudokuTheme {
                Box(Modifier.requiredSize(1000.dp, 800.dp)) {
                    GameScreen(
                        state = miniState(sheet = Sheet.None),
                        onEvent = {},
                        widthSizeClass = WindowWidthSizeClass.Expanded,
                        completedCount = 0,
                        themeLabel = "System",
                        onCycleTheme = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("sudoku-board").assertExists()
        composeRule.onNodeWithTag("coach-action").assertExists()
        composeRule.onNodeWithText("Select a cell").assertExists()
    }

    private fun showMini(
        selected: Int?,
        events: MutableList<GameEvent> = mutableListOf(),
    ) {
        composeRule.setContent {
            SudokuTheme {
                GameScreen(
                    state = miniState(selected),
                    onEvent = { events += it },
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    completedCount = 0,
                    themeLabel = "System",
                    onCycleTheme = {},
                )
            }
        }
    }

    private fun miniState(
        selected: Int? = null,
        sheet: Sheet = Sheet.Keypad,
    ): GameUiState {
        val puzzle = Puzzles.mini6
        return GameUiState(
            size = puzzle.size,
            boxRows = puzzle.boxRows,
            boxCols = puzzle.boxCols,
            cells = puzzle.givens.map { value ->
                CellUi(
                    value = value,
                    given = value != 0,
                    notes = emptySet(),
                )
            },
            selected = selected,
            notesMode = false,
            sheet = sheet,
            hint = null,
            conflicts = emptySet(),
            solved = false,
        )
    }
}
