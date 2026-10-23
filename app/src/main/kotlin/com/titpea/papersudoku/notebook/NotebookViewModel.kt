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
import java.time.LocalDate

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
    /** Past days, newest first. */
    val pastDays: List<Long> = emptyList(),
    val pages: Map<Long, PageEntry> = emptyMap(),
    val stamped: Int = 0,
    val techniquesMet: Set<Technique> = emptySet(),
    /** False until the store has answered; the list stays empty so it opens at today, not below it. */
    val loaded: Boolean = true,
)

/**
 * The notebook is the app's home and its journal at once: today on top, the practice stack, the
 * technique handbook, then the earlier days. It starts on the day the app arrived (or the earliest
 * page it already holds), so a new notebook has no wall of blank days. There is no streak — only
 * stamped pages.
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
            // One row per day ever opened (projected columns only), so the whole notebook is one read.
            store.dailies(0),
            store.days,
            store.unfinishedPractice,
            store.cleanPractice,
            combine(settingsStore.settings, store.stampedCount, ::Pair),
        ) { dailies, days, practice, clean, (settings, stampedCount) ->
            val saved = dailies.associateBy { it.epochDay }
            val results = days.associateBy { it.epochDay }
            // A saved page says what it was dealt at; the calendar rule is for days not yet opened.
            fun entry(d: Long) = PageEntry(d, saved[d]?.difficulty ?: Catalog.dailyDifficulty(d), saved[d], results[d])
            // Pages restored from a backup may predate the install; they open the notebook instead.
            val start = minOf(
                firstDay,
                dailies.minOfOrNull { it.epochDay ?: day } ?: day,
                days.minOfOrNull { it.epochDay } ?: day,
            )
            val pastDays = (day - 1 downTo start).toList()
            NotebookUiState(
                today = day,
                todayPage = entry(day),
                // An unfinished page keeps the level it was dealt at, whatever the progression says now.
                practice = PracticeEntry(
                    level = practice?.difficulty ?: Practice.level(clean),
                    toNextLevel = Practice.remainingToAdvance(clean),
                    saved = practice,
                ),
                pastDays = pastDays,
                pages = (pastDays + day).associateWith(::entry),
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
