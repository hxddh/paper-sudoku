package com.titpea.papersudoku.page

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import com.titpea.papersudoku.data.FirstHint
import com.titpea.papersudoku.designsystem.Hairline
import com.titpea.papersudoku.designsystem.SheetShape
import com.titpea.papersudoku.designsystem.Space
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.InkStamp
import com.titpea.papersudoku.designsystem.PaperTheme
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.Mastery
import com.titpea.papersudoku.game.model.Path as TechniquePath
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.PuzzleKind
import com.titpea.papersudoku.game.model.PuzzleSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/** How long the stamp takes to land; then the page stops asking for it to land again. */
private const val STAMP_LANDING_MILLIS = 1_200L

/**
 * One page: a slim header, the board a fixed 16dp under it and as large as the window allows, and
 * the work area: the status line with the tools, the keys, and above them the room for a hint or
 * the Coach's note (or, once solved, the solved note). The board is sized with the work area
 * reserved, so nothing the work area shows moves it; spare height goes between the board and the
 * work area. On a window clearly wider than tall the work area sits beside the board, the keys'
 * foot on the board's foot.
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
    /** Opens the page's move-by-move reading; null where there is nowhere to read it. */
    onReadBack: ((GameState) -> Unit)? = null,
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
            val work = workAreaHeight(game?.size ?: 9, LocalDensity.current.fontScale)
            val header = @Composable {
                PageHeader(
                    state = state,
                    onBack = onBack,
                    onPrint = onPrint,
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
                Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, SheetShape)
            } else {
                Modifier
            }
            val board = boardModifier.then(focusRing).onGloballyPositioned { boardBounds = it.boundsInRoot() }
            val workArea = @Composable { modifier: Modifier ->
                WorkArea(
                    state, onEvent, onBack, onPrint, onShare, coachBack,
                    noteModifier = Modifier.onGloballyPositioned { noteBounds = it.boundsInRoot() },
                    modifier = modifier,
                    onReadBack = onReadBack,
                )
            }
            if (wide) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                    horizontalArrangement = Arrangement.spacedBy(Space.XL),
                ) {
                    // Against the panel, its foot on the keys' foot, so board and tools read as one spread.
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(start = Space.L, top = Space.BoardTop, bottom = Space.L),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        val side = minOf(maxWidth, maxHeight).coerceAtMost(720.dp)
                        BoardArea(state, onEvent, Modifier.size(side), board)
                    }
                    // The keys keep clear of the screen edge and its back gesture.
                    Column(Modifier.width(panel).fillMaxHeight().padding(end = Space.S, bottom = Space.L)) {
                        header()
                        workArea(Modifier.weight(1f).fillMaxWidth())
                    }
                }
            } else Column(Modifier.fillMaxSize()) {
                header()
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                ) {
                    // The board's edge is the page margin, where the text edge and the keys line up;
                    // its size leaves the work area its room, whatever the work area shows.
                    val side = minOf(maxWidth - Space.Margin * 2, maxHeight - Space.BoardTop - work - Space.L, 720.dp)
                        .coerceAtLeast(0.dp)
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(Space.BoardTop))
                        BoardArea(state, onEvent, Modifier.size(side), board)
                        workArea(
                            Modifier
                                .weight(1f)
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .padding(bottom = Space.L),
                        )
                    }
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

/** The board at its size, the stamp on it once solved. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoardArea(
    state: PageUiState,
    onEvent: (PageEvent) -> Unit,
    modifier: Modifier,
    boardModifier: Modifier,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val game = state.game
        if (game != null) {
            val marks = remember(game.values, state.coach) {
                coachMarks(game.spec.geometry, game.values, state.coach)
            }
            // A digit pressed on a printed digit shows where it is, as a held digit would, with
            // nothing held: the board draws it as its highlighted digit.
            val peek = state.peek
            val shown = remember(game, peek) { if (peek != null && game.lockedDigit == null) game.copy(lockedDigit = peek) else game }
            Board(
                game = shown,
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
                // One stamp per page: its landed state belongs to this page, not to the slot. The
                // time is on the solved note, in words; the stamp is the word alone.
                key(game.spec.key) {
                    InkStamp(
                        title = stringResource(R.string.stamp_title),
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

/**
 * Under the board: the room for a hint or the Coach's note, the status line with the tools, and
 * the keys, which stay in use while the note is up; once solved, the solved note in their place.
 * One height whatever it shows ([modifier] gives it), so the page never jumps.
 */
