package com.titpea.papersudoku

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
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
import com.titpea.papersudoku.page.ImportScreen
import com.titpea.papersudoku.page.ReviewScreen
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.page.PageScreen
import com.titpea.papersudoku.page.PageViewModel
import com.titpea.papersudoku.settings.LicencesScreen
import com.titpea.papersudoku.notebook.HandbookScreen
import androidx.compose.runtime.mutableIntStateOf
import com.titpea.papersudoku.settings.SettingsScreen
import com.titpea.papersudoku.share.printPuzzle
import com.titpea.papersudoku.share.sharePage
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

enum class Screen { Home, Settings, Licences, Handbook, Review, Import }

/**
 * Destinations on a Navigation 3 back stack: the notebook (home), the handbook, Settings and the
 * licences. The notebook
 * itself is a Material 3 adaptive list-detail scaffold — the list of pages and the open page —
 * which shows one pane on phones and both side by side on wide windows.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PaperSudokuApp(
    container: AppContainer,
    versionName: String,
    /** Emits when the widget asks for today's page. */
    openTodayRequests: Flow<Unit> = emptyFlow(),
) {
    val backStack: SnapshotStateList<Screen> = rememberSaveable(
        saver = listSaver(save = { stack -> stack.map { it.name } }, restore = { names -> names.map(Screen::valueOf).toMutableStateList() }),
    ) { mutableStateListOf(Screen.Home) }
    val pageViewModel: PageViewModel = viewModel(factory = PageViewModel.factory(container))
    val notebookViewModel: NotebookViewModel = viewModel(factory = NotebookViewModel.factory(container))
    val scope = rememberCoroutineScope()
    val grain = rememberPaperGrain(PaperTheme.ink)
    // The handbook topic to open at; saved with the back stack.
    var handbookStart by rememberSaveable { mutableIntStateOf(0) }

    // The widget asks for today's page: leave Settings if it is open, open today, and let the
    // notebook move its detail pane once it is on screen.
    var showToday by rememberSaveable { mutableStateOf(false) }
    // A page opened from another screen (the handbook, typing one in): the notebook shows it.
    var showKey by rememberSaveable { mutableStateOf<String?>(null) }
    fun backToNotebookWith(key: String) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        showKey = key
    }
    LaunchedEffect(openTodayRequests) {
        openTodayRequests.collect {
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            pageViewModel.onEvent(PageEvent.OpenToday)
            showToday = true
        }
    }

    // Shared axis for taps; for the back gesture, the leaving page shrinks and rounds under the
    // finger like the system's back preview, revealing the page beneath. Linear in time, so a
    // seeked gesture tracks the finger one to one.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val gesture = tween<Float>(durationMillis = BACK_PREVIEW_MILLIS, easing = LinearEasing)
    val gestureOffset = tween<IntOffset>(durationMillis = BACK_PREVIEW_MILLIS, easing = LinearEasing)

    Box(
        Modifier
            .fillMaxSize()
            .background(PaperTheme.colors.background)
            // Test tags double as resource ids for UI Automator (Macrobenchmark).
            .semantics { testTagsAsResourceId = true },
    ) {
        CompositionLocalProvider(LocalContentColor provides PaperTheme.colors.onBackground) {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                transitionSpec = {
                    (slideInHorizontally(spatial) { it / 10 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -it / 10 } + fadeOut(effects))
                },
                popTransitionSpec = {
                    (slideInHorizontally(spatial) { -it / 10 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { it / 10 } + fadeOut(effects))
                },
                predictivePopTransitionSpec = {
                    slideInHorizontally(gestureOffset) { -it / 20 } togetherWith
                        (
                            scaleOut(gesture, targetScale = 0.9f) +
                                // Gone only at the very end, as the gesture commits.
                                fadeOut(keyframes<Float> { durationMillis = BACK_PREVIEW_MILLIS; 1f at BACK_PREVIEW_MILLIS * 4 / 5 })
                            )
                },
                entryProvider = { screen ->
                    NavEntry(screen) {
                        // Every page is its own sheet of paper, so a page leaving never shows
                        // the one beneath through it.
                        PaperPage(grain) {
                        when (screen) {
                            Screen.Home -> NotebookHome(
                                pageViewModel = pageViewModel,
                                notebookViewModel = notebookViewModel,
                                // Guarded: a double tap during the transition must not stack Settings twice.
                                onOpenSettings = { if (backStack.lastOrNull() == Screen.Home) backStack.add(Screen.Settings) },
                                onOpenHandbook = { topic ->
                                    if (backStack.lastOrNull() == Screen.Home) {
                                        handbookStart = topic
                                        backStack.add(Screen.Handbook)
                                    }
                                },
                                showToday = showToday,
                                onTodayShown = { showToday = false },
                                showKey = showKey,
                                onKeyShown = { showKey = null },
                                onReadBack = { if (backStack.lastOrNull() == Screen.Home) backStack.add(Screen.Review) },
                                onTypeIn = { if (backStack.lastOrNull() == Screen.Home) backStack.add(Screen.Import) },
                            )
                            Screen.Settings -> {
                                val state by pageViewModel.state.collectAsStateWithLifecycle()
                                SettingsScreen(
                                    settings = state.settings,
                                    versionName = versionName,
                                    onUpdate = { transform -> scope.launch { container.settingsStore.update(transform) } },
                                    onOpenLicences = { if (backStack.lastOrNull() == Screen.Settings) backStack.add(Screen.Licences) },
                                    // Guarded: the leaving screen stays tappable while it animates out.
                                    onBack = { if (backStack.lastOrNull() == Screen.Settings) backStack.removeAt(backStack.lastIndex) },
                                )
                            }
                            Screen.Licences -> LicencesScreen(
                                onBack = { if (backStack.lastOrNull() == Screen.Licences) backStack.removeAt(backStack.lastIndex) },
                            )
                            Screen.Handbook -> {
                                val state by pageViewModel.state.collectAsStateWithLifecycle()
                                HandbookScreen(
                                    start = handbookStart,
                                    techniquesMet = state.settings.techniquesMet,
                                    cleanPages = state.settings.cleanPages,
                                    onBack = { if (backStack.lastOrNull() == Screen.Handbook) backStack.removeAt(backStack.lastIndex) },
                                    onPractise = { technique ->
                                        if (backStack.lastOrNull() == Screen.Handbook) {
                                            pageViewModel.onEvent(PageEvent.PractiseTechnique(technique))
                                            backToNotebookWith(Catalog.focusPrefix(technique))
                                        }
                                    },
                                )
                            }
                            Screen.Review -> {
                                val state by pageViewModel.state.collectAsStateWithLifecycle()
                                val game = state.game
                                val back = { if (backStack.lastOrNull() == Screen.Review) backStack.removeAt(backStack.lastIndex) }
                                if (game != null) ReviewScreen(spec = game.spec, moves = game.moves, onBack = { back() })
                                // The page went away under it (after process death, before it loads).
                                else LaunchedEffect(Unit) { back() }
                            }
                            Screen.Import -> ImportScreen(
                                onBack = { if (backStack.lastOrNull() == Screen.Import) backStack.removeAt(backStack.lastIndex) },
                                onImported = { spec ->
                                    if (backStack.lastOrNull() == Screen.Import) {
                                        pageViewModel.onEvent(PageEvent.OpenImported(spec))
                                        backToNotebookWith(spec.key)
                                    }
                                },
                            )
                        }
                        }
                    }
                },
            )
        }
    }
}

