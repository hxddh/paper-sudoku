package com.titpea.papersudoku.page

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.FrauncesPrinted
import com.titpea.papersudoku.designsystem.InkStamp
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class WorkMode { Play, Coach, Solved }

/** Minimum height of the work area under the board; the Coach and the solved note use the same. */
private val NoteHeight: Dp = 176.dp

/** The quiet line between the tools and the keys; a 48dp target when it is a button. */
private val StatusHeight: Dp = 48.dp

/** How long the stamp takes to land; then the page stops asking for it to land again. */
private const val STAMP_LANDING_MILLIS = 1_200L

/**
 * One page: a slim header, the board as large as the window allows, and the work area — tools,
 * the Coach's margin note, or the solved note — which always keeps the same size so the page never
 * jumps. On a window clearly wider than tall the work area sits beside the board.
 *
 * [onBack] is null when the notebook is already visible next to the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    state: PageUiState,
    onEvent: (PageEvent) -> Unit,
    onBack: (() -> Unit)?,
    onPrint: (GameState) -> Unit,
    onShare: (GameState) -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<PageEffect> = emptyFlow(),
    boardModifier: Modifier = Modifier,
) {
    // Standard feedback types go through the view, so the system's touch-feedback setting applies.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            haptics.performHapticFeedback(
                when (effect) {
                    PageEffect.Placed -> HapticFeedbackType.KeyboardTap
                    PageEffect.Conflict -> HapticFeedbackType.Reject
                    PageEffect.Solved -> HapticFeedbackType.Confirm
                    PageEffect.Toggled -> HapticFeedbackType.SegmentTick
                },
            )
        }
    }

    // Dialogs belong to the page they were opened on: another page arriving closes them.
    val pageKey = state.game?.spec?.key
    var confirmRestart by rememberSaveable(pageKey) { mutableStateOf(false) }
    var confirmNextPractice by rememberSaveable(pageKey) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var pageFocused by remember { mutableStateOf(false) }
    val keyboardMode = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    // Each page that opens takes focus, so a keyboard works at once (also beside the notebook).
    LaunchedEffect(pageKey) { if (pageKey != null) runCatching { focusRequester.requestFocus() } }

    // How far a back gesture has pulled the Coach's note away (it shrinks and fades with it).
    val coachBack = remember { Animatable(0f) }
    LaunchedEffect(state.coach == null) { if (state.coach != null) coachBack.snapTo(0f) }

    // Ink connector between the Coach's note and the board, in this screen's coordinates.
    var origin by remember { mutableStateOf(Offset.Zero) }
    var boardBounds by remember { mutableStateOf<Rect?>(null) }
    var noteBounds by remember { mutableStateOf<Rect?>(null) }

    val game = state.game
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                .focusRequester(focusRequester)
                .onFocusChanged { pageFocused = it.isFocused }
                .onKeyEvent { handleKey(it, state, onEvent, arrows = pageFocused) }
                .focusable()
                .testTag("page"),
        ) {
            val wide = maxWidth > maxHeight && maxWidth >= 560.dp
            val panel = (maxWidth * 0.4f).coerceIn(320.dp, 440.dp)
            val header = @Composable {
                PageHeader(
                    state = state,
                    onBack = onBack,
                    onPrint = onPrint,
                    onShare = onShare,
                    onRestart = { confirmRestart = true },
                    onNextPractice = {
                        if (game != null && !game.solved && game.history.isNotEmpty()) {
                            confirmNextPractice = true
                        } else {
                            onEvent(PageEvent.NextPractice)
                        }
                    },
                )
            }
            val focusRing = if (pageFocused && keyboardMode) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.small)
            } else {
                Modifier
            }
            if (wide) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    BoardArea(
                        state, onEvent,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        boardModifier.then(focusRing).onGloballyPositioned { boardBounds = it.boundsInRoot() },
                        // Against the panel, so board and tools read as one spread.
                        alignment = Alignment.CenterEnd,
                    )
                    // The keys keep clear of the screen edge and its back gesture.
                    Column(Modifier.width(panel).fillMaxHeight().padding(end = 24.dp)) {
                        header()
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            WorkArea(
                                state, onEvent, onBack, onPrint, onShare, coachBack,
                                Modifier.onGloballyPositioned { noteBounds = it.boundsInRoot() },
                            )
                        }
                    }
                }
            } else Column(Modifier.fillMaxSize()) {
                header()
                val content = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                // 9×9 cells need the width; the board itself keeps its edges from the back gesture.
                val boardPadding = if (game?.size == 9) 8.dp else 12.dp
                Column(content, horizontalAlignment = Alignment.CenterHorizontally) {
                    BoardArea(
                        state, onEvent,
                        Modifier.weight(1f).fillMaxWidth().widthIn(max = 720.dp).padding(horizontal = boardPadding),
                        boardModifier.then(focusRing).onGloballyPositioned { boardBounds = it.boundsInRoot() },
                        // Most of the spare height above the board, so it sits near the hand.
                        alignment = BiasAlignment(0f, 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    WorkArea(
                        state, onEvent, onBack, onPrint, onShare, coachBack,
                        Modifier
                            .widthIn(max = 640.dp)
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .onGloballyPositioned { noteBounds = it.boundsInRoot() },
                    )
                }
            }

            val coach = state.coach
            if (game != null && coach != null) {
                val marks = remember(game.values, coach) { coachMarks(game.spec.geometry, game.values, coach) }
                InkConnector(
                    // Only what the note names: the bracket reads as "this box", not every cell lit.
                    focus = marks.anchor,
                    size = game.size,
                    board = boardBounds?.translate(-origin),
                    note = noteBounds?.translate(-origin),
                    wide = wide,
                    key = coach,
                    modifier = Modifier.graphicsLayer { alpha = 1f - coachBack.value },
                )
            }
        }
    }

    if (confirmRestart) {
        ConfirmDialog(
            title = R.string.restart_title,
            body = R.string.restart_body,
            confirm = R.string.restart_confirm,
            onConfirm = {
                confirmRestart = false
                onEvent(PageEvent.Restart)
            },
            onDismiss = { confirmRestart = false },
        )
    }
    if (confirmNextPractice) {
        ConfirmDialog(
            title = R.string.next_practice_title,
            body = R.string.next_practice_body,
            confirm = R.string.next_practice_confirm,
            onConfirm = {
                confirmNextPractice = false
                onEvent(PageEvent.NextPractice)
            },
            onDismiss = { confirmNextPractice = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoardArea(
    state: PageUiState,
    onEvent: (PageEvent) -> Unit,
    modifier: Modifier,
    boardModifier: Modifier,
    alignment: Alignment,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val side = minOf(maxWidth, maxHeight).coerceAtMost(720.dp)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            val game = state.game
            if (game != null) {
                val marks = remember(game.values, state.coach) {
                    coachMarks(game.spec.geometry, game.values, state.coach)
                }
                Board(
                    game = game,
                    conflicts = state.conflicts,
                    marks = marks,
                    onTap = { onEvent(PageEvent.Tap(it)) },
                    onCellAction = { index, action ->
                        onEvent(PageEvent.Select(index))
                        onEvent(
                            when (action) {
                                BoardAction.Erase -> PageEvent.Erase
                            },
                        )
                    },
                    onAimMove = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                    modifier = Modifier.fillMaxSize().then(boardModifier),
                )
                if (game.solved) {
                    if (state.justSolved) {
                        LaunchedEffect(game.spec.key) {
                            delay(STAMP_LANDING_MILLIS)
                            onEvent(PageEvent.StampLanded)
                        }
                    }
                    // One stamp per page: its landed state belongs to this page, not to the slot.
                    key(game.spec.key) {
                        InkStamp(
                            title = stringResource(R.string.stamp_title),
                            caption = formatElapsed(game.elapsedMillis / 1_000),
                            animate = state.justSolved,
                            size = (side * 0.5f).coerceAtMost(220.dp),
                            seed = game.spec.key.hashCode(),
                            // The hand feels the stamp land, not only the last digit.
                            onLanded = { haptics.performHapticFeedback(HapticFeedbackType.Confirm) },
                            modifier = Modifier.testTag("solved-stamp"),
                        )
                    }
                }
            }
            if (state.loading) LoadingIndicator(Modifier.testTag("loading"))
        }
    }
}

@Composable
private fun WorkArea(
    state: PageUiState,
    onEvent: (PageEvent) -> Unit,
    onBack: (() -> Unit)?,
    onPrint: (GameState) -> Unit,
    onShare: (GameState) -> Unit,
    coachBack: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
) {
    val game = state.game ?: return
    // The note leaving keeps its words while it fades, though the session has already ended.
    var lastCoach by remember { mutableStateOf(state.coach) }
    if (state.coach != null) lastCoach = state.coach
    val scope = rememberCoroutineScope()
    val mode = when {
        game.solved -> WorkMode.Solved
        state.coach != null -> WorkMode.Coach
        else -> WorkMode.Play
    }
    // The work area keeps one height so the page does not jump between tools, Coach and solved note.
    // With large text it may grow, and the note scrolls, rather than squeezing the board.
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
    val height = maxOf(NoteHeight * fontScale, ToolbarHeight + StatusHeight + keypadHeight(game.size))

    // Predictive back: the note shrinks with the gesture; on commit it fades from where the
    // finger left it, and a cancelled gesture eases it back.
    PredictiveBackHandler(enabled = mode == WorkMode.Coach) { progress ->
        try {
            progress.collect { coachBack.snapTo(it.progress) }
            onEvent(PageEvent.DismissCoach)
        } catch (e: CancellationException) {
            scope.launch { coachBack.animateTo(0f) }
            throw e
        }
    }

    // Tools, note and solved note trade places along a short vertical axis, on the theme's motion.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val rise = with(LocalDensity.current) { 16.dp.roundToPx() }
    AnimatedContent(
        targetState = mode,
        transitionSpec = { (fadeIn(effects) + slideInVertically(spatial) { rise }) togetherWith fadeOut(effects) },
        modifier = modifier.fillMaxWidth().heightIn(min = height).testTag("work-area"),
        label = "work-area",
    ) { target ->
        when (target) {
            WorkMode.Play -> Column(
                Modifier.fillMaxWidth().heightIn(min = height),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                PageToolbar(game, onEvent)
                StatusLine(game, onEvent, showHint = !state.settings.wroteFirstDigit)
                Keypad(game, onEvent)
            }
            WorkMode.Coach -> (state.coach ?: lastCoach)?.let { coach ->
                MarginNote(
                    coach = coach,
                    geometry = game.spec.geometry,
                    onEvent = onEvent,
                    // The tools' height at normal sizes; with large text the note grows to show it all.
                    modifier = Modifier.fillMaxWidth().heightIn(min = height).graphicsLayer {
                        val s = 1f - 0.06f * coachBack.value
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.4f * coachBack.value
                    },
                )
            } ?: Spacer(Modifier)
            WorkMode.Solved -> SolvedNote(
                game = game,
                outlook = solvedOutlook(game, state),
                onShare = { onShare(game) },
                onPrint = { onPrint(game) },
                onNext = {
                    when {
                        game.spec.kind == PuzzleKind.Classic -> onEvent(PageEvent.NextPractice)
                        onBack != null -> onBack()
                        else -> onEvent(PageEvent.OpenPractice)
                    }
                },
                nextLabel = when {
                    game.spec.kind == PuzzleKind.Classic -> stringResource(R.string.solved_next_practice)
                    onBack != null -> stringResource(R.string.solved_back_to_notebook)
                    else -> stringResource(R.string.solved_open_practice)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = height),
            )
        }
    }
}

/**
 * One quiet line between the tools and the digits that says which mode the pen is in. Until the
 * first digit is ever written it carries the one how-to the app has.
 */
