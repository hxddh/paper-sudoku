package com.titpea.papersudoku

import android.app.Application
import android.content.Context
import com.titpea.papersudoku.data.RoomStatsRepository
import com.titpea.papersudoku.data.SettingsRepository
import com.titpea.papersudoku.data.StatsRepository
import com.titpea.papersudoku.data.SudokuDatabase

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
