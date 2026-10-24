package com.titpea.papersudoku.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.titpea.papersudoku.AppContainer
import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.PageSummary
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Path
import com.titpea.papersudoku.game.model.Practice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * One day of the notebook: its own page, how that ended, and every other page filed under it.
 * Days absent from [NotebookUiState.pages] have nothing on them.
 */
data class PageEntry(
    val epochDay: Long,
    /** The day's own page, if it has been begun. */
    val saved: PageSummary?,
    val result: DayResult?,
    /**
     * Other pages (all 9×9) filed under this day: finished on it, or last written on it if not
     * finished. Most recent first.
     */
    val others: List<PageSummary> = emptyList(),
) {
    val stamped: Boolean get() = result != null
    val clean: Boolean get() = (result?.coachLevel ?: 3) < 3

    /** Something on this day is finished: its own page or another. */
    val finished: Boolean get() = stamped || saved?.solved == true || others.any { it.solved }

    /** Begun and not finished, and nothing finished beside it: a dash in the contents. */
    val unfinished: Boolean get() = !finished && (saved != null || others.isNotEmpty())

    /** The day's own page is always one of its pages, written on or not; a tap on a day with more asks which. */
    val pageCount: Int get() = 1 + others.size
}

/** The practice card (11.0): progress only along the technique path, never a level. */
data class PracticeEntry(
    /** What the next pages teach ("Learning: Naked pair"); null once all the level deals is known. */
    val learning: Technique?,
    /** Techniques known, of [Technique.entries] ("Known: 6 of 11"). */
    val known: Int,
    /** The unfinished page that will be continued, if any. */
    val saved: PageSummary?,
)

/** A page and the day it is filed under in the contents. */
data class FiledPage(val page: PageSummary, val day: Long)

data class NotebookUiState(
    val today: Long = 0,
    val todayPage: PageEntry? = null,
    val practice: PracticeEntry = PracticeEntry(Technique.HiddenSingle, 0, null),
    /** The notebook's first day: the install day, or an earlier page restored from a backup. */
    val firstDay: Long = 0,
    /** Every day with something on it (today always included); days absent were never written on. */
    val pages: Map<Long, PageEntry> = emptyMap(),
    /**
     * The most recently written page not finished, of any kind, other than today's own page (the
     * Today card has that): the one "Continue" row.
     */
    val resume: FiledPage? = null,
    /** False until the store has answered; the list stays empty so it opens at today, not below it. */
    val loaded: Boolean = true,
)

/**
 * The notebook is the app's home and its one archive (11.0): today on top, practice, the
 * contents, a footer. Every page ever written is on its day in the contents: a day's own page on
 * its date, any other page on the day it was finished, or last written on if it is not finished.
 * The contents reach every day since the notebook began (the install day, or the earliest page it
 * already holds). There is no streak — only stamped pages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModel(
    store: GameStore,
    settingsStore: SettingsStore,
    private val clock: Clock,
    /** The day the app was installed; the notebook's first page. */
    private val firstDay: Long,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock).toEpochDay())

    val state: StateFlow<NotebookUiState> = today.flatMapLatest { day ->
        combine(
            // One row per page ever begun (projected columns only), so the whole notebook is a few reads.
            store.dailies(0),
            store.days,
            store.unfinishedPractice,
            combine(store.imported, store.solvedNineByNine, store.unfinishedOthers, ::Triple),
            combine(store.cleanPractice, settingsStore.settings, ::Pair),
        ) { dailies, days, practice, (imported, finished, others), (clean, settings) ->
            val saved = dailies.associateBy { it.epochDay }
            val results = days.associateBy { it.epochDay }
            // Every page that is not a day's own, once: finished ones (typed-in pages included),
            // then the begun ones still open.
            val loose = (finished + listOfNotNull(practice) + imported.filter { !it.solved } + others).distinctBy { it.key }
            val filed = loose.groupBy { filedDay(it, day) }
            val written = (saved.keys.filterNotNull() + results.keys + filed.keys).filter { it <= day }.distinct()
            val pages = (written + day).associateWith { d ->
                PageEntry(d, saved[d], results[d], filed[d].orEmpty().sortedByDescending { it.updatedAt })
            }
            // An unfinished page keeps the level it was dealt at, whatever the progression says now.
            val level = practice?.difficulty ?: Practice.level(clean)
            NotebookUiState(
                today = day,
                todayPage = pages.getValue(day),
                practice = PracticeEntry(
                    learning = Path.learning(settings.learner, level),
                    known = Path.known(settings.learner),
                    saved = practice,
                ),
                // Pages restored from a backup may predate the install; they open the notebook instead.
                firstDay = minOf(firstDay, written.minOrNull() ?: day),
                pages = pages,
                resume = (
                    dailies.mapNotNull { page -> page.epochDay?.takeIf { !page.solved && it != day }?.let { FiledPage(page, it) } } +
                        loose.filter { !it.solved }.map { FiledPage(it, filedDay(it, day)) }
                    ).maxByOrNull { it.page.updatedAt },
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState(today = today.value, loaded = false))

    /** The day a page that is not a day's own is filed under: when it was finished or last written on. */
    private fun filedDay(page: PageSummary, today: Long): Long {
        if (page.updatedAt <= 0) return page.epochDay ?: today
        // Never after today: a clock set back must not hide a page beyond the last month offered.
        return minOf(Instant.ofEpochMilli(page.updatedAt).atZone(clock.zone).toLocalDate().toEpochDay(), today)
    }

    /** Call when the app returns to the foreground, in case the date changed. */
    fun refreshDate() {
        today.value = LocalDate.now(clock).toEpochDay()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotebookViewModel(container.gameStore, container.settingsStore, container.clock, container.firstDay)
            }
        }
    }
}
