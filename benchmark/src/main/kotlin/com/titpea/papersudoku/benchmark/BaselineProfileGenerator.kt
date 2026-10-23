package com.titpea.papersudoku.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods of the app's critical journey, for comparison with (or to
 * regenerate) the hand-written app/src/main/baseline-prof.txt. Off by default: the module runs
 * only Macrobenchmarks (`androidx.benchmark.enabledRules` in benchmark/build.gradle.kts). To run
 * it on an API 33+ device or emulator (no root needed):
 *
 *     ./gradlew :benchmark:connectedBenchmarkAndroidTest -PpaperSudoku.benchmarkRules=BaselineProfile
 *
 * The profile (BaselineProfileGenerator_generate-baseline-prof.txt) lands under
 * benchmark/build/outputs/connected_android_test_additional_output/.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        // Cold start to today's page. (App data is not cleared here: clearing can drop the
        // profile ART is writing, which this rule reads back.)
        pressHome()
        device.launchToTodaysPage()

        // First input: select an empty cell, write a digit (none once the page is full).
        device.writeInEmptyCells(2)

        // The Coach's margin note.
        device.findObject(By.res("coach-action"))?.let {
            it.click()
            device.waitForIdle()
        }

        // Back to the notebook, and down its pages.
        device.backToNotebook()
        device.findObject(By.res("notebook-list"))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }
}
