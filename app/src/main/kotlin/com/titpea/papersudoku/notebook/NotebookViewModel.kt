package com.titpea.papersudoku.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.titpea.papersudoku.AppContainer
import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.engine.Difficulty
import com.titpea.papersudoku.engine.Technique
import com.titpea.papersudoku.game.model.Catalog
import com.titpea.papersudoku.game.model.GameState
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
    val game: GameState?,
    val result: DayResult?,
) {
    val stamped: Boolean get() = result != null
    val clean: Boolean get() = (result?.coachLevel ?: 3) < 3
}

data class PracticeEntry(
    val level: Difficulty,
    /** Clean pages still needed for the next level; null at the top. */
    val toNextLevel: Int?,
    val game: GameState?,
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
)

/**
 * The notebook is the app's home and its journal at once: today on top, the practice stack, then
 * every earlier day. There is no streak — only stamped pages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotebookViewModel(
    store: GameStore,
    settingsStore: SettingsStore,
    private val clock: Clock,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock).toEpochDay())

    val state: StateFlow<NotebookUiState> = today.flatMapLatest { day ->
        combine(
            store.dailies(day - DAYS_SHOWN),
            store.days,
            store.unfinishedPractice,
            store.cleanPractice,
            settingsStore.settings,
        ) { dailies, days, practice, clean, settings ->
            val saved = dailies.associateBy { it.spec.epochDay }
            val results = days.associateBy { it.epochDay }
            fun entry(d: Long) = PageEntry(d, Catalog.dailyDifficulty(d), saved[d], results[d])
            val pastDays = (1..DAYS_SHOWN).map { day - it }
            NotebookUiState(
                today = day,
                todayPage = entry(day),
                practice = PracticeEntry(Practice.level(clean), Practice.remainingToAdvance(clean), practice),
                pastDays = pastDays,
                pages = (pastDays + day).associateWith(::entry),
                stamped = days.size,
                techniquesMet = settings.techniquesMet,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookUiState(today = today.value))

    /** Call when the app returns to the foreground, in case the date changed. */
    fun refreshDate() {
        today.value = LocalDate.now(clock).toEpochDay()
    }

    companion object {
        const val DAYS_SHOWN = 365L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { NotebookViewModel(container.gameStore, container.settingsStore, container.clock) }
        }
    }
}
