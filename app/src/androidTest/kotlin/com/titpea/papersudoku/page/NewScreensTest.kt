package com.titpea.papersudoku.page

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.accessibility.disableAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesCheckNames
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import org.hamcrest.Matchers.`is`
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.SudokuTheme
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.Move
import com.titpea.papersudoku.game.model.PuzzleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Reading a page back (a page with its moves), and typing in a page of your own. */
class NewScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun enableAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
    }

    // The page being typed in is kept on disk: each test starts from a blank one and leaves none.
    @Before
    @After
    fun clearImportDraft() {
        ImportDraft.of(context).clear()
    }

    /**
     * A 9×9 board's cells are 43dp on a 411dp phone, as on every 9×9 page: precise input there
     * is press-and-slide aiming with the loupe, or the keyboard. Every other check still runs;
     * touch-target size is checked on the 6×6 page and the keys.
     */
    private fun checkAllButCellSize() {
        composeRule.disableAccessibilityChecks()
        composeRule.enableAccessibilityChecks(
            AccessibilityValidator()
                .setRunChecksFromRootView(true)
                .setThrowExceptionFor(AccessibilityCheckResultType.ERROR)
                .setSuppressingResultMatcher(matchesCheckNames(`is`("TouchTargetSizeCheck"))),
        )
    }

    /** Two right digits, a slip, its erasure, one more right digit: five moves. */
    private fun played(spec: PuzzleSpec): List<Move> {
        val empties = spec.givens.indices.filter { spec.givens[it] == 0 }
        var s = GameState.new(spec)
        s = s.select(empties[0]).input(spec.solution[empties[0]], true)
        s = s.select(empties[1]).input(spec.solution[empties[1]], true)
        s = s.select(empties[2]).input(spec.solution[empties[2]] % spec.size + 1, true)
        s = s.erase()
        s = s.select(empties[3]).input(spec.solution[empties[3]], true)
        return s.moves
    }

    @Test
    fun reviewOpensAtTheLastMoveAndStepsBack() {
        val spec = Catalog.daily(20_000)
        val moves = played(spec)
        assertEquals(5, moves.size)
        composeRule.setContent {
            SudokuTheme(darkTheme = false) { ReviewScreen(spec = spec, moves = moves, onBack = {}) }
        }
        // Read back off the main thread: wait for it.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("review-position").fetchSemanticsNodes().isNotEmpty()
        }
        // One page, no scrolling: the board and the controls are on screen together.
        composeRule.onNodeWithTag("review-board").assertIsDisplayed()
        composeRule.onNodeWithTag("review-slider").assertIsDisplayed()
        composeRule.onNodeWithTag("review-position")
            .assertTextEquals(context.getString(R.string.review_position, 5, 5))

        composeRule.onNodeWithTag("review-previous").performClick()
        composeRule.onNodeWithTag("review-position")
            .assertTextEquals(context.getString(R.string.review_position, 4, 5))
        composeRule.onNodeWithTag("review-why").assertTextEquals(context.getString(R.string.review_why_erased))

        composeRule.onNodeWithTag("review-previous").performClick()
        composeRule.onNodeWithTag("review-why").assertTextEquals(context.getString(R.string.review_why_mistake))
    }

    @Test
    fun thisPageSwapsTheMoveForThePagesCounts() {
        val spec = Catalog.daily(20_000)
        val moves = played(spec)
        composeRule.setContent {
            SudokuTheme(darkTheme = false) { ReviewScreen(spec = spec, moves = moves, onBack = {}) }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("review-this-page").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("review-summary").assertDoesNotExist()
        composeRule.onNodeWithTag("review-this-page").performClick()
        composeRule.onNodeWithTag("review-summary").assertIsDisplayed()
        composeRule.onNodeWithTag("review-step").assertDoesNotExist()
        // The slider stays: the counts are of the whole page, the moves still turn.
        composeRule.onNodeWithTag("review-slider").assertIsDisplayed()
        composeRule.onNodeWithTag("review-this-page").performClick()
        composeRule.onNodeWithTag("review-step").assertIsDisplayed()
    }

    @Test
    fun reviewOfAPageWithoutARecordSaysSo() {
        composeRule.setContent {
            SudokuTheme(darkTheme = false) { ReviewScreen(spec = Catalog.daily(20_000), moves = emptyList(), onBack = {}) }
        }
        composeRule.onNodeWithTag("review-empty").assertIsDisplayed()
    }

    @Test
    fun importRefusesTooFewDigits() {
        checkAllButCellSize()
        var imported: PuzzleSpec? = null
        composeRule.setContent {
            SudokuTheme(darkTheme = false) { ImportScreen(onBack = {}, onImported = { imported = it }) }
        }
        // One page, no scrolling: everything is on screen at once.
        fun tap(tag: String) = composeRule.onNodeWithTag(tag).performClick()
        tap("cell-0")
        tap("keypad-5")
        tap("cell-40")
        tap("keypad-3")
        tap("import-check")

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("import-message").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("import-message").assertTextEquals(context.getString(R.string.import_too_few))
        assertNull(imported)
    }

    @Test
    fun typingMovesOnAndLeavingKeepsTheDraft() {
        checkAllButCellSize()
        var left = false
        composeRule.setContent {
            SudokuTheme(darkTheme = false) { ImportScreen(onBack = { left = true }, onImported = {}) }
        }
        composeRule.onNodeWithTag("cell-0").performClick()
        composeRule.onNodeWithTag("keypad-5").performClick()
        // The cursor moves on, so a row is typed in digit after digit.
        composeRule.onNodeWithTag("cell-1").assertIsSelected()
        composeRule.onNodeWithTag("keypad-3").performClick()
        composeRule.onNodeWithTag("cell-2").assertIsSelected()

        // Back with digits on the page asks first; keeping them leaves, and they wait on disk.
        composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        composeRule.onNodeWithTag("import-leave-keep").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) { left && ImportDraft.of(context).read()?.take(2) == listOf(5, 3) }
    }

    @Test
    fun aKeptDraftComesBack() {
        checkAllButCellSize()
        ImportDraft.of(context).write(listOf(7) + List(80) { 0 })
        composeRule.setContent {
            SudokuTheme(darkTheme = false) { ImportScreen(onBack = {}, onImported = {}) }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("cell-0") and hasContentDescription("written 7", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