@Composable
private fun WorkArea(
    state: PageUiState,
    onEvent: (PageEvent) -> Unit,
    onBack: (() -> Unit)?,
    onPrint: (GameState) -> Unit,
    onShare: (GameState) -> Unit,
    coachBack: Animatable<Float, AnimationVector1D>,
    noteModifier: Modifier,
    modifier: Modifier,
    onReadBack: ((GameState) -> Unit)? = null,
) {
    val game = state.game ?: return
    val scope = rememberCoroutineScope()
    val coach = state.coach

    // Predictive back: the note shrinks with the gesture; on commit it goes from where the finger
    // left it, and a cancelled gesture eases it back.
    PredictiveBackHandler(enabled = coach != null && !game.solved) { progress ->
        try {
            progress.collect { coachBack.snapTo(it.progress) }
            onEvent(PageEvent.DismissCoach)
        } catch (e: CancellationException) {
            scope.launch { coachBack.animateTo(0f) }
            throw e
        }
    }

    if (game.solved) {
        SolvedNote(
            game = game,
            outlook = solvedOutlook(game, state),
            onShare = { onShare(game) },
            onPrint = { onPrint(game) },
            onReadBack = onReadBack?.takeIf { game.moves.isNotEmpty() }?.let { read -> { read(game) } },
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
            modifier = modifier.testTag("work-area"),
        )
        return
    }
    Column(modifier.testTag("work-area")) {
        // The note's room: the spare height above the status line. A long note (or large text)
        // scrolls here rather than push the keys away.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (coach != null) {
                    MarginNote(
                        coach = coach,
                        geometry = game.spec.geometry,
                        onEvent = onEvent,
                        learner = state.settings.learner,
                        firstTime = state.firstHint == FirstHint.Coach,
                        modifier = noteModifier.graphicsLayer {
                            val s = 1f - 0.06f * coachBack.value
                            scaleX = s
                            scaleY = s
                            alpha = 1f - coachBack.value
                        },
                    )
                } else {
                    PageHint(state, game)
                }
            }
        }
        StatusLine(game, onEvent)
        Keypad(game, onEvent, Modifier.padding(horizontal = Space.Margin))
    }
}

/**
 * One quiet line above the status line, shown the first time something happens: the pencil, a
 * digit in hand. Until the first digit is ever written, it is the page's how-to.
 */
@Composable
private fun PageHint(state: PageUiState, game: GameState) {
    val held = game.lockedDigit
    val text = when {
        state.firstHint == FirstHint.Pencil && game.notesMode -> stringResource(R.string.first_hint_pencil)
        state.firstHint == FirstHint.PickUp && held != null -> stringResource(R.string.first_hint_pick_up, held)
        !state.settings.wroteFirstDigit -> stringResource(R.string.status_hint)
        else -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = Space.TextEdge, vertical = Space.S)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("page-hint"),
    )
}

/**
 * The quiet line over the keys: what the pen is doing (a digit in hand, to put down; the pencil,
 * with "Fill every candidate" on a 9×9 page), and at its end the three tools.
 */
