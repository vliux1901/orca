package org.vliux.orca.provider

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.settings.OpenAiApiKeyAccessState
import org.vliux.orca.settings.OpenAiApiKeyStore
import org.vliux.orca.settings.OpenAiApiKeyMutationResult

class ProviderRuntimeConfigResolverTest {
    @Test
    fun authSourceMapsKnownValues() {
        assertEquals(ProviderAuthSource.USER_SETTINGS, ProviderAuthSource.fromRaw("user_settings"))
        assertEquals(ProviderAuthSource.NONE, ProviderAuthSource.fromRaw(""))
    }

    @Test
    fun authSourceFallsBackForUnexpectedValue() {
        assertEquals(ProviderAuthSource.UNKNOWN, ProviderAuthSource.fromRaw("vault"))
    }

    @Test
    fun providerRuntimeConfigTracksConfigurationState() {
        val configured = ProviderRuntimeConfig(
            providerName = "openai",
            requestUrl = "https://api.openai.com/v1/responses",
            modelName = "gpt-5.4",
            authToken = "secret",
            authSource = ProviderAuthSource.USER_SETTINGS,
        )
        val missingToken = configured.copy(authToken = "", authSource = ProviderAuthSource.NONE)

        assertTrue(configured.isConfigured())
        assertFalse(missingToken.isConfigured())
    }

    @Test
    fun runtimeConfigStoreBuildsConfigFromStoredApiKey() {
        val store = ProviderRuntimeConfigStore(
            metadata = ProviderRuntimeMetadata(
                providerName = "openai",
                requestUrl = "https://api.openai.com/v1/responses",
                modelName = "gpt-5.4",
            ),
            openAiApiKeyStore = FakeOpenAiApiKeyStore("secret"),
        )

        val config = store.currentConfig()

        assertEquals("secret", config.authToken)
        assertEquals(ProviderAuthSource.USER_SETTINGS, config.authSource)
        assertTrue(config.isConfigured())
    }

    @Test
    fun runtimeConfigStoreTreatsLockedStoreAsUnavailable() {
        val store = ProviderRuntimeConfigStore(
            metadata = ProviderRuntimeMetadata(
                providerName = "openai",
                requestUrl = "https://api.openai.com/v1/responses",
                modelName = "gpt-5.4",
            ),
            openAiApiKeyStore = FakeOpenAiApiKeyStore(
                initialValue = "",
                initialAccessState = OpenAiApiKeyAccessState.LOCKED,
                hasStoredApiKey = true,
            ),
        )

        val config = store.currentConfig()

        assertEquals("", config.authToken)
        assertEquals(ProviderAuthSource.NONE, config.authSource)
        assertFalse(config.isConfigured())
    }
}

private class FakeOpenAiApiKeyStore(
    initialValue: String,
    initialAccessState: OpenAiApiKeyAccessState = if (initialValue.isBlank()) {
        OpenAiApiKeyAccessState.NOT_STORED
    } else {
        OpenAiApiKeyAccessState.UNLOCKED
    },
    private val hasStoredApiKey: Boolean = initialValue.isNotBlank(),
) : OpenAiApiKeyStore {
    private val state = MutableStateFlow(initialValue)
    private val accessState = MutableStateFlow(initialAccessState)

    override fun observeApiKey() = state

    override fun observeAccessState() = accessState

    override fun currentApiKey(): String = state.value

    override suspend fun saveApiKey(activity: FragmentActivity, apiKey: String): OpenAiApiKeyMutationResult {
        state.value = apiKey
        accessState.value = if (apiKey.isBlank()) OpenAiApiKeyAccessState.NOT_STORED else OpenAiApiKeyAccessState.UNLOCKED
        return OpenAiApiKeyMutationResult.SAVED
    }

    override suspend fun unlockApiKey(activity: FragmentActivity): OpenAiApiKeyMutationResult {
        accessState.value = if (state.value.isBlank()) OpenAiApiKeyAccessState.NOT_STORED else OpenAiApiKeyAccessState.UNLOCKED
        return OpenAiApiKeyMutationResult.UNLOCKED
    }

    override suspend fun clearApiKey(): OpenAiApiKeyMutationResult {
        state.value = ""
        accessState.value = OpenAiApiKeyAccessState.NOT_STORED
        return OpenAiApiKeyMutationResult.CLEARED
    }

    override fun hasStoredApiKey(): Boolean = hasStoredApiKey
}