/**
 * A page of the notebook: opaque paper with its grain. While it leaves (a back gesture or a
 * tap), its corners round and it lifts, so it reads as a sheet over the page beneath.
 */
@Composable
private fun PaperPage(grain: ShaderBrush?, content: @Composable () -> Unit) {
    val leaving = LocalNavAnimatedContentScope.current.transition
        .animateFloat(label = "leaving") { if (it == EnterExitState.PostExit) 1f else 0f }
    val background = PaperTheme.colors.background
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val f = leaving.value
                if (f > 0f) {
                    shape = RoundedCornerShape(LEAVING_CORNER.toPx() * f)
                    clip = true
                    shadowElevation = LEAVING_ELEVATION.toPx() * f
                }
            }
            .background(background)
            .paperGrain(grain),
    ) { content() }
}

private const val BACK_PREVIEW_MILLIS = 400
private val LEAVING_CORNER = 28.dp
private val LEAVING_ELEVATION = 6.dp

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun NotebookHome(
    pageViewModel: PageViewModel,
    notebookViewModel: NotebookViewModel,
    onOpenSettings: () -> Unit,
    onOpenHandbook: (Int) -> Unit,
    showToday: Boolean,
    onTodayShown: () -> Unit,
    showKey: String?,
    onKeyShown: () -> Unit,
    onReadBack: () -> Unit,
    onTypeIn: () -> Unit,
) {
    val page by pageViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val todayKey = remember { PageKeys.daily(pageViewModel.state.value.today) }
    // Opening the app lands on today's page; back goes to the notebook.
    // A phone on its side is wide but short: one pane at a time, so the page gets the whole window
    // (and its board-beside-tools layout) instead of half of it.
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(adaptiveInfo) {
        val base = calculatePaneScaffoldDirective(adaptiveInfo)
        if (adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) {
            base
        } else {
            base.copy(maxHorizontalPartitions = 1)
        }
    }
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(
        scaffoldDirective = directive,
        initialDestinationHistory = listOf(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail, todayKey),
        ),
    )
    val twoPane = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val openKey = page.game?.let(PageKeys::of)
    val boardKey = navigator.currentDestination?.contentKey ?: openKey

    // Fully drawn once a page is on the desk (Macrobenchmark's time to full display).
    ReportDrawnWhen { page.game != null && !page.loading }
    LifecycleStartEffect(Unit) {
        notebookViewModel.refreshDate()
        onStopOrDispose { }
    }
    LaunchedEffect(showToday) {
        if (!showToday) return@LaunchedEffect
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, PageKeys.daily(pageViewModel.state.value.today))
        onTodayShown()
    }
    LaunchedEffect(showKey) {
        val key = showKey ?: return@LaunchedEffect
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key)
        onKeyShown()
    }
    val notebookTitle = stringResource(R.string.notebook)
    val pageTitle = stringResource(R.string.page_pane)

    SharedTransitionLayout {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane(Modifier.semantics { paneTitle = notebookTitle }) {
                    val paneScope = this
                    // Collected only while the list is on screen; on phones it is hidden behind the page.
                    val notebook by notebookViewModel.state.collectAsStateWithLifecycle()
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
                        onOpenHandbook = onOpenHandbook,
                        onOpenWeekly = {
                            pageViewModel.onEvent(PageEvent.OpenWeekly)
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, Catalog.weeklyKey(Catalog.weekOf(pageViewModel.state.value.today)))
                            }
                        },
                        onOpenSaved = { key ->
                            pageViewModel.onEvent(PageEvent.OpenSaved(key))
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
                        },
                        onTypeIn = onTypeIn,
                        selectedKey = if (twoPane) openKey else null,
                        boardTransition = { key ->
                            Modifier.sharedBounds(rememberSharedContentState("board-$key"), paneScope)
                        },
                    )
                }
            },
            detailPane = {
                AnimatedPane(Modifier.semantics { paneTitle = pageTitle }) {
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
                        onReadBack = { onReadBack() },
                        effects = pageViewModel.effects,
                        // Keyed by the page being opened, not the one still on screen while it loads.
                        // Bounds, not element: the live board and the thumbnail cross-fade as
                        // they fly, so a back gesture never swaps the board for a drawing at once.
                        boardModifier = if (boardKey != null) {
                            Modifier.sharedBounds(rememberSharedContentState("board-$boardKey"), paneScope)
                        } else {
                            Modifier
                        },
                    )
                }
            },
        )
    }
}
