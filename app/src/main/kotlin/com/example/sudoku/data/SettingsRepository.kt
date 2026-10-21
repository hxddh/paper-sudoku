package com.example.sudoku.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode {
    System,
    Light,
    Dark;

    fun next(): ThemeMode = when (this) {
        System -> Light
        Light -> Dark
        Dark -> System
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.settingsDataStore

    val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            val stored = preferences[THEME_MODE]
            ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.System
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
