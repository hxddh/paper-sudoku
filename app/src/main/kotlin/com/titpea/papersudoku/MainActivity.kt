package com.titpea.papersudoku

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.titpea.papersudoku.data.ThemeMode
import com.titpea.papersudoku.data.UserSettings
import com.titpea.papersudoku.designsystem.SudokuTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    private val openToday = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as SudokuApplication).container
        val versionName = BuildConfig.VERSION_NAME
        // Hold the splash until the theme is known, so a dark-theme choice never flashes paper.
        var settingsLoaded = false
        splash.setKeepOnScreenCondition { !settingsLoaded }
        if (savedInstanceState == null) handle(intent)
        // Fully drawn is reported during a draw; make sure a drawn frame follows it, so tools that
        // time to the frame after the report (Macrobenchmark's time to full display) find one.
        fullyDrawnReporter.addOnReportDrawnListener { window.decorView.postInvalidateOnAnimation() }

        setContent {
            val settingsFlow = remember { container.settingsStore.settings.map<UserSettings, UserSettings?> { it } }
            val loaded by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val settings = loaded ?: return@setContent
            settingsLoaded = true
            val darkTheme = when (settings.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SudokuTheme(darkTheme = darkTheme) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                PaperSudokuApp(container, versionName, openToday.receiveAsFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_TODAY) openToday.trySend(Unit)
    }

    companion object {
        /** Sent by the home-screen widget: show today's page. */
        const val ACTION_OPEN_TODAY = "com.titpea.papersudoku.action.OPEN_TODAY"
    }

    /** Lists the hardware-keyboard shortcuts in the system helper (Meta + /). */
    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        fun item(label: Int, keyCode: Int, modifiers: Int = 0) =
            KeyboardShortcutInfo(getString(label), keyCode, modifiers)
        data += KeyboardShortcutGroup(
            getString(R.string.app_name),
            listOf(
                item(R.string.shortcut_move, KeyEvent.KEYCODE_DPAD_UP),
                item(R.string.shortcut_digit, KeyEvent.KEYCODE_1),
                item(R.string.shortcut_erase, KeyEvent.KEYCODE_DEL),
                item(R.string.shortcut_undo, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON),
                item(R.string.shortcut_notes, KeyEvent.KEYCODE_N),
                item(R.string.shortcut_coach, KeyEvent.KEYCODE_H),
                item(R.string.shortcut_put_down, KeyEvent.KEYCODE_ESCAPE),
            ),
        )
    }
}
