package com.titpea.papersudoku.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The app under test (the minified `benchmark` build of :app). */
internal const val PACKAGE = "com.titpea.papersudoku"

/** Compose test tags are exposed as resource ids (`testTagsAsResourceId` on the app's root). */
private val CELL = Pattern.compile("cell-\\d+")

/** Wipes the app's data (and stops it), so the next launch deals today's page afresh. */
internal fun UiDevice.clearAppData() {
    executeShellCommand("pm clear $PACKAGE")
}

/**
 * `am start -W` plus waiting for the board, rather than startActivityAndWait(): the latter
 * confirms launch through gfxinfo frame stats, which the CI emulator does not report. A
 * Macrobenchmark's startup metric still comes from the Perfetto trace.
 */
internal fun UiDevice.launchToTodaysPage() {
    executeShellCommand("am start -W -n $PACKAGE/.MainActivity")
    check(wait(Until.hasObject(By.res("sudoku-board")), 10_000)) { "Today's page never showed its board" }
}

/** The object with this test tag, waiting up to [timeoutMs] for it; fails with [what] if it never shows. */
internal fun UiDevice.findTag(tag: String, what: String, timeoutMs: Long = 5_000): UiObject2 =
    checkNotNull(wait(Until.findObject(By.res(tag)), timeoutMs)) { "$what never showed (no \"$tag\")" }

/**
 * Writes [count] digits into empty cells of the open page: select an empty cell, then tap a
 * keypad digit. Cells are found by their spoken state ("…, empty"), so the taps never land on a
 * printed digit or rewrite (and so clear) an earlier one, whatever today's puzzle is.
 */
internal fun UiDevice.writeInEmptyCells(count: Int) {
    findTag("sudoku-board", "Today's board")
    repeat(count) { i ->
        val cell = findObject(By.res(CELL).descContains("empty")) ?: return
        cell.click()
        waitForIdle()
        val key = checkNotNull(findObject(By.res("keypad-${i % 6 + 1}"))) { "No keypad on today's page" }
        key.click()
        waitForIdle()
    }
}

/** Presses back until the notebook is on screen (at most [presses] times, so the app is never left). */
internal fun UiDevice.backToNotebook(presses: Int = 3) {
    repeat(presses) {
        if (hasObject(By.res("notebook"))) return
        pressBack()
        wait(Until.hasObject(By.res("notebook")), 2_000)
    }
    findTag("notebook", "The notebook")
}
