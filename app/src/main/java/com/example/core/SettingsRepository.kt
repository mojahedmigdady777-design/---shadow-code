package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "shadow_code_settings")

data class AppSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val showEnglishSubtitles: Boolean = true,
    val lastNickname: String = ""
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SHOW_ENGLISH = booleanPreferencesKey("show_english")
        val LAST_NICKNAME = stringPreferencesKey("last_nickname")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            showEnglishSubtitles = prefs[Keys.SHOW_ENGLISH] ?: true,
            lastNickname = prefs[Keys.LAST_NICKNAME] ?: ""
        )
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setShowEnglishSubtitles(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_ENGLISH] = enabled }
    }

    suspend fun setLastNickname(name: String) {
        context.settingsDataStore.edit { it[Keys.LAST_NICKNAME] = name.take(24) }
    }
}
