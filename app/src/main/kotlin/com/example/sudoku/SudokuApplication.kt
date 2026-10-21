package com.example.sudoku

import android.app.Application
import android.content.Context
import com.example.sudoku.data.RoomStatsRepository
import com.example.sudoku.data.SettingsRepository
import com.example.sudoku.data.StatsRepository
import com.example.sudoku.data.SudokuDatabase

interface AppContainer {
    val settingsRepository: SettingsRepository
    val statsRepository: StatsRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val database = SudokuDatabase.create(context)

    override val settingsRepository = SettingsRepository(context)
    override val statsRepository = RoomStatsRepository(database.gameResultDao())
}

class SudokuApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
