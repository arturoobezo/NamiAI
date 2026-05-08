package com.example.namiasistenteaioffline.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class AppSettings(private val context: Context) {
    companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val LOCAL_HISTORY = booleanPreferencesKey("local_history")
        val LAST_CHAT_ID = stringPreferencesKey("last_chat_id")
        val ACCELERATOR = stringPreferencesKey("accelerator")
        val SELECTED_VOICE = stringPreferencesKey("selected_voice")
        val RESPONSE_MODE = stringPreferencesKey("response_mode") // "corta", "normal", "detallada"
    }

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE] ?: false }
    val voiceEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[VOICE_ENABLED] ?: false }
    val selectedModelFlow: Flow<String> = context.dataStore.data.map { it[SELECTED_MODEL] ?: "qwen_0.5b" }
    val localHistoryFlow: Flow<Boolean> = context.dataStore.data.map { it[LOCAL_HISTORY] ?: true }
    val lastChatIdFlow: Flow<String?> = context.dataStore.data.map { it[LAST_CHAT_ID] }
    val acceleratorFlow: Flow<String> = context.dataStore.data.map { it[ACCELERATOR] ?: "cpu" }
    val selectedVoiceFlow: Flow<String> = context.dataStore.data.map { it[SELECTED_VOICE] ?: "" }
    val responseModeFlow: Flow<String> = context.dataStore.data.map { it[RESPONSE_MODE] ?: "normal" }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[VOICE_ENABLED] = enabled }
    }

    suspend fun setSelectedModel(modelId: String) {
        context.dataStore.edit { it[SELECTED_MODEL] = modelId }
    }

    suspend fun setLocalHistory(enabled: Boolean) {
        context.dataStore.edit { it[LOCAL_HISTORY] = enabled }
    }

    suspend fun setLastChatId(id: String?) {
        context.dataStore.edit { preferences ->
            if (id == null) preferences.remove(LAST_CHAT_ID)
            else preferences[LAST_CHAT_ID] = id
        }
    }

    suspend fun setAccelerator(value: String) {
        context.dataStore.edit { it[ACCELERATOR] = value }
    }

    suspend fun setSelectedVoice(voiceName: String) {
        context.dataStore.edit { it[SELECTED_VOICE] = voiceName }
    }

    suspend fun setResponseMode(mode: String) {
        context.dataStore.edit { it[RESPONSE_MODE] = mode }
    }
}
