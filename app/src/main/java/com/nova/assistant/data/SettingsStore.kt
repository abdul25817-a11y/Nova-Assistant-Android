package com.nova.assistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nova_settings")

data class NovaSettings(
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "local",
    val wakeWordEnabled: Boolean = true
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val WAKE = booleanPreferencesKey("wake_enabled")
    }

    val settings: Flow<NovaSettings> = context.dataStore.data.map { p ->
        NovaSettings(
            apiKey = p[Keys.API_KEY] ?: "",
            baseUrl = p[Keys.BASE_URL] ?: "",
            model = p[Keys.MODEL] ?: "local",
            wakeWordEnabled = p[Keys.WAKE] ?: true
        )
    }

    suspend fun save(settings: NovaSettings) {
        context.dataStore.edit { p ->
            p[Keys.API_KEY] = settings.apiKey
            p[Keys.BASE_URL] = settings.baseUrl
            p[Keys.MODEL] = settings.model
            p[Keys.WAKE] = settings.wakeWordEnabled
        }
    }
}