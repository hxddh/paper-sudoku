package com.titpea.papersudoku.notebook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.designsystem.PaperType
import com.titpea.papersudoku.page.monthDay
import com.titpea.papersudoku.page.spoken
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * The notebook's contents page: one month at a time, a square per day. A filled dot is a clean
 * stamp, a ring a page done with the Coach's answer, a grey dot a page begun; today is circled.
 * No streak is counted — the month only shows what is there. A day opens with a tap.
 *
 * [first] is the notebook's first day (install or earliest page); months before it and after
 * today are not offered.
 */
@Composable
fun MonthContents(
    pages: Map<Long, PageEntry>,
    today: Long,
    first: Long,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val thisMonth = YearMonth.from(LocalDate.ofEpochDay(today))
    val firstMonth = YearMonth.from(LocalDate.ofEpochDay(minOf(first, today)))
    // Months back from this one; saved, so turning the phone keeps the month open.
    var back by rememberSaveable { mutableIntStateOf(0) }
    val month = thisMonth.minusMonths(back.toLong())
    val canGoBack = month.isAfter(firstMonth)
    val canGoForward = back > 0
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerLow,
        border = if (colors.background.luminance() < 0.5f) BorderStroke(1.dp, colors.outlineVariant) else null,
        modifier = modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("contents"),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { back++ }, enabled = canGoBack, modifier = Modifier.testTag("contents-previous")) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.contents_previous))
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "LLLLyyyy"), locale)),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = { back-- }, enabled = canGoForward, modifier = Modifier.testTag("contents-next")) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.contents_next))
                }
            }
            val weekStart = WeekFields.of(locale).firstDayOfWeek
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                for (i in 0 until 7) {
                    Text(
                        weekStart.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val firstOfMonth = month.atDay(1)
            val lead = ((firstOfMonth.dayOfWeek.value - weekStart.value) + 7) % 7
            val cells = lead + month.lengthOfMonth()
            for (week in 0 until (cells + 6) / 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (column in 0 until 7) {
                        val dayOfMonth = week * 7 + column - lead + 1
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (dayOfMonth in 1..month.lengthOfMonth()) {
                                val day = month.atDay(dayOfMonth).toEpochDay()
                                DayCell(
                                    day = day,
                                    entry = pages[day],
                                    isToday = day == today,
                                    openable = day in first..today,
                                    onOpen = { onOpenDay(day) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Long, entry: PageEntry?, isToday: Boolean, openable: Boolean, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.ofEpochDay(day)
    val status = when {
        entry == null -> ""
        entry.stamped && entry.clean -> stringResource(R.string.contents_clean)
        entry.stamped -> stringResource(R.string.contents_coached)
        entry.saved != null -> stringResource(R.string.page_in_progress)
        else -> ""
    }
    val label = spoken(monthDay(date, locale), if (isToday) stringResource(R.string.today) else "", status)
    Column(
        Modifier
            .heightIn(min = 48.dp)
            .fillMaxWidth()
            .then(if (openable) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .clearAndSetSemantics { if (openable) contentDescription = label }
            .testTag("contents-day-$day"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .then(if (isToday) Modifier.border(1.5.dp, colors.secondary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = PaperType.figure.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize, lineHeight = MaterialTheme.typography.labelLarge.lineHeight),
                color = when {
                    !openable -> colors.onSurfaceVariant.copy(alpha = 0.38f)
                    entry?.stamped == true -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
            )
        }
        // The mark: shape carries the meaning, so it reads without colour too.
        Box(Modifier.size(6.dp)) {
            when {
                entry == null -> Unit
                entry.stamped && entry.clean -> Box(Modifier.size(6.dp).background(colors.primary, CircleShape))
                entry.stamped -> Box(Modifier.size(6.dp).border(1.dp, colors.primary, CircleShape))
                entry.saved != null -> Box(Modifier.size(6.dp).background(colors.onSurfaceVariant.copy(alpha = 0.5f), CircleShape))
            }
        }
    }
}
