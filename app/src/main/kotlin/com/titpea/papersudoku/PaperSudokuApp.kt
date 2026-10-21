package com.titpea.papersudoku

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.designsystem.paperGrain
import com.titpea.papersudoku.designsystem.rememberPaperGrain
import com.titpea.papersudoku.game.GameViewModel
import com.titpea.papersudoku.game.PlayEvent
import com.titpea.papersudoku.game.PlayScreen
import com.titpea.papersudoku.journal.JournalScreen
import com.titpea.papersudoku.journal.JournalViewModel
import com.titpea.papersudoku.settings.SettingsScreen
import com.titpea.papersudoku.share.printPuzzle
import com.titpea.papersudoku.share.sharePage
import kotlinx.coroutines.launch

enum class Screen { Play, Journal, Settings }

/**
 * Three destinations on a Navigation 3 back stack. Play is always the root; Journal and Settings
 * are pushed on top and get predictive back for free.
 */
@Composable
fun PaperSudokuApp(container: AppContainer, versionName: String) {
    val backStack: SnapshotStateList<Screen> = rememberSaveable(
        saver = listSaver(save = { stack -> stack.map { it.name } }, restore = { names -> names.map(Screen::valueOf).toMutableStateList() }),
    ) { mutableStateListOf(Screen.Play) }
    val gameViewModel: GameViewModel = viewModel(factory = GameViewModel.factory(container))
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val grain = rememberPaperGrain(PaperTheme.ink)

    Box(
        Modifier
            .fillMaxSize()
            .background(PaperTheme.colors.background)
            .paperGrain(grain),
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            entryProvider = { screen ->
                NavEntry(screen) {
                    when (screen) {
                        Screen.Play -> {
                            val state by gameViewModel.state.collectAsStateWithLifecycle()
                            LifecycleStartEffect(Unit) {
                                gameViewModel.onEvent(PlayEvent.Foreground)
                                onStopOrDispose { gameViewModel.onEvent(PlayEvent.Background) }
                            }
                            PlayScreen(
                                state = state,
                                onEvent = gameViewModel::onEvent,
                                onOpenJournal = { backStack.add(Screen.Journal) },
                                onOpenSettings = { backStack.add(Screen.Settings) },
                                onPrint = { printPuzzle(context, it) },
                                onShare = { sharePage(context, it) },
                                ticker = container.ticker,
                                effects = gameViewModel.effects,
                            )
                        }
                        Screen.Journal -> {
                            val journal: JournalViewModel = viewModel(factory = JournalViewModel.factory(container))
                            val state by journal.state.collectAsStateWithLifecycle()
                            JournalScreen(
                                state = state,
                                onBack = { backStack.removeAt(backStack.lastIndex) },
                                onOpenDay = { day ->
                                    gameViewModel.onEvent(PlayEvent.OpenDaily(day))
                                    backStack.retainAll(listOf(Screen.Play))
                                },
                            )
                        }
                        Screen.Settings -> {
                            val state by gameViewModel.state.collectAsStateWithLifecycle()
                            SettingsScreen(
                                settings = state.settings,
                                versionName = versionName,
                                onUpdate = { transform -> scope.launch { container.settingsStore.update(transform) } },
                                onBack = { backStack.removeAt(backStack.lastIndex) },
                            )
                        }
                    }
                }
            },
        )
    }
}
