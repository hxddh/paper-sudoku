package com.titpea.papersudoku.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.titpea.papersudoku.engine.Technique
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { System, Light, Dark }

data class UserSettings(
    val theme: ThemeMode = ThemeMode.System,
    /** Material You colors instead of Paper; Android 12+. */
    val dynamicColor: Boolean = false,
    val haptics: Boolean = true,
    val autoClearNotes: Boolean = true,
    /** Off by default: on paper nobody tells you a digit is wrong. */
    val showMistakes: Boolean = false,
    /** Key of the puzzle on the desk when the app last closed. */
    val activeKey: String? = null,
    /** Techniques met in solved puzzles or explained by the Coach. */
    val techniquesMet: Set<Technique> = emptySet(),
)

interface SettingsStore {
    val settings: Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

class DataStoreSettings(context: Context) : SettingsStore {

    private val dataStore = context.settingsDataStore

    override val settings: Flow<UserSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { p ->
            val next = transform(p.toSettings())
            p[THEME_MODE] = next.theme.name
            p[DYNAMIC_COLOR] = next.dynamicColor
            p[HAPTICS] = next.haptics
            p[AUTO_CLEAR_NOTES] = next.autoClearNotes
            p[SHOW_MISTAKES] = next.showMistakes
            if (next.activeKey != null) p[ACTIVE_KEY] = next.activeKey else p.remove(ACTIVE_KEY)
            p[TECHNIQUES_MET] = next.techniquesMet.map { it.name }.toSet()
        }
    }

    private fun Preferences.toSettings() = UserSettings(
        theme = ThemeMode.entries.firstOrNull { it.name == this[THEME_MODE] } ?: ThemeMode.System,
        dynamicColor = this[DYNAMIC_COLOR] ?: false,
        haptics = this[HAPTICS] ?: true,
        autoClearNotes = this[AUTO_CLEAR_NOTES] ?: true,
        showMistakes = this[SHOW_MISTAKES] ?: false,
        activeKey = this[ACTIVE_KEY],
        techniquesMet = this[TECHNIQUES_MET].orEmpty()
            .mapNotNull { name -> Technique.entries.firstOrNull { it.name == name } }
            .toSet(),
    )

    private companion object {
        // "theme_mode" is the v1 key; its values (System/Light/Dark) are unchanged.
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HAPTICS = booleanPreferencesKey("haptics")
        val AUTO_CLEAR_NOTES = booleanPreferencesKey("auto_clear_notes")
        val SHOW_MISTAKES = booleanPreferencesKey("show_mistakes")
        val ACTIVE_KEY = stringPreferencesKey("active_key")
        val TECHNIQUES_MET = stringSetPreferencesKey("techniques_met")
    }
}