@Composable
private fun StatusLine(game: GameState, onEvent: (PageEvent) -> Unit, showHint: Boolean) {
    val held = game.lockedDigit
    val text = when {
        held != null -> stringResource(R.string.status_holding, held)
        game.notesMode -> stringResource(R.string.status_pencil)
        showHint -> stringResource(R.string.status_hint)
        else -> ""
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = StatusHeight)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        if (held != null) {
            TextButton(
                onClick = { onEvent(PageEvent.ClearLock) },
                colors = ButtonDefaults.textButtonColors(contentColor = PaperTheme.ink.pencil),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.heightIn(min = StatusHeight).testTag("status-holding"),
            ) {
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        } else if (text.isNotEmpty()) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = PaperTheme.ink.pencil,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun SolvedNote(
    game: GameState,
    outlook: Outlook?,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .testTag("solved-panel")
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.Center,
    ) {
        // The stamp carries the time; the note says how the page was done, then the time again
        // for anyone not looking at the stamp (TalkBack reads this note, not the stamp).
        Text(
            coachUsageLine(game.coach.deepest),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        // Numerals are Fraunces everywhere: the time is set in its text cut within the sentence.
        val time = formatElapsed(game.elapsedMillis / 1_000)
        val line = stringResource(R.string.solved_time, time)
        val at = line.indexOf(time)
        Text(
            buildAnnotatedString {
                append(line)
                if (at >= 0) addStyle(SpanStyle(fontFamily = FrauncesPrinted, fontFeatureSettings = "lnum, tnum"), at, at + time.length)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (outlook != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                outlook.text,
                style = MaterialTheme.typography.labelLarge,
                color = if (outlook.news) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("solved-outlook"),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Quiet tools: blue is the player's pen, not a button colour.
            val tools = MaterialTheme.colorScheme.onSurfaceVariant
            IconButton(onClick = onShare) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_share), tint = tools)
            }
            IconButton(onClick = onPrint) {
                Icon(painterResource(R.drawable.ic_print), contentDescription = stringResource(R.string.action_print), tint = tools)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onNext, modifier = Modifier.heightIn(min = 48.dp).testTag("solved-next")) { Text(nextLabel) }
        }
    }
}

/** One line after a solve about what comes next; [news] when the level just moved up. */
private data class Outlook(val text: String, val news: Boolean = false)

@Composable
private fun solvedOutlook(game: GameState, state: PageUiState): Outlook? = when {
    game.spec.kind == PuzzleKind.Classic -> state.practiceAfter?.let { after ->
        val remaining = after.remaining
        when {
            after.leveledUp -> Outlook(stringResource(R.string.solved_level_up, difficultyName(after.level)), news = true)
            remaining != null -> Outlook(pluralStringResource(R.plurals.practice_to_next, remaining, remaining))
            else -> Outlook(stringResource(R.string.practice_top_level))
        }
    }
    // Today's page closes with tomorrow's: a reason to come back, without a streak.
    game.spec.epochDay == state.today -> Outlook(stringResource(R.string.solved_daily_close))
    else -> null
}

@Composable
fun coachUsageLine(deepest: Int): String = stringResource(
    when (deepest) {
        0 -> R.string.coach_usage_none
        1 -> R.string.coach_usage_look
        2 -> R.string.coach_usage_why
        else -> R.string.coach_usage_answer
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageHeader(
    state: PageUiState,
    onBack: (() -> Unit)?,
    onPrint: (GameState) -> Unit,
    onShare: (GameState) -> Unit,
    onRestart: () -> Unit,
    onNextPractice: () -> Unit,
) {
    val game = state.game
    var menuOpen by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    TopAppBar(
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("page-back")) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.notebook))
                }
            }
        },
        title = {
            if (game != null) {
                val spec = game.spec
                val day = spec.epochDay
                Column {
                    Text(
                        text = (
                            if (spec.kind == PuzzleKind.Daily && day != null) {
                                weekday(LocalDate.ofEpochDay(day), locale) + DOT + difficultyName(spec.difficulty)
                            } else {
                                stringResource(R.string.practice) + DOT + difficultyName(spec.difficulty)
                            }
                            // Once the answer is shown, the page says so while you play, not only at the end.
                            ) + if (game.coach.deepest >= 3) DOT + stringResource(R.string.page_answer_shown) else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = if (spec.kind == PuzzleKind.Daily && day != null) {
                            monthDay(LocalDate.ofEpochDay(day), locale)
                        } else {
                            stringResource(R.string.practice_page, (spec.number ?: 0) + 1)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                }
            }
        },
        actions = {
            if (game != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("page-menu")) {
                        Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (!game.solved) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_restart)) },
                                onClick = {
                                    menuOpen = false
                                    onRestart()
                                },
                            )
                        }
                        if (game.spec.kind == PuzzleKind.Classic) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_next_practice)) },
                                onClick = {
                                    menuOpen = false
                                    onNextPractice()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_print)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_print), contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onPrint(game)
                            },
                        )
                        if (game.solved) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_share), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onShare(game)
                                },
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        // Two lines of title: at large font scales the bar grows instead of clipping them.
        expandedHeight = maxOf(TopAppBarDefaults.TopAppBarExpandedHeight, (48 * LocalDensity.current.fontScale + 12).dp),
    )
}

