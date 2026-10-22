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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
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
 * The notebook: today's page on top, the practice stack, then every earlier day, and the
 * technique handbook at the back. Tapping a page opens it.
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
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
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
            modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            item(key = "earlier") { SectionTitle(stringResource(R.string.notebook_earlier)) }
            items(state.pastDays, key = { it }) { day ->
                val page = state.pages[day] ?: return@items
                PageRow(
                    page = page,
                    selected = selectedKey == "daily-$day",
                    onClick = { onOpenDay(day) },
                    thumbnailModifier = boardTransition("daily-$day"),
                )
            }
            item(key = "techniques") { SectionTitle(stringResource(R.string.notebook_techniques)) }
            items(Technique.entries, key = { it.name }) { technique ->
                TechniqueRow(technique, technique in state.techniquesMet)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

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
    val label = stringResource(R.string.today) + ", " + monthDay(date, locale) + ", " + status
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .testTag("notebook-today")
            .semantics {
                contentDescription = label
                this.selected = selected
            },
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                Text(
                    stringResource(R.string.today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(date.dayOfMonth.toString(), style = PaperType.pageNumeral, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    weekday(date, locale) + " · " + monthDay(date, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    difficultyName(page.difficulty) + " · " + status,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(Modifier.size(128.dp)) {
                PageThumbnail(page.saved, size = 6, modifier = thumbnailModifier.size(128.dp))
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
        stringResource(R.string.practice_to_next, next)
    }
    val status = if (practice.saved != null) {
        stringResource(R.string.practice_continue, (practice.saved.number ?: 0) + 1)
    } else {
        stringResource(R.string.practice_new_page)
    }
    val label = stringResource(R.string.practice) + ", " + difficultyName(practice.level) + ", " + status + ", " + progress
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .testTag("notebook-practice")
            .semantics {
                contentDescription = label
                this.selected = selected
            },
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                Text(
                    stringResource(R.string.practice),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(difficultyName(practice.level), style = MaterialTheme.typography.headlineSmall)
                Text(progress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.labelLarge)
            }
            PageThumbnail(practice.saved, size = 9, modifier = thumbnailModifier.size(96.dp))
        }
    }
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
        headlineContent = { Text(weekday(date, locale) + " · " + monthDay(date, locale)) },
        supportingContent = { Text(difficultyName(page.difficulty) + " · " + status) },
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
            Icons.Default.Check,
            contentDescription = null,
            tint = if (clean) MaterialTheme.colorScheme.onPrimary else color,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
private fun TechniqueRow(technique: Technique, met: Boolean) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        leadingContent = {
            Box(
                Modifier
                    .size(24.dp)
                    .then(
                        if (met) Modifier.background(colors.tertiaryContainer, CircleShape)
                        else Modifier.border(1.dp, colors.outlineVariant, CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (met) Icon(Icons.Default.Check, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.size(16.dp))
            }
        },
        headlineContent = {
            Text(
                techniqueName(technique) + " · " +
                    stringResource(if (met) R.string.technique_met else R.string.technique_not_met),
                color = if (met) colors.onSurface else colors.onSurfaceVariant,
            )
        },
        supportingContent = { Text(techniqueSummary(technique)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
    )
}
