package com.titpea.papersudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titpea.papersudoku.data.ThemeMode
import com.titpea.papersudoku.designsystem.SudokuTheme
import com.titpea.papersudoku.game.GameScreen
import com.titpea.papersudoku.game.GameViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as SudokuApplication).container

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val themeMode by container.settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.System)
            val completedCount by container.statsRepository.completedCount
                .collectAsStateWithLifecycle(initialValue = 0)

            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val themeLabel = stringResource(
                when (themeMode) {
                    ThemeMode.System -> R.string.theme_system
                    ThemeMode.Light -> R.string.theme_light
                    ThemeMode.Dark -> R.string.theme_dark
                },
            )

            SudokuTheme(darkTheme = darkTheme) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val viewModel: GameViewModel = viewModel(
                        factory = GameViewModel.factory(container),
                    )
                    val state by viewModel.uiState.collectAsStateWithLifecycle()

                    GameScreen(
                        state = state,
                        onEvent = viewModel::onEvent,
                        widthSizeClass = windowSizeClass.widthSizeClass,
                        completedCount = completedCount,
                        themeLabel = themeLabel,
                        onCycleTheme = {
                            lifecycleScope.launch {
                                container.settingsRepository.setThemeMode(themeMode.next())
                            }
                        },
                    )
                }
            }
        }
    }
}
