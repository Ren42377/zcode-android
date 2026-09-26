package com.zcode.android.core.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

// App preferences backed by DataStore. Values mirror the ZCode setting.json
// scope: provider and model selection, thinking effort, theme.
class UserPreferences(private val context: Context) {
    val onboarded: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDED] ?: false }

    val providerId: Flow<String?> =
        context.dataStore.data.map { it[KEY_PROVIDER_ID] }

    val model: Flow<String?> =
        context.dataStore.data.map { it[KEY_MODEL] }

    val thinkingEffort: Flow<String?> =
        context.dataStore.data.map { it[KEY_THINKING_EFFORT] }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDED] = value }
    }

    suspend fun setProviderId(value: String) {
        context.dataStore.edit { it[KEY_PROVIDER_ID] = value }
    }

    suspend fun setModel(value: String) {
        context.dataStore.edit { it[KEY_MODEL] = value }
    }

    suspend fun setThinkingEffort(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(KEY_THINKING_EFFORT)
            } else {
                prefs[KEY_THINKING_EFFORT] = value
            }
        }
    }

    private companion object {
        fun key(name: String): Preferences.Key<String> = stringPreferencesKey(name)

        val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        val KEY_PROVIDER_ID = key("provider_id")
        val KEY_MODEL = key("model")
        val KEY_THINKING_EFFORT = key("thinking_effort")
    }
}
