package com.titpea.papersudoku

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        // Hold the splash until the settings are read, so the first frame already has them (the
        // theme follows the system since 11.0 and no longer needs the wait).
        var settingsLoaded = false
        splash.setKeepOnScreenCondition { !settingsLoaded }
        if (savedInstanceState == null) handle(intent)
        // Fully drawn is reported during a draw; make sure a drawn frame follows it, so tools that
        // time to the frame after the report (Macrobenchmark's time to full display) find one.
        fullyDrawnReporter.addOnReportDrawnListener { window.decorView.postInvalidateOnAnimation() }

        setContent {
            val settingsFlow = remember { container.settingsStore.settings.map<UserSettings, UserSettings?> { it } }
            val loaded by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            if (loaded == null) return@setContent
            settingsLoaded = true
            // 11.0: the theme follows the system; there is no theme setting.
            val darkTheme = isSystemInDarkTheme()
            // System bars follow the app's theme, not the system's: on Android 8–9 the navigation
            // bar's scrim is drawn by the app, so it must match the icons it sits behind.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }
            SudokuTheme(darkTheme = darkTheme) {
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

        // The platform's own navigation-bar scrims (androidx.activity defaults).
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
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