@Composable
private fun StatusLine(game: GameState, onEvent: (PageEvent) -> Unit) {
    val held = game.lockedDigit
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    // The words start on the text edge; the tools' glyphs end on the board's edge.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = StatusHeight)
            .padding(start = Space.L, end = Space.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.CenterStart) {
            when {
                held != null -> TextButton(
                    onClick = { onEvent(PageEvent.ClearLock) },
                    colors = ButtonDefaults.textButtonColors(contentColor = quiet),
                    shape = SheetShape,
                    contentPadding = PaddingValues(horizontal = Space.XS),
                    modifier = Modifier.heightIn(min = StatusHeight).testTag("status-holding"),
                ) {
                    Text(stringResource(R.string.status_holding, held), style = MaterialTheme.typography.bodyMedium)
                }
                // In pencil on a 9×9 page, filling in every candidate is said, not hidden in a long press.
                game.notesMode && game.size == 9 -> TextButton(
                    onClick = { onEvent(PageEvent.FillCandidates) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    shape = SheetShape,
                    contentPadding = PaddingValues(horizontal = Space.XS),
                    modifier = Modifier.heightIn(min = StatusHeight).testTag("fill-candidates"),
                ) {
                    Text(stringResource(R.string.fill_candidates), style = MaterialTheme.typography.bodyMedium)
                }
                game.notesMode -> Text(
                    stringResource(R.string.status_pencil),
                    style = MaterialTheme.typography.bodyMedium,
                    color = quiet,
                    modifier = Modifier.padding(start = Space.XS),
                )
            }
        }
        PageTools(game, onEvent)
    }
}

/**
 * After the last digit: how the page was done, its time, one line about what comes next, and the
 * ways on: share, print and read back as icons, and the way on as one ink-outlined button.
 */
@Composable
private fun SolvedNote(
    game: GameState,
    outlook: Outlook?,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onReadBack: (() -> Unit)?,
    onNext: () -> Unit,
    nextLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        Column(
            Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("solved-panel"),
        ) {
            Column(Modifier.padding(horizontal = Space.TextEdge)) {
                // The note says how the page was done, then the time (TalkBack reads this note, not
                // the stamp). One voice: the time is set in the sentence's own sans.
                Text(
                    coachUsageLine(game.coach.deepest),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.solved_time, formatElapsed(game.elapsedMillis / 1_000)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
                if (outlook != null) {
                    // News (a level, a technique found) is said in ink, a step louder; red is the stamp's.
                    Text(
                        outlook.text,
                        style = if (outlook.news) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        color = if (outlook.news) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.XS).testTag("solved-outlook"),
                    )
                }
            }
            Spacer(Modifier.height(Space.L))
            val tools = @Composable {
                CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                    IconButton(onClick = onShare) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
                    }
                    IconButton(onClick = onPrint) {
                        Icon(painterResource(R.drawable.ic_print), contentDescription = stringResource(R.string.action_print))
                    }
                    // An icon beside the others, not a line of its own: the note never grows on a solve.
                    if (onReadBack != null) {
                        IconButton(onClick = onReadBack, modifier = Modifier.testTag("solved-read-back")) {
                            Icon(painterResource(R.drawable.ic_read_back), contentDescription = stringResource(R.string.solved_read_back))
                        }
                    }
                }
            }
            // Ink, not the stamp's red, and no fill: going on is a way, not a reward.
            val next = @Composable {
                OutlinedButton(
                    onClick = onNext,
                    shape = SheetShape,
                    border = BorderStroke(Hairline.Width, colors.onSurface),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("solved-next"),
                ) { Text(nextLabel) }
            }
            // Icons' glyphs on the text edge; the way on ends on the board's edge. In a narrow
            // panel or at large text the way on takes its own line.
            BoxWithConstraints(Modifier.fillMaxWidth().padding(start = Space.S, end = Space.L)) {
                if (maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f) {
                    Column {
                        Row { tools() }
                        Box(Modifier.padding(start = Space.M)) { next() }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        tools()
                        Spacer(Modifier.weight(1f))
                        next()
                    }
                }
            }
        }
    }
}

/** One line after a solve about what comes next; [news] when it is about the player's own progress. */
private data class Outlook(val text: String, val news: Boolean = false)

