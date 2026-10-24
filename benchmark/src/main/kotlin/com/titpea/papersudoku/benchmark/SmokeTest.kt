package com.titpea.papersudoku.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The minified (R8) build walked end to end: the app's own UI tests run on the debug build, so
 * this is what catches a class or resource that shrinking removed. Each screen must appear.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun pageNotebookLicences() {
        device.executeShellCommand("am force-stop $PACKAGE")
        device.pressHome()
        device.launchToTodaysPage()
        device.writeInEmptyCells(1)

        device.backToNotebook()
        // There are no settings (11.0): the licences are at the notebook's foot. Scroll down to them.
        val notebook = device.findTag("notebook-list", "The notebook's pages")
        var licences = device.findObject(By.res("open-licences"))
        var scrolls = 0
        while (licences == null && scrolls < 10) {
            notebook.scroll(Direction.DOWN, 0.8f)
            licences = device.findObject(By.res("open-licences"))
            scrolls++
        }
        checkNotNull(licences) { "The notebook has no licences link" }.click()
        device.findTag("licences", "The licences")

        device.pressBack()
        device.findTag("notebook", "The notebook, after leaving the licences")
        // Found again: the list on screen now is not the one scrolled before.
        repeat(3) { device.findTag("notebook-list", "The notebook's pages").scroll(Direction.UP, 1f) }

        device.findTag("notebook-today", "The notebook's today entry").click()
        device.findTag("sudoku-board", "Today's page, reopened from the notebook")
    }
}
