package com.titpea.papersudoku.notebook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.BuildConfig
import com.titpea.papersudoku.R
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.designsystem.PaperType
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.PuzzleKind
import com.titpea.papersudoku.page.DOT
import com.titpea.papersudoku.page.formatElapsed
import com.titpea.papersudoku.page.month
import com.titpea.papersudoku.page.monthDay
import com.titpea.papersudoku.page.spoken
import com.titpea.papersudoku.page.techniqueName
import com.titpea.papersudoku.page.weekday
import java.time.LocalDate
import java.util.Locale

/**
 * The notebook (11.0), everything on the one page: today's page, the one page to continue (if
 * any), practice, the contents with every page ever written on its day, and a footer. The
 * handbook is a word in the top bar; typing in a page is a quiet action on the practice card.
 * Tapping a page opens it.
 *
 * One left edge runs down the page: card text, the continue row and the footer all start
 * [TextInset] in from the cards' edge.
 *
 * [boardTransition] attaches the shared-element transition between a page's thumbnail here and
 * its board on the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    state: NotebookUiState,
    onOpenDay: (Long) -> Unit,
    onOpenPractice: () -> Unit,
    onOpenLicences: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the handbook at a topic: 0 is using the pen, then the techniques in order. */
    onOpenHandbook: (Int) -> Unit = {},
    selectedKey: String? = null,
    boardTransition: @Composable (key: String) -> Modifier = { Modifier },
    /** Opens a saved page by key: a page typed in, a finished practice page, an old 9×9 page. */
    onOpenSaved: (String) -> Unit = {},
    onTypeIn: () -> Unit = {},
    /** The open page's own key, for pages that open under another key (practice). */
    selectedPageKey: String? = null,
    versionName: String = BuildConfig.VERSION_NAME,
) {
    // Every page opens the way it is kept: a day's page by its day, the practice page being
    // written by continuing practice, anything else by its key.
    val openPage: (PageSummary) -> Unit = { page ->
        val day = page.epochDay
        when {
            page.kind == PuzzleKind.Daily && day != null -> onOpenDay(day)
            page.kind == PuzzleKind.Classic && !page.solved -> onOpenPractice()
            else -> onOpenSaved(page.key)
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).testTag("notebook"),
        containerColor = Color.Transparent,
        // Landscape cutouts and side navigation bars are kept clear, not only the top and bottom.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            MediumTopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // The handbook is a page of the notebook, reached in words.
                    TextButton(
                        onClick = { onOpenHandbook(0) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("open-handbook"),
                    ) { Text(stringResource(R.string.notebook_techniques)) }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).testTag("notebook-list"),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + 16.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Items arrive together: inserting today above a visible card would leave the list
            // scrolled past today under the app bar.
            if (!state.loaded) return@LazyColumn
            state.todayPage?.let { page ->
                item(key = "today", contentType = "card") {
                    TodayCard(
                        page = page,
                        selected = selectedKey == "daily-${page.epochDay}",
                        onClick = { onOpenDay(page.epochDay) },
                        thumbnailModifier = boardTransition("daily-${page.epochDay}"),
                    )
                }
            }
            // The one page left open most recently, of any kind: the next thing to do after today.
            state.resume?.let { filed ->
                item(key = "continue", contentType = "row") {
                    val page = filed.page
                    ContinueRow(
                        filed = filed,
                        selected = page.key == selectedPageKey || page.key == selectedKey,
                        onClick = { openPage(page) },
                        // The practice page's board already belongs to the practice card.
                        thumbnailModifier = if (page.kind == PuzzleKind.Classic) Modifier else boardTransition(page.key),
                    )
                }
            }
            item(key = "practice", contentType = "card") {
                PracticeCard(
                    practice = state.practice,
                    selected = selectedKey == "practice",
                    onClick = onOpenPractice,
                    onTypeIn = onTypeIn,
                    thumbnailModifier = boardTransition("practice"),
                )
            }
            // The contents: a month at a time, the way back to any day and every page on it.
            item(key = "contents") {
                MonthContents(
                    pages = state.pages,
                    today = state.today,
                    first = state.firstDay,
                    onOpenDay = onOpenDay,
                    onOpenPage = openPage,
                    selectedKey = selectedPageKey,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            item(key = "footer") { Footer(versionName = versionName, onOpenLicences = onOpenLicences) }
        }
    }
}

/** How far text sits in from a card's edge; rows outside cards line up with it. */
private val TextInset = 20.dp

private val SheetShape = RoundedCornerShape(8.dp)

/**
 * A sheet of the notebook: 8dp corners and a hairline edge, a step off the page; a shade deeper
 * when it is the page open beside the notebook.
 */
// v11: PaperSheet
@Composable
internal fun NotebookSheet(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val color = if (selected) colors.surfaceContainerHigh else colors.surfaceContainerLow
    val border = BorderStroke(Dp.Hairline, colors.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = SheetShape, color = color, border = border, content = content)
    } else {
        Surface(modifier = modifier, shape = SheetShape, color = color, border = border, content = content)
    }
}

