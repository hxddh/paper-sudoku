package com.titpea.papersudoku.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.titpea.papersudoku.settings.SettingsScaffold
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

    SettingsScaffold(
        title = stringResource(R.string.import_title),
        onBack = onBack,
        tag = "import",
    ) {
        item(key = "intro") {
            ImportColumn {
                Text(
                    stringResource(R.string.import_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "board") {
            ImportColumn {
                Board(
                    game = game,
                    conflicts = conflicts,
                    marks = marks,
                    onTap = { index -> game = game.tap(index, autoClearNotes = false) },
                    onCellAction = { index, action ->
                        if (action == BoardAction.Erase) game = game.select(index).erase()
                    },
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .align(Alignment.CenterHorizontally),
                )
            }
        }
        item(key = "message") {
            val message = shown?.let { messageFor(it.reason) }
            if (message != null) {
                ImportColumn {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("import-message"),
                    )
                }
            }
        }
        item(key = "keypad") {
            ImportColumn {
                Keypad(
                    game = game,
                    onEvent = { handle(it) },
                    modifier = Modifier.widthIn(max = 480.dp).align(Alignment.CenterHorizontally),
                )
            }
        }
        item(key = "actions") {
            ImportColumn {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { confirmClear = true },
                        enabled = !checking && values.any { it != 0 },
                        modifier = Modifier.testTag("import-clear"),
                    ) { Text(stringResource(R.string.import_clear)) }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { check() },
                        enabled = !checking,
                        modifier = Modifier.testTag("import-check"),
                    ) {
                        Text(stringResource(if (checking) R.string.import_checking else R.string.import_check))
                    }
                }
            }
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
private fun ImportColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
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
