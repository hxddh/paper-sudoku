package com.titpea.papersudoku.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.engine.Technique
import kotlinx.coroutines.launch

@Composable
fun GameScreen(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    completedCount: Int,
    themeLabel: String,
    onCycleTheme: () -> Unit,
) {
    if (widthSizeClass == WindowWidthSizeClass.Expanded) {
        ExpandedGameScreen(
            state = state,
            onEvent = onEvent,
            completedCount = completedCount,
            themeLabel = themeLabel,
            onCycleTheme = onCycleTheme,
        )
    } else {
        CompactGameScreen(
            state = state,
            onEvent = onEvent,
            completedCount = completedCount,
            themeLabel = themeLabel,
            onCycleTheme = onCycleTheme,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactGameScreen(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    completedCount: Int,
    themeLabel: String,
    onCycleTheme: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navigationBars = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val sheetState = rememberStandardBottomSheetState(
        initialValue = if (state.isMini) SheetValue.Expanded else SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )

    LaunchedEffect(state.sheet, state.isMini) {
        if (state.sheet == Sheet.Keypad || state.sheet == Sheet.Coach) {
            sheetState.expand()
        } else {
            sheetState.partialExpand()
        }
    }

    // Mini keeps the keypad permanently visible: back should leave the screen, not re-expand.
    // Classic uses the sheet as an on-demand keypad, so back collapses it first.
    // Coach is modal in both modes, so back dismisses it first.
    val interceptBack = state.sheet == Sheet.Coach ||
        (!state.isMini && state.sheet == Sheet.Keypad)
    BackHandler(enabled = interceptBack) {
        scope.launch { sheetState.partialExpand() }
        onEvent(GameEvent.DismissSheet)
    }

    BottomSheetScaffold(
        topBar = {
            GameTopBar(
                state = state,
                onEvent = onEvent,
                completedCount = completedCount,
                themeLabel = themeLabel,
                onCycleTheme = onCycleTheme,
            )
        },
        sheetContent = {
            GameSheet(state = state, onEvent = onEvent)
        },
        sheetPeekHeight = 64.dp + navigationBars,
        sheetDragHandle = { BottomSheetDefaults.DragHandle() },
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        containerColor = MaterialTheme.colorScheme.background,
        sheetSwipeEnabled = true,
    ) { padding: PaddingValues ->
        GameBoardArea(
            state = state,
            onEvent = onEvent,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedGameScreen(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    completedCount: Int,
    themeLabel: String,
    onCycleTheme: () -> Unit,
) {
    BackHandler(enabled = state.sheet == Sheet.Coach) {
        onEvent(GameEvent.DismissSheet)
    }

    Scaffold(
        topBar = {
            GameTopBar(
                state = state,
                onEvent = onEvent,
                completedCount = completedCount,
                themeLabel = themeLabel,
                onCycleTheme = onCycleTheme,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            GameBoardArea(
                state = state,
                onEvent = onEvent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(24.dp),
            )

            Surface(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 0.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    GameDock(state = state, onEvent = onEvent)
                    when (state.sheet) {
                        Sheet.Keypad -> Keypad(
                            size = state.size,
                            onDigit = { onEvent(GameEvent.Digit(it)) },
                        )
                        Sheet.Coach -> CoachContent(
                            state = state,
                            onEvent = onEvent,
                        )
                        Sheet.None -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.select_a_cell),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameBoardArea(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        SudokuBoard(
            state = state,
            onCellSelected = { onEvent(GameEvent.SelectCell(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
        )

        if (state.solved) {
            SolvedOverlay(
                state = state,
                onNewGame = { onEvent(GameEvent.NewGame) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameTopBar(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    completedCount: Int,
    themeLabel: String,
    onCycleTheme: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(if (state.isMini) R.string.daily_mini else R.string.classic),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(if (state.isMini) R.string.mini_format else R.string.classic_format),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (state.isMini) R.string.switch_to_classic else R.string.switch_to_mini,
                                ),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onEvent(GameEvent.ToggleMode)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.new_game)) },
                        onClick = {
                            menuOpen = false
                            onEvent(GameEvent.NewGame)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.theme_menu, themeLabel)) },
                        onClick = {
                            menuOpen = false
                            onCycleTheme()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                pluralStringResource(
                                    R.plurals.completed_count,
                                    completedCount,
                                    completedCount,
                                ),
                            )
                        },
                        enabled = false,
                        onClick = {},
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
private fun GameSheet(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        GameDock(state = state, onEvent = onEvent)
        when (state.sheet) {
            Sheet.Keypad -> Keypad(
                size = state.size,
                onDigit = { onEvent(GameEvent.Digit(it)) },
            )
            Sheet.Coach -> CoachContent(
                state = state,
                onEvent = onEvent,
            )
            Sheet.None -> Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun GameDock(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(
            onClick = { onEvent(GameEvent.ToggleNotes) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(if (state.notesMode) R.string.notes_active else R.string.notes),
                color = if (state.notesMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        TextButton(
            onClick = { onEvent(GameEvent.Undo) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.undo))
        }
        TextButton(
            onClick = { onEvent(GameEvent.Erase) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.erase))
        }
        Button(
            onClick = { onEvent(GameEvent.Coach) },
            modifier = Modifier
                .weight(1.4f)
                .heightIn(min = 48.dp)
                .testTag("coach-action"),
            shape = CircleShape,
        ) {
            Text(stringResource(R.string.coach))
        }
    }
}

@Composable
private fun Keypad(
    size: Int,
    onDigit: (Int) -> Unit,
) {
    val rows = if (size == 6) {
        listOf(1..3, 4..6)
    } else {
        listOf(1..3, 4..6, 7..9)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { digit ->
                    FilledTonalButton(
                        onClick = { onDigit(digit) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("keypad-$digit"),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachContent(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
) {
    val hint = state.hint

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
    ) {
        Text(
            text = when (hint?.technique) {
                Technique.NakedSingle -> stringResource(R.string.technique_naked_single)
                Technique.HiddenSingle -> stringResource(R.string.technique_hidden_single)
                null -> stringResource(R.string.coach_label)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = if (hint != null) {
                stringResource(
                    if (hint.technique == Technique.NakedSingle) {
                        R.string.hint_title_naked
                    } else {
                        R.string.hint_title_hidden
                    },
                    hint.digit,
                )
            } else {
                stringResource(R.string.no_hint_title)
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (hint != null) {
                stringResource(
                    if (hint.technique == Technique.NakedSingle) {
                        R.string.hint_body_naked
                    } else {
                        R.string.hint_body_hidden
                    },
                )
            } else {
                stringResource(R.string.no_hint_body)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = { onEvent(GameEvent.DismissSheet) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.not_now))
            }
            if (hint != null) {
                Button(
                    onClick = { onEvent(GameEvent.PlaceHint) },
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.place_digit, hint.digit))
                }
            }
        }
    }
}

@Composable
private fun SolvedOverlay(
    state: GameUiState,
    onNewGame: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.solved),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.solved_body, stringResource(if (state.isMini) R.string.mini_time else R.string.classic_time)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNewGame) {
                Text(stringResource(R.string.new_game))
            }
        }
    }
}
