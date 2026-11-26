package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "truebit_preferences")

data class UserPreferencesState(
    val isObsidianDarkTheme: Boolean = true,
    val isDeepSpectralScan: Boolean = true,
    val savedFolderUris: Set<String> = emptySet()
)

class ThemePreferencesRepository(private val context: Context) {

    private object Keys {
        val OBSIDIAN_DARK_THEME = booleanPreferencesKey("obsidian_dark_theme")
        val DEEP_SPECTRAL_SCAN = booleanPreferencesKey("deep_spectral_scan")
        val SAVED_FOLDER_URIS = stringSetPreferencesKey("saved_folder_uris")
    }

    val userPreferencesFlow: Flow<UserPreferencesState> = context.dataStore.data.map { prefs ->
        UserPreferencesState(
            isObsidianDarkTheme = prefs[Keys.OBSIDIAN_DARK_THEME] ?: true,
            isDeepSpectralScan = prefs[Keys.DEEP_SPECTRAL_SCAN] ?: true,
            savedFolderUris = prefs[Keys.SAVED_FOLDER_URIS] ?: emptySet()
        )
    }

    suspend fun setObsidianDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OBSIDIAN_DARK_THEME] = isDark
        }
    }

    suspend fun setDeepSpectralScan(isDeep: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEEP_SPECTRAL_SCAN] = isDeep
        }
    }

    suspend fun addSavedFolderUri(uriString: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SAVED_FOLDER_URIS] ?: emptySet()
            prefs[Keys.SAVED_FOLDER_URIS] = current + uriString
        }
    }
}
