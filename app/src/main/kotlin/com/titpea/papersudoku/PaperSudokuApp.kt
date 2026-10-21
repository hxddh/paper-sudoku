package com.titpea.papersudoku

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.designsystem.paperGrain
import com.titpea.papersudoku.designsystem.rememberPaperGrain
import com.titpea.papersudoku.notebook.NotebookScreen
import com.titpea.papersudoku.notebook.NotebookViewModel
import com.titpea.papersudoku.page.PageEvent
import com.titpea.papersudoku.page.PageKeys
import com.titpea.papersudoku.page.PageScreen
import com.titpea.papersudoku.page.PageViewModel
import com.titpea.papersudoku.settings.SettingsScreen
import com.titpea.papersudoku.share.printPuzzle
import com.titpea.papersudoku.share.sharePage
import java.time.LocalDate
import kotlinx.coroutines.launch

enum class Screen { Home, Settings }

/**
 * Two destinations on a Navigation 3 back stack: the notebook (home) and Settings. The notebook
 * itself is a Material 3 adaptive list-detail scaffold — the list of pages and the open page —
 * which shows one pane on phones and both side by side on wide windows.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PaperSudokuApp(container: AppContainer, versionName: String) {
    val backStack: SnapshotStateList<Screen> = rememberSaveable(
        saver = listSaver(save = { stack -> stack.map { it.name } }, restore = { names -> names.map(Screen::valueOf).toMutableStateList() }),
    ) { mutableStateListOf(Screen.Home) }
    val pageViewModel: PageViewModel = viewModel(factory = PageViewModel.factory(container))
    val notebookViewModel: NotebookViewModel = viewModel(factory = NotebookViewModel.factory(container))
    val scope = rememberCoroutineScope()
    val grain = rememberPaperGrain(PaperTheme.ink)

    Box(
        Modifier
            .fillMaxSize()
            .background(PaperTheme.colors.background)
            .paperGrain(grain)
            // Test tags double as resource ids for UI Automator (Macrobenchmark).
            .semantics { testTagsAsResourceId = true },
    ) {
        CompositionLocalProvider(LocalContentColor provides PaperTheme.colors.onBackground) {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                entryProvider = { screen ->
                    NavEntry(screen) {
                        when (screen) {
                            Screen.Home -> NotebookHome(
                                pageViewModel = pageViewModel,
                                notebookViewModel = notebookViewModel,
                                onOpenSettings = { backStack.add(Screen.Settings) },
                            )
                            Screen.Settings -> {
                                val state by pageViewModel.state.collectAsStateWithLifecycle()
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
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun NotebookHome(
    pageViewModel: PageViewModel,
    notebookViewModel: NotebookViewModel,
    onOpenSettings: () -> Unit,
) {
    val page by pageViewModel.state.collectAsStateWithLifecycle()
    val notebook by notebookViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val todayKey = remember { PageKeys.daily(LocalDate.now().toEpochDay()) }
    // Opening the app lands on today's page; back goes to the notebook.
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(
        initialDestinationHistory = listOf(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail, todayKey),
        ),
    )
    val twoPane = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val openKey = page.game?.let(PageKeys::of)

    LifecycleStartEffect(Unit) {
        notebookViewModel.refreshDate()
        onStopOrDispose { }
    }

    SharedTransitionLayout {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    val paneScope = this
                    NotebookScreen(
                        state = notebook,
                        onOpenDay = { day ->
                            pageViewModel.onEvent(PageEvent.OpenDaily(day))
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, PageKeys.daily(day)) }
                        },
                        onOpenPractice = {
                            pageViewModel.onEvent(PageEvent.OpenPractice)
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, PageKeys.PRACTICE) }
                        },
                        onOpenSettings = onOpenSettings,
                        selectedKey = if (twoPane) openKey else null,
                        boardTransition = { key ->
                            Modifier.sharedElement(rememberSharedContentState("board-$key"), paneScope)
                        },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val paneScope = this
                    // The hidden clock runs only while the page is on screen.
                    LifecycleStartEffect(Unit) {
                        pageViewModel.onEvent(PageEvent.Foreground)
                        onStopOrDispose { pageViewModel.onEvent(PageEvent.Background) }
                    }
                    PageScreen(
                        state = page,
                        onEvent = pageViewModel::onEvent,
                        onBack = if (twoPane) null else ({ scope.launch { navigator.navigateBack() } }),
                        onPrint = { printPuzzle(context, it) },
                        onShare = { sharePage(context, it) },
                        effects = pageViewModel.effects,
                        boardModifier = if (openKey != null) {
                            Modifier.sharedElement(rememberSharedContentState("board-$openKey"), paneScope)
                        } else {
                            Modifier
                        },
                    )
                }
            },
        )
    }
}
