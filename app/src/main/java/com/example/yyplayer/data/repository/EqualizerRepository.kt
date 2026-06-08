package com.example.yyplayer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.equalizerDataStore: DataStore<Preferences> by preferencesDataStore(name = "equalizer_settings")

class EqualizerRepository(private val context: Context) {

    companion object {
        private val PRESET_KEY = stringPreferencesKey("eq_preset")
        private const val DEFAULT_PRESET = "normal"
    }

    val currentPreset: Flow<String> = context.equalizerDataStore.data.map { prefs ->
        prefs[PRESET_KEY] ?: DEFAULT_PRESET
    }

    suspend fun setPreset(preset: String) {
        context.equalizerDataStore.edit { prefs ->
            prefs[PRESET_KEY] = preset
        }
    }

    suspend fun getCurrentPreset(): String {
        return context.equalizerDataStore.data.first()[PRESET_KEY] ?: DEFAULT_PRESET
    }
}
