package com.titpea.papersudoku.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.titpea.papersudoku.R
import com.titpea.papersudoku.engine.Difficulty
import java.util.Locale

fun formatElapsed(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val hours = s / 3_600
    val minutes = (s % 3_600) / 60
    val seconds = s % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

fun difficultyRes(difficulty: Difficulty): Int = when (difficulty) {
    Difficulty.Gentle -> R.string.difficulty_gentle
    Difficulty.Steady -> R.string.difficulty_steady
    Difficulty.Tricky -> R.string.difficulty_tricky
}

@Composable
fun difficultyName(difficulty: Difficulty): String = stringResource(difficultyRes(difficulty))

/** Localized "October 21" / "10月21日" (no year). */
fun monthDay(date: java.time.LocalDate, locale: java.util.Locale): String =
    date.format(
        java.time.format.DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMMd"),
            locale,
        ),
    )

/** Localized weekday, e.g. "Wednesday" / "星期三". */
fun weekday(date: java.time.LocalDate, locale: java.util.Locale): String =
    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