@Composable
private fun solvedOutlook(game: GameState, state: PageUiState): Outlook? {
    val learner = state.settings.learner
    val found = state.foundYourself
    return when {
        // The one true sentence about this page: what the player found themselves, and on the
        // page that makes it known (11.0: progress is the technique path, never a level), that.
        found != null && learner.found(found) == Mastery.MASTERED ->
            Outlook(stringResource(R.string.solved_known_now, techniqueName(found)), news = true)
        found != null -> Outlook(stringResource(R.string.solved_found_yourself, techniqueName(found)), news = true)
        // Practice says where the path goes next; news when it has moved on to a new level's techniques.
        game.spec.kind == PuzzleKind.Classic -> state.practiceAfter?.let { after ->
            TechniquePath.learning(learner, after.level)?.let { next ->
                Outlook(stringResource(R.string.practice_next, techniqueName(next)), news = after.leveledUp)
            }
        }
        // 9.0's weekly and technique pages, and pages typed in, are plain 9×9 pages now.
        game.spec.kind != PuzzleKind.Daily -> null
        // Today's page closes with tomorrow's: a reason to come back, without a streak.
        game.spec.epochDay == state.today -> Outlook(stringResource(R.string.solved_daily_close))
        // Finished a day late, just now, with today's page still to do.
        state.todayWaiting -> Outlook(stringResource(R.string.solved_daily_waiting))
        else -> null
    }
}

/**
 * The small line above a page's title: where it comes from. Levels stay inside (11.0): progress
 * is the technique path, never a level name.
 */
@Composable
fun pageOverline(spec: PuzzleSpec, locale: Locale): String {
    val day = spec.epochDay
    return when (spec.kind) {
        PuzzleKind.Daily -> day?.let { weekday(LocalDate.ofEpochDay(it), locale) }.orEmpty()
        PuzzleKind.Classic, PuzzleKind.Focus -> stringResource(R.string.practice)
        PuzzleKind.Weekly -> stringResource(R.string.nine_by_nine)
        PuzzleKind.Imported -> if (spec.beyondCoach) stringResource(R.string.imported) + DOT + stringResource(R.string.imported_beyond_coach) else stringResource(R.string.imported)
    }
}

