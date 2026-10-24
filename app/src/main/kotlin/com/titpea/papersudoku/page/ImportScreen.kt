package com.titpea.papersudoku.page

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import java.io.File

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

/**
 * A 9×9 page as text, pasted or kept on disk: 81 cells, a digit each, 0 or "." for a blank. Spaces,
 * line breaks and a printed grid's rules (| - +) are skipped. Null for anything else.
 */
fun parsePage(text: String): List<Int>? {
    val cells = text.filterNot { it.isWhitespace() || it in "|-+" }
    if (cells.length != 81) return null
    return cells.map { c ->
        when (c) {
            '.', '0' -> 0
            in '1'..'9' -> c - '0'
            else -> return null
        }
    }
}

/**
 * The page being typed in, kept on disk (11.0) so leaving the screen, or the app, loses nothing:
 * one small file of 81 digits, gone once the page is added or thrown away. Written whole to a
 * temporary file and renamed, so it is never left half written.
 */
class ImportDraft(private val file: File) {
    fun read(): List<Int>? = synchronized(LOCK) {
        runCatching { if (file.exists()) parsePage(file.readText()) else null }.getOrNull()
    }

    /**
     * One writer at a time: a write whose coroutine was cancelled by the next digit still runs its
     * file operations to the end, and two writes through the same temporary file could lose both.
     */
    fun write(values: List<Int>): Unit = synchronized(LOCK) {
        if (values.all { it == 0 }) {
            file.delete()
            return
        }
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(values.joinToString(""))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    fun clear() {
        synchronized(LOCK) { file.delete() }
    }

    companion object {
        private val LOCK = Any()

        fun of(context: Context) = ImportDraft(File(context.filesDir, "import-draft.txt"))
    }
}

/** After a digit is written into [from], the cursor moves on to the next cell, as on paper. */
private fun GameState.movedOnFrom(from: Int): GameState =
    if (values[from] != 0 && from + 1 < values.size) select(from + 1) else this

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
 * "Bring your own page": type the printed digits of a puzzle from a newspaper or a book (or paste
 * them as 81 characters), then check it. A page with one solution is handed to [onImported];
 * otherwise the reason is shown (and clashing digits outlined) so it can be checked against the
 * original. The draft is kept on disk until it is added or thrown away; leaving with digits on it
 * asks whether to keep them.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ImportScreen(onBack: () -> Unit, onImported: (PuzzleSpec) -> Unit) {
    var game by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(blankDraft()) }
    var checking by remember { mutableStateOf(false) }
    // The last refusal, with the digits it was about: it stands only while they are unchanged.
    var refusal by remember { mutableStateOf<Pair<List<Int>, Import.Invalid>?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    // The digits a paste found no page in: the note stands only while they are unchanged.
    var pasteRefused by remember { mutableStateOf<List<Int>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnImported by rememberUpdatedState(onImported)
    val currentOnBack by rememberUpdatedState(onBack)
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val draft = remember(context) { ImportDraft.of(context.applicationContext) }

    val values = game.values
    val shown = refusal?.takeIf { it.first == values }?.second
    val hasDigits = values.any { it != 0 }

    // The draft on disk is read once per visit; after rotation the saved state already holds it.
    var draftRead by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (draftRead) return@LaunchedEffect
        val stored = withContext(Dispatchers.IO) { draft.read() }
        // Only onto a blank page: nothing typed meanwhile is overwritten.
        if (stored != null && game.values.all { it == 0 }) game = blankDraft().copy(values = stored)
        draftRead = true
    }
    LaunchedEffect(values, draftRead) {
        if (draftRead) withContext(Dispatchers.IO) { draft.write(values) }
    }

    // Back with digits on the page asks first; a blank page just closes.
    fun leave() {
        if (hasDigits) confirmLeave = true else onBack()
    }
    BackHandler(enabled = hasDigits) { confirmLeave = true }

    fun paste() {
        scope.launch {
            val clip = clipboard.getClipEntry()?.clipData
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            val pasted = text?.let(::parsePage)
            if (pasted != null) {
                refusal = null
                pasteRefused = null
                game = blankDraft().copy(values = pasted)
            } else {
                pasteRefused = game.values
            }
        }
    }
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
                    selected != null && g.isEditable(selected) -> g.input(event.digit, false).movedOnFrom(selected)
                    else -> g.toggleLock(event.digit)
                }
            }
            is PageEvent.TypeDigit -> g.selected?.let { selected -> g.input(event.digit, false).movedOnFrom(selected) } ?: g
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
                is Import.Ok -> {
                    // Added: the draft has done its work.
                    withContext(Dispatchers.IO) { draft.clear() }
                    currentOnImported(result.spec)
                }
                is Import.Invalid -> refusal = typed to result
            }
        }
    }

    // A page, not a list: the board fills what the keys leave, and one line under it carries the
    // how-to or, after a check, why the page was refused, so nothing below it ever moves.
    Scaffold(
        modifier = Modifier.testTag("import"),
        containerColor = Color.Transparent,
        // A transparent container names no content colour: text and icons would fall back to black.
        contentColor = MaterialTheme.colorScheme.onBackground,
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
                    IconButton(onClick = { leave() }) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
                // The actions sit in the bar, so the board keeps the height its cells need.
                actions = {
                    // Paste and Clear wait in the menu; checking the page is the one action in sight.
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = !checking, modifier = Modifier.testTag("import-menu")) {
                            Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_paste)) },
                                onClick = {
                                    menuOpen = false
                                    paste()
                                },
                                modifier = Modifier.testTag("import-paste"),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_clear)) },
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                },
                                enabled = hasDigits,
                                modifier = Modifier.testTag("import-clear"),
                            )
                        }
                    }
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
                ?: if (pasteRefused == values) stringResource(R.string.import_paste_none) else null
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

    // Leaving keeps the digits on disk for next time, unless they are thrown away; dismissing stays.
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.import_leave_title)) },
            text = { Text(stringResource(R.string.import_leave_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        currentOnBack()
                    },
                    modifier = Modifier.testTag("import-leave-keep"),
                ) { Text(stringResource(R.string.import_leave_keep)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        refusal = null
                        game = blankDraft()
                        scope.launch {
                            withContext(Dispatchers.IO) { draft.clear() }
                            currentOnBack()
                        }
                    },
                    modifier = Modifier.testTag("import-leave-discard"),
                ) { Text(stringResource(R.string.import_leave_discard)) }
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
