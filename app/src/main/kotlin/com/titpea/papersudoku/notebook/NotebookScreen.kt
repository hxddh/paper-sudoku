package com.titpea.papersudoku.notebook

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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.page.spoken
import com.titpea.papersudoku.page.month
import com.titpea.papersudoku.page.DOT
import com.titpea.papersudoku.designsystem.SectionHeader
import com.titpea.papersudoku.designsystem.PaperType
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.page.difficultyName
import com.titpea.papersudoku.page.formatElapsed
import com.titpea.papersudoku.page.monthDay
import com.titpea.papersudoku.page.techniqueName
import com.titpea.papersudoku.page.techniqueSummary
import com.titpea.papersudoku.page.weekday
import java.time.LocalDate

/**
 * The notebook: today's page on top, the practice stack, the technique handbook, then every
 * earlier day. Tapping a page opens it.
 *
 * One left edge runs down the page: card text, section titles, the summary and the rows all
 * start [TextInset] in from the cards' edge.
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
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    selectedKey: String? = null,
    boardTransition: @Composable (key: String) -> Modifier = { Modifier },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection).testTag("notebook"),
        containerColor = Color.Transparent,
        // Landscape cutouts and side navigation bars are kept clear, not only the top and bottom.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LargeTopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open-settings")) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                    }
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
            modifier = Modifier.fillMaxSize().testTag("notebook-list"),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + 16.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Items arrive together: inserting today above a visible card would leave the list
            // scrolled past today under the app bar.
            if (!state.loaded) return@LazyColumn
            state.todayPage?.let { page ->
                item(key = "today") {
                    TodayCard(
                        page = page,
                        selected = selectedKey == "daily-${page.epochDay}",
                        onClick = { onOpenDay(page.epochDay) },
                        thumbnailModifier = boardTransition("daily-${page.epochDay}"),
                    )
                }
            }
            item(key = "practice") {
                PracticeCard(
                    practice = state.practice,
                    selected = selectedKey == "practice",
                    onClick = onOpenPractice,
                    thumbnailModifier = boardTransition("practice"),
                )
            }
            item(key = "summary") {
                Text(
                    if (state.stamped == 0) {
                        stringResource(R.string.notebook_summary_empty)
                    } else {
                        pluralStringResource(R.plurals.notebook_summary, state.stamped, state.stamped)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = TextInset, vertical = 8.dp),
                )
            }
            // The handbook sits above the days, so it never drifts to the end of a long notebook.
            item(key = "techniques") { SectionTitle(stringResource(R.string.notebook_techniques)) }
            item(key = "handbook") { Handbook(state.techniquesMet) }
            if (state.pastDays.isNotEmpty()) {
                item(key = "earlier") { SectionTitle(stringResource(R.string.notebook_earlier)) }
            }
            items(state.pastDays, key = { it }) { day ->
                val page = state.pages[day] ?: return@items
                PageRow(
                    page = page,
                    selected = selectedKey == "daily-$day",
                    onClick = { onOpenDay(day) },
                    thumbnailModifier = boardTransition("daily-$day"),
                )
            }
        }
    }
}

/** How far text sits in from a card's edge; titles and rows outside cards line up with it. */
private val TextInset = 20.dp

@Composable
private fun SectionTitle(text: String) {
    SectionHeader(
        text,
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(start = TextInset, top = 16.dp, bottom = 4.dp),
    )
}

/** Cards: a step off the page, and edged at night, where tone alone barely separates them. */
@Composable
private fun cardColor(selected: Boolean) =
    if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow

@Composable
private fun cardBorder(): BorderStroke? =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }

/** Cards shrink their page drawing at large text, so the words keep the width. */
@Composable
private fun thumbnailSize() = if (LocalDensity.current.fontScale >= 1.3f) 88.dp else 112.dp