/** Cards shrink their page drawing at large text, so the words keep the width. */
@Composable
private fun thumbnailSize() = if (LocalDensity.current.fontScale >= 1.3f) 88.dp else 112.dp

/** How a day's own page stands: stamped (and how), begun, or blank. */
@Composable
internal fun pageStatus(page: PageEntry): String {
    val result = page.result
    return when {
        result != null && page.clean -> stringResource(R.string.page_stamped, formatElapsed(result.durationSeconds))
        result != null -> stringResource(R.string.page_stamped_coached, formatElapsed(result.durationSeconds))
        page.saved != null -> stringResource(R.string.page_in_progress)
        else -> stringResource(R.string.page_blank)
    }
}

/** A day's own page by name: its size and its date, "6×6 · October 21". */
internal fun dailyName(epochDay: Long, locale: Locale, size: Int = 6): String =
    "$size×$size" + DOT + monthDay(LocalDate.ofEpochDay(epochDay), locale)

/**
 * What a page is called in the notebook (11.0): a day's page and an old weekly or technique page
 * by size and date ("9×9 · October 5", [day] being the day it is filed under), a practice page by
 * its number, a typed-in page as the player's own. No level words.
 */
@Composable
internal fun pageName(page: PageSummary, day: Long, locale: Locale): String = when (page.kind) {
    PuzzleKind.Daily -> dailyName(page.epochDay ?: day, locale, page.size)
    PuzzleKind.Classic -> stringResource(R.string.practice) + DOT + stringResource(R.string.practice_page, (page.number ?: 0) + 1)
    PuzzleKind.Weekly, PuzzleKind.Focus -> "${page.size}×${page.size}" + DOT + monthDay(LocalDate.ofEpochDay(day), locale)
    PuzzleKind.Imported -> stringResource(R.string.imported)
}

@Composable
private fun TodayCard(
    page: PageEntry,
    selected: Boolean,
    onClick: () -> Unit,
    thumbnailModifier: Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.ofEpochDay(page.epochDay)
    val status = pageStatus(page)
    val label = spoken(stringResource(R.string.today), monthDay(date, locale), status)
    NotebookSheet(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .testTag("notebook-today")
            .semantics {
                contentDescription = label
                this.selected = selected
            },
    ) {
        val thumbnail = thumbnailSize()
        Row(Modifier.padding(TextInset), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                CardLabel(stringResource(R.string.today))
                // The numeral is the date; the line under it says the rest once.
                Text(date.dayOfMonth.toString(), style = PaperType.pageNumeral, color = MaterialTheme.colorScheme.onSurface)
                // Each language's own order: "Wednesday · October", 「十月 星期三」.
                CardSupport(stringResource(R.string.today_support, weekday(date, locale), month(date, locale)))
                Spacer(Modifier.height(8.dp))
                CardAction(status)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(thumbnail)) {
                PageThumbnail(page.saved, size = 6, modifier = thumbnailModifier.size(thumbnail))
                if (page.stamped) StampMark(page.clean, Modifier.align(Alignment.TopEnd), 28.dp)
            }
        }
    }
}

