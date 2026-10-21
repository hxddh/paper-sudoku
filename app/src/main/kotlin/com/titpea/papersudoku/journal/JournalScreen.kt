package com.titpea.papersudoku.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.RoomGameStore
import com.titpea.papersudoku.data.StatsRow
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.difficultyRes
import com.titpea.papersudoku.game.formatDate
import com.titpea.papersudoku.game.formatElapsed
import com.titpea.papersudoku.game.techniqueName
import com.titpea.papersudoku.game.techniqueSummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * A notebook, not a scoreboard: each solved Daily stamps its page. Filled stamps are pages done
 * without the Coach's answer; rings are pages the Coach helped with. There is no streak.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    state: JournalUiState,
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("journal"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Figure(stringResource(R.string.journal_pages), state.pagesFilled.toString(), Modifier.weight(1f))
                    Figure(stringResource(R.string.journal_solved), state.solved.toString(), Modifier.weight(1f))
                }
            }
            item { Calendar(state, onOpenDay, Modifier.widthIn(max = 640.dp)) }
            item { SectionTitle(stringResource(R.string.journal_best)) }
            if (state.stats.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.journal_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    )
                }
            }
            items(state.stats) { row -> BestRow(row, Modifier.widthIn(max = 640.dp)) }
            item { SectionTitle(stringResource(R.string.journal_techniques)) }
            items(Technique.entries) { technique ->
                TechniqueRow(technique, technique in state.techniquesMet, Modifier.widthIn(max = 640.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.displaySmall)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Calendar(state: JournalUiState, onOpenDay: (Long) -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.ofEpochDay(state.today)
    var monthIndex by rememberSaveable { mutableIntStateOf(today.year * 12 + today.monthValue - 1) }
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val colors = MaterialTheme.colorScheme

    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLowest) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthIndex-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.journal_prev_month))
                }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)} ${month.year}",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(
                    onClick = { monthIndex++ },
                    enabled = month.isBefore(YearMonth.from(today)),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.journal_next_month))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val dow = firstDayOfWeek.plus(i.toLong())
                    Text(
                        dow.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val first = month.atDay(1)
            val offset = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, 7)
            val cells = offset + month.lengthOfMonth()
            val weeks = (cells + 6) / 7
            for (week in 0 until weeks) {
                Row(Modifier.fillMaxWidth()) {
                    for (d in 0 until 7) {
                        val dayNumber = week * 7 + d - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (dayNumber in 1..month.lengthOfMonth()) {
                                val date = month.atDay(dayNumber)
                                DayCell(
                                    date = date,
                                    result = state.days[date.toEpochDay()],
                                    isToday = date == today,
                                    enabled = !date.isAfter(today),
                                    onClick = { onOpenDay(date.toEpochDay()) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.journal_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    result: DayResult?,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val clean = result != null && result.coachLevel < 3
    val status = stringResource(
        when {
            result == null -> if (enabled) R.string.day_open else R.string.day_future
            clean -> R.string.day_clean
            else -> R.string.day_coached
        },
    )
    val description = "${formatDate(date)}, $status"
    Box(
        modifier = Modifier
            .padding(3.dp)
            .fillMaxSize()
            .then(
                when {
                    clean -> Modifier.background(colors.primary, CircleShape)
                    result != null -> Modifier.border(2.dp, colors.primary, CircleShape)
                    isToday -> Modifier.border(1.5.dp, colors.secondary, CircleShape)
                    else -> Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                clean -> colors.onPrimary
                !enabled -> colors.onSurfaceVariant.copy(alpha = 0.5f)
                else -> colors.onSurface
            },
        )
    }
}

@Composable
private fun BestRow(row: StatsRow, modifier: Modifier = Modifier) {
    val mode = stringResource(if (row.mode == RoomGameStore.MODE_MINI) R.string.daily_mini else R.string.classic)
    val difficulty = Difficulty.entries.firstOrNull { it.name == row.difficulty }
    val label = if (difficulty != null) "$mode · ${stringResource(difficultyRes(difficulty))}" else mode
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.journal_solved_count, row.solved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            row.bestSeconds?.let(::formatElapsed) ?: "—",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun TechniqueRow(technique: Technique, met: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .then(
                    if (met) Modifier.background(colors.tertiaryContainer, CircleShape)
                    else Modifier.border(1.dp, colors.outlineVariant, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (met) Icon(Icons.Default.Check, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                techniqueName(technique) + " · " +
                    stringResource(if (met) R.string.technique_met else R.string.technique_not_met),
                style = MaterialTheme.typography.titleMedium,
                color = if (met) colors.onSurface else colors.onSurfaceVariant,
            )
            Text(techniqueSummary(technique), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