/** A page's title: its date, its number, or what it practises. */
@Composable
fun pageTitle(spec: PuzzleSpec, locale: Locale): String {
    val day = spec.epochDay
    return when (spec.kind) {
        PuzzleKind.Daily -> day?.let { monthDay(LocalDate.ofEpochDay(it), locale) }.orEmpty()
        PuzzleKind.Classic -> stringResource(R.string.practice_page, (spec.number ?: 0) + 1)
        PuzzleKind.Weekly -> day?.let { monthDay(LocalDate.ofEpochDay(it), locale) } ?: stringResource(R.string.nine_by_nine)
        PuzzleKind.Focus -> stringResource(R.string.nine_by_nine)
        PuzzleKind.Imported -> stringResource(R.string.imported)
    }
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
                // Without a back button the title starts on the page's text edge.
                Column(Modifier.padding(start = if (onBack == null) Space.XS else 0.dp)) {
                    Text(
                        text = (
                            pageOverline(spec, locale)
                            // Once the answer is shown, the page says so while you play, not only at the end.
                            ) + if (game.coach.deepest >= 3) DOT + stringResource(R.string.page_answer_shown) else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // At large text "answer shown" wraps rather than being cut off.
                        maxLines = if (LocalDensity.current.fontScale > 1.3f) 2 else 1,
                    )
                    Text(
                        text = pageTitle(spec, locale),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                }
            }
        },
        actions = {
            // A solved page's note already offers share, print and read back: the menu keeps only
            // what the note does not (a new practice page). With nothing left, there is no menu.
            val unsolved = game != null && !game.solved
            val practice = game?.spec?.kind == PuzzleKind.Classic
            if (game != null && (unsolved || practice)) {
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("page-menu")) {
                        Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (unsolved) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_restart)) },
                                onClick = {
                                    menuOpen = false
                                    onRestart()
                                },
                            )
                        }
                        if (practice) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_next_practice)) },
                                onClick = {
                                    menuOpen = false
                                    onNextPractice()
                                },
                            )
                        }
                        if (unsolved) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_print)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_print), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onPrint(game)
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
        // Two lines of title (three when the subtitle wraps at large text): the bar grows
        // instead of clipping them.
        expandedHeight = LocalDensity.current.fontScale.let { scale ->
            val lines = if (scale > 1.3f && (game?.coach?.deepest ?: 0) >= 3) 3 else 2
            maxOf(TopAppBarDefaults.TopAppBarExpandedHeight, (24 * lines * scale + 12).dp)
        },
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
        val gap = 4.dp.toPx()
        val path = Path()
        val cols = focus.map { it % size }
        val bracketFrom: Offset
        val bracketTo: Offset
        // A column runs the board's whole height: its bracket goes along the bottom edge, under
        // it, in either layout. Anything else gets a bracket down the board's side, beside its
        // rows: on the side the note faces when it sits beside the board. Underneath, a box is
        // bracketed on its own side of the board (on the far side it would read as the box
        // there); a row or a middle box on the side of the note's margin rule. (Along the bottom
        // edge, a box's columns would read as the bottom box.)
        val column = rows.distinct().size == size
        val box = !column && cols.distinct().size < size
        val nearSide = when {
            wide -> !rtl
            box && cols.min() * 2 >= size -> true
            box && (cols.max() + 1) * 2 <= size -> false
            else -> rtl
        }
        if (column) {
            val y = board.bottom + gap
            bracketFrom = Offset(board.left + cols.min() * cell + cell * 0.2f, y)
            bracketTo = Offset(board.left + (cols.max() + 1) * cell - cell * 0.2f, y)
        } else {
            val x = if (nearSide) board.right + gap else board.left - gap
            bracketFrom = Offset(x, board.top + rows.min() * cell + cell * 0.2f)
            bracketTo = Offset(x, board.top + (rows.max() + 1) * cell - cell * 0.2f)
        }
        val end = Offset((bracketFrom.x + bracketTo.x) / 2f, (bracketFrom.y + bracketTo.y) / 2f)
        // The note's pen rule hangs 8dp into the margin; the line leaves from it.
        val rule = (Space.S + 1.dp).toPx()
        if (wide) {
            val start = Offset(if (rtl) note.right - rule else note.left + rule, note.top + 24.dp.toPx())
            path.moveTo(start.x, start.y)
            path.cubicTo((start.x + end.x) / 2f, start.y, (start.x + end.x) / 2f, end.y, end.x, end.y)
        } else if (column) {
            val start = Offset(if (rtl) note.right - rule else note.left + rule, note.top)
            path.moveTo(start.x, start.y)
            path.cubicTo(start.x, (start.y + end.y) / 2f, end.x, (start.y + end.y) / 2f, end.x, end.y)
        } else {
            // From the top of the note, up the margin on the bracket's own side, into its middle:
            // starting on the far side would cross the grid to get there.
            val start = Offset(if (nearSide) note.right - Space.S.toPx() else note.left + rule, note.top)
            val reach = if (nearSide) 12.dp.toPx() else -12.dp.toPx()
            path.moveTo(start.x, start.y)
            path.cubicTo(start.x, (start.y + end.y) / 2f, end.x + reach, end.y, end.x, end.y)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * progress.value, partial, true)
        drawPath(partial, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
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
            // H does what the note's main button does, the answer's Place included.
            val coach = state.coach
            onEvent(
                when {
                    coach == null -> PageEvent.Coach
                    coach.level < 3 -> PageEvent.CoachDeeper
                    else -> PageEvent.CoachApply
                },
            )
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