@Composable
private fun pageStatus(page: PageEntry): String {
    val result = page.result
    return when {
        result != null && page.clean -> stringResource(R.string.page_stamped, formatElapsed(result.durationSeconds))
        result != null -> stringResource(R.string.page_stamped_coached, formatElapsed(result.durationSeconds))
        page.saved != null -> stringResource(R.string.page_in_progress)
        else -> stringResource(R.string.page_blank)
    }
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
    val label = spoken(stringResource(R.string.today), monthDay(date, locale), difficultyName(page.difficulty), status)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = cardColor(selected),
        border = cardBorder(),
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
                CardSupport(weekday(date, locale) + DOT + month(date, locale))
                Spacer(Modifier.height(8.dp))
                CardAction(difficultyName(page.difficulty) + DOT + status)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(thumbnail)) {
                PageThumbnail(page.saved, size = 6, modifier = thumbnailModifier.size(thumbnail))
                if (page.stamped) StampMark(page.clean, Modifier.align(Alignment.TopEnd), 28.dp)
            }
        }
    }
}

@Composable
private fun PracticeCard(
    practice: PracticeEntry,
    selected: Boolean,
    onClick: () -> Unit,
    thumbnailModifier: Modifier,
) {
    val next = practice.toNextLevel
    val progress = if (next == null) {
        stringResource(R.string.practice_top_level)
    } else {
        pluralStringResource(R.plurals.practice_to_next, next, next)
    }
    val status = if (practice.saved != null) {
        stringResource(R.string.practice_continue, (practice.saved.number ?: 0) + 1)
    } else {
        stringResource(R.string.practice_new_page)
    }
    val label = spoken(stringResource(R.string.practice), difficultyName(practice.level), status, progress)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = cardColor(selected),
        border = cardBorder(),
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .testTag("notebook-practice")
            .semantics {
                contentDescription = label
                this.selected = selected
            },
    ) {
        val thumbnail = thumbnailSize()
        // The same build as today's card: label, the big line, what it means, what a tap does.
        Row(Modifier.padding(TextInset), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                CardLabel(stringResource(R.string.practice))
                Text(difficultyName(practice.level), style = MaterialTheme.typography.headlineMedium)
                CardSupport(progress)
                Spacer(Modifier.height(8.dp))
                CardAction(status)
            }
            Spacer(Modifier.width(12.dp))
            PageThumbnail(practice.saved, size = 9, modifier = thumbnailModifier.size(thumbnail))
        }
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CardSupport(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CardAction(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun PageRow(
    page: PageEntry,
    selected: Boolean,
    onClick: () -> Unit,
    thumbnailModifier: Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.ofEpochDay(page.epochDay)
    val status = pageStatus(page)
    ListItem(
        leadingContent = {
            Text(
                date.dayOfMonth.toString(),
                style = PaperType.figure,
                color = if (page.stamped) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(44.dp),
            )
        },
        headlineContent = { Text(weekday(date, locale) + DOT + monthDay(date, locale)) },
        supportingContent = { Text(difficultyName(page.difficulty) + DOT + status) },
        trailingContent = {
            Box(Modifier.size(48.dp)) {
                PageThumbnail(page.saved, size = 6, modifier = thumbnailModifier.size(48.dp))
                if (page.stamped) StampMark(page.clean, Modifier.align(Alignment.TopEnd), 16.dp)
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        ),
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            // ListItem's own 16dp plus this lines the day up with the cards' text.
            .padding(horizontal = TextInset - 16.dp)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .testTag("page-row-${page.epochDay}"),
    )
}

/** A filled stamp for a page done without the Coach's answer, a ring when the Coach gave it. */
@Composable
private fun StampMark(clean: Boolean, modifier: Modifier, size: androidx.compose.ui.unit.Dp) {
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

/**
 * The seven techniques on one card, in learning order. A ring fills once a page has needed the
 * technique; only those say "Seen", so the rest stay quiet.
 */
@Composable
private fun Handbook(met: Set<Technique>) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = cardBorder(),
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("handbook"),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Technique.entries.forEach { TechniqueRow(it, it in met) }
        }
    }
}

@Composable
private fun TechniqueRow(technique: Technique, met: Boolean) {
    val colors = MaterialTheme.colorScheme
    val seen = stringResource(R.string.technique_seen)
    val name = techniqueName(technique)
    val summary = techniqueSummary(technique)
    val label = spoken(name, if (met) seen else "", summary)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TextInset, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = label }
            .testTag("technique-${technique.name}"),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(20.dp)
                .then(
                    if (met) Modifier.background(colors.tertiaryContainer, CircleShape)
                    else Modifier.border(1.dp, colors.outlineVariant, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (met) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (met) colors.onSurface else colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (met) {
                    Spacer(Modifier.width(8.dp))
                    Text(seen, style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
                }
            }
            Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

