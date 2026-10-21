package com.titpea.papersudoku.game

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.InkStamp
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.PuzzleKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class PanelMode { Play, Coach, Solved }

/**
 * The desk: top bar, the board, and the tool panel.
 *
 * Layout follows the window, not the device: when the window is clearly wider than tall (phone
 * landscape, tablets, desktop windows) the panel moves beside the board; otherwise it sits below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    state: PlayUiState,
    onEvent: (PlayEvent) -> Unit,
    onOpenJournal: () -> Unit,
    onOpenSettings: () -> Unit,
    onPrint: (GameState) -> Unit,
    onShare: (GameState) -> Unit,
    ticker: () -> Long,
    modifier: Modifier = Modifier,
    effects: Flow<PlayEffect> = emptyFlow(),
) {
    val haptics = LocalHapticFeedback.current
    val hapticsOn by rememberUpdatedState(state.settings.haptics)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            if (!hapticsOn) return@collect
            haptics.performHapticFeedback(
                when (effect) {
                    PlayEffect.Placed -> HapticFeedbackType.KeyboardTap
                    PlayEffect.Conflict -> HapticFeedbackType.Reject
                    PlayEffect.Solved -> HapticFeedbackType.Confirm
                    PlayEffect.Toggled -> HapticFeedbackType.SegmentTick
                },
            )
        }
    }

    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    var confirmNewClassic by rememberSaveable { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { handleKey(it, state, onEvent) }
            .focusable(),
    ) {
        val wide = maxWidth > maxHeight && maxWidth >= 560.dp
        val panelWidth: Dp = (maxWidth * 0.4f).coerceIn(320.dp, 440.dp)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PlayTopBar(
                    state = state,
                    onEvent = onEvent,
                    ticker = ticker,
                    onOpenJournal = onOpenJournal,
                    onOpenSettings = onOpenSettings,
                    onPrint = onPrint,
                    onShare = onShare,
                    onRestart = { confirmRestart = true },
                    onNewClassic = {
                        val game = state.game
                        if (game != null && !game.solved && game.history.isNotEmpty()) {
                            confirmNewClassic = true
                        } else {
                            onEvent(PlayEvent.NextClassic)
                        }
                    },
                )
            },
        ) { padding: PaddingValues ->
            val content = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            if (wide) {
                Row(
                    content.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    BoardArea(state, onEvent, Modifier.weight(1f).fillMaxHeight())
                    Column(
                        Modifier
                            .width(panelWidth)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ToolPanel(state, onEvent, onOpenJournal, onPrint, onShare)
                    }
                }
            } else {
                Column(
                    content.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BoardArea(state, onEvent, Modifier.weight(1f).fillMaxWidth().widthIn(max = 720.dp))
                    Spacer(Modifier.height(12.dp))
                    ToolPanel(
                        state,
                        onEvent,
                        onOpenJournal,
                        onPrint,
                        onShare,
                        Modifier.widthIn(max = 640.dp).padding(bottom = 12.dp),
                    )
                }
            }
        }
    }

    state.picker?.let { picker ->
        PuzzlePicker(picker = picker, current = state.game, onEvent = onEvent)
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.restart_title)) },
            text = { Text(stringResource(R.string.restart_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    onEvent(PlayEvent.Restart)
                }) { Text(stringResource(R.string.restart_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (confirmNewClassic) {
        AlertDialog(
            onDismissRequest = { confirmNewClassic = false },
            title = { Text(stringResource(R.string.new_classic_title)) },
            text = { Text(stringResource(R.string.new_classic_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmNewClassic = false
                    onEvent(PlayEvent.NextClassic)
                }) { Text(stringResource(R.string.new_classic_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewClassic = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoardArea(
    state: PlayUiState,
    onEvent: (PlayEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight).coerceAtMost(720.dp)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            val game = state.game
            if (game != null) {
                val marks = remember(game.values, state.coach) {
                    coachMarks(game.spec.geometry, game.values, state.coach)
                }
                SudokuBoard(
                    game = game,
                    conflicts = state.conflicts,
                    mistakes = state.mistakes,
                    marks = marks,
                    hidden = state.userPaused,
                    onTap = { onEvent(PlayEvent.Tap(it)) },
                    onCellAction = { index, action ->
                        onEvent(PlayEvent.Select(index))
                        onEvent(
                            when (action) {
                                BoardAction.Notes -> PlayEvent.ToggleNotes
                                BoardAction.Erase -> PlayEvent.Erase
                                BoardAction.Coach -> PlayEvent.Coach
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (game.solved) {
                    InkStamp(
                        title = stringResource(R.string.stamp_title),
                        caption = stampCaption(game),
                        animate = state.justSolved,
                        size = (side * 0.5f).coerceAtMost(220.dp),
                        modifier = Modifier.testTag("solved-stamp"),
                    )
                }
                if (state.userPaused) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.paused), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onEvent(PlayEvent.TogglePause) }) {
                            Text(stringResource(R.string.resume))
                        }
                    }
                }
            }
            if (state.loading) {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolPanel(
    state: PlayUiState,
    onEvent: (PlayEvent) -> Unit,
    onOpenJournal: () -> Unit,
    onPrint: (GameState) -> Unit,
    onShare: (GameState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = state.game ?: return
    val mode = when {
        game.solved -> PanelMode.Solved
        state.coach != null -> PanelMode.Coach
        else -> PanelMode.Play
    }

    // Predictive back: the Coach card shrinks with the gesture and closes on commit.
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = mode == PanelMode.Coach) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            onEvent(PlayEvent.DismissCoach)
        } catch (e: CancellationException) {
            throw e
        } finally {
            backProgress = 0f
        }
    }

    AnimatedContent(
        targetState = mode,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier.fillMaxWidth().animateContentSize(),
        label = "tool-panel",
    ) { target ->
        when (target) {
            PanelMode.Play -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                game.lockedDigit?.let { digit ->
                    Text(
                        stringResource(R.string.lock_hint, digit),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                GameDock(game, onEvent)
                Keypad(game, onEvent)
            }
            PanelMode.Coach -> state.coach?.let { coach ->
                CoachPanel(
                    coach = coach,
                    geometry = game.spec.geometry,
                    onEvent = onEvent,
                    modifier = Modifier.graphicsLayer {
                        val s = 1f - 0.08f * backProgress
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.4f * backProgress
                    },
                )
            } ?: Spacer(Modifier.height(1.dp))
            PanelMode.Solved -> SolvedPanel(
                game = game,
                onShare = { onShare(game) },
                onPrint = { onPrint(game) },
                onNext = {
                    if (game.spec.kind == PuzzleKind.Daily) onOpenJournal() else onEvent(PlayEvent.NextClassic)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayTopBar(
    state: PlayUiState,
    onEvent: (PlayEvent) -> Unit,
    ticker: () -> Long,
    onOpenJournal: () -> Unit,
    onOpenSettings: () -> Unit,
    onPrint: (GameState) -> Unit,
    onShare: (GameState) -> Unit,
    onRestart: () -> Unit,
    onNewClassic: () -> Unit,
) {
    val game = state.game
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            if (game != null) {
                Column {
                    Text(
                        text = overline(game),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = difficultyName(game.spec.difficulty),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                }
            }
        },
        actions = {
            if (game != null) {
                TimerChip(state, ticker, onToggle = { onEvent(PlayEvent.TogglePause) })
            }
            IconButton(onClick = { onEvent(PlayEvent.OpenPicker) }, modifier = Modifier.testTag("action-puzzles")) {
                Icon(painterResource(R.drawable.ic_grid), contentDescription = stringResource(R.string.action_puzzles))
            }
            IconButton(onClick = onOpenJournal, modifier = Modifier.testTag("action-journal")) {
                Icon(painterResource(R.drawable.ic_journal), contentDescription = stringResource(R.string.action_journal))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (game != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_restart)) },
                            onClick = {
                                menuOpen = false
                                onRestart()
                            },
                        )
                        if (game.spec.kind == PuzzleKind.Classic) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.menu_new_classic, difficultyName(game.spec.difficulty)))
                                },
                                onClick = {
                                    menuOpen = false
                                    onNewClassic()
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
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onShare(game)
                                },
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_title)) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onOpenSettings()
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun TimerChip(state: PlayUiState, ticker: () -> Long, onToggle: () -> Unit) {
    val game = state.game ?: return
    val since = state.runningSince
    var now by remember(since) { mutableLongStateOf(ticker()) }
    LaunchedEffect(since) {
        while (since != null) {
            now = ticker()
            delay(250)
        }
    }
    val millis = game.elapsedMillis + if (since != null) (now - since).coerceAtLeast(0) else 0
    val text = formatElapsed(millis / 1_000)
    val description = stringResource(
        if (state.userPaused) R.string.timer_resume else R.string.timer_pause,
        text,
    )
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = if (state.userPaused) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp)
            .testTag("timer")
            .semantics { contentDescription = description }
            .clickable(enabled = !game.solved, onClick = onToggle),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!game.solved) {
                if (state.userPaused) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_pause), contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.merge(TextStyle(fontFeatureSettings = "tnum")),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PuzzlePicker(
    picker: PickerUi,
    current: GameState?,
    onEvent: (PlayEvent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onEvent(PlayEvent.ClosePicker) },
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(bottom = 24.dp).testTag("puzzle-picker")) {
            Text(
                stringResource(R.string.picker_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val today = LocalDate.ofEpochDay(picker.today)
            PickerRow(
                title = stringResource(R.string.picker_daily),
                subtitle = stringResource(
                    R.string.picker_daily_sub,
                    formatDate(today),
                    difficultyName(Catalog.dailyDifficulty(picker.today)),
                ),
                status = statusLabel(picker.daily),
                selected = current?.spec?.key == Catalog.dailyKey(picker.today),
                onClick = { onEvent(PlayEvent.OpenDaily(picker.today)) },
                modifier = Modifier.testTag("picker-daily"),
            )
            for (difficulty in Difficulty.entries) {
                val number = picker.classicInProgress[difficulty]
                PickerRow(
                    title = stringResource(R.string.picker_classic, difficultyName(difficulty)),
                    subtitle = stringResource(
                        when (difficulty) {
                            Difficulty.Gentle -> R.string.picker_classic_gentle
                            Difficulty.Steady -> R.string.picker_classic_steady
                            Difficulty.Tricky -> R.string.picker_classic_tricky
                        },
                    ),
                    status = if (number != null) {
                        stringResource(R.string.status_continue, number + 1)
                    } else {
                        stringResource(R.string.status_new)
                    },
                    selected = current != null && current.spec.kind == PuzzleKind.Classic &&
                        current.spec.difficulty == difficulty,
                    onClick = { onEvent(PlayEvent.OpenClassic(difficulty)) },
                    modifier = Modifier.testTag("picker-classic-${difficulty.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    status: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Text(
                status,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        ),
        modifier = modifier
            .padding(horizontal = 12.dp)
            .background(Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    )
}

@Composable
private fun statusLabel(status: SlotStatus): String = stringResource(
    when (status) {
        SlotStatus.New -> R.string.status_new
        SlotStatus.InProgress -> R.string.status_in_progress
        SlotStatus.Solved -> R.string.status_solved
    },
)

@Composable
private fun overline(game: GameState): String {
    val spec = game.spec
    val day = spec.epochDay
    return if (spec.kind == PuzzleKind.Daily && day != null) {
        stringResource(R.string.overline_daily, formatDate(LocalDate.ofEpochDay(day)))
    } else {
        stringResource(R.string.overline_classic, (spec.number ?: 0) + 1)
    }
}

@Composable
private fun stampCaption(game: GameState): String {
    val day = game.spec.epochDay
    return if (day != null) formatDate(LocalDate.ofEpochDay(day)) else formatElapsed(game.elapsedMillis / 1_000)
}

@Composable
fun formatDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
}

private fun handleKey(event: KeyEvent, state: PlayUiState, onEvent: (PlayEvent) -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val game = state.game ?: return false
    when (event.key) {
        Key.P -> {
            onEvent(PlayEvent.TogglePause)
            return true
        }
        Key.Escape -> if (state.coach != null) {
            onEvent(PlayEvent.DismissCoach)
            return true
        }
        else -> Unit
    }
    if (state.boardLocked) return false
    val size = game.size
    val current = game.selected ?: game.values.indexOfFirst { it == 0 }.coerceAtLeast(0)
    val row = current / size
    val column = current % size

    fun moveTo(r: Int, c: Int): Boolean {
        if (r !in 0 until size || c !in 0 until size) return false
        onEvent(PlayEvent.Select(r * size + c))
        return true
    }

    return when (event.key) {
        Key.DirectionUp -> moveTo(row - 1, column)
        Key.DirectionDown -> moveTo(row + 1, column)
        Key.DirectionLeft -> moveTo(row, column - 1)
        Key.DirectionRight -> moveTo(row, column + 1)
        Key.Backspace, Key.Delete -> {
            onEvent(PlayEvent.Erase)
            true
        }
        Key.Z -> if (event.isCtrlPressed) {
            onEvent(PlayEvent.Undo)
            true
        } else {
            false
        }
        Key.N -> {
            onEvent(PlayEvent.ToggleNotes)
            true
        }
        Key.H -> {
            onEvent(if (state.coach == null) PlayEvent.Coach else PlayEvent.CoachDeeper)
            true
        }
        else -> {
            val digit = event.utf16CodePoint - '0'.code
            if (digit in 1..size) {
                if (game.selected == null) onEvent(PlayEvent.Select(current))
                onEvent(PlayEvent.Digit(digit))
                true
            } else {
                false
            }
        }
    }
}
