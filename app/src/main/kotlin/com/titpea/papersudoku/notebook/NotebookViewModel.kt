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
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Catalog
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
import java.time.YearMonth

/** One page of the notebook: a day, what is written on it, and how it ended. */
data class PageEntry(
    val epochDay: Long,
    val difficulty: Difficulty,
    /** The saved page, if it has been opened. */
    val saved: PageSummary?,
    val result: DayResult?,
) {
    val stamped: Boolean get() = result != null
    val clean: Boolean get() = (result?.coachLevel ?: 3) < 3
}

data class PracticeEntry(
    val level: Difficulty,
    /** Clean pages still needed for the next level; null at the top. */
    val toNextLevel: Int?,
    /** The unfinished page that will be continued, if any. */
    val saved: PageSummary?,
)

data class NotebookUiState(
    val today: Long = 0,
    val todayPage: PageEntry? = null,
    val practice: PracticeEntry = PracticeEntry(Difficulty.Gentle, Practice.CLEAN_SOLVES_TO_ADVANCE, null),
    /** The notebook's first day: the install day, or an earlier page restored from a backup. */
    val firstDay: Long = 0,
    /** Earlier days begun and not finished, newest first. */
    val unfinished: List<Long> = emptyList(),
    /** Earlier days stamped, newest first. */
    val stampedDays: List<Long> = emptyList(),
    /** Every day with something on it (today included); days absent were never begun. */
    val pages: Map<Long, PageEntry> = emptyMap(),
    /** Practice pages finished, per month. */
    val practiceByMonth: Map<YearMonth, Int> = emptyMap(),
    val stamped: Int = 0,
    val techniquesMet: Set<Technique> = emptySet(),
    /** False until the store has answered; the list stays empty so it opens at today, not below it. */
    val loaded: Boolean = true,
)

/**
 * The notebook is the app's home and its journal at once: today on top, the practice stack, the
 * month's contents, the technique handbook, then the pages written on. The contents reach every
 * day since the notebook began (the install day, or the earliest page it already holds); the list
 * below holds only pages with something on them, so it never fills with blank days. There is no
 * streak — only stamped pages.
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
            // One row per day ever begun (projected columns only), so the whole notebook is one read.
            store.dailies(0),
            store.days,
            store.unfinishedPractice,
            store.cleanPractice,
            combine(settingsStore.settings, store.stampedCount, store.practiceCompletions, ::Triple),
        ) { dailies, days, practice, clean, (settings, stampedCount, completions) ->
            val saved = dailies.associateBy { it.epochDay }
            val results = days.associateBy { it.epochDay }
            // A saved page says what it was dealt at; the calendar rule is for days not yet opened.
            fun entry(d: Long) = PageEntry(d, saved[d]?.difficulty ?: Catalog.dailyDifficulty(d), saved[d], results[d])
            val written = (saved.keys.filterNotNull() + results.keys).filter { it < day }.distinct().sortedDescending()
            val pages = (written + day).associateWith(::entry)
            NotebookUiState(
                today = day,
                todayPage = entry(day),
                // An unfinished page keeps the level it was dealt at, whatever the progression says now.
                practice = PracticeEntry(
                    level = practice?.difficulty ?: Practice.level(clean),
                    toNextLevel = Practice.remainingToAdvance(clean),
                    saved = practice,
                ),
                // Pages restored from a backup may predate the install; they open the notebook instead.
                firstDay = minOf(firstDay, written.lastOrNull() ?: day),
                unfinished = written.filter { pages.getValue(it).let { p -> !p.stamped && p.saved != null } },
                stampedDays = written.filter { pages.getValue(it).stamped },
                pages = pages,
                practiceByMonth = completions
                    .groupingBy { YearMonth.from(Instant.ofEpochMilli(it).atZone(clock.zone)) }
                    .eachCount(),
                stamped = stampedCount,
                techniquesMet = settings.techniquesMet,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState(today = today.value, loaded = false))

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