/** The one page to go back to: the most recent left unfinished, whatever kind it is. */
@Composable
private fun ContinueRow(
    filed: FiledPage,
    selected: Boolean,
    onClick: () -> Unit,
    thumbnailModifier: Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val page = filed.page
    val title = pageName(page, filed.day, locale)
    val label = spoken(stringResource(R.string.notebook_continue), title)
    Row(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clip(SheetShape)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = TextInset, vertical = 8.dp)
            .testTag("notebook-continue")
            .clearAndSetSemantics {
                contentDescription = label
                this.selected = selected
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            CardLabel(stringResource(R.string.notebook_continue))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(12.dp))
        PageThumbnail(page, size = page.size, modifier = thumbnailModifier.size(48.dp))
    }
}

/**
 * The practice stack, described only by the technique path: what the next pages teach and how
 * many techniques are known. A tap opens the practice page; typing in a page of one's own is a
 * quiet action under it.
 */
@Composable
private fun PracticeCard(
    practice: PracticeEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onTypeIn: () -> Unit,
    thumbnailModifier: Modifier,
) {
    val known = stringResource(R.string.practice_known, practice.known, Technique.entries.size)
    val learning = practice.learning?.let { stringResource(R.string.practice_learning, techniqueName(it)) }
    val status = if (practice.saved != null) {
        stringResource(R.string.practice_continue, (practice.saved.number ?: 0) + 1)
    } else {
        stringResource(R.string.practice_new_page)
    }
    val label = spoken(stringResource(R.string.practice), learning.orEmpty(), known, status)
    NotebookSheet(selected = selected, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        Column {
            val thumbnail = thumbnailSize()
            // The same build as today's card: label, the big line, what it means, what a tap does.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(TextInset)
                    .testTag("notebook-practice")
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                    CardLabel(stringResource(R.string.practice))
                    // With everything at this level known, the count is the big line.
                    Text(learning ?: known, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    if (learning != null) CardSupport(known)
                    Spacer(Modifier.height(8.dp))
                    CardAction(status)
                }
                Spacer(Modifier.width(12.dp))
                PageThumbnail(practice.saved, size = 9, modifier = thumbnailModifier.size(thumbnail))
            }
            HorizontalDivider(Modifier.padding(horizontal = TextInset), color = MaterialTheme.colorScheme.outlineVariant)
            // In ink at the card's text edge: a way to bring a page of one's own, not a red button.
            TextButton(
                onClick = onTypeIn,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .padding(start = TextInset - 12.dp, top = 4.dp, bottom = 4.dp)
                    .heightIn(min = 48.dp)
                    .testTag("notebook-type-in"),
            ) { Text(stringResource(R.string.imported_add)) }
        }
    }
}

/** The end of the notebook: which version, what it keeps to itself, and the licences. */
@Composable
private fun Footer(versionName: String, onOpenLicences: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(top = 24.dp)
            .testTag("notebook-footer"),
    ) {
        Text(
            stringResource(R.string.notebook_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = TextInset),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.notebook_version, versionName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = TextInset).weight(1f).testTag("notebook-version"),
            )
            TextButton(
                onClick = onOpenLicences,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.padding(end = TextInset - 12.dp).heightIn(min = 48.dp).testTag("notebook-licences"),
            ) { Text(stringResource(R.string.notebook_licences)) }
        }
    }
}

/** Card labels are ink, not the accent: red is for the Coach, the stamp and the digit in hand. */
@Composable
private fun CardLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CardSupport(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CardAction(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

/** A filled stamp for a page done without the Coach's answer, a ring when the Coach gave it. */
@Composable
private fun StampMark(clean: Boolean, modifier: Modifier, size: Dp) {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(size)
            .then(if (clean) Modifier.background(color, CircleShape) else Modifier.border(2.dp, color, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = if (clean) MaterialTheme.colorScheme.onPrimary else color,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}