/**
 * A fine ink line from the Coach's note to the board's edge, ending in a short bracket beside the
 * rows or columns the note is about, drawn as if by pen each time the note changes. It never
 * crosses the grid: the tinted cells already show the place.
 */
@Composable
private fun InkConnector(
    focus: Collection<Int>,
    size: Int,
    board: Rect?,
    note: Rect?,
    wide: Boolean,
    key: Any,
    modifier: Modifier = Modifier,
) {
    if (board == null || note == null || focus.isEmpty()) return
    val color = PaperTheme.ink.margin
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, spec)
    }
    Canvas(modifier.fillMaxSize().testTag("ink-connector")) {
        val cell = board.width / size
        val rows = focus.map { it / size }
        val cols = focus.map { it % size }
        val gap = 4.dp.toPx()
        val path = Path()
        val bracketFrom: Offset
        val bracketTo: Offset
        if (wide) {
            // The note sits beside the board: a bracket down the board's near edge, by the rows.
            val x = if (rtl) board.left - gap else board.right + gap
            bracketFrom = Offset(x, board.top + rows.min() * cell + cell * 0.2f)
            bracketTo = Offset(x, board.top + (rows.max() + 1) * cell - cell * 0.2f)
            val start = Offset(if (rtl) note.right else note.left, note.top + 24.dp.toPx())
            val end = Offset(x, (bracketFrom.y + bracketTo.y) / 2f)
            path.moveTo(start.x, start.y)
            path.cubicTo((start.x + end.x) / 2f, start.y, (start.x + end.x) / 2f, end.y, end.x, end.y)
        } else {
            // The note sits under the board: a bracket along the bottom edge, under the columns.
            val y = board.bottom + gap
            bracketFrom = Offset(board.left + cols.min() * cell + cell * 0.2f, y)
            bracketTo = Offset(board.left + (cols.max() + 1) * cell - cell * 0.2f, y)
            val start = Offset(if (rtl) note.right - 1.dp.toPx() else note.left + 1.dp.toPx(), note.top)
            val end = Offset((bracketFrom.x + bracketTo.x) / 2f, y)
            path.moveTo(start.x, start.y)
            path.cubicTo(start.x, (start.y + end.y) / 2f, end.x, (start.y + end.y) / 2f, end.x, end.y)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * progress.value, partial, true)
        drawPath(partial, color.copy(alpha = 0.8f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        if (progress.value >= 1f) {
            drawLine(color, bracketFrom, bracketTo, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun ConfirmDialog(title: Int, body: Int, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun handleKey(event: KeyEvent, state: PageUiState, onEvent: (PageEvent) -> Unit, arrows: Boolean): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val game = state.game ?: return false
    if (event.key == Key.Escape) {
        return when {
            state.coach != null -> {
                onEvent(PageEvent.DismissCoach)
                true
            }
            game.lockedDigit != null -> {
                onEvent(PageEvent.ClearLock)
                true
            }
            else -> false
        }
    }
    if (state.boardLocked) return false
    val size = game.size
    val current = game.selected ?: game.values.indexOfFirst { it == 0 }.coerceAtLeast(0)
    val row = current / size
    val column = current % size

    fun moveTo(r: Int, c: Int): Boolean {
        if (r !in 0 until size || c !in 0 until size) return false
        onEvent(PageEvent.Select(r * size + c))
        return true
    }

    return when (event.key) {
        // Arrows that reach here from a focused button belong to focus navigation, not the board.
        Key.DirectionUp -> arrows && moveTo(row - 1, column)
        Key.DirectionDown -> arrows && moveTo(row + 1, column)
        Key.DirectionLeft -> arrows && moveTo(row, column - 1)
        Key.DirectionRight -> arrows && moveTo(row, column + 1)
        Key.Backspace, Key.Delete -> {
            onEvent(PageEvent.Erase)
            true
        }
        Key.Z -> if (event.isCtrlPressed) {
            onEvent(PageEvent.Undo)
            true
        } else {
            false
        }
        Key.N -> {
            onEvent(PageEvent.ToggleNotes)
            true
        }
        Key.H -> {
            onEvent(if (state.coach == null) PageEvent.Coach else PageEvent.CoachDeeper)
            true
        }
        else -> {
            val digit = event.utf16CodePoint - '0'.code
            if (digit in 1..size) {
                if (game.selected == null) onEvent(PageEvent.Select(current))
                onEvent(PageEvent.TypeDigit(digit))
                true
            } else {
                false
            }
        }
    }
}
