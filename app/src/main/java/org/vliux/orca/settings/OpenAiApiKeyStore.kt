package org.vliux.orca.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface OpenAiApiKeyStore {
    fun observeApiKey(): StateFlow<String>

    fun currentApiKey(): String

    suspend fun saveApiKey(apiKey: String)
}

@Singleton
class SharedPreferencesOpenAiApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : OpenAiApiKeyStore {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val apiKeyFlow = MutableStateFlow(readStoredApiKey())

    override fun observeApiKey(): StateFlow<String> = apiKeyFlow.asStateFlow()

    override fun currentApiKey(): String = apiKeyFlow.value

    override suspend fun saveApiKey(apiKey: String) {
        val normalizedKey = apiKey.trim()
        preferences.edit().putString(KEY_OPENAI_API_KEY, normalizedKey).apply()
        apiKeyFlow.value = normalizedKey
    }

    private fun readStoredApiKey(): String = preferences.getString(KEY_OPENAI_API_KEY, "").orEmpty().trim()

    private companion object {
        const val PREFERENCES_NAME = "orca_user_settings"
        const val KEY_OPENAI_API_KEY = "openai_api_key"
    }
}
