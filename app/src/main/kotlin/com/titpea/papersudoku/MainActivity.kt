package com.titpea.papersudoku

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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.titpea.papersudoku.data.ThemeMode
import com.titpea.papersudoku.data.UserSettings
import com.titpea.papersudoku.designsystem.SudokuTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as SudokuApplication).container
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

        setContent {
            val settings by container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings())
            val darkTheme = when (settings.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SudokuTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                PaperSudokuApp(container, versionName)
            }
        }
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
                item(R.string.shortcut_pause, KeyEvent.KEYCODE_P),
            ),
        )
    }
}
