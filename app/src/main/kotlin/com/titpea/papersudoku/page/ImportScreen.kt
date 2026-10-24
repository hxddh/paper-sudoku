package com.titpea.papersudoku.page

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
import com.titpea.papersudoku.game.model.Import
import com.titpea.papersudoku.game.model.PuzzleKind
import com.titpea.papersudoku.game.model.PuzzleSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The blank page givens are typed into. Its solution is all zeros, so it is never a real puzzle:
 * [PuzzleSpec.puzzle] would refuse it (and so would [GameState.decode]); only the board, the
 * keypad and the plain moves (select, input, erase, lock) are used on it.
 */
private val DraftSpec = PuzzleSpec(
    key = "import-draft",
    kind = PuzzleKind.Imported,
    difficulty = Difficulty.Gentle,
    hardest = Technique.HiddenSingle,
    size = 9,
    boxRows = 3,
    boxCols = 3,
    givens = List(81) { 0 },
    solution = List(81) { 0 },
)

private fun blankDraft(): GameState = GameState.new(DraftSpec)

/** The draft survives rotation and process death as its digits, selection and held digit. */
private val DraftSaver: Saver<GameState, Any> = listSaver(
    save = { listOf(it.values.toIntArray(), it.selected ?: -1, it.lockedDigit ?: -1) },
    restore = { saved ->
        val values = (saved[0] as IntArray).toList()
        if (values.size != DraftSpec.cellCount || values.any { it !in 0..9 }) {
            blankDraft()
        } else {
            blankDraft().copy(
                values = values,
                selected = (saved[1] as Int).takeIf { it in values.indices },
                lockedDigit = (saved[2] as Int).takeIf { it in 1..9 },
            )
        }
    },
)

/**
 * "Bring your own page": type the printed digits of a puzzle from a newspaper or a book, then
 * check it. A page with one solution is handed to [onImported]; otherwise the reason is shown (and
 * clashing digits outlined) so it can be checked against the original.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ImportScreen(onBack: () -> Unit, onImported: (PuzzleSpec) -> Unit) {
    var game by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(blankDraft()) }
    var checking by remember { mutableStateOf(false) }
    // The last refusal, with the digits it was about: it stands only while they are unchanged.
    var refusal by remember { mutableStateOf<Pair<List<Int>, Import.Invalid>?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnImported by rememberUpdatedState(onImported)

    val values = game.values
    val shown = refusal?.takeIf { it.first == values }?.second
    val conflicts = remember(values) {
        val peers = DraftSpec.geometry.peers
        values.indices.filter { i -> values[i] != 0 && peers[i].any { values[it] == values[i] } }.toSet()
    }
    val marks = if (shown != null && shown.reason == Import.Reason.Clash) CoachMarks(pattern = shown.cells.toSet()) else CoachMarks.None

    // Mirrors the page: with a cell selected a digit is written; otherwise it is picked up and
    // every tap on the board writes it, until it is put down again.
    fun handle(event: PageEvent) {
        val g = game
        game = when (event) {
            is PageEvent.Digit -> {
                val selected = g.selected
                when {
                    g.lockedDigit != null -> g.toggleLock(event.digit)
                    selected != null && g.isEditable(selected) -> g.input(event.digit, false)
                    else -> g.toggleLock(event.digit)
                }
            }
            is PageEvent.TypeDigit -> g.input(event.digit, false)
            PageEvent.Erase -> g.erase()
            PageEvent.ClearLock -> g.lockedDigit?.let { g.toggleLock(it) } ?: g
            else -> g
        }
    }

    fun check() {
        val typed = game.values
        checking = true
        scope.launch {
            val result = withContext(Dispatchers.Default) { Catalog.imported(typed) }
            checking = false
            when (result) {
                is Import.Ok -> currentOnImported(result.spec)
                is Import.Invalid -> refusal = typed to result
            }
        }
    }

    // A page, not a list: the board fills what the keys leave, and one line under it carries the
    // how-to or, after a check, why the page was refused, so nothing below it ever moves.
    Scaffold(
        modifier = Modifier.testTag("import"),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                // A page header, as on any page: where it comes from over what it is.
                title = {
                    Column {
                        Text(
                            stringResource(R.string.import_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(stringResource(R.string.imported), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
                // The actions sit in the bar, so the board keeps the height its cells need.
                actions = {
                    TextButton(
                        onClick = { confirmClear = true },
                        enabled = !checking && values.any { it != 0 },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("import-clear"),
                    ) { Text(stringResource(R.string.import_clear)) }
                    // Ink, not the stamp's red: adding a page is a way on, as "Back to the notebook" is.
                    FilledTonalButton(
                        onClick = { check() },
                        enabled = !checking,
                        modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp).testTag("import-check"),
                    ) { Text(stringResource(R.string.import_check)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                // Two lines of title: the bar grows at large text instead of clipping them.
                expandedHeight = LocalDensity.current.fontScale.let { scale ->
                    maxOf(TopAppBarDefaults.TopAppBarExpandedHeight, (24 * 2 * scale + 12).dp)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Board(
                    game = game,
                    conflicts = conflicts,
                    marks = marks,
                    onTap = { index -> game = game.tap(index, autoClearNotes = false) },
                    onCellAction = { index, action ->
                        if (action == BoardAction.Erase) game = game.select(index).erase()
                    },
                    modifier = Modifier.widthIn(max = 480.dp).aspectRatio(1f, matchHeightConstraintsFirst = true),
                )
            }
            val message = shown?.let { messageFor(it.reason) }
            Text(
                message ?: stringResource(R.string.import_intro),
                style = MaterialTheme.typography.bodyMedium,
                // A clash is the board's own mistake; anything else is plain information.
                color = if (shown?.reason == Import.Reason.Clash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .then(if (message != null) Modifier.testTag("import-message") else Modifier),
            )
            Keypad(
                game = game,
                onEvent = { handle(it) },
                counts = false,
                modifier = Modifier.widthIn(max = 480.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.import_clear_title)) },
            text = { Text(stringResource(R.string.import_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        refusal = null
                        game = blankDraft()
                    },
                    modifier = Modifier.testTag("import-clear-confirm"),
                ) { Text(stringResource(R.string.import_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun messageFor(reason: Import.Reason): String = stringResource(
    when (reason) {
        Import.Reason.Shape -> R.string.import_shape
        Import.Reason.Clash -> R.string.import_clash
        Import.Reason.TooFew -> R.string.import_too_few
        Import.Reason.NoSolution -> R.string.import_no_solution
        Import.Reason.ManySolutions -> R.string.import_many_solutions
    },
)
