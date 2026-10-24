package com.titpea.papersudoku.notebook

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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titpea.papersudoku.R
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.designsystem.PaperType
import com.titpea.papersudoku.page.DOT
import com.titpea.papersudoku.page.monthDay
import com.titpea.papersudoku.page.spoken
import com.titpea.papersudoku.page.weekday
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/**
 * The notebook's contents, and its one archive (11.0): a month at a time, a square per day, and
 * every page ever written on its day. A filled dot is a stamp, a ring a day's page done with the
 * Coach's answer, a dash a page begun and not finished; today is circled. Each day says its own
 * marks to TalkBack; there is no key. The month's tally counts pages, not days in a row — there is
 * no streak.
 *
 * A tap on a day with only its own page opens it; a day with more lists them under the grid to
 * choose from. [first] is the notebook's first day (install or earliest page); months before it
 * and after today are not offered.
 */
@Composable
fun MonthContents(
    pages: Map<Long, PageEntry>,
    today: Long,
    first: Long,
    onOpenDay: (Long) -> Unit,
    onOpenPage: (PageSummary) -> Unit,
    modifier: Modifier = Modifier,
    /** The open page's key, to mark it among a day's pages. */
    selectedKey: String? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val thisMonth = YearMonth.from(LocalDate.ofEpochDay(today))
    val firstMonth = YearMonth.from(LocalDate.ofEpochDay(minOf(first, today)))
    // Months back from this one; saved, so turning the phone keeps the month open.
    var back by rememberSaveable { mutableIntStateOf(0) }
    // The day whose pages are listed under the grid, one day at a time.
    var chosen by rememberSaveable { mutableStateOf<Long?>(null) }
    val month = thisMonth.minusMonths(back.toLong())
    val canGoBack = month.isAfter(firstMonth)
    val canGoForward = back > 0
    val colors = MaterialTheme.colorScheme

    NotebookSheet(modifier = modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("contents")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        back++
                        chosen = null
                    },
                    enabled = canGoBack,
                    modifier = Modifier.testTag("contents-previous"),
                ) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.contents_previous))
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "LLLLyyyy"), locale)),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(
                    onClick = {
                        back--
                        chosen = null
                    },
                    enabled = canGoForward,
                    modifier = Modifier.testTag("contents-next"),
                ) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.contents_next))
                }
            }
            // Pages finished this month, of every kind: a day's own stamp and any other page.
            val finished = pages.values
                .filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
                .sumOf { (if (it.stamped) 1 else 0) + it.others.count { page -> page.solved } }
            if (finished > 0) {
                Text(
                    pluralStringResource(R.plurals.contents_month_stamped, finished, finished),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("contents-tally"),
                )
            }
            val weekStart = WeekFields.of(locale).firstDayOfWeek
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                for (i in 0 until 7) {
                    Text(
                        weekStart.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium,
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
                                val entry = pages[day]
                                DayCell(
                                    day = day,
                                    entry = entry,
                                    isToday = day == today,
                                    chosen = day == chosen,
                                    openable = day in first..today,
                                    onOpen = {
                                        if (entry == null || entry.pageCount == 1) {
                                            chosen = null
                                            onOpenDay(day)
                                        } else {
                                            chosen = if (chosen == day) null else day
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            val listed = chosen?.let { pages[it] }
            if (listed != null) {
                DayPages(
                    entry = listed,
                    isToday = listed.epochDay == today,
                    selectedKey = selectedKey,
                    onOpenDay = onOpenDay,
                    onOpenPage = onOpenPage,
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** A day with more than one page, listed under the grid: its own page first, then the others. */
@Composable
private fun DayPages(
    entry: PageEntry,
    isToday: Boolean,
    selectedKey: String?,
    onOpenDay: (Long) -> Unit,
    onOpenPage: (PageSummary) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.ofEpochDay(entry.epochDay)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("contents-day-pages")) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            (if (isToday) stringResource(R.string.today) else weekday(date, locale)) + DOT + monthDay(date, locale),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
        )
        val ownKey = "daily-${entry.epochDay}"
        DayPageRow(
            title = dailyName(entry.epochDay, locale, entry.saved?.size ?: 6),
            status = pageStatus(entry),
            page = entry.saved,
            size = 6,
            selected = selectedKey == ownKey,
            tag = "contents-page-$ownKey",
            onClick = { onOpenDay(entry.epochDay) },
        )
        entry.others.forEach { page ->
            DayPageRow(
                title = pageName(page, entry.epochDay, locale),
                status = stringResource(if (page.solved) R.string.widget_stamped else R.string.page_in_progress),
                page = page,
                size = 9,
                selected = selectedKey == page.key,
                tag = "contents-page-${page.key}",
                onClick = { onOpenPage(page) },
            )
        }
    }
}

@Composable
private fun DayPageRow(
    title: String,
    status: String,
    page: PageSummary?,
    size: Int,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val label = spoken(title, status)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag(tag)
            .clearAndSetSemantics {
                contentDescription = label
                this.selected = selected
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        PageThumbnail(page, size = size, modifier = Modifier.size(40.dp))
    }
}

private enum class DayMark { Clean, Coached, Begun }

/** Shape carries the meaning, so it reads without colour too. */
@Composable
private fun Mark(mark: DayMark?) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
        when (mark) {
            DayMark.Clean -> Box(Modifier.size(8.dp).background(colors.primary, CircleShape))
            DayMark.Coached -> Box(Modifier.size(8.dp).border(1.5.dp, colors.primary, CircleShape))
            // A short dash, not a smaller dot: told apart by shape, not by size and colour alone.
            DayMark.Begun -> Box(Modifier.size(width = 8.dp, height = 2.dp).background(colors.onSurfaceVariant, RoundedCornerShape(1.dp)))
            null -> Unit
        }
    }
}

/**
 * A day's mark: its own page's stamp (clean, or with the answer) if it has one, else a stamp for
 * any other page finished on it, else a dash for a page begun.
 */
private fun markOf(entry: PageEntry?): DayMark? = when {
    entry == null -> null
    entry.stamped && entry.clean -> DayMark.Clean
    entry.stamped -> DayMark.Coached
    entry.finished -> DayMark.Clean
    entry.unfinished -> DayMark.Begun
    else -> null
}

@Composable
private fun DayCell(day: Long, entry: PageEntry?, isToday: Boolean, chosen: Boolean, openable: Boolean, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.ofEpochDay(day)
    val mark = markOf(entry)
    val status = when (mark) {
        DayMark.Clean -> stringResource(if (entry?.stamped == true) R.string.contents_clean else R.string.widget_stamped)
        DayMark.Coached -> stringResource(R.string.contents_coached)
        DayMark.Begun -> stringResource(R.string.page_in_progress)
        null -> ""
    }
    val count = entry?.pageCount?.takeIf { it > 1 }?.let { pluralStringResource(R.plurals.contents_pages, it, it) }.orEmpty()
    val label = spoken(monthDay(date, locale), if (isToday) stringResource(R.string.today) else "", status, count)
    Column(
        Modifier
            .heightIn(min = 56.dp)
            .fillMaxWidth()
            .then(if (chosen) Modifier.background(colors.surfaceContainerHigh, RoundedCornerShape(8.dp)) else Modifier)
            .then(if (openable) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            // Tagged before the semantics are replaced, so tests and UI Automator still find it.
            .testTag("contents-day-$day")
            .clearAndSetSemantics {
                if (openable) contentDescription = label
                selected = chosen
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                .then(if (isToday) Modifier.border(1.5.dp, colors.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                // Fraunces at 28 for dates in lists and the contents.
                style = PaperType.figure.copy(fontSize = 28.sp, lineHeight = 32.sp),
                color = when {
                    !openable -> colors.onSurfaceVariant.copy(alpha = 0.38f)
                    entry?.finished == true -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false,
            )
        }
        Mark(mark)
    }
}
