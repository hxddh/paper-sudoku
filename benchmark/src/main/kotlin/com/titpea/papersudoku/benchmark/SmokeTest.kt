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
    fun pageNotebookSettingsLicences() {
        device.executeShellCommand("am force-stop $PACKAGE")
        device.pressHome()
        device.launchToTodaysPage()
        device.writeInEmptyCells(1)

        device.backToNotebook()
        device.findTag("open-settings", "The notebook's Settings button").click()
        val settings = device.findTag("settings", "Settings")

        // Licences is the last entry: scroll down to it.
        var licences = device.findObject(By.res("open-licences"))
        var scrolls = 0
        while (licences == null && scrolls < 10) {
            settings.scroll(Direction.DOWN, 0.8f)
            licences = device.findObject(By.res("open-licences"))
            scrolls++
        }
        checkNotNull(licences) { "Settings has no licences entry" }.click()
        device.findTag("licences", "The licences")

        device.pressBack()
        device.findTag("settings", "Settings, after leaving the licences")
        device.pressBack()
        device.findTag("notebook", "The notebook, after leaving Settings")

        device.findTag("notebook-today", "The notebook's today entry").click()
        device.findTag("sudoku-board", "Today's page, reopened from the notebook")
    }
}
