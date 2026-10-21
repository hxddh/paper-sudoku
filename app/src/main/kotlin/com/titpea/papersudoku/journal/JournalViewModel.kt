package com.titpea.papersudoku.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.titpea.papersudoku.AppContainer
import com.titpea.papersudoku.data.DayResult
import com.titpea.papersudoku.data.GameStore
import com.titpea.papersudoku.data.SettingsStore
import com.titpea.papersudoku.data.StatsRow
import com.titpea.papersudoku.engine.Technique
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

data class JournalUiState(
    val today: Long = 0,
    val days: Map<Long, DayResult> = emptyMap(),
    val stats: List<StatsRow> = emptyList(),
    val techniquesMet: Set<Technique> = emptySet(),
) {
    val pagesFilled: Int get() = days.size
    val solved: Int get() = stats.sumOf { it.solved }
}

class JournalViewModel(
    store: GameStore,
    settingsStore: SettingsStore,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<JournalUiState> = combine(store.days, store.stats, settingsStore.settings) { days, stats, settings ->
        JournalUiState(
            today = LocalDate.now(clock).toEpochDay(),
            days = days.associateBy { it.epochDay },
            stats = stats,
            techniquesMet = settings.techniquesMet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState(today = LocalDate.now(clock).toEpochDay()))

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { JournalViewModel(container.gameStore, container.settingsStore, container.clock) }
        }
    }
}
